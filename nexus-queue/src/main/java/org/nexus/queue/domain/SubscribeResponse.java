package org.nexus.queue.domain;

import java.util.List;

public record SubscribeResponse(
    QueueFrameHeader header,
    int errorCode,
    List<Integer> assignedQueues
) {

  public SubscribeResponse {
    if (assignedQueues == null) {
      assignedQueues = List.of();
    }
  }

  public int assignedQueueCount() {
    return assignedQueues.size();
  }
}
