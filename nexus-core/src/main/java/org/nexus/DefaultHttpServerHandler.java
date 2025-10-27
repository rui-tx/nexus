package org.nexus;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpObject;
import io.netty.util.CharsetUtil;
import java.util.Map;
import nexus.generated.GeneratedRoutes;
import org.nexus.annotations.Route;
import org.nexus.enums.ProblemDetailsTypes;
import org.nexus.exceptions.ProblemDetailsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class DefaultHttpServerHandler extends SimpleChannelInboundHandler<HttpObject> {

  private static final Logger LOGGER = LoggerFactory.getLogger(DefaultHttpServerHandler.class);

  @Override
  protected void channelRead0(ChannelHandlerContext ctx, HttpObject msg) {
    if (msg instanceof FullHttpRequest request) {

      String method = request.method().name();
      String rawUri = request.uri();
      String path = rawUri.split("\\?")[0];           // strip query string
      String body = request.content().toString(CharsetUtil.UTF_8);

      Response<?> result;

      if (method.equals("GET") || method.equals("POST")) {

        Route<?> route = GeneratedRoutes.getRoute(method, path);
        Map<String, String> params = Map.of();

        if (route == null) {
          for (Route<?> candidate : GeneratedRoutes.getRoutes()) {
            PathMatcher.Result r = PathMatcher.match(candidate.getPath(), path);
            if (r.matches()) {
              route = candidate;
              params = r.params();
              break;                     // first match wins
            }
          }
        }

        result = (route != null)
            ? route.handle(ctx, request, params)
            : new Response<>(404, "Not Found");
      } else {
        result = new Response<>(405, "Method Not Allowed");
      }

      request.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
      ctx.writeAndFlush(result.toHttpResponse())
          .addListener(ChannelFutureListener.CLOSE)
          .addListener(future -> {
            if (!future.isSuccess()) {
              LOGGER.error("Failed to send response: {}", future.cause().toString());
            }
          });
    }
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
    ProblemDetails error;

    switch (cause) {
      case ProblemDetailsException pde -> error = pde.getProblemDetails();
      default -> {
        LOGGER.error("Unexpected exception in channel", cause);
        error = new ProblemDetails.Single(
            ProblemDetailsTypes.SERVER_ERROR,
            "Internal Server Error",
            500,
            "An unexpected error occurred",
            "unknown",
            Map.of(
                "exception", cause.getMessage())
        );
      }
    }

    Response<ProblemDetails> response = new Response<>(500, error);
    ctx.writeAndFlush(response.toHttpResponse())
        .addListener(ChannelFutureListener.CLOSE);
  }
}