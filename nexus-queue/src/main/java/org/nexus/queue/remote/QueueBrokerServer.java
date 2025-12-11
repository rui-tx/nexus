package org.nexus.queue.remote;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import java.net.InetSocketAddress;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.nexus.queue.embedded.EmbeddedQueueBroker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QueueBrokerServer {

  private static final Logger LOGGER = LoggerFactory.getLogger(QueueBrokerServer.class);
  private static final int DEFAULT_MAX_FRAME_LENGTH = 16 * 1024 * 1024; // 16MB

  private final EmbeddedQueueBroker broker;
  private final String bindAddress;
  private final int port;
  private final int maxFrameLength;

  private EventLoopGroup bossGroup;
  private EventLoopGroup workerGroup;
  private Channel serverChannel;

  public QueueBrokerServer(EmbeddedQueueBroker broker, String bindAddress, int port) {
    this(broker, bindAddress, port, DEFAULT_MAX_FRAME_LENGTH);
  }

  public QueueBrokerServer(EmbeddedQueueBroker broker, String bindAddress, int port,
      int maxFrameLength) {
    this.broker = Objects.requireNonNull(broker, "broker cannot be null");
    this.bindAddress = Objects.requireNonNull(bindAddress, "bindAddress cannot be null");
    this.port = port;
    this.maxFrameLength = maxFrameLength;
  }

  public int getPort() {
    if (serverChannel == null) {
      throw new IllegalStateException("Server not started");
    }
    InetSocketAddress addr = (InetSocketAddress) serverChannel.localAddress();
    return addr.getPort();
  }

  public void start() throws InterruptedException {
    if (serverChannel != null) {
      return;
    }

    bossGroup = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
    workerGroup = new MultiThreadIoEventLoopGroup(8, NioIoHandler.newFactory());

    ServerBootstrap b = new ServerBootstrap();
    b.group(bossGroup, workerGroup)
        .channel(NioServerSocketChannel.class)
        .childHandler(new ChannelInitializer<>() {
          @Override
          protected void initChannel(Channel ch) {
            configurePipeline(ch);
          }
        })
        .option(ChannelOption.SO_BACKLOG, 128)
        .childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
        .childOption(ChannelOption.SO_KEEPALIVE, true)
        .childOption(ChannelOption.TCP_NODELAY, true);

    LOGGER.info("Starting QueueBrokerServer on {}:{}", bindAddress, port);
    ChannelFuture f = b.bind(bindAddress, port).sync();
    serverChannel = f.channel();

    int actualPort = getPort();
    LOGGER.info("QueueBrokerServer started on {}:{}", bindAddress, actualPort);
  }

  public void startAndAwait() throws InterruptedException {
    if (serverChannel == null) {
      start();
    }
    try {
      serverChannel.closeFuture().sync();
    } finally {
      stop();
    }
  }

  protected void configurePipeline(Channel ch) {
    ChannelPipeline p = ch.pipeline();

    // Frame decoder for length-prefixed frames.
    // frameLength is a 4-byte int at offset 0, includes header+body.
    p.addLast("frameDecoder", new LengthFieldBasedFrameDecoder(
        maxFrameLength,
        0,
        Integer.BYTES,
        0,
        0
    ));

    p.addLast("queueHandler", new QueueServerHandler(broker));
  }

  public void stop() {
    LOGGER.info("Stopping QueueBrokerServer...");

    if (serverChannel != null) {
      try {
        serverChannel.close().sync();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        LOGGER.warn("Interrupted while waiting for server channel to close", e);
      } finally {
        serverChannel = null;
      }
    }

    if (workerGroup != null) {
      try {
        workerGroup.shutdownGracefully(0, 5, TimeUnit.SECONDS).sync();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        LOGGER.warn("Interrupted while shutting down worker group", e);
      } finally {
        workerGroup = null;
      }
    }

    if (bossGroup != null) {
      try {
        bossGroup.shutdownGracefully(0, 5, TimeUnit.SECONDS).sync();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        LOGGER.warn("Interrupted while shutting down boss group", e);
      } finally {
        bossGroup = null;
      }
    }

    LOGGER.info("QueueBrokerServer shutdown complete");
  }
}
