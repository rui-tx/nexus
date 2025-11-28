package org.nexus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.nexus.domain.MessageMetadata;
import org.nexus.domain.PartitionStats;
import org.nexus.domain.StoredMessage;
import org.nexus.domain.TopicConfig;
import org.nexus.exceptions.QueueFullException;

/**
 * A single partition within a topic. Stores messages in order and tracks offsets.
 */
public class Partition {

  private final int partitionId;
  private final TopicConfig config;

  // Message storage (in-memory queue)
  private final BlockingQueue<StoredMessage> messages;

  // Offset tracking
  private final AtomicLong nextOffset = new AtomicLong(0);
  private final AtomicLong oldestOffset = new AtomicLong(0);

  // Size tracking
  private final AtomicLong sizeBytes = new AtomicLong(0);
  private final AtomicLong messageCount = new AtomicLong(0);

  // For cleaning old messages
  private final ReadWriteLock lock = new ReentrantReadWriteLock();

  public Partition(int partitionId, TopicConfig config) {
    this.partitionId = partitionId;
    this.config = config;
    this.messages = new LinkedBlockingQueue<>(50000);
  }

  /**
   * Append a message to this partition
   */
  public long append(byte[] payload, MessageMetadata metadata) {
    long offset = nextOffset.getAndIncrement();

    StoredMessage stored = new StoredMessage(
        offset,
        metadata,
        payload,
        Instant.now()
    );

    lock.readLock().lock();
    try {
      // Add to queue
      if (!messages.offer(stored)) {
        throw new QueueFullException(
            "Partition " + partitionId + " is full"
        );
      }

      // Update metrics
      sizeBytes.addAndGet(payload.length);
      messageCount.incrementAndGet();

      return offset;

    } finally {
      lock.readLock().unlock();
    }
  }

  /**
   * Read messages starting from an offset
   */
  public List<StoredMessage> read(long fromOffset, int maxMessages) {
    List<StoredMessage> result = new ArrayList<>();

    lock.readLock().lock();
    try {
      for (StoredMessage msg : messages) {
        if (msg.offset() >= fromOffset) {
          result.add(msg);
          if (result.size() >= maxMessages) {
            break;
          }
        }
      }
      return result;

    } finally {
      lock.readLock().unlock();
    }
  }

  /**
   * Get the latest offset (for consumers to start at "end")
   */
  public long getLatestOffset() {
    return nextOffset.get();
  }

  /**
   * Get the oldest available offset
   */
  public long getOldestOffset() {
    return oldestOffset.get();
  }

  /**
   * Clean up old messages based on retention policy
   */
  public void cleanup() {
    lock.writeLock().lock();
    try {
      long retentionMs = config.retentionMs();
      long cutoffTime = System.currentTimeMillis() - retentionMs;

      // Remove messages older than retention
      while (!messages.isEmpty()) {
        StoredMessage oldest = messages.peek();
        if (oldest == null) {
          break;
        }

        if (oldest.timestamp().toEpochMilli() < cutoffTime) {
          StoredMessage removed = messages.poll();
          if (removed != null) {
            sizeBytes.addAndGet(-removed.payload().length);
            messageCount.decrementAndGet();
            oldestOffset.set(removed.offset() + 1);
          }
        } else {
          break; // Messages are in order, no need to continue
        }
      }

    } finally {
      lock.writeLock().unlock();
    }
  }

  /**
   * Remove messages that have been consumed by all consumer groups
   */
  public void removeConsumedMessages(long minCommittedOffset) {
    lock.writeLock().lock();
    try {
      while (!messages.isEmpty()) {
        StoredMessage oldest = messages.peek();
        if (oldest == null) {
          break;
        }

        // If all consumers have passed this offset, remove it
        if (oldest.offset() < minCommittedOffset) {
          StoredMessage removed = messages.poll();
          if (removed != null) {
            sizeBytes.addAndGet(-removed.payload().length);
            messageCount.decrementAndGet();
            oldestOffset.set(removed.offset() + 1);
          }
        } else {
          break; // Can't remove yet
        }
      }
    } finally {
      lock.writeLock().unlock();
    }
  }

  /**
   * Get statistics for this partition
   */
  public PartitionStats getStats() {
    return new PartitionStats(
        partitionId,
        messageCount.get(),
        oldestOffset.get(),
        nextOffset.get() - 1, // Last assigned offset
        sizeBytes.get()
    );
  }

  /**
   * Get partition ID
   */
  public int getId() {
    return partitionId;
  }

  /**
   * Get current size in bytes
   */
  public long getSizeBytes() {
    return sizeBytes.get();
  }

  /**
   * Get message count
   */
  public long getMessageCount() {
    return messageCount.get();
  }
}
