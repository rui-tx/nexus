package org.nexus;

import org.nexus.annotations.RequestContext;

@FunctionalInterface
public interface Middleware {
  
  void handle(RequestContext ctx, MiddlewareChain next) throws Exception;
}