package org.nexus.services;

import io.avaje.inject.Bean;
import io.avaje.inject.Factory;
import org.nexus.domain.CategoryConfig;
import org.nexus.embedded.EmbeddedQueueBroker;
import org.nexus.interfaces.QueueBroker;

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