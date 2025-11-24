//package org.nexus.middleware;
//
//import io.netty.handler.codec.http.FullHttpRequest;
//import java.util.Set;
//import org.nexus.RequestContext;
//import org.nexus.config.jwt.JwtService;
//import org.nexus.interfaces.Middleware;
//import org.nexus.interfaces.MiddlewareChain;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
//public class JwtAuthenticationMiddleware implements Middleware {
//
//  private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationMiddleware.class);
//  private static final String AUTH_HEADER = "Authorization";
//  private static final String BEARER_PREFIX = "Bearer ";
//
//  private final JwtService jwtService;
//
//  public JwtAuthenticationMiddleware(JwtService jwtService) {
//    this.jwtService = jwtService;
//  }
//
//  @Override
//  public void handle(RequestContext ctx, MiddlewareChain chain) throws Exception {
//    FullHttpRequest request = ctx.getRequest();
//    String authHeader = request.headers().get(AUTH_HEADER);
//
//    if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
//      ctx.setUser("");
//      chain.next(ctx);
//      return;
//    }
//
//    String token = authHeader.substring(BEARER_PREFIX.length());
//    boolean isRefresh = request.uri().endsWith("/refresh");
//
//    boolean valid = isRefresh
//        ? jwtService.validateRefreshToken(token)
//        : jwtService.validateAccessToken(token);
//
//    if (!valid) {
//      ctx.setUser(null);
//      chain.next(ctx);
//      return;
//    }
//
//    try {
//      String subject = jwtService.getSubjectFromToken(token, isRefresh);
//      Set<String> roles = jwtService.getRolesFromToken(token, isRefresh);
//      Set<String> permissions = jwtService.getPermissionsFromToken(token, isRefresh);
//
//      AuthenticatedUser user = new AuthenticatedUser(subject, roles, permissions);
//      ctx.setUser(user);
//    } catch (Exception e) {
//      log.warn("Failed to parse JWT claims", e);
//      ctx.setUser(null);
//    }
//
//    chain.next(ctx);
//  }
//}
