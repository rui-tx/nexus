package org.nexus.domain;

/**
 * Topic configuration
 */
public record TopicConfig(
    int partitions,
    int replicationFactor,
    long retentionMs,
    boolean persistent
) {

  public static TopicConfig defaults() {
    return new TopicConfig(
        1,            // Single partition by default
        1,                      // No replication in embedded mode
        24 * 60 * 60 * 1000L,   // 24 hours retention
        false                   // In-memory by default
    );
  }
}

