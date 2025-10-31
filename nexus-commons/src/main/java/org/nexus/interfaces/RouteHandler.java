package org.nexus.interfaces;

import java.util.concurrent.CompletableFuture;
import org.nexus.RequestContext;
import org.nexus.Response;

@FunctionalInterface
public interface RouteHandler<T> {

  CompletableFuture<Response<T>> handle(RequestContext rc);
}
