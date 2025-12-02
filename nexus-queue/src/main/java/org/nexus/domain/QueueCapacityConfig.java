package org.nexus.domain;

/**
 * Configuration for queue capacity limits
 */
public record QueueCapacityConfig(
    int maxMessages,
    long maxSizeBytes
) {

  public QueueCapacityConfig {
    if (maxMessages <= 0) {
      throw new IllegalArgumentException("maxMessages must be positive");
    }
    if (maxSizeBytes <= 0) {
      throw new IllegalArgumentException("maxSizeBytes must be positive");
    }
  }

  /**
   * Default configuration: 50K messages, 1GB size limit
   */
  public static QueueCapacityConfig defaultConfig() {
    return new QueueCapacityConfig(50_000, 1_073_741_824L); // 1GB
  }

  /**
   * Check if adding a message would exceed capacity
   */
  public boolean wouldExceedCapacity(long currentMessages, long currentBytes, int newMessageBytes) {
    return currentMessages >= maxMessages || (currentBytes + newMessageBytes) > maxSizeBytes;
  }
}
