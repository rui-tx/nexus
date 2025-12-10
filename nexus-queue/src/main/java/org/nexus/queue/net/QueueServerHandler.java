package org.nexus.queue.net;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.nexus.domain.ConsumerConfig;
import org.nexus.domain.MessageId;
import org.nexus.domain.MessageInput;
import org.nexus.domain.PublishResult;
import org.nexus.domain.StoredMessage;
import org.nexus.embedded.EmbeddedQueueBroker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Sharable
public class QueueServerHandler extends SimpleChannelInboundHandler<ByteBuf> {

  private static final Logger LOGGER = LoggerFactory.getLogger(QueueServerHandler.class);
  private static final byte CURRENT_API_VERSION = 1;

  private final EmbeddedQueueBroker broker;

  public QueueServerHandler(EmbeddedQueueBroker broker) {
    this.broker = broker;
  }

  @Override
  protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
    ByteBuffer buffer = toByteBuffer(msg);

    // Currently we only support ProduceRequest frames
    ByteBuffer headerView = buffer.asReadOnlyBuffer();
    headerView.getInt(); // frameLength
    byte apiKeyId = headerView.get();
    QueueApiKey apiKey = QueueApiKey.fromId(apiKeyId);

    switch (apiKey) {
      case PRODUCE_REQUEST -> handleProduce(ctx, buffer);
      case FETCH_REQUEST -> handleFetch(ctx, buffer);
      case COMMIT_OFFSET_REQUEST -> handleCommitOffset(ctx, buffer);
      case SUBSCRIBE_REQUEST -> handleSubscribe(ctx, buffer);
      case HEARTBEAT_REQUEST -> handleHeartbeat(ctx, buffer);
      default -> {
        LOGGER.warn("Received unsupported api key: {}", apiKey);
        ctx.close();
      }
    }
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
    LOGGER.error("Unexpected exception in QueueServerHandler", cause);
    ctx.close();
  }

  private void handleProduce(ChannelHandlerContext ctx, ByteBuffer buffer) {
    ProduceRequest request = QueueNetworkProtocol.decodeProduceRequest(buffer);

    List<ProduceResponse.Result> results = new ArrayList<>(request.messageCount());

    for (byte[] payload : request.payloads()) {
      try {
        MessageInput input = new MessageInput(
            request.category(),
            request.key(),
            request.headers()
        );

        PublishResult publishResult = broker.publish(request.category(), payload, input);

        MessageId messageId = publishResult.messageId();
        int queueId = publishResult.partition();
        long offset = publishResult.offset();

        results.add(new ProduceResponse.Result(
            messageId.value(),
            queueId,
            offset,
            0
        ));
      } catch (Exception e) {
        LOGGER.error("Failed to publish message for category {}", request.category(), e);
        results.add(new ProduceResponse.Result(
            new java.util.UUID(0L, 0L),
            -1,
            -1,
            1
        ));
      }
    }

    QueueFrameHeader responseHeader = new QueueFrameHeader(
        QueueApiKey.PRODUCE_RESPONSE,
        CURRENT_API_VERSION,
        request.header().correlationId()
    );

    ProduceResponse response = new ProduceResponse(responseHeader, results);
    ByteBuffer encoded = QueueNetworkProtocol.encodeProduceResponse(response);
    writeResponse(ctx, encoded);
  }

  private void handleFetch(ChannelHandlerContext ctx, ByteBuffer buffer) {
    FetchRequest request = QueueNetworkProtocol.decodeFetchRequest(buffer);

    List<Integer> assignedQueues = broker.getAssignedQueues(
        request.groupId(),
        request.clientId(),
        request.category()
    );

    List<StoredMessage> storedMessages;

    if (assignedQueues.contains(request.queueId())) {
      long committedOffset = broker.getCommittedOffset(
          request.groupId(),
          request.category(),
          request.queueId()
      );

      long effectiveFromOffset = Math.max(request.fromOffset(), committedOffset);

      storedMessages = broker.fetchMessages(
          request.category(),
          request.queueId(),
          effectiveFromOffset,
          request.maxRecords()
      );
    } else {
      storedMessages = java.util.Collections.emptyList();
    }

    List<FetchResponse.Message> messages = new ArrayList<>(storedMessages.size());
    for (StoredMessage stored : storedMessages) {
      var metadata = stored.metadata();
      java.util.UUID messageId = metadata.id().value();
      int queueId = metadata.queue();
      long offset = stored.offset();
      long timestamp = stored.timestamp().toEpochMilli();
      String key = metadata.key();
      Map<String, String> headers = metadata.headers();
      byte[] payload = stored.payload();

      messages.add(new FetchResponse.Message(
          messageId,
          queueId,
          offset,
          timestamp,
          key,
          headers,
          payload
      ));
    }

    QueueFrameHeader responseHeader = new QueueFrameHeader(
        QueueApiKey.FETCH_RESPONSE,
        CURRENT_API_VERSION,
        request.header().correlationId()
    );

    FetchResponse response = new FetchResponse(responseHeader, messages);
    ByteBuffer encoded = QueueNetworkProtocol.encodeFetchResponse(response);
    writeResponse(ctx, encoded);
  }

  private void handleCommitOffset(ChannelHandlerContext ctx, ByteBuffer buffer) {
    CommitOffsetRequest request = QueueNetworkProtocol.decodeCommitOffsetRequest(buffer);

    int errorCode = 0;
    try {
      for (Map.Entry<Integer, Long> entry : request.offsets().entrySet()) {
        int queueId = entry.getKey();
        long offset = entry.getValue();
        broker.commitOffset(request.groupId(), request.category(), queueId, offset);
      }
    } catch (Exception e) {
      LOGGER.error("Failed to commit offsets for category {}", request.category(), e);
      errorCode = 1;
    }

    QueueFrameHeader responseHeader = new QueueFrameHeader(
        QueueApiKey.COMMIT_OFFSET_RESPONSE,
        CURRENT_API_VERSION,
        request.header().correlationId()
    );

    CommitOffsetResponse response = new CommitOffsetResponse(responseHeader, errorCode);
    ByteBuffer encoded = QueueNetworkProtocol.encodeCommitOffsetResponse(response);
    writeResponse(ctx, encoded);
  }

  private void handleSubscribe(ChannelHandlerContext ctx, ByteBuffer buffer) {
    SubscribeRequest request = QueueNetworkProtocol.decodeSubscribeRequest(buffer);

    int errorCode = 0;
    List<Integer> assignedQueues;
    try {
      ConsumerConfig config = new ConsumerConfig(
          request.clientId(),
          request.groupId(),
          true,
          1000L,
          1000
      );

      broker.registerConsumer(request.groupId(), request.clientId(), request.category(), config);
      assignedQueues = broker.getAssignedQueues(
          request.groupId(),
          request.clientId(),
          request.category()
      );
    } catch (Exception e) {
      LOGGER.error("Failed to subscribe consumer {} to category {}",
          request.clientId(), request.category(), e);
      errorCode = 1;
      assignedQueues = List.of();
    }

    QueueFrameHeader responseHeader = new QueueFrameHeader(
        QueueApiKey.SUBSCRIBE_RESPONSE,
        CURRENT_API_VERSION,
        request.header().correlationId()
    );

    SubscribeResponse response = new SubscribeResponse(responseHeader, errorCode, assignedQueues);
    ByteBuffer encoded = QueueNetworkProtocol.encodeSubscribeResponse(response);
    writeResponse(ctx, encoded);
  }

  private void handleHeartbeat(ChannelHandlerContext ctx, ByteBuffer buffer) {
    HeartbeatRequest request = QueueNetworkProtocol.decodeHeartbeatRequest(buffer);

    int errorCode = 0;
    List<Integer> assignedQueues = List.of();
    try {
      broker.heartbeat(request.groupId(), request.clientId(), request.category());
      assignedQueues = broker.getAssignedQueues(
          request.groupId(),
          request.clientId(),
          request.category()
      );
    } catch (Exception e) {
      LOGGER.error("Failed to process heartbeat for consumer {} in category {}",
          request.clientId(), request.category(), e);
      errorCode = 1;
    }

    QueueFrameHeader responseHeader = new QueueFrameHeader(
        QueueApiKey.HEARTBEAT_RESPONSE,
        CURRENT_API_VERSION,
        request.header().correlationId()
    );

    HeartbeatResponse response = new HeartbeatResponse(responseHeader, errorCode, assignedQueues);
    ByteBuffer encoded = QueueNetworkProtocol.encodeHeartbeatResponse(response);
    writeResponse(ctx, encoded);
  }

  private static ByteBuffer toByteBuffer(ByteBuf buf) {
    byte[] data = new byte[buf.readableBytes()];
    buf.readBytes(data);
    return ByteBuffer.wrap(data);
  }

  private void writeResponse(ChannelHandlerContext ctx, ByteBuffer encoded) {
    ByteBuf out = ctx.alloc().buffer(encoded.remaining());
    out.writeBytes(encoded);

    ChannelFuture f = ctx.writeAndFlush(out);
    f.addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
  }
}
