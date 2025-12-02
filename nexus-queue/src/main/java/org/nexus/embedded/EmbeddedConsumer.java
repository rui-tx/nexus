package org.nexus.embedded;

import java.util.ArrayList;
import java.util.HashMap;
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

  // Offset tracking per queue
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

    // Create a polling thread
    this.pollExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
      Thread t = new Thread(r, "consumer-poll-" + config.clientId());
      t.setDaemon(true);
      return t;
    });

    // Create a handler thread pool
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
  public void subscribe(String category, MessageHandler<T> handler) {
    subscribe(new String[]{category}, handler);
  }

  @Override
  public void subscribe(String[] categories, MessageHandler<T> handler) {
    for (String category : categories) {
      subscriptions.put(category, handler);
      offsets.putIfAbsent(category, new ConcurrentHashMap<>());

      // Register with broker
      broker.registerConsumer(config.consumerGroup(), config.clientId(), category, config);
    }

    // Start polling if not already running
    if (running.compareAndSet(false, true)) {
      startPolling();
    }
  }

  @Override
  public void unsubscribe(String category) {
    subscriptions.remove(category);
    offsets.remove(category);

    // Unregister from broker
    broker.unregisterConsumer(config.consumerGroup(), config.clientId(), category);

    // Stop polling if no more subscriptions
    if (subscriptions.isEmpty()) {
      running.set(false);
    }
  }

  @Override
  public void unsubscribeAll() {
    for (String category : new ArrayList<>(subscriptions.keySet())) {
      unsubscribe(category);
    }
  }

  @Override
  public CompletableFuture<Void> commitSync() {
    return CompletableFuture.runAsync(this::autoCommit);
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

      // Stop accepting new messages
      unsubscribeAll();

      // Shutdown executors
      pollExecutor.shutdown();
      handlerExecutor.shutdown();
      if (commitExecutor != null) {
        commitExecutor.shutdown();
      }

      try {
        // Wait for pending operations to complete
        pollExecutor.awaitTermination(5, TimeUnit.SECONDS);
        handlerExecutor.awaitTermination(5, TimeUnit.SECONDS);
        if (commitExecutor != null) {
          commitExecutor.awaitTermination(5, TimeUnit.SECONDS);
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        System.err.println("Interrupted during shutdown: " + e.getMessage());
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
        String category = entry.getKey();
        MessageHandler<T> handler = entry.getValue();

        // Get assigned queues
        List<Integer> queues = broker.getAssignedQueues(
            config.consumerGroup(),
            config.clientId(),
            category
        );

        for (int queue : queues) {
          pollQueue(category, queue, handler);
        }
      }
    } catch (Exception e) {
      System.err.println("Error polling messages: " + e.getMessage());
      e.printStackTrace();
    }
  }

  /**
   * Poll messages from a specific queue
   */
  private void pollQueue(String category, int queue, MessageHandler<T> handler) {
    // Initialize the topic in the offsets map if it doesn't exist
    Map<Integer, Long> queueOffsets = offsets.computeIfAbsent(category,
        _ -> new ConcurrentHashMap<>());

    // Always get the committed offset from the broker
    long currentOffset = getCommittedOffset(category, queue);

    // If we have a newer offset locally, use that (but log a warning)
    Long localOffset = queueOffsets.get(queue);
    if (localOffset != null && localOffset > currentOffset) {
      System.err.println("WARN: Local offset (" + localOffset +
          ") is ahead of committed offset (" + currentOffset +
          ") for " + category + "-" + queue);
      currentOffset = localOffset;
    }

    // Fetch messages
    List<StoredMessage> messages = broker.fetchMessages(
        category,
        queue,
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

      // Skip if we've already processed this message
      if (storedMessage.offset() < currentOffset) {
        continue;
      }

      // Deserialize and process
      T payload = deserializer.deserialize(category, storedMessage.payload());
      Message<T> message = new Message<>(storedMessage.metadata(), payload);

      try {
        // Process the message
        handler.handle(message).get(30, TimeUnit.SECONDS);

        // Update offset and commit
        long newOffset = storedMessage.offset() + 1;
        queueOffsets.put(queue, newOffset);

        // synchronous for now. TODO: implement batching
        broker.commitOffset(
            config.consumerGroup(),
            category,
            queue,
            newOffset
        );

      } catch (Exception e) {
        System.err.println("Error handling message at offset " +
            storedMessage.offset() + ": " + e.getMessage());
        break;
      }
    }
  }

  /**
   * Get committed offset from broker
   */
  private long getCommittedOffset(String category, int queue) {
    return broker.getCommittedOffset(config.consumerGroup(), category, queue);
  }

  /**
   * Auto-commit offsets
   */
  private void autoCommit() {
    if (!config.autoCommit() || offsets.isEmpty()) {
      return;
    }

    // Make a copy of the current offsets to avoid concurrent modification
    Map<String, Map<Integer, Long>> offsetsCopy = new HashMap<>();
    for (Map.Entry<String, Map<Integer, Long>> entry : offsets.entrySet()) {
      offsetsCopy.put(entry.getKey(), new HashMap<>(entry.getValue()));
    }

    // Commit all current offsets
    for (Map.Entry<String, Map<Integer, Long>> topicEntry : offsetsCopy.entrySet()) {
      String topic = topicEntry.getKey();
      for (Map.Entry<Integer, Long> partitionEntry : topicEntry.getValue().entrySet()) {
        broker.commitOffset(
            config.consumerGroup(),
            topic,
            partitionEntry.getKey(),
            partitionEntry.getValue()
        );
      }
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
