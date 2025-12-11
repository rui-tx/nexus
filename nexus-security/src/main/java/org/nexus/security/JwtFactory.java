package org.nexus.security;

import io.avaje.inject.Bean;
import io.avaje.inject.Factory;
import org.nexus.commons.NexusConfig;
import org.nexus.commons.config.jwt.JwtConfig;
import org.nexus.commons.config.jwt.JwtService;

@Factory
public class JwtFactory {

  @Bean
  public JwtService jwtService() {
    NexusConfig config = NexusConfig.getInstance();
    JwtConfig jwtConfig = new JwtConfig(config);
    return new JwtService(jwtConfig);
  }
}
