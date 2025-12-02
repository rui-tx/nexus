package org.nexus.domain;

import java.time.Instant;

/**
 * A message stored in a queue with its offset
 */
public record StoredMessage(
    long offset,
    MessageMetadata metadata,
    byte[] payload,
    Instant timestamp
) {

  public StoredMessage {
    if (metadata == null) {
      throw new IllegalArgumentException("metadata cannot be null");
    }
    if (payload == null) {
      throw new IllegalArgumentException("payload cannot be null");
    }
    if (timestamp == null) {
      throw new IllegalArgumentException("timestamp cannot be null");
    }
  }
}
