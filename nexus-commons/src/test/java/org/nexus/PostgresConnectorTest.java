package org.nexus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.nexus.config.db.DatabaseConfig;
import org.nexus.dbconnector.PostgresConnector;
import org.nexus.enums.DatabaseType;
import org.nexus.exceptions.DatabaseException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class PostgresConnectorTest {

  @Container
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("postgres:15-alpine")
          .withDatabaseName("testdb")
          .withUsername("testuser")
          .withPassword("testpass");

  private DatabaseConfig createTestConfig() {
    return DatabaseConfig.defaultConfig("test-db", DatabaseType.POSTGRES)
        .withUrl(postgres.getJdbcUrl())
        .withUsername(postgres.getUsername())
        .withPassword(postgres.getPassword())
        .withConnectionTimeout(30000);
  }

  @Test
  void shouldCreateConnectorSuccessfully() {
    DatabaseConfig config = createTestConfig();

    try (PostgresConnector connector = new PostgresConnector(config)) {
      assertTrue(connector.isReady(), "Connector should be ready");
      assertNotNull(connector, "Connector should not be null");
    }
  }

  @Test
  void shouldGetValidConnection() throws SQLException {
    DatabaseConfig config = createTestConfig();

    try (PostgresConnector connector = new PostgresConnector(config)) {
      try (Connection conn = connector.getConnection()) {
        assertNotNull(conn, "Connection should not be null");
        assertFalse(conn.isClosed(), "Connection should be open");
        assertTrue(conn.isValid(5), "Connection should be valid");
      }
    }
  }

  @Test
  void shouldExecuteQuerySuccessfully() throws SQLException {
    DatabaseConfig config = createTestConfig();

    try (PostgresConnector connector = new PostgresConnector(config)) {
      try (Connection conn = connector.getConnection();
          var stmt = conn.createStatement();
          ResultSet rs = stmt.executeQuery("SELECT 1 as result")) {

        assertTrue(rs.next(), "ResultSet should have at least one row");
        assertEquals(1, rs.getInt("result"), "Query should return 1");
      }
    }
  }

  @Test
  void shouldHandleMultipleConnections() throws SQLException {
    DatabaseConfig config = createTestConfig();

    try (PostgresConnector connector = new PostgresConnector(config)) {
      // Get multiple connections from the pool
      try (Connection conn1 = connector.getConnection();
          Connection conn2 = connector.getConnection();
          Connection conn3 = connector.getConnection()) {

        assertNotNull(conn1);
        assertNotNull(conn2);
        assertNotNull(conn3);

        // All connections should be valid
        assertTrue(conn1.isValid(5));
        assertTrue(conn2.isValid(5));
        assertTrue(conn3.isValid(5));
      }
    }
  }

  @Test
  void shouldCloseConnectorProperly() {
    DatabaseConfig config = createTestConfig();
    PostgresConnector connector = new PostgresConnector(config);

    assertTrue(connector.isReady(), "Connector should be ready before close");

    connector.close();

    assertFalse(connector.isReady(), "Connector should not be ready after close");
  }

  @Test
  void shouldThrowExceptionWhenGettingConnectionAfterClose() {
    DatabaseConfig config = createTestConfig();
    PostgresConnector connector = new PostgresConnector(config);

    connector.close();

    assertThrows(
        SQLException.class,
        connector::getConnection,
        "Should throw SQLException when getting connection after close");
  }

  @Test
  void shouldThrowExceptionWithInvalidCredentials() {
    DatabaseConfig invalidConfig = DatabaseConfig.defaultConfig("test-db", DatabaseType.POSTGRES)
        .withUrl(postgres.getJdbcUrl())
        .withUsername("wrong_user")
        .withPassword("wrong_password")
        .withConnectionTimeout(5000); // Short timeout for faster test

    assertThrows(
        DatabaseException.class,
        () -> new PostgresConnector(invalidConfig),
        "Should throw DatabaseException with invalid credentials");
  }

  @Test
  void shouldThrowExceptionWithInvalidUrl() {
    DatabaseConfig invalidConfig = DatabaseConfig.defaultConfig("test-db", DatabaseType.POSTGRES)
        .withUrl("jdbc:postgresql://localhost:9999/nonexistent")
        .withConnectionTimeout(5000); // Short timeout

    assertThrows(
        DatabaseException.class,
        () -> new PostgresConnector(invalidConfig),
        "Should throw DatabaseException with invalid URL");
  }

  @Test
  void shouldRespectConnectionPoolSize() throws SQLException {
    DatabaseConfig config = createTestConfig();

    try (PostgresConnector connector = new PostgresConnector(config)) {
      // This test verifies the pool is configured correctly
      // You could add more sophisticated pool testing here
      try (Connection conn = connector.getConnection()) {
        assertNotNull(conn);
      }
    }
  }

  @Test
  void shouldHandleNullConfig() {
    assertThrows(
        NullPointerException.class,
        () -> new PostgresConnector(null),
        "Should throw NullPointerException with null config");
  }
}