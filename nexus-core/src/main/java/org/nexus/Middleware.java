package org.nexus;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;

public interface Middleware {
  default void handle(ChannelHandlerContext ctx, FullHttpRequest req) {
  }

  default Response process(ChannelHandlerContext ctx, FullHttpRequest req, Response response) {
    return response;
  }
}