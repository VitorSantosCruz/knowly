package br.com.conectabyte.knowly.audit;

import br.com.conectabyte.knowly.tenancy.GlobalPermission;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Denies the call unless the caller (a {@code STAFF} user) has been granted the given global
 * permission (directly or via a global access group). {@code STAFF_ADMIN} always passes.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RequiresGlobalPermission {

    GlobalPermission value();
}
