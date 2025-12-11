package org.nexus.queue.persistence;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Persists committed consumer offsets per (category, groupId) as JSON files.
 *
 * <p>Files are stored under {@code QUEUE_DATA_DIR} using {@link QueueStorage}:
 * <pre>
 *   data/<category>/offsets-<groupId>.json
 * </pre>
 * with the structure:
 * <pre>{ "queues": { "0": 1234, "1": 5678 } }</pre>
 */
public final class OffsetStore {

  private static final ObjectMapper MAPPER = new ObjectMapper()
      .enable(SerializationFeature.INDENT_OUTPUT);

  public OffsetStore() {
  }

  /**
   * Persist committed offsets for a consumer group within a category.
   */
  public synchronized void persist(String category, String groupId, Map<Integer, Long> offsets) {
    if (offsets == null || offsets.isEmpty()) {
      return;
    }

    Path path = QueueStorage.offsetsPath(category, groupId);
    try {
      Path parent = path.getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }

      Map<String, Long> queues = new HashMap<>();
      for (Map.Entry<Integer, Long> entry : offsets.entrySet()) {
        queues.put(String.valueOf(entry.getKey()), entry.getValue());
      }

      OffsetsFile data = new OffsetsFile(queues);
      MAPPER.writeValue(path.toFile(), data);
    } catch (IOException e) {
      throw new RuntimeException("Failed to persist offsets to " + path, e);
    }
  }

  /**
   * Load committed offsets for a consumer group within a category.
   */
  public synchronized Map<Integer, Long> load(String category, String groupId) {
    Path path = QueueStorage.offsetsPath(category, groupId);
    if (!Files.exists(path)) {
      return Collections.emptyMap();
    }

    try {
      OffsetsFile data = MAPPER.readValue(path.toFile(), OffsetsFile.class);
      Map<Integer, Long> result = new HashMap<>();
      if (data.queues != null) {
        for (Map.Entry<String, Long> entry : data.queues.entrySet()) {
          try {
            int queueId = Integer.parseInt(entry.getKey());
            result.put(queueId, entry.getValue());
          } catch (NumberFormatException _) {
            // Skip invalid keys
          }
        }
      }
      return result;
    } catch (IOException e) {
      throw new RuntimeException("Failed to load offsets from " + path, e);
    }
  }

  /**
   * Simple DTO for JSON serialization.
   */
  private record OffsetsFile(Map<String, Long> queues) {

    @JsonCreator
    private OffsetsFile(@JsonProperty("queues") Map<String, Long> queues) {
      this.queues = queues != null ? queues : Collections.emptyMap();
    }
  }
}
