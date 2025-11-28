package org.nexus.interfaces;

import java.util.concurrent.CompletableFuture;
import org.nexus.domain.ConsumerConfig;
import org.nexus.domain.ProducerConfig;
import org.nexus.domain.TopicConfig;


/**
 * Core queue broker interface
 */
public interface QueueBroker {

  /**
   * Create a producer for a specific type
   */
  <T> MessageProducer<T> createProducer(ProducerConfig config);

  /**
   * Create a producer with default config
   */
  default <T> MessageProducer<T> createProducer() {
    return createProducer(ProducerConfig.defaults());
  }

  /**
   * Create a consumer for a specific type
   */
  <T> MessageConsumer<T> createConsumer(ConsumerConfig config);

  /**
   * Create a consumer with default config
   */
  default <T> MessageConsumer<T> createConsumer(String consumerGroup) {
    return createConsumer(ConsumerConfig.defaults(consumerGroup));
  }

  /**
   * Create or get a topic
   *
   * @return the Topic interface
   */
  Topic getTopic(String name, TopicConfig config);

  /**
   * Delete a topic
   */
  CompletableFuture<Void> deleteTopic(String name);

  /**
   * List all topics
   */
  String[] listTopics();

  /**
   * Shutdown the broker
   */
  CompletableFuture<Void> shutdown();
}
