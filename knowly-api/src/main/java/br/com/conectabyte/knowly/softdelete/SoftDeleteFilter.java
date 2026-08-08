package br.com.conectabyte.knowly.softdelete;

/**
 * Name of the Hibernate {@code @Filter} that excludes soft-deleted rows ({@code deleted_at is not
 * null}) from standard JPA entity-load-time queries, mirroring {@link
 * br.com.conectabyte.knowly.tenancy.TenantFilter}'s role for tenant isolation. Unlike {@code
 * TenantFilter}, this filter's condition is static (no per-request parameter, no fail-closed
 * sentinel needed) -- see {@link SoftDeleteFilterAspect}.
 */
public final class SoftDeleteFilter {

    public static final String NAME = "softDeleteFilter";

    private SoftDeleteFilter() {}
}
