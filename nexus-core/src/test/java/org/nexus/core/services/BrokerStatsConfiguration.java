package org.nexus.core.services;

import io.avaje.inject.Bean;
import io.avaje.inject.Factory;
import org.nexus.queue.embedded.EmbeddedQueueBroker;
import org.nexus.queue.interfaces.QueueBroker;

@Factory
public class BrokerStatsConfiguration {

  @Bean
  public QueueBroker queueBroker() {
    return new EmbeddedQueueBroker();
  }
}