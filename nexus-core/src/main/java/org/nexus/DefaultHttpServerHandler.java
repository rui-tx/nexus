package org.nexus;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.util.AttributeKey;
import io.netty.util.CharsetUtil;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import nexus.generated.GeneratedRoutes;
import org.nexus.enums.ProblemDetailsTypes;
import org.nexus.exceptions.ProblemDetailsException;
import org.nexus.interfaces.Middleware;
import org.nexus.interfaces.MiddlewareChain;
import org.nexus.interfaces.ProblemDetails;
import org.nexus.interfaces.ProblemDetails.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class DefaultHttpServerHandler extends SimpleChannelInboundHandler<HttpObject> {

  private static final Logger LOGGER = LoggerFactory.getLogger(DefaultHttpServerHandler.class);
  private static final AttributeKey<RequestContext> REQUEST_CONTEXT_KEY =
      AttributeKey.valueOf("requestContext");
  private final List<Middleware> middlewares;

  public DefaultHttpServerHandler(List<Middleware> middlewares) {
    this.middlewares = List.copyOf(
        Objects.requireNonNull(middlewares, "middlewares cannot be null"));
  }

  @Override
  protected void channelRead0(ChannelHandlerContext ctx, HttpObject msg) {
    if (!(msg instanceof FullHttpRequest request)) {
      return;
    }

    boolean keepAlive = HttpUtil.isKeepAlive(request);
    String method = request.method().name();
    String rawUri = request.uri();
    String path = rawUri.split("\\?")[0];
    QueryStringDecoder qsd = new QueryStringDecoder(request.uri(), CharsetUtil.UTF_8);
    Map<String, List<String>> queryParams = qsd.parameters();

    Route<?> route = findMatchingRoute(method, path);
    if (route == null) {
      sendResponse(ctx, new Response<>(404, "Not Found"), keepAlive);
      return;
    }

    Map<String, String> pathParams = extractPathParams(route, path);
    RequestContext requestContext = new RequestContext(ctx, request, pathParams, queryParams);
    // Store the context in the channel's attributes, so we can use it in the sendResponse()
    ctx.channel().attr(REQUEST_CONTEXT_KEY).set(requestContext);

    // Create middleware chain with route execution as the final action
    MiddlewareChain chain = DefaultMiddlewareChain.create(
        middlewares,
        () -> executeRoute(route, requestContext, keepAlive)
    );

    // Start the middleware chain
    try {
      chain.next(requestContext);
    } catch (Exception e) {
      handleError(ctx, e, keepAlive);
    }
  }

  private Route<?> findMatchingRoute(String method, String path) {
    Route<?> route = GeneratedRoutes.getRoute(method, path);
    if (route != null) {
      return route;
    }

    for (Route<?> candidate : GeneratedRoutes.getRoutes(method)) {
      PathMatcher.Result r = PathMatcher.match(candidate.getPath(), path);
      if (r.matches()) {
        return candidate;
      }
    }

    return null;
  }

  private Map<String, String> extractPathParams(Route<?> route, String path) {
    PathMatcher.Result result = PathMatcher.match(route.getPath(), path);
    return result.matches() ? result.params() : Map.of();
  }

  private void executeRoute(Route<?> route, RequestContext ctx, boolean keepAlive) {
    route.handle(ctx).whenComplete((response, error) -> {
      try {
        if (error != null) {
          handleError(ctx.getCtx(), error, keepAlive);
          return;
        }

        sendResponse(ctx.getCtx(), response, keepAlive);
      } catch (Exception e) {
        handleError(ctx.getCtx(), e, keepAlive);
      }
    });
  }

  private void sendResponse(ChannelHandlerContext ctx, Response<?> response, boolean keepAlive) {
    FullHttpResponse httpResponse = response.toHttpResponse();
    RequestContext requestContext = ctx.channel().attr(REQUEST_CONTEXT_KEY).get();

    // add to the response all the headers from middleware, etc...
    if (requestContext != null && requestContext.getRequestHeaders() != null) {
      httpResponse.headers().add(requestContext.getRequestHeaders());
    }

    if (keepAlive) {
      httpResponse.headers()
          .set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
      //.set(HttpHeaderNames.KEEP_ALIVE, "timeout=5");
    }

    HttpUtil.setContentLength(httpResponse, httpResponse.content().readableBytes());

    ChannelFuture future = ctx.writeAndFlush(httpResponse);
    // Add listener to trigger completion callbacks
    future.addListener(f -> {
      // this is needed for 404, because it does nto go through middleware
      if (requestContext != null) {
        if (f.isSuccess()) {
          requestContext.complete(httpResponse, null);
        } else {
          requestContext.complete(null, f.cause());
        }
      }
    });

    if (!keepAlive) {
      future.addListener(ChannelFutureListener.CLOSE);
    }
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
    ProblemDetails error = (cause instanceof ProblemDetailsException pde)
        ? pde.getProblemDetails()
        : createInternalServerError(cause);

    Response<ProblemDetails> response = new Response<>(error.getStatus(), error);
    ctx.writeAndFlush(response.toHttpResponse())
        .addListener(ChannelFutureListener.CLOSE);
  }

  private void handleError(ChannelHandlerContext ctx, Throwable error, boolean keepAlive) {
    Response<?> errorResponse = (error instanceof ProblemDetailsException pde)
        ? new Response<>(pde.getProblemDetails().getStatus(), pde.getProblemDetails())
        : createErrorResponse(error);

    sendResponse(ctx, errorResponse, keepAlive);
  }

  private ProblemDetails createInternalServerError(Throwable cause) {
    LOGGER.error("Unexpected exception in channel", cause);
    return new Single(
        ProblemDetailsTypes.SERVER_ERROR,
        "Internal Server Error",
        500,
        "An unexpected error occurred",
        "unknown",
        Map.of("exception", Objects.toString(cause.getMessage(), cause.getClass().getSimpleName()))
    );
  }

  private Response<?> createErrorResponse(Throwable error) {
    LOGGER.error("Unexpected error", error);
    return new Response<>(500, "Internal Server Error");
  }
}