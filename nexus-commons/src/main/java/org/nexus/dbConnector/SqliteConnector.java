package org.nexus.dbConnector;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
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
   * @param databasePath Path to the SQLite database file
   * @param readOnly     Whether to open the database in read-only mode
   * @param poolSize     Maximum number of connections in the pool
   */
  public SqliteConnector(String databasePath, boolean readOnly, int poolSize) {
    Objects.requireNonNull(databasePath, "Database path cannot be null");

    try {
      File dbFile = new File(databasePath);
      if (!readOnly && !dbFile.exists()) {
        // Create parent directories if they don't exist
        File parent = dbFile.getParentFile();
        if (parent != null && !parent.exists()) {
          if (!parent.mkdirs()) {
            throw new IllegalStateException("Failed to create database directory: " + parent);
          }
        }
        // Create the database file
        if (!dbFile.createNewFile()) {
          throw new IllegalStateException("Failed to create database file: " + databasePath);
        }
      }

      HikariConfig config = new HikariConfig();
      String url = String.format("jdbc:sqlite:%s%s",
          databasePath,
          readOnly ? "?mode=ro" : "");

      config.setJdbcUrl(url);
      config.setMaximumPoolSize(poolSize);
      config.setMinimumIdle(1);
      config.setPoolName("sqlite-" + (readOnly ? "reader" : "writer") + "-pool");

      // SQLite-specific optimizations
      config.addDataSourceProperty("journal_mode", "WAL");
      config.addDataSourceProperty("synchronous", "NORMAL");
      config.addDataSourceProperty("busy_timeout", 30000); // 30 seconds

      this.dataSource = new HikariDataSource(config);
      this.isReady = true;

      // Test the connection
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
