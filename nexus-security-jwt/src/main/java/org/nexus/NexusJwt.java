package org.nexus;

import org.nexus.config.jwt.JwtConfig;
import org.nexus.config.jwt.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class NexusJwt {

  private static final Logger LOGGER = LoggerFactory.getLogger(NexusJwt.class);
  private static volatile NexusConfig config;
  private static volatile boolean initialized = false;

  private NexusJwt() {
  }

  public static synchronized void initialize(NexusConfig nexusConfig) {
    if (initialized) {
      throw new IllegalStateException("NexusJwt is already initialized");
    }
    if (nexusConfig == null) {
      throw new IllegalArgumentException("NexusConfig cannot be null");
    }
    config = nexusConfig;
    initialized = true;
    LOGGER.info("NexusJwt initialized");
  }

  public static JwtService getJwtService() {
    checkInitialized();
    return InstanceHolder.jwtService;
  }

  public static JwtConfig getJwtConfig() {
    checkInitialized();
    return InstanceHolder.jwtConfig;
  }

  private static void checkInitialized() {
    if (!initialized) {
      throw new IllegalStateException(
          "NexusJwt has not been initialized. Call initialize() first.");
    }
  }

  private static final class InstanceHolder {

    private static final JwtConfig jwtConfig = new JwtConfig(config);
    private static final JwtService jwtService = new JwtService(jwtConfig);
  }
}