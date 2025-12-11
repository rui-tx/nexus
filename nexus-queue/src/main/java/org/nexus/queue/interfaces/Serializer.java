package org.nexus.queue.interfaces;

/**
 * Serializer interface for converting objects to bytes
 */
@FunctionalInterface
public interface Serializer<T> {

  byte[] serialize(String category, T data);
}
