# TASKS — user-role-selection-at-creation

> Atomic, sequential, verifiable tasks derived from PLAN.md.

- [ ] 1. Write `AuditLogAspectTest` case for a new `metadataExpression`
      attribute on `@AuditLog` (Red).
- [ ] 2. Add `metadataExpression` to `@AuditLog` and evaluate it in
      `AuditLogAspect`, merging into `AuditEvent.metadata` alongside the
      existing `captureSourceIp` output (Green).
- [ ] 3. Write `StaffServiceTest` cases for REQ-2/REQ-3/REQ-4/REQ-5
      (`createStaffUser` role handling: `STAFF_ADMIN` caller succeeds,
      non-`STAFF_ADMIN` caller rejected regardless of granted
      permissions, default/`STAFF` unchanged, no floor/ceiling check)
      (Red).
- [ ] 4. Add optional `GlobalRole role` to `CreateStaffUserRequestDto`,
      update `StaffController`/`StaffService.createStaffUser` signature,
      add `requireCallerIsStaffAdmin()`, wire the REQ-3 check, add
      `metadataExpression = "#role"` to its `@AuditLog` (Green).
- [ ] 5. Write `TenantServiceTest` cases for REQ-7/REQ-8/REQ-9/REQ-10
      (`addMember` role handling: `STAFF_ADMIN`/tenant `MEMBER_ADMIN`
      caller succeeds for `role=MEMBER_ADMIN`, plain `MEMBER` and
      cross-tenant `MEMBER_ADMIN` callers rejected, default/`MEMBER`
      unchanged, no floor/ceiling check) (Red).
- [ ] 6. Relax `AddMemberRequestDto.role` from `@NotNull` to optional,
      update `TenantService.addMember` to default to `MEMBER` and add
      `requireCallerIsAdminOfTenant()`, wire the REQ-8 check, add
      `metadataExpression = "#role"` to its `@AuditLog` (Green).
- [ ] 7. Update/extend `TenantManagementIntegrationTest` and the
      staff-user-provisioning integration test for the end-to-end
      authorized/rejected paths on both endpoints, including the audit
      metadata assertion.
- [ ] 8. Audit existing tests that called `addMember`/`AddMemberRequestDto`
      assuming `role` was mandatory; confirm none silently broke by the
      `@NotNull` relaxation (regression pass for REQ-9's "unchanged
      default").
- [ ] 9. Run `./mvnw spotless:apply` then the full `./mvnw verify` and
      confirm the suite is green.
- [ ] 10. Update `PLAN.md`/`PROJECT_STATUS.md` if any decision changed
       during implementation, and flag to the product owner/orchestrator
       that `mandatory-complete-profile`'s PLAN/TASKS are still needed
       to fully close out `staff-user-provisioning`'s and `tenancy`'s
       2026-08-02 amendments (this feature's scope stops at role
       selection, per PLAN.md's explicit note).
