package org.nexus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.nexus.domain.CategoryConfig;
import org.nexus.domain.ConsumerConfig;
import org.nexus.domain.ProducerConfig;
import org.nexus.embedded.EmbeddedQueueBroker;
import org.nexus.interfaces.MessageConsumer;
import org.nexus.interfaces.MessageProducer;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class QueuePerformanceTest {

  private EmbeddedQueueBroker broker;

  @BeforeEach
  void setUp() {
    broker = new EmbeddedQueueBroker();
  }

  @AfterEach
  void tearDown() throws Exception {
    broker.shutdown().get(5, TimeUnit.SECONDS);
  }

  @Test
  void testQueuesWithConsumers_HighVolume() throws Exception {

    String category = "test-topic";
    String groupId = "group-1";
    int numberOfMessages = 100_000;
    int queues = 64;
    int consumersN = 32;

    broker.getOrCreateCategory(
        "test-topic",
        new CategoryConfig(queues, 1, 86400000L, false));

    CountDownLatch latch = new CountDownLatch(numberOfMessages);
    AtomicInteger receivedCount = new AtomicInteger();

    List<MessageConsumer<byte[]>> consumers = new ArrayList<>();
    for (int i = 0; i < consumersN; i++) {
      String clientId = "consumer-" + i;
      MessageConsumer<byte[]> c = broker.createConsumer(
          new ConsumerConfig(
              clientId,
              groupId,
              true,
              1000L,
              1000)
      );

      c.subscribe(category, message -> {
        int current = receivedCount.incrementAndGet();
        latch.countDown();
//        if (current % 100000 == 0 || current >= numberOfMessages - 5) {
//          System.out.println("[DEBUG] Handler received message #" + current + ", latch remaining=" + latch.getCount());
//        }
        return CompletableFuture.completedFuture(null);
      });

      consumers.add(c);
    }

    MessageProducer<byte[]> producer = broker.createProducer(ProducerConfig.defaults());

    long startTime = System.nanoTime();
    List<CompletableFuture<?>> futures = new ArrayList<>(numberOfMessages);
    for (int i = 0; i < numberOfMessages; i++) {
      byte[] payload = ("message-" + i).getBytes(StandardCharsets.UTF_8);
      futures.add(producer.send(category, payload));
    }

    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    long sendCompleteTime = System.nanoTime();

    // Debug: Print final queue stats
    /*System.out.println("\nFinal queue stats:");
    for (int i = 0; i < queues; i++) {
      long committedOffset = broker.getCommittedOffset(groupId, category, i);
      System.out.println("Queue " + i + " committed offset: " + committedOffset);
    }*/

    boolean completed = latch.await(30, TimeUnit.SECONDS); // Increased timeout
/*    System.out.println("[DEBUG] Latch await completed=" + completed +
        ", remaining=" + latch.getCount() +
        ", receivedCount=" + receivedCount.get());*/
    long receiveCompleteTime = System.nanoTime();

    /*consumers.forEach(c -> {
      if (c instanceof org.nexus.embedded.EmbeddedConsumer<?> embedded) {
        long delivered = embedded.getTotalDeliveredCount(category);
        long handled = embedded.getTotalHandledCount(category);
        System.out.println("[DEBUG] Consumer " + ((org.nexus.embedded.EmbeddedConsumer<?>) c)
            .getConfig().clientId() +
            " delivered=" + delivered + ", handled=" + handled);
      }
    });*/

    double sendTimeMs = (sendCompleteTime - startTime) / 1_000_000.0;
    double receiveTimeMs = (receiveCompleteTime - startTime) / 1_000_000.0;
    double throughput = numberOfMessages / (receiveTimeMs / 1000.0);

    System.out.printf("""
            Queues Test Results:
            - Messages sent in: %.2f ms (%.0f msg/s)
            - All messages received in: %.2f ms (%.0f msg/s)
            - Total messages received: %d
            %s%n""",
        sendTimeMs,
        numberOfMessages / (sendTimeMs / 1000.0),
        receiveTimeMs,
        throughput,
        receivedCount.get(),
        completed ? "SUCCESS" : "TIMEOUT"
    );

    assertTrue(completed, "Test timed out before all messages were received");
    assertEquals(numberOfMessages, receivedCount.get(),
        "Should have received all messages");

    consumers.forEach(c -> {
      try {
        c.close();
      } catch (Exception ignored) {
      }
    });
    producer.close();
  }
}
