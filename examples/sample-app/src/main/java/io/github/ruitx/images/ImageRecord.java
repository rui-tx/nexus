package io.github.ruitx.images;

import java.time.Instant;

public class ImageRecord {

  private final String id;
  private final ImageStatus status;
  private final String originalKey;
  private final String originalCleanKey;
  private final String smallKey;
  private final String mediumKey;
  private final String largeKey;
  private final String mimeType;
  private final Integer originalWidth;
  private final Integer originalHeight;
  private final Instant createdAt;
  private final Instant updatedAt;
  private final String errorMessage;

  public ImageRecord(
      String id,
      ImageStatus status,
      String originalKey,
      String originalCleanKey,
      String smallKey,
      String mediumKey,
      String largeKey,
      String mimeType,
      Integer originalWidth,
      Integer originalHeight,
      Instant createdAt,
      Instant updatedAt,
      String errorMessage
  ) {
    this.id = id;
    this.status = status;
    this.originalKey = originalKey;
    this.originalCleanKey = originalCleanKey;
    this.smallKey = smallKey;
    this.mediumKey = mediumKey;
    this.largeKey = largeKey;
    this.mimeType = mimeType;
    this.originalWidth = originalWidth;
    this.originalHeight = originalHeight;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
    this.errorMessage = errorMessage;
  }

  public String getId() {
    return id;
  }

  public ImageStatus getStatus() {
    return status;
  }

  public String getOriginalKey() {
    return originalKey;
  }

  public String getOriginalCleanKey() {
    return originalCleanKey;
  }

  public String getSmallKey() {
    return smallKey;
  }

  public String getMediumKey() {
    return mediumKey;
  }

  public String getLargeKey() {
    return largeKey;
  }

  public String getMimeType() {
    return mimeType;
  }

  public Integer getOriginalWidth() {
    return originalWidth;
  }

  public Integer getOriginalHeight() {
    return originalHeight;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public ImageRecord withStatus(ImageStatus newStatus) {
    return new ImageRecord(
        id,
        newStatus,
        originalKey,
        originalCleanKey,
        smallKey,
        mediumKey,
        largeKey,
        mimeType,
        originalWidth,
        originalHeight,
        createdAt,
        updatedAt,
        errorMessage
    );
  }

  public ImageRecord withKeys(
      String originalCleanKey,
      String smallKey,
      String mediumKey,
      String largeKey
  ) {
    return new ImageRecord(
        id,
        status,
        originalKey,
        originalCleanKey,
        smallKey,
        mediumKey,
        largeKey,
        mimeType,
        originalWidth,
        originalHeight,
        createdAt,
        updatedAt,
        errorMessage
    );
  }

  public ImageRecord withDimensions(Integer originalWidth, Integer originalHeight) {
    return new ImageRecord(
        id,
        status,
        originalKey,
        originalCleanKey,
        smallKey,
        mediumKey,
        largeKey,
        mimeType,
        originalWidth,
        originalHeight,
        createdAt,
        updatedAt,
        errorMessage
    );
  }

  public ImageRecord withTimestamps(Instant createdAt, Instant updatedAt) {
    return new ImageRecord(
        id,
        status,
        originalKey,
        originalCleanKey,
        smallKey,
        mediumKey,
        largeKey,
        mimeType,
        originalWidth,
        originalHeight,
        createdAt,
        updatedAt,
        errorMessage
    );
  }

  public ImageRecord withError(ImageStatus status, String errorMessage) {
    return new ImageRecord(
        id,
        status,
        originalKey,
        originalCleanKey,
        smallKey,
        mediumKey,
        largeKey,
        mimeType,
        originalWidth,
        originalHeight,
        createdAt,
        updatedAt,
        errorMessage
    );
  }
}
