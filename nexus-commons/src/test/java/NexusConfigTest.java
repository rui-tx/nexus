import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nexus.NexusConfig;
import org.nexus.config.db.DatabaseConfig;
import org.nexus.enums.DatabaseType;

@DisplayName("NexusConfig Tests")
class NexusConfigTest {

  @TempDir
  Path tempDir;

  private Path envFile;

  private static void resetNexusConfig() {
    try {
      Field instance = NexusConfig.class.getDeclaredField("instance");
      instance.setAccessible(true);
      instance.set(null, null);
    } catch (Exception e) {
      throw new RuntimeException("Failed to reset NexusConfig singleton", e);
    }
  }

  @BeforeEach
  void setUp() throws IOException {
    resetNexusConfig();

    String envContent = """
        APP_ENV=test
        APP_PORT=8080
        APP_HOST=localhost
        DB1_NAME=testdb
        DB1_TYPE=SQLITE
        DB1_URL=jdbc:sqlite:test.db
        DB1_USER=testuser
        DB1_PASSWORD=testpass
        DB1_POOL_SIZE=5
        DB2_NAME=anotherdb
        DB2_TYPE=POSTGRES
        DB2_URL=jdbc:postgresql://localhost:5432/testdb
        DB2_USER=postgres
        DB2_PASSWORD=postgres
        """;
    envFile = tempDir.resolve(".env");
    Files.writeString(envFile, envContent);
  }

  @AfterEach
  void tearDown() {
    resetNexusConfig();
  }

  @Nested
  @DisplayName("Configuration Loading")
  class ConfigurationLoading {

    @Test
    @DisplayName("Should load configuration from .env file")
    void testLoadConfigFromEnvFile() {
      NexusConfig config = NexusConfig.getInstance();
      config.setEnvFilePath(envFile.toString());

      config.init(new String[]{});

      assertEquals("test", config.get("APP_ENV"));
      assertEquals("8080", config.get("APP_PORT"));
      assertEquals("localhost", config.get("APP_HOST"));
      assertEquals("testdb", config.get("DB1_NAME"));
      assertEquals("SQLITE", config.get("DB1_TYPE"));
    }

    @Test
    @DisplayName("Should work without .env file")
    void testWorksWithoutEnvFile() {
      NexusConfig config = NexusConfig.getInstance();
      config.setEnvFilePath("nonexistent.env");

      config.init(new String[]{"--APP_ENV=production"});

      assertEquals("production", config.get("APP_ENV"));
      assertNotNull(config.get("PATH")); // System env var should still be loaded
    }

    @Test
    @DisplayName("Should handle malformed .env file gracefully")
    void testMalformedEnvFile() throws IOException {
      Path malformedEnv = tempDir.resolve("malformed.env");
      Files.writeString(malformedEnv, "INVALID LINE WITHOUT EQUALS\nVALID=value");

      NexusConfig config = NexusConfig.getInstance();
      config.setEnvFilePath(malformedEnv.toString());

      // Should not throw, just skip invalid lines
      config.init(new String[]{});
      assertEquals("value", config.get("VALID"));
    }

    @Test
    @DisplayName("CLI arguments should take precedence over .env file")
    void testCliArgumentsTakePrecedence() {
      NexusConfig config = NexusConfig.getInstance();
      config.setEnvFilePath(envFile.toString());

      config.init(new String[]{
          "--APP_ENV=staging",
          "--DB1_NAME=stagingdb",
          "--DB1_TYPE=POSTGRES",
          "--DB1_POOL_SIZE=15"
      });

      assertEquals("staging", config.get("APP_ENV"));
      assertEquals("stagingdb", config.get("DB1_NAME"));
      assertEquals("POSTGRES", config.get("DB1_TYPE"));
      assertEquals(15, config.getInt("DB1_POOL_SIZE", 0));
    }

    @Test
    @DisplayName("System environment variables should be available")
    void testSystemEnvironmentVariables() {
      NexusConfig config = NexusConfig.getInstance();
      config.setEnvFilePath("nonexistent.env");

      config.init(new String[]{});

      // PATH should exist on all systems
      assertNotNull(config.get("PATH"));
    }
  }

  @Nested
  @DisplayName("CLI Arguments Parsing")
  class CliArgumentsParsing {

    @Test
    @DisplayName("Should parse key=value arguments")
    void testParseKeyValueArgs() {
      NexusConfig config = NexusConfig.getInstance();
      config.setEnvFilePath("nonexistent.env");

      config.init(new String[]{
          "--KEY1=value1",
          "--KEY2=value2"
      });

      assertEquals("value1", config.get("KEY1"));
      assertEquals("value2", config.get("KEY2"));
    }

    @Test
    @DisplayName("Should parse boolean flags")
    void testParseBooleanFlags() {
      NexusConfig config = NexusConfig.getInstance();
      config.setEnvFilePath("nonexistent.env");

      config.init(new String[]{"--ENABLE_FEATURE"});

      assertEquals("true", config.get("ENABLE_FEATURE"));
    }

    @Test
    @DisplayName("Should handle arguments with equals in value")
    void testArgsWithEqualsInValue() {
      NexusConfig config = NexusConfig.getInstance();
      config.setEnvFilePath("nonexistent.env");

      config.init(
          new String[]{"--CONNECTION_STRING=jdbc:postgresql://localhost:5432/db?user=admin"});

      assertEquals("jdbc:postgresql://localhost:5432/db?user=admin",
          config.get("CONNECTION_STRING"));
    }

    @Test
    @DisplayName("Should handle null arguments array")
    void testNullArgsArray() {
      NexusConfig config = NexusConfig.getInstance();
      config.setEnvFilePath("nonexistent.env");

      // Should not throw
      config.init(null);

      assertNotNull(config.get("PATH")); // System env should still load
    }
  }

  @Nested
  @DisplayName("Value Getters")
  class ValueGetters {

    @BeforeEach
    void initConfig() {
      NexusConfig config = NexusConfig.getInstance();
      config.setEnvFilePath("nonexistent.env");
      config.init(new String[]{
          "--STRING_VALUE=hello",
          "--INT_VALUE=42",
          "--LONG_VALUE=9876543210",
          "--BOOL_TRUE=true",
          "--BOOL_FALSE=false",
          "--BOOL_ONE=1",
          "--INVALID_INT=notanumber"
      });
    }

    @Test
    @DisplayName("Should get string values")
    void testGetString() {
      NexusConfig config = NexusConfig.getInstance();
      assertEquals("hello", config.get("STRING_VALUE"));
      assertNull(config.get("NONEXISTENT"));
      assertEquals("default", config.get("NONEXISTENT", "default"));
    }

    @Test
    @DisplayName("Should get integer values")
    void testGetInt() {
      NexusConfig config = NexusConfig.getInstance();
      assertEquals(42, config.getInt("INT_VALUE", 0));
      assertEquals(999, config.getInt("NONEXISTENT", 999));
      assertEquals(100, config.getInt("INVALID_INT", 100)); // Should use default on parse error
    }

    @Test
    @DisplayName("Should get long values")
    void testGetLong() {
      NexusConfig config = NexusConfig.getInstance();
      assertEquals(9876543210L, config.getLong("LONG_VALUE", 0L));
      assertEquals(999L, config.getLong("NONEXISTENT", 999L));
    }

    @Test
    @DisplayName("Should get boolean values")
    void testGetBoolean() {
      NexusConfig config = NexusConfig.getInstance();
      assertTrue(config.getBoolean("BOOL_TRUE", false));
      assertFalse(config.getBoolean("BOOL_FALSE", true));
      assertTrue(config.getBoolean("BOOL_ONE", false)); // "1" should be true
      assertFalse(config.getBoolean("NONEXISTENT", false));
      assertTrue(config.getBoolean("NONEXISTENT", true));
    }
  }

  @Nested
  @DisplayName("Database Configuration")
  class DatabaseConfiguration {

    @Test
    @DisplayName("Should load SQLite database config from .env")
    void testLoadSqliteConfig() {
      NexusConfig config = NexusConfig.getInstance();
      config.setEnvFilePath(envFile.toString());
      config.init(new String[]{});

      DatabaseConfig dbConfig = config.getDatabaseConfig("testdb");
      assertEquals(DatabaseType.SQLITE, dbConfig.type());
      assertEquals("jdbc:sqlite:test.db", dbConfig.url());
      assertEquals("testuser", dbConfig.username());
      assertEquals("testpass", dbConfig.password());
      assertEquals(5, dbConfig.poolSize());
    }

    @Test
    @DisplayName("Should load PostgreSQL database config from .env")
    void testLoadPostgresConfig() {
      NexusConfig config = NexusConfig.getInstance();
      config.setEnvFilePath(envFile.toString());
      config.init(new String[]{});

      DatabaseConfig dbConfig = config.getDatabaseConfig("anotherdb");
      assertEquals(DatabaseType.POSTGRES, dbConfig.type());
      assertEquals("jdbc:postgresql://localhost:5432/testdb", dbConfig.url());
      assertEquals("postgres", dbConfig.username());
      assertEquals("postgres", dbConfig.password());
    }

    @Test
    @DisplayName("Should use default values for missing database config properties")
    void testDatabaseConfigDefaults() {
      NexusConfig config = NexusConfig.getInstance();
      config.setEnvFilePath("nonexistent.env");

      config.init(new String[]{
          "--DB1_NAME=testdb",
          "--DB1_TYPE=SQLITE",
          "--DB1_URL=jdbc:sqlite:test.db"
      });

      DatabaseConfig dbConfig = config.getDatabaseConfig("testdb");
      assertEquals(DatabaseType.SQLITE, dbConfig.type());
      assertEquals("jdbc:sqlite:test.db", dbConfig.url());
      assertEquals(10, dbConfig.poolSize()); // default pool size
      assertTrue(dbConfig.autoCommit()); // default auto-commit
      assertEquals(30000, dbConfig.connectionTimeout()); // default timeout
    }

    @Test
    @DisplayName("Should load multiple database configurations")
    void testMultipleDatabaseConfigs() {
      NexusConfig config = NexusConfig.getInstance();
      config.setEnvFilePath(envFile.toString());
      config.init(new String[]{});

      assertTrue(config.getAllDatabaseConfigs().size() >= 2);
      assertNotNull(config.getDatabaseConfig("testdb"));
      assertNotNull(config.getDatabaseConfig("anotherdb"));
    }

    @Test
    @DisplayName("Should throw exception for missing database config")
    void testMissingDatabaseConfigThrowsException() {
      NexusConfig config = NexusConfig.getInstance();
      config.setEnvFilePath(envFile.toString());
      config.init(new String[]{});

      IllegalStateException exception = assertThrows(
          IllegalStateException.class, () -> config.getDatabaseConfig("nonexistent"));

      assertTrue(exception.getMessage().contains("No database configuration found"));
    }

    @Test
    @DisplayName("Should infer database name when only type and URL provided")
    void testInferDatabaseName() {
      NexusConfig config = NexusConfig.getInstance();
      config.setEnvFilePath("nonexistent.env");

      config.init(new String[]{
          "--DB1_TYPE=POSTGRES",
          "--DB1_URL=jdbc:postgresql://localhost:5432/mydb"
      });

      // Should infer name as "db1"
      DatabaseConfig dbConfig = config.getDatabaseConfig("db1");
      assertNotNull(dbConfig);
      assertEquals(DatabaseType.POSTGRES, dbConfig.type());
    }

    @Test
    @DisplayName("Should default to POSTGRES for invalid database type")
    void testInvalidDatabaseType() {
      NexusConfig config = NexusConfig.getInstance();
      config.setEnvFilePath("nonexistent.env");

      config.init(new String[]{
          "--DB1_NAME=testdb",
          "--DB1_TYPE=INVALID_TYPE",
          "--DB1_URL=jdbc:postgresql://localhost/db"
      });

      DatabaseConfig dbConfig = config.getDatabaseConfig("testdb");
      assertEquals(DatabaseType.POSTGRES, dbConfig.type()); // Should default to POSTGRES
    }
  }

  @Nested
  @DisplayName("Initialization Behavior")
  class InitializationBehavior {

    @Test
    @DisplayName("Should be idempotent - second init call ignored")
    void testInitIsIdempotent() {
      NexusConfig config = NexusConfig.getInstance();
      config.setEnvFilePath(envFile.toString());
      config.init(new String[]{"--APP_ENV=test"});

      config.init(new String[]{"--APP_ENV=production"}); // This should be ignored

      assertEquals("test", config.get("APP_ENV"));
    }

    @Test
    @DisplayName("Should not allow changing env file path after init")
    void testCannotChangeEnvPathAfterInit() {
      NexusConfig config = NexusConfig.getInstance();
      config.setEnvFilePath(envFile.toString());
      config.init(new String[]{});

      assertThrows(IllegalStateException.class, () -> config.setEnvFilePath("another.env"));
    }

    @Test
    @DisplayName("Should return same singleton instance")
    void testSingletonInstance() {
      NexusConfig instance1 = NexusConfig.getInstance();
      NexusConfig instance2 = NexusConfig.getInstance();

      assertSame(instance1, instance2); // Same reference
    }
  }

  @Nested
  @DisplayName("Edge Cases")
  class EdgeCases {

    @Test
    @DisplayName("Should handle empty .env file")
    void testEmptyEnvFile() throws IOException {
      Path emptyEnv = tempDir.resolve("empty.env");
      Files.writeString(emptyEnv, "");

      NexusConfig config = NexusConfig.getInstance();
      config.setEnvFilePath(emptyEnv.toString());

      config.init(new String[]{});

      assertNotNull(config.get("PATH")); // System env should still work
    }

    @Test
    @DisplayName("Should handle .env file with only comments")
    void testEnvFileWithOnlyComments() throws IOException {
      Path commentEnv = tempDir.resolve("comment.env");
      Files.writeString(commentEnv, "# This is a comment\n# Another comment");

      NexusConfig config = NexusConfig.getInstance();
      config.setEnvFilePath(commentEnv.toString());

      config.init(new String[]{});

      assertNotNull(config.get("PATH"));
    }

    @Test
    @DisplayName("Should handle empty CLI args array")
    void testEmptyArgsArray() {
      NexusConfig config = NexusConfig.getInstance();
      config.setEnvFilePath("nonexistent.env");

      config.init(new String[]{});

      assertNotNull(config.get("PATH"));
    }

    @Test
    @DisplayName("Should handle values with special characters")
    void testValuesWithSpecialChars() {
      NexusConfig config = NexusConfig.getInstance();
      config.setEnvFilePath("nonexistent.env");

      config.init(new String[]{
          "--SPECIAL=hello@world!#$%",
          "--URL=https://example.com?param1=value1&param2=value2"
      });

      assertEquals("hello@world!#$%", config.get("SPECIAL"));
      assertEquals("https://example.com?param1=value1&param2=value2", config.get("URL"));
    }
  }
}