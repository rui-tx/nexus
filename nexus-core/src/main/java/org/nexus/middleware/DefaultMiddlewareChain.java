package org.nexus.middleware;

import java.util.List;
import java.util.Objects;
import org.nexus.RequestContext;
import org.nexus.interfaces.Middleware;
import org.nexus.interfaces.MiddlewareChain;

public class DefaultMiddlewareChain implements MiddlewareChain {

  private final List<Middleware> middlewares;
  private final Runnable finalAction;
  private int index = 0;

  private DefaultMiddlewareChain(List<Middleware> middlewares, Runnable finalAction) {
    this.middlewares = middlewares;
    this.finalAction = finalAction;
  }

  public static MiddlewareChain create(List<Middleware> middlewares, Runnable finalAction) {
    Objects.requireNonNull(middlewares, "middlewares cannot be null");
    Objects.requireNonNull(finalAction, "finalAction cannot be null");
    return new DefaultMiddlewareChain(middlewares, finalAction);
  }

  @Override
  public void next(RequestContext ctx) throws Exception {
    if (ctx == null) {
      throw new NullPointerException("RequestContext cannot be null");
    }

    if (index < middlewares.size()) {
      Middleware middleware = middlewares.get(index);
      index++;
      middleware.handle(ctx, this);
    } else {
      executeFinalAction();
    }
  }

  private void executeFinalAction() {
    finalAction.run();
  }
}