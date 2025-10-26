package org.nexus;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import java.util.Map;
import nexus.generated.GeneratedRoutes;
import org.nexus.annotations.Route;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class DefaultHttpServerHandler extends SimpleChannelInboundHandler<HttpObject> {

  private static final Logger LOGGER = LoggerFactory.getLogger(DefaultHttpServerHandler.class);

//  private final List<Middleware> beforeMiddlewares = new ArrayList<>();
//  private final List<Middleware> afterMiddlewares = new ArrayList<>();
//
//  public void addBeforeMiddleware(Middleware middleware) {
//    beforeMiddlewares.add(middleware);
//  }
//
//  public void addAfterMiddleware(Middleware middleware) {
//    afterMiddlewares.add(middleware);
//  }

  @Override
  protected void channelRead0(ChannelHandlerContext ctx, HttpObject msg) {
    if (msg instanceof HttpRequest request) {

//      for (Middleware middleware : beforeMiddlewares) {
//        middleware.handle(ctx, req);
//      }

      String method = request.method().name();
      String path = request.uri().split("\\?")[0];

      Response<?> result;
      if (method.equals("GET")
          || method.equals("POST")) {
        Route<?> route = GeneratedRoutes.getRoute(method, path);
        result = route != null ?
            route.handle(ctx, request, Map.of()) :
            new Response<>(404, "Not Found");
      } else {
        result = new Response<>(405, "Method Not Allowed");
      }

//      for (Middleware middleware : afterMiddlewares) {
//        response = middleware.process(ctx, req, response);
//      }

      // Write and flush the response, then close the connection
      request.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
      ctx.writeAndFlush(result.toHttpResponse())
          .addListener(ChannelFutureListener.CLOSE)
          .addListener(future -> {
            if (!future.isSuccess()) {
              LOGGER.error("Failed to send response: {}", String.valueOf(future.cause()));
            }
          });
    }
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
    LOGGER.error("{}", cause.getMessage());
    Response<String> response = new Response<>(500, "Internal Server Error");
    ctx.writeAndFlush(response.toHttpResponse())
        .addListener(ChannelFutureListener.CLOSE)
        .addListener(future -> {
          if (!future.isSuccess()) {
            LOGGER.error("Failed to send response: {}", String.valueOf(future.cause()));
          }
        });
  }
}