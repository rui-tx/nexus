package org.nexus.persistence;

import java.nio.file.Path;
import java.nio.file.Paths;
import org.nexus.NexusConfig;

/**
 * Helper for resolving queue persistence paths based on NexusConfig.
 *
 * <p>Uses the {@code QUEUE_DATA_DIR} configuration key, falling back to
 * {@code "data"} when not configured.
 */
public final class QueueStorage {

  private static final String QUEUE_DATA_DIR_KEY = "QUEUE_DATA_DIR";
  private static final String DEFAULT_QUEUE_DATA_DIR = "data";

  private QueueStorage() {
  }

  /**
   * Resolve the base directory for queue persistence.
   */
  public static Path baseDir() {
    NexusConfig config = NexusConfig.getInstance();
    String dir = config.get(QUEUE_DATA_DIR_KEY, DEFAULT_QUEUE_DATA_DIR);
    return Paths.get(dir);
  }

  /**
   * Directory for a specific category.
   */
  public static Path categoryDir(String category) {
    return baseDir().resolve(category);
  }

  /**
   * Log file path for a specific (category, queueId) pair.
   */
  public static Path logPath(String category, int queueId) {
    return categoryDir(category).resolve(queueId + ".log");
  }

  /**
   * Offsets file path for a specific (category, consumer group).
   */
  public static Path offsetsPath(String category, String groupId) {
    return categoryDir(category).resolve("offsets-" + groupId + ".json");
  }
}
