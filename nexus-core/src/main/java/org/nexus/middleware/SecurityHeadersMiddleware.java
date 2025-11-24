package org.nexus.middleware;

import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpHeaders;
import org.nexus.RequestContext;
import org.nexus.interfaces.Middleware;
import org.nexus.interfaces.MiddlewareChain;

public class SecurityHeadersMiddleware implements Middleware {

  private final boolean https;
  private final String cspPolicy;
  private final String permissionsPolicy;

  public SecurityHeadersMiddleware(boolean https) {
    this(https,
        "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; font-src 'self'; connect-src 'self'; frame-ancestors 'none'",
        "geolocation=(), microphone=(), camera=(), payment=()"
    );
  }

  public SecurityHeadersMiddleware(boolean https, String cspPolicy, String permissionsPolicy) {
    this.https = https;
    this.cspPolicy = cspPolicy != null ? cspPolicy : "";
    this.permissionsPolicy = permissionsPolicy != null ? permissionsPolicy : "";
  }

  @Override
  public void handle(RequestContext ctx, MiddlewareChain chain) throws Exception {
    HttpHeaders headers = ctx.getRequestHeaders();
    if (headers == null) {
      headers = new DefaultHttpHeaders();
    }

    // Always add these
    headers.set("X-Content-Type-Options", "nosniff");
    headers.set("X-Frame-Options", "DENY");
    headers.set("X-XSS-Protection", "1; mode=block");
    headers.set("Referrer-Policy", "strict-origin-when-cross-origin");

    if (!cspPolicy.isEmpty()) {
      headers.set("Content-Security-Policy", cspPolicy);
    }
    if (!permissionsPolicy.isEmpty()) {
      headers.set("Permissions-Policy", permissionsPolicy);
    }

    if (https) {
      headers.set("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
    }

    chain.next(ctx);
  }
}
