package org.nexus.domain;

import java.util.Map;

/**
 * Statistics about a topic
 */
public record TopicStats(
    String name,
    long messageCount,
    long bytesIn,
    long bytesOut,
    double messagesPerSecond,
    Map<Integer, PartitionStats> partitionStats
) {

}
