package org.nexus.middleware;

import org.nexus.RequestContext;
import org.nexus.interfaces.Middleware;
import org.nexus.interfaces.MiddlewareChain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SecuritySslMiddleware implements Middleware {

  private static final Logger LOGGER = LoggerFactory.getLogger(SecuritySslMiddleware.class);
  private final boolean isHttps;
  private final boolean enforceHsts;

  public SecuritySslMiddleware(boolean isHttps) {
    this(isHttps, isHttps); // Enable HSTS by default when using HTTPS
  }

  public SecuritySslMiddleware(boolean isHttps, boolean enforceHsts) {
    this.isHttps = isHttps;
    this.enforceHsts = enforceHsts && isHttps; // HSTS only makes sense over HTTPS

    if (this.enforceHsts) {
      LOGGER.info("HSTS enabled - browsers will enforce HTTPS for 1 year");
    }
  }

  @Override
  public void handle(RequestContext ctx, MiddlewareChain chain) throws Exception {
    // Add security headers to response
    var headers = ctx.getRequestHeaders();

    // HSTS - tells browsers to only use HTTPS (only send over HTTPS)
    if (enforceHsts) {
      headers.set(
          "Strict-Transport-Security",
          "max-age=31536000; includeSubDomains" // 1 year, include subdomains
      );
    }

    // Prevent MIME type sniffing
    headers.set("X-Content-Type-Options", "nosniff");

    // Prevent clickjacking
    headers.set("X-Frame-Options", "DENY");

    // XSS protection (legacy, but doesn't hurt)
    headers.set("X-XSS-Protection", "1; mode=block");

    // Content Security Policy - adjust based on your needs
    // This is restrictive - modify for your use case
    headers.set(
        "Content-Security-Policy",
        "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; font-src 'self'; connect-src 'self'; frame-ancestors 'none'"
    );

    // Referrer policy
    headers.set("Referrer-Policy", "strict-origin-when-cross-origin");

    // Permissions policy (formerly Feature-Policy)
    headers.set(
        "Permissions-Policy",
        "geolocation=(), microphone=(), camera=(), payment=()"
    );

    // Continue to next middleware/handler
    chain.next(ctx);
  }
}