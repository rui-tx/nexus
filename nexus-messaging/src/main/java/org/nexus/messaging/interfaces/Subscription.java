package org.nexus.messaging.interfaces;

public interface Subscription extends AutoCloseable {

  void start();

  void stop();

  @Override
  void close();
}
