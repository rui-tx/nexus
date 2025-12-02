package org.nexus.exceptions;

/**
 * Exception thrown when queue is full
 */
public class QueueFullException extends RuntimeException {

  public QueueFullException(String message) {
    super(message);
  }
}
