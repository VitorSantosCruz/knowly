# TASKS — role-model-refinement

> Atomic, sequential, verifiable tasks derived from PLAN.md.
> Each task should be small enough for a single implementation iteration.
>
> Note: per explicit instruction this session, do **not** run the full
> `./mvnw verify` suite at the end of this feature — that full-suite run
> is deliberately deferred until all other backlog work is done. Each
> task below runs only the specific test class(es) it touches.

## (a) Enum rename + compile fixes (REQ-1, REQ-2, Acceptance Criterion 1)

- [x] 1. In `TenantManagementIntegrationTest.java`, rename every
      `MembershipRole.ADMIN` usage (8 occurrences) to
      `MembershipRole.MEMBER_ADMIN` — this alone will not compile yet
      (Red: the enum constant doesn't exist), which is the expected Red
      state for this rename.
- [x] 2. In `MembershipRole.java`, rename the `ADMIN` constant to
      `MEMBER_ADMIN`. In `TenantService.java`, update both call sites
      (line 146: `new TenantMembership(admin, tenant,
      MembershipRole.ADMIN)`; line 411: `.filter(membership ->
      membership.getRole() == MembershipRole.ADMIN)`) to
      `MEMBER_ADMIN`. Confirm the project compiles
      (`./mvnw compile test-compile`) — Green.
- [x] 3. Run `./mvnw test -Dtest=TenantManagementIntegrationTest` and
      confirm it passes unchanged (this is the existing regression
      coverage for Acceptance Criterion 3 — "all existing tenant-admin-
      gated behavior continues to work identically").

## (b) Migration (REQ-3, Acceptance Criterion 2)

- [x] 4. Create
      `src/main/resources/db/migration/V15__rename_membership_role_admin_to_member_admin.sql`
      exactly as specified in PLAN.md's Data schema section (two
      `UPDATE` statements: `tenant_memberships` and
      `tenant_memberships_aud`, `'ADMIN'` → `'MEMBER_ADMIN'`).
- [x] 5. Run `./mvnw test -Dtest=TenantManagementIntegrationTest` again
      (Testcontainers re-runs every migration, including the new V15,
      from a clean schema) and confirm it still passes — this is the
      evidence V15 applies cleanly and doesn't corrupt freshly-seeded
      `MEMBER_ADMIN` fixture data (see PLAN.md's Testing strategy note
      on why a dedicated pre/post-migration test isn't practical here).

## (c) STAFF ceiling check + tests (REQ-4, REQ-5, REQ-6, REQ-7, REQ-8)

- [x] 6. Write the Red test(s) in a new
      `src/test/java/br/com/conectabyte/knowly/tenancy/StaffServiceCeilingIntegrationTest.java`
      (follow `TenantManagementIntegrationTest`'s
      `@SpringBootTest`/Testcontainers setup) for `getStaffUserDetail`:
      a `STAFF` actor holding every `GlobalPermission` (including
      `STAFF_PERMISSION_MANAGE`) is rejected with
      `PermissionDeniedException` when the target user's `GlobalRole` is
      `STAFF`; the same actor *can* call it when the target has
      `GlobalRole == null`. Confirm Red (test compiles, fails because
      the ceiling doesn't exist yet).
- [x] 7. Implement the minimum code for task 6: add the private
      `enforceStaffCeiling(GlobalRole targetGlobalRole)` helper to
      `StaffService.java` (exact body per PLAN.md) and call it from
      `getStaffUserDetail` right after `requireUser(userId)`; add
      `@AuditLog(action = "staff.user.detail.view", resourceType =
      "User", resourceIdExpression = "#userId")` to `getStaffUserDetail`
      (it currently has none). Run
      `./mvnw test -Dtest=StaffServiceCeilingIntegrationTest` — Green.
- [x] 8. Write the Red test(s) for `grantPermission` and
      `revokePermission` (same actor/target matrix as task 6) in the
      same test class.
- [x] 9. Implement: call `enforceStaffCeiling(user.getGlobalRole())`
      after `requireUser(userId)` in both `grantPermission` and
      `revokePermission`. Run
      `./mvnw test -Dtest=StaffServiceCeilingIntegrationTest` — Green.
- [x] 10. Write the Red test(s) for `assignAccessGroup` and
      `unassignAccessGroup` (same actor/target matrix) in the same test
      class.
- [x] 11. Implement: call `enforceStaffCeiling(user.getGlobalRole())`
      after `requireUser(userId)` in both `assignAccessGroup` and
      `unassignAccessGroup`. Run
      `./mvnw test -Dtest=StaffServiceCeilingIntegrationTest` — Green.
- [x] 12. Write the Red test asserting `STAFF_ADMIN` is unaffected
      (REQ-6, Acceptance Criterion 6): a `STAFF_ADMIN` actor can call
      every one of the five methods above against a `STAFF`/
      `STAFF_ADMIN` target with no ceiling rejection. This should
      already be Green given `enforceStaffCeiling`'s condition only
      fires for `GlobalRole.STAFF` actors — run it to confirm (if Red,
      that's a real regression to fix before continuing).
- [x] 13. Write the Red test asserting every REQ-5 rejection above
      produced an `AuditEvent` row with `outcome = DENIED` (REQ-8,
      Acceptance Criterion 7) — query via `AuditEventRepository` in the
      test, for both a method that already had `@AuditLog`
      (`grantPermission`) and the newly-audited `getStaffUserDetail`.
      Confirm Green (should pass given `AuditLogAspect` already catches
      `PermissionDeniedException` — if Red, investigate whether
      `AuditLogAspect`'s aspect ordering actually wraps
      `enforceStaffCeiling`'s throw before concluding more code is
      needed).

## (d) `createStaffUser` STAFF_ADMIN-only restriction (REQ-4 reduction, SPEC Decision 2)

- [x] 14. Write the Red test in `StaffServiceCeilingIntegrationTest`: a
      `STAFF` actor granted `GlobalPermission.STAFF_USER_CREATE` (and,
      separately, one granted every permission) is rejected with
      `PermissionDeniedException` when calling `createStaffUser`; a
      `STAFF_ADMIN` actor can still call it successfully (regression
      check).
- [x] 15. Implement: add `enforceStaffCeiling(GlobalRole.STAFF)` as the
      first line of `createStaffUser`, before the existing
      `email`-already-exists check. Run
      `./mvnw test -Dtest=StaffServiceCeilingIntegrationTest` — Green.

## (e) Targeted verification and handoff

- [x] 16. Run `./mvnw spotless:apply` to format all changed files.
- [x] 17. Run, explicitly and only, these test classes (capture the
      real exit code directly, never through a pipe to `tail`/`grep` —
      per `DECISIONS.md`'s entry on this):
      `./mvnw test -Dtest=TenantManagementIntegrationTest,StaffServiceCeilingIntegrationTest,PermissionAspectTest > /tmp/role-model-refinement-tests.log 2>&1; echo $?`
      Confirm exit code `0` and re-check every SPEC.md acceptance
      criterion checkbox against these results one by one (the
      "Analyze" gate from `constitution.md`'s workflow section). Do
      **not** run the full `./mvnw verify` suite — that is explicitly
      deferred this session.
- [ ] 18. Commit the completed feature (Conventional Commits, e.g.
      `refactor(backend): rename MembershipRole.ADMIN to MEMBER_ADMIN
      and add STAFF management ceiling`), referencing REQ-1 through
      REQ-8.
- [x] 19. Hand off to `qa-test-automation` and `appsec` subagents for
      review before this is considered fully done — in particular ask
      `appsec` to double-check `enforceStaffCeiling`'s placement can't
      be bypassed by any `StaffService` call path that doesn't go
      through the six methods listed above (e.g. any future new method
      accepting a target `userId`), and ask `qa-test-automation` to
      confirm the REQ-7 "ceiling doesn't apply to non-staff targets"
      case is exercised for every one of the six methods, not just
      `getStaffUserDetail`.
- [x] 20. Update `PROJECT_STATUS.md` with: this feature done, and the
      flagged frontend follow-up (PLAN.md's contract-change note — any
      `knowly-app/` code comparing a tenant membership role against the
      literal string `"ADMIN"` needs its own SPEC/task to update to
      `"MEMBER_ADMIN"`).
