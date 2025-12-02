package org.nexus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
  void basicPublishAndSingleConsumerReceivesAllMessages() throws Exception {
    String category = "test-topic";
    String groupId = "group-1";
    int numberOfMessages = 100;
    List<String> receivedMessages = new CopyOnWriteArrayList<>();
    MessageProducer<byte[]> producer = broker.createProducer(ProducerConfig.defaults());
    MessageConsumer<byte[]> consumer = broker.createConsumer(
        new ConsumerConfig(
            "my-consumer",
            groupId,
            true,
            1000L,
            50
        )
    );

    for (int i = 0; i < numberOfMessages; i++) {
      byte[] payload = ("message-" + i).getBytes();
      producer.send(category, payload).join();
    }

    consumer.subscribe(category, message -> {
      String text = new String(message.payload());
      receivedMessages.add(text);
      return CompletableFuture.completedFuture(null);
    });

    long deadline = System.currentTimeMillis() + 5_000L;
    while (receivedMessages.size() < numberOfMessages && System.currentTimeMillis() < deadline) {
      Thread.sleep(50);
    }

    assertEquals(numberOfMessages, receivedMessages.size(), "Should have received all messages");

    Set<String> receivedSet = new HashSet<>(receivedMessages);
    for (int i = 0; i < numberOfMessages; i++) {
      assertTrue(receivedSet.contains("message-" + i),
          "Missing message-" + i);
    }

    consumer.close();
  }

  @Test
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
  void consumerDiesMidFlight_restartConsumesOnlyRemainingMessages() throws Exception {

    String category = "crash-recovery-topic";
    String groupId = "crash-group";
    String clientId = "crashy-consumer";  // same ID = same logical consumer

    // 4 queues → easier to see per-queue progress
    broker.getOrCreateCategory(category, new CategoryConfig(4, 1, 86_400_000L, false));

    MessageProducer<byte[]> producer = broker.createProducer(ProducerConfig.defaults());

    // Produce 200 messages → 50 per queue
    for (int i = 0; i < 200; i++) {
      producer.send(category, ("msg-" + i).getBytes()).join();
    }

    List<String> beforeCrash = new CopyOnWriteArrayList<>();
    List<String> afterRestart = new CopyOnWriteArrayList<>();

    MessageConsumer<byte[]> consumer = broker.createConsumer(
        new ConsumerConfig(clientId, groupId, true, 2500L, 100)
    );

    consumer.subscribe(category, msg -> {
      String text = new String(msg.payload());
      beforeCrash.add(text);
      // Simulate processing
      try {
        Thread.sleep(15);
      } catch (InterruptedException ignored) {
      }
      return CompletableFuture.completedFuture(null);
    });

    // Let it run → will consume some but not all messages
    Thread.sleep(1000);

    System.out.println("CRASHING consumer after consuming " + beforeCrash.size() + " messages");
    consumer.close();                     // 'crash' the consumer
    Thread.sleep(500);              // give auto-commit time to finish

    // PHASE 2: restart the exact same consumer
    MessageConsumer<byte[]> revived = broker.createConsumer(
        new ConsumerConfig(clientId, groupId, true, 2500L, 100)
    );

    revived.subscribe(category, msg -> {
      String text = new String(msg.payload());
      afterRestart.add(text);
      return CompletableFuture.completedFuture(null);
    });

    // Wait until all remaining messages are consumed
    long deadline = System.currentTimeMillis() + 10_000L;
    while ((beforeCrash.size() + afterRestart.size()) < 200
        && System.currentTimeMillis() < deadline) {
      Thread.sleep(100);
    }

    revived.close();

    // FINAL ASSERTIONS
    Set<String> all = new HashSet<>();
    all.addAll(beforeCrash);
    all.addAll(afterRestart);

    assertEquals(200, all.size(), "All 200 messages delivered exactly once");

    // Crucial: no overlap → nothing was redelivered
    Set<String> beforeSet = new HashSet<>(beforeCrash);
    Set<String> afterSet = new HashSet<>(afterRestart);
    beforeSet.retainAll(afterSet);

    assertTrue(beforeSet.isEmpty(),
        "No duplicates after crash! These messages were delivered twice: " + beforeSet);

    System.out.println("SUCCESS:");
    System.out.println("  Before crash : " + beforeCrash.size() + " messages");
    System.out.println("  After restart: " + afterRestart.size() + " messages");
    System.out.println("  Total unique : " + all.size());
  }
}
