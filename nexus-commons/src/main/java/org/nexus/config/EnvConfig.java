package org.nexus.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import org.nexus.enums.DatabaseType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for loading configuration from environment variables and .env files.
 */
public final class EnvConfig {

  private static final Logger LOGGER = LoggerFactory.getLogger(EnvConfig.class);
  private static final Map<String, String> envVars = new HashMap<>();
  private static boolean loaded = false;

  private EnvConfig() {
  }

  /**
   * Load environment variables, with .env file taking precedence over system env vars.
   */
  public static synchronized void load() {
    if (loaded) {
      return;
    }

    //load system environment variables
    envVars.putAll(System.getenv());

    // then from .env file if it exists (will override system env vars)
    loadEnvFile();

    // Set system properties from environment variables
    envVars.forEach((key, value) -> {
      if (System.getProperty(key) == null) {
        System.setProperty(key, value);
      }
    });

    loaded = true;
  }

  /**
   * Get an environment variable value.
   *
   * @param key The environment variable name
   * @return The value, or null if not found
   */
  public static String get(String key) {
    if (!loaded) {
      load();
    }
    return envVars.get(key);
  }

  /**
   * Get an environment variable value with a default.
   *
   * @param key          The environment variable name
   * @param defaultValue The default value to return if not found
   * @return The value, or defaultValue if not found
   */
  public static String get(String key, String defaultValue) {
    String value = get(key);
    return value != null ? value : defaultValue;
  }

  /**
   * Get an environment variable as an integer.
   *
   * @param key          The environment variable name
   * @param defaultValue The default value to return if not found or invalid
   * @return The value as an integer, or defaultValue if not found or invalid
   */
  public static int getInt(String key, int defaultValue) {
    try {
      String value = get(key);
      return value != null ? Integer.parseInt(value) : defaultValue;
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }

  /**
   * Get an environment variable as a boolean.
   *
   * @param key          The environment variable name
   * @param defaultValue The default value to return if not found
   * @return The value as a boolean, or defaultValue if not found
   */
  public static boolean getBoolean(String key, boolean defaultValue) {
    String value = get(key);
    if (value == null) {
      return defaultValue;
    }
    return Boolean.parseBoolean(value) || "1".equals(value) || "true".equalsIgnoreCase(value);
  }

  /**
   * Load environment variables from a .env file.
   *
   * @param filePath Path to the .env file
   */
  public static void loadEnvFile(String filePath) {
    Path envPath = Paths.get(filePath);
    if (!Files.exists(envPath)) {
      LOGGER.warn("Environment file '{}' does not exist", envPath);
      return;
    }

    Properties props = new Properties();
    try {
      props.load(Files.newBufferedReader(envPath));
      // Convert properties to envVars map
      props.forEach((key, value) -> {
        if (key != null && value != null) {
          envVars.put(key.toString(), value.toString());
        }
      });
    } catch (IOException e) {
      System.err.println("Warning: Failed to load .env file: " + e.getMessage());
    }
  }

  /**
   * Load environment variables from the default .env file in the current directory.
   */
  public static void loadEnvFile() {
    loadEnvFile(".env");
  }

  /**
   * Load database configurations from environment variables.
   *
   * @return Map of database configurations by name
   */
  public static Map<String, DatabaseConfig> loadDatabaseConfigs() {
    Map<String, DatabaseConfig> configs = new HashMap<>();
    int dbNum = 1;

    while (true) {
      String prefix = "DB" + dbNum + "_";
      String name = get(prefix + "NAME");

      // If no name is specified but we have a type or URL, use a default name
      if (name == null && (get(prefix + "TYPE") != null || get(prefix + "URL") != null)) {
        name = "db" + dbNum;
      } else if (name == null) {
        // No more database configurations
        break;
      }

      // Get database type (default to POSTGRES)
      DatabaseType type;
      try {
        type = DatabaseType.valueOf(get(prefix + "TYPE", "POSTGRES").toUpperCase());
      } catch (IllegalArgumentException e) {
        System.err.println("Invalid database type for " + prefix + ", defaulting to POSTGRES");
        type = DatabaseType.POSTGRES;
      }

      // Create configuration with defaults
      DatabaseConfig config = DatabaseConfig.defaultConfig(name, type)
          .withUrl(get(prefix + "URL", ""))
          .withUsername(get(prefix + "USER", ""))
          .withPassword(get(prefix + "PASSWORD", ""))
          .withPoolSize(getInt(prefix + "POOL_SIZE", 10))
          .withAutoCommit(getBoolean(prefix + "AUTO_COMMIT", true))
          .withConnectionTimeout(getInt(prefix + "CONNECTION_TIMEOUT", 30000));

      configs.put(name, config);
      dbNum++;
    }

    return configs;
  }
}
