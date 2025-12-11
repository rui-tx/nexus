package org.nexus.queue.remote;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.nexus.queue.domain.ProduceRequest;
import org.nexus.queue.domain.ProduceResponse;
import org.nexus.queue.domain.QueueFrameHeader;
import org.nexus.commons.enums.QueueApiKey;

class QueueNetworkProtocolTest {

  @Test
  void produceRequest_roundTrip() {
    QueueFrameHeader header = new QueueFrameHeader(
        QueueApiKey.PRODUCE_REQUEST,
        (byte) 1,
        42
    );

    ProduceRequest original = new ProduceRequest(
        header,
        "test-category",
        "test-key",
        Map.of("h1", "v1"),
        123,
        List.of("payload-1".getBytes(StandardCharsets.UTF_8))
    );

    ByteBuffer encoded = QueueNetworkProtocol.encodeProduceRequest(original);
    ProduceRequest decoded = QueueNetworkProtocol.decodeProduceRequest(encoded);

    assertEquals(original.header().apiKey(), decoded.header().apiKey());
    assertEquals(original.header().apiVersion(), decoded.header().apiVersion());
    assertEquals(original.header().correlationId(), decoded.header().correlationId());

    assertEquals(original.category(), decoded.category());
    assertEquals(original.key(), decoded.key());
    assertEquals(original.headers(), decoded.headers());
    assertEquals(original.flags(), decoded.flags());
    assertEquals(original.messageCount(), decoded.messageCount());

    byte[] expectedPayload = original.payloads().get(0);
    byte[] actualPayload = decoded.payloads().get(0);
    assertEquals(new String(expectedPayload, StandardCharsets.UTF_8),
        new String(actualPayload, StandardCharsets.UTF_8));
  }

  @Test
  void produceResponse_roundTrip() {
    QueueFrameHeader header = new QueueFrameHeader(
        QueueApiKey.PRODUCE_RESPONSE,
        (byte) 1,
        99
    );

    ProduceResponse.Result result = new ProduceResponse.Result(
        UUID.randomUUID(),
        3,
        10L,
        0
    );

    ProduceResponse original = new ProduceResponse(header, List.of(result));

    ByteBuffer encoded = QueueNetworkProtocol.encodeProduceResponse(original);
    ProduceResponse decoded = QueueNetworkProtocol.decodeProduceResponse(encoded);

    assertEquals(original.header().apiKey(), decoded.header().apiKey());
    assertEquals(original.header().apiVersion(), decoded.header().apiVersion());
    assertEquals(original.header().correlationId(), decoded.header().correlationId());

    assertEquals(original.messageCount(), decoded.messageCount());
    assertEquals(1, decoded.results().size());

    ProduceResponse.Result decodedResult = decoded.results().get(0);
    assertEquals(result.messageId(), decodedResult.messageId());
    assertEquals(result.queueId(), decodedResult.queueId());
    assertEquals(result.offset(), decodedResult.offset());
    assertEquals(result.errorCode(), decodedResult.errorCode());

    assertNotNull(decodedResult.messageId());
  }
}
