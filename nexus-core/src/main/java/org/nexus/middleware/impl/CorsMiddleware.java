package org.nexus.middleware.impl;

import io.netty.handler.codec.http.HttpMethod;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.nexus.Response;
import org.nexus.annotations.RequestContext;
import org.nexus.exceptions.ProblemDetailsException;
import org.nexus.middleware.Middleware;
import org.nexus.middleware.NextHandler;

/**
 * Middleware that handles Cross-Origin Resource Sharing (CORS) for the server. Supports preflight
 * requests and adds appropriate CORS headers to responses.
 */
public class CorsMiddleware implements Middleware {

  private static final List<HttpMethod> ALLOWED_METHODS = Arrays.asList(
      HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT,
      HttpMethod.DELETE, HttpMethod.OPTIONS, HttpMethod.HEAD
  );

  private static final List<String> ALLOWED_HEADERS = Arrays.asList(
      "Origin", "X-Requested-With", "Content-Type", "Accept", "Authorization"
  );

  private static final long MAX_AGE = 3600; // 1 hour
  private final String allowedOrigins;

  /**
   * Creates a new CORS middleware that allows requests from any origin.
   */
  public CorsMiddleware() {
    this("*");
  }

  /**
   * Creates new CORS middleware with specific allowed origins.
   *
   * @param allowedOrigins Comma-separated list of allowed origins, or "*" for all
   */
  public CorsMiddleware(String allowedOrigins) {
    this.allowedOrigins = allowedOrigins;
  }

  @Override
  public CompletableFuture<Response<?>> handle(RequestContext ctx, NextHandler next) {
    // Handle preflight requests
    if (ctx.request().method().equals(HttpMethod.OPTIONS)) {
      return CompletableFuture.completedFuture(createCorsResponse(ctx));
    }

    CompletableFuture<Response<?>> future = new CompletableFuture<>();

    next.next().whenComplete((response, throwable) -> {
      if (throwable != null) {
        // Handle error case - create an error response with CORS headers
        if (throwable instanceof ProblemDetailsException) {
          ProblemDetailsException pde = (ProblemDetailsException) throwable;
          Response<?> errorResponse = new Response<>(
              pde.getProblemDetails().getStatus(),
              pde.getProblemDetails()
          );
          addCorsHeaders(ctx, errorResponse);
          future.complete(errorResponse);
        } else {
          // For other types of exceptions, create a 500 response
          Response<?> errorResponse = new Response<>(500, "Internal Server Error");
          addCorsHeaders(ctx, errorResponse);
          future.complete(errorResponse);
        }
      } else {
        // Handle success case - add CORS headers to the response
        addCorsHeaders(ctx, response);
        future.complete(response);
      }
    });

    return future;
  }

  private Response<Void> createCorsResponse(RequestContext ctx) {
    Response<Void> response = new Response<>(200);
    addCorsHeaders(ctx, response);
    return response;
  }

  private void addCorsHeaders(RequestContext ctx, Response<?> response) {
    // Get the request origin for dynamic CORS
    String requestOrigin = ctx.request().headers().get("Origin");
    String origin = "*".equals(allowedOrigins) ? "*" :
        (requestOrigin != null && allowedOrigins.contains(requestOrigin) ? requestOrigin
            : allowedOrigins.split(",")[0]);

    // Convert HttpMethod enums to string names
    String allowedMethods = String.join(", ",
        ALLOWED_METHODS.stream().map(HttpMethod::name).toArray(String[]::new));

    // Add CORS headers
    response.addHeader("Access-Control-Allow-Origin", origin);
    response.addHeader("Access-Control-Allow-Methods", allowedMethods);
    response.addHeader("Access-Control-Allow-Headers", String.join(", ", ALLOWED_HEADERS));
    response.addHeader("Access-Control-Max-Age", String.valueOf(MAX_AGE));
    response.addHeader("Access-Control-Expose-Headers", "*");

    // Add Vary header if using specific origins
    if (!"*".equals(allowedOrigins)) {
      response.addHeader("Vary", "Origin");
    }
  }
}
