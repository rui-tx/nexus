package org.nexus.queue.remote;

import java.util.concurrent.CompletableFuture;
import org.nexus.queue.domain.CategoryConfig;
import org.nexus.queue.domain.ConsumerConfig;
import org.nexus.queue.domain.ProducerConfig;
import org.nexus.queue.interfaces.Category;
import org.nexus.queue.interfaces.Deserializer;
import org.nexus.queue.interfaces.MessageConsumer;
import org.nexus.queue.interfaces.MessageProducer;
import org.nexus.queue.interfaces.QueueBroker;
import org.nexus.queue.interfaces.Serializer;
import org.nexus.queue.serialization.Deserializers;
import org.nexus.queue.serialization.Serializers;

public class RemoteQueueBroker implements QueueBroker {

  private final String host;
  private final int port;

  public RemoteQueueBroker(String host, int port) {
    this.host = host;
    this.port = port;
  }

  @Override
  public <T> MessageProducer<T> createProducer(ProducerConfig config) {
    @SuppressWarnings("unchecked")
    Serializer<T> serializer = (Serializer<T>) Serializers.byteArray();
    return new RemoteProducer<>(host, port, config, serializer);
  }

  @Override
  public <T> MessageConsumer<T> createConsumer(ConsumerConfig config) {
    @SuppressWarnings("unchecked")
    Deserializer<T> deserializer = (Deserializer<T>) Deserializers.byteArray();
    return new RemoteConsumer<>(host, port, config, deserializer);
  }

  @Override
  public Category getOrCreateCategory(String name, CategoryConfig config) {
    throw new UnsupportedOperationException("Remote category management is not implemented yet");
  }

  @Override
  public CompletableFuture<Void> deleteCategory(String name) {
    return CompletableFuture.failedFuture(
        new UnsupportedOperationException("Remote category deletion is not implemented yet"));
  }

  @Override
  public String[] listCategories() {
    throw new UnsupportedOperationException("Remote category listing is not implemented yet");
  }

  @Override
  public CompletableFuture<Void> shutdown() {
    return CompletableFuture.completedFuture(null);
  }
}
