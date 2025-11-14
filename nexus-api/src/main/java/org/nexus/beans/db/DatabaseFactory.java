package org.nexus.beans.db;

import io.avaje.inject.Bean;
import io.avaje.inject.Factory;
import org.nexus.NexusDatabase;

@Factory
public class DatabaseFactory {

  @Bean
  public NexusDatabase defaultDatabase(DatabaseRegistry registry) {
    return registry.getDefault();
  }
}
