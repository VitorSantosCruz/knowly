package br.com.conectabyte.knowly.audit;

import br.com.conectabyte.knowly.tenancy.Permission;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Denies the call unless the caller's active tenant membership has the given permission (directly
 * or via an access group). Staff (global role) always pass, regardless of tenant context.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RequiresPermission {

    Permission value();
}
