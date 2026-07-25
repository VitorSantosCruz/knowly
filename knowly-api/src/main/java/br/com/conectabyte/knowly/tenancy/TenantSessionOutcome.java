package br.com.conectabyte.knowly.tenancy;

/**
 * What a login should do with the session's tenant-selection state, decided by how many active
 * memberships the logging-in user holds (REQ-3/4/5).
 */
public sealed interface TenantSessionOutcome {

    record Staff() implements TenantSessionOutcome {}

    record AutoSelected(Long tenantId) implements TenantSessionOutcome {}

    record SelectionPending() implements TenantSessionOutcome {}
}
