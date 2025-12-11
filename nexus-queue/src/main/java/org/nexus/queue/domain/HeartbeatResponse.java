package org.nexus.queue.domain;

import java.util.List;

public record HeartbeatResponse(
    QueueFrameHeader header,
    int errorCode,
    List<Integer> assignedQueues
) {

}
