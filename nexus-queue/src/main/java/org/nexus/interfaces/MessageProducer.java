package org.nexus.interfaces;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.nexus.domain.PublishResult;

/**
 * Producer interface for publishing messages
 */
public interface MessageProducer<T> {

  /**
   * Send a message to a topic
   */
  CompletableFuture<PublishResult> send(String topic, T payload);

  /**
   * Send a message with a specific key (for partitioning)
   */
  CompletableFuture<PublishResult> send(String topic, String key, T payload);

  /**
   * Send a message with custom headers
   */
  CompletableFuture<PublishResult> send(
      String topic,
      String key,
      T payload,
      Map<String, String> headers
  );

  /**
   * Flush any buffered messages
   */
  CompletableFuture<Void> flush();

  /**
   * Close the producer
   */
  CompletableFuture<Void> close();
}
