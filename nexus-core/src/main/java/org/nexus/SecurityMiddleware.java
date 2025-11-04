package org.nexus;

import io.netty.handler.codec.http.FullHttpRequest;
import java.util.Set;
import nexus.generated.GeneratedSecurityRules;
import org.nexus.interfaces.Middleware;
import org.nexus.interfaces.MiddlewareChain;
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

    // Perform authentication and authorization checks
    try {
      AuthResult authResult = validateAuth(request, rule);
      if (!authResult.isAuthenticated()) {
        throw new SecurityException("Unauthorized: Invalid or missing credentials");
      }
      if (!authResult.hasRequiredRoles(rule.requiredRoles()) || !authResult.hasRequiredPermissions(
          rule.requiredPermissions())) {
        throw new SecurityException("Forbidden: Insufficient privileges");
      }

      // If needed, attach user info to context for downstream use
      // e.g., ctx.setAttribute("user", authResult.getUser());

      chain.next(ctx);
    } catch (Exception e) {
      LOGGER.warn("Security check failed for {} {}: {}", method, path, e.getMessage());
      ctx.complete(null, e);
      throw e;
    }
  }

  /**
   * Placeholder for authentication validation. Implement actual logic here, e.g., JWT parsing, API
   * key check, etc.
   */
  private AuthResult validateAuth(FullHttpRequest request, SecurityRule rule) {
    String authHeader = request.headers().get(AUTH_HEADER);
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      return new AuthResult(false, null, Set.of(), Set.of());
    }

    // Example: Extract token and validate (dummy implementation)
    String token = authHeader.substring(7);
    if (!"valid-token".equals(token)) {  // Replace with real validation
      return new AuthResult(false, null, Set.of(), Set.of());
    }

    // Dummy user data: In real impl, decode token to get roles/permissions
    Set<String> userRoles = Set.of("admin");  // From token claims
    Set<String> userPermissions = Set.of("read", "write");

    return new AuthResult(true, "user-id", userRoles, userPermissions);
  }

  // Helper class for auth results
  private static class AuthResult {

    private final boolean authenticated;
    private final String userId;
    private final Set<String> roles;
    private final Set<String> permissions;

    public AuthResult(boolean authenticated, String userId, Set<String> roles,
        Set<String> permissions) {
      this.authenticated = authenticated;
      this.userId = userId;
      this.roles = roles;
      this.permissions = permissions;
    }

    public boolean isAuthenticated() {
      return authenticated;
    }

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