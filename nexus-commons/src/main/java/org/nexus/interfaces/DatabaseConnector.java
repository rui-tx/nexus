package org.nexus.interfaces;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Interface for database connection management.
 */
public interface DatabaseConnector {

  /**
   * Get a database connection.
   *
   * @return A database connection
   * @throws SQLException if a database access error occurs
   */
  Connection getConnection() throws SQLException;

  /**
   * Close the connector and release all resources.
   */
  void close();

  /**
   * Check if the connector is ready to be used.
   *
   * @return true if ready, false otherwise
   */
  boolean isReady();
}
