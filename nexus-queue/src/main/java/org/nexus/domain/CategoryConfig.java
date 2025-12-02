package org.nexus.domain;

/**
 * Category configuration
 */
public record CategoryConfig(
    int queues,
    int replicationFactor,
    long retentionMs,
    boolean persistent
) {

  public static CategoryConfig defaults() {
    return new CategoryConfig(
        1,            // Single queue by default
        1,                      // No replication in embedded mode
        24 * 60 * 60 * 1000L,   // 24 hours retention
        false                   // In-memory by default
    );
  }
}

