package io.github.ruitx.messaging;

import io.avaje.inject.Singleton;
import java.util.concurrent.atomic.AtomicInteger;
import org.nexus.messaging.JsonMessageCodec;
import org.nexus.messaging.MessageCodec;
import org.nexus.messaging.MessagePublisher;
import org.nexus.messaging.MessagingClient;
import org.nexus.messaging.MessagingClients;
import org.nexus.messaging.MessagingConfig;
import org.nexus.messaging.MessagingMode;
import org.nexus.messaging.Subscription;

@Singleton
class MessagingDemo {

  private final MessagingClient client;
  private final MessagePublisher<String> publisher;
  private final Subscription subscription;
  private final AtomicInteger processedCount = new AtomicInteger();

  MessagingDemo() {
    String category = "demo-http";
    String groupId = "demo-http-group";

    MessagingConfig config = new MessagingConfig(
        MessagingMode.EMBEDDED,
        "ignored-host",
        0,
        200_000
    ); // switch to MessagingMode.REMOTE and set host/port to talk to a remote broker

    this.client = MessagingClients.create(config);
    MessageCodec<String> codec = new JsonMessageCodec<>(String.class);

    this.subscription = client.subscribe(
        category,
        groupId,
        codec,
        (payload, meta) -> {
          processedCount.incrementAndGet();
        }
    );
    this.subscription.start();

    this.publisher = client.publisher(category, codec);
  }

  void send(String key, String value) {
    publisher.send(key, value);
  }

  int getProcessedCount() {
    return processedCount.get();
  }
}
