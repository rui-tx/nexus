package io.github.ruitx.images.messaging;

public class ImageProcessRequested {

  private String imageId;
  private String originalKey;
  private String mimeType;

  public ImageProcessRequested() {
  }

  public ImageProcessRequested(String imageId, String originalKey, String mimeType) {
    this.imageId = imageId;
    this.originalKey = originalKey;
    this.mimeType = mimeType;
  }

  public String getImageId() {
    return imageId;
  }

  public String getOriginalKey() {
    return originalKey;
  }

  public String getMimeType() {
    return mimeType;
  }
}
