package org.nexus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.nexus.domain.ConsumerConfig;

/**
 * Manages a group of consumers for a topic. Handles partition assignment and offset tracking.
 */
public class ConsumerGroup {

  private final String groupId;
  private final String topic;

  // Active consumers in this group
  private final Map<String, ConsumerRegistration> consumers = new ConcurrentHashMap<>();

  // Partition assignments: consumerId -> List<partitionId>
  private final Map<String, List<Integer>> assignments = new ConcurrentHashMap<>();

  // Offset tracking: partitionId -> committed offset
  private final Map<Integer, Long> committedOffsets = new ConcurrentHashMap<>();

  // For coordinating rebalancing
  private final ReadWriteLock rebalanceLock = new ReentrantReadWriteLock();

  public ConsumerGroup(String groupId, String topic) {
    this.groupId = groupId;
    this.topic = topic;
  }

  /**
   * Register a new consumer
   */
  public void registerConsumer(String consumerId, ConsumerConfig config, int totalPartitions) {
    rebalanceLock.writeLock().lock();
    try {
      ConsumerRegistration registration = new ConsumerRegistration(
          consumerId,
          config,
          System.currentTimeMillis()
      );

      consumers.put(consumerId, registration);

      // Trigger rebalance
      rebalance(totalPartitions);

    } finally {
      rebalanceLock.writeLock().unlock();
    }
  }

  /**
   * Unregister a consumer
   */
  public void unregisterConsumer(String consumerId, int totalPartitions) {
    rebalanceLock.writeLock().lock();
    try {
      consumers.remove(consumerId);
      assignments.remove(consumerId);

      // Trigger rebalance to reassign partitions
      if (!consumers.isEmpty()) {
        rebalance(totalPartitions);
      }

    } finally {
      rebalanceLock.writeLock().unlock();
    }
  }

  /**
   * Rebalance partitions among consumers Strategy: Simple round-robin assignment
   */
  private void rebalance(int totalPartitions) {
    // Clear existing assignments
    assignments.clear();

    if (consumers.isEmpty()) {
      return;
    }

    // Get sorted list of consumer IDs for consistent assignment
    List<String> consumerIds = new ArrayList<>(consumers.keySet());
    Collections.sort(consumerIds);

    // Assign partitions round-robin
    for (int partition = 0; partition < totalPartitions; partition++) {
      String consumerId = consumerIds.get(partition % consumerIds.size());
      assignments.computeIfAbsent(consumerId, k -> new ArrayList<>())
          .add(partition);
    }

    System.out.println("Rebalanced topic '" + topic + "' for group '" + groupId + "':");
    for (Map.Entry<String, List<Integer>> entry : assignments.entrySet()) {
      System.out.println("  " + entry.getKey() + " -> partitions " + entry.getValue());
    }
  }

  /**
   * Get partitions assigned to a consumer
   */
  public List<Integer> getAssignedPartitions(String consumerId) {
    rebalanceLock.readLock().lock();
    try {
      return assignments.getOrDefault(consumerId, Collections.emptyList());
    } finally {
      rebalanceLock.readLock().unlock();
    }
  }

  /**
   * Commit an offset for a partition
   */
  public void commitOffset(int partition, long offset) {
    committedOffsets.put(partition, offset);
  }

  /**
   * Get the committed offset for a partition
   */
  public long getCommittedOffset(int partition) {
    return committedOffsets.getOrDefault(partition, 0L);
  }

  /**
   * Get all committed offsets
   */
  public Map<Integer, Long> getAllCommittedOffsets() {
    return new HashMap<>(committedOffsets);
  }

  /**
   * Check if consumer is registered
   */
  public boolean hasConsumer(String consumerId) {
    return consumers.containsKey(consumerId);
  }

  /**
   * Get consumer count
   */
  public int getConsumerCount() {
    return consumers.size();
  }

  /**
   * Update consumer heartbeat
   */
  public void heartbeat(String consumerId) {
    ConsumerRegistration registration = consumers.get(consumerId);
    if (registration != null) {
      registration.updateHeartbeat();
    }
  }

  /**
   * Remove inactive consumers (heartbeat timeout)
   */
  public void removeInactiveConsumers(long timeoutMs, int totalPartitions) {
    rebalanceLock.writeLock().lock();
    try {
      long now = System.currentTimeMillis();
      List<String> toRemove = new ArrayList<>();

      for (Map.Entry<String, ConsumerRegistration> entry : consumers.entrySet()) {
        if (now - entry.getValue().getLastHeartbeat() > timeoutMs) {
          toRemove.add(entry.getKey());
        }
      }

      if (!toRemove.isEmpty()) {
        for (String consumerId : toRemove) {
          System.out.println("Removing inactive consumer: " + consumerId);
          consumers.remove(consumerId);
          assignments.remove(consumerId);
        }

        // Rebalance if we removed consumers
        rebalance(totalPartitions);
      }

    } finally {
      rebalanceLock.writeLock().unlock();
    }
  }

  public String getGroupId() {
    return groupId;
  }

  public String getTopic() {
    return topic;
  }
}
