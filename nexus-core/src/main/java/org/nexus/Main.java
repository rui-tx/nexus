package org.nexus;

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
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import java.net.InetSocketAddress;
import java.util.List;
import nexus.generated.GeneratedDIInitializer;
import org.nexus.config.EnvConfig;
import org.nexus.dbConnector.DatabaseConnectorFactory;
import org.nexus.handlers.DefaultHttpServerHandler;
import org.nexus.handlers.testing.TestRouteRegistry;
import org.nexus.handlers.testing.TestRouterHandler;
import org.nexus.interfaces.Middleware;
import org.nexus.middleware.LoggingMiddleware;
import org.nexus.middleware.SecurityMiddleware;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {

  private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

  private EventLoopGroup bossGroup;
  private EventLoopGroup workerGroup;
  private Channel serverChannel;

  static void main(String[] args) throws Exception {
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      LOGGER.info("Shutdown, performing cleanup...");
      Main app = new Main();
      app.stop();
      LOGGER.info("Done. Bye!");
    }));

    int port = EnvConfig.getInt("SERVER_PORT", 15001);

    for (String arg : args) {
      switch (arg) {
        case "--help":
        case "-h":
          printHelp();
          System.exit(0);
        default:
          System.err.printf("Unknown argument: %s%n", arg);
          printHelp();
          System.exit(1);
      }
    }

    Main app = new Main();
    app.start(port, null);
    app.serverChannel.closeFuture().sync();
    app.stop();
  }

  private static void printHelp() {
    String help = """
        nexus - Netty-based web server
        Usage: nexus [options]
          -h, --help            Prints this help
        
        For more information, read the documentation in the repository
        """;
    System.out.print(help);
  }

  public void start(int port, TestRouteRegistry testRoutes) throws InterruptedException {
    EventLoopGroup bossGroup = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
    EventLoopGroup workerGroup = new MultiThreadIoEventLoopGroup(8, NioIoHandler.newFactory());

    List<Middleware> middlewares = List.of(
        new LoggingMiddleware(),
        new SecurityMiddleware()
    );

    // Initialize dependency injection for controllers, services and repos
    GeneratedDIInitializer.initialize();

    ServerBootstrap bootstrap = new ServerBootstrap();
    bootstrap.group(bossGroup, workerGroup)
        .channel(NioServerSocketChannel.class)
        .option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
        .option(ChannelOption.SO_BACKLOG, 1024)
        .option(ChannelOption.SO_REUSEADDR, true)
        .childOption(ChannelOption.TCP_NODELAY, true)
        .childOption(ChannelOption.SO_KEEPALIVE, true)
        .childOption(ChannelOption.SO_REUSEADDR, true)
        .childHandler(new ChannelInitializer<>() {
          @Override
          protected void initChannel(Channel ch) {
            ChannelPipeline pipeline = ch.pipeline();
            pipeline.addLast(new HttpServerCodec());
            pipeline.addLast(new HttpObjectAggregator(65536)); //64KB

            if (testRoutes != null && !testRoutes.routes().isEmpty()) {
              pipeline.addLast(new TestRouterHandler(testRoutes));
            }

            pipeline.addLast(new DefaultHttpServerHandler(middlewares));
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

    // Close server channel
    if (serverChannel != null) {
      serverChannel.close().awaitUninterruptibly();
      serverChannel = null;
    }

    // Shutdown worker groups
    if (bossGroup != null) {
      bossGroup.shutdownGracefully().awaitUninterruptibly();
      bossGroup = null;
    }
    if (workerGroup != null) {
      workerGroup.shutdownGracefully().awaitUninterruptibly();
      workerGroup = null;
    }

    try {
      DatabaseConnectorFactory.closeAll();
    } catch (Exception e) {
      LOGGER.error("Error closing database connections", e);
    }
  }
}
