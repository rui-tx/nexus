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
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
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

      boolean keepAlive = HttpUtil.isKeepAlive(request);

      String method = request.method().name();
      String rawUri = request.uri();
      String path = rawUri.split("\\?")[0];           // strip query string
      String body = request.content().toString(CharsetUtil.UTF_8);

      QueryStringDecoder qsd = new QueryStringDecoder(request.uri(), CharsetUtil.UTF_8);
      Map<String, List<String>> queryParams = qsd.parameters();

      Response<?> result = new Response<>(500, "Internal Server Error");

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
          FullHttpResponse notFound =
              new Response<>(404, "Not Found").toHttpResponse();
          HttpUtil.setContentLength(notFound, notFound.content().readableBytes());
          ctx.writeAndFlush(notFound)
              .addListener(ChannelFutureListener.CLOSE);
        } else {
          RequestContext rc = new RequestContext(ctx, request, pathParams, queryParams);

          route.handle(rc).whenComplete((response, error) -> {
            try {
              if (error != null) {
                handleError(ctx, error, keepAlive);
              } else {
                FullHttpResponse httpResponse = response.toHttpResponse();
                if (keepAlive) {
                  httpResponse.headers()
                      .set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
                  httpResponse.headers().set(HttpHeaderNames.KEEP_ALIVE, "timeout=5");
                }
                HttpUtil.setContentLength(httpResponse, httpResponse.content().readableBytes());

                ChannelFuture future = ctx.writeAndFlush(httpResponse);
                if (!keepAlive) {
                  future.addListener(ChannelFutureListener.CLOSE);
                }
              }
            } catch (Exception e) {
              handleError(ctx, e, keepAlive);
            }

            // No need to release the request.
            // SimpleChannelInboundHandler automatically does it for us
            //request.release();
          });
        }
      } else {
        result = new Response<>(
            HttpResponseStatus.METHOD_NOT_ALLOWED.code(),
            "Method Not Allowed");

        FullHttpResponse httpResponse = result.toHttpResponse();
        if (keepAlive) {
          httpResponse.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
        }
        HttpUtil.setContentLength(httpResponse, httpResponse.content().readableBytes());

        ChannelFuture future = ctx.writeAndFlush(httpResponse);
        if (!keepAlive) {
          future.addListener(ChannelFutureListener.CLOSE);
        }
      }
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
}