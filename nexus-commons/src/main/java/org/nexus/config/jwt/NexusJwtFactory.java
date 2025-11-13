package org.nexus.config.jwt;

import io.avaje.inject.Bean;
import io.avaje.inject.Factory;
import org.nexus.NexusConfig;

@Factory
public class NexusJwtFactory {

  @Bean
  public JwtService jwtService() {
    NexusConfig config = NexusConfig.getInstance();
    JwtConfig jwtConfig = new JwtConfig(config);
    return new JwtService(jwtConfig);
  }
}
