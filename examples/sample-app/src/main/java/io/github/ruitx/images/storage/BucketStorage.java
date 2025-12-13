package io.github.ruitx.images.storage;

public interface BucketStorage {

  String save(String key, byte[] bytes, String contentType);

  byte[] load(String key);
}
