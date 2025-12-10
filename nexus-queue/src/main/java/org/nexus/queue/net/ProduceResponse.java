package org.nexus.queue.net;

import java.util.List;
import java.util.UUID;

public record ProduceResponse(
    QueueFrameHeader header,
    List<Result> results
) {

  public ProduceResponse {
    if (results == null) {
      results = List.of();
    }
  }

  public int messageCount() {
    return results.size();
  }

  public record Result(
      UUID messageId,
      int queueId,
      long offset,
      int errorCode
  ) {
  }
}
