package org.nexus.queue.remote;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.nexus.queue.domain.BrokerStats;
import org.nexus.queue.domain.CategoryStats;
import org.nexus.queue.domain.ProduceRequest;
import org.nexus.queue.domain.ProduceResponse;
import org.nexus.queue.domain.QueueFrameHeader;
import org.nexus.queue.embedded.EmbeddedQueueBroker;
import org.nexus.commons.enums.QueueApiKey;

class QueueBrokerServerProduceTest {

  private EmbeddedQueueBroker broker;
  private QueueBrokerServer server;

  private static byte[] readFully(InputStream in, int length) throws IOException {
    byte[] data = new byte[length];
    int offset = 0;
    while (offset < length) {
      int read = in.read(data, offset, length - offset);
      if (read == -1) {
        throw new IOException("Unexpected end of stream");
      }
      offset += read;
    }
    return data;
  }

  @BeforeEach
  void setUp() throws Exception {
    broker = new EmbeddedQueueBroker();
    server = new QueueBrokerServer(broker, "127.0.0.1", 0);
    server.start();
  }

  @AfterEach
  void tearDown() throws Exception {
    if (server != null) {
      server.stop();
    }
    if (broker != null) {
      broker.shutdown().get(5, TimeUnit.SECONDS);
    }
  }

  @Test
  void produceSingleMessage_overNetwork_succeeds() throws Exception {
    BrokerStats before = broker.getStats();

    int port = server.getPort();

    try (Socket socket = new Socket("127.0.0.1", port)) {
      socket.setSoTimeout(5_000);

      QueueFrameHeader header = new QueueFrameHeader(
          QueueApiKey.PRODUCE_REQUEST,
          (byte) 1,
          123
      );

      byte[] payload = "hello-remote".getBytes(StandardCharsets.UTF_8);

      ProduceRequest request = new ProduceRequest(
          header,
          "net-produce-category",
          null,
          Map.of("h", "v"),
          0,
          List.of(payload)
      );

      ByteBuffer encoded = QueueNetworkProtocol.encodeProduceRequest(request);
      byte[] toSend = new byte[encoded.remaining()];
      encoded.get(toSend);

      OutputStream out = socket.getOutputStream();
      out.write(toSend);
      out.flush();

      InputStream in = socket.getInputStream();

      byte[] lenBytes = readFully(in, Integer.BYTES);
      int frameLength = ByteBuffer.wrap(lenBytes).getInt();
      byte[] body = readFully(in, frameLength);

      byte[] all = new byte[Integer.BYTES + frameLength];
      System.arraycopy(lenBytes, 0, all, 0, Integer.BYTES);
      System.arraycopy(body, 0, all, Integer.BYTES, frameLength);

      ProduceResponse response = QueueNetworkProtocol.decodeProduceResponse(
          ByteBuffer.wrap(all));

      assertEquals(QueueApiKey.PRODUCE_RESPONSE, response.header().apiKey());
      assertEquals(123, response.header().correlationId());
      assertEquals(1, response.messageCount());

      ProduceResponse.Result result = response.results().get(0);
      assertEquals(0, result.errorCode());
      assertTrue(result.queueId() >= 0);
      assertTrue(result.offset() >= 0);
    }

    BrokerStats after = broker.getStats();

    assertEquals(before.totalMessages() + 1, after.totalMessages());

    CategoryStats beforeCategory = before.topicStats().get("net-produce-category");
    long beforeCategoryMessages = beforeCategory != null ? beforeCategory.messageCount() : 0L;

    CategoryStats afterCategory = after.topicStats().get("net-produce-category");
    assertNotNull(afterCategory);
    assertEquals(beforeCategoryMessages + 1, afterCategory.messageCount());
  }
}
