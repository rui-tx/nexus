package org.nexus;

import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import org.nexus.interfaces.Middleware;
import org.nexus.interfaces.MiddlewareChain;

public class DefaultMiddlewareChain implements MiddlewareChain {

  private final Iterator<Middleware> iterator;
  private final MiddlewareChain nextChain;

  private DefaultMiddlewareChain(Iterator<Middleware> iterator, Runnable finalAction) {
    this.iterator = iterator;
    this.nextChain = new MiddlewareChain() {
      @Override
      public void next(RequestContext ctx) {
        finalAction.run();
      }
    };
  }

  public static MiddlewareChain create(List<Middleware> middlewares, Runnable finalAction) {
    Objects.requireNonNull(middlewares, "middlewares cannot be null");
    Objects.requireNonNull(finalAction, "finalAction cannot be null");

    return new DefaultMiddlewareChain(middlewares.iterator(), finalAction);
  }

  @Override
  public void next(RequestContext ctx) throws Exception {
    if (ctx == null) {
      throw new NullPointerException("RequestContext cannot be null");
    }

    if (iterator.hasNext()) {
      Middleware middleware = iterator.next();
      middleware.handle(ctx, this);
    } else {
      nextChain.next(ctx);
    }
  }
}