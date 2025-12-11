package org.nexus.messaging.domain;

import java.util.Map;

public record MessageMetadata(
    String category,
    String key,
    Map<String, String> headers,
    int queueId,
    long offset,
    long timestamp
) {

}
