package org.nexus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import org.nexus.config.db.DatabaseConfig;
import org.nexus.enums.DatabaseType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class NexusConfig {

  private static final Logger LOGGER = LoggerFactory.getLogger(NexusConfig.class);
  private static NexusConfig instance;
  private final Map<String, String> config = new HashMap<>();
  private final Map<String, DatabaseConfig> databaseConfigs = new HashMap<>();
  private boolean initialized = false;
  private String envFilePath = ".env"; // Default path

  private NexusConfig() {
  }

  public static synchronized NexusConfig getInstance() {
    if (instance == null) {
      instance = new NexusConfig();
    }
    return instance;
  }

  public static synchronized void closeInstance() {
    if (instance != null) {
      instance.config.clear();
      instance.databaseConfigs.clear();
      instance.initialized = false;  // Reset the flag
      instance = null;
    }
  }

  /**
   * Sets the .env file path (for testing purposes). Must be called before init().
   */
  public void setEnvFilePath(String path) {
    if (initialized) {
      throw new IllegalStateException("Cannot change env file path after initialization");
    }
    this.envFilePath = path;
  }

  public synchronized void init(String[] args) {
    if (initialized) {
      return;
    }

    // Priority: system environment variables > .env file > CLI arguments

    config.putAll(System.getenv());
    LOGGER.debug("Loaded {} system environment variables", config.size());
    loadEnvFile(envFilePath);
    parseCommandLineArgs(args);

    // load database configurations
    loadDatabaseConfigs();

    initialized = true;
    LOGGER.debug("Configuration initialized with {} parameters", config.size());
  }

  private void parseCommandLineArgs(String[] args) {
    if (args == null) {
      return;
    }

    for (String arg : args) {
      if (arg.startsWith("--")) {
        String[] parts = arg.substring(2).split("=", 2);
        if (parts.length == 2) {
          config.put(parts[0], parts[1]);
          LOGGER.debug("Set config from CLI: {}={}", parts[0], "*".repeat(parts[1].length()));
        } else {
          config.put(parts[0], "true");
          LOGGER.debug("Set flag from CLI: {}", parts[0]);
        }
      }
    }
  }

  private void loadEnvFile(String filePath) {
    Path envPath = Paths.get(filePath);
    if (!Files.exists(envPath)) {
      LOGGER.debug("No .env file found at: {}", envPath.toAbsolutePath());
      return;
    }

    Properties props = new Properties();
    try {
      props.load(Files.newBufferedReader(envPath));
      int count = 0;
      for (var entry : props.entrySet()) {
        if (entry.getKey() != null && entry.getValue() != null) {
          String key = entry.getKey().toString();
          String value = entry.getValue().toString();
          config.put(key, value);
          count++;
          LOGGER.trace("Loaded from .env: {}={}", key,
              key.toLowerCase().contains("password") ? "*****" : value);
        }
      }
      LOGGER.debug("Loaded {} parameters from {}", count, envPath.getFileName());
    } catch (IOException e) {
      LOGGER.warn("Failed to load .env file: {}", e.getMessage());
    }
  }

  private void loadDatabaseConfigs() {
    int dbNum = 1;
    while (true) {
      String prefix = "DB" + dbNum + "_";
      String name = get(prefix + "NAME");

      if (name == null && (get(prefix + "TYPE") != null || get(prefix + "URL") != null)) {
        name = "db" + dbNum;
        LOGGER.debug("Inferred database name '{}' for DB{}", name, dbNum);
      } else if (name == null) {
        LOGGER.debug("No more database configurations found after DB{}", dbNum - 1);
        break;
      }

      DatabaseType type;
      try {
        type = DatabaseType.valueOf(get(prefix + "TYPE", "POSTGRES").toUpperCase());
        LOGGER.debug("Database '{}' type: {}", name, type);
      } catch (IllegalArgumentException e) {
        LOGGER.warn("Invalid database type for {}, defaulting to POSTGRES", prefix);
        type = DatabaseType.POSTGRES;
      }

      DatabaseConfig dbConfig = DatabaseConfig.defaultConfig(name, type)
          .withUrl(get(prefix + "URL", ""))
          .withUsername(get(prefix + "USER", ""))
          .withPassword(get(prefix + "PASSWORD", ""))
          .withPoolSize(getInt(prefix + "POOL_SIZE", 10))
          .withAutoCommit(getBoolean(prefix + "AUTO_COMMIT", true))
          .withConnectionTimeout(getInt(prefix + "CONNECTION_TIMEOUT", 30000))
          .withMigrationsPath(get(prefix + "MIGRATIONS_PATH", null));

      databaseConfigs.put(name, dbConfig);
      LOGGER.debug("Configured database '{}' (type: {})", name, type);
      dbNum++;
    }
  }

  // Database configuration access
  public DatabaseConfig getDatabaseConfig(String name) {
    DatabaseConfig config = databaseConfigs.get(name);
    if (config == null) {
      throw new IllegalStateException("No database configuration found for: " + name);
    }
    return config;
  }

  public Map<String, DatabaseConfig> getAllDatabaseConfigs() {
    return new HashMap<>(databaseConfigs);
  }

  public String get(String key) {
    return config.get(key);
  }

  public String get(String key, String defaultValue) {
    return config.getOrDefault(key, defaultValue);
  }

  public int getInt(String key, int defaultValue) {
    try {
      String value = get(key);
      return value != null ? Integer.parseInt(value) : defaultValue;
    } catch (NumberFormatException e) {
      LOGGER.warn("Invalid integer value for '{}', using default: {}", key, defaultValue);
      return defaultValue;
    }
  }

  public boolean getBoolean(String key, boolean defaultValue) {
    String value = get(key);
    if (value == null) {
      return defaultValue;
    }
    return Boolean.parseBoolean(value) || "1".equals(value) || "true".equalsIgnoreCase(value);
  }

  public long getLong(String key, long defaultValue) {
    try {
      String value = get(key);
      return value != null ? Long.parseLong(value) : defaultValue;
    } catch (NumberFormatException e) {
      LOGGER.warn("Invalid long value for '{}', using default: {}", key, defaultValue);
      return defaultValue;
    }
  }
}