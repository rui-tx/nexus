package org.nexus.dbconnector;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.nexus.NexusConfig;
import org.nexus.config.db.DatabaseConfig;
import org.nexus.exceptions.DatabaseException;
import org.nexus.interfaces.DatabaseConnector;

/**
 * Factory for creating database connectors based on configuration.
 */
public final class DatabaseConnectorFactory {

  private static final Map<String, DatabaseConnector> connectorCache = new ConcurrentHashMap<>();
  private static final NexusConfig config = NexusConfig.getInstance();

  private DatabaseConnectorFactory() {
  }

  /**
   * Get a database configuration by name.
   *
   * @param name The name of the database configuration
   * @return The database configuration
   * @throws DatabaseException if the configuration is not found
   */
  public static DatabaseConfig getConfig(String name) {
    return config.getDatabaseConfig(name);
  }

  /**
   * Get all database configurations.
   *
   * @return Map of database configurations by name
   */
  public static Map<String, DatabaseConfig> getAllConfigs() {
    return config.getAllDatabaseConfigs();
  }

  /**
   * Get or create a database connector for the given configuration. Connectors are cached by their
   * configuration name.
   *
   * @param config The database configuration
   * @return A shared DatabaseConnector instance
   */
  public static DatabaseConnector create(DatabaseConfig config) {
    if (config == null) {
      throw new IllegalArgumentException("Database configuration cannot be null");
    }

    return connectorCache.computeIfAbsent(config.name(), _ -> createNewConnector(config));
  }

  /**
   * Create a new, non-cached database connector. Useful for migrations or temporary connections.
   *
   * @param config The database configuration
   * @return A new DatabaseConnector instance (not cached)
   */
  public static DatabaseConnector createNonCached(DatabaseConfig config) {
    if (config == null) {
      throw new IllegalArgumentException("Database configuration cannot be null");
    }
    return createNewConnector(config);
  }

  private static DatabaseConnector createNewConnector(DatabaseConfig config) {
    try {
      return switch (config.type()) {
        case SQLITE -> new SqliteConnector(config);
        case POSTGRES -> new PostgresConnector(config);
        default -> throw new DatabaseException("Unsupported database type: " + config.type());
      };
    } catch (Exception e) {
      throw new DatabaseException("Failed to create database connector: " + e.getMessage(), e);
    }
  }

  /**
   * Close all cached database connectors. Call this during application shutdown.
   */
  public static void closeAll() {
    for (Map.Entry<String, DatabaseConnector> entry : connectorCache.entrySet()) {
      try {
        entry.getValue().close();
      } catch (Exception e) {
        System.err.println(
            "Error closing database connector " + entry.getKey() + ": " + e.getMessage());
      }
    }
    connectorCache.clear();
  }

  /**
   * Creates a new read-only database connector based on the provided configuration. Note: Only
   * applicable for SQLite databases.
   *
   * @param config The database configuration
   * @return A new read-only database connector instance
   * @throws DatabaseException if the database type is not supported for read-only mode
   */
  public static DatabaseConnector createReadOnly(DatabaseConfig config) {
    if (config == null) {
      throw new IllegalArgumentException("Database configuration cannot be null");
    }

    return switch (config.type()) {
      case SQLITE -> new SqliteConnector(config, true);
      case POSTGRES ->
          throw new DatabaseException("Read-only mode is only supported for SQLite databases");
      default -> throw new DatabaseException("Unsupported database type: " + config.type());
    };
  }
}
