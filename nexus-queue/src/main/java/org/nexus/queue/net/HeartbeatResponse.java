package org.nexus.queue.net;

import java.util.List;

public record HeartbeatResponse(
    QueueFrameHeader header,
    int errorCode,
    List<Integer> assignedQueues
) {
}
