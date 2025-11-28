package org.nexus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.nexus.domain.SecurityRule;

class SecurityRuleTest {

  @Test
  void permitAll_shouldAllowEveryone() {
    SecurityRule rule = new SecurityRule(
        "UserController", "getUser", "GET", "/users/{id}",
        true,                                 // permitAll = true
        Set.of("ADMIN"),
        Set.of("user:read")
    );

    boolean allowed = rule.isPermitted(Set.of(), Set.of());
    assertTrue(allowed, "permitAll = true should allow access even with no roles/permissions");
  }

  @Test
  void emptyRolesAndPermissions_shouldAllowEveryone_whenNotPermitAll() {
    SecurityRule rule = new SecurityRule(
        "UserController", "listUsers", "GET", "/users",
        false,
        Set.of(),          // no required roles
        Set.of()           // no required permissions
    );

    assertTrue(rule.isPermitted(Set.of(), Set.of()),
        "No required roles or permissions → everyone allowed");
    assertTrue(rule.isPermitted(Set.of("USER"), Set.of("something")),
        "Even users with extra roles/permissions should be allowed");
  }

  @Test
  void requiresRole_userHasIt_shouldAllow() {
    SecurityRule rule = new SecurityRule(
        "AdminController", "deleteUser", "DELETE", "/users/{id}",
        false,
        Set.of("ADMIN", "MODERATOR"),
        Set.of()
    );

    assertTrue(rule.isPermitted(Set.of("ADMIN"), Set.of()));
    assertTrue(rule.isPermitted(Set.of("MODERATOR", "USER"), Set.of()));
  }

  @Test
  void requiresRole_userDoesNotHaveIt_shouldDeny() {
    SecurityRule rule = new SecurityRule(
        "AdminController", "deleteUser", "DELETE", "/users/{id}",
        false,
        Set.of("ADMIN", "MODERATOR"),
        Set.of()
    );

    assertFalse(rule.isPermitted(Set.of("USER"), Set.of()));
    assertFalse(rule.isPermitted(Set.of(), Set.of()));
  }

  @Test
  void requiresPermission_userHasAll_shouldAllow() {
    SecurityRule rule = new SecurityRule(
        "UserController", "updateUser", "PUT", "/users/{id}",
        false,
        Set.of(),
        Set.of("user:write", "profile:edit")
    );

    assertTrue(rule.isPermitted(Set.of(), Set.of("user:write", "profile:edit", "something:else")));
    assertTrue(rule.isPermitted(Set.of("USER"), Set.of("user:write", "profile:edit")));
  }

  @Test
  void requiresPermission_userMissingOne_shouldDeny() {
    SecurityRule rule = new SecurityRule(
        "UserController", "updateUser", "PUT", "/users/{id}",
        false,
        Set.of(),
        Set.of("user:write", "profile:edit")
    );

    assertFalse(rule.isPermitted(Set.of(), Set.of("user:write")));
    assertFalse(rule.isPermitted(Set.of(), Set.of("profile:edit")));
    assertFalse(rule.isPermitted(Set.of(), Set.of("completely:wrong")));
  }

  @Test
  void requiresBothRoleAndPermissions_userHasBoth_shouldAllow() {
    SecurityRule rule = new SecurityRule(
        "AdminController", "banUser", "POST", "/users/{id}/ban",
        false,
        Set.of("ADMIN"),
        Set.of("user:ban")
    );

    assertTrue(rule.isPermitted(
        Set.of("ADMIN", "MODERATOR"),
        Set.of("user:ban", "user:read")
    ));
  }

  @Test
  void requiresBothRoleAndPermissions_missingRole_shouldDeny() {
    SecurityRule rule = new SecurityRule(
        "AdminController", "banUser", "POST", "/users/{id}/ban",
        false,
        Set.of("ADMIN"),
        Set.of("user:ban")
    );

    assertFalse(rule.isPermitted(
        Set.of("MODERATOR"),
        Set.of("user:ban", "user:read")
    ));
  }

  @Test
  void requiresBothRoleAndPermissions_missingPermission_shouldDeny() {
    SecurityRule rule = new SecurityRule(
        "AdminController", "banUser", "POST", "/users/{id}/ban",
        false,
        Set.of("ADMIN"),
        Set.of("user:ban")
    );

    assertFalse(rule.isPermitted(
        Set.of("ADMIN"),
        Set.of("user:read")
    ));
  }

  @Test
  void createKey_shouldProduceCorrectFormat() {
    String key = SecurityRule.createKey("com.example.UserController", "getById");
    assertEquals("com.example.UserController.getById", key);
  }

  @Test
  void recordEquality_shouldWorkAsExpected() {
    SecurityRule a = new SecurityRule(
        "C", "m", "GET", "/x", false,
        Set.of("A"), Set.of("p1")
    );
    SecurityRule b = new SecurityRule(
        "C", "m", "GET", "/x", false,
        Set.of("A"), Set.of("p1")
    );

    assertEquals(a, b);
    assertEquals(a.hashCode(), b.hashCode());
  }

  @Test
  void shouldHandleNullCollectionsGracefully() {
    SecurityRule rule = new SecurityRule(
        "Test", "method", "GET", "/test",
        false, null, null
    );

    assertTrue(rule.requiredRoles().isEmpty());
    assertTrue(rule.requiredPermissions().isEmpty());
    assertTrue(rule.isPermitted(Set.of("ANY"), Set.of("ANY"))); // empty requirements → allow all
    assertTrue(rule.isPermitted(null, null));
  }
}
