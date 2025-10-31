package org.nexus.annotations;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.util.CharsetUtil;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class RequestContext {

  private static final Logger LOGGER = LoggerFactory.getLogger(RequestContext.class);
  private final ChannelHandlerContext nettyCtx;
  private final FullHttpRequest request;
  private final Map<String, String> pathParams;
  private final Map<String, List<String>> queryParams;
  private final String body;
  private final Map<String, Object> attributes = new HashMap<>();
  private final long startTime = System.currentTimeMillis();
  private final List<BiConsumer<FullHttpResponse, Throwable>> completionHandlers = new ArrayList<>();
  private final HttpHeaders responseHeaders;

  private FullHttpResponse response;
  private boolean responseCompleted = false;

  public RequestContext(
      ChannelHandlerContext nettyCtx,
      FullHttpRequest request,
      Map<String, String> pathParams,
      Map<String, List<String>> queryParams
  ) {
    this.nettyCtx = Objects.requireNonNull(nettyCtx, "nettyCtx cannot be null");
    this.request = Objects.requireNonNull(request, "request cannot be null");
    this.pathParams = Objects.requireNonNull(pathParams, "pathParams cannot be null");
    this.queryParams = Objects.requireNonNull(queryParams, "queryParams cannot be null");
    this.body = request.content() != null ? request.content().toString(CharsetUtil.UTF_8) : "";
    this.responseHeaders = new DefaultHttpHeaders();
  }

  // Getters
  public ChannelHandlerContext getCtx() {
    return nettyCtx;
  }

  public FullHttpRequest getRequest() {
    return request;
  }

  public HttpHeaders getRequestHeaders() {
    return responseHeaders;
  }

  public Map<String, String> getPathParams() {
    return pathParams;
  }

  public Map<String, List<String>> getQueryParams() {
    return queryParams;
  }

  public String getQueryParam(String name) {
    var list = queryParams.get(name);
    return (list != null && !list.isEmpty()) ? list.getFirst() : null;
  }

  public List<String> getQueryParams(String name) {
    return queryParams.getOrDefault(name, List.of());
  }

  public String getBody() {
    return body;
  }

  public FullHttpResponse getResponse() {
    return response;
  }

  public void setResponse(FullHttpResponse response) {
    this.response = response;
  }

  public void setAttribute(String key, Object value) {
    attributes.put(key, value);
  }

  @SuppressWarnings("unchecked")
  public <T> T getAttribute(String key) {
    return (T) attributes.get(key);
  }

  public long getRequestDuration() {
    return System.currentTimeMillis() - startTime;
  }

  public void addCompletionHandler(BiConsumer<FullHttpResponse, Throwable> handler) {
    if (responseCompleted) {
      LOGGER.debug("Completion handler called immediately");
      handler.accept(response, null);
    } else {
      LOGGER.debug("Adding completion handler (total: {})", completionHandlers.size() + 1);
      completionHandlers.add(handler);
    }
  }

  public void complete(FullHttpResponse response, Throwable error) {
    if (responseCompleted) {
      LOGGER.debug("Already completed, ignoring duplicate completion");
      return;
    }

    LOGGER.debug("Completing request with {} and error: {}",
        response != null ? "response" : "no response",
        error != null ? error.getMessage() : "no error");

    this.response = response;
    this.responseCompleted = true;

    for (BiConsumer<FullHttpResponse, Throwable> handler : completionHandlers) {
      try {
        LOGGER.debug("Calling completion handler");
        handler.accept(response, error);
      } catch (Exception e) {
        LOGGER.error("Error in completion handler", e);
      }
    }
    completionHandlers.clear();
  }
}