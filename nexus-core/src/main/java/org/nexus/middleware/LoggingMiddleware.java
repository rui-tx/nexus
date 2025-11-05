package org.nexus.middleware;

import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpMethod;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import org.nexus.RequestContext;
import org.nexus.interfaces.Middleware;
import org.nexus.interfaces.MiddlewareChain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoggingMiddleware implements Middleware {

  private static final Logger LOGGER = LoggerFactory.getLogger(LoggingMiddleware.class);
  private static final String REQUEST_ID_HEADER = "X-Request-ID";
  private static final AtomicLong ID_COUNTER = new AtomicLong();  // Todo: change this to UUID

  private static void logResponse(RequestContext ctx, Throwable error) {
    long duration = ctx.getRequestDuration();
    String requestId = ctx.getRequestHeaders().get(REQUEST_ID_HEADER);
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

    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug("HEADERS {}", response.headers());
    }

    int statusCode = response.status().code();
    String logMsg = "{} {} -> {} [req_id={}, duration={}ms, size={}]";
    int contentBytes = response.content().readableBytes();

    if (statusCode >= 500) {
      LOGGER.error(logMsg, method, uri, statusCode, requestId, duration, contentBytes);
    } else if (statusCode >= 400) {
      LOGGER.warn(logMsg, method, uri, statusCode, requestId, duration, contentBytes);
    } else if (LOGGER.isTraceEnabled()) {
      LOGGER.trace(logMsg, method, uri, statusCode, requestId, duration, contentBytes);
    }
  }

  @Override
  public void handle(RequestContext ctx, MiddlewareChain chain) throws Exception {
    logRequest(ctx);

    ctx.addCompletionHandler(new CompletionHandler(ctx));

    chain.next(ctx);
  }

  private void logRequest(RequestContext ctx) {
    FullHttpRequest request = ctx.getRequest();
    String requestId = getOrGenerateRequestId(ctx);
    String method = request.method().name();
    String uri = request.uri();

    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug("REQ {} {} [req_id={}]", method, uri, requestId);
      LOGGER.debug("REQ HEADERS {}", request.headers());
      if (request.method() != HttpMethod.GET && request.content() != null) {
        LOGGER.debug("REQ body {} bytes", request.content().readableBytes());
      }
    }
  }

  private String getOrGenerateRequestId(RequestContext ctx) {
    String requestId = ctx.getRequestHeaders().get(REQUEST_ID_HEADER);
    if (requestId != null) {
      return requestId;
    }

    requestId = ctx.getRequest().headers().get(REQUEST_ID_HEADER);
    if (requestId == null) {
      requestId = String.valueOf(ID_COUNTER.incrementAndGet());
    }

    ctx.getRequestHeaders().add(REQUEST_ID_HEADER, requestId);
    return requestId;
  }

  private record CompletionHandler(RequestContext ctx) implements
      BiConsumer<FullHttpResponse, Throwable> {

    @Override
    public void accept(FullHttpResponse response, Throwable error) {
      logResponse(ctx, error);
    }
  }
}