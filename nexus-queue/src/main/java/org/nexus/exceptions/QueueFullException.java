package org.nexus.exceptions;

/**
 * Exception thrown when partition is full
 */
public class QueueFullException extends RuntimeException {

  public QueueFullException(String message) {
    super(message);
  }
}
