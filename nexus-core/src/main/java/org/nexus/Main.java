package org.nexus;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import java.net.InetSocketAddress;
import org.nexus.handlers.testing.TestRouteRegistry;
import org.nexus.handlers.testing.TestRouterHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {

  private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

  private EventLoopGroup bossGroup;
  private EventLoopGroup workerGroup;
  private Channel serverChannel;

  // cli entry
  static void main(String[] args) throws Exception {
    Main app = new Main();
    app.start(15000, null);
    app.serverChannel.closeFuture().sync();
    app.stop();
  }

  public void start(int port, TestRouteRegistry testRoutes) throws InterruptedException {
    EventLoopGroup bossGroup = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());
    EventLoopGroup workerGroup = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());

    ServerBootstrap bootstrap = new ServerBootstrap();
    bootstrap.group(bossGroup, workerGroup)
        .channel(NioServerSocketChannel.class)
        .childHandler(new ChannelInitializer<>() {
          @Override
          protected void initChannel(Channel ch) {
            ChannelPipeline pipeline = ch.pipeline();
            pipeline.addLast(new HttpServerCodec());
            pipeline.addLast(new HttpObjectAggregator(65536)); //64KB

            if (testRoutes != null && !testRoutes.routes().isEmpty()) {
              pipeline.addLast(new TestRouterHandler(testRoutes));
            }

            pipeline.addLast(new DefaultHttpServerHandler());
          }
        })
        .childOption(ChannelOption.SO_KEEPALIVE, true);

    ChannelFuture bindFuture = bootstrap.bind(port).sync();
    serverChannel = bindFuture.channel();

    int actualPort = ((InetSocketAddress) serverChannel.localAddress()).getPort();
    LOGGER.info("Started at port {}", actualPort);
  }

  public int getPort() {
    if (serverChannel == null) {
      throw new IllegalStateException("Server not started");
    }
    return ((InetSocketAddress) serverChannel.localAddress()).getPort();
  }

  public void stop() {
    if (serverChannel != null) {
      serverChannel.close().awaitUninterruptibly();
      serverChannel = null;
    }
    if (bossGroup != null) {
      bossGroup.shutdownGracefully().awaitUninterruptibly();
      bossGroup = null;
    }
    if (workerGroup != null) {
      workerGroup.shutdownGracefully().awaitUninterruptibly();
      workerGroup = null;
    }
  }
}
