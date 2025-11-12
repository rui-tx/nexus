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
import org.nexus.config.SslConfig;
import org.nexus.config.jwt.JwtService;
import org.nexus.dbconnector.DatabaseConnectorFactory;
import org.nexus.generated.GeneratedDIInitializer;
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
    NexusConfig config = NexusConfig.getInstance();
    config.initialize(args);

    // Check for migration flag
    String runMigration = config.get("run-migration");
    if (runMigration != null) {
      String specificDb = "true".equals(runMigration) ? null : runMigration;
      new NexusDatabaseMigrator().migrateAll(specificDb);
      LOGGER.info("Migrations completed. Exiting.");
      System.exit(0);
    }

    // Get and validate configuration values
    boolean enableSsl = config.getBoolean("SSL_ENABLED", false);
    String bindAddress = config.get("BIND_ADDRESS", "0.0.0.0");
    int port = config.getInt("SERVER_PORT", 15000);
    int idleTimeout = config.getInt("IDLE_TIMEOUT_SECONDS", 300);
    int maxContentLength = config.getInt("MAX_CONTENT_LENGTH", 10_485_760);

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

    // Initialize JWT services
    NexusJwt.initialize(config);
    JwtService jwtService = NexusJwt.getInstance().getJwtService();

    Main app = new Main();
    try {
      app.start(bindAddress, port, null, idleTimeout, maxContentLength, sslConfig, jwtService);
      app.serverChannel.closeFuture().sync();
    } finally {
      app.stop();
    }
  }

  private static void printHelp() {
    String help = """
        nexus - Netty-based web server
        
        Usage:
        EXPORT BIND_ADDRESS=0.0.0.0
        EXPORT SERVER_PORT=15000
        nexus
        
        Use environment variables or .env file to setup the server
        Check https://github.com/ruitx/nexus for more information on the available options
        
        Options:
          -h, --help            Show this help message and exit
          --run-migration       Run database migrations for all DBs and exit
          --run-migration=DB_NAME  Run migrations for specific DB and exit
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
   * @param sslConfig        SSL configuration (null for HTTP)
   * @throws InterruptedException if the server is interrupted while starting
   */
  public void start(String bindAddress, int port, TestRouteRegistry testRoutes,
      int idleTimeout, int maxContentLength, SslConfig sslConfig, JwtService jwtService)
      throws InterruptedException {
    this.bossGroup = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
    this.workerGroup = new MultiThreadIoEventLoopGroup(8, NioIoHandler.newFactory());

    List<Middleware> middlewares = List.of(
        new LoggingMiddleware(),
        new SecurityMiddleware(
            NexusConfig
                .getInstance()
                .getBoolean("SSL_ENABLED", false),
            jwtService)
    );

    // Initialize dependency injection for controllers, services and repos
    GeneratedDIInitializer.initialize();

    final boolean isSsl = sslConfig != null;
    if (isSsl) {
      try {
        sslConfig.getSslContext();
      } catch (Exception e) {
        LOGGER.error("Failed to initialize SSL context", e);
        throw new RuntimeException("Failed to initialize SSL context", e);
      }
    }

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
            if (isSsl) {
              try {
                pipeline.addLast("ssl", sslConfig.getSslContext().newHandler(ch.alloc()));
                LOGGER.debug("Added SSL handler to pipeline");
              } catch (Exception e) {
                LOGGER.error("Failed to initialize SSL handler", e);
                ch.close();
                return;
              }
            }

            pipeline.addLast("http-codec", new HttpServerCodec());
            pipeline.addLast("http-aggregator", new HttpObjectAggregator(maxContentLength));

            if (testRoutes != null && !testRoutes.routes().isEmpty()) {
              pipeline.addLast("test-router", new TestRouterHandler(testRoutes));
            }

            pipeline.addLast("http-handler", new DefaultHttpServerHandler(middlewares));
          }
        });

    try {
      InetSocketAddress address = new InetSocketAddress(bindAddress, port);
      ChannelFuture bindFuture = bootstrap.bind(address).sync();
      serverChannel = bindFuture.channel();
      int actualPort = ((InetSocketAddress) serverChannel.localAddress()).getPort();
      String host = "localhost".equals(bindAddress) || "0.0.0.0".equals(bindAddress)
          ? "localhost"
          : bindAddress;
      LOGGER.info("Server started on {}:{} ({}://{}:{})",
          bindAddress, actualPort,
          isSsl ? "https" : "http",
          host,
          actualPort);
    } catch (Exception e) {
      LOGGER.error("Failed to start server on port {}", port, e);
      throw e;
    }
  }

  // for tests
  public void start(int port, TestRouteRegistry testRoutes, int idleTimeout, int maxContentLength)
      throws InterruptedException {
    start("0.0.0.0", port, testRoutes, idleTimeout, maxContentLength, null, null);
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