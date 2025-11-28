package org.nexus.domain;

import java.time.Instant;

/**
 * A message in the queue system
 */
public record Message<T>(
    MessageMetadata metadata,
    T payload
) {

  public Message {
    if (metadata == null) {
      throw new IllegalArgumentException("metadata cannot be null");
    }
    if (payload == null) {
      throw new IllegalArgumentException("payload cannot be null");
    }
  }

  public MessageId id() {
    return metadata.id();
  }

  public String topic() {
    return metadata.topic();
  }

  public String key() {
    return metadata.key();
  }

  public Instant timestamp() {
    return metadata.timestamp();
  }
}