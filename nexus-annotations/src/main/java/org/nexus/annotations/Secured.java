// Secured.java
package org.nexus.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for securing endpoints with role-based and permission-based access control.
 * Can be applied to methods or classes.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Secured {
    /**
     * Roles that are allowed to access the endpoint.
     * If empty, no specific role is required (but authentication may still be required).
     */
    String[] value() default {};

    /**
     * Permissions required to access the endpoint.
     * Common permissions include "R" for read and "W" for write.
     * If empty, no specific permission is required.
     */
    String[] permissions() default {};

    /**
     * If true, allows access without authentication.
     * Takes precedence over roles and permissions.
     */
    boolean permitAll() default false;
}