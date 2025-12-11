package org.nexus.commons.interfaces;

import org.nexus.commons.RequestContext;

@FunctionalInterface
public interface MiddlewareChain {

  void next(RequestContext ctx) throws Exception;
}