package org.nexus.config;

import org.nexus.NexusConfig;

/**
 * Configuration class for Nexus server.
 */
public class ServerConfig {

  private final String bindAddress;
  private final int port;
  private final int idleTimeoutSeconds;
  private final int maxContentLength;
  private final SslConfig sslConfig;

  private ServerConfig(Builder builder) {
    this.bindAddress = builder.bindAddress;
    this.port = builder.port;
    this.idleTimeoutSeconds = builder.idleTimeoutSeconds;
    this.maxContentLength = builder.maxContentLength;
    this.sslConfig = builder.sslConfig;
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

  public static class Builder {

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

    public ServerConfig build() {
      return new ServerConfig(this);
    }
  }
}
