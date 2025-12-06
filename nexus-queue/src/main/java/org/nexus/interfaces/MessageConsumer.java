package org.nexus.interfaces;

import java.util.concurrent.CompletableFuture;

/**
 * Consumer interface for receiving messages
 */
public interface MessageConsumer<T> {

  /**
   * Subscribe to a category with a handler
   */
  void subscribe(String category, MessageHandler<T> handler);

  /**
   * Subscribe to multiple categories
   */
  void subscribe(String[] categories, MessageHandler<T> handler);

  /**
   * Unsubscribe from a category
   */
  void unsubscribe(String category);

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
