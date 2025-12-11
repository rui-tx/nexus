package org.nexus.queue.domain;

public record HeartbeatRequest(
    QueueFrameHeader header,
    String category,
    String groupId,
    String clientId
) {

}
