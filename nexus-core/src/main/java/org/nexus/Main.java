package org.nexus;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {

  private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

  static void main() throws Exception {
    // Create boss and worker groups
    EventLoopGroup bossGroup = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());
    EventLoopGroup workerGroup = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());

    try {
      // Set up the server bootstrap
      ServerBootstrap bootstrap = new ServerBootstrap();
      bootstrap.group(bossGroup, workerGroup)
          .channel(NioServerSocketChannel.class)
          .childHandler(new ChannelInitializer<>() {
            @Override
            protected void initChannel(Channel ch) {
              ChannelPipeline pipeline = ch.pipeline();
              pipeline.addLast(new HttpServerCodec());
              pipeline.addLast(new HttpObjectAggregator(65536)); // 64KB
              pipeline.addLast(new DefaultHttpServerHandler());
            }
          });

      ChannelFuture future = bootstrap.bind(15000).sync();
      LOGGER.info("Started at port 15000");

      // Wait until the server socket is closed
      future.channel().closeFuture().sync();
    } finally {
      bossGroup.shutdownGracefully();
      workerGroup.shutdownGracefully();
    }
  }
}
