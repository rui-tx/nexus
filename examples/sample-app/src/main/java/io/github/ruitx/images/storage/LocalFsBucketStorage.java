package io.github.ruitx.images.storage;

import jakarta.inject.Singleton;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class LocalFsBucketStorage implements BucketStorage {

  private static final Logger LOGGER = LoggerFactory.getLogger(LocalFsBucketStorage.class);

  private final Path baseDir;

  public LocalFsBucketStorage() {
    this.baseDir = Paths.get("data", "bucket");
  }

  @Override
  public String save(String key, byte[] bytes, String contentType) {
    try {
      Path target = baseDir.resolve(key).normalize();
      Path parent = target.getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
      Files.write(target, bytes);
      return key;
    } catch (IOException e) {
      LOGGER.error("Failed to save object to bucket", e);
      throw new RuntimeException("Failed to save object to bucket", e);
    }
  }

  @Override
  public byte[] load(String key) {
    try {
      Path target = baseDir.resolve(key).normalize();
      return Files.readAllBytes(target);
    } catch (IOException e) {
      LOGGER.error("Failed to load object from bucket", e);
      throw new RuntimeException("Failed to load object from bucket", e);
    }
  }
}
