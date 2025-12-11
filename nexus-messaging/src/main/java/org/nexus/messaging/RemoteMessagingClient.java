package org.nexus.messaging;

import org.nexus.messaging.interfaces.MessageCodec;
import org.nexus.messaging.interfaces.MessageHandler;
import org.nexus.messaging.interfaces.MessagePublisher;
import org.nexus.messaging.interfaces.MessagingClient;
import org.nexus.messaging.interfaces.Subscription;
import org.nexus.queue.domain.ConsumerConfig;
import org.nexus.queue.interfaces.MessageConsumer;
import org.nexus.queue.remote.RemoteQueueBroker;

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
