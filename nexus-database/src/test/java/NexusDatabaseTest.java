import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nexus.NexusDatabase;
import org.nexus.config.db.DatabaseConfig;
import org.nexus.dbconnector.SqliteConnector;
import org.nexus.enums.DatabaseType;
import org.nexus.exceptions.DatabaseException;

@DisplayName("NexusDatabase Integration Tests")
class NexusDatabaseTest {

  @TempDir
  Path tempDir;

  private NexusDatabase database;
  private SqliteConnector connector;
  private Path dbFile;

  @BeforeEach
  void setUp() throws IOException {
    dbFile = tempDir.resolve("test.db");

    DatabaseConfig config = DatabaseConfig.defaultConfig("test", DatabaseType.SQLITE)
        .withUrl("jdbc:sqlite:" + dbFile.toString())
        .withPoolSize(5)
        .withAutoCommit(true);

    connector = new SqliteConnector(config);
    database = new NexusDatabase(connector);

    // Create a test table
    database.update("""
        CREATE TABLE users (
          id INTEGER PRIMARY KEY AUTOINCREMENT,
          name TEXT NOT NULL,
          email TEXT UNIQUE NOT NULL,
          age INTEGER,
          active INTEGER DEFAULT 1
        )
        """);
  }

  @AfterEach
  void tearDown() {
    if (database != null) {
      database.close();
    }
    if (connector != null) {
      connector.close();
    }
  }

  // Helper record for tests
  record User(int id, String name, String email, int age) {

  }

  @Nested
  @DisplayName("Query Operations")
  class QueryOperations {

    @Test
    @DisplayName("Should query multiple rows")
    void testQueryMultipleRows() {
      // Given
      database.update("INSERT INTO users (name, email, age) VALUES (?, ?, ?)",
          "Alice", "alice@example.com", 25);
      database.update("INSERT INTO users (name, email, age) VALUES (?, ?, ?)",
          "Bob", "bob@example.com", 30);
      database.update("INSERT INTO users (name, email, age) VALUES (?, ?, ?)",
          "Charlie", "charlie@example.com", 35);

      // When
      List<User> users = database.query(
          "SELECT id, name, email, age FROM users ORDER BY age",
          rs -> new User(
              rs.getInt("id"),
              rs.getString("name"),
              rs.getString("email"),
              rs.getInt("age")
          )
      );

      // Then
      assertEquals(3, users.size());
      assertEquals("Alice", users.get(0).name());
      assertEquals("Bob", users.get(1).name());
      assertEquals("Charlie", users.get(2).name());
    }

    @Test
    @DisplayName("Should query with parameters")
    void testQueryWithParameters() {
      // Given
      database.update("INSERT INTO users (name, email, age) VALUES (?, ?, ?)",
          "Alice", "alice@example.com", 25);
      database.update("INSERT INTO users (name, email, age) VALUES (?, ?, ?)",
          "Bob", "bob@example.com", 30);

      // When
      List<User> users = database.query(
          "SELECT id, name, email, age FROM users WHERE age > ?",
          rs -> new User(
              rs.getInt("id"),
              rs.getString("name"),
              rs.getString("email"),
              rs.getInt("age")
          ),
          26
      );

      // Then
      assertEquals(1, users.size());
      assertEquals("Bob", users.get(0).name());
      assertEquals(30, users.get(0).age());
    }

    @Test
    @DisplayName("Should return empty list when no results")
    void testQueryNoResults() {
      // When
      List<User> users = database.query(
          "SELECT id, name, email, age FROM users WHERE age > ?",
          rs -> new User(
              rs.getInt("id"),
              rs.getString("name"),
              rs.getString("email"),
              rs.getInt("age")
          ),
          100
      );

      // Then
      assertTrue(users.isEmpty());
    }

    @Test
    @DisplayName("Should query one row")
    void testQueryOne() {
      // Given
      database.update("INSERT INTO users (name, email, age) VALUES (?, ?, ?)",
          "Alice", "alice@example.com", 25);
      database.update("INSERT INTO users (name, email, age) VALUES (?, ?, ?)",
          "Bob", "bob@example.com", 30);

      // When
      Optional<User> user = database.queryOne(
          "SELECT id, name, email, age FROM users WHERE email = ?",
          rs -> new User(
              rs.getInt("id"),
              rs.getString("name"),
              rs.getString("email"),
              rs.getInt("age")
          ),
          "alice@example.com"
      );

      // Then
      assertTrue(user.isPresent());
      assertEquals("Alice", user.get().name());
      assertEquals("alice@example.com", user.get().email());
    }

    @Test
    @DisplayName("Should return empty optional when no results")
    void testQueryOneNoResults() {
      // When
      Optional<User> user = database.queryOne(
          "SELECT id, name, email, age FROM users WHERE email = ?",
          rs -> new User(
              rs.getInt("id"),
              rs.getString("name"),
              rs.getString("email"),
              rs.getInt("age")
          ),
          "nonexistent@example.com"
      );

      // Then
      assertFalse(user.isPresent());
    }

    @Test
    @DisplayName("Should handle null values in query results")
    void testQueryWithNulls() {
      // Given
      database.update("INSERT INTO users (name, email) VALUES (?, ?)",
          "Alice", "alice@example.com");

      // When
      Optional<User> user = database.queryOne(
          "SELECT id, name, email, age FROM users WHERE name = ?",
          rs -> new User(
              rs.getInt("id"),
              rs.getString("name"),
              rs.getString("email"),
              rs.getInt("age")
          ),
          "Alice"
      );

      // Then
      assertTrue(user.isPresent());
      assertEquals(0, user.get().age()); // NULL INTEGER becomes 0
    }
  }

  @Nested
  @DisplayName("Update Operations")
  class UpdateOperations {

    @Test
    @DisplayName("Should insert rows")
    void testInsert() {
      // When
      int rowsAffected = database.update(
          "INSERT INTO users (name, email, age) VALUES (?, ?, ?)",
          "Alice", "alice@example.com", 25
      );

      // Then
      assertEquals(1, rowsAffected);

      List<User> users = database.query(
          "SELECT id, name, email, age FROM users",
          rs -> new User(rs.getInt("id"), rs.getString("name"),
              rs.getString("email"), rs.getInt("age"))
      );
      assertEquals(1, users.size());
      assertEquals("Alice", users.get(0).name());
    }

    @Test
    @DisplayName("Should update rows")
    void testUpdate() {
      // Given
      database.update("INSERT INTO users (name, email, age) VALUES (?, ?, ?)",
          "Alice", "alice@example.com", 25);

      // When
      int rowsAffected = database.update(
          "UPDATE users SET age = ? WHERE email = ?",
          26, "alice@example.com"
      );

      // Then
      assertEquals(1, rowsAffected);

      Optional<User> user = database.queryOne(
          "SELECT id, name, email, age FROM users WHERE email = ?",
          rs -> new User(rs.getInt("id"), rs.getString("name"),
              rs.getString("email"), rs.getInt("age")),
          "alice@example.com"
      );
      assertTrue(user.isPresent());
      assertEquals(26, user.get().age());
    }

    @Test
    @DisplayName("Should delete rows")
    void testDelete() {
      // Given
      database.update("INSERT INTO users (name, email, age) VALUES (?, ?, ?)",
          "Alice", "alice@example.com", 25);
      database.update("INSERT INTO users (name, email, age) VALUES (?, ?, ?)",
          "Bob", "bob@example.com", 30);

      // When
      int rowsAffected = database.update(
          "DELETE FROM users WHERE email = ?",
          "alice@example.com"
      );

      // Then
      assertEquals(1, rowsAffected);

      List<User> users = database.query(
          "SELECT id, name, email, age FROM users",
          rs -> new User(rs.getInt("id"), rs.getString("name"),
              rs.getString("email"), rs.getInt("age"))
      );
      assertEquals(1, users.size());
      assertEquals("Bob", users.get(0).name());
    }

    @Test
    @DisplayName("Should return 0 when update affects no rows")
    void testUpdateNoRows() {
      // When
      int rowsAffected = database.update(
          "UPDATE users SET age = ? WHERE email = ?",
          26, "nonexistent@example.com"
      );

      // Then
      assertEquals(0, rowsAffected);
    }
  }

  @Nested
  @DisplayName("Insert with Generated Keys")
  class InsertWithKeys {

    @Test
    @DisplayName("Should insert and return generated key")
    void testInsertWithGeneratedKey() {
      // When
      Integer id = database.insert(
          "INSERT INTO users (name, email, age) VALUES (?, ?, ?)",
          rs -> rs.getInt(1),
          "Alice", "alice@example.com", 25
      );

      // Then
      assertNotNull(id);
      assertTrue(id > 0);

      Optional<User> user = database.queryOne(
          "SELECT id, name, email, age FROM users WHERE id = ?",
          rs -> new User(rs.getInt("id"), rs.getString("name"),
              rs.getString("email"), rs.getInt("age")),
          id
      );
      assertTrue(user.isPresent());
      assertEquals(id, user.get().id());
      assertEquals("Alice", user.get().name());
    }

    @Test
    @DisplayName("Should insert multiple rows and return generated keys")
    void testInsertMultipleWithKeys() {
      // When
      Integer id1 = database.insert(
          "INSERT INTO users (name, email, age) VALUES (?, ?, ?)",
          rs -> rs.getInt(1),
          "Alice", "alice@example.com", 25
      );

      Integer id2 = database.insert(
          "INSERT INTO users (name, email, age) VALUES (?, ?, ?)",
          rs -> rs.getInt(1),
          "Bob", "bob@example.com", 30
      );

      // Then
      assertNotNull(id1);
      assertNotNull(id2);
      assertTrue(id2 > id1);
    }
  }

  @Nested
  @DisplayName("Transaction Management")
  class TransactionManagement {

    @Test
    @DisplayName("Should commit transaction")
    void testCommitTransaction() throws SQLException {
      // When
      database.beginTransaction();
      database.update("INSERT INTO users (name, email, age) VALUES (?, ?, ?)",
          "Alice", "alice@example.com", 25);
      database.update("INSERT INTO users (name, email, age) VALUES (?, ?, ?)",
          "Bob", "bob@example.com", 30);
      database.commitTransaction();

      // Then
      List<User> users = database.query(
          "SELECT id, name, email, age FROM users",
          rs -> new User(rs.getInt("id"), rs.getString("name"),
              rs.getString("email"), rs.getInt("age"))
      );
      assertEquals(2, users.size());
    }

    @Test
    @DisplayName("Should rollback transaction")
    void testRollbackTransaction() throws SQLException {
      // Given
      database.update("INSERT INTO users (name, email, age) VALUES (?, ?, ?)",
          "Charlie", "charlie@example.com", 35);

      // When
      database.beginTransaction();
      database.update("INSERT INTO users (name, email, age) VALUES (?, ?, ?)",
          "Alice", "alice@example.com", 25);
      database.update("INSERT INTO users (name, email, age) VALUES (?, ?, ?)",
          "Bob", "bob@example.com", 30);
      database.rollbackTransaction();

      // Then
      List<User> users = database.query(
          "SELECT id, name, email, age FROM users",
          rs -> new User(rs.getInt("id"), rs.getString("name"),
              rs.getString("email"), rs.getInt("age"))
      );
      assertEquals(1, users.size());
      assertEquals("Charlie", users.get(0).name());
    }

    @Test
    @DisplayName("Should throw exception when beginning transaction twice")
    void testBeginTransactionTwice() throws SQLException {
      // Given
      database.beginTransaction();

      // When/Then
      assertThrows(SQLException.class, () -> database.beginTransaction());

      // Cleanup
      database.rollbackTransaction();
    }

    @Test
    @DisplayName("Should throw exception when committing without transaction")
    void testCommitWithoutTransaction() {
      // When/Then
      assertThrows(SQLException.class, () -> database.commitTransaction());
    }

    @Test
    @DisplayName("Should throw exception when rolling back without transaction")
    void testRollbackWithoutTransaction() {
      // When/Then
      assertThrows(SQLException.class, () -> database.rollbackTransaction());
    }

    @Test
    @DisplayName("Should rollback transaction on error")
    void testRollbackOnError() throws SQLException {
      // Given
      database.beginTransaction();
      database.update("INSERT INTO users (name, email, age) VALUES (?, ?, ?)",
          "Alice", "alice@example.com", 25);

      try {
        // This should fail due to unique constraint
        database.update("INSERT INTO users (name, email, age) VALUES (?, ?, ?)",
            "Bob", "alice@example.com", 30);
      } catch (DatabaseException e) {
        database.rollbackTransaction();
      }

      // Then
      List<User> users = database.query(
          "SELECT id, name, email, age FROM users",
          rs -> new User(rs.getInt("id"), rs.getString("name"),
              rs.getString("email"), rs.getInt("age"))
      );
      assertEquals(0, users.size());
    }
  }

  @Nested
  @DisplayName("Error Handling")
  class ErrorHandling {

    @Test
    @DisplayName("Should throw DatabaseException on invalid SQL")
    void testInvalidSql() {
      // When/Then
      assertThrows(DatabaseException.class, () -> {
        database.query("SELECT * FROM nonexistent_table",
            rs -> rs.getString(1));
      });
    }

    @Test
    @DisplayName("Should throw DatabaseException on constraint violation")
    void testConstraintViolation() {
      // Given
      database.update("INSERT INTO users (name, email, age) VALUES (?, ?, ?)",
          "Alice", "alice@example.com", 25);

      // When/Then - Try to insert duplicate email
      assertThrows(DatabaseException.class, () -> {
        database.update("INSERT INTO users (name, email, age) VALUES (?, ?, ?)",
            "Bob", "alice@example.com", 30);
      });
    }

    @Test
    @DisplayName("Should throw DatabaseException when mapper throws SQLException")
    void testMapperThrowsSqlException() {
      // Given
      database.update("INSERT INTO users (name, email, age) VALUES (?, ?, ?)",
          "Alice", "alice@example.com", 25);

      // When/Then
      assertThrows(DatabaseException.class, () -> {
        database.query("SELECT * FROM users",
            rs -> {
              throw new SQLException("Mapper error");
            });
      });
    }
  }

  @Nested
  @DisplayName("Connection Management")
  class ConnectionManagement {

    @Test
    @DisplayName("Should execute work within connection")
    void testWithConnection() {
      // When
      Integer count = database.withConnection(conn -> {
        try (var stmt = conn.createStatement();
            var rs = stmt.executeQuery("SELECT COUNT(*) FROM users")) {
          return rs.next() ? rs.getInt(1) : 0;
        }
      });

      // Then
      assertEquals(0, count);
    }

    @Test
    @DisplayName("Should handle multiple sequential operations")
    void testMultipleSequentialOperations() {
      // When
      database.update("INSERT INTO users (name, email, age) VALUES (?, ?, ?)",
          "Alice", "alice@example.com", 25);
      database.update("INSERT INTO users (name, email, age) VALUES (?, ?, ?)",
          "Bob", "bob@example.com", 30);
      database.update("UPDATE users SET age = ? WHERE name = ?", 26, "Alice");
      database.update("DELETE FROM users WHERE name = ?", "Bob");

      // Then
      List<User> users = database.query(
          "SELECT id, name, email, age FROM users",
          rs -> new User(rs.getInt("id"), rs.getString("name"),
              rs.getString("email"), rs.getInt("age"))
      );
      assertEquals(1, users.size());
      assertEquals("Alice", users.get(0).name());
      assertEquals(26, users.get(0).age());
    }

    @Test
    @DisplayName("Should clean up resources on close")
    void testCloseCleanup() throws SQLException {
      // Given
      database.beginTransaction();
      database.update("INSERT INTO users (name, email, age) VALUES (?, ?, ?)",
          "Alice", "alice@example.com", 25);

      // When
      database.close();

      // Then - Transaction should be rolled back
      // Create new database instance to verify
      NexusDatabase newDb = new NexusDatabase(connector);
      List<User> users = newDb.query(
          "SELECT id, name, email, age FROM users",
          rs -> new User(rs.getInt("id"), rs.getString("name"),
              rs.getString("email"), rs.getInt("age"))
      );
      assertEquals(0, users.size());
      newDb.close();
    }
  }
}