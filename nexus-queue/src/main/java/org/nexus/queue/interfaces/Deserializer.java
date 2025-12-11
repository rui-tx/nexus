package org.nexus.queue.interfaces;

/**
 * Deserializer interface for converting bytes to objects
 */
@FunctionalInterface
public interface Deserializer<T> {

  T deserialize(String category, byte[] data);
}
