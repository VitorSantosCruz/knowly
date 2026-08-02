# TASKS — staff-rbac-management-operations

> Atomic, sequential, verifiable tasks derived from PLAN.md.
> Each task should be small enough for a single implementation iteration.

## Shared plumbing (needed by everything below)

- [x] 1. Add `LastAdminRemainingException` (409) + exception handler mapping.
- [x] 2. Add `UserRepository.findByGlobalRoleForUpdate` (pessimistic write lock) and
      `TenantMembershipRepository.findByTenantIdAndRoleAndActiveTrueForUpdate`.
- [x] 3. `StaffService.requireCallerIsStaffAdmin()` and
      `TenantService.requireCallerIsAdminOfTenant(User, Long)` — these are
      owned by `user-role-selection-at-creation`'s own TASKS.md (same
      name/signature, per its PLAN.md). If that feature has already landed
      when this task is picked up, confirm both methods exist with this
      exact shape and skip straight to unit tests below; if not, implement
      them here first (so the sibling feature finds them already present
      later — do not implement both independently). Unit tests covering:
      `STAFF_ADMIN` caller passes; `STAFF`-with-permission caller rejected;
      matching-tenant `MEMBER_ADMIN` passes; wrong-tenant `MEMBER_ADMIN`
      rejected; `MEMBER`-with-permission caller rejected.

## Demotion (REQ-1–6, REQ-21–23)

- [x] 4. Test: `STAFF_ADMIN`→`STAFF` demotion succeeds when ≥2 `STAFF_ADMIN`s
      exist, audit event recorded (Red).
- [x] 5. Implement `StaffService.demoteStaffUser` + `POST
      /api/staff/users/{userId}/demote` (Green).
- [x] 6. Test: demotion rejected (409) when target is the last `STAFF_ADMIN`
      (Red).
- [x] 7. Implement the locked last-admin check in `demoteStaffUser` (Green).
- [x] 8. Test: self-demotion rejected regardless of admin count (Red) → Green.
- [x] 9. Test: `STAFF` caller (with an unrelated granted permission) rejected
      from demoting a `STAFF_ADMIN` (Red) → Green.
- [x] 10. Test: two concurrent demote requests against two different
       `STAFF_ADMIN`s, exactly two existing — exactly one succeeds, one 409s
       (Red) → confirm the pessimistic lock makes this Green without further
       code changes; if it doesn't, fix the lock scope.
- [x] 11. Repeat tasks 4–10 for tenant-scope `MEMBER_ADMIN`→`MEMBER`
       demotion (`TenantService.demoteMember`, `POST
       /api/tenants/{tenantId}/members/{membershipId}/demote`), including the
       per-tenant (not platform-wide) floor and cross-tenant isolation.

## Deletion (REQ-7–11)

- [x] 12. Test: `POST /api/staff/users/{userId}/deletion-confirmation-token`
       returns a token for a `STAFF_ADMIN` caller (Red) → Green.
- [x] 13. Test: `DELETE /api/staff/users/{userId}` with a valid token deletes
       the user (and dependent grant/group rows) (Red) → Green
       (`StaffService.deleteStaffUser`).
- [x] 14. Test: deletion rejected without a token / with a wrong one, generic
       error per `deletion-confirmation-token` REQ-7 (Red) → Green.
- [x] 15. Test: deleting the last `STAFF_ADMIN` rejected (409); deleting a
       `STAFF` user never blocked (Red) → Green.
- [x] 16. Test: self-deletion rejected (Red) → Green.
- [x] 17. Repeat tasks 12–16 for tenant-member hard delete (`DELETE
       /api/tenants/{tenantId}/members/{membershipId}/hard-delete`,
       `TenantService.hardDeleteMember`), including the "a lone `MEMBER` is
       never blocked" case.

## Admin-target grant/revoke/assign rejection (REQ-17–19)

- [x] 18. Test: `POST /api/staff/users/{userId}/permissions` targeting a
       `STAFF_ADMIN` returns 403, no `DirectGlobalPermissionGrant` row
       created (Red) → Green.
- [x] 19. Test: `POST/DELETE
       /api/tenants/{tenantId}/members/{membershipId}/permissions[/{permission}]`
       and `assignAccessGroup` (both scopes) targeting a `MEMBER_ADMIN`/
       `STAFF_ADMIN` return 403 (Red) → Green.

## Batch permission update (REQ-12–16)

- [x] 20. Test: `PUT /api/staff/users/{userId}/permissions/batch` with an
       additions-only diff requires and consumes a valid token (Red) →
       Green (`StaffService.batchUpdatePermissions` +
       `BatchPermissionUpdateRequestDto`).
- [x] 21. Test: same endpoint, removals-only diff, also requires a token
       (Red) → Green.
- [x] 22. Test: a no-op batch (identical submitted set) succeeds with no
       token and never calls `validateAndConsume` (Red) → Green.
- [x] 23. Test: one `AuditEvent` per added/removed permission (Red) → Green.
- [x] 24. Test: batch targeting a `STAFF_ADMIN` rejected outright regardless
       of token (Red) → Green.
- [x] 25. Repeat tasks 20–24 for tenant-scope batch update (`PUT
       /api/tenants/{tenantId}/members/{membershipId}/permissions/batch`).

## Promotion (REQ-24–30)

- [x] 26. Test: `STAFF_ADMIN` promotes a `STAFF` user to `STAFF_ADMIN`,
       succeeds regardless of existing admin count (e.g. 5 pre-existing),
       no token/lock interaction, audit event recorded (Red) → Green
       (`StaffService.promoteStaffUser`, `POST
       /api/staff/users/{userId}/promote`).
- [x] 27. Test: `STAFF` caller (with or without a granted permission)
       rejected from promoting anyone to `STAFF_ADMIN` (Red) → Green.
- [x] 28. Test: self-promotion rejected (Red) → Green.
- [x] 29. Repeat tasks 26–28 for tenant-scope `MEMBER`→`MEMBER_ADMIN`
       promotion (`TenantService.promoteMember`, `POST
       /api/tenants/{tenantId}/members/{membershipId}/promote`).

## `isLastAdminOfType` detail-DTO amendment (2026-08-02)

- [x] 32. Add `TenantMembershipRepository.countByTenantIdAndRoleAndActiveTrue`
       (non-locking count). Test: counts only active memberships of the
       given role/tenant, not all active members (Red) → Green.
- [x] 33. Test: `StaffService.getStaffUserDetail` returns `globalRole`
       and `isLastAdminOfType == true` for the sole `STAFF_ADMIN`,
       `false` once a second `STAFF_ADMIN` exists, `false` for a `STAFF`
       target regardless of count (Red) → Green (add `globalRole`/
       `isLastAdminOfType` to `StaffUserDetailDto`, compute via
       `countByGlobalRoleIn`).
- [x] 34. Test: `TenantService.getMemberDetail` returns
       `isLastAdminOfType == true` for the sole `MEMBER_ADMIN` of that
       tenant, `false` once a second exists, `false` for a `MEMBER`
       target, and a lone `MEMBER_ADMIN` in tenant A does not affect
       tenant B's reading (Red) → Green (add `isLastAdminOfType` to
       `MemberDetailDto`, compute via
       `countByTenantIdAndRoleAndActiveTrue`).
- [x] 35. Run `./mvnw verify`; commit
       `feat(staff-rbac-management-operations): expose isLastAdminOfType on detail DTOs`.

## Wrap-up

- [x] 30. Run the full `./mvnw verify` and confirm the suite is green.
- [x] 31. Update `PROJECT_STATUS.md` and `PLAN.md` for any decision that
       changed during implementation (e.g. lock scope, DTO shape).
</content>
