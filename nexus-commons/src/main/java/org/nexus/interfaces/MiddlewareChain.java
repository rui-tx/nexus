package org.nexus.interfaces;

import org.nexus.RequestContext;

@FunctionalInterface
public interface MiddlewareChain {

  void next(RequestContext ctx) throws Exception;
}