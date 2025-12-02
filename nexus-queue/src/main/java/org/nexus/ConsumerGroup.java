package org.nexus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.nexus.domain.ConsumerConfig;

public class ConsumerGroup {

  private static final long REBALANCE_DEBOUNCE_MS = 1000L;
  private final String groupId;
  private final String category;
  private final Map<String, ConsumerRegistration> consumers = new ConcurrentHashMap<>();
  private final Map<String, List<Integer>> assignments = new ConcurrentHashMap<>();
  private final Map<Integer, Long> committedOffsets = new ConcurrentHashMap<>();
  private final ReadWriteLock rebalanceLock = new ReentrantReadWriteLock();
  private final ScheduledExecutorService rebalanceScheduler;
  private volatile ScheduledFuture<?> pendingRebalance = null;

  public ConsumerGroup(String groupId, String category) {
    this.groupId = groupId;
    this.category = category;
    this.rebalanceScheduler =
        Executors.newSingleThreadScheduledExecutor(r -> {
          Thread t = new Thread(r, "cg-rebalance");
          t.setDaemon(true);
          return t;
        });
  }

  public void registerConsumer(String consumerId, ConsumerConfig config, int totalQueues) {
    rebalanceLock.writeLock().lock();
    try {
      consumers.put(consumerId,
          new ConsumerRegistration(consumerId, config, System.currentTimeMillis()));
    } finally {
      rebalanceLock.writeLock().unlock();
    }
    scheduleRebalance(totalQueues);
  }

  public void unregisterConsumer(String consumerId, int totalQueues) {
    rebalanceLock.writeLock().lock();
    try {
      consumers.remove(consumerId);
      assignments.remove(consumerId);
    } finally {
      rebalanceLock.writeLock().unlock();
    }
    if (!consumers.isEmpty()) {
      scheduleRebalance(totalQueues);
    }
  }

  private void scheduleRebalance(int totalQueues) {
    if (pendingRebalance != null && !pendingRebalance.isDone()) {
      pendingRebalance.cancel(false);
    }
    pendingRebalance = rebalanceScheduler.schedule(
        () -> rebalance(totalQueues),
        REBALANCE_DEBOUNCE_MS,
        TimeUnit.MILLISECONDS
    );
  }

  private void rebalance(int totalQueues) {
    rebalanceLock.writeLock().lock();
    try {
      assignments.clear();

      if (consumers.isEmpty()) {
        return;
      }

      List<String> consumerIds = new ArrayList<>(consumers.keySet());
      Collections.sort(consumerIds); // deterministic order

      for (int queue = 0; queue < totalQueues; queue++) {
        String consumer = consumerIds.get(queue % consumerIds.size());
        assignments.computeIfAbsent(consumer, _ -> new ArrayList<>()).add(queue);
      }

      System.out.println("Rebalanced category '" + category + "' for group '" + groupId + "':");
      assignments.forEach((c, queues) ->
          System.out.println("  " + c + " -> queues " + queues));

    } finally {
      rebalanceLock.writeLock().unlock();
    }
  }

  public void removeInactiveConsumers(long timeoutMs, int totalQueues) {
    rebalanceLock.writeLock().lock();
    try {
      long now = System.currentTimeMillis();
      List<String> toRemove = new ArrayList<>();

      for (ConsumerRegistration reg : consumers.values()) {
        if (now - reg.getLastHeartbeat() > timeoutMs) {
          toRemove.add(reg.getConsumerId());
        }
      }

      if (!toRemove.isEmpty()) {
        for (String id : toRemove) {
          System.out.println("Removing inactive consumer: " + id);
          consumers.remove(id);
          assignments.remove(id);
        }
        // rebalance only after all removals
        if (!consumers.isEmpty()) {
          scheduleRebalance(totalQueues);
        }
      }
    } finally {
      rebalanceLock.writeLock().unlock();
    }
  }

  // heartbeat just updates timestamp – no rebalance
  public void heartbeat(String consumerId) {
    ConsumerRegistration reg = consumers.get(consumerId);
    if (reg != null) {
      reg.updateHeartbeat();
    }
  }

  // unchanged methods below
  public List<Integer> getAssignedQueues(String consumerId) {
    rebalanceLock.readLock().lock();
    try {
      return List.copyOf(assignments.getOrDefault(consumerId, Collections.emptyList()));
    } finally {
      rebalanceLock.readLock().unlock();
    }
  }

  public void commitOffset(int partition, long offset) {
    committedOffsets.put(partition, offset);
  }

  public long getCommittedOffset(int partition) {
    return committedOffsets.getOrDefault(partition, 0L);
  }

  public Map<Integer, Long> getAllCommittedOffsets() {
    return Map.copyOf(committedOffsets);
  }

  public int getConsumerCount() {
    return consumers.size();
  }

  public void shutdown() {
    if (rebalanceScheduler != null) {
      rebalanceScheduler.shutdownNow();
    }
  }
}