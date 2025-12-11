package org.nexus.messaging.interfaces;

public interface MessageCodec<T> {

  byte[] encode(T value);

  T decode(byte[] bytes);
}
