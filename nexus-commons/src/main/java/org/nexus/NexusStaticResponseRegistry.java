package org.nexus;

import static org.nexus.NexusUtils.DF_MAPPER;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.CharsetUtil;
import java.util.HashMap;
import java.util.Map;

public class NexusStaticResponseRegistry {

  private static final Map<String, FullHttpResponse> cache = new HashMap<>();

  private NexusStaticResponseRegistry() {
  }

  public static void register(String key, Object body, int statusCode) {
    try {
      String json = DF_MAPPER.writeValueAsString(new Response.ApiResponse(statusCode, body));
      FullHttpResponse response = new DefaultFullHttpResponse(
          HttpVersion.HTTP_1_1,
          HttpResponseStatus.valueOf(statusCode),
          Unpooled.copiedBuffer(json, CharsetUtil.UTF_8)
      );
      response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json");
      response.headers().set(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
      cache.put(key, response);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("Failed to precompute static response for key: " + key, e);
    }
  }

  public static FullHttpResponse get(String key) {
    return cache.get(key);
  }
}