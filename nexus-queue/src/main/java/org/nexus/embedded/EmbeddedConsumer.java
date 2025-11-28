package org.nexus.embedded;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.nexus.domain.ConsumerConfig;
import org.nexus.domain.Message;
import org.nexus.domain.StoredMessage;
import org.nexus.interfaces.Deserializer;
import org.nexus.interfaces.MessageConsumer;
import org.nexus.interfaces.MessageHandler;

/**
 * Embedded consumer implementation. Polls messages from broker and dispatches to handler.
 */
public class EmbeddedConsumer<T> implements MessageConsumer<T> {

  private final EmbeddedQueueBroker broker;
  private final ConsumerConfig config;
  private final Deserializer<T> deserializer;

  // Subscriptions
  private final Map<String, MessageHandler<T>> subscriptions = new ConcurrentHashMap<>();

  // Offset tracking per partition
  private final Map<String, Map<Integer, Long>> offsets = new ConcurrentHashMap<>();

  // Polling control
  private final AtomicBoolean running = new AtomicBoolean(false);
  private final AtomicBoolean paused = new AtomicBoolean(false);
  private final AtomicBoolean closed = new AtomicBoolean(false);

  // Polling thread
  private final ScheduledExecutorService pollExecutor;
  private final ExecutorService handlerExecutor;

  // Auto-commit tracking
  private final ScheduledExecutorService commitExecutor;

  public EmbeddedConsumer(
      EmbeddedQueueBroker broker,
      ConsumerConfig config,
      Deserializer<T> deserializer
  ) {
    this.broker = broker;
    this.config = config;
    this.deserializer = deserializer;

    // Create polling thread
    this.pollExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
      Thread t = new Thread(r, "consumer-poll-" + config.clientId());
      t.setDaemon(true);
      return t;
    });

    // Create handler thread pool
    this.handlerExecutor = Executors.newFixedThreadPool(
        Math.max(2, Runtime.getRuntime().availableProcessors()),
        r -> {
          Thread t = new Thread(r, "consumer-handler-" + config.clientId());
          t.setDaemon(true);
          return t;
        }
    );

    // Auto-commit executor
    if (config.autoCommit()) {
      this.commitExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "consumer-commit-" + config.clientId());
        t.setDaemon(true);
        return t;
      });

      // Schedule auto-commit
      commitExecutor.scheduleAtFixedRate(
          this::autoCommit,
          config.autoCommitIntervalMs(),
          config.autoCommitIntervalMs(),
          TimeUnit.MILLISECONDS
      );
    } else {
      this.commitExecutor = null;
    }
  }

  @Override
  public void subscribe(String topic, MessageHandler<T> handler) {
    subscribe(new String[]{topic}, handler);
  }

  @Override
  public void subscribe(String[] topics, MessageHandler<T> handler) {
    for (String topic : topics) {
      subscriptions.put(topic, handler);
      offsets.putIfAbsent(topic, new ConcurrentHashMap<>());

      // Register with broker
      broker.registerConsumer(config.consumerGroup(), config.clientId(), topic, config);
    }

    // Start polling if not already running
    if (running.compareAndSet(false, true)) {
      startPolling();
    }
  }

  @Override
  public void unsubscribe(String topic) {
    subscriptions.remove(topic);
    offsets.remove(topic);

    // Unregister from broker
    broker.unregisterConsumer(config.consumerGroup(), config.clientId(), topic);

    // Stop polling if no more subscriptions
    if (subscriptions.isEmpty()) {
      running.set(false);
    }
  }

  @Override
  public void unsubscribeAll() {
    for (String topic : new ArrayList<>(subscriptions.keySet())) {
      unsubscribe(topic);
    }
  }

  @Override
  public CompletableFuture<Void> commitSync() {
    return CompletableFuture.runAsync(() -> {
      for (Map.Entry<String, Map<Integer, Long>> entry : offsets.entrySet()) {
        String topic = entry.getKey();
        for (Map.Entry<Integer, Long> partitionOffset : entry.getValue().entrySet()) {
          broker.commitOffset(
              config.consumerGroup(),
              topic,
              partitionOffset.getKey(),
              partitionOffset.getValue()
          );
        }
      }
    });
  }

  @Override
  public void pause() {
    paused.set(true);
  }

  @Override
  public void resume() {
    paused.set(false);
  }

  @Override
  public CompletableFuture<Void> close() {
    if (closed.compareAndSet(false, true)) {
      running.set(false);
      unsubscribeAll();

      // Shutdown executors
      pollExecutor.shutdown();
      handlerExecutor.shutdown();
      if (commitExecutor != null) {
        commitExecutor.shutdown();
      }

      try {
        pollExecutor.awaitTermination(5, TimeUnit.SECONDS);
        handlerExecutor.awaitTermination(5, TimeUnit.SECONDS);
        if (commitExecutor != null) {
          commitExecutor.awaitTermination(5, TimeUnit.SECONDS);
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }

    return CompletableFuture.completedFuture(null);
  }

  /**
   * Start the polling loop
   */
  private void startPolling() {
    pollExecutor.scheduleWithFixedDelay(
        this::pollMessages,
        0,
        100, // Poll every 100ms
        TimeUnit.MILLISECONDS
    );
  }

  /**
   * Poll messages from broker
   */
  private void pollMessages() {
    if (!running.get() || paused.get() || closed.get()) {
      return;
    }

    try {
      for (Map.Entry<String, MessageHandler<T>> entry : subscriptions.entrySet()) {
        String topic = entry.getKey();
        MessageHandler<T> handler = entry.getValue();

        // Get assigned partitions
        List<Integer> partitions = broker.getAssignedPartitions(
            config.consumerGroup(),
            config.clientId(),
            topic
        );

        for (int partition : partitions) {
          pollPartition(topic, partition, handler);
        }
      }
    } catch (Exception e) {
      System.err.println("Error polling messages: " + e.getMessage());
      e.printStackTrace();
    }
  }

  /**
   * Poll messages from a specific partition
   */
  private void pollPartition(String topic, int partition, MessageHandler<T> handler) {
    // Get current offset for this partition
    long currentOffset = offsets.get(topic)
        .getOrDefault(partition, getCommittedOffset(topic, partition));

    // Fetch messages
    List<StoredMessage> messages = broker.fetchMessages(
        topic,
        partition,
        currentOffset,
        config.maxPollRecords()
    );

    if (messages.isEmpty()) {
      return;
    }

    // Process messages
    for (StoredMessage storedMessage : messages) {
      if (!running.get() || paused.get()) {
        break;
      }

      // Deserialize
      T payload = deserializer.deserialize(topic, storedMessage.payload());

      // Create message
      Message<T> message = new Message<>(
          storedMessage.metadata(),
          payload
      );

      // Handle message asynchronously
      CompletableFuture<Void> handleFuture = CompletableFuture
          .runAsync(() -> {
            try {
              handler.handle(message).join();
            } catch (Exception e) {
              System.err.println("Error handling message: " + e.getMessage());
              // TODO: Implement retry logic or DLQ
            }
          }, handlerExecutor);

      // Wait for handler to complete (blocking for simplicity)
      // In production, you'd want better flow control
      try {
        handleFuture.get(30, TimeUnit.SECONDS);

        // Update offset
        offsets.get(topic).put(partition, storedMessage.offset() + 1);

      } catch (Exception e) {
        System.err.println("Handler timeout or error: " + e.getMessage());
        break; // Stop processing this partition
      }
    }
  }

  /**
   * Get committed offset from broker
   */
  private long getCommittedOffset(String topic, int partition) {
    return broker.getCommittedOffset(config.consumerGroup(), topic, partition);
  }

  /**
   * Auto-commit offsets
   */
  private void autoCommit() {
    if (config.autoCommit() && !offsets.isEmpty()) {
      commitSync().join();
    }
  }

  public ConsumerConfig getConfig() {
    return config;
  }

  public boolean isClosed() {
    return closed.get();
  }

  public boolean isPaused() {
    return paused.get();
  }
}
