package org.nexus;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.CharsetUtil;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import org.nexus.enums.ResponseType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Response<T> {

  private static final Logger LOGGER = LoggerFactory.getLogger(Response.class);

  private final ObjectMapper mapper = new ObjectMapper();

  private final int statusCode;
  private final T body;
  private final Map<String, String> headers = new HashMap<>();
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

  /**
   * Adds a header to the response.
   *
   * @param name The name of the header
   * @param value The value of the header
   * @return This response instance for method chaining
   */
  public Response<T> addHeader(String name, String value) {
    headers.put(name, value);
    return this;
  }

  /**
   * Gets all headers that have been added to this response.
   *
   * @return An unmodifiable map of headers
   */
  public Map<String, String> getHeaders() {
    return Map.copyOf(headers);
  }

  public FullHttpResponse toHttpResponse() {
    boolean isProblem = body instanceof ProblemDetails;
    boolean errorParsing = false;
    String parsed;

    try {
      if (isProblem) {
        parsed = mapper.writeValueAsString(body);
      } else if (responseType == ResponseType.JSON) {
        ApiResponse wrapper = new ApiResponse(statusCode, body);
        parsed = mapper.writeValueAsString(wrapper);
      } else {
        parsed = body != null ? body.toString() : "null";
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
        Unpooled.copiedBuffer(parsed, CharsetUtil.UTF_8)
    );

    // Set content type and length
    response.headers().set(HttpHeaderNames.CONTENT_TYPE, contentType);
    response.headers().set(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
    
    // Add custom headers
    headers.forEach((name, value) -> 
        response.headers().set(name, value)
    );

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