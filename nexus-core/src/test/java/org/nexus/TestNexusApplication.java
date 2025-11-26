package org.nexus;

import org.nexus.config.ServerConfig;
import org.nexus.middleware.SecurityHeadersMiddleware;
import org.nexus.server.NexusServer;

/**
 * Test application for end-to-end testing with NexusApplication.
 */
public class TestNexusApplication extends NexusApplication {

  private static TestNexusApplication instance;
  private NexusServer server;
  private ServerConfig serverConfig;

  private TestNexusApplication() {
  }

  public static synchronized TestNexusApplication getInstance() {
    if (instance == null) {
      instance = new TestNexusApplication();
    }
    return instance;
  }

  @Override
  protected ServerConfig createServerConfig() {
    this.serverConfig = ServerConfig.builder()
        .bindAddress("127.0.0.1")
        .port(0)  // Auto-assign port
        .idleTimeoutSeconds(300)
        .maxContentLength(1_048_576)
        .middleware(new SecurityHeadersMiddleware(true))
        .build();
    return this.serverConfig;
  }

  @Override
  protected NexusServer createServer(ServerConfig config) {
    // Store the server instance for later use
    this.server = super.createServer(config);
    return this.server;
  }

  public int getPort() {
    if (server == null) {
      throw new IllegalStateException("Server not started. Call start() first.");
    }
    return server.getPort();
  }

  public String getBaseUrl() {
    //noinspection HttpUrlsUsage
    return "http://" + serverConfig.getBindAddress() + ":" + getPort();
  }

  public NexusServer getServer() {
    return server;
  }

  public ServerConfig getServerConfig() {
    return serverConfig;
  }
}
