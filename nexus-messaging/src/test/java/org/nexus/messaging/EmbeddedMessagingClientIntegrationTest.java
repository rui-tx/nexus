package org.nexus.messaging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.nexus.commons.enums.MessagingMode;
import org.nexus.messaging.domain.MessagingConfig;
import org.nexus.messaging.interfaces.MessageCodec;
import org.nexus.messaging.interfaces.MessagePublisher;
import org.nexus.messaging.interfaces.MessagingClient;
import org.nexus.messaging.interfaces.Subscription;

class EmbeddedMessagingClientIntegrationTest {

  @Test
  void embeddedMessagingClient_canSendAndReceive() throws Exception {
    String category = "messaging-embedded-test";
    String groupId = "embedded-group";
    int numberOfMessages = 1000;

    MessagingConfig config = new MessagingConfig(
        MessagingMode.EMBEDDED,
        "ignored-host",
        0
    );

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
