package org.nexus.middleware.impl;

import io.netty.handler.codec.http.HttpResponseStatus;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import nexus.generated.SecurityConfig;
import org.nexus.ProblemDetails.Single;
import org.nexus.Response;
import org.nexus.annotations.RequestContext;
import org.nexus.enums.ProblemDetailsTypes;
import org.nexus.middleware.Middleware;
import org.nexus.middleware.NextHandler;
import org.nexus.security.SecurityRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AuthMiddleware implements Middleware {

  private static final Logger LOGGER = LoggerFactory.getLogger(AuthMiddleware.class);

  private static final String AUTH_HEADER = "Authorization";
  private static final String BEARER_PREFIX = "Bearer ";

  private final Function<String, CompletableFuture<UserPrincipal>> tokenValidator;

  public AuthMiddleware(Function<String, CompletableFuture<UserPrincipal>> tokenValidator) {
    this.tokenValidator = Objects.requireNonNull(tokenValidator, "tokenValidator cannot be null");
  }

  @Override
  public CompletableFuture<Response<?>> handle(RequestContext ctx, NextHandler next) {
    try {
      // Get the security rule for this request
      SecurityRule rule = SecurityConfig.getRule(
          ctx.request().method().name(),
          ctx.request().uri()
      );

      // If no rule exists, deny by default
//      if (rule == null) {
//        return CompletableFuture.completedFuture(createForbiddenResponse());
//      }

      LOGGER.info("init");

      LOGGER.info(rule.toString());

      LOGGER.info("init2");

      // If the endpoint is public, allow access
      if (rule.permitAll()) {
        LOGGER.info("permitted");
        return next.next();
      }

      // Check for Authorization header
      String authHeader = ctx.request().headers().get(AUTH_HEADER);
      if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
        LOGGER.info("Missing or invalid Authorization header");
        return CompletableFuture.completedFuture(
            createUnauthorizedResponse("Missing or invalid Authorization header"));
      }

      // Extract and validate the token
      String token = authHeader.substring(BEARER_PREFIX.length()).trim();
      if (token.isEmpty()) {
        LOGGER.info("Missing token");

        return CompletableFuture.completedFuture(createUnauthorizedResponse("Missing token"));
      }

      LOGGER.info("After extraction");

      // Validate token and check permissions
      return tokenValidator.apply(token)
          .thenCompose(user -> {
            if (user == null) {
              LOGGER.info("Invalid or expired token");
              return CompletableFuture.completedFuture(
                  createUnauthorizedResponse("Invalid or expired token"));
            }

            // Check if the user has required roles and permissions
            if (!rule.isPermitted(user.roles(), user.permissions())) {
              LOGGER.info("not permitted");
              return CompletableFuture.completedFuture(createForbiddenResponse());
            }

            LOGGER.info("After check, next middleware");

            return next.next();
          });

    } catch (Exception e) {
      return CompletableFuture.failedFuture(e);
    }
  }


  private Response<?> createUnauthorizedResponse(String detail) {
    return new Response<>(
        HttpResponseStatus.UNAUTHORIZED.code(),
        new Single(
            ProblemDetailsTypes.SERVER_ERROR,
            "Unauthorized",
            401,
            detail,
            "unknown",
            Map.of())
    );
  }

  private Response<?> createForbiddenResponse() {
    return new Response<>(
        HttpResponseStatus.FORBIDDEN.code(),
        new Single(
            ProblemDetailsTypes.SERVER_ERROR,
            "Forbidden",
            403,
            "You are forbidden to perform this operation",
            "unknown",
            Map.of())
    );
  }

  /**
   * Represents an authenticated user with roles and permissions.
   */
  public record UserPrincipal(String userId, Set<String> roles, Set<String> permissions) {

    public UserPrincipal(String userId, Set<String> roles, Set<String> permissions) {
      this.userId = Objects.requireNonNull(userId, "userId cannot be null");
      this.roles = Set.copyOf(Objects.requireNonNull(roles, "roles cannot be null"));
      this.permissions = Set.copyOf(
          Objects.requireNonNull(permissions, "permissions cannot be null"));
    }
  }
}