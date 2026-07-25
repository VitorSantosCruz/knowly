package br.com.conectabyte.knowly.tenancy;

/**
 * Sentinel used when no tenant is active (pending selection, or staff with no active tenant): no
 * real tenant can ever have this id, so any tenant-scoped query fails closed (returns nothing)
 * instead of erroring or leaking rows.
 */
public final class TenantFilter {

    public static final String NAME = "tenantFilter";
    public static final String PARAMETER = "tenantId";
    public static final long NO_ACTIVE_TENANT_SENTINEL = -1L;

    private TenantFilter() {}
}
