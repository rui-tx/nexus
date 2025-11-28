package org.nexus.serialization;

import java.nio.charset.StandardCharsets;
import org.nexus.interfaces.Deserializer;

/**
 * Common deserializers
 */
public final class Deserializers {

  private Deserializers() {
  }

  public static Deserializer<byte[]> byteArray() {
    return (_, data) -> data;
  }

  public static Deserializer<String> string() {
    return (_, data) -> new String(data, StandardCharsets.UTF_8);
  }
}
