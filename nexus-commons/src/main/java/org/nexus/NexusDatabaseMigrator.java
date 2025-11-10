package org.nexus;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.nexus.config.DatabaseConfig;
import org.nexus.dbconnector.DatabaseConnectorFactory;
import org.nexus.exceptions.DatabaseException;
import org.nexus.interfaces.DatabaseConnector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NexusDatabaseMigrator {

  private static final Logger LOGGER = LoggerFactory.getLogger(NexusDatabaseMigrator.class);
  private static final String MIGRATION_TABLE = "migrations";
  private static final String CREATE_MIGRATION_TABLE_SQL = """
      CREATE TABLE IF NOT EXISTS %s (
        name VARCHAR(255) PRIMARY KEY,
        applied_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
      )
      """.formatted(MIGRATION_TABLE);
  private static final NexusConfig config = NexusConfig.getInstance();

  public NexusDatabaseMigrator() {
  }

  /**
   * Migrate all configured databases or a specific one.
   *
   * @param specificDbName Optional specific DB name to migrate; null for all
   */
  public void migrateAll(String specificDbName) {
    Map<String, DatabaseConfig> dbConfigs = config.getAllDatabaseConfigs();
    if (specificDbName != null) {
      DatabaseConfig dbConfig = dbConfigs.get(specificDbName);
      if (dbConfig == null) {
        throw new IllegalArgumentException(
            "No database configuration found for: " + specificDbName);
      }
      migrateSingle(dbConfig);
    } else {
      for (Map.Entry<String, DatabaseConfig> entry : dbConfigs.entrySet()) {
        migrateSingle(entry.getValue());
      }
    }
  }

  private void migrateSingle(DatabaseConfig dbConfig) {
    String dbName = dbConfig.name();
    LOGGER.info("Starting migrations for database: {}", dbName);

    String migrationsPathStr = dbConfig.migrationsPath();
    if (migrationsPathStr == null) {
      migrationsPathStr = config.get("MIGRATIONS_PATH", null);
    }
    if (migrationsPathStr == null) {
      LOGGER.warn("No migrations path configured for database {}, skipping", dbName);
      return;
    }

    Path migrationsPath = Paths.get(migrationsPathStr);
    if (!Files.exists(migrationsPath) || !Files.isDirectory(migrationsPath)) {
      throw new IllegalStateException("Migrations directory not found for DB " + dbName + ": "
          + migrationsPath.toAbsolutePath());
    }

    try (DatabaseConnector connector = DatabaseConnectorFactory.create(dbConfig)) {
      NexusDatabase db = new NexusDatabase(connector);
      ensureMigrationTableExists(db);
      List<Path> migrationFiles = loadMigrationFiles(migrationsPath);
      for (Path file : migrationFiles) {
        String migrationName = file.getFileName().toString();
        if (!isMigrationApplied(db, migrationName)) {
          applyMigration(db, file, migrationName);
        }
      }
      LOGGER.info("Migrations completed for database: {}", dbName);
    } catch (Exception e) {
      throw new DatabaseException("Failed to migrate database " + dbName, e);
    }
  }

  /**
   * Check if the migration table exists, create if not.
   */
  private void ensureMigrationTableExists(NexusDatabase db) {
    db.withConnection(conn -> {
      try (var stmt = conn.createStatement()) {
        stmt.executeQuery("SELECT COUNT(*) FROM " + MIGRATION_TABLE); // Test query
        return null;
      } catch (SQLException e) {
        // Table doesn't exist, create it
        db.update(CREATE_MIGRATION_TABLE_SQL);
        LOGGER.info("Created migration table: {}", MIGRATION_TABLE);
        return null;
      }
    });
  }

  /**
   * Load and sort SQL migration files from the directory.
   */
  private List<Path> loadMigrationFiles(Path migrationsPath) throws IOException {
    List<Path> files;
    try (Stream<Path> stream = Files.walk(migrationsPath, 1)) {
      files = stream
          .filter(p -> p.toString().endsWith(".sql"))
          .sorted() // Sort alphabetically (assume numbered prefixes)
          .collect(Collectors.toList());
    }

    if (files.isEmpty()) {
      LOGGER.warn("No migration files found in: {}", migrationsPath.toAbsolutePath());
    }

    return files;
  }

  /**
   * Check if a migration has been applied.
   */
  private boolean isMigrationApplied(NexusDatabase db, String name) {
    return db.queryOne("SELECT 1 FROM " + MIGRATION_TABLE + " WHERE name = ?",
        rs -> true,
        name).isPresent();
  }

  /**
   * Apply a single migration file in a transaction.
   */
  private void applyMigration(NexusDatabase db, Path file, String name) throws IOException {
    List<String> statements = readSqlStatements(file);
    db.withConnection(conn -> {
      db.beginTransaction();
      try {
        for (String sql : statements) {
          db.update(sql);
        }
        db.update("INSERT INTO " + MIGRATION_TABLE + " (name, applied_at) VALUES (?, ?)",
            name, new Timestamp(System.currentTimeMillis()));
        db.commitTransaction();
        LOGGER.info("Applied migration: {}", name);
        return null;
      } catch (Exception e) {
        db.rollbackTransaction();
        throw new DatabaseException("Failed to apply migration: " + name, e);
      }
    });
  }

  /**
   * Read SQL file and split into statements (simple: split on ';', ignore comments/empty).
   */
  private List<String> readSqlStatements(Path file) throws IOException {
    List<String> statements = new ArrayList<>();
    StringBuilder currentStmt = new StringBuilder();
    try (BufferedReader reader = Files.newBufferedReader(file)) {
      String line;
      while ((line = reader.readLine()) != null) {
        line = line.trim();
        if (line.isEmpty() || line.startsWith("--") || line.startsWith("#")) {
          continue; // Skip comments/empty
        }
        currentStmt.append(line).append(" ");
        if (line.endsWith(";")) {
          String sql = currentStmt.toString().trim();
          if (!sql.isEmpty()) {
            statements.add(sql.substring(0, sql.length() - 1)); // Remove trailing ';'
          }
          currentStmt.setLength(0);
        }
      }
    }
    // Add any remaining (if no trailing ';')
    String remaining = currentStmt.toString().trim();
    if (!remaining.isEmpty()) {
      statements.add(remaining);
    }
    return statements;
  }
}