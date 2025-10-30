package org.nexus.middleware;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import org.nexus.Response;
import org.nexus.annotations.RequestContext;

public class MiddlewareChain {

  private final List<Middleware> middlewares;
  private final RequestContext ctx;
  private final Supplier<CompletableFuture<Response<?>>> finalHandler;
  private int index = 0;

  private MiddlewareChain(List<Middleware> middlewares,
      RequestContext ctx,
      Supplier<CompletableFuture<Response<?>>> finalHandler) {
    this.middlewares = new ArrayList<>(middlewares);
    this.ctx = Objects.requireNonNull(ctx, "Request context cannot be null");
    this.finalHandler = Objects.requireNonNull(finalHandler, "Final handler cannot be null");
  }

  public static Builder builder() {
    return new Builder();
  }

  public CompletableFuture<Response<?>> execute() {
    if (middlewares.isEmpty()) {
      return finalHandler.get();
    }
    return next().next();
  }

  private NextHandler next() {
    if (index >= middlewares.size()) {
      return NextHandler.of(finalHandler);
    }

    final int currentIndex = index++;
    return new NextHandler() {
      @Override
      public CompletableFuture<Response<?>> next() {
        try {
          return middlewares.get(currentIndex).handle(ctx, MiddlewareChain.this.next());
        } catch (Exception e) {
          return CompletableFuture.failedFuture(e);
        }
      }

      @Override
      public RequestContext getContext() {
        return ctx;
      }
    };
  }

  public static class Builder {

    private final List<Middleware> middlewares = new ArrayList<>();
    private Supplier<CompletableFuture<Response<?>>> finalHandler;

    public Builder addMiddleware(Middleware middleware) {
      this.middlewares.add(Objects.requireNonNull(middleware, "Middleware cannot be null"));
      return this;
    }

    public Builder addMiddlewares(List<Middleware> middlewares) {
      middlewares.forEach(this::addMiddleware);
      return this;
    }

    public Builder setFinalHandler(Supplier<CompletableFuture<Response<?>>> handler) {
      this.finalHandler = Objects.requireNonNull(handler, "Final handler cannot be null");
      return this;
    }

    public MiddlewareChain build(RequestContext ctx) {
      if (finalHandler == null) {
        throw new IllegalStateException("Final handler must be set");
      }
      return new MiddlewareChain(
          Collections.unmodifiableList(middlewares),
          ctx,
          finalHandler
      );
    }
  }
}
