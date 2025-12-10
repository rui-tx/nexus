package org.nexus.queue.net;

public record CommitOffsetResponse(
    QueueFrameHeader header,
    int errorCode
) {
}
