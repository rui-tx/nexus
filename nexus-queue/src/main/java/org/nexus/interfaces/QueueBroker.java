package org.nexus.interfaces;

import java.util.concurrent.CompletableFuture;
import org.nexus.domain.CategoryConfig;
import org.nexus.domain.ConsumerConfig;
import org.nexus.domain.ProducerConfig;


/**
 * Core queue broker interface
 */
public interface QueueBroker {

  /**
   * Create a producer for a specific type
   */
  <T> MessageProducer<T> createProducer(ProducerConfig config);

  /**
   * Create a producer with a default config
   */
  default <T> MessageProducer<T> createProducer() {
    return createProducer(ProducerConfig.defaults());
  }

  /**
   * Create a consumer for a specific type
   */
  <T> MessageConsumer<T> createConsumer(ConsumerConfig config);

  /**
   * Create a consumer with a default config
   */
  default <T> MessageConsumer<T> createConsumer(String consumerGroup) {
    return createConsumer(ConsumerConfig.defaults(consumerGroup));
  }

  /**
   * Create or get a category
   *
   * @return the Category interface
   */
  Category getOrCreateCategory(String name, CategoryConfig config);

  /**
   * Delete a category
   */
  CompletableFuture<Void> deleteCategory(String name);

  /**
   * List all categories
   */
  String[] listCategories();

  /**
   * Shutdown the broker
   */
  CompletableFuture<Void> shutdown();
}
