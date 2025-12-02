package org.nexus;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Tracks metrics for a category
 */
public class MetricsTracker {

  private final AtomicLong totalMessages = new AtomicLong(0);
  private final AtomicLong bytesIn = new AtomicLong(0);
  private final AtomicLong bytesOut = new AtomicLong(0);

  // For calculating messages/sec
  private volatile long lastMessageCount = 0;
  private volatile long lastTimestamp = System.currentTimeMillis();

  public void recordMessage(int payloadSize) {
    totalMessages.incrementAndGet();
    bytesIn.addAndGet(payloadSize);
  }

  public void recordRead(int payloadSize) {
    bytesOut.addAndGet(payloadSize);
  }

  public long getTotalMessages() {
    return totalMessages.get();
  }

  public long getBytesIn() {
    return bytesIn.get();
  }

  public long getBytesOut() {
    return bytesOut.get();
  }

  public double getMessagesPerSecond() {
    long currentCount = totalMessages.get();
    long currentTime = System.currentTimeMillis();

    long messagesDiff = currentCount - lastMessageCount;
    long timeDiff = currentTime - lastTimestamp;

    if (timeDiff == 0) {
      return 0.0;
    }

    double rate = (messagesDiff * 1000.0) / timeDiff;

    // Update for next calculation
    lastMessageCount = currentCount;
    lastTimestamp = currentTime;

    return rate;
  }
}
