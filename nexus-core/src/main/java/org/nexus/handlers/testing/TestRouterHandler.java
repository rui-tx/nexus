package org.nexus.handlers.testing;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.util.CharsetUtil;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

    final String methodName = request.method().name(); // e.g., "GET"
    final String rawUri = request.uri();
    final String path = rawUri.split("\\?")[0];

    // Try to find a match among test routes
    TestRouteRegistry.TestRoute matched = null;
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

    // Build query params to feed RequestContext (keep parity with your prod handler)
    QueryStringDecoder qsd = new QueryStringDecoder(rawUri, CharsetUtil.UTF_8);
    Map<String, List<String>> queryParams = qsd.parameters();

    Response<?> result;
    try {
      RequestContext rc = new RequestContext(ctx, request, pathParams, queryParams);
      result = matched.handler().apply(rc);
    } catch (ProblemDetailsException pde) {
      result = new Response<>(pde.getProblemDetails().getStatus(), pde.getProblemDetails());
    } catch (Throwable t) {
      LOGGER.error("Unexpected exception during TEST route handling", t);
      ProblemDetails error =
          new ProblemDetails.Single(
              org.nexus.enums.ProblemDetailsTypes.SERVER_ERROR,
              "Internal Server Error",
              500,
              "An unexpected error occurred (test route)",
              matched.pathTemplate(),
              Map.of("exception", Objects.toString(t.getMessage(), t.getClass().getSimpleName())));
      result = new Response<>(500, error);
    }

    // Send and close like your prod handler does
    ctx.writeAndFlush(result.toHttpResponse())
        .addListener(ChannelFutureListener.CLOSE)
        .addListener(future -> {
          if (!future.isSuccess()) {
            LOGGER.error("Failed to send TEST response: {}", future.cause().toString());
          }
        });
  }
}