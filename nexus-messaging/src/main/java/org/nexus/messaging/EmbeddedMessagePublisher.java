package org.nexus.messaging;

import java.util.Map;
import java.util.concurrent.CompletionException;
import org.nexus.domain.ProducerConfig;
import org.nexus.embedded.EmbeddedQueueBroker;
import org.nexus.interfaces.MessageProducer;

final class EmbeddedMessagePublisher<T> implements MessagePublisher<T> {

  private final String category;
  private final MessageCodec<T> codec;
  private final MessageProducer<byte[]> producer;

  EmbeddedMessagePublisher(String category, MessageCodec<T> codec, EmbeddedQueueBroker broker) {
    this.category = category;
    this.codec = codec;

    ProducerConfig config = ProducerConfig.defaults();
    MessageProducer<byte[]> p = broker.<byte[]>createProducer(config);
    this.producer = p;
  }

  @Override
  public void send(String key, T payload) {
    send(key, payload, null);
  }

  @Override
  public void send(String key, T payload, MessageHeaders headers) {
    byte[] bytes = codec.encode(payload);
    Map<String, String> headerMap = headers != null ? headers.asMap() : Map.of();
    try {
      producer.send(category, key, bytes, headerMap).join();
    } catch (CompletionException e) {
      if (e.getCause() instanceof RuntimeException runtime) {
        throw runtime;
      }
      throw e;
    }
  }

  @Override
  public void close() {
    producer.close();
  }
}
