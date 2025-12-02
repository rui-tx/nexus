package org.nexus;

import static org.nexus.ProtocolVersion.CURRENT_VERSION;
import static org.nexus.ProtocolVersion.VERSION_1;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.nexus.domain.BinaryMessage;

public class Protocol {

  // Protocol constants
  public static final short MAGIC = (short) 0x4E51; // 'NQ' in hex

  // Command domain
  public static final byte CMD_PUBLISH = 0x01;
  public static final byte CMD_SUBSCRIBE = 0x02;
  public static final byte CMD_UNSUBSCRIBE = 0x03;
  public static final byte CMD_ACK = 0x04;
  public static final byte CMD_NACK = 0x05;
  public static final byte CMD_FETCH = 0x06;
  public static final byte CMD_COMMIT_OFFSET = 0x07;
  public static final byte CMD_HEARTBEAT = 0x08;

  // Flags (bit positions)
  public static final int FLAG_COMPRESSED = 0x01;      // Payload is compressed
  public static final int FLAG_PERSISTENT = 0x02;      // Message should be persisted
  public static final int FLAG_PRIORITY_HIGH = 0x04;   // High priority message
  public static final int FLAG_REQUIRES_ACK = 0x08;    // Requires explicit acknowledgment

  // Size constants
  private static final int HEADER_SIZE = 2 + 1 + 1 + 4 + 16 + 8; // 32 bytes fixed header
  private static final int MAX_TOPIC_LENGTH = 255;
  private static final int MAX_KEY_LENGTH = 255;
  private static final int MAX_HEADER_KEY_LENGTH = 255;
  private static final int MAX_HEADER_VALUE_LENGTH = 1024;
  private static final int MAX_PAYLOAD_SIZE = 10 * 1024 * 1024; // 10 MB

  /**
   * Encodes a binary message to wire format
   */
  public static byte[] encode(BinaryMessage message) {
    // Calculate total size
    int size = HEADER_SIZE;

    byte[] topicBytes = message.category().getBytes(StandardCharsets.UTF_8);
    if (topicBytes.length > MAX_TOPIC_LENGTH) {
      throw new IllegalArgumentException("Topic too long: " + topicBytes.length);
    }
    size += 2 + topicBytes.length;

    byte[] keyBytes = message.key() != null ?
        message.key().getBytes(StandardCharsets.UTF_8) : new byte[0];
    if (keyBytes.length > MAX_KEY_LENGTH) {
      throw new IllegalArgumentException("Key too long: " + keyBytes.length);
    }
    size += 2 + keyBytes.length;

    size += 2; // header count
    for (Map.Entry<String, String> entry : message.headers().entrySet()) {
      byte[] headerKey = entry.getKey().getBytes(StandardCharsets.UTF_8);
      byte[] headerValue = entry.getValue().getBytes(StandardCharsets.UTF_8);
      size += 2 + headerKey.length + 2 + headerValue.length;
    }

    if (message.payload().length > MAX_PAYLOAD_SIZE) {
      throw new IllegalArgumentException("Payload too large: " + message.payload().length);
    }
    size += 4 + message.payload().length;

    // Encode
    ByteBuffer buffer = ByteBuffer.allocate(size);

    // Fixed header
    buffer.putShort(MAGIC);
    buffer.put(message.version());
    buffer.put(message.command());
    buffer.putInt(message.flags());
    buffer.putLong(message.messageId().getMostSignificantBits());
    buffer.putLong(message.messageId().getLeastSignificantBits());
    buffer.putLong(message.timestamp());

    // Topic
    buffer.putShort((short) topicBytes.length);
    buffer.put(topicBytes);

    // Key
    buffer.putShort((short) keyBytes.length);
    if (keyBytes.length > 0) {
      buffer.put(keyBytes);
    }

    // Headers
    buffer.putShort((short) message.headers().size());
    for (Map.Entry<String, String> entry : message.headers().entrySet()) {
      byte[] headerKey = entry.getKey().getBytes(StandardCharsets.UTF_8);
      byte[] headerValue = entry.getValue().getBytes(StandardCharsets.UTF_8);
      buffer.putShort((short) headerKey.length);
      buffer.put(headerKey);
      buffer.putShort((short) headerValue.length);
      buffer.put(headerValue);
    }

    // Payload
    buffer.putInt(message.payload().length);
    buffer.put(message.payload());

    return buffer.array();
  }

  /**
   * Decodes wire format to binary message
   */
  public static BinaryMessage decode(byte[] data) {
    ByteBuffer buffer = ByteBuffer.wrap(data);

    // Validate magic
    short magic = buffer.getShort();
    if (magic != MAGIC) {
      throw new IllegalArgumentException(
          String.format("Invalid magic number: 0x%04X (expected 0x%04X)", magic, MAGIC)
      );
    }

    // Fixed header
    byte version = buffer.get();
    byte command = buffer.get();
    int flags = buffer.getInt();
    long mostSigBits = buffer.getLong();
    long leastSigBits = buffer.getLong();
    UUID messageId = new UUID(mostSigBits, leastSigBits);
    long timestamp = buffer.getLong();

    // Topic
    short topicLength = buffer.getShort();
    byte[] topicBytes = new byte[topicLength];
    buffer.get(topicBytes);
    String topic = new String(topicBytes, StandardCharsets.UTF_8);

    // Key
    short keyLength = buffer.getShort();
    String key = null;
    if (keyLength > 0) {
      byte[] keyBytes = new byte[keyLength];
      buffer.get(keyBytes);
      key = new String(keyBytes, StandardCharsets.UTF_8);
    }

    // Headers
    short headerCount = buffer.getShort();
    Map<String, String> headers = new HashMap<>();
    for (int i = 0; i < headerCount; i++) {
      short headerKeyLength = buffer.getShort();
      byte[] headerKeyBytes = new byte[headerKeyLength];
      buffer.get(headerKeyBytes);
      String headerKey = new String(headerKeyBytes, StandardCharsets.UTF_8);

      short headerValueLength = buffer.getShort();
      byte[] headerValueBytes = new byte[headerValueLength];
      buffer.get(headerValueBytes);
      String headerValue = new String(headerValueBytes, StandardCharsets.UTF_8);

      headers.put(headerKey, headerValue);
    }

    // Payload
    int payloadLength = buffer.getInt();
    byte[] payload = new byte[payloadLength];
    buffer.get(payload);

    return new BinaryMessage(
        version, command, flags, messageId, timestamp,
        topic, key, headers, payload
    );
  }

  /**
   * Version-aware decoder that can handle protocol evolution
   */
  public static BinaryMessage decodeVersioned(byte[] data) {
    if (data.length < 3) {
      throw new IllegalArgumentException("Data too short");
    }

    // Peek at version without consuming
    byte version = data[2]; // After magic (2 bytes)

    return switch (version) {
      case VERSION_1 -> decode(data);
      // case VERSION_2 -> decodeV2(data);
      default -> throw new IllegalArgumentException(
          "Unsupported protocol version: " + version
      );
    };
  }

  /**
   * Builder for creating binary messages
   */
  public static class MessageBuilder {

    private byte version = CURRENT_VERSION;
    private byte command = CMD_PUBLISH;
    private int flags = 0;
    private UUID messageId = UUID.randomUUID();
    private long timestamp = System.currentTimeMillis();
    private String topic;
    private String key;
    private Map<String, String> headers = new HashMap<>();
    private byte[] payload;

    public MessageBuilder version(byte version) {
      this.version = version;
      return this;
    }

    public MessageBuilder command(byte command) {
      this.command = command;
      return this;
    }

    public MessageBuilder flags(int flags) {
      this.flags = flags;
      return this;
    }

    public MessageBuilder addFlag(int flag) {
      this.flags |= flag;
      return this;
    }

    public MessageBuilder messageId(UUID messageId) {
      this.messageId = messageId;
      return this;
    }

    public MessageBuilder timestamp(long timestamp) {
      this.timestamp = timestamp;
      return this;
    }

    public MessageBuilder topic(String topic) {
      this.topic = topic;
      return this;
    }

    public MessageBuilder key(String key) {
      this.key = key;
      return this;
    }

    public MessageBuilder header(String key, String value) {
      this.headers.put(key, value);
      return this;
    }

    public MessageBuilder headers(Map<String, String> headers) {
      this.headers.putAll(headers);
      return this;
    }

    public MessageBuilder payload(byte[] payload) {
      this.payload = payload;
      return this;
    }

    public BinaryMessage build() {
      return new BinaryMessage(
          version, command, flags, messageId, timestamp,
          topic, key, headers, payload
      );
    }
  }
}