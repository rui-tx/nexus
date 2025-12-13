package io.github.ruitx.images;

import static io.github.ruitx.images.beans.DatabaseFactory.DEFAULT_DB;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import java.sql.Timestamp;
import java.time.Instant;
import org.nexus.commons.exceptions.DatabaseException;
import org.nexus.database.NexusDatabase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ImageRepository {

  private static final Logger LOGGER = LoggerFactory.getLogger(ImageRepository.class);

  private final NexusDatabase db;

  @Inject
  public ImageRepository(@Named(DEFAULT_DB) NexusDatabase db) {
    this.db = db;
  }

  public ImageRecord insertNew(String id, String originalKey, String mimeType) {
    try {
      db.insert(
          "INSERT INTO images (id, status, original_key, mime_type) VALUES (?, ?, ?, ?)",
          rs -> id,
          id,
          ImageStatus.PENDING.name(),
          originalKey,
          mimeType
      );
      return new ImageRecord(
          id,
          ImageStatus.PENDING,
          originalKey,
          null,
          null,
          null,
          null,
          mimeType,
          null,
          null,
          null,
          null,
          null
      );
    } catch (DatabaseException e) {
      LOGGER.error("Failed to insert image record", e);
      throw e;
    }
  }

  public void markProcessing(String id) {
    db.update(
        "UPDATE images SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
        ImageStatus.PROCESSING.name(),
        id
    );
  }

  public void updateProcessed(
      String id,
      String originalCleanKey,
      String smallKey,
      String mediumKey,
      String largeKey,
      int width,
      int height
  ) {
    db.update(
        "UPDATE images SET status = ?, original_clean_key = ?, small_key = ?, medium_key = ?, "
            + "large_key = ?, original_width = ?, original_height = ?, error_message = NULL, "
            + "updated_at = CURRENT_TIMESTAMP WHERE id = ?",
        ImageStatus.READY.name(),
        originalCleanKey,
        smallKey,
        mediumKey,
        largeKey,
        width,
        height,
        id
    );
  }

  public void markFailed(String id, String errorMessage) {
    db.update(
        "UPDATE images SET status = ?, error_message = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
        ImageStatus.FAILED.name(),
        errorMessage,
        id
    );
  }

  public ImageRecord findById(String id) {
    try {
      return db.queryOne(
              "SELECT id, status, original_key, original_clean_key, small_key, medium_key, large_key, "
                  + "mime_type, original_width, original_height, created_at, updated_at, error_message "
                  + "FROM images WHERE id = ?",
              rs -> {
                String status = rs.getString("status");
                String originalKey = rs.getString("original_key");
                String originalCleanKey = rs.getString("original_clean_key");
                String smallKey = rs.getString("small_key");
                String mediumKey = rs.getString("medium_key");
                String largeKey = rs.getString("large_key");
                String mimeType = rs.getString("mime_type");
                Integer width = (Integer) rs.getObject("original_width");
                Integer height = (Integer) rs.getObject("original_height");
                Timestamp createdTs = rs.getTimestamp("created_at");
                Timestamp updatedTs = rs.getTimestamp("updated_at");
                Instant createdAt = createdTs != null ? createdTs.toInstant() : null;
                Instant updatedAt = updatedTs != null ? updatedTs.toInstant() : null;
                String errorMessage = rs.getString("error_message");

                return new ImageRecord(
                    id,
                    ImageStatus.valueOf(status),
                    originalKey,
                    originalCleanKey,
                    smallKey,
                    mediumKey,
                    largeKey,
                    mimeType,
                    width,
                    height,
                    createdAt,
                    updatedAt,
                    errorMessage
                );
              },
              id
          )
          .orElse(null);
    } catch (DatabaseException e) {
      LOGGER.error("Failed to fetch image record", e);
      return null;
    }
  }
}
