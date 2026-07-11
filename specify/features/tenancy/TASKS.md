# TASKS — Multi-tenant authorization

> Atomic, sequential, verifiable tasks derived from PLAN.md.
> Each task should be small enough for a single implementation iteration.
> Every "Implement" task ends with `./mvnw spotless:apply && ./mvnw verify`
> and a small Conventional Commit before moving on.

## 0. Foundations

- [x] 1. Add `spring-boot-starter-aspectj` to `pom.xml` (Spring Boot 4
      renamed `spring-boot-starter-aop`).
- [ ] 2. `V3__create_tenancy_tables.sql` — all tables from PLAN.md's
      Data Schema section, plus the `users.global_role` column.
- [ ] 3. `V4__create_tenancy_envers_audit_tables.sql` — Envers mirror
      tables for every `@Audited` entity below, plus `users_aud.global_role`.
- [ ] 4. `V5__create_audit_events_table.sql`.

## 1. Core entities (REQ-1, REQ-2, REQ-3)

- [ ] 5. Test: `Tenant` persists and round-trips via
      `TenantRepository` (Red).
- [ ] 6. Implement `Tenant`, `TenantRepository` (Green).
- [ ] 7. Test: `GlobalRole.STAFF` on `User` persists; a `User` with
      `globalRole = null` is unaffected (Red).
- [ ] 8. Implement `GlobalRole` enum + `User.globalRole` field (Green).
- [ ] 9. Test: `TenantMembership` enforces the unique `(user_id,
      tenant_id)` constraint; inserting a second row for the same pair
      throws (Red).
- [ ] 10. Implement `TenantMembership` (+ `MembershipRole` enum),
       `TenantMembershipRepository` (Green).
- [ ] 11. Test: removing a membership sets `active = false` and the row
       still exists (soft delete, REQ-19) (Red).
- [ ] 12. Implement `TenantService.removeMember` performing the soft
       delete (Green).
- [ ] 13. Test: re-adding a previously removed member reactivates the
       same row (`active = true`) instead of inserting a duplicate
       (REQ-19) (Red).
- [ ] 14. Implement `TenantService.addMember` reactivation path
       (Green).

## 2. Permission model (REQ-12, REQ-13, REQ-15, REQ-18)

- [ ] 15. Implement `Permission` enum with an initial placeholder
       constant (e.g. `TENANT_MEMBER_MANAGE`) — no test needed, it's a
       plain enum; real constants are added as consuming features need
       them.
- [ ] 16. Implement `AccessGroup`, `AccessGroupPermission`,
       `DirectPermissionGrant`, `UserAccessGroup` entities + repositories
       (no behavior yet, just persistence — covered indirectly by task
       17's test).
- [ ] 17. Test: `PermissionService.effectivePermissions(membership)`
       returns the union of direct grants and every group's grants, with
       no duplicates when a permission is both directly granted and
       granted via a group (REQ-15) (Red).
- [ ] 18. Implement `PermissionService.effectivePermissions` (Green).
- [ ] 19. Test: granting/revoking a direct permission or an access-group
       assignment is immediately visible in the next
       `effectivePermissions` call — no caching staleness (REQ-14)
       (Red — should already pass if task 18 has no cache; write it
       anyway as a regression guard).
- [ ] 20. (Green — likely no code change; commit the passing test.)

## 3. Enforcement aspects (REQ-8, REQ-12, REQ-17, REQ-18, REQ-20)

- [ ] 21. Test: a test-only service method annotated
       `@RequiresPermission(SOME_PERMISSION)` throws
       `PermissionDeniedException` when the caller's active membership
       lacks the permission (Red).
- [ ] 22. Implement `RequiresPermission` annotation +
       `PermissionAspect` (Green).
- [ ] 23. Test: the same annotated method succeeds when the permission
       is granted directly, and separately when granted only via an
       access group (REQ-15 interaction) (Red).
- [ ] 24. (Green — should already pass from task 22; commit as
       regression coverage.)
- [ ] 25. Test: a `globalRole = STAFF` caller passes the same
       `@RequiresPermission` check regardless of tenant context (REQ-8)
       (Red).
- [ ] 26. Implement the staff bypass in `PermissionAspect` (Green).
- [ ] 27. Test: a test-only method annotated `@AuditLog(action=...,
       resourceType=...)` writes one `AuditEvent` row with the right
       actor/tenant/action/outcome on success (Red).
- [ ] 28. Implement `AuditEvent`, `AuditOutcome`, `AuditEventRepository`
       (no update/delete methods), `AuditLog` annotation,
       `AuditLogAspect` (Green).
- [ ] 29. Test: the same annotated method, when it throws, still writes
       an `AuditEvent` row with `outcome = ERROR` (Red).
- [ ] 30. Implement the failure path in `AuditLogAspect` (Green).
- [ ] 31. Test: a **read-only** annotated method (no state change at
       all) still produces an `AuditEvent` row (REQ-20's whole reason
       for existing) (Red).
- [ ] 32. (Green — should already pass from task 28's generic
       around-advice; commit as regression coverage.)

## 4. Tenant data isolation (REQ-8, NFR "fails closed")

- [ ] 33. Test (Testcontainers): with the Hibernate `tenantFilter`
       enabled for tenant A, a query against a `@Filter`-annotated
       entity never returns tenant B's rows, even via a raw repository
       method with no manual `WHERE tenant_id` (Red).
- [ ] 34. Implement the `@FilterDef`/`@Filter` on tenant-scoped
       entities + a `HibernateFilterConfig` (or equivalent) that exposes
       an "enable for tenant X" hook (Green).
- [ ] 35. Test: with no active tenant enabled (filter left at the
       sentinel value), the same query returns zero rows rather than
       erroring or returning everything (fail closed) (Red).
- [ ] 36. Implement `TenantContext` (`@RequestScope`) +
       `TenantContextFilter` that enables the Hibernate filter per
       request from the session's active tenant, defaulting to the
       sentinel when none is set (Green).

## 5. Login and active-tenant session mechanics (REQ-4, REQ-5, REQ-6, REQ-7)

- [ ] 37. Test: login for a user with exactly one membership
       auto-selects that tenant as active (REQ-4) (Red).
- [ ] 38. Implement the auto-select branch in
       `AuthController.establishSession` (Green).
- [ ] 39. Test: login for a user with more than one membership leaves
       the session pending-selection, and a tenant-scoped endpoint hit
       before selection returns `409 TENANT_SELECTION_REQUIRED` (REQ-5)
       (Red).
- [ ] 40. Implement the pending-selection branch + the
       `TenantContextFilter` rejection (Green).
- [ ] 41. Test: `POST /api/tenants/active` with a tenant the caller is
       a member of updates the session and the *same* session cookie
       continues to work — no new login (REQ-6) (Red).
- [ ] 42. Implement `TenantController.setActiveTenant` +
       authority-refresh on the existing `SecurityContext` (Green).
- [ ] 43. Test: `POST /api/tenants/active` with a tenant the caller is
       **not** a member of returns `403 TENANT_ACCESS_DENIED` and
       produces an audit event (REQ-7) (Red).
- [ ] 44. Implement the rejection + `@AuditLog` on that path (Green).
- [ ] 45. Test: `GET /api/tenants/memberships` returns only the
       caller's own active memberships (Red).
- [ ] 46. Implement the endpoint (Green).

## 6. Tenant and membership management endpoints (REQ-9, REQ-10, REQ-11, REQ-16)

- [ ] 47. Test: `POST /api/tenants` by a staff user creates a tenant
       with its first ADMIN membership atomically; a non-staff caller
       gets `403 PERMISSION_DENIED` (REQ-10) (Red).
- [ ] 48. Implement `TenantService.createTenant` + the endpoint
       (Green).
- [ ] 49. Test: a tenant admin can add/remove/change the role of a
       member in their own tenant, including another admin, but the
       same call against a different tenant is rejected (REQ-9) (Red).
- [ ] 50. Implement `TenantService.addMember`/`removeMember`/
       `changeRole` scoping + endpoints (Green).
- [ ] 51. Test: every membership change (add, remove, role change) and
       every active-tenant switch produces an audit event (REQ-11)
       (Red).
- [ ] 52. Implement `@AuditLog` on all of the above (Green — likely
       already covered if annotations were applied in tasks 48/50;
       write the test first regardless, per TDAD).
- [ ] 53. Test: a plain member calling any grant/revoke/access-group
       endpoint gets `403 PERMISSION_DENIED`; only tenant admin (own
       tenant) or staff succeed (REQ-16) (Red).
- [ ] 54. Implement `@RequiresPermission`/role checks on the
       grant/revoke/access-group endpoints (Green).
- [ ] 55. Test: `POST .../access-groups`, `.../permissions`,
       `.../access-groups/{id}` endpoints work end-to-end, and a grant
       takes effect for the very next request in the same session with
       no re-login (REQ-14 end-to-end) (Red).
- [ ] 56. Implement the remaining endpoints listed in PLAN.md's API
       Contracts section (Green).

## 7. Final verification

- [ ] 57. Run the full `./mvnw spotless:apply && ./mvnw verify` and
       confirm the entire suite (auth + tenancy) is green.
- [ ] 58. Re-read PLAN.md against what was actually built; update it if
       any decision changed during implementation (e.g. endpoint
       shapes, exception codes).
- [ ] 59. Update SPEC.md's acceptance-criteria checkboxes to reflect
       what's now verified by tests.
