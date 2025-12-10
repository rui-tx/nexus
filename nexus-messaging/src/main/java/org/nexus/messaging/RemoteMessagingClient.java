package org.nexus.messaging;

import org.nexus.domain.ConsumerConfig;
import org.nexus.interfaces.MessageConsumer;
import org.nexus.queue.net.RemoteQueueBroker;

public final class RemoteMessagingClient implements MessagingClient {

  private final RemoteQueueBroker broker;

  public RemoteMessagingClient(String host, int port) {
    this.broker = new RemoteQueueBroker(host, port);
  }

  @Override
  public <T> MessagePublisher<T> publisher(String category, MessageCodec<T> codec) {
    return new RemoteMessagePublisher<>(category, codec, broker);
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

    return new RemoteSubscription<>(category, codec, handler, consumer);
  }

  @Override
  public void close() {
    broker.shutdown();
  }
}
