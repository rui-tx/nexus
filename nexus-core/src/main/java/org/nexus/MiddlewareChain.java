package org.nexus;

import org.nexus.annotations.RequestContext;

@FunctionalInterface
public interface MiddlewareChain {
  
  void next(RequestContext ctx) throws Exception;
}