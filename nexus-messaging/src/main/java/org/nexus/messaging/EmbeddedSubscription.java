package org.nexus.messaging;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import org.nexus.interfaces.MessageConsumer;

final class EmbeddedSubscription<T> implements Subscription {

  private final String category;
  private final MessageCodec<T> codec;
  private final MessageHandler<T> handler;
  private final MessageConsumer<byte[]> consumer;
  private final AtomicBoolean started = new AtomicBoolean(false);
  private final AtomicBoolean stopped = new AtomicBoolean(false);

  EmbeddedSubscription(
      String category,
      MessageCodec<T> codec,
      MessageHandler<T> handler,
      MessageConsumer<byte[]> consumer
  ) {
    this.category = category;
    this.codec = codec;
    this.handler = handler;
    this.consumer = consumer;
  }

  @Override
  public void start() {
    if (!started.compareAndSet(false, true)) {
      return;
    }

    org.nexus.interfaces.MessageHandler<byte[]> lowLevelHandler = message -> {
      byte[] payloadBytes = message.payload();
      T value = codec.decode(payloadBytes);
      org.nexus.domain.MessageMetadata metadata = message.metadata();

      MessageMetadata highLevelMetadata = new MessageMetadata(
          metadata.category(),
          metadata.key(),
          metadata.headers(),
          metadata.queue(),
          metadata.offset(),
          metadata.timestamp().toEpochMilli()
      );

      try {
        handler.onMessage(value, highLevelMetadata);
        return CompletableFuture.completedFuture(null);
      } catch (Exception e) {
        return CompletableFuture.failedFuture(e);
      }
    };

    consumer.subscribe(category, lowLevelHandler);
  }

  @Override
  public void stop() {
    if (!stopped.compareAndSet(false, true)) {
      return;
    }

    consumer.unsubscribe(category);
    consumer.close();
  }

  @Override
  public void close() {
    stop();
  }
}
