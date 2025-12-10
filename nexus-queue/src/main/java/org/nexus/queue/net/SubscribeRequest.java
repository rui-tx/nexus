package org.nexus.queue.net;

public record SubscribeRequest(
    QueueFrameHeader header,
    String category,
    String groupId,
    String clientId
) {
}
