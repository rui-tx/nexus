package org.nexus;

import jakarta.inject.Singleton;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.nexus.config.db.DatabaseConfig;
import org.nexus.dbconnector.PostgresConnector;
import org.nexus.dbconnector.SqliteConnector;
import org.nexus.enums.DatabaseType;
import org.nexus.interfaces.DatabaseConnector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class NexusDatabaseRegistry implements AutoCloseable {

  private static final Logger LOGGER = LoggerFactory.getLogger(NexusDatabaseRegistry.class);

  private final Map<String, NexusDatabase> databases;
  private final Map<String, DatabaseConnector> connectors;

  public NexusDatabaseRegistry() {
    NexusConfig cfg = NexusConfig.getInstance();
    Map<String, DatabaseConfig> all = cfg.getAllDatabaseConfigs();

    Map<String, DatabaseConnector> conns = new HashMap<>();
    Map<String, NexusDatabase> dbs = new HashMap<>();

    for (Map.Entry<String, DatabaseConfig> e : all.entrySet()) {
      String name = e.getKey();
      DatabaseConfig dc = e.getValue();
      DatabaseConnector connector = createConnector(dc);
      conns.put(name, connector);
      dbs.put(name, new NexusDatabase(connector));
    }

    this.connectors = Collections.unmodifiableMap(conns);
    this.databases = Collections.unmodifiableMap(dbs);
  }

  private DatabaseConnector createConnector(DatabaseConfig config) {
    DatabaseType type = config.type();
    return switch (type) {
      case SQLITE -> new SqliteConnector(config);
      case POSTGRES -> new PostgresConnector(config);
      default -> throw new IllegalStateException("Unsupported database type: " + type);
    };
  }

  public NexusDatabase get(String name) {
    NexusDatabase db = databases.get(Objects.requireNonNull(name));
    if (db == null) {
      throw new IllegalStateException("Unknown database: " + name);
    }
    return db;
  }

  public NexusDatabase getDefault() {
    if (databases == null || databases.isEmpty()) {
      throw new IllegalStateException("No databases configured");
    }

    // gets 1st entry ie DB1_...
    return databases.entrySet().iterator().next().getValue();
  }

  @Override
  public void close() {
    for (DatabaseConnector c : connectors.values()) {
      try {
        c.close();
      } catch (Exception _) {
        LOGGER.error("Error closing database connector {}", c);
      }
    }
  }
}

