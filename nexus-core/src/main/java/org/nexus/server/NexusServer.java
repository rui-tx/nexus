package org.nexus.server;

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
import io.netty.handler.timeout.IdleStateHandler;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.nexus.NexusBeanScope;
import org.nexus.config.ServerConfig;
import org.nexus.handlers.DefaultHttpServerHandler;
import org.nexus.handlers.testing.TestRouteRegistry;
import org.nexus.handlers.testing.TestRouterHandler;
import org.nexus.interfaces.Middleware;
import org.nexus.middleware.LoggingMiddleware;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NexusServer {

  private static final Logger LOGGER = LoggerFactory.getLogger(NexusServer.class);

  private final ServerConfig config;
  private final List<Middleware> middlewares;
  // For tests only: optional test route registry to short-circuit routing
  private final TestRouteRegistry testRoutes;
  private EventLoopGroup bossGroup;
  private EventLoopGroup workerGroup;
  private Channel serverChannel;

  /**
   * Creates a new NexusServer with default middlewares.
   *
   * @param config The server configuration
   */
  public NexusServer(ServerConfig config) {
    this.config = config;
    List<Middleware> defaults = new ArrayList<>();
    defaults.add(new LoggingMiddleware());
//    defaults.add(new SecurityMiddleware());
    this.middlewares = List.copyOf(defaults);
    this.testRoutes = null;
    verifyRoutesAvailability();
  }

  /**
   * Creates a new NexusServer with custom middlewares.
   *
   * @param config      The server configuration
   * @param middlewares List of middlewares to use (in addition to default ones)
   */
  public NexusServer(ServerConfig config, List<Middleware> middlewares) {
    this.config = config;
    // Use exactly the provided list, caller decides whether to include defaults.
    this.middlewares = List.copyOf(middlewares);
    this.testRoutes = null;
    verifyRoutesAvailability();
  }

  /**
   * Test-only constructor allowing injection of a test route registry that is evaluated before the
   * normal router. Not intended for production use.
   */
  public NexusServer(ServerConfig config, List<Middleware> middlewares,
      TestRouteRegistry testRoutes) {
    this.config = config;
    this.middlewares = List.copyOf(middlewares);
    this.testRoutes = testRoutes;
    verifyRoutesAvailability();
  }

  /**
   * Returns the actual bound port. Useful when binding to 0 for an ephemeral port.
   */
  public int getPort() {
    if (serverChannel == null) {
      throw new IllegalStateException("Server not started");
    }
    java.net.InetSocketAddress addr = (java.net.InetSocketAddress) serverChannel.localAddress();
    return addr.getPort();
  }

  /**
   * Starts the Nexus server and begins accepting connections.
   *
   * @throws Exception if the server fails to start
   */
  public void start() throws Exception {
    ensureBeanScopeInitialized();

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

    LOGGER.info("Starting Nexus server on {}:{}", config.getBindAddress(), config.getPort());

    ChannelFuture f = b.bind(config.getBindAddress(), config.getPort()).sync();
    serverChannel = f.channel();

    int actualPort = getPort();
    LOGGER.info("Server started successfully on {}:{}",
        config.getBindAddress(),
        actualPort);
  }

  /**
   * Starts the server (if not already started) and blocks until it is shut down.
   */
  public void startAndAwait() throws Exception {
    if (serverChannel == null) {
      start();
    }
    try {
      serverChannel.closeFuture().sync();
    } finally {
      stop();
    }
  }

  /**
   * Configures the Netty pipeline with necessary handlers.
   *
   * @param ch The channel to configure
   */
  protected void configurePipeline(Channel ch) {
    ChannelPipeline p = ch.pipeline();

    // Add SSL first if enabled
//    if (config.isSslEnabled() && config.getSslConfig() != null) {
//      p.addLast(config.getSslConfig().createSslHandler(ch.alloc()));
//    }

    // Add HTTP codec
    p.addLast(new HttpServerCodec());

    // Add aggregator for full HTTP requests
    p.addLast(new HttpObjectAggregator(config.getMaxContentLength()));

    // Add idle state handler
    p.addLast(new IdleStateHandler(
        config.getIdleTimeoutSeconds(),
        config.getIdleTimeoutSeconds(),
        config.getIdleTimeoutSeconds(),
        java.util.concurrent.TimeUnit.SECONDS
    ));

    // If test routes are provided (tests), short-circuit to them first
    if (testRoutes != null && !testRoutes.routes().isEmpty()) {
      p.addLast("test-router", new TestRouterHandler(testRoutes));
    }

    // Add custom handlers with route resolution
    p.addLast(new DefaultHttpServerHandler(middlewares));
  }

  /**
   * Stops the server and releases all resources.
   */
  public void stop() {
    LOGGER.info("Shutting down Nexus server...");

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

    LOGGER.info("Server shutdown complete");
  }

  private void verifyRoutesAvailability() {
    try {
      Class.forName("org.nexus.GeneratedRoutes");
    } catch (ClassNotFoundException e) {
      LOGGER.warn("No routes found. Did you annotate your controller methods with @Mapping?");
    }
  }

  private void ensureBeanScopeInitialized() {
    try {
      Object scope = NexusBeanScope.get();
      if (scope == null) {
        throw new IllegalStateException(
            "NexusBeanScope is not initialized. Initialize DI before starting the server.");
      }
    } catch (NoClassDefFoundError | Exception e) {
      // If class not present or method fails, provide a helpful message
      throw new IllegalStateException(
          "Failed to access NexusBeanScope. Ensure Avaje Inject is configured and BeanScope initialized before server start.",
          e);
    }
  }
}
