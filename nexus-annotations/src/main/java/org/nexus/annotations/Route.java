package org.nexus.annotations;

import io.netty.handler.codec.http.HttpMethod;
import java.util.concurrent.CompletableFuture;
import org.nexus.Response;

public class Route<T> {

  private final HttpMethod method;
  private final String path;
  private final RouteHandler<T> handler;

  public Route(HttpMethod method, String path, RouteHandler<T> handler) {
    this.method = method;
    this.path = path;
    this.handler = handler;
  }

  public HttpMethod getMethod() {
    return method;
  }

  public String getPath() {
    return path;
  }

  public CompletableFuture<Response<T>> handle(RequestContext rc) {
    try {
      return handler.handle(rc);
    } catch (Exception e) {
      return CompletableFuture.failedFuture(e);
    }
  }
}