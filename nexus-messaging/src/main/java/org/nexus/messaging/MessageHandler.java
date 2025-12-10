package org.nexus.messaging;

public interface MessageHandler<T> {

  void onMessage(T payload, MessageMetadata metadata) throws Exception;
}
