package org.nexus.domain;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.nexus.ProtocolVersion;

/**
 * Represents a message in the Nexus Queue Protocol
 */
public record BinaryMessage(
    byte version,
    byte command,
    int flags,
    UUID messageId,
    long timestamp,
    String topic,
    String key,
    Map<String, String> headers,
    byte[] payload
) {

  public BinaryMessage {
    if (version < ProtocolVersion.VERSION_1) {
      throw new IllegalArgumentException("Invalid protocol version: " + version);
    }
    if (topic == null || topic.isEmpty()) {
      throw new IllegalArgumentException("Topic cannot be null or empty");
    }
    if (payload == null) {
      throw new IllegalArgumentException("Payload cannot be null");
    }
  }

  public boolean hasFlag(int flag) {
    return (flags & flag) != 0;
  }

  public Instant timestampAsInstant() {
    return Instant.ofEpochMilli(timestamp);
  }
}
