package org.nexus.embedded;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import org.nexus.domain.MessageId;
import org.nexus.domain.MessageMetadata;
import org.nexus.domain.ProducerConfig;
import org.nexus.domain.PublishResult;
import org.nexus.interfaces.MessageProducer;
import org.nexus.interfaces.Serializer;

/**
 * Embedded producer implementation. Publishes messages directly to the broker (in-memory, no
 * network).
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
  public CompletableFuture<PublishResult> send(String topic, T payload) {
    return send(topic, null, payload, Map.of());
  }

  @Override
  public CompletableFuture<PublishResult> send(String topic, String key, T payload) {
    return send(topic, key, payload, Map.of());
  }

  @Override
  public CompletableFuture<PublishResult> send(
      String topic,
      String key,
      T payload,
      Map<String, String> headers
  ) {
    if (closed.get()) {
      return CompletableFuture.failedFuture(
          new IllegalStateException("Producer is closed")
      );
    }

    return CompletableFuture.supplyAsync(() -> {
      try {
        // Serialize payload
        byte[] payloadBytes = serializer.serialize(topic, payload);

        // Create metadata
        MessageId messageId = MessageId.generate();
        MessageMetadata metadata = new MessageMetadata(
            messageId,
            topic,
            key,
            Instant.now(),
            -1, // Will be set by topic
            -1L, // Will be set by partition
            headers
        );

        // Publish to broker
        PublishResult result = broker.publish(topic, payloadBytes, metadata);

        return result;

      } catch (Exception e) {
        throw new RuntimeException("Failed to publish message", e);
      }
    });
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
