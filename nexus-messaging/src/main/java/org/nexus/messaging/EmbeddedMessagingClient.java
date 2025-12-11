package org.nexus.messaging;

import org.nexus.messaging.interfaces.MessageCodec;
import org.nexus.messaging.interfaces.MessageHandler;
import org.nexus.messaging.interfaces.MessagePublisher;
import org.nexus.messaging.interfaces.MessagingClient;
import org.nexus.messaging.interfaces.Subscription;
import org.nexus.queue.domain.ConsumerConfig;
import org.nexus.queue.embedded.EmbeddedQueueBroker;
import org.nexus.queue.interfaces.MessageConsumer;

public final class EmbeddedMessagingClient implements MessagingClient {

  private final EmbeddedQueueBroker broker;

  public EmbeddedMessagingClient(EmbeddedQueueBroker broker) {
    this.broker = broker;
  }

  @Override
  public <T> MessagePublisher<T> publisher(String category, MessageCodec<T> codec) {
    return new EmbeddedMessagePublisher<>(category, codec, broker);
  }

  @Override
  public <T> Subscription subscribe(
      String category,
      String groupId,
      MessageCodec<T> codec,
      MessageHandler<T> handler
  ) {
    ConsumerConfig config = ConsumerConfig.defaults(groupId);

    MessageConsumer<byte[]> consumer = broker.<byte[]>createConsumer(config);

    return new EmbeddedSubscription<>(category, codec, handler, consumer);
  }

  @Override
  public void close() {
    broker.shutdown();
  }
}
