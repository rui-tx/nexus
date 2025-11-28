package org.nexus.domain;

import java.util.UUID;

/**
 * Configuration for message consumers
 */
public record ConsumerConfig(
    String clientId,
    String consumerGroup,
    boolean autoCommit,
    long autoCommitIntervalMs,
    int maxPollRecords
) {

  public static ConsumerConfig defaults(String consumerGroup) {
    return new ConsumerConfig(
        "consumer-" + UUID.randomUUID(),
        consumerGroup,
        true,
        5000,
        100
    );
  }
}
