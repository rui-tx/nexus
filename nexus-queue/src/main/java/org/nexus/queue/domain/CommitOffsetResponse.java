package org.nexus.queue.domain;

public record CommitOffsetResponse(
    QueueFrameHeader header,
    int errorCode
) {

}
