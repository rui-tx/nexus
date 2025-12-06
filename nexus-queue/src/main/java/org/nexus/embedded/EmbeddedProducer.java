package org.nexus.embedded;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import org.nexus.domain.MessageInput;
import org.nexus.domain.ProducerConfig;
import org.nexus.domain.PublishResult;
import org.nexus.interfaces.MessageProducer;
import org.nexus.interfaces.Serializer;

/**
 * Embedded producer implementation. Publishes messages directly to the broker (in-memory).
 */
public class EmbeddedProducer<T> implements MessageProducer<T> {

  private final EmbeddedQueueBroker broker;
  private final ProducerConfig config;
  private final Serializer<T> serializer;
  private final AtomicBoolean closed = new AtomicBoolean(false);

  public EmbeddedProducer(
      EmbeddedQueueBroker broker,
      ProducerConfig config,
      Serializer<T> serializer
  ) {
    this.broker = broker;
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
      return CompletableFuture.failedFuture(
          new IllegalStateException("Producer is closed")
      );
    }
    try {
      // Serialize payload
      byte[] payloadBytes = serializer.serialize(category, payload);

      // Create message input
      MessageInput input = new MessageInput(
          category,
          key,
          headers
      );

      PublishResult result = broker.publish(category, payloadBytes, input);
      return CompletableFuture.completedFuture(result);

    } catch (Exception e) {
      return CompletableFuture.failedFuture(
          new RuntimeException("Failed to publish message", e)
      );
    }
  }

  @Override
  public CompletableFuture<Void> flush() {
    // In embedded mode, messages are sent immediately
    // Nothing to flush
    return CompletableFuture.completedFuture(null);
  }

  @Override
  public CompletableFuture<Void> close() {
    closed.set(true);
    return CompletableFuture.completedFuture(null);
  }

  public ProducerConfig getConfig() {
    return config;
  }

  public boolean isClosed() {
    return closed.get();
  }
}
