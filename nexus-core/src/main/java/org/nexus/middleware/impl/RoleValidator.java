package org.nexus.middleware.impl;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * A simple token validator for testing purposes. Accepts any token and returns a user with the
 * specified roles and permissions.
 */
public class RoleValidator implements
    Function<String, CompletableFuture<AuthMiddleware.UserPrincipal>> {

  private final String userId;
  private final Set<String> roles;
  private final Set<String> permissions;

  /**
   * Creates a new RoleValidator with default test user.
   */
  public RoleValidator() {
    this("suntzu", Set.of("USER"), Set.of("R"));
  }

  /**
   * Creates a new RoleValidator with custom user details.
   *
   * @param userId      The user ID to return
   * @param roles       The roles to assign to the user
   * @param permissions The permissions to assign to the user
   */
  public RoleValidator(String userId, Set<String> roles, Set<String> permissions) {
    this.userId = userId;
    this.roles = Set.copyOf(roles);
    this.permissions = Set.copyOf(permissions);
  }

  public static RoleValidator adminUser() {
    return new RoleValidator(
        "admin",
        Set.of("ADMIN", "USER"),
        Set.of("R", "W", "ADMIN")
    );
  }

  public static RoleValidator regularUser() {
    return new RoleValidator(
        "user",
        Set.of("USER"),
        Set.of("R")
    );
  }

  @Override
  public CompletableFuture<AuthMiddleware.UserPrincipal> apply(String token) {
    System.out.println("Validating token: " + token);

    // Simulate async operation
    return CompletableFuture.supplyAsync(() ->
        new AuthMiddleware.UserPrincipal(userId, roles, permissions)
    );
  }
}