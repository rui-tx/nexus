package org.nexus.annotations;

import java.util.concurrent.CompletableFuture;
import org.nexus.Response;

@FunctionalInterface
public interface RouteHandler<T> {

  CompletableFuture<Response<T>> handle(RequestContext rc);
}
