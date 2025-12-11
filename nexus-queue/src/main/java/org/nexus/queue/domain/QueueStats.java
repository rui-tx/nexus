package org.nexus.queue.domain;

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
