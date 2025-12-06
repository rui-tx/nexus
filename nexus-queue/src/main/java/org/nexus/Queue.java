package org.nexus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.nexus.domain.CategoryConfig;
import org.nexus.domain.MessageId;
import org.nexus.domain.MessageInput;
import org.nexus.domain.MessageMetadata;
import org.nexus.domain.QueueCapacityConfig;
import org.nexus.domain.QueueStats;
import org.nexus.domain.StoredMessage;
import org.nexus.exceptions.QueueFullException;

/**
 * A single queue within a category. Stores messages in order and tracks offsets.
 */
public class Queue {

  private final int queueId;
  private final CategoryConfig config;
  private final QueueCapacityConfig capacityConfig;

  // Message storage, indexed by offset
  private final ConcurrentSkipListMap<Long, StoredMessage> messages;

  // Offset tracking
  private final AtomicLong nextOffset = new AtomicLong(0);
  private final AtomicLong oldestOffset = new AtomicLong(0);

  // Size tracking
  private final AtomicLong sizeBytes = new AtomicLong(0);
  private final AtomicLong messageCount = new AtomicLong(0);

  // For cleaning old messages
  private final ReadWriteLock lock = new ReentrantReadWriteLock();

  public Queue(int queueId, CategoryConfig config) {
    this(queueId, config, QueueCapacityConfig.defaultConfig());
  }

  public Queue(int queueId, CategoryConfig config, QueueCapacityConfig capacityConfig) {
    this.queueId = queueId;
    this.config = config;
    this.capacityConfig = capacityConfig;
    this.messages = new ConcurrentSkipListMap<>();
  }

  /**
   * Append a message to this queue.
   */
  public MessageMetadata append(byte[] payload, MessageInput input) {
    if (payload == null || payload.length == 0) {
      throw new IllegalArgumentException("payload cannot be null or empty");
    }
    lock.writeLock().lock();
    try {
      // Check capacity BEFORE allocating offset
      long currentMessages = messageCount.get();
      long currentBytes = sizeBytes.get();

      if (capacityConfig.wouldExceedCapacity(currentMessages, currentBytes, payload.length)) {
        throw new QueueFullException(
            String.format("Queue %d is full (messages: %d/%d, bytes: %d/%d)",
                queueId, currentMessages, capacityConfig.maxMessages(),
                currentBytes, capacityConfig.maxSizeBytes())
        );
      }

      long offset = nextOffset.getAndIncrement();
      MessageMetadata metadata = new MessageMetadata(
          MessageId.generate(),
          input.category(),
          input.key(),
          Instant.now(),
          queueId,
          offset,
          input.headers()
      );

      StoredMessage stored = new StoredMessage(
          offset,
          metadata,
          payload,
          metadata.timestamp()
      );

      // Store and update message metrics
      messages.put(offset, stored);
      sizeBytes.addAndGet(payload.length);
      messageCount.incrementAndGet();

      return metadata;
    } finally {
      lock.writeLock().unlock();
    }
  }

  /**
   * Read messages starting from an offset.
   */
  public List<StoredMessage> read(long fromOffset, int maxMessages) {
    if (maxMessages <= 0) {
      return List.of();
    }

    List<StoredMessage> result = new ArrayList<>(Math.min(maxMessages, 100));

    Map<Long, StoredMessage> tail = messages.tailMap(fromOffset);

    int count = 0;
    for (StoredMessage msg : tail.values()) {
      result.add(msg);
      if (++count >= maxMessages) {
        break;
      }
    }

    return result;
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
   * Clean up old messages based on retention policy. Uses write lock to prevent concurrent
   * modifications during cleanup.
   */
  public void cleanup() {
    lock.writeLock().lock();
    try {
      long retentionMs = config.retentionMs();
      long cutoffTime = System.currentTimeMillis() - retentionMs;

      // Iterate and remove old messages
      Iterator<Map.Entry<Long, StoredMessage>> iterator = messages.entrySet().iterator();

      while (iterator.hasNext()) {
        Map.Entry<Long, StoredMessage> entry = iterator.next();
        StoredMessage msg = entry.getValue();

        if (msg.timestamp().toEpochMilli() < cutoffTime) {
          iterator.remove();

          // Update metrics
          sizeBytes.addAndGet(-msg.payload().length);
          messageCount.decrementAndGet();
          oldestOffset.set(entry.getKey() + 1);
        } else {
          // Messages are ordered by offset (and typically by time)
          // Once we hit a message that's not expired, we can stop
          break;
        }
      }

    } finally {
      lock.writeLock().unlock();
    }
  }

  /**
   * Remove messages that have been consumed by all consumer groups. This helps free up memory for
   * messages that no consumer needs anymore.
   */
  public void removeConsumedMessages(long minCommittedOffset) {
    lock.writeLock().lock();
    try {
      // headMap gives us all entries < minCommittedOffset
      Map<Long, StoredMessage> toRemove = messages.headMap(minCommittedOffset);

      if (toRemove.isEmpty()) {
        return;
      }

      // Remove all consumed messages
      Iterator<Map.Entry<Long, StoredMessage>> iterator = toRemove.entrySet().iterator();

      while (iterator.hasNext()) {
        Map.Entry<Long, StoredMessage> entry = iterator.next();
        StoredMessage msg = entry.getValue();

        iterator.remove();

        // Update metrics
        sizeBytes.addAndGet(-msg.payload().length);
        messageCount.decrementAndGet();
        oldestOffset.set(entry.getKey() + 1);
      }

    } finally {
      lock.writeLock().unlock();
    }
  }

  /**
   * Get statistics for this queue
   */
  public QueueStats getStats() {
    long count = messageCount.get();
    long oldest = oldestOffset.get();
    long newest = nextOffset.get() - 1; // Last assigned offset

    return new QueueStats(
        queueId,
        count,
        oldest,
        newest,
        sizeBytes.get()
    );
  }

  /**
   * Get queue ID
   */
  public int getId() {
    return queueId;
  }

  /**
   * Get the current size in bytes
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

  /**
   * Get capacity configuration
   */
  public QueueCapacityConfig getCapacityConfig() {
    return capacityConfig;
  }
}