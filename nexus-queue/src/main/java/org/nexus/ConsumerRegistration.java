package org.nexus;

import org.nexus.domain.ConsumerConfig;

/**
 * Registration information for a consumer
 */
class ConsumerRegistration {

  private final String consumerId;
  private final ConsumerConfig config;
  private volatile long lastHeartbeat;

  public ConsumerRegistration(String consumerId, ConsumerConfig config, long timestamp) {
    this.consumerId = consumerId;
    this.config = config;
    this.lastHeartbeat = timestamp;
  }

  public void updateHeartbeat() {
    this.lastHeartbeat = System.currentTimeMillis();
  }

  public long getLastHeartbeat() {
    return lastHeartbeat;
  }

  public String getConsumerId() {
    return consumerId;
  }

  public ConsumerConfig getConfig() {
    return config;
  }
}
