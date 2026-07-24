package br.com.conectabyte.knowly.tenancy;

/** Names of the HttpSession attributes that carry tenant-selection state. */
public final class TenantSessionKeys {

    public static final String ACTIVE_TENANT_ID = "knowly.tenancy.activeTenantId";
    public static final String STAFF = "knowly.tenancy.staff";
    public static final String SELECTION_PENDING = "knowly.tenancy.selectionPending";

    private TenantSessionKeys() {}
}
