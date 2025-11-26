package org.nexus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.nexus.config.db.DatabaseConfig;
import org.nexus.dbconnector.DatabaseConnectorFactory;
import org.nexus.dbconnector.SqliteConnector;
import org.nexus.exceptions.DatabaseException;
import org.nexus.interfaces.DatabaseConnector;

@DisplayName("NexusDatabaseMigrator Integration Tests")
@Execution(ExecutionMode.SAME_THREAD)
class NexusDatabaseMigratorTest {

  private Path tempDir;
  private Path dbFile;
  private Path migrationsDir;
  private Path envFile;
  private NexusDatabaseMigrator migrator;

  @BeforeEach
  void setUp() throws IOException {
    // close any existing instance before creating temp files
    NexusConfig.closeInstance();

    // Create a fresh temp directory for each test
    tempDir = Files.createTempDirectory("nexus-migrator-test-");
    dbFile = tempDir.resolve("test.db");
    migrationsDir = tempDir.resolve("migrations");

    Files.createDirectories(migrationsDir);

    if (!Files.exists(migrationsDir) || !Files.isDirectory(migrationsDir)) {
      throw new IllegalStateException(
          "Failed to create migrations directory: %s".formatted(migrationsDir));
    }

    // new .env file
    envFile = tempDir.resolve(".env");

    String envContent = String.format("""
        DB1_NAME=testdb
        DB1_TYPE=SQLITE
        DB1_URL=jdbc:sqlite:%s
        DB1_MIGRATIONS_PATH=%s
        """, dbFile.toString(), migrationsDir.toAbsolutePath());

    Files.writeString(envFile, envContent);

    // get a fresh instance and initialize it
    NexusConfig config = NexusConfig.getInstance();
    config.setEnvFilePath(envFile.toString());
    config.init(new String[]{});

    // verify the config was properly loaded
    DatabaseConfig dbConfig = config.getDatabaseConfig("testdb");
    String configuredPath = dbConfig.migrationsPath();
    if (!migrationsDir.toAbsolutePath().toString().equals(configuredPath)) {
      throw new IllegalStateException(
          "Migration path mismatch! Expected: %s, but config has: %s"
              .formatted(migrationsDir.toAbsolutePath(), configuredPath));
    }

    migrator = new NexusDatabaseMigrator();
  }

  @AfterEach
  void tearDown() throws IOException {
    // clean up
    migrator = null;
    NexusConfig.closeInstance();
    if (tempDir != null && Files.exists(tempDir)) {
      deleteDirectory(tempDir);
    }
  }

  private void deleteDirectory(Path directory) throws IOException {
    if (Files.exists(directory)) {
      try (var stream = Files.walk(directory)) {
        stream.sorted(java.util.Comparator.reverseOrder())
            .forEach(path -> {
              try {
                Files.delete(path);
              } catch (IOException e) {
                // Log but don't fail the test cleanup
                System.err.println("Failed to delete: " + path + " - " + e.getMessage());
              }
            });
      }
    }
  }

  @Nested
  @DisplayName("Migration Table Creation")
  class MigrationTableCreation {

    @Test
    @DisplayName("Should create migrations table if not exists")
    void testCreateMigrationTable() {
      // When
      migrator.migrateAll("testdb");

      // Then - Verify migration table exists
      DatabaseConfig config = NexusConfig.getInstance().getDatabaseConfig("testdb");
      try (SqliteConnector connector = new SqliteConnector(config);
          NexusDatabase db = new NexusDatabase(connector)) {

        Integer count = db.queryOne(
            "SELECT COUNT(*) as cnt FROM sqlite_master WHERE type='table' AND name='migrations'",
            rs -> rs.getInt("cnt")
        ).orElse(0);

        assertEquals(1, count);
      }
    }

    @Test
    @DisplayName("Should not fail if migrations table already exists")
    void testMigrationTableAlreadyExists() {
      // Given - Run migrations once
      migrator.migrateAll("testdb");

      // When - Run migrations again (should work fine since migrator creates new connector)
      migrator.migrateAll("testdb");

      // Then - Should not throw
      DatabaseConfig config = NexusConfig.getInstance().getDatabaseConfig("testdb");
      try (SqliteConnector connector = new SqliteConnector(config);
          NexusDatabase db = new NexusDatabase(connector)) {

        Integer count = db.queryOne(
            "SELECT COUNT(*) as cnt FROM sqlite_master WHERE type='table' AND name='migrations'",
            rs -> rs.getInt("cnt")
        ).orElse(0);

        assertEquals(1, count);
      }
    }
  }

  @Nested
  @DisplayName("Migration File Loading")
  class MigrationFileLoading {

    @Test
    @DisplayName("Should load and apply single migration")
    void testSingleMigration() throws IOException {
      // Given
      Files.writeString(migrationsDir.resolve("001_create_users.sql"), """
          CREATE TABLE users (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL,
            email TEXT UNIQUE NOT NULL
          );
          """);

      // When
      migrator.migrateAll("testdb");

      // Then - Verify table was created
      DatabaseConfig config = NexusConfig.getInstance().getDatabaseConfig("testdb");
      try (SqliteConnector connector = new SqliteConnector(config);
          NexusDatabase db = new NexusDatabase(connector)) {

        Integer count = db.queryOne(
            "SELECT COUNT(*) as cnt FROM sqlite_master WHERE type='table' AND name='users'",
            rs -> rs.getInt("cnt")
        ).orElse(0);

        assertEquals(1, count);

        // Verify migration was recorded
        boolean applied = db.queryOne(
            "SELECT 1 FROM migrations WHERE name = ?",
            _ -> true,
            "001_create_users.sql"
        ).isPresent();

        assertTrue(applied);
      }
    }

    @Test
    @DisplayName("Should load and apply multiple migrations in order")
    void testMultipleMigrationsInOrder() throws IOException {
      // Given
      Files.writeString(migrationsDir.resolve("001_create_users.sql"), """
          CREATE TABLE users (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL
          );
          """);

      Files.writeString(migrationsDir.resolve("002_add_email_to_users.sql"), """
          ALTER TABLE users ADD COLUMN email TEXT;
          """);

      Files.writeString(migrationsDir.resolve("003_create_posts.sql"), """
          CREATE TABLE posts (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            user_id INTEGER NOT NULL,
            title TEXT NOT NULL,
            FOREIGN KEY (user_id) REFERENCES users(id)
          );
          """);

      // When
      migrator.migrateAll("testdb");

      // Then
      DatabaseConfig config = NexusConfig.getInstance().getDatabaseConfig("testdb");
      try (SqliteConnector connector = new SqliteConnector(config);
          NexusDatabase db = new NexusDatabase(connector)) {

        // Verify all tables were created
        Integer userTableCount = db.queryOne(
            "SELECT COUNT(*) as cnt FROM sqlite_master WHERE type='table' AND name='users'",
            rs -> rs.getInt("cnt")
        ).orElse(0);
        assertEquals(1, userTableCount);

        Integer postTableCount = db.queryOne(
            "SELECT COUNT(*) as cnt FROM sqlite_master WHERE type='table' AND name='posts'",
            rs -> rs.getInt("cnt")
        ).orElse(0);
        assertEquals(1, postTableCount);

        // Verify all migrations were recorded
        Integer migrationCount = db.queryOne(
            "SELECT COUNT(*) as cnt FROM migrations",
            rs -> rs.getInt("cnt")
        ).orElse(0);
        assertEquals(3, migrationCount);
      }
    }

    @Test
    @DisplayName("Should skip already applied migrations")
    void testSkipAppliedMigrations() throws IOException {
      // Given
      Files.writeString(migrationsDir.resolve("001_create_users.sql"), """
          CREATE TABLE users (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL
          );
          """);

      // Apply first migration
      migrator.migrateAll("testdb");

      // Add second migration
      Files.writeString(migrationsDir.resolve("002_create_posts.sql"), """
          CREATE TABLE posts (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            title TEXT NOT NULL
          );
          """);

      // When - Run migrations again
      migrator.migrateAll("testdb");

      // Then
      DatabaseConfig config = NexusConfig.getInstance().getDatabaseConfig("testdb");
      try (SqliteConnector connector = new SqliteConnector(config);
          NexusDatabase db = new NexusDatabase(connector)) {

        // Both tables should exist
        Integer userTableCount = db.queryOne(
            "SELECT COUNT(*) as cnt FROM sqlite_master WHERE type='table' AND name='users'",
            rs -> rs.getInt("cnt")
        ).orElse(0);
        assertEquals(1, userTableCount);

        Integer postTableCount = db.queryOne(
            "SELECT COUNT(*) as cnt FROM sqlite_master WHERE type='table' AND name='posts'",
            rs -> rs.getInt("cnt")
        ).orElse(0);
        assertEquals(1, postTableCount);

        // Both migrations should be recorded
        Integer migrationCount = db.queryOne(
            "SELECT COUNT(*) as cnt FROM migrations",
            rs -> rs.getInt("cnt")
        ).orElse(0);
        assertEquals(2, migrationCount);
      }
    }

    @Test
    @DisplayName("Should handle empty migrations directory")
    void testEmptyMigrationsDirectory() {
      // When/Then - Should not throw
      migrator.migrateAll("testdb");

      // Verify only migration table was created
      DatabaseConfig config = NexusConfig.getInstance().getDatabaseConfig("testdb");
      try (SqliteConnector connector = new SqliteConnector(config);
          NexusDatabase db = new NexusDatabase(connector)) {

        Integer migrationCount = db.queryOne(
            "SELECT COUNT(*) as cnt FROM migrations",
            rs -> rs.getInt("cnt")
        ).orElse(0);
        assertEquals(0, migrationCount);
      }
    }
  }

  @Nested
  @DisplayName("SQL Statement Parsing")
  class SqlStatementParsing {

    @Test
    @DisplayName("Should parse multiple statements separated by semicolons")
    void testMultipleStatements() throws IOException {
      // Given
      Files.writeString(migrationsDir.resolve("001_multi.sql"), """
          CREATE TABLE users (id INTEGER PRIMARY KEY, name TEXT);
          CREATE TABLE posts (id INTEGER PRIMARY KEY, title TEXT);
          CREATE INDEX idx_posts_title ON posts(title);
          """);

      // When
      migrator.migrateAll("testdb");

      // Then
      DatabaseConfig config = NexusConfig.getInstance().getDatabaseConfig("testdb");
      try (SqliteConnector connector = new SqliteConnector(config);
          NexusDatabase db = new NexusDatabase(connector)) {

        Integer tableCount = db.queryOne(
            "SELECT COUNT(*) as cnt FROM sqlite_master WHERE type='table' AND name IN ('users', 'posts')",
            rs -> rs.getInt("cnt")
        ).orElse(0);
        assertEquals(2, tableCount);

        Integer indexCount = db.queryOne(
            "SELECT COUNT(*) as cnt FROM sqlite_master WHERE type='index' AND name='idx_posts_title'",
            rs -> rs.getInt("cnt")
        ).orElse(0);
        assertEquals(1, indexCount);
      }
    }

    @Test
    @DisplayName("Should ignore SQL comments")
    void testIgnoreComments() throws IOException {
      // Given
      Files.writeString(migrationsDir.resolve("001_with_comments.sql"), """
          -- This is a comment
          CREATE TABLE users (
            id INTEGER PRIMARY KEY,
            -- Another comment
            name TEXT NOT NULL
          );
          # This is also a comment
          CREATE TABLE posts (id INTEGER PRIMARY KEY);
          """);

      // When
      migrator.migrateAll("testdb");

      // Then
      DatabaseConfig config = NexusConfig.getInstance().getDatabaseConfig("testdb");
      try (SqliteConnector connector = new SqliteConnector(config);
          NexusDatabase db = new NexusDatabase(connector)) {

        Integer tableCount = db.queryOne(
            "SELECT COUNT(*) as cnt FROM sqlite_master WHERE type='table' AND name IN ('users', 'posts')",
            rs -> rs.getInt("cnt")
        ).orElse(0);
        assertEquals(2, tableCount);
      }
    }

    @Test
    @DisplayName("Should handle statements without trailing semicolon")
    void testStatementWithoutSemicolon() throws IOException {
      // Given
      Files.writeString(migrationsDir.resolve("001_no_semicolon.sql"), """
          CREATE TABLE users (
            id INTEGER PRIMARY KEY,
            name TEXT NOT NULL
          )
          """);

      // When
      migrator.migrateAll("testdb");

      // Then
      DatabaseConfig config = NexusConfig.getInstance().getDatabaseConfig("testdb");
      try (SqliteConnector connector = new SqliteConnector(config);
          NexusDatabase db = new NexusDatabase(connector)) {

        Integer tableCount = db.queryOne(
            "SELECT COUNT(*) as cnt FROM sqlite_master WHERE type='table' AND name='users'",
            rs -> rs.getInt("cnt")
        ).orElse(0);
        assertEquals(1, tableCount);
      }
    }

    @Test
    @DisplayName("Should handle empty lines and whitespace")
    void testEmptyLinesAndWhitespace() throws IOException {
      // Given
      Files.writeString(migrationsDir.resolve("001_whitespace.sql"), """
          
          
          CREATE TABLE users (id INTEGER PRIMARY KEY);
          
          
          CREATE TABLE posts (id INTEGER PRIMARY KEY);
          
          
          """);

      // When
      migrator.migrateAll("testdb");

      // Then
      DatabaseConfig config = NexusConfig.getInstance().getDatabaseConfig("testdb");
      try (SqliteConnector connector = new SqliteConnector(config);
          NexusDatabase db = new NexusDatabase(connector)) {

        Integer tableCount = db.queryOne(
            "SELECT COUNT(*) as cnt FROM sqlite_master WHERE type='table' AND name IN ('users', 'posts')",
            rs -> rs.getInt("cnt")
        ).orElse(0);
        assertEquals(2, tableCount);
      }
    }
  }

  @Nested
  @DisplayName("Transaction Handling")
  class TransactionHandling {

    @Test
    @DisplayName("Should rollback migration on error")
    void testRollbackOnError() throws IOException {
      // Given
      Files.writeString(migrationsDir.resolve("001_with_error.sql"), """
          CREATE TABLE users (id INTEGER PRIMARY KEY, name TEXT);
          INSERT INTO users (id, name) VALUES (1, 'Alice');
          CREATE TABLE invalid syntax here;
          INSERT INTO users (id, name) VALUES (2, 'Bob');
          """);

      // When
      assertThrows(DatabaseException.class, () -> migrator.migrateAll("testdb"));

      // Then - Nothing should be applied due to rollback
      DatabaseConfig config = NexusConfig.getInstance().getDatabaseConfig("testdb");
      try (SqliteConnector connector = new SqliteConnector(config);
          NexusDatabase db = new NexusDatabase(connector)) {

        // Migration table should exist but be empty
        Integer migrationCount = db.queryOne(
            "SELECT COUNT(*) as cnt FROM migrations",
            rs -> rs.getInt("cnt")
        ).orElse(0);
        assertEquals(0, migrationCount);

        // Users table should not exist
        Integer tableCount = db.queryOne(
            "SELECT COUNT(*) as cnt FROM sqlite_master WHERE type='table' AND name='users'",
            rs -> rs.getInt("cnt")
        ).orElse(0);
        assertEquals(0, tableCount);
      }
    }

    @Test
    @DisplayName("Should apply each migration in separate transaction")
    void testSeparateTransactions() throws IOException {
      // Given
      Files.writeString(migrationsDir.resolve("001_good.sql"), """
          CREATE TABLE users (id INTEGER PRIMARY KEY, name TEXT);
          """);

      Files.writeString(migrationsDir.resolve("002_bad.sql"), """
          CREATE TABLE invalid syntax;
          """);

      Files.writeString(migrationsDir.resolve("003_good.sql"), """
          CREATE TABLE posts (id INTEGER PRIMARY KEY, title TEXT);
          """);

      // When - Apply migrations, second one will fail
      try {
        migrator.migrateAll("testdb");
      } catch (DatabaseException _) {
        // Expected
      }

      // Then - First migration should be applied, second rolled back
      DatabaseConfig config = NexusConfig.getInstance().getDatabaseConfig("testdb");
      try (SqliteConnector connector = new SqliteConnector(config);
          NexusDatabase db = new NexusDatabase(connector)) {

        // First migration should be applied
        Integer userTableCount = db.queryOne(
            "SELECT COUNT(*) as cnt FROM sqlite_master WHERE type='table' AND name='users'",
            rs -> rs.getInt("cnt")
        ).orElse(0);
        assertEquals(1, userTableCount);

        // Second migration should not be recorded
        boolean secondApplied = db.queryOne(
            "SELECT 1 FROM migrations WHERE name = ?",
            _ -> true,
            "002_bad.sql"
        ).isPresent();
        assertFalse(secondApplied);

        // Only first migration should be recorded
        Integer migrationCount = db.queryOne(
            "SELECT COUNT(*) as cnt FROM migrations",
            rs -> rs.getInt("cnt")
        ).orElse(0);
        assertEquals(1, migrationCount);
      }
    }
  }

  @Disabled
  @Nested
  @DisplayName("Multiple Database Support")
  class MultipleDatabaseSupport {

    @Test
    @DisplayName("Should migrate specific database")
    void testMigrateSpecificDatabase() throws IOException {
      // Given - Create second database config
      Path db2File = tempDir.resolve("test2.db");
      Path migrations2Dir = tempDir.resolve("migrations2");
      Files.createDirectories(migrations2Dir);

      // Recreate config with two databases
      String envContent = String.format("""
              DB1_NAME=testdb1
              DB1_TYPE=SQLITE
              DB1_URL=jdbc:sqlite:%s
              DB1_MIGRATIONS_PATH=%s
              DB2_NAME=testdb2
              DB2_TYPE=SQLITE
              DB2_URL=jdbc:sqlite:%s
              DB2_MIGRATIONS_PATH=%s
              """, dbFile.toString(), migrationsDir.toString(),
          db2File, migrations2Dir);

      Files.writeString(envFile, envContent);
      NexusConfig config = NexusConfig.getInstance();
      config.setEnvFilePath(envFile.toString());
      config.init(new String[]{});

      // Recreate migrator with new config
      migrator = new NexusDatabaseMigrator();

      // Create migration for first database
      Files.writeString(migrationsDir.resolve("001_db1.sql"), """
          CREATE TABLE db1_table (id INTEGER PRIMARY KEY);
          """);

      // Create migration for second database
      Files.writeString(migrations2Dir.resolve("001_db2.sql"), """
          CREATE TABLE db2_table (id INTEGER PRIMARY KEY);
          """);

      // When - Migrate only first database
      migrator.migrateAll("testdb1");

      // Then - Only first database should have migrations
      DatabaseConfig db1Config = config.getDatabaseConfig("testdb1");
      try (SqliteConnector connector = new SqliteConnector(db1Config);
          NexusDatabase db = new NexusDatabase(connector)) {

        Integer tableCount = db.queryOne(
            "SELECT COUNT(*) as cnt FROM sqlite_master WHERE type='table' AND name='db1_table'",
            rs -> rs.getInt("cnt")
        ).orElse(0);
        assertEquals(1, tableCount);
      }

      // Second database should not have migrations yet
      DatabaseConfig db2Config = config.getDatabaseConfig("testdb2");
      try (SqliteConnector connector = new SqliteConnector(db2Config);
          NexusDatabase db = new NexusDatabase(connector)) {

        Integer tableCount = db.queryOne(
            "SELECT COUNT(*) as cnt FROM sqlite_master WHERE type='table' AND name='db2_table'",
            rs -> rs.getInt("cnt")
        ).orElse(0);
        assertEquals(0, tableCount);
      }
    }

    @Test
    @DisplayName("Should migrate all databases when no specific name provided")
    void testMigrateAllDatabases() throws IOException {
      // Given - Create second database config
      Path db2File = tempDir.resolve("test2.db");
      Path migrations2Dir = tempDir.resolve("migrations2");
      Files.createDirectories(migrations2Dir);

      // Recreate config with two databases
      String envContent = String.format("""
              DB1_NAME=testdb1
              DB1_TYPE=SQLITE
              DB1_URL=jdbc:sqlite:%s
              DB1_MIGRATIONS_PATH=%s
              DB2_NAME=testdb2
              DB2_TYPE=SQLITE
              DB2_URL=jdbc:sqlite:%s
              DB2_MIGRATIONS_PATH=%s
              """, dbFile.toString(), migrationsDir.toString(),
          db2File, migrations2Dir);

      Files.writeString(envFile, envContent);
      NexusConfig config = NexusConfig.getInstance();
      config.setEnvFilePath(envFile.toString());
      config.init(new String[]{});

      // Recreate migrator with new config
      migrator = new NexusDatabaseMigrator();

      // Create migrations
      Files.writeString(migrationsDir.resolve("001_db1.sql"), """
          CREATE TABLE db1_table (id INTEGER PRIMARY KEY);
          """);
      Files.writeString(migrations2Dir.resolve("001_db2.sql"), """
          CREATE TABLE db2_table (id INTEGER PRIMARY KEY);
          """);

      // When - Migrate all databases
      migrator.migrateAll(null);

      // Then - Both databases should have migrations
      DatabaseConfig db1Config = config.getDatabaseConfig("testdb1");
      try (SqliteConnector connector = new SqliteConnector(db1Config);
          NexusDatabase db = new NexusDatabase(connector)) {

        Integer tableCount = db.queryOne(
            "SELECT COUNT(*) as cnt FROM sqlite_master WHERE type='table' AND name='db1_table'",
            rs -> rs.getInt("cnt")
        ).orElse(0);
        assertEquals(1, tableCount);
      }

      DatabaseConfig db2Config = config.getDatabaseConfig("testdb2");
      try (SqliteConnector connector = new SqliteConnector(db2Config);
          NexusDatabase db = new NexusDatabase(connector)) {

        Integer tableCount = db.queryOne(
            "SELECT COUNT(*) as cnt FROM sqlite_master WHERE type='table' AND name='db2_table'",
            rs -> rs.getInt("cnt")
        ).orElse(0);
        assertEquals(1, tableCount);
      }
    }
  }

  @Disabled
  @Nested
  @DisplayName("Error Cases")
  class ErrorCases {

    @Test
    @DisplayName("Should throw exception when migrations directory does not exist")
    void testMigrationsDirectoryNotFound() throws IOException {
      // Given
      String envContent = String.format("""
          DB1_NAME=testdb
          DB1_TYPE=SQLITE
          DB1_URL=jdbc:sqlite:%s
          DB1_MIGRATIONS_PATH=%s
          """, dbFile.toString(), tempDir.resolve("nonexistent"));

      Files.writeString(envFile, envContent);
      NexusConfig config = NexusConfig.getInstance();
      config.setEnvFilePath(envFile.toString());
      config.init(new String[]{});

      // Recreate migrator with new config
      migrator = new NexusDatabaseMigrator();

      // When/Then
      assertThrows(IllegalStateException.class, () -> migrator.migrateAll("testdb"));
    }

    @Test
    @DisplayName("Should skip database when no migrations path configured")
    void testNoMigrationsPathConfigured() throws IOException {
      // Given
      String envContent = String.format("""
          DB1_NAME=testdb
          DB1_TYPE=SQLITE
          DB1_URL=jdbc:sqlite:%s
          """, dbFile.toString());

      Files.writeString(envFile, envContent);
      NexusConfig config = NexusConfig.getInstance();
      config.setEnvFilePath(envFile.toString());
      config.init(new String[]{});

      migrator = new NexusDatabaseMigrator();

      // When
      migrator.migrateAll("testdb");

      // Then - Verify migration table was NOT created (since migrations were skipped)
      try (DatabaseConnector connector = DatabaseConnectorFactory.createNonCached(
          config.getAllDatabaseConfigs().get("testdb"))) {
        NexusDatabase db = new NexusDatabase(connector);

        boolean tableExists = db.withConnection(conn -> {
          try (var stmt = conn.createStatement()) {
            stmt.executeQuery("SELECT COUNT(*) FROM migrations");
            return true;
          } catch (SQLException _) {
            return false;
          }
        });

        assertFalse(tableExists, "Migration table should not exist when migrations are skipped");
      }
    }

    @Test
    @DisplayName("Should throw exception when database config not found")
    void testDatabaseConfigNotFound() {
      // When/Then
      assertThrows(
          IllegalArgumentException.class,
          () -> migrator.migrateAll("nonexistent"));
    }
  }
}