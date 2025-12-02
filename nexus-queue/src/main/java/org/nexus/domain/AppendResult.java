package org.nexus.domain;

/**
 * Result of appending a message to a queue.
 */
public record AppendResult(
    MessageId messageId,
    int queueId,
    long offset
) {

  public AppendResult {
    if (messageId == null) {
      throw new IllegalArgumentException("messageId cannot be null");
    }
    if (queueId < 0) {
      throw new IllegalArgumentException("queueId must be non-negative");
    }
    if (offset < 0) {
      throw new IllegalArgumentException("offset must be non-negative");
    }
  }
}