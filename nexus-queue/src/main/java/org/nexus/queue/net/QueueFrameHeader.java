package org.nexus.queue.net;

public record QueueFrameHeader(
    QueueApiKey apiKey,
    byte apiVersion,
    int correlationId
) {
}
