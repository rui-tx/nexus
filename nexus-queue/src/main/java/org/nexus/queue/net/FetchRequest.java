package org.nexus.queue.net;

public record FetchRequest(
    QueueFrameHeader header,
    String category,
    String groupId,
    String clientId,
    int queueId,
    long fromOffset,
    int maxRecords,
    int maxWaitMs
) {
}
