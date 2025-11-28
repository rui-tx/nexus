package org.nexus.domain;

import java.time.Instant;
import java.util.Map;

/**
 * Metadata about a message
 */
public record MessageMetadata(
    MessageId id,
    String topic,
    String key,
    Instant timestamp,
    int partition,
    long offset,
    Map<String, String> headers
) {

  public MessageMetadata {
    if (id == null) {
      throw new IllegalArgumentException("id cannot be null");
    }
    if (topic == null || topic.isEmpty()) {
      throw new IllegalArgumentException("topic cannot be null or empty");
    }
  }

  public static MessageMetadata create(String topic) {
    return new MessageMetadata(
        MessageId.generate(),
        topic,
        null,
        Instant.now(),
        0,
        -1L,
        Map.of()
    );
  }

  public static MessageMetadata create(String topic, String key) {
    return new MessageMetadata(
        MessageId.generate(),
        topic,
        key,
        Instant.now(),
        0,
        -1L,
        Map.of()
    );
  }
}