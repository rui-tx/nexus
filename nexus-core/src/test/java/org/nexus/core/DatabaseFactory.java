package org.nexus.core;

import io.avaje.inject.Bean;
import io.avaje.inject.External;
import io.avaje.inject.Factory;
import org.nexus.database.NexusDatabase;
import org.nexus.database.NexusDatabaseRegistry;

@Factory
public class DatabaseFactory {

  @Bean
  NexusDatabaseRegistry registry() {
    // This creates the registry with whatever databases are configured
    // in your test environment (test .env or test properties)
    return new NexusDatabaseRegistry();
  }

  @Bean
  NexusDatabase db1(@External NexusDatabaseRegistry registry) {
    return registry.getDefault();
  }
}
