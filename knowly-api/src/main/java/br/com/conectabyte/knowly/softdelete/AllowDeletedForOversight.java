package br.com.conectabyte.knowly.softdelete;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a {@code @Transactional} method whose query needs deliberate, narrow visibility into
 * soft-deleted rows for one of the 13 entities covered by {@link SoftDeleteFilterAspect} (e.g. a
 * staff oversight query or a future restore/reactivate flow -- see
 * specify/features/soft-delete-default-filter/SPEC.md requirement 7).
 *
 * <p>This only widens what {@link SoftDeleteFilterAspect} lets the query see for the duration of
 * that one method -- it never substitutes for an authorization check, which the annotated method
 * must still perform itself. This is the single, aspect-managed extension point for this kind of
 * read; a manual {@code Session.disableFilter} call anywhere else is disallowed, mirroring {@link
 * br.com.conectabyte.knowly.tenancy.BypassTenantFilterForOversight}'s exact contract for the tenant
 * filter.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface AllowDeletedForOversight {}
