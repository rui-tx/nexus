package org.nexus.handlers;

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
import java.nio.channels.ClosedChannelException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionException;
import nexus.generated.GeneratedRoutes;
import nexus.generated.GeneratedRoutes.RouteMatch;
import org.nexus.RequestContext;
import org.nexus.Response;
import org.nexus.Route;
import org.nexus.enums.ProblemDetailsTypes;
import org.nexus.exceptions.ProblemDetailsException;
import org.nexus.interfaces.Middleware;
import org.nexus.interfaces.MiddlewareChain;
import org.nexus.interfaces.ProblemDetails;
import org.nexus.interfaces.ProblemDetails.Single;
import org.nexus.middleware.DefaultMiddlewareChain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultHttpServerHandler extends SimpleChannelInboundHandler<HttpObject> {

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
    int qIndex = rawUri.indexOf('?');
    String path = (qIndex < 0) ? rawUri : rawUri.substring(0, qIndex);
    Map<String, List<String>> queryParams =
        (qIndex < 0)
            ? Map.of()
            : new QueryStringDecoder(rawUri, CharsetUtil.UTF_8).parameters();

    RouteMatch match = GeneratedRoutes.findMatchingRoute(method, path);
    if (match == null) {
      sendResponse(ctx, new Response<>(404, "Not Found"), keepAlive);
      return;
    }

    Route<?> route = match.route();
    Map<String, String> pathParams = match.params();
    RequestContext requestContext = new RequestContext(ctx, request, pathParams, queryParams);
    // Store the context in the channel's attributes, so we can use it in the sendResponse()
    ctx.channel().attr(REQUEST_CONTEXT_KEY).set(requestContext);

    // Create a middleware chain with route execution as the final action
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
    if (!ctx.channel().isActive()) {
      LOGGER.debug("Channel closed, skipping response send");
      return;
    }

    FullHttpResponse httpResponse = response.toHttpResponse();
    RequestContext requestContext = ctx.channel().attr(REQUEST_CONTEXT_KEY).get();

    if (requestContext != null && requestContext.getRequestHeaders() != null) {
      requestContext.setRequestDuration();

      // Custom headers
      requestContext.getRequestHeaders().add(
          "X-Response-Time",
          requestContext.getRequestDuration());

      // add to the response all the headers from middleware, etc...
      httpResponse.headers().add(requestContext.getRequestHeaders());
    }

    if (keepAlive) {
      httpResponse.headers()
          .set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
    }

    HttpUtil.setContentLength(httpResponse, httpResponse.content().readableBytes());

    ChannelFuture future = ctx.writeAndFlush(httpResponse);
    future.addListener(f -> {
      if (requestContext != null) {
        if (!f.isSuccess() && f.cause() instanceof ClosedChannelException) {
          LOGGER.debug("Channel closed during write, ignoring", f.cause());
        } else if (!f.isSuccess()) {

          requestContext.complete(null, f.cause());  // Other errors
        } else {
          requestContext.complete(httpResponse, null);
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
    // Unwrap CompletionException to get to the root cause
    Throwable cause = error instanceof CompletionException ? error.getCause() : error;

    Response<?> errorResponse = (cause instanceof ProblemDetailsException pde)
        ? new Response<>(pde.getProblemDetails().getStatus(), pde.getProblemDetails())
        : createErrorResponse(cause);

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