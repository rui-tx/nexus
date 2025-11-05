package org.nexus.middleware;

import io.netty.handler.codec.http.FullHttpRequest;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import nexus.generated.GeneratedSecurityRules;
import org.nexus.RequestContext;
import org.nexus.SecurityRule;
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
  private static final Set<String> EMPTY_SET = Collections.emptySet();

  @Override
  public void handle(RequestContext ctx, MiddlewareChain chain) throws Exception {
    FullHttpRequest request = ctx.getRequest();
    String method = request.method().name().toUpperCase();
    String uri = request.uri();
    int qIndex = uri.indexOf('?');
    String path = (qIndex < 0) ? uri : uri.substring(0, qIndex);

    SecurityRule rule = GeneratedSecurityRules.getRule(method, path);
    if (rule == null || rule.permitAll()) {
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

    // ctx.setAttribute("user", authResult.getUser());  // If needed

    chain.next(ctx);
  }

  private AuthResult validateAuth(FullHttpRequest request, SecurityRule rule) {
    String authHeader = request.headers().get(AUTH_HEADER);
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      return new AuthResult(false, null, EMPTY_SET, EMPTY_SET);
    }

    String token = authHeader.substring(7);
    if (!"valid".equals(token)) {
      return new AuthResult(false, null, EMPTY_SET, EMPTY_SET);
    }

    Set<String> userRoles = Set.of("admin");  // Reuse if static
    Set<String> userPermissions = Set.of("read", "write");

    return new AuthResult(true, "user-id", userRoles, userPermissions);
  }

  private void throwSecurityException(String title, String message, int status, String path) {
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
      return requiredRoles.isEmpty() || roles.containsAll(requiredRoles);
    }

    public boolean hasRequiredPermissions(Set<String> requiredPermissions) {
      return requiredPermissions.isEmpty() || permissions.containsAll(requiredPermissions);
    }
  }
}