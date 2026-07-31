package br.com.conectabyte.knowly.tenancy;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a {@code @Transactional} method whose query needs cross-tenant read visibility for a narrow
 * oversight look-in (e.g. {@code STAFF_ADMIN}/active-{@code MEMBER_ADMIN} reading a group
 * conversation they aren't a participant of -- see specify/features/internal-team-chat/PLAN.md).
 *
 * <p>This only widens what {@link TenantFilterAspect} lets the query see for the duration of that
 * one method -- it never substitutes for the authorization check, which the annotated method must
 * still perform itself by re-deriving the caller's role/membership from {@link TenantContext}/
 * {@code TenantMembershipRepository}. This is the single, aspect-managed extension point for this
 * kind of cross-tenant read; a manual {@code Session.disableFilter} call anywhere else is
 * disallowed (see PLAN's AppSec-corrected design).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface BypassTenantFilterForOversight {}
