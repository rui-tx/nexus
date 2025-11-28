package org.nexus.interfaces;

import java.util.concurrent.CompletableFuture;

/**
 * Consumer interface for receiving messages
 */
public interface MessageConsumer<T> {

  /**
   * Subscribe to a topic with a handler
   */
  void subscribe(String topic, MessageHandler<T> handler);

  /**
   * Subscribe to multiple topics
   */
  void subscribe(String[] topics, MessageHandler<T> handler);

  /**
   * Unsubscribe from a topic
   */
  void unsubscribe(String topic);

  /**
   * Unsubscribe from all topics
   */
  void unsubscribeAll();

  /**
   * Manually commit offsets (for non-auto-commit mode)
   */
  CompletableFuture<Void> commitSync();

  /**
   * Pause consumption
   */
  void pause();

  /**
   * Resume consumption
   */
  void resume();

  /**
   * Close the consumer
   */
  CompletableFuture<Void> close();
}
