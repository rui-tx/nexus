package org.nexus.middleware;

import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import org.nexus.GeneratedSecurityRules;
import org.nexus.RequestContext;
import org.nexus.SecurityRule;
import org.nexus.config.jwt.JwtService;
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

  private final JwtService jwtService;
  private final boolean enforceHsts;
  private final boolean isHttps;

  public SecurityMiddleware(boolean isHttps, JwtService jwtService) {
    this.isHttps = isHttps;
    this.enforceHsts = isHttps;
    this.jwtService = jwtService;
  }

  @Override
  public void handle(RequestContext ctx, MiddlewareChain chain) throws Exception {
    FullHttpRequest request = ctx.getRequest();
    String method = request.method().name().toUpperCase();
    String uri = request.uri();
    int qIndex = uri.indexOf('?');
    String path = (qIndex < 0) ? uri : uri.substring(0, qIndex);

    if (isHttps) {
      applySecurityHeaders(ctx);
    }

    // Check authentication and authorization
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

    if (!authResult.hasRequiredRoles(rule.requiredRoles()) ||
        !authResult.hasRequiredPermissions(rule.requiredPermissions())) {
      throwSecurityException("Forbidden", "Insufficient privileges", 403, path);
      return;
    }

    // Store user in context if needed
    // ctx.setAttribute("user", authResult.getUser());

    chain.next(ctx);
  }

  /**
   * Applies security headers to the response.
   *
   * @param ctx The request context
   */
  private void applySecurityHeaders(RequestContext ctx) {
    // Get or create response headers
    HttpHeaders headers = ctx.getRequestHeaders();
    if (headers == null) {
      headers = new DefaultHttpHeaders();
    }

    // HSTS - only send over HTTPS
    if (enforceHsts) {
      headers.add(
          "Strict-Transport-Security",
          "max-age=31536000; includeSubDomains" // 1 year, include subdomains
      );
    }

    // Prevent MIME type sniffing
    headers.add("X-Content-Type-Options", "nosniff");

    // Prevent clickjacking
    headers.add("X-Frame-Options", "DENY");

    // XSS protection
    headers.add("X-XSS-Protection", "1; mode=block");

    // Content Security Policy - TODO: adjust
    headers.add(
        "Content-Security-Policy",
        "default-src 'self'; script-src 'self'; style-src 'self'; " +
            "img-src 'self' data:; font-src 'self'; connect-src 'self'; frame-ancestors 'none'"
    );

    // Referrer policy
    headers.add("Referrer-Policy", "strict-origin-when-cross-origin");

    // Permissions policy (formerly Feature-Policy) TODO: adjust
    headers.add(
        "Permissions-Policy",
        "geolocation=(), microphone=(), camera=(), payment=()"
    );
  }

  private AuthResult validateAuth(FullHttpRequest request, SecurityRule rule) {
    String authHeader = request.headers().get(AUTH_HEADER);
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      return new AuthResult(false, null, EMPTY_SET, EMPTY_SET);
    }

    String token = authHeader.substring(7);
    boolean isRefreshEndpoint = request.uri().endsWith("/refresh");

    // Validate token type based on endpoint
    boolean isValid = isRefreshEndpoint ?
        jwtService.validateRefreshToken(token) :
        jwtService.validateAccessToken(token);

    if (!isValid) {
      return new AuthResult(false, null, EMPTY_SET, EMPTY_SET);
    }

    try {
      String username = jwtService.getSubjectFromToken(token, isRefreshEndpoint);
      // Extract roles and permissions from token claims
      // For now, using default values
      Set<String> userRoles = Set.of("user");
      Set<String> userPermissions = Set.of("read");

      return new AuthResult(true, username, userRoles, userPermissions);
    } catch (Exception e) {
      LOGGER.warn("Error processing JWT token", e);
      return new AuthResult(false, null, EMPTY_SET, EMPTY_SET);
    }
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