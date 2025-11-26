package org.nexus;

import io.avaje.inject.Bean;
import io.avaje.inject.External;
import io.avaje.inject.Factory;

@Factory
public class DatabaseFactory {

  @Bean
  NexusDatabaseRegistry registry() {
    // This creates the registry with whatever databases are configured
    // in your test environment (test .env or test properties)
    return new NexusDatabaseRegistry();
  }
  
  @Bean
  NexusDatabase defaultDb(@External NexusDatabaseRegistry registry) {
    return registry.getDefault();
  }
}
