package org.nexus.domain;

import java.time.Instant;

/**
 * Result of publishing a message
 */
public record PublishResult(
    MessageId messageId,
    String topic,
    int partition,
    long offset,
    Instant timestamp
) {

}
