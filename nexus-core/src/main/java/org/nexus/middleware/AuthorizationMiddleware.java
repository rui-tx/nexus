//package org.nexus.middleware;
//
//import java.util.Map;
//import org.nexus.RequestContext;
//import org.nexus.SecurityResolver;
//import org.nexus.SecurityRule;
//import org.nexus.enums.ProblemDetailsTypes;
//import org.nexus.exceptions.ProblemDetailsException;
//import org.nexus.interfaces.Middleware;
//import org.nexus.interfaces.MiddlewareChain;
//import org.nexus.interfaces.ProblemDetails;
//
//public class AuthorizationMiddleware implements Middleware {
//
//  @Override
//  public void handle(RequestContext ctx, MiddlewareChain chain) throws Exception {
//    String method = ctx.getRequest().method().name();
//    String path = ctx.getPath(); // you should have this helper
//
//    SecurityRule rule = SecurityResolver.getRule(method, path);
//    if (rule == null || rule.permitAll()) {
//      chain.next(ctx);
//      return;
//    }
//
//    AuthenticatedUser user = ctx.getUser();
//
//    if (user == null || !user.isAuthenticated()) {
//      throw unauthorized("Bearer token required");
//    }
//
//    if (!user.hasAllRoles(rule.requiredRoles())) {
//      throw forbidden("Missing required role");
//    }
//
//    if (!user.hasAllPermissions(rule.requiredPermissions())) {
//      throw forbidden("Missing required permission");
//    }
//
//    chain.next(ctx);
//  }
//
//  private RuntimeException unauthorized(String message) {
//    throw new ProblemDetailsException(new ProblemDetails.Single(
//        ProblemDetailsTypes.SECURITY_ERROR,
//        "Unauthorized",
//        401,
//        message,
//        null,
//        Map.of()
//    ));
//  }
//
//  private RuntimeException forbidden(String message) {
//    throw new ProblemDetailsException(new ProblemDetails.Single(
//        ProblemDetailsTypes.SECURITY_ERROR,
//        "Forbidden",
//        403,
//        message,
//        null,
//        Map.of()
//    ));
//  }
//}