package org.nexus.config;

import org.nexus.enums.DatabaseType;

/**
 * Configuration for a database connection.
 *
 * @param name              The name of the database configuration
 * @param type              The type of the database (SQLITE or POSTGRES)
 * @param url               The JDBC URL for the database
 * @param username          The username for authentication
 * @param password          The password for authentication
 * @param poolSize          The maximum number of connections in the pool
 * @param autoCommit        Whether to auto-commit transactions
 * @param connectionTimeout The connection timeout in milliseconds
 * @param migrationsPath    The path for the SQL migration files
 */
public record DatabaseConfig(
    String name,
    DatabaseType type,
    String url,
    String username,
    String password,
    int poolSize,
    boolean autoCommit,
    long connectionTimeout,
    String migrationsPath
) {

  /**
   * Creates a default configuration for a database type.
   *
   * @param name The name of the database configuration
   * @param type The type of the database
   * @return A default configuration for the specified database type
   */
  public static DatabaseConfig defaultConfig(String name, DatabaseType type) {
    return switch (type) {
      case SQLITE -> new DatabaseConfig(
          name,
          type,
          "jdbc:sqlite:db.db",
          "",
          "",
          10,
          true,
          30000,
          "db/migrations/sqlite"
      );
      case POSTGRES -> new DatabaseConfig(
          name,
          type,
          "jdbc:postgresql://localhost:5432/mydb",
          "postgres",
          "postgres",
          10,
          true,
          30000,
          "db/migrations/postgres"
      );
    };
  }

  public DatabaseConfig withUrl(String url) {
    return new DatabaseConfig(name, type, url, username, password, poolSize, autoCommit,
        connectionTimeout, migrationsPath);
  }

  public DatabaseConfig withUsername(String username) {
    return new DatabaseConfig(name, type, url, username, password, poolSize, autoCommit,
        connectionTimeout, migrationsPath);
  }

  public DatabaseConfig withPassword(String password) {
    return new DatabaseConfig(name, type, url, username, password, poolSize, autoCommit,
        connectionTimeout, migrationsPath);
  }

  public DatabaseConfig withPoolSize(int poolSize) {
    return new DatabaseConfig(name, type, url, username, password, poolSize, autoCommit,
        connectionTimeout, migrationsPath);
  }

  public DatabaseConfig withAutoCommit(boolean autoCommit) {
    return new DatabaseConfig(name, type, url, username, password, poolSize, autoCommit,
        connectionTimeout, migrationsPath);
  }

  public DatabaseConfig withConnectionTimeout(long connectionTimeout) {
    return new DatabaseConfig(name, type, url, username, password, poolSize, autoCommit,
        connectionTimeout, migrationsPath);
  }

  public DatabaseConfig withMigrationsPath(String migrationsPath) {
    return new DatabaseConfig(name, type, url, username, password, poolSize, autoCommit,
        connectionTimeout, migrationsPath);
  }
}
