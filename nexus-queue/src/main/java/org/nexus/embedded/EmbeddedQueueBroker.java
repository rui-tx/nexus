package org.nexus.embedded;

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
import org.nexus.domain.BrokerStats;
import org.nexus.domain.ConsumerConfig;
import org.nexus.domain.MessageMetadata;
import org.nexus.domain.ProducerConfig;
import org.nexus.domain.PublishResult;
import org.nexus.domain.StoredMessage;
import org.nexus.domain.TopicConfig;
import org.nexus.domain.TopicStats;
import org.nexus.impl.TopicImpl;
import org.nexus.interfaces.Deserializer;
import org.nexus.interfaces.MessageConsumer;
import org.nexus.interfaces.MessageProducer;
import org.nexus.interfaces.QueueBroker;
import org.nexus.interfaces.Serializer;
import org.nexus.interfaces.Topic;
import org.nexus.serialization.Deserializers;
import org.nexus.serialization.Serializers;

/**
 * Main embedded queue broker. Coordinates topics, consumer groups, and message routing.
 */
public class EmbeddedQueueBroker implements QueueBroker {

  // Topics storage
  private final Map<String, TopicImpl> topics = new ConcurrentHashMap<>();

  // Consumer groups: topicName -> groupId -> ConsumerGroup
  private final Map<String, Map<String, ConsumerGroup>> consumerGroups = new ConcurrentHashMap<>();

  // Background tasks
  private final ScheduledExecutorService maintenanceExecutor;

  // Shutdown flag
  private volatile boolean shutdown = false;

  public EmbeddedQueueBroker() {
    // Start maintenance tasks (cleanup, heartbeat checks, etc.)
    this.maintenanceExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
      Thread t = new Thread(r, "broker-maintenance");
      t.setDaemon(true);
      return t;
    });

    // Run maintenance every 30 seconds
    maintenanceExecutor.scheduleAtFixedRate(
        this::runMaintenance,
        30, 30, TimeUnit.SECONDS
    );
  }

  @Override
  public <T> MessageProducer<T> createProducer(ProducerConfig config) {
    if (shutdown) {
      throw new IllegalStateException("Broker is shut down");
    }

    // Use byte array serializer by default
    // Users can wrap this with their own serializer
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
  public Topic getTopic(String name, TopicConfig config) {
    return topics.computeIfAbsent(name, k -> new TopicImpl(name, config));
  }

  @Override
  public CompletableFuture<Void> deleteTopic(String name) {
    return CompletableFuture.runAsync(() -> {
      TopicImpl topic = topics.remove(name);
      if (topic != null) {
        topic.shutdown();

        // Remove consumer groups for this topic
        consumerGroups.remove(name);
      }
    });
  }

  @Override
  public String[] listTopics() {
    return topics.keySet().toArray(new String[0]);
  }

  @Override
  public CompletableFuture<Void> shutdown() {
    return CompletableFuture.runAsync(() -> {
      shutdown = true;

      // Shutdown all topics
      for (TopicImpl topic : topics.values()) {
        topic.shutdown();
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

  // ========================================================================
  // Internal methods used by producers and consumers
  // ========================================================================

  /**
   * Internal helper to get TopicImpl (with full implementation)
   */
  private TopicImpl getTopicImpl(String name) {
    Topic topic = getTopic(name, TopicConfig.defaults());
    return (TopicImpl) topic;
  }

  /**
   * Publish a message (called by producer)
   */
  public PublishResult publish(String topicName, byte[] payload, MessageMetadata metadata) {
    // Get or create topic
    TopicImpl topic = getTopicImpl(topicName);

    // Append message
    long offset = topic.append(payload, metadata);

    // Return result
    return new PublishResult(
        metadata.id(),
        topicName,
        metadata.partition(),
        offset,
        Instant.now()
    );
  }

  /**
   * Register a consumer (called when consumer subscribes)
   */
  public void registerConsumer(
      String groupId,
      String consumerId,
      String topicName,
      ConsumerConfig config
  ) {
    // Ensure topic exists
    TopicImpl topic = getTopicImpl(topicName);

    // Get or create consumer group
    ConsumerGroup group = consumerGroups
        .computeIfAbsent(topicName, k -> new ConcurrentHashMap<>())
        .computeIfAbsent(groupId, k -> new ConsumerGroup(groupId, topicName));

    // Register consumer
    group.registerConsumer(consumerId, config, topic.partitionCount());
  }

  /**
   * Unregister a consumer
   */
  public void unregisterConsumer(String groupId, String consumerId, String topicName) {
    Map<String, ConsumerGroup> topicGroups = consumerGroups.get(topicName);
    if (topicGroups != null) {
      ConsumerGroup group = topicGroups.get(groupId);
      if (group != null) {
        TopicImpl topic = topics.get(topicName);
        if (topic != null) {
          group.unregisterConsumer(consumerId, topic.partitionCount());
        }
      }
    }
  }

  /**
   * Get partitions assigned to a consumer
   */
  public List<Integer> getAssignedPartitions(String groupId, String consumerId, String topicName) {
    Map<String, ConsumerGroup> topicGroups = consumerGroups.get(topicName);
    if (topicGroups == null) {
      return Collections.emptyList();
    }

    ConsumerGroup group = topicGroups.get(groupId);
    if (group == null) {
      return Collections.emptyList();
    }

    return group.getAssignedPartitions(consumerId);
  }

  /**
   * Fetch messages from a partition (called by consumer)
   */
  public List<StoredMessage> fetchMessages(
      String topicName,
      int partition,
      long fromOffset,
      int maxMessages
  ) {
    TopicImpl topic = topics.get(topicName);
    if (topic == null) {
      return Collections.emptyList();
    }

    return topic.read(partition, fromOffset, maxMessages);
  }

  /**
   * Commit an offset
   */
  public void commitOffset(String groupId, String topicName, int partition, long offset) {
    Map<String, ConsumerGroup> topicGroups = consumerGroups.get(topicName);
    if (topicGroups != null) {
      ConsumerGroup group = topicGroups.get(groupId);
      if (group != null) {
        group.commitOffset(partition, offset);
      }
    }
  }

  /**
   * Get committed offset
   */
  public long getCommittedOffset(String groupId, String topicName, int partition) {
    Map<String, ConsumerGroup> topicGroups = consumerGroups.get(topicName);
    if (topicGroups == null) {
      return 0L;
    }

    ConsumerGroup group = topicGroups.get(groupId);
    if (group == null) {
      return 0L;
    }

    return group.getCommittedOffset(partition);
  }

  /**
   * Background maintenance tasks
   */
  private void runMaintenance() {
    try {
      // Check for inactive consumers
      for (Map.Entry<String, Map<String, ConsumerGroup>> topicEntry : consumerGroups.entrySet()) {
        String topicName = topicEntry.getKey();
        TopicImpl topic = topics.get(topicName);

        if (topic == null) {
          continue;
        }

        // Remove inactive consumers
        for (ConsumerGroup group : topicEntry.getValue().values()) {
          group.removeInactiveConsumers(30000, topic.partitionCount());
        }

        // cleanup consumed messages
        Map<String, Map<Integer, Long>> allOffsets = new HashMap<>();
        for (Map.Entry<String, ConsumerGroup> groupEntry : topicEntry.getValue().entrySet()) {
          allOffsets.put(groupEntry.getKey(), groupEntry.getValue().getAllCommittedOffsets());
        }
        topic.cleanupConsumedMessages(allOffsets);
      }

    } catch (Exception e) {
      System.err.println("Error in maintenance: " + e.getMessage());
    }
  }

  // ========================================================================
  // Statistics and Monitoring
  // ========================================================================

  /**
   * Get broker statistics
   */
  public BrokerStats getStats() {
    Map<String, TopicStats> topicStats = new HashMap<>();
    long totalMessages = 0;
    long totalBytes = 0;

    for (Map.Entry<String, TopicImpl> entry : topics.entrySet()) {
      TopicStats stats = entry.getValue().stats();
      topicStats.put(entry.getKey(), stats);
      totalMessages += stats.messageCount();
      totalBytes += stats.bytesIn();
    }

    int totalConsumers = consumerGroups.values().stream()
        .flatMap(m -> m.values().stream())
        .mapToInt(ConsumerGroup::getConsumerCount)
        .sum();

    return new BrokerStats(
        topics.size(),
        totalMessages,
        totalBytes,
        totalConsumers,
        topicStats
    );
  }
}

