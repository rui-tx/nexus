package org.nexus;

import io.netty.handler.codec.http.FullHttpRequest;
import java.util.Map;
import java.util.Set;
import nexus.generated.GeneratedSecurityRules;
import org.nexus.enums.ProblemDetailsTypes;
import org.nexus.exceptions.ProblemDetailsException;
import org.nexus.interfaces.Middleware;
import org.nexus.interfaces.MiddlewareChain;
import org.nexus.interfaces.ProblemDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SecurityMiddleware implements Middleware {

  private static final Logger LOGGER = LoggerFactory.getLogger(SecurityMiddleware.class);
  private static final String AUTH_HEADER = "Authorization";

  @Override
  public void handle(RequestContext ctx, MiddlewareChain chain) throws Exception {
    FullHttpRequest request = ctx.getRequest();
    String method = request.method().name().toUpperCase();
    String path = request.uri().split("\\?")[0];

    SecurityRule rule = GeneratedSecurityRules.getRule(method, path);

    if (rule == null) {
      // No security rule defined: assume public access
      chain.next(ctx);
      return;
    }

    if (rule.permitAll()) {
      chain.next(ctx);
      return;
    }

    AuthResult authResult = validateAuth(request, rule);
    if (!authResult.authenticated()) {
      throwSecurityException("Unauthorized", "Invalid or missing credentials", 401, path);
      return;
    }

    if (!authResult.hasRequiredRoles(rule.requiredRoles()) || !authResult.hasRequiredPermissions(
        rule.requiredPermissions())) {
      throwSecurityException("Forbidden", "Insufficient privileges", 403, path);
      return;
    }

    // add the logged user to context
    // ctx.setAttribute("user", authResult.getUser());

    chain.next(ctx);
  }

  private AuthResult validateAuth(FullHttpRequest request, SecurityRule rule) {
    String authHeader = request.headers().get(AUTH_HEADER);
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      return new AuthResult(false, null, Set.of(), Set.of());
    }

    String token = authHeader.substring(7);
    if (!"valid".equals(token)) {
      return new AuthResult(false, null, Set.of(), Set.of());
    }

    Set<String> userRoles = Set.of("admin");  // From token claims
    Set<String> userPermissions = Set.of("read", "write");

    return new AuthResult(true, "user-id", userRoles, userPermissions);
  }

  private void throwSecurityException(String title, String message, int status,
      String path) {
    throw new ProblemDetailsException(
        new ProblemDetails.Single(
            ProblemDetailsTypes.SECURITY_ERROR,
            title,
            status,
            message,
            path,
            Map.of()
        )
    );
  }

  private record AuthResult(boolean authenticated,
                            String userId,
                            Set<String> roles,
                            Set<String> permissions) {

    public String getUser() {
      return userId;
    }

    public boolean hasRequiredRoles(Set<String> requiredRoles) {
      if (requiredRoles.isEmpty()) {
        return true;
      }
      return roles.containsAll(requiredRoles);
    }

    public boolean hasRequiredPermissions(Set<String> requiredPermissions) {
      if (requiredPermissions.isEmpty()) {
        return true;
      }
      return permissions.containsAll(requiredPermissions);
    }
  }
}