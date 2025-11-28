package org.nexus.domain;

/**
 * Statistics about a partition
 */
public record PartitionStats(
    int partition,
    long messageCount,
    long oldestOffset,
    long newestOffset,
    long sizeBytes
) {

}
