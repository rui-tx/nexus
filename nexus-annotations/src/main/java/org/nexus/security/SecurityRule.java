package org.nexus.security;

import java.util.Set;

/**
 * Immutable record representing a security rule for an endpoint. Generated at compile time by
 * SecurityProcessor.
 */
public record SecurityRule(
    String httpMethod,
    String path,
    String className,
    String methodName,
    boolean permitAll,
    Set<String> requiredRoles,
    Set<String> requiredPermissions
) {

  /**
   * Creates a key for looking up security rules.
   */
  public static String createKey(String httpMethod, String path) {
    return httpMethod.toUpperCase() + ":" + normalizePath(path);
  }

  private static String normalizePath(String path) {
    // Ensure path starts with a slash and doesn't end with one (except root)
    String normalized = path.startsWith("/") ? path : "/" + path;
    return normalized.length() > 1 && normalized.endsWith("/") ?
        normalized.substring(0, normalized.length() - 1) : normalized;
  }

  /**
   * Checks if the given roles and permissions satisfy this security rule.
   */
  public boolean isPermitted(Set<String> userRoles, Set<String> userPermissions) {
    // Permit if endpoint is public
    if (permitAll) {
      return true;
    }

    // Check if user has any of the required roles (if any are specified)
    boolean hasRequiredRole = requiredRoles.isEmpty() ||
        userRoles.stream().anyMatch(requiredRoles::contains);

    // Check if user has all required permissions (if any are specified)
    boolean hasRequiredPermissions = requiredPermissions.isEmpty() ||
        userPermissions.containsAll(requiredPermissions);

    return hasRequiredRole && hasRequiredPermissions;
  }
}