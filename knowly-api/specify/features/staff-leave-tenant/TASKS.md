# TASKS — staff-leave-tenant (backend)

> Atomic, sequential, verifiable tasks derived from PLAN.md.
> Each task should be small enough for a single implementation iteration.
> All tests land in `br.com.conectabyte.knowly.tenancy.TenantSessionIntegrationTest`
> unless noted otherwise. Run `./mvnw spotless:apply` before each commit.

- [ ] 1. Write the AC1 integration test: staff switches into a tenant via
      `POST /api/tenants/active`, then calls `POST
      /api/tenants/active/clear` (with CSRF cookie/header); assert `200
      OK`, then assert a subsequent `GET /api/tenants` (staff-only,
      global-scope) succeeds and a subsequent `GET
      /api/tenants/permissions` (tenant-scoped) returns `403
      TENANT_ACCESS_DENIED` (Red — endpoint does not exist yet).
- [ ] 2. Implement the minimum code for task 1's test to pass (Green):
      add `clearActiveTenant(HttpServletRequest, HttpServletResponse)`
      to `TenantController`, `@PostMapping("/active/clear")`, staff-only
      check via `tenantContext.isStaff()` (throw
      `TenantAccessDeniedException` otherwise — covered fully by task 5,
      but the branch must exist now), session/authority mutation
      mirroring `switchActiveTenant`'s staff branch
      (`TenantAuthorityFactory.forStaff(...)`, fresh
      `UsernamePasswordAuthenticationToken`,
      `SecurityContextHolder.setContext(...)`,
      `HttpSessionSecurityContextRepository().saveContext(...)`,
      `session.removeAttribute(TenantSessionKeys.ACTIVE_TENANT_ID)`). No
      `@AuditLog` annotation yet (added in task 5) and no CSRF exemption
      added anywhere.
- [ ] 3. Write the AC4 integration test: a staff session with no active
      tenant selected (fresh login, never switched) calls `POST
      /api/tenants/active/clear` with a valid CSRF token; assert `200
      OK` and no exception/500 (Red only if task 2's implementation
      branches on "already clear" — otherwise confirms the existing
      no-branch behavior; write it regardless to lock in REQ-4 as a
      regression guard).
- [ ] 4. Confirm task 3 passes with no code change (Green) — the PLAN's
      no-op decision (no second code path) means task 2's implementation
      already satisfies REQ-4; if the test fails, fix
      `clearActiveTenant` to remove the `ACTIVE_TENANT_ID` attribute
      unconditionally (no-op removal when absent) rather than adding a
      branch.
- [ ] 5. Write the AC3 integration test: staff switches into tenant A,
      then clears; assert (via
      `auditEventRepository.findByActorUserIdOrderByOccurredAtDesc`) the
      most recent event has `action = "tenant.active_tenant.clear"`,
      `outcome = SUCCESS`, `resourceId` equal to tenant A's id (Red —
      no `@AuditLog` annotation/attribute-stash exists yet).
- [ ] 6. Implement the minimum code for task 5's test to pass (Green):
      add the private `static final String PREVIOUS_TENANT_ID_ATTR =
      "clearActiveTenant.previousTenantId"` constant to
      `TenantController`; in `clearActiveTenant`, read the
      previously-active tenant id from the session *before* removing
      `ACTIVE_TENANT_ID`, store it via
      `httpRequest.setAttribute(PREVIOUS_TENANT_ID_ATTR, previousTenantId)`;
      add `@AuditLog(action = "tenant.active_tenant.clear", resourceType
      = "Tenant", resourceIdExpression =
      "#httpRequest.getAttribute('clearActiveTenant.previousTenantId')")`
      to the method.
- [ ] 7. Write the AC2 assertion as an explicit addition to the AC1 test
      (or a dedicated test) confirming `ACTIVE_TENANT_ID` absence is
      exercised via the same `GET /api/tenants/permissions` → 403 check
      already added in task 1 — no new production code expected (Red/Green
      folded into task 1; this task exists to make the AC explicit in
      the test suite rather than only implicit in AC1's assertions).
- [ ] 8. Write the AC5 integration test: a regular tenant member
      (`MEMBER`, real `TenantMembership`) calls `POST
      /api/tenants/active/clear`; assert `403` body contains
      `TENANT_ACCESS_DENIED`, and that a subsequent `GET
      /api/tenants/memberships` still reflects their membership
      unchanged (Red only if the staff-only branch from task 2 is
      broken — otherwise this is a regression-lock; if the branch is
      missing/wrong, fix it now to Green).
- [ ] 9. Confirm/implement task 8's Green state: verify
      `clearActiveTenant` throws `TenantAccessDeniedException` when
      `!tenantContext.isStaff()` before any session/authority mutation
      occurs (no partial state change on the rejected path).
- [ ] 10. Write the AC6 integration test: call `POST
      /api/tenants/active/clear` with no session cookie at all; assert
      `401` (Red only if some misconfiguration bypasses the security
      filter chain — otherwise a regression-lock requiring no new code,
      since 401 is handled by the existing Spring Security filter chain
      per the PLAN).
- [ ] 11. Confirm task 10 passes with no code change (Green) — 401 for
      unauthenticated requests is inherited from the existing security
      filter chain, same as every other authenticated endpoint.
- [ ] 12. Write the CSRF (NFR) integration test: call the endpoint with
      a valid session cookie but no CSRF cookie/header; assert `403`
      (Spring Security's CSRF filter rejection), proving the endpoint
      was not added to `SecurityConfig`'s `ignoringRequestMatchers` list
      (Red only if someone later adds this endpoint to that list —
      otherwise a regression-lock requiring no new code).
- [ ] 13. Confirm task 12 passes with no code change (Green) — explicitly
      verify `SecurityConfig.java` was not touched by this feature (no
      diff expected in that file).
- [ ] 14. Run `./mvnw spotless:apply` then the full `./mvnw verify` and
      confirm the suite is green (all 7 test cases from the PLAN's
      testing strategy passing, plus the full existing suite unaffected).
- [ ] 15. Update `PLAN.md`/`DECISIONS.md` if any decision changed during
      implementation (e.g. if the audit-attribute-stash pattern is
      reused a second time elsewhere and needs promotion to a documented
      convention, per the PLAN's own note flagging that trigger).
- [ ] 16. Commit the completed, verified feature (Conventional Commits).
