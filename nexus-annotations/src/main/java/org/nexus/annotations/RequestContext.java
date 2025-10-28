package org.nexus.annotations;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.util.CharsetUtil;
import java.util.List;
import java.util.Map;

public final class RequestContext {

  private final ChannelHandlerContext nettyCtx;
  private final FullHttpRequest request;
  private final Map<String, String> pathParams;           // single-valued (from /:id)
  private final Map<String, List<String>> queryParams;    // multivalued (?a=1&a=2)
  private final String body;

  public RequestContext(
      ChannelHandlerContext nettyCtx,
      FullHttpRequest request,
      Map<String, String> pathParams,
      Map<String, List<String>> queryParams
  ) {
    this.nettyCtx = nettyCtx;
    this.request = request;
    this.pathParams = pathParams;
    this.queryParams = queryParams;
    this.body = request.content() != null ? request.content().toString(CharsetUtil.UTF_8) : "";
  }

  public ChannelHandlerContext ctx() {
    return nettyCtx;
  }

  public FullHttpRequest request() {
    return request;
  }

  public Map<String, String> pathParams() {
    return pathParams;
  }

  public Map<String, List<String>> queryParams() {
    return queryParams;
  }

  public String queryParam(String name) {
    var list = queryParams.get(name);
    return (list != null && !list.isEmpty())
        ? list.getFirst()
        : null;
  }

  public List<String> queryParams(String name) {
    var list = queryParams.get(name);
    return list != null
        ? list
        : List.of();
  }

  public String body() {
    return body;
  }
}