package org.nexus;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.nexus.exceptions.DatabaseException;
import org.nexus.interfaces.DatabaseConnector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Database is a simple, lightweight database access layer. It provides a clean API for executing
 * queries and managing transactions.
 */
public class NexusDatabase implements AutoCloseable {

  private static final Logger LOGGER = LoggerFactory.getLogger(NexusDatabase.class);

  private final DatabaseConnector connector;
  private final ThreadLocal<Connection> transactionConnection = new ThreadLocal<>();

  /**
   * Create a new Database instance with the specified connector.
   *
   * @param connector The database connector to use
   * @throws NullPointerException if connector is null
   */
  public NexusDatabase(DatabaseConnector connector) {
    this.connector = Objects.requireNonNull(connector, "dbconnector must not be null");
  }

  // region Transaction Management

  /**
   * Begin a new transaction.
   *
   * @throws SQLException if a database access error occurs or a transaction is already in progress
   */
  public void beginTransaction() throws SQLException {
    if (transactionConnection.get() != null) {
      throw new SQLException("Transaction already in progress");
    }
    Connection conn = connector.getConnection();
    conn.setAutoCommit(false);
    transactionConnection.set(conn);
  }

  /**
   * Commit the current transaction.
   *
   * @throws SQLException if a database access error occurs or no transaction is in progress
   */
  public void commitTransaction() throws SQLException {
    Connection conn = getTransactionConnection();
    try {
      conn.commit();
    } finally {
      cleanupTransaction(conn);
    }
  }

  /**
   * Rollback the current transaction.
   *
   * @throws SQLException if a database access error occurs or no transaction is in progress
   */
  public void rollbackTransaction() throws SQLException {
    Connection conn = getTransactionConnection();
    try {
      conn.rollback();
    } finally {
      cleanupTransaction(conn);
    }
  }

  private Connection getTransactionConnection() throws SQLException {
    Connection conn = transactionConnection.get();
    if (conn == null) {
      throw new SQLException("No transaction in progress");
    }
    return conn;
  }

  private void cleanupTransaction(Connection conn) throws SQLException {
    try {
      conn.setAutoCommit(true);
      conn.close();
    } finally {
      transactionConnection.remove();
    }
  }

  // endregion

  // region Query Execution

  /**
   * Execute a query and map the results using the provided mapper function.
   *
   * @param sql    The SQL query
   * @param mapper Function to map ResultSet to result type T
   * @param params Query parameters
   * @param <T>    The result type
   * @return A list of results
   */
  public <T> List<T> query(String sql, ResultSetMapper<T> mapper, Object... params) {
    return withConnection(conn -> {
      try (PreparedStatement stmt = prepareStatement(conn, sql, params);
          ResultSet rs = stmt.executeQuery()) {
        List<T> results = new ArrayList<>();
        while (rs.next()) {
          results.add(mapper.map(rs));
        }
        return results;
      }
    });
  }

  /**
   * Execute a query and return the first result, if any.
   *
   * @param sql    The SQL query
   * @param mapper Function to map ResultSet to result type T
   * @param params Query parameters
   * @param <T>    The result type
   * @return An Optional containing the first result, or empty if no results
   */
  public <T> Optional<T> queryOne(String sql, ResultSetMapper<T> mapper, Object... params) {
    List<T> results = query(sql + " LIMIT 1", mapper, params);
    return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
  }

  /**
   * Execute an update statement (INSERT, UPDATE, DELETE).
   *
   * @param sql    The SQL statement
   * @param params Statement parameters
   * @return The number of rows affected
   */
  public int update(String sql, Object... params) {
    return withConnection(conn -> {
      try (PreparedStatement stmt = prepareStatement(conn, sql, params)) {
        return stmt.executeUpdate();
      }
    });
  }

  /**
   * Execute an INSERT statement and return the generated keys.
   *
   * @param sql       The SQL INSERT statement
   * @param keyMapper Function to map the generated keys to type K
   * @param params    Statement parameters
   * @param <K>       The type of the generated key
   * @return The generated key
   */
  public <K> K insert(String sql, ResultSetMapper<K> keyMapper, Object... params) {
    return withConnection(conn -> {
      try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
        setParameters(stmt, params);
        stmt.executeUpdate();
        try (ResultSet rs = stmt.getGeneratedKeys()) {
          if (rs.next()) {
            return keyMapper.map(rs);
          }
          throw new SQLException("No generated keys returned");
        }
      }
    });
  }

  // endregion

  // region Helper Methods

  /**
   * Execute work within a database connection.
   *
   * @param work The work to execute
   * @param <T>  The result type
   * @return The result of the work
   */
  public <T> T withConnection(ConnectionWork<T> work) {
    Connection conn = transactionConnection.get();
    boolean isTransaction = (conn != null);

    try {
      if (!isTransaction) {
        conn = connector.getConnection();
      }
      return work.execute(conn);
    } catch (SQLException e) {
      throw new DatabaseException("Database operation failed", e);
    } finally {
      if (!isTransaction && conn != null) {
        try {
          conn.close();
        } catch (SQLException e) {
          LOGGER.error("Failed to close database connection", e);
        }
      }
    }
  }

  private PreparedStatement prepareStatement(Connection conn, String sql, Object[] params)
      throws SQLException {
    PreparedStatement stmt = conn.prepareStatement(sql);
    setParameters(stmt, params);
    return stmt;
  }

  private void setParameters(PreparedStatement stmt, Object[] params) throws SQLException {
    for (int i = 0; i < params.length; i++) {
      stmt.setObject(i + 1, params[i]);
    }
  }

  @Override
  public void close() {
    // Clean up any open transaction
    Connection conn = transactionConnection.get();
    if (conn != null) {
      try {
        conn.rollback();
        cleanupTransaction(conn);
      } catch (SQLException e) {
        LOGGER.error("Failed to close database connection", e);
      }
    }
  }

  // endregion

  // region Functional Interfaces

  /**
   * Functional interface for mapping ResultSet to a specific type.
   *
   * @param <T> The result type
   */
  @FunctionalInterface
  public interface ResultSetMapper<T> {

    T map(ResultSet rs) throws SQLException;
  }

  /**
   * Functional interface for executing work within a connection.
   *
   * @param <T> The result type
   */
  @FunctionalInterface
  public interface ConnectionWork<T> {

    T execute(Connection connection) throws SQLException;
  }

  // endregion
}
