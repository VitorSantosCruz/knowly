# TASKS — tenant-crud

> Atomic, sequential, verifiable tasks derived from PLAN.md.
> Each task should be small enough for a single implementation iteration.

- [x] 0. **Blocking prerequisite check**: confirm `tenant-creation` and
      `permission-granularity-model` PLANs are both fully implemented and
      merged (finalized `Tenant` field names, `GlobalPermission.TENANT_VIEW`/
      `TENANT_EDIT`/`TENANT_DELETE` with `viewDependency()` wired). Confirm
      the actual next-free Flyway migration number on disk (do not assume
      `V24`). If either prerequisite is not yet landed, stop and hand this
      feature back to the orchestrator rather than proceeding.
- [x] 1. Write the migration test asserting `deleted_at` doesn't yet exist /
      the old unconditional `ux_tenants_tax_id` index is unconditional
      (Red — proves the "before" state the migration must change).
- [x] 2. Write `V<n>__add_tenant_soft_delete.sql` (adds `deleted_at` to
      `tenants`/`tenants_aud`, replaces `ux_tenants_tax_id` with the
      partial `WHERE deleted_at IS NULL` version) and confirm task 1's
      test now asserts the "after" state (Green).
- [x] 3. Write `TenantServiceTest` cases for `requireActiveMembership`/
      `requireTenant` rejecting a soft-deleted tenant's id the same way as
      "no access" (REQ-11) (Red).
- [x] 4. Add `Tenant.deletedAt` and the `deletedAt != null` check to
      `requireActiveMembership`/`requireTenant`/`getActiveTenant` (Green).
- [x] 5. Write `TenantServiceTest` cases for `editTenant`: updates only
      supplied fields; a present-but-blank mandatory field is rejected
      with no partial update; `taxId` is not accepted on the DTO at all
      (compile-time/DTO-shape check); editing a soft-deleted or
      nonexistent tenant → `TenantNotFoundException` (Red).
- [x] 6. Implement `EditTenantRequestDto`, `TenantDetailDto` (or widen
      `TenantSummaryDto`), `TenantNotFoundException`, `TenantService#editTenant`
      (`@RequiresGlobalPermission(GlobalPermission.TENANT_EDIT)`,
      `@AuditLog(action = "tenant.edit", ...)`), and `TenancyExceptionHandler`'s
      new 404 mapping (Green).
- [x] 7. Write the integration test (`STAFF_ADMIN`, and `STAFF` with/without
      `TENANT_EDIT`+`TENANT_VIEW`) for `PATCH /api/tenants/{tenantId}`
      covering REQ-1 through REQ-7 and the `MEMBER_ADMIN`-forbidden
      acceptance criterion (Red).
- [x] 8. Implement `TenantController#editTenant` and confirm task 7 is
      green.
- [x] 9. Write `TenantMembershipRepositoryTest`/`TenantServiceTest` cases
      for `deactivateAllByTenant` and `deleteTenant`'s atomicity (tenant
      row + membership cascade commit/rollback together) (Red).
- [x] 10. Implement `TenantMembershipRepository#deactivateAllByTenant`,
      `TenantService#generateTenantDeletionConfirmationToken`
      (`@RequiresGlobalPermission(GlobalPermission.TENANT_DELETE)`), and
      `TenantService#deleteTenant` (`@RequiresGlobalPermission`,
      `@AuditLog(action = "tenant.delete", ...)`, calls
      `deletionConfirmationTokenService.validateAndConsume` with
      resourceType `"tenant"`) (Green).
- [x] 11. Write the integration test for the full delete flow: generate
      token → `DELETE` with word soft-deletes the tenant, cascades
      membership deactivation, leaves `Article`/`Conversation`/
      `AccessGroup`/permission-grant rows untouched (REQ-8 through
      REQ-10); missing/wrong/expired word → 400, tenant untouched;
      `STAFF`/`STAFF_ADMIN` without `TENANT_DELETE`+`TENANT_VIEW` → 403,
      no token generated, no deletion; already-deleted/nonexistent tenant
      → 404; no volume-based rejection (REQ-18) (Red).
- [x] 12. Implement `TenantController`'s new `POST
      /{tenantId}/deletion-confirmation-token` and `DELETE /{tenantId}`
      handlers and confirm task 11 is green.
- [x] 13. Write the integration test for REQ-11: after soft-deletion, a
      member's `switchActiveTenant` and staff's act-as-tenant flow against
      that tenant id both → 403, and an `AuditEvent` with `outcome =
      DENIED` exists for the attempt (Red — should already be green from
      task 4's unit coverage, this confirms it end-to-end through the
      controller/aspect).
- [x] 14. Write the integration test for REQ-12: a new `POST
      /api/tenants` reusing a soft-deleted tenant's `taxId` succeeds
      (Red, should already be green from task 2's migration — confirms
      the constraint from the API layer, not just SQL).
- [x] 15. Run the full `./mvnw verify` and confirm the suite is green.
- [x] 16. **Resolved** (product owner decision, 2026-08-02, SPEC.md
      REQ-19/REQ-20/REQ-21): a soft-deleted tenant leaves the normal
      active listing and surfaces in a separate deactivated listing.
      Write `TenantRepositoryTest`/`TenantServiceTest` cases: `search`
      excludes a soft-deleted tenant once one exists (REQ-19); a new
      `searchDeactivated` returns only soft-deleted tenants (REQ-20)
      (Red).
- [x] 16a. Implement the `TenantRepository#search` `deletedAt IS NULL`
      filter, `TenantRepository#searchDeactivated`,
      `TenantService#listDeactivatedTenants`
      (`@RequiresGlobalPermission(GlobalPermission.TENANT_DELETE)`), and
      widen `TenantSummaryDto` with `deletedAt` (Green).
- [x] 16b. Write the integration test for `GET /api/tenants/deactivated`:
      `STAFF_ADMIN`/`STAFF` with `TENANT_DELETE`+`TENANT_VIEW` → 200 with
      only deactivated tenants, `deletedAt` populated; `STAFF` with only
      `TENANT_ACT_AS_ANY` (no `TENANT_DELETE`) → 403; `GET /api/tenants`
      (existing endpoint) no longer returns a soft-deleted tenant created
      earlier in the test (REQ-19, REQ-21) (Red).
- [x] 16c. Implement `TenantController#listDeactivatedTenants` and
      confirm task 16b is green.
- [x] 17. Update `PLAN.md`/`PROJECT_STATUS.md` with the actual migration
      number used and any other deviation discovered during
      implementation.
