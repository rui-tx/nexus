package org.nexus.domain;

import java.util.Map;

/**
 * Statistics about a category
 */
public record CategoryStats(
    String name,
    long messageCount,
    long bytesIn,
    long bytesOut,
    double messagesPerSecond,
    Map<Integer, QueueStats> queueStats
) {

}
