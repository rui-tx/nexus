package org.nexus.embedded;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.nexus.ConsumerGroup;
import org.nexus.domain.AppendResult;
import org.nexus.domain.BinaryMessage;
import org.nexus.domain.BrokerStats;
import org.nexus.domain.CategoryConfig;
import org.nexus.domain.CategoryStats;
import org.nexus.domain.ConsumerConfig;
import org.nexus.domain.MessageInput;
import org.nexus.domain.ProducerConfig;
import org.nexus.domain.PublishResult;
import org.nexus.domain.StoredMessage;
import org.nexus.impl.CategoryImpl;
import org.nexus.interfaces.Category;
import org.nexus.interfaces.Deserializer;
import org.nexus.interfaces.MessageConsumer;
import org.nexus.interfaces.MessageProducer;
import org.nexus.interfaces.QueueBroker;
import org.nexus.interfaces.Serializer;
import org.nexus.persistence.FileLog;
import org.nexus.persistence.OffsetStore;
import org.nexus.persistence.QueueStorage;
import org.nexus.serialization.Deserializers;
import org.nexus.serialization.Serializers;

/**
 * Main embedded queue broker. Coordinates categories, consumer groups, and message routing.
 * <p>
 * This embedded implementation currently operates on {@code byte[]} payloads via the default
 * serializers/deserializers. Higher-level typed producers/consumers are expected to wrap this
 * broker.
 */
public class EmbeddedQueueBroker implements QueueBroker {

  private static final long MAINTENANCE_INTERVAL_MINUTES = 5L;
  private static final long INACTIVE_CONSUMER_TIMEOUT_MS = 30_000L;
  private final Map<String, CategoryImpl> categories = new ConcurrentHashMap<>();
  // Consumer groups: categoryName -> groupId -> ConsumerGroup
  private final Map<String, Map<String, ConsumerGroup>> consumerGroups = new ConcurrentHashMap<>();
  // Background tasks
  private final ScheduledExecutorService maintenanceExecutor;
  private final OffsetStore offsetStore = new OffsetStore();
  private volatile boolean shutdown = false;

  public EmbeddedQueueBroker() {
    this.maintenanceExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
      Thread t = new Thread(r, "broker-maintenance");
      t.setDaemon(true);
      return t;
    });

    maintenanceExecutor.scheduleAtFixedRate(
        this::runMaintenance,
        MAINTENANCE_INTERVAL_MINUTES,
        MAINTENANCE_INTERVAL_MINUTES,
        TimeUnit.MINUTES
    );

    recoverFromDisk();
  }

  @Override
  public <T> MessageProducer<T> createProducer(ProducerConfig config) {
    if (shutdown) {
      throw new IllegalStateException("Broker is shut down");
    }

    // Use byte array serializer by default
    @SuppressWarnings("unchecked")
    Serializer<T> serializer = (Serializer<T>) Serializers.byteArray();

    return new EmbeddedProducer<>(this, config, serializer);
  }

  @Override
  public <T> MessageConsumer<T> createConsumer(ConsumerConfig config) {
    if (shutdown) {
      throw new IllegalStateException("Broker is shut down");
    }

    // Use byte array deserializer by default
    @SuppressWarnings("unchecked")
    Deserializer<T> deserializer = (Deserializer<T>) Deserializers.byteArray();

    return new EmbeddedConsumer<>(this, config, deserializer);
  }

  @Override
  public Category getOrCreateCategory(String name, CategoryConfig config) {
    return categories.computeIfAbsent(name, k -> new CategoryImpl(name, config));
  }

  @Override
  public CompletableFuture<Void> deleteCategory(String name) {
    return CompletableFuture.runAsync(() -> {
      CategoryImpl category = categories.remove(name);
      if (category != null) {
        category.shutdown();
        consumerGroups.remove(name);
      }
    });
  }

  @Override
  public String[] listCategories() {
    return categories.keySet().toArray(new String[0]);
  }

  @Override
  public CompletableFuture<Void> shutdown() {
    return CompletableFuture.runAsync(() -> {
      shutdown = true;

      for (CategoryImpl category : categories.values()) {
        category.shutdown();
      }

      for (Map<String, ConsumerGroup> groups : consumerGroups.values()) {
        for (ConsumerGroup cg : groups.values()) {
          cg.shutdown();
        }
      }

      // Shutdown maintenance
      maintenanceExecutor.shutdown();
      try {
        maintenanceExecutor.awaitTermination(5, TimeUnit.SECONDS);
      } catch (InterruptedException _) {
        Thread.currentThread().interrupt();
      }
    });
  }

  private CategoryImpl getOrCreateCategoryImpl(String name) {
    Category category = getOrCreateCategory(name, CategoryConfig.defaults());
    return (CategoryImpl) category;
  }

  /**
   * Publish a message
   */
  public PublishResult publish(String categoryName, byte[] payload, MessageInput input) {
    // Get or create a category
    CategoryImpl category = getOrCreateCategoryImpl(categoryName);

    // Append message assigning queue, offset, and generate message ID
    AppendResult result = category.append(payload, input);

    return new PublishResult(
        result.messageId(),
        categoryName,
        result.queueId(),
        result.offset(),
        Instant.now()
    );
  }

  /**
   * Register a consumer
   */
  public void registerConsumer(
      String groupId,
      String consumerId,
      String categoryName,
      ConsumerConfig config
  ) {
    // Ensure a category exists
    CategoryImpl category = getOrCreateCategoryImpl(categoryName);

    // Get or create a consumer group
    ConsumerGroup group = consumerGroups
        .computeIfAbsent(categoryName, _ -> new ConcurrentHashMap<>())
        .computeIfAbsent(groupId, _ -> new ConsumerGroup(groupId, categoryName));

    // Register consumer
    group.registerConsumer(consumerId, config, category.queueCount());
  }

  /**
   * Unregister a consumer
   */
  public void unregisterConsumer(String groupId, String consumerId, String topicName) {
    Map<String, ConsumerGroup> categoryGroups = consumerGroups.get(topicName);
    if (categoryGroups != null) {
      ConsumerGroup group = categoryGroups.get(groupId);
      if (group != null) {
        CategoryImpl category = categories.get(topicName);
        if (category != null) {
          group.unregisterConsumer(consumerId, category.queueCount());
        }
      }
    }
  }

  private void recoverFromDisk() {
    Path baseDir = QueueStorage.baseDir();
    if (!Files.exists(baseDir) || !Files.isDirectory(baseDir)) {
      return;
    }

    try (DirectoryStream<Path> categoryDirs = Files.newDirectoryStream(baseDir)) {
      for (Path categoryDir : categoryDirs) {
        if (!Files.isDirectory(categoryDir)) {
          continue;
        }

        String categoryName = categoryDir.getFileName().toString();

        int maxQueueId = -1;
        try (DirectoryStream<Path> logFiles = Files.newDirectoryStream(categoryDir, "*.log")) {
          for (Path logFile : logFiles) {
            String fileName = logFile.getFileName().toString();
            int dotIndex = fileName.indexOf('.');
            if (dotIndex <= 0) {
              continue;
            }
            String queuePart = fileName.substring(0, dotIndex);
            try {
              int queueId = Integer.parseInt(queuePart);
              if (queueId > maxQueueId) {
                maxQueueId = queueId;
              }
            } catch (NumberFormatException ignored) {
            }
          }
        }

        if (maxQueueId < 0) {
          continue;
        }

        int queueCount = maxQueueId + 1;

        CategoryImpl category = new CategoryImpl(
            categoryName,
            new CategoryConfig(queueCount, 1, 86_400_000L, true)
        );
        categories.put(categoryName, category);

        try (DirectoryStream<Path> offsetFiles = Files.newDirectoryStream(categoryDir,
            "offsets-*.json")) {
          for (Path offsetFile : offsetFiles) {
            String fileName = offsetFile.getFileName().toString();
            if (!fileName.startsWith("offsets-") || !fileName.endsWith(".json")) {
              continue;
            }
            String groupId = fileName.substring("offsets-".length(),
                fileName.length() - ".json".length());
            if (groupId.isEmpty()) {
              continue;
            }
            Map<Integer, Long> offsets = offsetStore.load(categoryName, groupId);
            if (offsets.isEmpty()) {
              continue;
            }
            ConsumerGroup group = consumerGroups
                .computeIfAbsent(categoryName, _ -> new ConcurrentHashMap<>())
                .computeIfAbsent(groupId, _ -> new ConsumerGroup(groupId, categoryName));
            for (Map.Entry<Integer, Long> entry : offsets.entrySet()) {
              group.commitOffset(entry.getKey(), entry.getValue());
            }
          }
        }

        for (int queueId = 0; queueId < queueCount; queueId++) {
          try (FileLog log = FileLog.forCategoryQueue(categoryName, queueId)) {
            List<BinaryMessage> messages = log.replayFromStart();
            for (BinaryMessage message : messages) {
              int msgQueueId = message.queueId();
              long msgOffset = message.offset();
              category.getQueue(msgQueueId).loadFromLog(msgOffset, message);
            }
          }
        }
      }
    } catch (IOException e) {
      System.err.println("Failed to recover broker state from disk: " + e.getMessage());
    }
  }

  /**
   * Get queues assigned to a consumer
   */
  public List<Integer> getAssignedQueues(String groupId, String consumerId, String categoryName) {
    Map<String, ConsumerGroup> categoryGroups = consumerGroups.get(categoryName);
    if (categoryGroups == null) {
      return Collections.emptyList();
    }

    ConsumerGroup group = categoryGroups.get(groupId);
    if (group == null) {
      return Collections.emptyList();
    }

    return group.getAssignedQueues(consumerId);
  }

  /**
   * Fetch messages from a queue
   */
  public List<StoredMessage> fetchMessages(
      String categoryName,
      int queue,
      long fromOffset,
      int maxMessages
  ) {
    CategoryImpl topic = categories.get(categoryName);
    if (topic == null) {
      return Collections.emptyList();
    }

    return topic.read(queue, fromOffset, maxMessages);
  }

  /**
   * Commit an offset
   */
  public void commitOffset(String groupId, String categoryName, int queue, long offset) {
    Map<String, ConsumerGroup> categoryGroups = consumerGroups.get(categoryName);
    if (categoryGroups == null) {
      return;
    }

    ConsumerGroup group = categoryGroups.get(groupId);
    if (group == null) {
      return;
    }

    // Update in-memory offsets first
    group.commitOffset(queue, offset);

    // Persist offsets only for persistent categories
    CategoryImpl category = categories.get(categoryName);
    if (category != null && category.config().persistent()) {
      try {
        offsetStore.persist(categoryName, groupId, group.getAllCommittedOffsets());
      } catch (RuntimeException e) {
        System.err.println("Failed to persist offsets for group '" + groupId + "' in category '"
            + categoryName + "': " + e.getMessage());
      }
    }
  }

  /**
   * Get committed offset
   */
  public long getCommittedOffset(String groupId, String categoryName, int queue) {
    Map<String, ConsumerGroup> categoryGroups = consumerGroups.get(categoryName);
    if (categoryGroups == null) {
      return 0L;
    }

    ConsumerGroup group = categoryGroups.get(groupId);
    if (group == null) {
      return 0L;
    }

    return group.getCommittedOffset(queue);
  }

  /**
   * Record a heartbeat for an active consumer.
   */
  public void heartbeat(String groupId, String consumerId, String categoryName) {
    Map<String, ConsumerGroup> categoryGroups = consumerGroups.get(categoryName);
    if (categoryGroups == null) {
      return;
    }

    ConsumerGroup group = categoryGroups.get(groupId);
    if (group == null) {
      return;
    }

    group.heartbeat(consumerId);
  }

  /**
   * Background maintenance tasks
   */
  private void runMaintenance() {
    try {
      // Check for inactive consumers
      for (Map.Entry<String, Map<String, ConsumerGroup>> categoryEntry : consumerGroups.entrySet()) {
        String categoryName = categoryEntry.getKey();
        CategoryImpl category = categories.get(categoryName);

        if (category == null) {
          continue;
        }

        // Remove inactive consumers
        for (ConsumerGroup group : categoryEntry.getValue().values()) {
          group.removeInactiveConsumers(INACTIVE_CONSUMER_TIMEOUT_MS, category.queueCount());
        }

        // cleanup consumed messages
        Map<String, Map<Integer, Long>> allOffsets = new HashMap<>();
        for (Map.Entry<String, ConsumerGroup> groupEntry : categoryEntry.getValue().entrySet()) {
          allOffsets.put(groupEntry.getKey(), groupEntry.getValue().getAllCommittedOffsets());
        }
        category.cleanupConsumedMessages(allOffsets);
      }

    } catch (Exception e) {
      System.err.println("Error in maintenance: " + e.getMessage());
    }
  }

  /**
   * Get broker statistics
   */
  public BrokerStats getStats() {
    Map<String, CategoryStats> categoryStats = new HashMap<>();
    long totalMessages = 0;
    long totalBytes = 0;

    for (Map.Entry<String, CategoryImpl> entry : categories.entrySet()) {
      CategoryStats stats = entry.getValue().stats();
      categoryStats.put(entry.getKey(), stats);
      totalMessages += stats.messageCount();
      totalBytes += stats.bytesIn();
    }

    int totalConsumers = consumerGroups.values().stream()
        .flatMap(m -> m.values().stream())
        .mapToInt(ConsumerGroup::getConsumerCount)
        .sum();

    return new BrokerStats(
        categories.size(),
        totalMessages,
        totalBytes,
        totalConsumers,
        categoryStats
    );
  }
}