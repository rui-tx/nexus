package org.nexus.messaging.interfaces;

import org.nexus.messaging.domain.MessageMetadata;

public interface MessageHandler<T> {

  void onMessage(T payload, MessageMetadata metadata) throws Exception;
}
