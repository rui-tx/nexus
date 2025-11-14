package org.nexus.beans.jwt;

import io.avaje.inject.Bean;
import io.avaje.inject.Factory;
import org.nexus.NexusConfig;
import org.nexus.config.jwt.JwtConfig;
import org.nexus.config.jwt.JwtService;

@Factory
public class JwtFactory {

  @Bean
  public JwtService jwtService() {
    NexusConfig config = NexusConfig.getInstance();
    JwtConfig jwtConfig = new JwtConfig(config);
    return new JwtService(jwtConfig);
  }
}
