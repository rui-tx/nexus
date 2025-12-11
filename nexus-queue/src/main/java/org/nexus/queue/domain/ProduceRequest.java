package org.nexus.queue.domain;

import java.util.List;
import java.util.Map;

public record ProduceRequest(
    QueueFrameHeader header,
    String category,
    String key,
    Map<String, String> headers,
    int flags,
    List<byte[]> payloads
) {

  public ProduceRequest {
    if (category == null || category.isEmpty()) {
      throw new IllegalArgumentException("category cannot be null or empty");
    }
    if (headers == null) {
      headers = Map.of();
    }
    if (payloads == null || payloads.isEmpty()) {
      throw new IllegalArgumentException("payloads cannot be null or empty");
    }
  }

  public int messageCount() {
    return payloads.size();
  }
}
