# TASKS — permission-granularity-model

> Atomic, sequential, verifiable tasks derived from PLAN.md.
> Each task should be small enough for a single implementation iteration.

- [x] 1. Write a unit test for `Permission#viewDependency()` enumerating
      every expected pair (`ARTICLE_EDIT`→`ARTICLE_VIEW`,
      `ARTICLE_DELETE`→`ARTICLE_VIEW`) and every other current value
      returning empty (Red).
- [x] 2. Add `viewDependency()` to `Permission` implementing task 1's
      expectations (Green).
- [x] 3. Write a unit test for `GlobalPermission#viewDependency()`
      enumerating `TENANT_EDIT`/`TENANT_DELETE`→`TENANT_VIEW`,
      `STAFF_USER_EDIT`/`STAFF_USER_DELETE`→`STAFF_USER_VIEW`,
      `TENANT_MEMBER_EDIT`/`TENANT_MEMBER_DELETE`→`TENANT_MEMBER_VIEW`,
      `TENANT_ACCESS_GROUP_EDIT`/`TENANT_ACCESS_GROUP_DELETE`→
      `TENANT_ACCESS_GROUP_VIEW`, `TENANT_PERMISSION_GRANT_DELETE`→
      `TENANT_PERMISSION_GRANT_VIEW`, everything else empty (Red — will
      not compile until task 4 adds the new enum values).
- [x] 4. Add the new `GlobalPermission` values (`TENANT_VIEW`,
      `TENANT_EDIT`, `TENANT_DELETE`, `STAFF_USER_EDIT`,
      `STAFF_USER_DELETE`, `TENANT_MEMBER_VIEW`, `TENANT_MEMBER_CREATE`,
      `TENANT_MEMBER_EDIT`, `TENANT_MEMBER_DELETE`,
      `TENANT_ACCESS_GROUP_VIEW`, `TENANT_ACCESS_GROUP_CREATE`,
      `TENANT_ACCESS_GROUP_EDIT`, `TENANT_ACCESS_GROUP_DELETE`,
      `TENANT_PERMISSION_GRANT_VIEW`, `TENANT_PERMISSION_GRANT_CREATE`,
      `TENANT_PERMISSION_GRANT_DELETE`) and `viewDependency()`; do not
      remove the three bundled values yet (Green for task 3 only).
- [x] 5. Write `PermissionAspectTest` cases for REQ-2/REQ-5 on
      `ARTICLE_EDIT`/`ARTICLE_DELETE`: (a) action+view permission → 200,
      (b) action alone → denied, (c) view alone → denied, (d)
      `ARTICLE_CREATE` alone (no view dependency) → unaffected/proceeds
      (Red).
- [x] 6. Implement the dependency check in
      `PermissionAspect.checkPermission` using
      `Permission#viewDependency()` (Green for task 5).
- [x] 7. Write `GlobalPermissionAspectTest` cases mirroring task 5 for
      one representative pair, e.g. `TENANT_MEMBER_DELETE`/
      `TENANT_MEMBER_VIEW` (Red).
- [x] 8. Implement the dependency check in
      `GlobalPermissionAspect.checkGlobalPermission` using
      `GlobalPermission#viewDependency()` (Green for task 7).
- [x] 9. Write a `TenantService` unit/integration test: a `STAFF` user
      granted `TENANT_MEMBER_DELETE` only (no `TENANT_MEMBER_VIEW`) is
      denied `removeMember`; granted both, succeeds (Red — will not pass
      until `requireAdminOfTenantOrStaff` and the call sites are
      updated).
- [x] 10. Update `TenantService#requireAdminOfTenantOrStaff` to also
      check `requiredPermission.viewDependency()` for the `STAFF`
      branch, and update every staff-branch call site
      (`addMember`→`TENANT_MEMBER_CREATE`, `listMembers`→
      `TENANT_MEMBER_VIEW`, `removeMember`/its token-generation sibling→
      `TENANT_MEMBER_DELETE`, `createAccessGroup`→
      `TENANT_ACCESS_GROUP_CREATE`, `listAccessGroups`→
      `TENANT_ACCESS_GROUP_VIEW`, `grantAccessGroupPermission`→
      `TENANT_ACCESS_GROUP_EDIT`, `grantPermission`/`assignAccessGroup`→
      `TENANT_PERMISSION_GRANT_CREATE`, `revokePermission`/
      `unassignAccessGroup`/their token-generation siblings→
      `TENANT_PERMISSION_GRANT_DELETE`, `getMemberDetail`→
      `TENANT_PERMISSION_GRANT_VIEW`) per PLAN.md's table (Green for
      task 9).
- [x] 11. Extend `StaffRbacIntegrationTest` with the remaining
      REQ-9/REQ-10/REQ-11 denial/success pairs not covered by task 9
      (`TENANT_ACCESS_GROUP_EDIT`/`_VIEW`,
      `TENANT_PERMISSION_GRANT_DELETE`/`_VIEW`, and a `_CREATE`-only
      case for each resource proceeding without its view permission per
      REQ-3) (Red then Green — should already pass after task 10 if the
      call-site mapping is correct; task exists to lock in coverage, not
      to drive new production code).
- [x] 12. Write `ArticleControllerIntegrationTest` (or extend the
      existing one) cases: `ARTICLE_EDIT`/`ARTICLE_DELETE` without
      `ARTICLE_VIEW` denied; `ARTICLE_CREATE` alone still succeeds (Red
      then Green — should already pass after task 6).
- [x] 13. Write a `V24MigrationTest` per PLAN.md's testing
      strategy: run migrations only through `V22` in an isolated
      Testcontainers instance, insert bundled-permission rows
      (`TENANT_MEMBER_MANAGE_ANY`, `TENANT_ACCESS_GROUP_MANAGE_ANY`,
      `TENANT_PERMISSION_GRANT_MANAGE_ANY`) into
      `direct_global_permission_grants` and
      `global_access_group_permissions` via raw JDBC for at least one
      holder/group each, including one holder who already also holds one
      of the granular replacements directly (to exercise `ON CONFLICT DO
      NOTHING`), then run `V24`, then assert via raw JDBC that every
      expected granular row exists exactly once and every bundled row is
      gone (Red — migration doesn't exist yet).
- [x] 14. Write `V24__expand_bundled_global_permissions.sql` per
      PLAN.md's pattern (Green for task 13).
- [x] 15. Remove `TENANT_MEMBER_MANAGE_ANY`, `TENANT_ACCESS_GROUP_MANAGE_ANY`,
      `TENANT_PERMISSION_GRANT_MANAGE_ANY` from `GlobalPermission` now
      that no code references them and `V24` has run in every
      environment's migration path; run `./mvnw verify` to confirm
      nothing still compiles against the removed values (Green — should
      be a clean compile given tasks 4/10 already moved every call
      site).
- [x] 16. Run the full `./mvnw verify` and confirm the suite is green.
- [x] 17. Update `PLAN.md`/root `DECISIONS.md` if any decision changed
      during implementation (in particular, if the `V24` migration test
      approach in task 13 turns out not to be viable as described, that
      change must be reflected back into PLAN.md's testing strategy
      section, not left undocumented).

## Implementation notes (2026-08-02, post-implementation)

- **Migration number confirmed as `V24` before implementation started** (this
  TASKS.md originally referenced `V23` in tasks 13/14, written before
  `tenant-creation` claimed `V23` for its own schema change). Verified via
  `ls src/main/resources/db/migration/` at the start of this session; PLAN.md's
  "Data schema" section already correctly said `V24`, only this file's task
  text lagged and has been corrected in place (search/replace, no content
  change beyond the version number and test class name).
- **Reconnected two `staff-rbac-management-operations` gates that had fallen
  back to a coarser permission**, per that feature's PLAN.md "Implementation
  notes" section (`STAFF_USER_DELETE`/`TENANT_MEMBER_DELETE`/
  `TENANT_PERMISSION_GRANT_CREATE` didn't exist yet when that feature shipped):
  - `StaffService#deleteStaffUser` and its token-generation sibling now
    require `GlobalPermission.STAFF_USER_DELETE` (was
    `STAFF_PERMISSION_MANAGE`) — combined with `enforceStaffCeiling`,
    unchanged in practice (still `STAFF_ADMIN`-only for a `STAFF`/
    `STAFF_ADMIN` target), exactly as that feature's PLAN.md anticipated.
  - `TenantService#requireHardDeleteGate`'s non-admin-target branch now
    requires `GlobalPermission.TENANT_MEMBER_DELETE` (was
    `TENANT_MEMBER_MANAGE_ANY`), mirroring `removeMember`'s gate.
  - `TenantService#batchUpdatePermissions` (tenant-scoped) and its
    token-generation sibling now require
    `GlobalPermission.TENANT_PERMISSION_GRANT_CREATE` (was
    `TENANT_PERMISSION_GRANT_MANAGE_ANY`), mirroring `grantPermission`'s
    gate.
  - `StaffService#batchUpdatePermissions` (staff-scope) intentionally kept
    `STAFF_PERMISSION_MANAGE` — that gate was never one of the three
    fallback constants flagged by that feature's PLAN.md, it's
    `staff-rbac-split`'s original, still-correct gate for staff-scope
    permission management.
- `./mvnw verify` run at the end of this feature; see the implementing
  agent's summary for the outcome and commit hashes.
