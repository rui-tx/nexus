package org.nexus.queue.domain;

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
