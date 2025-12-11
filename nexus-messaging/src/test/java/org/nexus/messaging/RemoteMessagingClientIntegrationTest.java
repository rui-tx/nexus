package org.nexus.messaging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.nexus.commons.enums.MessagingMode;
import org.nexus.messaging.domain.MessagingConfig;
import org.nexus.messaging.interfaces.MessageCodec;
import org.nexus.messaging.interfaces.MessagePublisher;
import org.nexus.messaging.interfaces.MessagingClient;
import org.nexus.messaging.interfaces.Subscription;
import org.nexus.queue.domain.CategoryConfig;
import org.nexus.queue.embedded.EmbeddedQueueBroker;
import org.nexus.queue.remote.QueueBrokerServer;

class RemoteMessagingClientIntegrationTest {

  private EmbeddedQueueBroker broker;
  private QueueBrokerServer server;

  @BeforeEach
  void setUp() throws Exception {
    broker = new EmbeddedQueueBroker();
    server = new QueueBrokerServer(broker, "127.0.0.1", 0);
    server.start();
  }

  @AfterEach
  void tearDown() throws Exception {
    if (server != null) {
      server.stop();
    }
    if (broker != null) {
      broker.shutdown().get(5, TimeUnit.SECONDS);
    }
  }

  @Test
  void remoteMessagingClient_canSendAndReceive() throws Exception {
    String category = "messaging-remote-test";
    String groupId = "test-group";
    int numberOfMessages = 1000;
    int queues = 8;

    broker.getOrCreateCategory(
        category,
        new CategoryConfig(queues, 1, 86_400_000L, false)
    );

    int port = server.getPort();

    MessagingConfig config = new MessagingConfig(MessagingMode.REMOTE, "127.0.0.1", port);

    try (MessagingClient client = MessagingClients.create(config)) {
      MessageCodec<String> codec = new JsonMessageCodec<>(String.class);

      CountDownLatch latch = new CountDownLatch(numberOfMessages);
      AtomicInteger received = new AtomicInteger();

      Subscription subscription = client.subscribe(
          category,
          groupId,
          codec,
          (payload, meta) -> {
            received.incrementAndGet();
            latch.countDown();
          }
      );
      subscription.start();

      MessagePublisher<String> publisher = client.publisher(category, codec);

      for (int i = 0; i < numberOfMessages; i++) {
        publisher.send(Integer.toString(i), "msg-" + i);
      }

      boolean completed = latch.await(30, TimeUnit.SECONDS);

      assertTrue(completed, "Timed out before receiving all messages");
      assertEquals(numberOfMessages, received.get());

      subscription.close();
      publisher.close();
    }
  }
}
