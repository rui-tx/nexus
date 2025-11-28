package org.nexus.domain;

import java.util.UUID;

public record ProducerConfig(
    String clientId,
    boolean requiresAck,
    boolean persistent,
    int retryAttempts,
    long retryBackoffMs
) {

  public static ProducerConfig defaults() {
    return new ProducerConfig(
        "producer-" + UUID.randomUUID(),
        false,
        false,
        3,
        100
    );
  }
}