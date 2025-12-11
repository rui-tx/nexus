package org.nexus.queue.domain;

import org.nexus.commons.enums.QueueApiKey;

public record QueueFrameHeader(
    QueueApiKey apiKey,
    byte apiVersion,
    int correlationId
) {

}
