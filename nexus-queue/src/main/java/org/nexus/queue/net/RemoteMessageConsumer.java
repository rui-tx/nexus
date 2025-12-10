package org.nexus.queue.net;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.time.Instant;
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
import org.nexus.domain.MessageId;
import org.nexus.domain.MessageMetadata;
import org.nexus.interfaces.Deserializer;
import org.nexus.interfaces.MessageConsumer;
import org.nexus.interfaces.MessageHandler;

public class RemoteMessageConsumer<T> implements MessageConsumer<T> {

  private static final int DEFAULT_BATCH_SIZE = 512;
  private static final int DEFAULT_POLL_RATE_MS = 128;
  private static final int MAX_IN_FLIGHT_PER_QUEUE = 1024;
  private static final byte CURRENT_API_VERSION = 1;
  private static final int DEFAULT_SOCKET_TIMEOUT_MS = 5_000;

  private final String host;
  private final int port;
  private final ConsumerConfig config;
  private final Deserializer<T> deserializer;

  private final Map<String, MessageHandler<T>> subscriptions = new ConcurrentHashMap<>();
  private final Map<String, List<Integer>> assignedQueuesByCategory = new ConcurrentHashMap<>();
  private final Map<String, Map<Integer, QueueState>> queueStates = new ConcurrentHashMap<>();
  private final Map<String, Map<Integer, Long>> pendingCommits = new ConcurrentHashMap<>();

  private final AtomicBoolean running = new AtomicBoolean(false);
  private final AtomicBoolean paused = new AtomicBoolean(false);
  private final AtomicBoolean closed = new AtomicBoolean(false);

  private final ScheduledExecutorService pollExecutor;
  private final ExecutorService handlerExecutor;

  private final ScheduledExecutorService commitTimerExecutor;
  private final ExecutorService commitWorkExecutor;

  private final AtomicInteger correlationCounter = new AtomicInteger(1);

  private Socket socket;
  private InputStream input;
  private OutputStream output;

  private final Object ioLock = new Object();

  public RemoteMessageConsumer(
      String host,
      int port,
      ConsumerConfig config,
      Deserializer<T> deserializer
  ) {
    this.host = host;
    this.port = port;
    this.config = config;
    this.deserializer = deserializer;

    this.pollExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
      Thread t = new Thread(r, "remote-consumer-poll-" + config.clientId());
      t.setDaemon(true);
      return t;
    });

    this.handlerExecutor = Executors.newFixedThreadPool(
        Math.max(2, Runtime.getRuntime().availableProcessors()),
        r -> {
          Thread t = new Thread(r, "remote-consumer-handler-" + config.clientId());
          t.setDaemon(true);
          return t;
        }
    );

    if (config.autoCommit()) {
      this.commitTimerExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "remote-consumer-commit-timer-" + config.clientId());
        t.setDaemon(true);
        return t;
      });

      this.commitWorkExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "remote-consumer-commit-work-" + config.clientId());
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

      SubscribeResponse subscribeResponse = sendSubscribe(category);
      if (subscribeResponse.errorCode() != 0) {
        throw new IllegalStateException(
            "Failed to subscribe remotely to category '" + category + "' with error code "
                + subscribeResponse.errorCode());
      }

      List<Integer> queues = new ArrayList<>(subscribeResponse.assignedQueues());
      assignedQueuesByCategory.put(category, queues);
    }

    if (running.compareAndSet(false, true)) {
      startPolling();
    }

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
    assignedQueuesByCategory.remove(category);

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

      unsubscribeAll();

      pollExecutor.shutdown();
      handlerExecutor.shutdown();

      try {
        pollExecutor.awaitTermination(5, TimeUnit.SECONDS);
        handlerExecutor.awaitTermination(5, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }

      commitOffsetsFromQueueStates();
      commitPendingOffsets();

      if (commitTimerExecutor != null) {
        commitTimerExecutor.shutdown();
      }
      if (commitWorkExecutor != null) {
        commitWorkExecutor.shutdown();
      }

      try {
        if (commitTimerExecutor != null) {
          commitTimerExecutor.awaitTermination(5, TimeUnit.SECONDS);
        }
        if (commitWorkExecutor != null) {
          commitWorkExecutor.awaitTermination(5, TimeUnit.SECONDS);
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }

      synchronized (ioLock) {
        closeCurrentConnection();
      }
    }
    return CompletableFuture.completedFuture(null);
  }

  private void startPolling() {
    pollExecutor.scheduleWithFixedDelay(
        this::pollMessages,
        0,
        DEFAULT_POLL_RATE_MS,
        TimeUnit.MILLISECONDS
    );
  }

  private void pollMessages() {
    if (!running.get() || paused.get() || closed.get()) {
      return;
    }

    try {
      for (Map.Entry<String, MessageHandler<T>> entry : subscriptions.entrySet()) {
        String category = entry.getKey();
        MessageHandler<T> handler = entry.getValue();

        sendHeartbeat(category);

        List<Integer> queues = assignedQueuesByCategory.get(category);
        if (queues == null || queues.isEmpty()) {
          continue;
        }

        for (int queueId : queues) {
          pollQueue(category, queueId, handler);
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private void pollQueue(String category, int queueId, MessageHandler<T> handler) {
    Map<Integer, QueueState> categoryStates = queueStates
        .computeIfAbsent(category, _ -> new ConcurrentHashMap<>());

    QueueState state = categoryStates.computeIfAbsent(queueId, _ -> new QueueState());

    if (state.initialized.compareAndSet(false, true)) {
      long committedOffset = 0L;
      state.nextFetchOffset.set(committedOffset);
    }

    if (state.inFlightCount.get() >= MAX_IN_FLIGHT_PER_QUEUE) {
      return;
    }

    long fromOffset = state.nextFetchOffset.get();

    FetchResponse fetchResponse = sendFetch(category, queueId, fromOffset,
        config.maxPollRecords());
    if (fetchResponse.messageCount() == 0) {
      return;
    }

    for (FetchResponse.Message networkMessage : fetchResponse.messages()) {
      if (!running.get() || paused.get()) {
        break;
      }

      long messageOffset = networkMessage.offset();
      if (messageOffset < fromOffset) {
        continue;
      }

      state.nextFetchOffset.set(messageOffset + 1);

      MessageId messageId = new MessageId(networkMessage.messageId());
      MessageMetadata metadata = new MessageMetadata(
          messageId,
          category,
          networkMessage.key(),
          Instant.ofEpochMilli(networkMessage.timestamp()),
          networkMessage.queueId(),
          networkMessage.offset(),
          networkMessage.headers()
      );

      T payload = deserializer.deserialize(category, networkMessage.payload());
      Message<T> message = new Message<>(metadata, payload);

      state.inFlightCount.incrementAndGet();
      state.deliveredCount.incrementAndGet();

      CompletableFuture<Void> processingFuture;
      try {
        processingFuture = CompletableFuture
            .supplyAsync(() -> handler.handle(message), handlerExecutor)
            .thenCompose(f -> f);
      } catch (Exception e) {
        state.inFlightCount.decrementAndGet();
        break;
      }

      processingFuture.whenComplete((ignored, throwable) -> {
        try {
          if (throwable == null) {
            state.handledCount.incrementAndGet();

            long newOffset = messageOffset + 1;
            if (state.processedSinceLastCommit.incrementAndGet() >= getBatchSize()) {
              commitQueueOffset(category, queueId, newOffset);
              state.processedSinceLastCommit.set(0);
            } else {
              pendingCommits
                  .computeIfAbsent(category, _ -> new ConcurrentHashMap<>())
                  .put(queueId, newOffset);
            }
          }
        } finally {
          state.inFlightCount.decrementAndGet();
        }
      });
    }
  }

  private void commitOffsetsFromQueueStates() {
    Map<String, Map<Integer, Long>> offsetsByCategory = new HashMap<>();
    queueStates.forEach((category, queues) -> {
      Map<Integer, Long> queueOffsets = new HashMap<>();
      queues.forEach((queueId, state) -> {
        long offset = state.nextFetchOffset.get();
        if (offset > 0) {
          queueOffsets.put(queueId, offset);
        }
      });
      if (!queueOffsets.isEmpty()) {
        offsetsByCategory.put(category, queueOffsets);
      }
    });

    offsetsByCategory.forEach(this::sendCommitOffsets);
  }

  private void commitPendingOffsets() {
    if (pendingCommits.isEmpty()) {
      return;
    }

    Map<String, Map<Integer, Long>> commitsToProcess = new HashMap<>();
    pendingCommits.forEach((category, queues) ->
        commitsToProcess.put(category, new HashMap<>(queues))
    );

    commitsToProcess.forEach(this::sendCommitOffsets);
  }

  private void commitQueueOffset(String category, int queue, long offset) {
    Map<Integer, Long> offsets = Map.of(queue, offset);
    sendCommitOffsets(category, offsets);
  }

  private void autoCommit() {
    if (!config.autoCommit() || commitWorkExecutor == null) {
      return;
    }

    commitWorkExecutor.submit(() -> {
      try {
        commitPendingOffsets();
      } catch (Exception ignored) {
      }
    });
  }

  private int getBatchSize() {
    return DEFAULT_BATCH_SIZE;
  }

  private SubscribeResponse sendSubscribe(String category) {
    int correlationId = correlationCounter.getAndIncrement();
    QueueFrameHeader header = new QueueFrameHeader(
        QueueApiKey.SUBSCRIBE_REQUEST,
        CURRENT_API_VERSION,
        correlationId
    );

    SubscribeRequest request = new SubscribeRequest(
        header,
        category,
        config.consumerGroup(),
        config.clientId()
    );

    ByteBuffer encoded = QueueNetworkProtocol.encodeSubscribeRequest(request);
    return sendAndReceive(encoded, QueueNetworkProtocol::decodeSubscribeResponse);
  }

  private void sendHeartbeat(String category) {
    int correlationId = correlationCounter.getAndIncrement();
    QueueFrameHeader header = new QueueFrameHeader(
        QueueApiKey.HEARTBEAT_REQUEST,
        CURRENT_API_VERSION,
        correlationId
    );

    HeartbeatRequest request = new HeartbeatRequest(
        header,
        category,
        config.consumerGroup(),
        config.clientId()
    );

    ByteBuffer encoded = QueueNetworkProtocol.encodeHeartbeatRequest(request);
    HeartbeatResponse response = sendAndReceive(encoded, QueueNetworkProtocol::decodeHeartbeatResponse);
    if (response.errorCode() != 0) {
      throw new IllegalStateException("Heartbeat failed with error code " + response.errorCode());
    }

    assignedQueuesByCategory.put(category, response.assignedQueues());
  }

  private FetchResponse sendFetch(String category, int queueId, long fromOffset, int maxRecords) {
    int correlationId = correlationCounter.getAndIncrement();
    QueueFrameHeader header = new QueueFrameHeader(
        QueueApiKey.FETCH_REQUEST,
        CURRENT_API_VERSION,
        correlationId
    );

    FetchRequest request = new FetchRequest(
        header,
        category,
        config.consumerGroup(),
        config.clientId(),
        queueId,
        fromOffset,
        maxRecords,
        0
    );

    ByteBuffer encoded = QueueNetworkProtocol.encodeFetchRequest(request);
    return sendAndReceive(encoded, QueueNetworkProtocol::decodeFetchResponse);
  }

  private void sendCommitOffsets(String category, Map<Integer, Long> offsets) {
    if (offsets.isEmpty()) {
      return;
    }

    int correlationId = correlationCounter.getAndIncrement();
    QueueFrameHeader header = new QueueFrameHeader(
        QueueApiKey.COMMIT_OFFSET_REQUEST,
        CURRENT_API_VERSION,
        correlationId
    );

    CommitOffsetRequest request = new CommitOffsetRequest(
        header,
        category,
        config.consumerGroup(),
        offsets
    );

    ByteBuffer encoded = QueueNetworkProtocol.encodeCommitOffsetRequest(request);
    CommitOffsetResponse response = sendAndReceive(encoded,
        QueueNetworkProtocol::decodeCommitOffsetResponse);
    if (response.errorCode() != 0) {
      throw new IllegalStateException("CommitOffset failed with error code " + response.errorCode());
    }
  }

  private void closeCurrentConnection() {
    if (socket != null) {
      try {
        socket.close();
      } catch (IOException ignored) {
      } finally {
        socket = null;
        input = null;
        output = null;
      }
    }
  }

  private void ensureConnected() throws IOException {
    if (socket != null && socket.isConnected() && !socket.isClosed()) {
      return;
    }

    Socket s = new Socket(host, port);
    s.setSoTimeout(DEFAULT_SOCKET_TIMEOUT_MS);

    input = s.getInputStream();
    output = s.getOutputStream();
    socket = s;
  }

  private <R> R sendAndReceive(ByteBuffer encoded,
      java.util.function.Function<ByteBuffer, R> decoder) {
    byte[] frame = new byte[encoded.remaining()];
    encoded.get(frame);

    synchronized (ioLock) {
      try {
        ensureConnected();

        output.write(frame);
        output.flush();

        byte[] lenBytes = readFully(input, Integer.BYTES);
        int frameLength = ByteBuffer.wrap(lenBytes).getInt();
        byte[] body = readFully(input, frameLength);

        byte[] all = new byte[Integer.BYTES + frameLength];
        System.arraycopy(lenBytes, 0, all, 0, Integer.BYTES);
        System.arraycopy(body, 0, all, Integer.BYTES, frameLength);

        return decoder.apply(ByteBuffer.wrap(all));
      } catch (IOException e) {
        closeCurrentConnection();
        throw new RuntimeException("Remote consumer I/O failure", e);
      }
    }
  }

  private static byte[] readFully(InputStream in, int length) throws IOException {
    byte[] data = new byte[length];
    int offset = 0;
    while (offset < length) {
      int read = in.read(data, offset, length - offset);
      if (read == -1) {
        throw new IOException("Unexpected end of stream");
      }
      offset += read;
    }
    return data;
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
