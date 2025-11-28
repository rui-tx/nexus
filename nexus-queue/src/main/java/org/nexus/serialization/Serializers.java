package org.nexus.serialization;

import java.nio.charset.StandardCharsets;
import org.nexus.interfaces.Serializer;

/**
 * Common serializers
 */
public final class Serializers {

  private Serializers() {
  }

  public static Serializer<byte[]> byteArray() {
    return (_, data) -> data;
  }

  public static Serializer<String> string() {
    return (_, data) -> data.getBytes(StandardCharsets.UTF_8);
  }
}
