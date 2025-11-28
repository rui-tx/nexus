package org.nexus.services;

import io.avaje.inject.Bean;
import io.avaje.inject.Factory;
import org.nexus.domain.TopicConfig;
import org.nexus.embedded.EmbeddedQueueBroker;
import org.nexus.interfaces.QueueBroker;

@Factory
public class QueueConfiguration {

  @Bean
  public QueueBroker queueBroker() {
    QueueBroker broker = new EmbeddedQueueBroker();
    broker.getTopic("pkg.created", new TopicConfig(8, 1, 86400000L, false));
    return broker;
  }

  // Start 4 consumers (in same group, they'll share partitions)
//  @Bean
//  @Named("processor1")
//  public QueueProcessor processor1(QueueBroker broker) {
//    return new QueueProcessor(broker);
//  }

//  @Bean
//  @Named("processor2")
//  public QueueProcessor processor2(QueueBroker broker) {
//    return new QueueProcessor(broker);
//  }
//
//  @Bean
//  @Named("processor3")
//  public QueueProcessor processor3(QueueBroker broker) {
//    return new QueueProcessor(broker);
//  }
//
//  @Bean
//  @Named("processor4")
//  public QueueProcessor processor4(QueueBroker broker) {
//    return new QueueProcessor(broker);
//  }
}