package org.nexus.config;

import org.nexus.NexusConfig;

public class NexusJwt {

  private static volatile NexusJwt instance;
  private final JwtConfig config;
  private final JwtService jwtService;

  private NexusJwt(NexusConfig nexusConfig) {
    if (nexusConfig == null) {
      throw new IllegalArgumentException("NexusConfig cannot be null");
    }
    this.config = new JwtConfig(nexusConfig);
    this.jwtService = new JwtService(this.config);
  }

  public static void initialize(NexusConfig nexusConfig) {
    if (instance != null) {
      throw new IllegalStateException("NexusJwt is already initialized");
    }
    instance = new NexusJwt(nexusConfig);
  }

  public static NexusJwt getInstance() {
    NexusJwt result = instance;
    if (result == null) {
      throw new IllegalStateException(
          "NexusJwt has not been initialized. Call initialize() first.");
    }
    return result;
  }

  public JwtService getJwtService() {
    return jwtService;
  }

  public JwtConfig getJwtConfig() {
    return config;
  }
}
