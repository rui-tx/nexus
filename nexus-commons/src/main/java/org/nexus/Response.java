package org.nexus;

import static org.nexus.NexusUtils.DF_MAPPER;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.CharsetUtil;
import java.time.Instant;
import java.time.ZoneId;
import org.nexus.enums.ResponseType;
import org.nexus.interfaces.ProblemDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Response<T> {

  private static final Logger LOGGER = LoggerFactory.getLogger(Response.class);

  private final int statusCode;
  private final T body;

  private ResponseType responseType = ResponseType.JSON;

  public Response(int statusCode) {
    this.statusCode = statusCode;
    this.body = null;
  }

  public Response(int statusCode, T body) {
    this.statusCode = statusCode;
    this.body = body;
  }

  public Response(int statusCode, T body, ResponseType responseType) {
    this.statusCode = statusCode;
    this.body = body;
    this.responseType = responseType;
  }

  public int getStatusCode() {
    return statusCode;
  }

  public T getBody() {
    return body;
  }

  public ResponseType getResponseType() {
    return responseType;
  }

  public FullHttpResponse toHttpResponse() {
    boolean isProblem = body instanceof ProblemDetails;
    boolean errorParsing = false;
    String parsed;

    try {
      if (isProblem) {
        parsed = DF_MAPPER.writeValueAsString(body);
      } else if (responseType == ResponseType.JSON) {
        ApiResponse wrapper = new ApiResponse(statusCode, body);
        parsed = DF_MAPPER.writeValueAsString(wrapper);
      } else {
        parsed = body != null ? body.toString() : "";
      }
    } catch (JsonProcessingException e) {
      LOGGER.error("Serialization failed", e);
      errorParsing = true;
      parsed = """
          {"status":%d,"error":"Serialization failed"}
          """.formatted(statusCode);
    }

    String contentType = isProblem
        ? "application/problem+json"
        : responseType == ResponseType.JSON
            ? "application/json"
            : "text/plain";

    DefaultFullHttpResponse response = new DefaultFullHttpResponse(
        HttpVersion.HTTP_1_1,
        HttpResponseStatus.valueOf(!errorParsing ? statusCode : 500),
        body != null ? Unpooled.copiedBuffer(parsed, CharsetUtil.UTF_8) : Unpooled.EMPTY_BUFFER
    );

    response.headers().set(HttpHeaderNames.CONTENT_TYPE, contentType);
    response.headers().set(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());

    return response;
  }

  public record ApiResponse(
      @JsonProperty("date") String date,
      int status,
      Object data
  ) {

    public ApiResponse(int status, Object data) {
      this(
          Instant.now()
              .atZone(ZoneId.systemDefault())
              .toString(),
          status,
          data
      );
    }
  }
}