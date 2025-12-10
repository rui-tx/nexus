package org.nexus.queue.net;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.nexus.domain.MessageId;
import org.nexus.domain.ProducerConfig;
import org.nexus.domain.PublishResult;
import org.nexus.interfaces.MessageProducer;
import org.nexus.interfaces.Serializer;

public class RemoteMessageProducer<T> implements MessageProducer<T> {

  private static final byte CURRENT_API_VERSION = 1;
  private static final int DEFAULT_SOCKET_TIMEOUT_MS = 5_000;

  private final String host;
  private final int port;
  private final ProducerConfig config;
  private final Serializer<T> serializer;
  private final AtomicBoolean closed = new AtomicBoolean(false);
  private final AtomicInteger correlationCounter = new AtomicInteger(1);

  public RemoteMessageProducer(
      String host,
      int port,
      ProducerConfig config,
      Serializer<T> serializer
  ) {
    this.host = host;
    this.port = port;
    this.config = config;
    this.serializer = serializer;
  }

  @Override
  public CompletableFuture<PublishResult> send(String category, T payload) {
    return send(category, null, payload, Map.of());
  }

  @Override
  public CompletableFuture<PublishResult> send(String category, String key, T payload) {
    return send(category, key, payload, Map.of());
  }

  @Override
  public CompletableFuture<PublishResult> send(
      String category,
      String key,
      T payload,
      Map<String, String> headers
  ) {
    if (closed.get()) {
      return CompletableFuture.failedFuture(new IllegalStateException("Producer is closed"));
    }

    try {
      byte[] payloadBytes = serializer.serialize(category, payload);

      int flags = 0;
      if (config.requiresAck()) {
        // For future use; server currently always replies.
      }
      if (config.persistent()) {
        // Persistence is determined server-side by category config; flag is reserved for later.
      }

      int correlationId = correlationCounter.getAndIncrement();
      QueueFrameHeader header = new QueueFrameHeader(
          QueueApiKey.PRODUCE_REQUEST,
          CURRENT_API_VERSION,
          correlationId
      );

      ProduceRequest request = new ProduceRequest(
          header,
          category,
          key,
          headers,
          flags,
          java.util.List.of(payloadBytes)
      );

      ByteBuffer encoded = QueueNetworkProtocol.encodeProduceRequest(request);
      byte[] frame = new byte[encoded.remaining()];
      encoded.get(frame);

      ProduceResponse response = sendAndReceive(frame);

      if (response.messageCount() != 1) {
        return CompletableFuture.failedFuture(
            new IllegalStateException("Expected 1 result, got " + response.messageCount()));
      }

      ProduceResponse.Result result = response.results().get(0);
      if (result.errorCode() != 0) {
        return CompletableFuture.failedFuture(
            new IllegalStateException("Broker returned error code " + result.errorCode()));
      }

      MessageId messageId = new MessageId(result.messageId());

      PublishResult publishResult = new PublishResult(
          messageId,
          category,
          result.queueId(),
          result.offset(),
          java.time.Instant.now()
      );

      return CompletableFuture.completedFuture(publishResult);
    } catch (Exception e) {
      return CompletableFuture.failedFuture(
          new RuntimeException("Failed to send message over remote broker", e));
    }
  }

  @Override
  public CompletableFuture<Void> flush() {
    return CompletableFuture.completedFuture(null);
  }

  @Override
  public CompletableFuture<Void> close() {
    closed.set(true);
    return CompletableFuture.completedFuture(null);
  }

  private ProduceResponse sendAndReceive(byte[] frame) throws IOException {
    try (Socket socket = new Socket(host, port)) {
      socket.setSoTimeout(DEFAULT_SOCKET_TIMEOUT_MS);

      OutputStream out = socket.getOutputStream();
      out.write(frame);
      out.flush();

      InputStream in = socket.getInputStream();

      byte[] lenBytes = readFully(in, Integer.BYTES);
      int frameLength = ByteBuffer.wrap(lenBytes).getInt();
      byte[] body = readFully(in, frameLength);

      byte[] all = new byte[Integer.BYTES + frameLength];
      System.arraycopy(lenBytes, 0, all, 0, Integer.BYTES);
      System.arraycopy(body, 0, all, Integer.BYTES, frameLength);

      return QueueNetworkProtocol.decodeProduceResponse(ByteBuffer.wrap(all));
    }
  }

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
}
