package org.nexus.domain;

/**
 * Statistics about a queue
 */
public record QueueStats(
    int queue,
    long messageCount,
    long oldestOffset,
    long newestOffset,
    long sizeBytes
) {

}
