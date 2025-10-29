package org.nexus.handlers.testing;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.nexus.Response;
import org.nexus.annotations.RequestContext;
import org.nexus.enums.HttpMethod;

public final class TestRouteRegistry {

  private final List<TestRoute<?>> routes = new ArrayList<>();

  public <T> TestRouteRegistry get(String path,
      Function<RequestContext, CompletableFuture<Response<T>>> h) {
    routes.add(new TestRoute<>(HttpMethod.GET, path, h));
    return this;
  }

  public <T> TestRouteRegistry post(String path,
      Function<RequestContext, CompletableFuture<Response<T>>> h) {
    routes.add(new TestRoute<>(HttpMethod.POST, path, h));
    return this;
  }

  public List<TestRoute<?>> routes() {
    return routes;
  }

  public static final class TestRoute<T> {

    private final HttpMethod method;
    private final String pathTemplate;
    private final Function<RequestContext, CompletableFuture<Response<T>>> handler;

    public TestRoute(HttpMethod method, String pathTemplate,
        Function<RequestContext, CompletableFuture<Response<T>>> handler) {
      this.method = method;
      this.pathTemplate = pathTemplate;
      this.handler = handler;
    }

    public HttpMethod method() {
      return method;
    }

    public String pathTemplate() {
      return pathTemplate;
    }

    public Function<RequestContext, CompletableFuture<Response<T>>> handler() {
      return handler;
    }
  }
}