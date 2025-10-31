package org.nexus.interfaces;

import org.nexus.RequestContext;

@FunctionalInterface
public interface Middleware {

  void handle(RequestContext ctx, MiddlewareChain next) throws Exception;
}