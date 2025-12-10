package org.nexus.messaging;

public interface MessagePublisher<T> extends AutoCloseable {

  void send(String key, T payload);

  void send(String key, T payload, MessageHeaders headers);

  @Override
  void close();
}
