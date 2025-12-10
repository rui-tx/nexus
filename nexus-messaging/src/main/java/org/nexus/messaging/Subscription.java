package org.nexus.messaging;

public interface Subscription extends AutoCloseable {

  void start();

  void stop();

  @Override
  void close();
}
