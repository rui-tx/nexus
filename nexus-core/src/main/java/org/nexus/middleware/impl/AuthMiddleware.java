package org.nexus.middleware.impl;

import io.netty.handler.codec.http.HttpResponseStatus;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.nexus.ProblemDetails;
import org.nexus.Response;
import org.nexus.annotations.RequestContext;
import org.nexus.enums.ProblemDetailsTypes;
import org.nexus.exceptions.ProblemDetailsException;
import org.nexus.middleware.Middleware;
import org.nexus.middleware.NextHandler;

/**
 * Middleware that handles authentication using Bearer tokens. Can be configured with a static token
 * or a custom token validator function.
 */
public class AuthMiddleware implements Middleware {

  private static final String AUTH_HEADER = "Authorization";
  private static final String BEARER_PREFIX = "Bearer ";

  private final Function<String, CompletableFuture<Boolean>> tokenValidator;
  private final String realm;

  /**
   * Creates a new AuthMiddleware with a static token.
   *
   * @param validToken The valid bearer token to accept
   */
  public AuthMiddleware(String validToken) {
    this(validToken, "api");
  }

  /**
   * Creates a new AuthMiddleware with a static token and custom realm.
   *
   * @param validToken The valid bearer token to accept
   * @param realm      The authentication realm
   */
  public AuthMiddleware(String validToken, String realm) {
    Objects.requireNonNull(validToken, "Valid token cannot be null");
    this.realm = Objects.requireNonNull(realm, "Realm cannot be null");
    this.tokenValidator = token -> CompletableFuture.completedFuture(validToken.equals(token));
  }

  /**
   * Creates a new AuthMiddleware with a custom token validator function.
   *
   * @param tokenValidator A function that validates a token and returns a
   *                       CompletableFuture<Boolean>
   * @param realm          The authentication realm
   */
  public AuthMiddleware(Function<String, CompletableFuture<Boolean>> tokenValidator, String realm) {
    this.tokenValidator = Objects.requireNonNull(tokenValidator, "Token validator cannot be null");
    this.realm = Objects.requireNonNull(realm, "Realm cannot be null");
  }

  @Override
  public CompletableFuture<Response<?>> handle(RequestContext ctx, NextHandler next) {
    // Skip auth for OPTIONS requests (CORS preflight)
    if ("OPTIONS".equals(ctx.request().method().name())) {
      return next.next();
    }

    CompletableFuture<Response<?>> future = new CompletableFuture<>();
    String authHeader = ctx.request().headers().get(AUTH_HEADER);

    // Check for missing or malformed Authorization header
    if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
      future.completeExceptionally(createUnauthorizedException(
          "Missing or invalid Authorization header",
          "Bearer",
          "The request requires authentication"
      ));
      return future;
    }

    // Extract and validate the token
    String token = authHeader.substring(BEARER_PREFIX.length()).trim();
    if (token.isEmpty()) {
      future.completeExceptionally(createUnauthorizedException(
          "Missing token",
          "Bearer",
          "The access token is missing from the request"
      ));
      return future;
    }

    // Validate the token asynchronously
    tokenValidator.apply(token).whenComplete((isValid, throwable) -> {
      if (throwable != null) {
        future.completeExceptionally(throwable);
      } else if (!isValid) {
        future.completeExceptionally(createForbiddenException(
            "Invalid or expired token",
            "The provided token is not valid"
        ));
      } else {
        // Token is valid, continue to the next middleware/handler
        next.next().whenComplete((response, nextThrowable) -> {
          if (nextThrowable != null) {
            future.completeExceptionally(nextThrowable);
          } else {
            future.complete(response);
          }
        });
      }
    });

    return future;
  }

  private ProblemDetailsException createUnauthorizedException(String detail, String scheme,
      String description) {
    return new ProblemDetailsException(
        new ProblemDetails.Single(
            ProblemDetailsTypes.CLIENT_ERROR,
            "Unauthorized",
            HttpResponseStatus.UNAUTHORIZED.code(),
            detail,
            null // Instance can be set by the global exception handler
        )
    );
  }

/*    .addHeader("WWW-Authenticate",
      String.format("Bearer realm=\"%s\", error=\"invalid_token\", error_description=\"%s\"",
      realm, description)*/

  private ProblemDetailsException createForbiddenException(String detail, String description) {
    return new ProblemDetailsException(
        new ProblemDetails.Single(
            ProblemDetailsTypes.CLIENT_ERROR,
            "Forbidden",
            HttpResponseStatus.FORBIDDEN.code(),
            detail,
            null // Instance can be set by the global exception handler
        )
    );
  }
}
