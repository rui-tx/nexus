package org.nexus.dbconnector;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import org.nexus.config.DatabaseConfig;
import org.nexus.exceptions.DatabaseException;
import org.nexus.interfaces.DatabaseConnector;

/**
 * SQLite connector using HikariCP connection pool.
 */
public class SqliteConnector implements DatabaseConnector {

  private final HikariDataSource dataSource;
  private final boolean isReady;

  /**
   * Create a new SQLite connector.
   *
   * @param config The database configuration
   */
  public SqliteConnector(DatabaseConfig config) {
    this(config, false);
  }

  /**
   * Create a new SQLite connector with read-only option.
   *
   * @param config   The database configuration
   * @param readOnly Whether to open the database in read-only mode
   */
  public SqliteConnector(DatabaseConfig config, boolean readOnly) {
    Objects.requireNonNull(config, "Database config cannot be null");

    try {
      String databasePath = config.url().replaceFirst("^jdbc:sqlite:", "");
      File dbFile = new File(databasePath);

      if (!readOnly && !dbFile.exists()) {
        // Create parent directories if they don't exist
        File parent = dbFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
          throw new IllegalStateException("Failed to create database directory: " + parent);
        }

        // Create the database file
        if (!dbFile.createNewFile()) {
          throw new IllegalStateException("Failed to create database file: " + databasePath);
        }
      }

      HikariConfig hikariConfig = getHikariConfig(config, readOnly);

      this.dataSource = new HikariDataSource(hikariConfig);
      this.isReady = true;

      // Test the connection and enable foreign keys
      try (Connection conn = dataSource.getConnection()) {
        try (var stmt = conn.createStatement()) {
          stmt.execute("PRAGMA foreign_keys = ON");
        }
      }
    } catch (Exception e) {
      close();
      throw new DatabaseException("Failed to initialize SQLite connector", e);
    }
  }

  private static HikariConfig getHikariConfig(DatabaseConfig config, boolean readOnly) {
    HikariConfig hikariConfig = new HikariConfig();
    String url = config.url() + (readOnly ? "?mode=ro" : "");

    hikariConfig.setJdbcUrl(url);
    hikariConfig.setMaximumPoolSize(config.poolSize());
    hikariConfig.setMinimumIdle(1);
    hikariConfig.setPoolName("sqlite-" + config.name() + (readOnly ? "-ro" : "-rw") + "-pool");
    hikariConfig.setAutoCommit(config.autoCommit());
    hikariConfig.setConnectionTimeout(config.connectionTimeout());

    // SQLite-specific optimizations
    hikariConfig.addDataSourceProperty("journal_mode", "WAL");
    hikariConfig.addDataSourceProperty("synchronous", "NORMAL");
    hikariConfig.addDataSourceProperty("busy_timeout", 30000); // 30 seconds
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
