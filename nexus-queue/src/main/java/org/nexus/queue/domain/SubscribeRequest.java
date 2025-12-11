package org.nexus.queue.domain;

public record SubscribeRequest(
    QueueFrameHeader header,
    String category,
    String groupId,
    String clientId
) {

}
