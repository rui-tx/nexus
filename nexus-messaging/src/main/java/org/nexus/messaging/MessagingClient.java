package org.nexus.messaging;

public interface MessagingClient extends AutoCloseable {

  <T> MessagePublisher<T> publisher(
      String category,
      MessageCodec<T> codec
  );

  <T> Subscription subscribe(
      String category,
      String groupId,
      MessageCodec<T> codec,
      MessageHandler<T> handler
  );

  @Override
  void close();
}
