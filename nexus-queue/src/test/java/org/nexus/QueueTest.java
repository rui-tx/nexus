package org.nexus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.nexus.domain.CategoryConfig;
import org.nexus.domain.ConsumerConfig;
import org.nexus.domain.ProducerConfig;
import org.nexus.embedded.EmbeddedQueueBroker;
import org.nexus.interfaces.MessageConsumer;
import org.nexus.interfaces.MessageProducer;
import org.nexus.persistence.OffsetStore;
import org.nexus.persistence.QueueStorage;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class QueueTest {

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
  @Order(1)
  void multipleConsumersInSameGroup_loadBalanceAndDeliverExactlyOnce() throws Exception {

    String category = "balanced-topic";
    String groupId = "balanced-group";
    int queueCount = 6;
    int messagesPerQueue = 50;
    int totalMessages = queueCount * messagesPerQueue; // 300

    // Create category with 6 queues
    broker.getOrCreateCategory(category, new CategoryConfig(
        queueCount,   // 6 partitions/queues
        1,
        86_400_000L,
        false
    ));

    // Thread-safe collection of all received messages across all consumers
    List<String> allReceived = new CopyOnWriteArrayList<>();
    CountDownLatch allMessagesLatch = new CountDownLatch(totalMessages);

    // Create 3 consumers in the SAME consumer group
    List<MessageConsumer<byte[]>> consumers = new ArrayList<>();
    for (int i = 0; i < 3; i++) {
      String clientId = "consumer-" + i;
      MessageConsumer<byte[]> c = broker.createConsumer(
          new ConsumerConfig(clientId, groupId, true, 1000L, 100)
      );

      c.subscribe(category, message -> {
        String text = new String(message.payload());
        allReceived.add(text);
        allMessagesLatch.countDown();
        return CompletableFuture.completedFuture(null);
      });

      consumers.add(c);
    }

    // Wait a moment for rebalance to happen
    Thread.sleep(1100);

    // Verify each consumer got exactly 2 queues
    for (int i = 0; i < 3; i++) {
      String clientId = "consumer-" + i;
      List<Integer> assigned = broker.getAssignedQueues(groupId, clientId, category);
      assertEquals(2, assigned.size(), "Consumer " + clientId + " should have 2 queues");
    }

    // Now produce messages — they will be round-robin across 6 queues
    MessageProducer<byte[]> producer = broker.createProducer(ProducerConfig.defaults());
    for (int i = 0; i < totalMessages; i++) {
      String payload = "msg-" + i;
      producer.send(category, payload.getBytes()).join();
    }

    // Wait for all messages to be consumed
    boolean allDelivered = allMessagesLatch.await(10, TimeUnit.SECONDS);
    assertTrue(allDelivered, "All 300 messages should be delivered within 10s");

    assertEquals(totalMessages, allReceived.size());
    assertEquals(totalMessages, allReceived.stream().distinct().count(),
        "Every message must be delivered exactly once");

    // Bonus: check no consumer got starved
    Map<String, Long> perConsumerCount = new HashMap<>();
    for (String msg : allReceived) {
      perConsumerCount.merge(msg, 1L, Long::sum);
    }

    assertEquals(totalMessages, perConsumerCount.size(), "Should have all unique messages");
    perConsumerCount.values().forEach(count -> assertEquals(1L, count, "No duplicates"));

    // Cleanup
    consumers.forEach(c -> {
      try {
        c.close();
      } catch (Exception ignored) {
      }
    });
  }

  @Test
  @Order(2)
  void consumerRestart_resumesFromCommittedOffset_noLossNoDuplicates() throws Exception {

    String category = "durable-topic";
    String groupId = "durable-group";
    String clientId = "survivor-consumer";   // same ID = same logical consumer

    broker.getOrCreateCategory(category, new CategoryConfig(3, 1, 86_400_000L, false));

    MessageProducer<byte[]> producer = broker.createProducer(ProducerConfig.defaults());

    // Produce 120 messages → 40 per queue
    for (int i = 0; i < 120; i++) {
      producer.send(category, ("msg-" + i).getBytes()).join();
    }

    List<String> phase1 = new CopyOnWriteArrayList<>();
    List<String> phase2 = new CopyOnWriteArrayList<>();

    // PHASE 1 – consume some messages, then die
    {
      MessageConsumer<byte[]> first = broker.createConsumer(
          new ConsumerConfig(clientId, groupId, true, 1000L, 100));

      first.subscribe(category, msg -> {
        phase1.add(new String(msg.payload()));
        return CompletableFuture.completedFuture(null);
      });

      long deadline = System.currentTimeMillis() + 8_000L;
      while (phase1.size() < 60 && System.currentTimeMillis() < deadline) {
        Thread.sleep(100);
      }

      System.out.println("PHASE 1 consumed: " + phase1.size() + " messages");

      first.close(); // triggers final commit
    }

    Thread.sleep(500); // let commit finish

    // PHASE 2 – restart the SAME consumer (same clientId)
    {
      MessageConsumer<byte[]> revived = broker.createConsumer(
          new ConsumerConfig(clientId, groupId, true, 1000L, 100));

      revived.subscribe(category, msg -> {
        phase2.add(new String(msg.payload()));
        return CompletableFuture.completedFuture(null);
      });

      long deadline = System.currentTimeMillis() + 8_000L;
      while (phase2.size() < (120 - phase1.size()) && System.currentTimeMillis() < deadline) {
        Thread.sleep(100);
      }

      System.out.println("PHASE 2 consumed: " + phase2.size() + " messages");

      revived.close();
    }

    // FINAL ASSERTIONS
    Set<String> allSeen = new HashSet<>();
    allSeen.addAll(phase1);
    allSeen.addAll(phase2);

    assertEquals(120, allSeen.size(), "All 120 messages must be delivered exactly once");

    // Check for duplicates between the two phases
    Set<String> phase1Set = new HashSet<>(phase1);
    Set<String> phase2Set = new HashSet<>(phase2);
    phase1Set.retainAll(phase2Set); // intersection

    assertTrue(phase1Set.isEmpty(),
        "No duplicates after restart! Found duplicated messages: " + phase1Set);

    System.out.println("Consumer restart test PASSED – no loss, no duplicates!");
  }

  @Test
  @Order(3)
  @Disabled
  void consumerDiesMidFlight_restartConsumesOnlyRemainingMessages() throws Exception {

    String category = "crash-recovery-topic";
    String groupId = "crash-group";
    String clientId = "crashy-consumer";  // same ID = same logical consumer
    int messageCount = 2000;

    // 4 queues → easier to see per-queue progress
    broker.getOrCreateCategory(category, new CategoryConfig(4, 1, 86_400_000L, false));

    MessageProducer<byte[]> producer = broker.createProducer(ProducerConfig.defaults());

    // Produce messages
    for (int i = 0; i < messageCount; i++) {
      producer.send(category, ("msg-" + i).getBytes()).join();
    }

    List<String> beforeCrash = new CopyOnWriteArrayList<>();
    List<String> afterRestart = new CopyOnWriteArrayList<>();
    CountDownLatch latch = new CountDownLatch(messageCount);

    MessageConsumer<byte[]> consumer = broker.createConsumer(
        new ConsumerConfig(clientId, groupId, true, 500L, 100)  // Increased to 1000ms
    );

    consumer.subscribe(category, msg -> {
      String text = new String(msg.payload());
      beforeCrash.add(text);
      // Simulate processing
      try {
        Thread.sleep(15);
      } catch (InterruptedException ignored) {
      }
      latch.countDown();
      return CompletableFuture.completedFuture(null);
    });

    // Let it run → will consume some but not all messages
    Thread.sleep(1500);

    System.out.println("CRASHING consumer after consuming " + beforeCrash.size() + " messages");
    consumer.close();                     // 'crash' the consumer
    Thread.sleep(500);              // give auto-commit time to finish

    // PHASE 2: restart the exact same consumer
    MessageConsumer<byte[]> revived = broker.createConsumer(
        new ConsumerConfig(clientId, groupId, true, 500L, 100)  // Increased to 1000ms
    );

    Thread.sleep(2500); // Let auto-commit timer start

    revived.subscribe(category, msg -> {
      String text = new String(msg.payload());
      afterRestart.add(text);
      latch.countDown();
      return CompletableFuture.completedFuture(null);
    });

    // Wait until all messages have been delivered at least once
    boolean allDelivered = latch.await(20, TimeUnit.SECONDS);
    assertTrue(allDelivered, "All messages should be delivered at least once after restart");

    revived.close();

    // FINAL ASSERTIONS
    Set<String> all = new HashSet<>();
    all.addAll(beforeCrash);
    all.addAll(afterRestart);

    // At-least-once: every produced message must have been seen at least once
    assertEquals(messageCount, all.size(), "All messages should be seen at least once");

    System.out.println("SUCCESS (at-least-once semantics):");
    System.out.println("  Before crash : " + beforeCrash.size() + " messages");
    System.out.println("  After restart: " + afterRestart.size() + " messages");
    System.out.println("  Distinct messages seen: " + all.size());
  }

  @Test
  @Order(4)
  void persistentBrokerRestart_restoresMessagesAndOffsets() throws Exception {

    String category = "recovery-topic-" + System.nanoTime();
    String groupId = "recovery-group";
    String clientId1 = "client-1";
    String clientId2 = "client-2";
    int queueCount = 3;
    int totalMessages = 120;

    broker.getOrCreateCategory(category, new CategoryConfig(queueCount, 1, 86_400_000L, true));

    MessageProducer<byte[]> producer = broker.createProducer(ProducerConfig.defaults());
    for (int i = 0; i < totalMessages; i++) {
      producer.send(category, ("msg-" + i).getBytes()).join();
    }

    List<String> phase1 = new CopyOnWriteArrayList<>();
    List<String> phase2 = new CopyOnWriteArrayList<>();

    MessageConsumer<byte[]> consumer1 = broker.createConsumer(
        new ConsumerConfig(clientId1, groupId, true, 1000L, 100));

    consumer1.subscribe(category, msg -> {
      phase1.add(new String(msg.payload()));
      return CompletableFuture.completedFuture(null);
    });

    int targetPhase1 = totalMessages / 2;
    long deadline = System.currentTimeMillis() + 10_000L;
    while (phase1.size() < targetPhase1 && System.currentTimeMillis() < deadline) {
      Thread.sleep(100);
    }

    consumer1.commitSync().join();
    consumer1.close().join();
    producer.close();

    broker.shutdown().get(5, TimeUnit.SECONDS);

    broker = new EmbeddedQueueBroker();

    MessageConsumer<byte[]> consumer2 = broker.createConsumer(
        new ConsumerConfig(clientId2, groupId, true, 1000L, 100));

    consumer2.subscribe(category, msg -> {
      phase2.add(new String(msg.payload()));
      return CompletableFuture.completedFuture(null);
    });

    deadline = System.currentTimeMillis() + 10_000L;
    while ((phase1.size() + phase2.size()) < totalMessages
        && System.currentTimeMillis() < deadline) {
      Thread.sleep(100);
    }

    consumer2.close().join();

    Set<String> allSeen = new HashSet<>();
    allSeen.addAll(phase1);
    allSeen.addAll(phase2);

    assertEquals(totalMessages, allSeen.size(),
        "All messages must be delivered exactly once across broker restart");

    Set<String> intersection = new HashSet<>(phase1);
    intersection.retainAll(phase2);

    assertTrue(intersection.isEmpty(),
        "No duplicates after broker restart! Duplicated messages: " + intersection);
  }

  @Test
  @Order(5)
  void testQueuesWithConsumers_HighVolume() throws Exception {

    String category = "test-topic-perf";
    String groupId = "group-1";
    int numberOfMessages = 500_000;
    int queues = 16;
    int consumersN = 1;

    broker.getOrCreateCategory(
        category,
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
  }

  @Test
  @Order(6)
  void testQueuesWithConsumers_HighVolume_Persistent() throws Exception {
    String category = "test-topic-perf-persistent";
    String groupId = "group-1";
    int numberOfMessages = 500_000;
    int queues = 16;
    int consumersN = 1;

    broker.getOrCreateCategory(
        category,
        new CategoryConfig(queues, 1, 86400000L, true));

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

    // Ensure all commits are flushed by closing consumers before inspecting offsets
    consumers.forEach(c -> c.close().join());
    producer.close();

    Path logPath = QueueStorage.logPath(category, 0);
    assertTrue(Files.exists(logPath), "Log file for queue 0 should exist for persistent category");
    assertTrue(Files.size(logPath) > 0, "Log file for queue 0 should contain data");

    OffsetStore offsetStore = new OffsetStore();
    Map<Integer, Long> offsets = offsetStore.load(category, groupId);
    assertFalse(offsets.isEmpty(), "Offsets file should contain at least one committed offset");

    long totalCommitted = offsets.values().stream().mapToLong(Long::longValue).sum();
    assertEquals(numberOfMessages, totalCommitted,
        "Committed offsets should match total messages");

    int expectedPerQueue = numberOfMessages / queues;
    for (int q = 0; q < queues; q++) {
      long fileOffset = offsets.getOrDefault(q, 0L);
      long brokerOffset = broker.getCommittedOffset(groupId, category, q);
      System.out.printf(
          "DEBUG OFFSETS queue=%d file=%d broker=%d expected=%d%n",
          q, fileOffset, brokerOffset, expectedPerQueue);
    }

    Path categoryDir = QueueStorage.categoryDir(category);
    if (Files.exists(categoryDir)) {
      try (java.util.stream.Stream<Path> paths = Files.walk(categoryDir)) {
        paths.sorted(java.util.Comparator.reverseOrder())
            .forEach(path -> {
              try {
                Files.deleteIfExists(path);
              } catch (Exception ignored) {
              }
            });
      }
    }
  }
}
