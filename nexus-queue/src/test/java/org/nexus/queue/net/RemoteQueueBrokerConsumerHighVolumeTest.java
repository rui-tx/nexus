package org.nexus.queue.net;

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
import org.nexus.domain.CategoryConfig;
import org.nexus.domain.ConsumerConfig;
import org.nexus.domain.ProducerConfig;
import org.nexus.embedded.EmbeddedQueueBroker;
import org.nexus.interfaces.MessageConsumer;
import org.nexus.interfaces.MessageProducer;

class RemoteQueueBrokerConsumerHighVolumeTest {

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
  void remoteConsumer_highVolume() throws Exception {
    String category = "remote-test-topic-perf";
    String groupId = "remote-group-1";
    int numberOfMessages = 250_000;
    int queues = 64;
    int consumersN = 1;

    broker.getOrCreateCategory(
        category,
        new CategoryConfig(queues, 1, 86400000L, false));

    CountDownLatch latch = new CountDownLatch(numberOfMessages);
    AtomicInteger receivedCount = new AtomicInteger();

    RemoteQueueBroker remoteBroker = new RemoteQueueBroker("127.0.0.1", server.getPort());

    List<MessageConsumer<byte[]>> consumers = new ArrayList<>();
    for (int i = 0; i < consumersN; i++) {
      String clientId = "remote-consumer-" + i;
      MessageConsumer<byte[]> c = remoteBroker.createConsumer(
          new ConsumerConfig(
              clientId,
              groupId,
              true,
              1000L,
              1000)
      );

      c.subscribe(category, message -> {
        receivedCount.incrementAndGet();
        latch.countDown();
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

    boolean completed = latch.await(30, TimeUnit.SECONDS);
    long receiveCompleteTime = System.nanoTime();

    double sendTimeMs = (sendCompleteTime - startTime) / 1_000_000.0;
    double receiveTimeMs = (receiveCompleteTime - startTime) / 1_000_000.0;
    double throughput = numberOfMessages / (receiveTimeMs / 1000.0);

    System.out.printf("""
            Remote Queues Test Results:
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

    consumers.forEach(c -> c.close().join());
    producer.close();
  }
}
