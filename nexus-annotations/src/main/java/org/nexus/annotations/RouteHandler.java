package org.nexus.annotations;

import org.nexus.Response;

@FunctionalInterface
public interface RouteHandler<T> {

  Response<T> handle(RequestContext rc);
}
