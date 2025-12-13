  package io.github.ruitx.images.beans;

import io.avaje.inject.Bean;
import io.avaje.inject.External;
import io.avaje.inject.Factory;
import jakarta.inject.Named;
import org.nexus.database.NexusDatabase;
import org.nexus.database.NexusDatabaseRegistry;

@Factory
public class DatabaseFactory {

  public static final String DEFAULT_DB = "db1";

  @Bean
  @Named(DEFAULT_DB)
  NexusDatabase db1(@External NexusDatabaseRegistry registry) {
    return registry.get(DEFAULT_DB);
  }
}