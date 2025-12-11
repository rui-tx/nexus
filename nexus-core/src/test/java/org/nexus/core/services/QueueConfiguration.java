package org.nexus.core.services;

import io.avaje.inject.Bean;
import io.avaje.inject.Factory;
import org.nexus.queue.domain.CategoryConfig;
import org.nexus.queue.embedded.EmbeddedQueueBroker;
import org.nexus.queue.interfaces.QueueBroker;

@Factory
public class QueueConfiguration {

  @Bean
  public QueueBroker queueBroker() {
    QueueBroker broker = new EmbeddedQueueBroker();
    broker.getOrCreateCategory("pkg.created", new CategoryConfig(8, 1, 86400000L, true));
    return broker;
  }

  // Start consumers (in same group, they'll share queues)
//  @Bean
//  @Named("processor1")
//  public QueueProcessor processor1(QueueBroker broker) {
//    return new QueueProcessor(broker);
//  }
}