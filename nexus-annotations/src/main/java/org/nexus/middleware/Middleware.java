package org.nexus.middleware;

import java.util.concurrent.CompletableFuture;
import org.nexus.Response;
import org.nexus.annotations.RequestContext;

@FunctionalInterface
public interface Middleware {

  /**
   *
   * Process the HTTP request and/or response.
   *
   * @param ctx  The request context
   * @param next The next handler in the chain
   * @return A CompletableFuture that completes with the response
   */
  CompletableFuture<Response<?>> handle(RequestContext ctx, NextHandler next);
}
