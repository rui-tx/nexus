package org.nexus.domain;

import java.time.Instant;
import java.util.Map;

public record MessageMetadata(
    MessageId id,
    String category,
    String key,
    Instant timestamp,
    int queue,
    long offset,
    Map<String, String> headers
) {

  public MessageMetadata {
    if (id == null) {
      throw new IllegalArgumentException("id cannot be null");
    }
    if (category == null || category.isEmpty()) {
      throw new IllegalArgumentException("category cannot be null or empty");
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