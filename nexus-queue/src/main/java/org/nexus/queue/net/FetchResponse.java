package org.nexus.queue.net;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record FetchResponse(
    QueueFrameHeader header,
    List<Message> messages
) {

  public FetchResponse {
    if (messages == null) {
      messages = List.of();
    }
  }

  public int messageCount() {
    return messages.size();
  }

  public record Message(
      UUID messageId,
      int queueId,
      long offset,
      long timestamp,
      String key,
      Map<String, String> headers,
      byte[] payload
  ) {
  }
}
