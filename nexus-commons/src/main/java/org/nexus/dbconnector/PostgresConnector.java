package org.nexus.dbconnector;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import org.nexus.config.db.DatabaseConfig;
import org.nexus.exceptions.DatabaseException;
import org.nexus.interfaces.DatabaseConnector;

/**
 * PostgreSQL connector using HikariCP connection pool.
 */
public class PostgresConnector implements DatabaseConnector {

  private final HikariDataSource dataSource;
  private final boolean isReady;

  /**
   * Create a new PostgreSQL connector using the provided configuration.
   *
   * @param config The database configuration
   */
  public PostgresConnector(DatabaseConfig config) {
    Objects.requireNonNull(config, "Database config cannot be null");

    try {
      HikariConfig hikariConfig = getHikariConfig(config);

      this.dataSource = new HikariDataSource(hikariConfig);
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

  private static HikariConfig getHikariConfig(DatabaseConfig config) {
    HikariConfig hikariConfig = new HikariConfig();

    hikariConfig.setJdbcUrl(config.url());
    hikariConfig.setUsername(config.username());
    hikariConfig.setPassword(config.password());
    hikariConfig.setMaximumPoolSize(config.poolSize());
    hikariConfig.setMinimumIdle(1);
    hikariConfig.setPoolName("postgres-" + config.name() + "-pool");
    hikariConfig.setAutoCommit(config.autoCommit());
    hikariConfig.setConnectionTimeout(config.connectionTimeout());

    // PostgreSQL-specific optimizations
    hikariConfig.addDataSourceProperty("tcpKeepAlive", "true");
    hikariConfig.addDataSourceProperty("loginTimeout", "30");

    // Optional: Enable SSL
    /*
     hikariConfig.addDataSourceProperty("ssl", "true");
     hikariConfig.addDataSourceProperty("sslfactory", "org.postgresql.ssl.NonValidatingFactory");
    */

    return hikariConfig;
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