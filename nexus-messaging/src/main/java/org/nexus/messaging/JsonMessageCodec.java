package org.nexus.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;

public final class JsonMessageCodec<T> implements MessageCodec<T> {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final Class<T> type;

  public JsonMessageCodec(Class<T> type) {
    this.type = type;
  }

  @Override
  public byte[] encode(T value) {
    try {
      return MAPPER.writeValueAsBytes(value);
    } catch (Exception e) {
      throw new RuntimeException("Failed to encode message as JSON", e);
    }
  }

  @Override
  public T decode(byte[] bytes) {
    try {
      return MAPPER.readValue(bytes, type);
    } catch (Exception e) {
      throw new RuntimeException("Failed to decode JSON message", e);
    }
  }
}
