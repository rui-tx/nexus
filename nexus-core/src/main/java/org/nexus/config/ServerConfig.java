package org.nexus.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.nexus.NexusConfig;
import org.nexus.interfaces.Middleware;

/**
 * Configuration class for Nexus server.
 */
public class ServerConfig {

  private final String bindAddress;
  private final int port;
  private final int idleTimeoutSeconds;
  private final int maxContentLength;
  private final SslConfig sslConfig;
  private final List<Middleware> middlewares;

  private ServerConfig(Builder builder) {
    this.bindAddress = builder.bindAddress;
    this.port = builder.port;
    this.idleTimeoutSeconds = builder.idleTimeoutSeconds;
    this.maxContentLength = builder.maxContentLength;
    this.sslConfig = builder.sslConfig;
    this.middlewares = List.copyOf(builder.middlewares); // immutable
  }

  public static ServerConfig from(NexusConfig config) {
    return builder()
        .bindAddress(config.get("BIND_ADDRESS", "0.0.0.0"))
        .port(config.getInt("SERVER_PORT", 15000))
        .idleTimeoutSeconds(config.getInt("IDLE_TIMEOUT_SECONDS", 300))
        .maxContentLength(config.getInt("MAX_CONTENT_LENGTH", 10_485_760))
        .sslConfig(config.getBoolean("SSL_ENABLED", false) ? SslConfig.fromConfig() : null)
        .build();
  }

  public static Builder builder() {
    return new Builder();
  }

  // Getters
  public String getBindAddress() {
    return bindAddress;
  }

  public int getPort() {
    return port;
  }

  public int getIdleTimeoutSeconds() {
    return idleTimeoutSeconds;
  }

  public int getMaxContentLength() {
    return maxContentLength;
  }

  public SslConfig getSslConfig() {
    return sslConfig;
  }

  public boolean isSslEnabled() {
    return sslConfig != null;
  }

  public List<Middleware> getMiddlewares() {
    return middlewares;
  }

  public static class Builder {

    private final List<Middleware> middlewares = new ArrayList<>();
    private String bindAddress = "0.0.0.0";
    private int port = 15000;
    private int idleTimeoutSeconds = 300;
    private int maxContentLength = 10_485_760; // 10MB
    private SslConfig sslConfig = null;

    public Builder bindAddress(String bindAddress) {
      this.bindAddress = bindAddress;
      return this;
    }

    public Builder port(int port) {
      this.port = port;
      return this;
    }

    public Builder idleTimeoutSeconds(int idleTimeoutSeconds) {
      this.idleTimeoutSeconds = idleTimeoutSeconds;
      return this;
    }

    public Builder maxContentLength(int maxContentLength) {
      this.maxContentLength = maxContentLength;
      return this;
    }

    public Builder sslConfig(SslConfig sslConfig) {
      this.sslConfig = sslConfig;
      return this;
    }

    public Builder middleware(Middleware middleware) {
      this.middlewares.add(Objects.requireNonNull(middleware));
      return this;
    }

    public Builder middlewares(Middleware... middlewares) {
      for (Middleware m : middlewares) {
        this.middlewares.add(Objects.requireNonNull(m));
      }
      return this;
    }

    public Builder middlewares(List<Middleware> middlewares) {
      this.middlewares.addAll(Objects.requireNonNull(middlewares));
      return this;
    }

    public ServerConfig build() {
      return new ServerConfig(this);
    }
  }
}