package org.nexus.queue.domain;

import java.util.Map;

/**
 * Broker-wide statistics
 */
public record BrokerStats(
    int topicCount,
    long totalMessages,
    long totalBytes,
    int totalConsumers,
    Map<String, CategoryStats> topicStats
) {

}
