package org.nexus.handlers.testing;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.util.CharsetUtil;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import org.nexus.PathMatcher;
import org.nexus.ProblemDetails;
import org.nexus.Response;
import org.nexus.annotations.RequestContext;
import org.nexus.exceptions.ProblemDetailsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TestRouterHandler extends SimpleChannelInboundHandler<HttpObject> {

  private static final Logger LOGGER = LoggerFactory.getLogger(TestRouterHandler.class);

  private final TestRouteRegistry registry;

  public TestRouterHandler(TestRouteRegistry registry) {
    this.registry = Objects.requireNonNull(registry, "registry");
  }

  @Override
  protected void channelRead0(ChannelHandlerContext ctx, HttpObject msg) {
    if (!(msg instanceof FullHttpRequest request)) {
      // Not an HTTP request we care about; pass through
      ctx.fireChannelRead(msg);
      return;
    }

    final String methodName = request.method().name();
    final String rawUri = request.uri();
    final String path = rawUri.split("\\?")[0];

    // Try to find a match among test routes
    TestRouteRegistry.TestRoute<?> matched = null;
    Map<String, String> pathParams = Map.of();

    for (var r : registry.routes()) {
      if (!r.method().name().equals(methodName)) {
        continue;
      }

      if (r.pathTemplate().equals(path)) {
        matched = r;
        break;
      }
      var match = PathMatcher.match(r.pathTemplate(), path);
      if (match.matches()) {
        matched = r;
        pathParams = match.params();
        break;
      }
    }

    if (matched == null) {
      // Not handled by tests → pass down to the "real" handler
      ctx.fireChannelRead(request.retain());
      return;
    }

    // Build query params to feed RequestContext
    QueryStringDecoder qsd = new QueryStringDecoder(rawUri, CharsetUtil.UTF_8);
    Map<String, List<String>> queryParams = qsd.parameters();

    try {
      RequestContext rc = new RequestContext(ctx, request, pathParams, queryParams);
      Object result = matched.handler().apply(rc);

      if (result instanceof CompletableFuture<?> future) {
        // Handle async response
        future.whenComplete((response, error) -> {
          try {
            if (error != null) {
              handleError(ctx, error);
            } else if (response instanceof Response<?> r) {
              sendResponse(ctx, r.toHttpResponse(), HttpUtil.isKeepAlive(request));
            } else {
              LOGGER.error("Unexpected response type: {}", response.getClass().getName());
              sendErrorResponse(ctx, 500, "Internal Server Error: Invalid response type");
            }
          } catch (Exception e) {
            LOGGER.error("Error processing async response", e);
            sendErrorResponse(ctx, 500, "Internal Server Error");
          }
        });
      } else {
        LOGGER.error("Unexpected response type: {}",
            result != null ? result.getClass().getName() : "null");
        sendErrorResponse(ctx, 500, "Internal Server Error: Invalid response type");
      }
    } catch (ProblemDetailsException pde) {
      sendErrorResponse(ctx, pde.getProblemDetails().getStatus(), pde.getProblemDetails());
    } catch (Throwable t) {
      LOGGER.error("Unexpected exception during TEST route handling", t);
      ProblemDetails error = new ProblemDetails.Single(
          org.nexus.enums.ProblemDetailsTypes.SERVER_ERROR,
          "Internal Server Error",
          500,
          "An unexpected error occurred (test route)",
          matched.pathTemplate(),
          Map.of("exception", Objects.toString(t.getMessage(), t.getClass().getSimpleName()))
      );
      sendErrorResponse(ctx, 500, error);
    }
  }

  private void handleError(ChannelHandlerContext ctx, Throwable error) {
    if (error instanceof ProblemDetailsException pde) {
      sendErrorResponse(ctx, pde.getProblemDetails().getStatus(), pde.getProblemDetails());
    } else {
      LOGGER.error("Unhandled exception in async handler", error);
      sendErrorResponse(ctx, 500, "Internal Server Error: " + error.getMessage());
    }
  }

  private void sendResponse(ChannelHandlerContext ctx, FullHttpResponse response,
      boolean keepAlive) {
    try {
      if (keepAlive) {
        response.headers().set(HttpHeaderNames.CONNECTION, "keep-alive");
        response.headers().set(HttpHeaderNames.KEEP_ALIVE, "timeout=5");
      }
      HttpUtil.setContentLength(response, response.content().readableBytes());

      ctx.writeAndFlush(response).addListener(future -> {
        if (!future.isSuccess()) {
          LOGGER.error("Failed to send response: {}", future.cause().toString());
        }
        if (!keepAlive) {
          ctx.close();
        }
      });
    } catch (Exception e) {
      LOGGER.error("Error sending response", e);
      ctx.close();
    }
  }

  private void sendErrorResponse(ChannelHandlerContext ctx, int status, Object body) {
    Response<?> errorResponse = new Response<>(status, body);
    sendResponse(ctx, errorResponse.toHttpResponse(), false);
  }
}