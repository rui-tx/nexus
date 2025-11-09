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
import org.nexus.config.AppConfig;
import org.nexus.config.SslConfig;
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
    if (args.length > 0 && ("--help".equals(args[0]) || "-h".equals(args[0]))) {
      printHelp();
      System.exit(0);
    }

    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      LOGGER.info("Shutdown, performing cleanup...");
      Main app = new Main();
      app.stop();
      LOGGER.info("Done. Bye!");
    }));

    // Initialize configuration system
    AppConfig config = AppConfig.getInstance();
    config.initialize(args);

    // Get and validate configuration values
    boolean enableSsl = config.getBoolean("SSL_ENABLED", false);
    int port = config.getInt("PORT", enableSsl ? 15443 : 15000);
    int idleTimeout = config.getInt("IDLE_TIMEOUT_SECONDS", 300);
    int maxContentLength = config.getInt("MAX_CONTENT_LENGTH", 10_485_760);

    // Log configuration
    LOGGER.info("Server configuration:");
    LOGGER.info("  Port: {}", port);
    LOGGER.info("  SSL: {}", enableSsl ? "enabled" : "disabled");

    // Configure SSL if enabled
    SslConfig sslConfig = null;
    if (enableSsl) {
      try {
        sslConfig = SslConfig.fromConfig();
        LOGGER.info("SSL/TLS enabled with {} authentication",
            config.getBoolean("SSL_REQUIRE_CLIENT_AUTH", false)
                ? "required client" : "server-only");
      } catch (Exception e) {
        LOGGER.error("Failed to initialize SSL configuration: {}", e.getMessage());
        System.err.println("SSL configuration error: " + e.getMessage());
        System.exit(1);
      }
    }

    Main app = new Main();
    try {
      app.start(port, null, idleTimeout, maxContentLength);
      app.serverChannel.closeFuture().sync();
    } finally {
      app.stop();
    }
  }

  private static void printHelp() {
    String help = """
        nexus - High-performance Netty-based web server
        
        Usage: nexus [options]
        
        Options:
          -h, --help            Show this help message and exit
          -p, --port PORT        Port to listen on (default: 8080 for HTTP, 8443 for HTTPS)
          -s, --ssl              Enable SSL/TLS (HTTPS)
        
        SSL Options (when -s/--ssl is used):
          --keystore PATH        Path to the keystore file
          --keystore-password P  Keystore password
          --key-password P       Key password (defaults to keystore password)
          --require-client-auth  Require client certificate authentication
        
        Advanced Options:
          --idle-timeout SEC     Connection idle timeout in seconds (default: 300)
          --max-content-length N Maximum HTTP content length in bytes (default: 10MB)
        
        Configuration can also be provided via environment variables or .env file.
        Command line arguments take precedence over environment variables.
        
        Examples:
          # Start with default settings
          nexus
        
          # Start with custom port and SSL
          nexus -p 15443 -s --keystore /path/to/keystore.p12 --keystore-password secret
        
          # Use environment variables
          export SSL_ENABLED=true
          export SSL_KEYSTORE_PATH=/path/to/keystore.p12
          export SSL_KEYSTORE_PASSWORD=secret
          nexus
        """;
    System.out.println(help);
  }

  /**
   * Starts the server with the specified configuration.
   *
   * @param port             The port to listen on
   * @param testRoutes       Optional test routes (for testing only)
   * @param idleTimeout      Connection idle timeout in seconds (default: 300)
   * @param maxContentLength Maximum HTTP content length in bytes (default: 10MB)
   * @throws InterruptedException if the server is interrupted while starting
   */
  public void start(int port, TestRouteRegistry testRoutes, int idleTimeout, int maxContentLength)
      throws InterruptedException {
    this.bossGroup = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
    this.workerGroup = new MultiThreadIoEventLoopGroup(8, NioIoHandler.newFactory());

    List<Middleware> middlewares = List.of(
        new LoggingMiddleware(),
        new SecurityMiddleware()
    );

    // Initialize dependency injection for controllers, services and repos
    GeneratedDIInitializer.initialize();

    // Configure server with the loaded settings
    ServerBootstrap bootstrap = new ServerBootstrap();
    bootstrap.group(bossGroup, workerGroup)
        .channel(NioServerSocketChannel.class)
        .option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
        .option(ChannelOption.SO_BACKLOG, 1024)
        .option(ChannelOption.SO_REUSEADDR, true)
        .childOption(ChannelOption.TCP_NODELAY, true)
        .childOption(ChannelOption.SO_KEEPALIVE, true)
        .childOption(ChannelOption.SO_REUSEADDR, true)
        .childOption(ChannelOption.CONNECT_TIMEOUT_MILLIS, idleTimeout * 1000)
        .childHandler(new ChannelInitializer<>() {
          @Override
          protected void initChannel(Channel ch) {
            ChannelPipeline pipeline = ch.pipeline();
            pipeline.addLast(new HttpServerCodec());
            pipeline.addLast(new HttpObjectAggregator(maxContentLength));

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
