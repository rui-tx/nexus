package org.nexus;

import java.util.Set;

public record SecurityRule(
    String className,
    String methodName,
    String httpMethod,
    String endpoint,
    boolean permitAll,
    Set<String> requiredRoles,
    Set<String> requiredPermissions
) {

  public static String createKey(String className, String methodName) {
    return className + "." + methodName;
  }

  public boolean isPermitted(Set<String> userRoles, Set<String> userPermissions) {
    // Permit if the endpoint is public
    if (permitAll) {
      return true;
    }

    // Check if a user has any of the required roles (if any are specified)
    boolean hasRequiredRole = requiredRoles.isEmpty() ||
        userRoles.stream().anyMatch(requiredRoles::contains);

    // Check if the user has all required permissions (if any are specified)
    boolean hasRequiredPermissions = requiredPermissions.isEmpty() ||
        userPermissions.containsAll(requiredPermissions);

    return hasRequiredRole && hasRequiredPermissions;
  }
}