package org.nexus.interfaces;

/**
 * Deserializer interface for converting bytes to objects
 */
@FunctionalInterface
public interface Deserializer<T> {

  T deserialize(String topic, byte[] data);
}
