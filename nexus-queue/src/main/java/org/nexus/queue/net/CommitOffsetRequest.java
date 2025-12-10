package org.nexus.queue.net;

import java.util.Map;

public record CommitOffsetRequest(
    QueueFrameHeader header,
    String category,
    String groupId,
    Map<Integer, Long> offsets
) {

  public CommitOffsetRequest {
    if (offsets == null) {
      offsets = Map.of();
    }
  }

  public int count() {
    return offsets.size();
  }
}
