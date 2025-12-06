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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.nexus.domain.ConsumerConfig;
import org.nexus.domain.Message;
import org.nexus.domain.StoredMessage;
import org.nexus.interfaces.Deserializer;
import org.nexus.interfaces.MessageConsumer;
import org.nexus.interfaces.MessageHandler;

/**
 * Embedded consumer implementation. Polls messages from broker and dispatches to handler.
 * <p>
 * This implementation currently operates on {@code byte[]} payloads when used with the
 * default {@link EmbeddedQueueBroker}, which provides a {@code byte[]} deserializer.
 */
public class EmbeddedConsumer<T> implements MessageConsumer<T> {

  private static final int DEFAULT_BATCH_SIZE = 512;
  private static final int DEFAULT_POOL_RATE_MS = 128;
  private static final int MAX_IN_FLIGHT_PER_QUEUE = 1024;
  private final Map<String, Map<Integer, Long>> pendingCommits = new ConcurrentHashMap<>();

  private final Map<String, Map<Integer, QueueState>> queueStates = new ConcurrentHashMap<>();

  private final EmbeddedQueueBroker broker;
  private final ConsumerConfig config;
  private final Deserializer<T> deserializer;

  // Subscriptions
  private final Map<String, MessageHandler<T>> subscriptions = new ConcurrentHashMap<>();

  // Polling control
  private final AtomicBoolean running = new AtomicBoolean(false);
  private final AtomicBoolean paused = new AtomicBoolean(false);
  private final AtomicBoolean closed = new AtomicBoolean(false);

  // Polling thread
  private final ScheduledExecutorService pollExecutor;
  private final ExecutorService handlerExecutor;

  // Auto-commit tracking and execution
  private final ScheduledExecutorService commitTimerExecutor;
  private final ExecutorService commitWorkExecutor;


  public EmbeddedConsumer(
      EmbeddedQueueBroker broker,
      ConsumerConfig config,
      Deserializer<T> deserializer
  ) {
    this.broker = broker;
    this.config = config;
    this.deserializer = deserializer;

    // Create a polling thread
    this.pollExecutor = Executors.newSingleThreadScheduledExecutor(
        r -> {
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

    // Auto-commit pools
    if (config.autoCommit()) {
      // Auto-commit timer executor (just for scheduling)
      this.commitTimerExecutor = Executors.newSingleThreadScheduledExecutor(
          r -> {
            Thread t = new Thread(r, "consumer-commit-timer-" + config.clientId());
            t.setDaemon(true);
            return t;
          });

      // Auto-commit work executor (for actual commits)
      this.commitWorkExecutor = Executors.newSingleThreadExecutor(
          r -> {
            Thread t = new Thread(r, "consumer-commit-work-" + config.clientId());
            t.setDaemon(true);
            return t;
          });
    } else {
      this.commitTimerExecutor = null;
      this.commitWorkExecutor = null;
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

      // Register with broker
      broker.registerConsumer(config.consumerGroup(), config.clientId(), category, config);
    }

    // Start polling if not already running
    if (running.compareAndSet(false, true)) {
      startPolling();
    }

    // Start auto-commit timer
    if (config.autoCommit() && commitTimerExecutor != null) {
      commitTimerExecutor.scheduleAtFixedRate(
          this::autoCommit,
          config.autoCommitIntervalMs(),
          config.autoCommitIntervalMs(),
          TimeUnit.MILLISECONDS
      );
    }
  }

  @Override
  public void unsubscribe(String category) {
    subscriptions.remove(category);

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
    commitPendingOffsets();
    return CompletableFuture.completedFuture(null);
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

      // Shutdown executors first to stop processing
      pollExecutor.shutdown();
      handlerExecutor.shutdown();

      // Wait for processing to complete
      try {
        pollExecutor.awaitTermination(5, TimeUnit.SECONDS);
        handlerExecutor.awaitTermination(5, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        System.err.println("Interrupted during executor shutdown: " + e.getMessage());
      }

      // commit any pending offsets after all processing is done
      commitPendingOffsets();

      if (commitTimerExecutor != null) {
        commitTimerExecutor.shutdown();
      }
      if (commitWorkExecutor != null) {
        commitWorkExecutor.shutdown();
      }

      try {
        // Wait for commit executors to finish
        if (commitTimerExecutor != null) {
          commitTimerExecutor.awaitTermination(5, TimeUnit.SECONDS);
        }
        if (commitWorkExecutor != null) {
          commitWorkExecutor.awaitTermination(5, TimeUnit.SECONDS);
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
        DEFAULT_POOL_RATE_MS,
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

        // Heartbeat to keep this consumer marked as active for this category
        broker.heartbeat(config.consumerGroup(), config.clientId(), category);

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
    Map<Integer, QueueState> categoryStates = queueStates
        .computeIfAbsent(category, _ -> new ConcurrentHashMap<>());

    QueueState state = categoryStates.computeIfAbsent(queue, _ -> new QueueState());

    if (state.initialized.compareAndSet(false, true)) {
      long committedOffset = getCommittedOffset(category, queue);
      state.nextFetchOffset.set(committedOffset);
    }

    if (state.inFlightCount.get() >= MAX_IN_FLIGHT_PER_QUEUE) {
      return;
    }

    long fromOffset = state.nextFetchOffset.get();

    List<StoredMessage> messages = broker.fetchMessages(
        category,
        queue,
        fromOffset,
        config.maxPollRecords()
    );

    if (messages.isEmpty()) {
      return;
    }

    for (StoredMessage storedMessage : messages) {
      if (!running.get() || paused.get()) {
        break;
      }

      long messageOffset = storedMessage.offset();

      if (messageOffset < fromOffset) {
        continue;
      }

      state.nextFetchOffset.set(messageOffset + 1);

      T payload = deserializer.deserialize(category, storedMessage.payload());
      Message<T> message = new Message<>(storedMessage.metadata(), payload);

      state.inFlightCount.incrementAndGet();
      state.deliveredCount.incrementAndGet();

      CompletableFuture<Void> processingFuture;
      try {
        processingFuture = CompletableFuture
            .supplyAsync(() -> handler.handle(message), handlerExecutor)
            .thenCompose(f -> f);
      } catch (Exception e) {
        state.inFlightCount.decrementAndGet();
        System.err.println("Error submitting handler for message at offset " +
            messageOffset + ": " + e.getMessage());
        break;
      }

      processingFuture.whenComplete((ignored, throwable) -> {
        try {
          if (throwable == null) {
            state.handledCount.incrementAndGet();

            long newOffset = messageOffset + 1;
            if (state.processedSinceLastCommit.incrementAndGet() >= getBatchSize()) {
              commitQueueOffset(category, queue, newOffset);
              state.processedSinceLastCommit.set(0);
            } else {
              pendingCommits
                  .computeIfAbsent(category, _ -> new ConcurrentHashMap<>())
                  .put(queue, newOffset);
            }
          } else {
            System.err.println("Error handling message at offset " +
                messageOffset + ": " + throwable.getMessage());
          }
        } finally {
          state.inFlightCount.decrementAndGet();
        }
      });
    }
  }

  private void commitPendingOffsets() {
    if (pendingCommits.isEmpty()) {
      return;
    }

    // Make a copy to avoid holding the lock during commit
    Map<String, Map<Integer, Long>> commitsToProcess = new HashMap<>();
    pendingCommits.forEach((category, queues) ->
        commitsToProcess.put(category, new HashMap<>(queues))
    );

    // Commit each offset
    commitsToProcess.forEach((category, queues) -> {
      queues.forEach((queue, offset) -> {
        try {
          broker.commitOffset(
              config.consumerGroup(),
              category,
              queue,
              offset
          );
        } catch (Exception e) {
          System.err.println("Failed to commit offset for " +
              category + "-" + queue + ": " + e.getMessage());
        }
      });
    });

    // Clear only the offsets we've processed
    pendingCommits.forEach((category, queues) -> {
      Map<Integer, Long> committed = commitsToProcess.get(category);
      if (committed != null) {
        committed.keySet().forEach(queues::remove);
        if (queues.isEmpty()) {
          pendingCommits.remove(category);
        }
      }
    });

    // Reset per-queue counters for auto-commit scenarios
    //perQueueProcessedCount.clear();
  }

  /**
   * Commit a single queue's offset immediately
   */
  private void commitQueueOffset(String category, int queue, long offset) {
    try {
      broker.commitOffset(
          config.consumerGroup(),
          category,
          queue,
          offset
      );

      // Remove from pending commits since it's now committed
      Map<Integer, Long> categoryPending = pendingCommits.get(category);
      if (categoryPending != null) {
        categoryPending.remove(queue);
        if (categoryPending.isEmpty()) {
          pendingCommits.remove(category);
        }
      }
    } catch (Exception e) {
      System.err.println("Failed to commit offset for " +
          category + "-" + queue + ": " + e.getMessage());
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
    //System.out.println("AUTO-COMMIT CALLED at " + System.currentTimeMillis());
    if (!config.autoCommit() || commitWorkExecutor == null) {
      return;
    }
    //System.out.println("AUTO-COMMIT RUNNING: pendingCommits size = " + pendingCommits.size());

    // Use separate work executor to avoid timer interference
    commitWorkExecutor.submit(() -> {
      try {
        commitPendingOffsets();
      } catch (Exception e) {
        System.err.println("Auto-commit failed: " + e.getMessage());
      }
    });
  }

  private int getBatchSize() {
    return DEFAULT_BATCH_SIZE;
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

  public long getTotalDeliveredCount(String category) {
    Map<Integer, QueueState> states = queueStates.get(category);
    if (states == null) {
      return 0L;
    }
    long total = 0L;
    for (QueueState state : states.values()) {
      total += state.deliveredCount.get();
    }
    return total;
  }

  public long getTotalHandledCount(String category) {
    Map<Integer, QueueState> states = queueStates.get(category);
    if (states == null) {
      return 0L;
    }
    long total = 0L;
    for (QueueState state : states.values()) {
      total += state.handledCount.get();
    }
    return total;
  }

  private static class QueueState {

    private final AtomicLong nextFetchOffset = new AtomicLong(0L);
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicInteger inFlightCount = new AtomicInteger(0);
    private final AtomicLong deliveredCount = new AtomicLong(0L);
    private final AtomicLong handledCount = new AtomicLong(0L);
    private final AtomicInteger processedSinceLastCommit = new AtomicInteger(0);
  }
}
