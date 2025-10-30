package org.nexus;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.util.CharsetUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import org.nexus.ProblemDetails.Single;
import org.nexus.annotations.RequestContext;
import org.nexus.annotations.Route;
import org.nexus.enums.ProblemDetailsTypes;
import org.nexus.exceptions.ProblemDetailsException;
import org.nexus.middleware.Middleware;
import org.nexus.middleware.MiddlewareChain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ChannelHandler.Sharable
public class DefaultHttpServerHandler extends SimpleChannelInboundHandler<HttpObject> {

  private static final Logger LOGGER = LoggerFactory.getLogger(DefaultHttpServerHandler.class);
  private final List<Middleware> middlewares;

  private DefaultHttpServerHandler(List<Middleware> middlewares) {
    this.middlewares = new ArrayList<>(middlewares);
  }

  public static Builder builder() {
    return new Builder();
  }

  @Override
  protected void channelRead0(ChannelHandlerContext ctx, HttpObject msg) {
    if (msg instanceof FullHttpRequest request) {

      boolean keepAlive = HttpUtil.isKeepAlive(request);
      String method = request.method().name();
      String rawUri = request.uri();
      String path = rawUri.split("\\?")[0];           // strip query string

      QueryStringDecoder qsd = new QueryStringDecoder(request.uri(), CharsetUtil.UTF_8);
      Map<String, List<String>> queryParams = qsd.parameters();

      // Find the matching route
      Route<?> route = nexus.generated.GeneratedRoutes.getRoute(method, path);
      Map<String, String> pathParams = Map.of();

      if (route == null) {
        for (Route<?> candidate : nexus.generated.GeneratedRoutes.getRoutes(method)) {
          PathMatcher.Result r = PathMatcher.match(candidate.getPath(), path);
          if (r.matches()) {
            route = candidate;
            pathParams = r.params();
            break;
          }
        }

        // If still no route found, return 404
        if (route == null) {
          sendResponse(ctx, new Response<>(404, "Not Found"), keepAlive);
          return;
        }
      }

      // Create a request context and process it
      RequestContext rc = new RequestContext(ctx, request, pathParams, queryParams);
      handleRoute(rc, route, keepAlive);
    }
  }

  private void handleRoute(RequestContext context, Route<?> route, boolean keepAlive) {
    // Create and execute the middleware chain
    MiddlewareChain.builder()
        .addMiddlewares(middlewares)
        // add route logic as last 'middleware'
        .setFinalHandler(() -> route.handle(context).thenApply(Function.identity()))
        .build(context)
        .execute()
        .whenComplete((response, error) -> {
          try {
            if (error != null) {
              handleError(context.ctx(), error, keepAlive);
              return;
            }
            sendResponse(context.ctx(), response, keepAlive);
          } catch (Exception e) {
            handleError(context.ctx(), e, keepAlive);
          }
        });
  }

  private void sendResponse(ChannelHandlerContext ctx, Response<?> response, boolean keepAlive) {
    FullHttpResponse httpResponse = response.toHttpResponse();

    if (keepAlive) {
      httpResponse.headers()
          .set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE)
          .set(HttpHeaderNames.KEEP_ALIVE, "timeout=5");
    }

    HttpUtil.setContentLength(httpResponse, httpResponse.content().readableBytes());

    ChannelFuture future = ctx.writeAndFlush(httpResponse);
    if (!keepAlive) {
      future.addListener(ChannelFutureListener.CLOSE);
    }
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
    ProblemDetails error;
    if (cause instanceof ProblemDetailsException pde) {
      error = pde.getProblemDetails();
    } else {
      LOGGER.error("Unexpected exception in channel", cause);
      error = new Single(
          ProblemDetailsTypes.SERVER_ERROR,
          "Internal Server Error",
          500,
          "An unexpected error occurred",
          "unknown",
          Map.of(
              "exception",
              Objects.toString(cause.getMessage(), cause.getClass().getSimpleName()))
      );
    }

    Response<ProblemDetails> response = new Response<>(error.getStatus(), error);
    ctx.writeAndFlush(response.toHttpResponse())
        .addListener(ChannelFutureListener.CLOSE);
  }

  private void handleError(ChannelHandlerContext ctx, Throwable error, boolean keepAlive) {
    Response<?> errorResponse;
    if (error instanceof ProblemDetailsException pde) {
      errorResponse = new Response<>(pde.getProblemDetails().getStatus(), pde.getProblemDetails());
    } else {
      LOGGER.error("Unexpected error", error);
      errorResponse = new Response<>(500, "Internal Server Error");
    }

    FullHttpResponse httpResponse = errorResponse.toHttpResponse();
    if (keepAlive) {
      httpResponse.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
    }
    HttpUtil.setContentLength(httpResponse, httpResponse.content().readableBytes());

    ChannelFuture future = ctx.writeAndFlush(httpResponse);
    if (!keepAlive) {
      future.addListener(ChannelFutureListener.CLOSE);
    }
  }

  public static class Builder {

    private final List<Middleware> middlewares = new ArrayList<>();

    public Builder addMiddleware(Middleware middleware) {
      this.middlewares.add(Objects.requireNonNull(middleware, "Middleware cannot be null"));
      return this;
    }

    public DefaultHttpServerHandler build() {
      return new DefaultHttpServerHandler(middlewares);
    }
  }
}