package org.nexus.impl;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.CRC32;
import org.nexus.MetricsTracker;
import org.nexus.Queue;
import org.nexus.domain.AppendResult;
import org.nexus.domain.CategoryConfig;
import org.nexus.domain.CategoryStats;
import org.nexus.domain.MessageInput;
import org.nexus.domain.MessageMetadata;
import org.nexus.domain.QueueCapacityConfig;
import org.nexus.domain.QueueStats;
import org.nexus.domain.StoredMessage;
import org.nexus.interfaces.Category;

/**
 * A category containing one or more queues. Handles message routing, partitioning, and background
 * cleanup.
 */
public class CategoryImpl implements Category {

  private final String name;
  private final CategoryConfig config;
  private final Queue[] queues;

  // Metrics tracking
  private final MetricsTracker metrics;

  // Round-robin counter for messages without keys
  private final AtomicLong roundRobinCounter = new AtomicLong(0);

  // Background cleanup
  private final ScheduledExecutorService cleanupExecutor;

  public CategoryImpl(String name, CategoryConfig config) {
    this(name, config, QueueCapacityConfig.defaultConfig());
  }

  public CategoryImpl(String name, CategoryConfig config, QueueCapacityConfig capacityConfig) {
    this.name = name;
    this.config = config;

    // Create queues with capacity config
    this.queues = new Queue[config.queues()];
    for (int i = 0; i < config.queues(); i++) {
      queues[i] = new Queue(i, config, capacityConfig);
    }

    this.metrics = new MetricsTracker();

    // Start background cleanup task
    this.cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
      Thread t = new Thread(r, "cleanup-" + name);
      t.setDaemon(true);
      return t;
    });

    // Run cleanup every minute
    cleanupExecutor.scheduleAtFixedRate(
        this::cleanup,
        1, 1, TimeUnit.MINUTES
    );
  }

  /**
   * Append a message to the appropriate queue.
   */
  public AppendResult append(byte[] payload, MessageInput input) {
    // Validate input
    if (!input.category().equals(name)) {
      throw new IllegalArgumentException(
          "Message category '" + input.category() + "' does not match this category '" + name + "'"
      );
    }

    // Select queue based on key (or round-robin if no key)
    int queueId = selectQueue(input.key());
    Queue queue = queues[queueId];

    // Append and update queue metrics
    MessageMetadata messageMetadata = queue.append(payload, input);
    metrics.recordMessage(payload.length);

    return new AppendResult(
        messageMetadata.id(),
        queueId,
        messageMetadata.offset()
    );
  }

  /**
   * Read messages from a specific queue
   */
  public List<StoredMessage> read(int queueId, long fromOffset, int maxMessages) {
    if (queueId < 0 || queueId >= queues.length) {
      throw new IllegalArgumentException("Invalid queue: " + queueId);
    }

    return queues[queueId].read(fromOffset, maxMessages);
  }

  /**
   * Select the queue based on a key using consistent hashing.
   */
  private int selectQueue(String key) {
    if (queues.length == 1) {
      return 0; // Fast path for a single queue
    }

    if (key == null || key.isEmpty()) {
      // Round-robin for null keys using dedicated counter
      // This ensures even distribution regardless of message rate
      return (int) (roundRobinCounter.getAndIncrement() % queues.length);
    }

    // Hash-based partitioning for keys
    CRC32 crc = new CRC32();
    crc.update(key.getBytes(StandardCharsets.UTF_8));
    long hash = crc.getValue();

    return (int) (Math.abs(hash) % queues.length);
  }

  /**
   * Get a specific queue
   */
  public Queue getQueue(int queueId) {
    if (queueId < 0 || queueId >= queues.length) {
      throw new IllegalArgumentException("Invalid queue: " + queueId);
    }
    return queues[queueId];
  }

  /**
   * Clean up old messages in all queues based on retention policy
   */
  private void cleanup() {
    for (Queue queue : queues) {
      try {
        queue.cleanup();
      } catch (Exception e) {
        // TODO: Replace with proper logging (SLF4J)
        System.err.println("Error cleaning queue " + queue.getId() +
            " in category " + name + ": " + e.getMessage());
        e.printStackTrace();
      }
    }
  }

  /**
   * Cleanup consumed messages across all queues. Removes messages that all consumer groups have
   * already processed.
   */
  public void cleanupConsumedMessages(Map<String, Map<Integer, Long>> allGroupOffsets) {
    for (int i = 0; i < queues.length; i++) {
      // Find minimum committed offset across all consumer groups for this queue
      long minOffset = Long.MAX_VALUE;

      for (Map<Integer, Long> groupOffsets : allGroupOffsets.values()) {
        Long offset = groupOffsets.get(i);
        if (offset != null && offset < minOffset) {
          minOffset = offset;
        }
      }

      // If we found a minimum offset, remove all messages before it
      if (minOffset != Long.MAX_VALUE && minOffset > 0) {
        try {
          queues[i].removeConsumedMessages(minOffset);
        } catch (Exception e) {
          // TODO: Replace with proper logging (SLF4J)
          System.err.println("Error removing consumed messages from queue " + i +
              " in category " + name + ": " + e.getMessage());
          e.printStackTrace();
        }
      }
    }
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public CategoryConfig config() {
    return config;
  }

  @Override
  public int queueCount() {
    return queues.length;
  }

  @Override
  public CategoryStats stats() {
    Map<Integer, QueueStats> queueStats = new HashMap<>();
    long totalMessages = 0;

    for (Queue queue : queues) {
      QueueStats stats = queue.getStats();
      queueStats.put(queue.getId(), stats);
      totalMessages += stats.messageCount();
    }

    return new CategoryStats(
        name,
        totalMessages,
        metrics.getBytesIn(),
        metrics.getBytesOut(),
        metrics.getMessagesPerSecond(),
        queueStats
    );
  }

  /**
   * Shutdown this category gracefully
   */
  public void shutdown() {
    cleanupExecutor.shutdown();
    try {
      if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
        cleanupExecutor.shutdownNow();
      }
    } catch (InterruptedException e) {
      cleanupExecutor.shutdownNow();
      Thread.currentThread().interrupt();
    }
  }
}