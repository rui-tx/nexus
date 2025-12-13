package io.github.ruitx.images.api;

public class ImageUploadRequest {

  private String imageBase64;
  private String mimeType;
  private String filename;

  public ImageUploadRequest() {
  }

  public String getImageBase64() {
    return imageBase64;
  }

  public String getMimeType() {
    return mimeType;
  }

  public String getFilename() {
    return filename;
  }
}
