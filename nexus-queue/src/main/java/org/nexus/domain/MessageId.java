package org.nexus.domain;

import java.util.UUID;

/**
 * Unique identifier for a message
 */
public record MessageId(UUID value) {

  public MessageId {
    if (value == null) {
      throw new IllegalArgumentException("MessageId cannot be null");
    }
  }

  public static MessageId generate() {
    return new MessageId(UUID.randomUUID());
  }

  @Override
  public String toString() {
    return value.toString();
  }
}
