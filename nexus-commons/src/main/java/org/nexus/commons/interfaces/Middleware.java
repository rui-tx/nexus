package org.nexus.commons.interfaces;

import org.nexus.commons.RequestContext;

@FunctionalInterface
public interface Middleware {

  void handle(RequestContext ctx, MiddlewareChain next) throws Exception;
}