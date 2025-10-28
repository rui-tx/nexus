package org.nexus;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.util.CharsetUtil;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import nexus.generated.GeneratedRoutes;
import org.nexus.ProblemDetails.Single;
import org.nexus.annotations.RequestContext;
import org.nexus.annotations.Route;
import org.nexus.enums.HttpMethod;
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

      QueryStringDecoder qsd = new QueryStringDecoder(request.uri(), CharsetUtil.UTF_8);
      Map<String, List<String>> queryParams = qsd.parameters();

      Response<?> result;

      if (HttpMethod.GET.name().equals(method) || HttpMethod.POST.name().equals(method)) {
        Route<?> route = GeneratedRoutes.getRoute(method, path);
        Map<String, String> pathParams = Map.of();

        if (route == null) {
          for (Route<?> candidate : GeneratedRoutes.getRoutes(method)) {
            PathMatcher.Result r = PathMatcher.match(candidate.getPath(), path);
            if (r.matches()) {
              route = candidate;
              pathParams = r.params();
              break;
            }
          }
        }

        if (route == null) {
          result = new Response<>(HttpResponseStatus.NOT_FOUND.code(), "Not Found");
        } else {
          RequestContext rc = new RequestContext(ctx, request, pathParams, queryParams);
          try {
            result = route.handle(rc);
          } catch (ProblemDetailsException pde) {
            result = new Response<>(pde.getProblemDetails().getStatus(), pde.getProblemDetails());
          } catch (Throwable t) {
            LOGGER.error("Unexpected exception during route handling", t);
            ProblemDetails error =
                new ProblemDetails.Single(
                    org.nexus.enums.ProblemDetailsTypes.SERVER_ERROR,
                    "Internal Server Error",
                    500,
                    "An unexpected error occurred",
                    "unknown",
                    Map.of(
                        "exception",
                        Objects.toString(t.getMessage(), t.getClass().getSimpleName()))
                );
            result = new Response<>(HttpResponseStatus.INTERNAL_SERVER_ERROR.code(), error);
          }
        }
      } else {
        result = new Response<>(
            HttpResponseStatus.METHOD_NOT_ALLOWED.code(),
            "Method Not Allowed");
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
    if (Objects.requireNonNull(cause) instanceof ProblemDetailsException pde) {
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
}