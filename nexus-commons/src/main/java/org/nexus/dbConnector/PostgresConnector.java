package org.nexus.dbConnector;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import org.nexus.exceptions.DatabaseException;
import org.nexus.interfaces.DatabaseConnector;

/**
 * PostgreSQL connector using HikariCP connection pool.
 */
public class PostgresConnector implements DatabaseConnector {

  private final HikariDataSource dataSource;
  private final boolean isReady;

  /**
   * Create a new PostgreSQL connector.
   *
   * @param host     The database host (e.g., "localhost")
   * @param port     The database port (e.g., 5432)
   * @param dbName   The database name
   * @param username The database username
   * @param password The database password
   * @param poolSize Maximum number of connections in the pool
   */
  public PostgresConnector(
      String host, int port, String dbName, String username, String password, int poolSize) {
    Objects.requireNonNull(host, "Host cannot be null");
    Objects.requireNonNull(dbName, "Database name cannot be null");
    Objects.requireNonNull(username, "Username cannot be null");
    Objects.requireNonNull(password, "Password cannot be null");

    try {
      HikariConfig config = new HikariConfig();
      String url = String.format("jdbc:postgresql://%s:%d/%s", host, port, dbName);

      config.setJdbcUrl(url);
      config.setUsername(username);
      config.setPassword(password);
      config.setMaximumPoolSize(poolSize);
      config.setMinimumIdle(1);
      config.setPoolName("postgres-pool");

      // PostgreSQL-specific optimizations
      config.setConnectionTimeout(30000); // 30 seconds
      config.addDataSourceProperty("tcpKeepAlive", "true");
      config.addDataSourceProperty("loginTimeout", "30");

      // Optional: Enable SSL if your setup requires it
      // config.addDataSourceProperty("ssl", "true");
      // config.addDataSourceProperty("sslfactory", "org.postgresql.ssl.NonValidatingFactory"); // For dev/testing only

      this.dataSource = new HikariDataSource(config);
      this.isReady = true;

      // Test the connection
      try (Connection conn = dataSource.getConnection()) {
        try (var stmt = conn.createStatement()) {
          stmt.execute("SELECT 1"); // Simple test query
        }
      }
    } catch (Exception e) {
      close();
      throw new DatabaseException("Failed to initialize PostgreSQL connector", e);
    }
  }

  @Override
  public Connection getConnection() throws SQLException {
    if (!isReady) {
      throw new SQLException("Database connector is not ready");
    }
    return dataSource.getConnection();
  }

  @Override
  public void close() {
    if (dataSource != null && !dataSource.isClosed()) {
      dataSource.close();
    }
  }

  @Override
  public boolean isReady() {
    return isReady && dataSource != null && !dataSource.isClosed();
  }
}