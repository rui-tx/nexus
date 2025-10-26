package org.nexus.annotations;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpRequest;
import java.util.Map;
import org.nexus.Response;

@FunctionalInterface
public interface RouteHandler<T> {

  Response<T> handle(ChannelHandlerContext ctx, HttpRequest req, Map<String, String> params);
}
