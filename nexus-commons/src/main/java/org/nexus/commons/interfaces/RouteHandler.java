package org.nexus.commons.interfaces;

import java.util.concurrent.CompletableFuture;
import org.nexus.commons.RequestContext;
import org.nexus.commons.Response;

@FunctionalInterface
public interface RouteHandler<T> {

  CompletableFuture<Response<T>> handle(RequestContext rc);
}
