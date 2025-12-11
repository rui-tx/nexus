package org.nexus.queue.interfaces;

import java.util.concurrent.CompletableFuture;
import org.nexus.queue.domain.Message;

/**
 * Handler for processing messages
 */
@FunctionalInterface
public interface MessageHandler<T> {

  /**
   * Process a message. Return a CompletableFuture that completes when processing is done. If the
   * future completes exceptionally, the message will be retried or sent to DLQ (not implemented
   * yet).
   */
  CompletableFuture<Void> handle(Message<T> message);
}
