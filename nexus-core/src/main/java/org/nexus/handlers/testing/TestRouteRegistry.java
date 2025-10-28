package org.nexus.handlers.testing;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.nexus.Response;
import org.nexus.annotations.RequestContext;
import org.nexus.enums.HttpMethod;

public final class TestRouteRegistry {

  private final List<TestRoute> routes = new ArrayList<>();

  public TestRouteRegistry get(String path, Function<RequestContext, Response<?>> h) {
    routes.add(new TestRoute(HttpMethod.GET, path, h));
    return this;
  }

  public TestRouteRegistry post(String path, Function<RequestContext, Response<?>> h) {
    routes.add(new TestRoute(HttpMethod.POST, path, h));
    return this;
  }

  public List<TestRoute> routes() {
    return routes;
  }

  // add more (put, delete, etc.) as needed

  public static final class TestRoute {

    private final HttpMethod method;
    private final String pathTemplate;
    private final Function<RequestContext, Response<?>> handler;

    public TestRoute(HttpMethod method, String pathTemplate,
        Function<RequestContext, Response<?>> handler) {
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

    public Function<RequestContext, Response<?>> handler() {
      return handler;
    }
  }
}
