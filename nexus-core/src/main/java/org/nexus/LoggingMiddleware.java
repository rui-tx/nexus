package org.nexus;

import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpMethod;
import java.util.UUID;
import org.nexus.interfaces.Middleware;
import org.nexus.interfaces.MiddlewareChain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoggingMiddleware implements Middleware {

  private static final Logger LOGGER = LoggerFactory.getLogger(LoggingMiddleware.class);
  private static final String REQUEST_ID_HEADER = "X-Request-ID";

  @Override
  public void handle(RequestContext ctx, MiddlewareChain chain) throws Exception {
    logRequest(ctx);

    ctx.addCompletionHandler((response, error) -> {
      logResponse(ctx, error);
    });

    try {
      chain.next(ctx);
    } catch (Exception e) {
      ctx.complete(null, e);
      throw e;
    }
  }

  private void logRequest(RequestContext ctx) {
    FullHttpRequest request = ctx.getRequest();
    String requestId = getOrGenerateRequestId(ctx);
    String method = request.method().name();
    String uri = request.uri();

    LOGGER.debug("REQ {} {} [req_id={}]", method, uri, requestId);
    LOGGER.debug("REQ HEADERS {}", request.headers());

    if (request.method() != HttpMethod.GET && request.content() != null) {
      LOGGER.debug("REQ body {} bytes", request.content().readableBytes());
    }
  }

  private void logResponse(RequestContext ctx, Throwable error) {
    long duration = ctx.getRequestDuration();
    String requestId = getOrGenerateRequestId(ctx);
    String method = ctx.getRequest().method().name();
    String uri = ctx.getRequest().uri();

    if (error != null) {
      LOGGER.error("fail {} {} [req_id={}, duration={}ms] - {}: {}",
          method, uri, requestId, duration,
          error.getClass().getSimpleName(),
          error.getMessage());
      return;
    }

    FullHttpResponse response = ctx.getResponse();
    if (response == null) {
      LOGGER.warn("No response set for {} {} [req_id={}, duration={}ms]",
          method, uri, requestId, duration);
      return;
    }

    LOGGER.debug("HEADERS {}", response.headers());

    int statusCode = response.status().code();
    String logMsg = "{} {} -> {} [req_id={}, duration={}ms, size={}]";

    if (statusCode >= 500) {
      LOGGER.error(logMsg, method, uri, statusCode, requestId, duration,
          response.content().readableBytes());
      return;
    }

    if (statusCode >= 400) {
      LOGGER.warn(logMsg, method, uri, statusCode, requestId, duration,
          response.content().readableBytes());
      return;
    }

    LOGGER.info(logMsg, method, uri, statusCode, requestId, duration,
        response.content().readableBytes());
  }

  private String getOrGenerateRequestId(RequestContext ctx) {
    String requestId = ctx.getRequest().headers().get(REQUEST_ID_HEADER);
    if (requestId == null) {
      requestId = UUID.randomUUID().toString();
    }

    ctx.getRequestHeaders().add(REQUEST_ID_HEADER, requestId);
    return requestId;
  }
}