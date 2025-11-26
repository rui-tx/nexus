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

  public SecurityRule {
    requiredRoles = requiredRoles != null ? Set.copyOf(requiredRoles) : Set.of();
    requiredPermissions = requiredPermissions != null ? Set.copyOf(requiredPermissions) : Set.of();

    if (className == null || className.isBlank()) {
      throw new IllegalArgumentException("className must not be null or blank");
    }
    if (methodName == null || methodName.isBlank()) {
      throw new IllegalArgumentException("methodName must not be null or blank");
    }
    if (httpMethod == null || httpMethod.isBlank()) {
      throw new IllegalArgumentException("httpMethod must not be null or blank");
    }
    if (endpoint == null || endpoint.isBlank()) {
      throw new IllegalArgumentException("endpoint must not be null or blank");
    }
  }

  public static String createKey(String className, String methodName) {
    return className + "." + methodName;
  }

  public boolean isPermitted(Set<String> userRoles, Set<String> userPermissions) {
    if (permitAll) {
      return true;
    }

    userRoles = userRoles != null ? userRoles : Set.of();
    userPermissions = userPermissions != null ? userPermissions : Set.of();

    boolean hasRequiredRole = requiredRoles.isEmpty() ||
        userRoles.stream().anyMatch(requiredRoles::contains);

    boolean hasRequiredPermissions = requiredPermissions.isEmpty() ||
        userPermissions.containsAll(requiredPermissions);

    return hasRequiredRole && hasRequiredPermissions;
  }
}