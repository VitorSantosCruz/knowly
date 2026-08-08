# TASKS — Soft-delete default filter

> Atomic, sequential, TDAD-ordered tasks derived from PLAN.md.
> Scoped `./mvnw test -Dtest=ClassName` after each task, NOT full
> `./mvnw verify` (reserved for task 26 only). Run `./mvnw spotless:apply`
> immediately before every commit. Each task ends in its own
> Conventional Commit.

## Phase 0 — Discovery (must run before any entity annotation change)

- [x] 1. **Existing-test migration risk audit.** Grep the test suite for
      fixtures that set a soft-deleted row (`setDeletedAt(...)` /
      equivalent builder) on any of the 13 entities and then assert that
      row *is* returned by a plain query/repository call. Produce a
      triage list in this task's commit message body (or a scratch note
      referenced by it): for each hit, record file + method + a
      classification (`A` = encodes the exact leak-class bug, expectation
      must flip to "excluded"; `B` = legitimate need to see deleted rows,
      needs `@AllowDeletedForOversight` or a native-query rewrite). Do
      **not** fix anything yet — this task only produces the list. No
      code change, so no `./mvnw test` needed; commit the audit findings
      as a doc/comment (e.g. append to this TASKS.md under a "Migration
      risk audit results" heading) so each subsequent fix task can
      reference one specific hit.
      Commit: `docs(soft-delete-default-filter): record existing-test migration risk audit`

> Note: Task 1's output determines how many "fix triaged hit" tasks are
> needed below (Phase 5). Do not collapse multiple hits into one task —
> each triaged hit is its own task/commit, inserted into Phase 5 as
> `5.N` once the audit is complete.

## Migration risk audit results (task 1)

Grepped `src/test/java` for `setDeletedAt`/`DeletedAt` fixtures against the
13 in-scope entities and traced each to its assertion. Key structural
finding: a call site is only ever subject to `SoftDeleteFilterAspect`
when it runs inside a real `@Transactional` *service* method (the aspect
pointcut excludes `Repository+` proxies, same as `TenantFilterAspect`) —
so any test calling a repository method **directly**, with no enclosing
`@Transactional` service call in between, is unaffected by this feature
even if it inserts a soft-deleted row and reads it right back. That
covers the large majority of `setDeletedAt` fixtures found (e.g.
`StaffRbacManagementOperationsTest#deletionSucceedsWithAValidToken`,
`TenantRbacManagementOperationsTest#hardDeleteRejected...`,
`UserRepositoryTest`, `TenantServiceTest`'s active-membership/act-as/
edit/delete rejection tests, `TenantDeleteIntegrationTest`,
`TenantEditIntegrationTest`, `AuthControllerIntegrationTest`) — all of
these already assert exclusion/rejection of the soft-deleted row (they
encode the *correct*, post-fix behavior already) or read via a plain
repository call outside any transactional service boundary, so they are
**not hits**.

One genuine root cause, surfacing as 5 test-method hits, all Type B
(legitimate need to see soft-deleted rows, needs the escape hatch —
not a test-expectation flip):

- `TenantRepository#searchDeactivated` is a custom JPQL query with an
  explicit `WHERE t.deletedAt IS NOT NULL` predicate. Once
  `softDeleteFilter` (condition `deleted_at is null`) is enabled by
  default on `Tenant`, Hibernate ANDs both predicates together, so this
  query becomes permanently empty (`deleted_at is not null AND
  deleted_at is null`) — this is exactly the "deactivated tenants"
  listing's entire purpose, so it is a functional regression, not just a
  test-expectation mismatch.
  - `TenantRepositoryTest#searchDeactivatedReturnsOnlySoftDeletedTenants`
  - `TenantServiceTest#listDeactivatedTenantsReturnsOnlySoftDeletedTenantsWithDeletedAtPopulated`
  - `TenantServiceTest#listDeactivatedTenantsSucceedsForStaffGrantedTenantDeleteAndTenantView`
  - `TenantDeactivatedListingIntegrationTest#staffAdminSeesOnlyDeactivatedTenantsWithDeletedAtPopulatedAndActiveListingExcludesIt`
  - `TenantDeactivatedListingIntegrationTest#staffGrantedTenantDeleteAndTenantViewSeesTheDeactivatedListing`
  - Fix: add `@AllowDeletedForOversight` to
    `TenantService#listDeactivatedTenants` (already `@Transactional` and
    already gated by `@RequiresGlobalPermission(GlobalPermission.TENANT_DELETE)`,
    so the authorization check the annotation's Javadoc requires is
    already in place on the same method) — one fix, one commit, since
    all 5 hits are the same one-line gap rather than five independent
    issues; splitting one production-code line across five commits would
    be artificial.

No other `*DeletedAtIsNull`-suffixed custom query, and no other custom
`deletedAt IS NOT NULL`-style predicate, was found for the remaining 12
entities (`Tenant`'s `searchDeactivated` is the only inverse-predicate
query in the codebase — confirmed via
`grep -rn "deletedAt IS NOT NULL\|DeletedAtIsNotNull" src/main/java`).

This becomes Phase 6 task 5.1 below.

## Phase 1 — Core mechanism (SoftDeleteFilter, aspect, entity: User)

- [ ] 2. Write a Testcontainers-backed integration test (new test class,
      e.g. `SoftDeleteFilterIntegrationTest`, mirroring the existing
      `TenantFilter` integration test's setup) that inserts one live
      `User` and one soft-deleted `User` (`deletedAt` set), calls
      `userRepository.findById(...)`/`findAll()` inside a
      `@Transactional` test service call with no per-query opt-in, and
      asserts only the live row is returned. Confirm Red (fails today —
      no filter applied yet, both rows returned).
      Test: `./mvnw test -Dtest=SoftDeleteFilterIntegrationTest`
- [ ] 3. Implement the minimum to go Green: create
      `br.com.conectabyte.knowly.softdelete.SoftDeleteFilter` (`NAME`
      constant `"softDeleteFilter"`); add
      `@FilterDef(name = SoftDeleteFilter.NAME, defaultCondition = "deleted_at is null")`
      + `@Filter(name = SoftDeleteFilter.NAME)` to `User`; create
      `br.com.conectabyte.knowly.softdelete.SoftDeleteFilterAspect`
      (`@Aspect`/`@Component`, same pointcut and `@Order(LOWEST_PRECEDENCE)`
      as `TenantFilterAspect`) that unconditionally calls
      `session.enableFilter(SoftDeleteFilter.NAME)` at the start of every
      `@Transactional` service method. Confirm Green.
      Test: `./mvnw test -Dtest=SoftDeleteFilterIntegrationTest`
      Commit: `feat(soft-delete-default-filter): add SoftDeleteFilter + SoftDeleteFilterAspect for User`

## Phase 2 — Escape hatch

- [ ] 4. Write a test on a throwaway `@Transactional` test-service method
      annotated with a not-yet-existing `@AllowDeletedForOversight` that
      asserts it *does* see the soft-deleted `User` row, plus a
      concurrent/subsequent plain call in the same test asserting the
      filter is back on afterward. Confirm Red (annotation doesn't
      compile/exist yet).
      Test: `./mvnw test -Dtest=SoftDeleteFilterIntegrationTest`
- [ ] 5. Implement the minimum to go Green: create
      `br.com.conectabyte.knowly.softdelete.AllowDeletedForOversight`
      (method-level marker annotation). **Its Javadoc must be written now,
      not deferred, carrying the same authorization caveat
      `BypassTenantFilterForOversight`'s Javadoc already states verbatim**:
      that the annotation only widens what the query can see for the
      duration of that one method, it never substitutes for an
      authorization check, and the annotated method must still perform
      that check itself. This is an explicit AppSec-review requirement on
      this task, not an implicit expectation — do not land the annotation
      without this Javadoc language present in the same commit. Update
      `SoftDeleteFilterAspect` to read the annotation via
      `MethodSignature#getMethod().getAnnotation(...)` the same way
      `TenantFilterAspect` reads `BypassTenantFilterForOversight`, and call
      `session.disableFilter(SoftDeleteFilter.NAME)` instead of enabling it
      when present. Confirm Green.
      Test: `./mvnw test -Dtest=SoftDeleteFilterIntegrationTest`
      Commit: `feat(soft-delete-default-filter): add AllowDeletedForOversight escape hatch`

## Phase 3 — Coexistence with tenant filter

- [ ] 6. Write a coexistence test on `Conversation` (already carries
      `@Filter(TenantFilter...)`): three fixtures (right tenant/live,
      right tenant/soft-deleted, wrong tenant/live), asserting a row is
      returned only when both in-tenant and not soft-deleted. Confirm Red
      (no `@Filter`/`softDeleteFilter` on `Conversation` yet).
      Test: `./mvnw test -Dtest=SoftDeleteFilterConversationIntegrationTest`
- [ ] 7. Implement the minimum to go Green: add
      `@Filter(name = SoftDeleteFilter.NAME)` to `Conversation` (no
      `defaultCondition`, since the `@FilterDef` already lives on `User`).
      Confirm Green, and confirm existing tenant-isolation tests for
      `Conversation` still pass unchanged.
      Test: `./mvnw test -Dtest=SoftDeleteFilterConversationIntegrationTest,TenantFilterIntegrationTest`
      Commit: `feat(soft-delete-default-filter): apply softDeleteFilter to Conversation, verify tenant-filter coexistence`

## Phase 4 — Roll out to the remaining 11 entities

Each of these follows the same Red/Green shape as task 2/3: one
per-entity test (insert live + soft-deleted row, assert only live
returned via a plain repository call, no opt-in), then the minimal
`@Filter(name = SoftDeleteFilter.NAME)` addition. Grouped one entity (or
tightly-coupled pair sharing one test class) per task/commit so a
regression is traceable to a single entity.

- [ ] 8. `UserProfile`: test (Red) + `@Filter` (Green).
      Test: `./mvnw test -Dtest=SoftDeleteFilterUserProfileIntegrationTest`
      Commit: `feat(soft-delete-default-filter): apply softDeleteFilter to UserProfile`
- [ ] 9. `Contact`: test (Red) + `@Filter` (Green).
      Test: `./mvnw test -Dtest=SoftDeleteFilterContactIntegrationTest`
      Commit: `feat(soft-delete-default-filter): apply softDeleteFilter to Contact`
- [ ] 10. `Address`: test (Red) + `@Filter` (Green).
      Test: `./mvnw test -Dtest=SoftDeleteFilterAddressIntegrationTest`
      Commit: `feat(soft-delete-default-filter): apply softDeleteFilter to Address`
- [ ] 11. `Tenant`: test (Red) + `@Filter` (Green).
      Test: `./mvnw test -Dtest=SoftDeleteFilterTenantIntegrationTest`
      Commit: `feat(soft-delete-default-filter): apply softDeleteFilter to Tenant`
- [x] 12. **BLOCKED, not implemented — schema gap discovered during
      implementation.** `AccessGroup` has no `deleted_at` column in this
      codebase snapshot: `access_groups` was never touched by V25 or V28
      (grep of `src/main/resources/db/migration/*.sql` confirms no
      `ALTER TABLE access_groups ADD COLUMN deleted_at` anywhere), and
      `AccessGroupRepository` has no `*DeletedAtIsNull`-style method,
      confirming `AccessGroup` rows are not currently soft-deletable at
      all in this snapshot (contradicts SPEC's premise that "every one
      of the 13 entities already has its own deletedAt/deleted_at
      column" — this SPEC/PLAN were evidently authored against a later
      codebase state than this worktree's). Adding `@Filter(condition =
      "deleted_at is null")` on an entity with no such column would break
      every query against it (SQL error: column does not exist), and
      adding the column itself is explicitly out of scope per SPEC's
      "Out of scope" section ("Adding soft-delete ... to any entity that
      doesn't already have one"). Left unimplemented; flagged for the
      orchestrator/data-architect-dba to decide whether a follow-up
      migration is warranted before this entity can be covered.
      Commit: none (no code change; see task 1's audit-results doc commit
      pattern — recorded here directly since discovered mid-Phase-4).
- [ ] 13. `TenantMembership` (already carries `@FilterDef`/`@Filter` for
      `tenantFilter`): add the `softDeleteFilter` pair alongside; test
      (Red) + `@Filter` (Green); confirm `*ActiveTrue` methods untouched
      and existing tests unaffected.
      Test: `./mvnw test -Dtest=SoftDeleteFilterTenantMembershipIntegrationTest`
      Commit: `feat(soft-delete-default-filter): apply softDeleteFilter to TenantMembership`
- [x] 14. **BLOCKED, not implemented — same schema gap as task 12.**
      `AccessGroupPermission` also has no `deleted_at` column
      (`access_group_permissions` untouched by V25/V28 either); grants
      are revoked by row deletion in this codebase snapshot, not by a
      soft-delete marker. Left unimplemented for the same reason as task
      12.
      Commit: none.
- [ ] 15. `UserAccessGroup`: test (Red) + `@Filter` (Green).
      Test: `./mvnw test -Dtest=SoftDeleteFilterUserAccessGroupIntegrationTest`
      Commit: `feat(soft-delete-default-filter): apply softDeleteFilter to UserAccessGroup`
- [ ] 16. `UserGlobalAccessGroup`: test (Red) + `@Filter` (Green).
      Test: `./mvnw test -Dtest=SoftDeleteFilterUserGlobalAccessGroupIntegrationTest`
      Commit: `feat(soft-delete-default-filter): apply softDeleteFilter to UserGlobalAccessGroup`
- [ ] 17. `DirectPermissionGrant`: test (Red) + `@Filter` (Green).
      Test: `./mvnw test -Dtest=SoftDeleteFilterDirectPermissionGrantIntegrationTest`
      Commit: `feat(soft-delete-default-filter): apply softDeleteFilter to DirectPermissionGrant`
- [ ] 18. `DirectGlobalPermissionGrant`: test (Red) + `@Filter` (Green).
      Test: `./mvnw test -Dtest=SoftDeleteFilterDirectGlobalPermissionGrantIntegrationTest`
      Commit: `feat(soft-delete-default-filter): apply softDeleteFilter to DirectGlobalPermissionGrant`

All 13 entities now carry the filter — SPEC requirements 1, 2, 3 are met.

## Phase 5 — Repository method renames (SPEC requirement 8)

Each row group below is its own task/commit: rename the method(s) on one
repository, update every call site (services, other repositories'
`@Query` references, test fixtures), re-run that repository's/consumer's
existing tests to confirm no behavior change, then commit. Renames that
drop a custom method in favor of an inherited `JpaRepository` method
(`findById`, `findAll`) are done in the same task as the rest of that
repository's renames.

- [ ] 19. `UserRepository`: rename `findByEmailIgnoreCaseAndDeletedAtIsNull`
      → `findByEmailIgnoreCase`, `findByGlobalRoleInAndDeletedAtIsNull` →
      `findByGlobalRoleIn`,
      `findByGlobalRoleInAndEmailContainingIgnoreCaseAndDeletedAtIsNull` →
      `findByGlobalRoleInAndEmailContainingIgnoreCase`,
      `countByGlobalRoleInAndDeletedAtIsNull` → `countByGlobalRoleIn`;
      drop `findByIdAndDeletedAtIsNull` and `findAllByDeletedAtIsNull` in
      favor of inherited `findById`/`findAll`. Update all call sites
      (this is the fix for the original `ChatEligibilityService`/
      `ChatConversationService` bug scenario — confirm those two services
      now go through the filtered inherited methods). Confirm no rows
      returned change.
      Test: `./mvnw test -Dtest=UserRepositoryTest,ChatEligibilityServiceTest,ChatConversationServiceTest`
      Commit: `refactor(soft-delete-default-filter): drop redundant DeletedAtIsNull suffix from UserRepository`
- [ ] 20. `ContactRepository`: rename `findByUserAndDeletedAtIsNull` →
      `findByUser`, `countByUserAndDeletedAtIsNull` → `countByUser`,
      `findByUserAndTypeAndDeletedAtIsNull` → `findByUserAndType`. Update
      call sites.
      Test: `./mvnw test -Dtest=ContactRepositoryTest`
      Commit: `refactor(soft-delete-default-filter): drop redundant DeletedAtIsNull suffix from ContactRepository`
- [ ] 21. `AccessGroupRepository`: rename `findByTenantAndDeletedAtIsNull`
      → `findByTenant`, `findByTenantAndIdInAndDeletedAtIsNull` →
      `findByTenantAndIdIn`; drop `findByIdAndDeletedAtIsNull` in favor of
      inherited `findById`. Update call sites.
      Test: `./mvnw test -Dtest=AccessGroupRepositoryTest`
      Commit: `refactor(soft-delete-default-filter): drop redundant DeletedAtIsNull suffix from AccessGroupRepository`
- [ ] 22. `AccessGroupPermissionRepository`: rename
      `findByAccessGroupInAndDeletedAtIsNull` → `findByAccessGroupIn`.
      Update call sites.
      Test: `./mvnw test -Dtest=AccessGroupPermissionRepositoryTest`
      Commit: `refactor(soft-delete-default-filter): drop redundant DeletedAtIsNull suffix from AccessGroupPermissionRepository`
- [ ] 23. `UserAccessGroupRepository` +
      `UserGlobalAccessGroupRepository`: rename
      `findByTenantMembershipAndDeletedAtIsNull` →
      `findByTenantMembership`,
      `findByTenantMembershipAndAccessGroupAndDeletedAtIsNull` →
      `findByTenantMembershipAndAccessGroup`,
      `findByUserAndDeletedAtIsNull` → `findByUser`,
      `findByUserAndGlobalAccessGroupAndDeletedAtIsNull` →
      `findByUserAndGlobalAccessGroup`. Update call sites.
      Test: `./mvnw test -Dtest=UserAccessGroupRepositoryTest,UserGlobalAccessGroupRepositoryTest`
      Commit: `refactor(soft-delete-default-filter): drop redundant DeletedAtIsNull suffix from UserAccessGroupRepository/UserGlobalAccessGroupRepository`
- [ ] 24. `DirectPermissionGrantRepository` +
      `DirectGlobalPermissionGrantRepository`: rename
      `findByTenantMembershipAndDeletedAtIsNull` →
      `findByTenantMembership`,
      `findByTenantMembershipAndPermissionAndDeletedAtIsNull` →
      `findByTenantMembershipAndPermission`,
      `findByUserAndDeletedAtIsNull` → `findByUser`,
      `findByUserAndPermissionAndDeletedAtIsNull` →
      `findByUserAndPermission`. Update call sites.
      Test: `./mvnw test -Dtest=DirectPermissionGrantRepositoryTest,DirectGlobalPermissionGrantRepositoryTest`
      Commit: `refactor(soft-delete-default-filter): drop redundant DeletedAtIsNull suffix from DirectPermissionGrantRepository/DirectGlobalPermissionGrantRepository`
- [ ] 25. `TenantRepository`: rename `existsByTaxIdAndDeletedAtIsNull` →
      `existsByTaxId`. Update call sites.
      Test: `./mvnw test -Dtest=TenantRepositoryTest`
      Commit: `refactor(soft-delete-default-filter): drop redundant DeletedAtIsNull suffix from TenantRepository`

## Phase 6 — Fix triaged migration-risk hits (from task 1)

- [ ] 5.1. `TenantService#listDeactivatedTenants`: add
      `@AllowDeletedForOversight` so `TenantRepository#searchDeactivated`'s
      `deletedAt IS NOT NULL` predicate is no longer cancelled out by the
      now-default `softDeleteFilter`. Fixes all 5 hits from the migration
      risk audit above (`TenantRepositoryTest`,
      `TenantServiceTest` x2, `TenantDeactivatedListingIntegrationTest` x2)
      in the one place they share a root cause.
      Test: `./mvnw test -Dtest=TenantRepositoryTest,TenantServiceTest,TenantDeactivatedListingIntegrationTest`
      Commit: `fix(soft-delete-default-filter): keep deactivated-tenants listing working under the default softDeleteFilter`

> This phase's exact task count is unknown until task 1 completes — do
> not skip ahead of it or assume zero hits.

## Phase 7 — Envers regression check

- [ ] 26. Write/confirm a smoke-level `AuditReader`-based test asserting
      a soft-deleted row's `_AUD` revision history still returns all
      revisions (including the soft-delete revision itself) after this
      change, for at least one `@Audited` entity among the 13. This
      should already pass (Envers is untouched by `@Filter`s) — this task
      exists to make that explicit and regression-proof, not to add new
      Envers infrastructure.
      Test: `./mvnw test -Dtest=<existing or new Envers audit test class>`
      Commit: `test(soft-delete-default-filter): confirm Envers _AUD history unaffected by softDeleteFilter`

## Phase 8 — Final verification

- [ ] 27. Run `./mvnw spotless:apply`, then the full `./mvnw verify` once,
      confirming no newly introduced failures (this is the single
      full-verify run for this feature — all prior tasks used scoped
      `-Dtest` runs only). Update `PLAN.md`/`PROJECT_STATUS.md` if any
      decision changed during implementation (e.g. discovery-phase
      findings that altered scope).
      Commit: `chore(soft-delete-default-filter): final verify pass, update PROJECT_STATUS.md`
