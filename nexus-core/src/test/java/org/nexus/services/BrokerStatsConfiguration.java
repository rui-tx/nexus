package org.nexus.services;

import io.avaje.inject.Bean;
import io.avaje.inject.Factory;
import org.nexus.embedded.EmbeddedQueueBroker;
import org.nexus.interfaces.QueueBroker;

@Factory
public class BrokerStatsConfiguration {

  @Bean
  public QueueBroker queueBroker() {
    return new EmbeddedQueueBroker();
  }
}