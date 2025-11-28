package org.nexus.impl;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.zip.CRC32;
import org.nexus.MetricsTracker;
import org.nexus.Partition;
import org.nexus.domain.MessageMetadata;
import org.nexus.domain.PartitionStats;
import org.nexus.domain.StoredMessage;
import org.nexus.domain.TopicConfig;
import org.nexus.domain.TopicStats;
import org.nexus.interfaces.Topic;

/**
 * A topic containing one or more partitions
 */
public class TopicImpl implements Topic {

  private final String name;
  private final TopicConfig config;
  private final Partition[] partitions;

  // Metrics tracking
  private final MetricsTracker metrics;

  // Background cleanup
  private final ScheduledExecutorService cleanupExecutor;

  public TopicImpl(String name, TopicConfig config) {
    this.name = name;
    this.config = config;

    // Create partitions
    this.partitions = new Partition[config.partitions()];
    for (int i = 0; i < config.partitions(); i++) {
      partitions[i] = new Partition(i, config);
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
   * Append a message to the appropriate partition
   */
  public long append(byte[] payload, MessageMetadata metadata) {
    // Select partition
    int partitionId = selectPartition(metadata.key());
    Partition partition = partitions[partitionId];

    // Append to partition
    long offset = partition.append(payload, metadata);

    // Update metrics
    metrics.recordMessage(payload.length);

    return offset;
  }

  /**
   * Read messages from a specific partition
   */
  public java.util.List<StoredMessage> read(int partitionId, long fromOffset, int maxMessages) {
    if (partitionId < 0 || partitionId >= partitions.length) {
      throw new IllegalArgumentException("Invalid partition: " + partitionId);
    }

    return partitions[partitionId].read(fromOffset, maxMessages);
  }

  /**
   * Select partition based on key (or round-robin if no key)
   */
  private int selectPartition(String key) {
    if (partitions.length == 1) {
      return 0; // Fast path for single partition
    }

    if (key == null || key.isEmpty()) {
      // Round-robin for null keys
      return (int) (metrics.getTotalMessages() % partitions.length);
    }

    // Hash-based partitioning for keys
    // Use CRC32 for fast, consistent hashing
    CRC32 crc = new CRC32();
    crc.update(key.getBytes(StandardCharsets.UTF_8));
    long hash = crc.getValue();

    return (int) (Math.abs(hash) % partitions.length);
  }

  /**
   * Get a specific partition
   */
  public Partition getPartition(int partitionId) {
    if (partitionId < 0 || partitionId >= partitions.length) {
      throw new IllegalArgumentException("Invalid partition: " + partitionId);
    }
    return partitions[partitionId];
  }

  /**
   * Cleanup old messages in all partitions
   */
  private void cleanup() {
    for (Partition partition : partitions) {
      try {
        partition.cleanup();
      } catch (Exception e) {
        System.err.println("Error cleaning partition " + partition.getId() + ": " + e.getMessage());
      }
    }
  }

  /**
   * Cleanup consumed messages
   */
  public void cleanupConsumedMessages(Map<String, Map<Integer, Long>> allGroupOffsets) {
    for (int i = 0; i < partitions.length; i++) {
      // Find minimum committed offset across all consumer groups
      long minOffset = Long.MAX_VALUE;

      for (Map<Integer, Long> groupOffsets : allGroupOffsets.values()) {
        Long offset = groupOffsets.get(i);
        if (offset != null && offset < minOffset) {
          minOffset = offset;
        }
      }

      if (minOffset != Long.MAX_VALUE) {
        partitions[i].removeConsumedMessages(minOffset);
      }
    }
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public TopicConfig config() {
    return config;
  }

  @Override
  public int partitionCount() {
    return partitions.length;
  }

  @Override
  public TopicStats stats() {
    Map<Integer, PartitionStats> partitionStats = new HashMap<>();
    long totalMessages = 0;

    for (Partition partition : partitions) {
      PartitionStats stats = partition.getStats();
      partitionStats.put(partition.getId(), stats);
      totalMessages += stats.messageCount();
    }

    return new TopicStats(
        name,
        totalMessages,
        metrics.getBytesIn(),
        metrics.getBytesOut(),
        metrics.getMessagesPerSecond(),
        partitionStats
    );
  }

  /**
   * Shutdown this topic
   */
  public void shutdown() {
    cleanupExecutor.shutdown();
    try {
      cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
