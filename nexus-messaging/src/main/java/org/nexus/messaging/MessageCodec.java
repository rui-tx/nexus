package org.nexus.messaging;

public interface MessageCodec<T> {

  byte[] encode(T value);

  T decode(byte[] bytes);
}
