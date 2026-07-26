package br.com.conectabyte.knowly.tenancy;

/**
 * Additive alongside {@link TenantMembership#isActive()} — see
 * specify/features/tenant-membership-acceptance/PLAN.md's "Architectural decisions" section. {@code
 * active} remains the single source of truth every existing authorization check reads; {@code
 * status} only disambiguates *why* a non-active row is non-active (never yet accepted vs. declined
 * forever), which {@code active} alone can't express.
 */
public enum MembershipStatus {
    PENDING,
    ACTIVE,
    DECLINED
}
