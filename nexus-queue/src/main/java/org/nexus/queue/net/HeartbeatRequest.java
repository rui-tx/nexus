package org.nexus.queue.net;

public record HeartbeatRequest(
    QueueFrameHeader header,
    String category,
    String groupId,
    String clientId
) {
}
