package br.com.conectabyte.knowly.tenancy;

/**
 * What a login should do with the session's tenant-selection state, decided by how many active
 * memberships the logging-in user holds (REQ-3/4/5).
 */
public sealed interface TenantSessionOutcome {

    /**
     * {@code pendingProfileCompletion} (REQ-5): computed once at login time via {@code
     * ProfileCompletenessService.isComplete(user)}, at the same point {@code isAnyStaff(user)} is
     * already checked -- mirrors {@code staff-rbac-split}'s {@code isStaffAccount} precedent
     * (compute once at the point of truth, pass straight through).
     */
    record Staff(boolean pendingProfileCompletion) implements TenantSessionOutcome {}

    record AutoSelected(Long tenantId) implements TenantSessionOutcome {}

    record SelectionPending() implements TenantSessionOutcome {}
}
