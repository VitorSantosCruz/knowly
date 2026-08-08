# PLAN — Soft-delete default filter

> The how. Translates the SPEC into concrete technical decisions.
> References SPEC.md.

## Architectural decisions

- Use Hibernate `@FilterDef`/`@Filter` (name `softDeleteFilter`, condition
  `deleted_at is null`) on each of the 13 entities — mirrors the existing
  `TenantFilter` mechanism exactly (same annotation family, same
  enable/disable-on-`Session` API), rather than `@SQLRestriction`, per the
  already-locked decision: `@Filter` is the only one of the two with a
  documented, callable disable path, which requirement 7 needs.
- No filter parameter is needed (unlike `tenantFilter`'s `:tenantId`):
  the condition `deleted_at is null` is static, so only one `@FilterDef`
  declaration is needed and it can be centralized (see below) rather than
  repeated with a `ParamDef` per entity.
- Centralize the `@FilterDef` declaration in one place (a new
  `br.com.conectabyte.knowly.softdelete.SoftDeleteFilter` marker/constants
  class, analogous to `TenantFilter`) and put `@FilterDef(name =
  SoftDeleteFilter.NAME, defaultCondition = "deleted_at is null")` +
  `@Filter(name = SoftDeleteFilter.NAME)` directly on each of the 13
  entity classes — Hibernate requires the `@Filter` annotation to be
  present on every entity the filter applies to (filters are not
  inherited across unrelated entities), but `defaultCondition` lets the
  condition be declared once per `@FilterDef` and omitted from each
  `@Filter` usage, avoiding 13x repetition of the SQL string. Every one
  of the 13 entities already has its own `deletedAt`/`deleted_at`
  column, so no schema change is needed.
- New aspect `SoftDeleteFilterAspect`, same shape and same `@Around`
  pointcut as `TenantFilterAspect`
  (`@annotation(Transactional) && !within(Repository+)`, `@Order(LOWEST_PRECEDENCE)`),
  but simpler: it unconditionally calls
  `session.enableFilter(SoftDeleteFilter.NAME)` at the start of every
  `@Transactional` service method, with no tenant-context branching and
  no fail-closed sentinel — there is no "ambiguous" state to fail closed
  on (SPEC requirement 2: this filter needs no context to decide, unlike
  tenant, so it is unconditionally on). The same
  `!within(Repository+)` exclusion is copied verbatim and for the same
  documented reason: without it, a non-`@Transactional`
  `@RabbitListener` consumer calling a plain repository method would
  never get the filter enabled and background jobs would silently see
  soft-deleted rows unfiltered — while REQ 2 asks for "on by default",
  the existing repository-proxy exclusion is orthogonal (it targets
  *which* pointcut enables the filter, not whether the filter itself is
  opt-in), so this is unchanged from the tenant precedent, not a new
  gap.
  - Alternative considered and rejected: a `SessionFactoryObserver` or a
    global `Interceptor` enabling the filter once at
    `Session`-creation time. Rejected because (a) it duplicates a
    second enablement mechanism alongside the tenant aspect instead of
    reusing the one hook this codebase already trusts for "make a
    filter live for this unit of work," and (b) `TenantFilterAspect`
    already re-derives/re-applies filter state per `@Transactional`
    boundary rather than per physical `Session`, and Spring-managed
    `EntityManager`/`Session` lifecycles in this app are themselves
    scoped per transaction, so matching that granularity keeps both
    filters' enablement semantics identical and auditable in one place
    (a reviewer checking "is soft-delete really always on" only has to
    read `SoftDeleteFilterAspect`, next to `TenantFilterAspect`, not a
    separate lower-level Hibernate SPI hook).
  - `SoftDeleteFilterAspect` and `TenantFilterAspect` both match the same
    pointcut and both run per `@Transactional` method entry, independent
    of each other's filter name — Hibernate sessions support multiple
    concurrently enabled named filters, so no interaction/ordering
    dependency between the two aspects needs to be introduced; `@Order`
    is set the same (`LOWEST_PRECEDENCE`) on both since neither depends
    on the other having run first.
- Escape hatch: new `@AllowDeletedForOversight` marker annotation
  (`br.com.conectabyte.knowly.softdelete` package), read by
  `SoftDeleteFilterAspect` the same way `TenantFilterAspect` already
  reads `@BypassTenantFilterForOversight` via
  `MethodSignature#getMethod().getAnnotation(...)`: if present, the
  aspect calls `session.disableFilter(SoftDeleteFilter.NAME)` instead of
  enabling it, for the duration of that one `@Transactional` method only
  (`joinPoint.proceed()` runs inside the same `try`, and — matching the
  tenant precedent's structure — no `finally` re-enable step is needed
  because a fresh `enableFilter`/`disableFilter` call happens at the
  *next* `@Transactional` entry on a session already scoped to that one
  transaction; this matches `TenantFilterAspect`'s existing behavior
  exactly and keeps both mechanisms consistent). No caller uses this
  annotation yet (SPEC explicitly scopes only "the mechanism must
  exist," not a consumer) — this is intentionally dead code until a
  future oversight/restore feature adopts it, same status as
  `BypassTenantFilterForOversight` was before `internal-team-chat`.
- Repository method renames (SPEC requirement 8): drop the
  `DeletedAtIsNull` suffix from every derived-query method on the 13
  entities' repositories, since the predicate becomes redundant once the
  filter is always on. `*ActiveTrue`-suffixed methods (`TenantMembershipRepository`,
  `ArticleRepository`) are explicitly **not** renamed or touched — `active`
  is a distinct business flag from `deletedAt` on those entities (see
  `TenantMembership`'s own class-level Javadoc), not a soft-delete
  filtering convention, and `Article` is not one of the 13 entities in
  scope for this feature at all.

## Data schema

No migration. All 13 entities already carry a `deleted_at` column
(`Instant deletedAt`, nullable). No new column, table, or index is
introduced — the existing partial indexes (e.g.
`ux_tenant_memberships_user_tenant ... WHERE deleted_at IS NULL`)
already assume "deleted_at is null" as the "live" predicate, consistent
with the new filter's condition.

## API contracts

None. This is a persistence-layer-only change with no new/changed REST
endpoint, request, or response shape.

## Dependencies

None. `@FilterDef`/`@Filter` and `Session#enableFilter`/`disableFilter`
are already-used Hibernate ORM APIs (no new `pom.xml` entry).

## Package/file structure

New:
- `br.com.conectabyte.knowly.softdelete.SoftDeleteFilter` — `NAME`
  constant (`"softDeleteFilter"`), mirrors `TenantFilter`'s shape
  (no parameter constant needed, no sentinel needed).
- `br.com.conectabyte.knowly.softdelete.SoftDeleteFilterAspect` —
  `@Aspect`/`@Component`, mirrors `TenantFilterAspect`'s pointcut and
  `@Order`.
- `br.com.conectabyte.knowly.softdelete.AllowDeletedForOversight` —
  method-level marker annotation, mirrors
  `BypassTenantFilterForOversight`. Its Javadoc must carry the same
  authorization caveat `BypassTenantFilterForOversight` already states
  verbatim: this annotation only widens what the query can see; it never
  substitutes for an authorization check, which the annotated method
  must still perform itself. Required per AppSec review of this PLAN —
  since no consumer exists yet, this contract must be written into the
  annotation at authoring time, not deferred to whenever a future
  oversight/restore feature adopts it.

Changed (add `@FilterDef`/`@Filter` annotations; entities that already
carry `@Filter(TenantFilter.NAME, ...)` get a second, independent
`@Filter` line, not a merged condition):
- `br.com.conectabyte.knowly.identity.UserProfile`
- `br.com.conectabyte.knowly.identity.Contact`
- `br.com.conectabyte.knowly.identity.Address`
- `br.com.conectabyte.knowly.auth.User`
- `br.com.conectabyte.knowly.conversation.Conversation` (already has `@Filter(TenantFilter...)`)
- `br.com.conectabyte.knowly.tenancy.Tenant`
- `br.com.conectabyte.knowly.tenancy.AccessGroup` (already has `@Filter(TenantFilter...)`)
- `br.com.conectabyte.knowly.tenancy.TenantMembership` (already has `@FilterDef`/`@Filter` for `tenantFilter`; add the `softDeleteFilter` pair alongside)
- `br.com.conectabyte.knowly.tenancy.AccessGroupPermission`
- `br.com.conectabyte.knowly.tenancy.UserAccessGroup`
- `br.com.conectabyte.knowly.tenancy.UserGlobalAccessGroup`
- `br.com.conectabyte.knowly.tenancy.DirectPermissionGrant`
- `br.com.conectabyte.knowly.tenancy.DirectGlobalPermissionGrant`

Only one entity needs the actual `@FilterDef` declaration (Hibernate
registers a `@FilterDef` once per `SessionFactory`, duplicate
identical declarations across entities are otherwise harmless but
noisy) — put it on `User` (the entity most central to the bug that
motivated this SPEC) and reference `@Filter(name =
SoftDeleteFilter.NAME)` (no `defaultCondition` repetition needed) on
the other 12.

Repository method renames (SPEC requirement 8), old → new, plus call
sites to update:

| Repository | Old method | New method |
|---|---|---|
| `UserRepository` | `findByEmailIgnoreCaseAndDeletedAtIsNull` | `findByEmailIgnoreCase` |
| `UserRepository` | `findByGlobalRoleInAndDeletedAtIsNull` | `findByGlobalRoleIn` |
| `UserRepository` | `findByIdAndDeletedAtIsNull` | `findById` (already exists via `JpaRepository` — drop the custom method entirely, update call sites to the inherited one) |
| `UserRepository` | `findAllByDeletedAtIsNull` | `findAll` (already exists via `JpaRepository` — drop the custom method, update call sites) |
| `UserRepository` | `findByGlobalRoleInAndEmailContainingIgnoreCaseAndDeletedAtIsNull` | `findByGlobalRoleInAndEmailContainingIgnoreCase` |
| `UserRepository` | `countByGlobalRoleInAndDeletedAtIsNull` | `countByGlobalRoleIn` |
| `ContactRepository` | `findByUserAndDeletedAtIsNull` | `findByUser` |
| `ContactRepository` | `countByUserAndDeletedAtIsNull` | `countByUser` |
| `ContactRepository` | `findByUserAndTypeAndDeletedAtIsNull` | `findByUserAndType` |
| `AccessGroupRepository` | `findByTenantAndDeletedAtIsNull` | `findByTenant` |
| `AccessGroupRepository` | `findByTenantAndIdInAndDeletedAtIsNull` | `findByTenantAndIdIn` |
| `AccessGroupRepository` | `findByIdAndDeletedAtIsNull` | `findById` (drop custom method, update call sites to inherited one) |
| `AccessGroupPermissionRepository` | `findByAccessGroupInAndDeletedAtIsNull` | `findByAccessGroupIn` |
| `UserAccessGroupRepository` | `findByTenantMembershipAndDeletedAtIsNull` | `findByTenantMembership` |
| `UserAccessGroupRepository` | `findByTenantMembershipAndAccessGroupAndDeletedAtIsNull` | `findByTenantMembershipAndAccessGroup` |
| `UserGlobalAccessGroupRepository` | `findByUserAndDeletedAtIsNull` | `findByUser` |
| `UserGlobalAccessGroupRepository` | `findByUserAndGlobalAccessGroupAndDeletedAtIsNull` | `findByUserAndGlobalAccessGroup` |
| `DirectPermissionGrantRepository` | `findByTenantMembershipAndDeletedAtIsNull` | `findByTenantMembership` |
| `DirectPermissionGrantRepository` | `findByTenantMembershipAndPermissionAndDeletedAtIsNull` | `findByTenantMembershipAndPermission` |
| `DirectGlobalPermissionGrantRepository` | `findByUserAndDeletedAtIsNull` | `findByUser` |
| `DirectGlobalPermissionGrantRepository` | `findByUserAndPermissionAndDeletedAtIsNull` | `findByUserAndPermission` |
| `TenantRepository` | `existsByTaxIdAndDeletedAtIsNull` | `existsByTaxId` |

`AddressRepository`/`UserProfileRepository` have no `*DeletedAtIsNull`
methods today (grep found none) — no rename needed there, but they still
gain the standing `@Filter` on their entities per requirement 1/3.
`TenantMembershipRepository`'s `*ActiveTrue` methods and
`ArticleRepository`'s `*ActiveTrue` methods are unaffected (see
"Architectural decisions").

Each rename requires a repo-wide call-site grep-and-update pass
(service classes, other repositories' `@Query` references, and test
fixtures) before the method is deleted — TASKS.md must enumerate this
per repository, not as one combined step, so a broken build surfaces
immediately after each individual rename rather than at the end.

## Testing strategy

- **Unit/slice tests per entity** (extend or add alongside existing
  `TenantFilter` integration tests, using the same Testcontainers
  Postgres setup): for each of the 13 entities, insert one live row and
  one soft-deleted row (`deletedAt` set), call a plain repository method
  (post-rename name) inside a `@Transactional` test service call, assert
  only the live row comes back — proves requirement 1/3 with zero opt-in
  code at the call site.
- **Escape-hatch test**: one test method annotated
  `@AllowDeletedForOversight` on a throwaway `@Transactional` test
  service method, asserting it *does* see the soft-deleted row, plus a
  concurrent/subsequent plain call on the same entity in the same test
  asserting the filter is back on afterward — proves requirement 7's
  "scoped to one call site only," matching the pattern of any existing
  `BypassTenantFilterForOversight` test if one exists (check
  `TenantFilterAspect`'s existing test suite for the reference shape).
- **Coexistence test**: on `Conversation` (or `AccessGroup`/
  `TenantMembership`), assert a row is returned only when it is both
  same-tenant and not soft-deleted — three fixtures (right tenant/live,
  right tenant/soft-deleted, wrong tenant/live) each producing the
  expected in/out result — proves requirement 4.
- **Envers regression**: confirm a soft-deleted row's `_AUD` revision
  history query still returns all revisions after this change — this is
  an existing `AuditReader`-based test path (Envers is untouched by
  Hibernate `@Filter`s, which only apply to the live table, not `_AUD`
  tables) — a smoke-level assertion is enough, not new Envers test
  infrastructure, per requirement 5.
- **Full regression**: `./mvnw verify` once, at the very end of
  implementation, immediately before the final commit — not run
  repeatedly per task. Task-by-task work uses scoped `./mvnw test
  -Dtest=ClassName` runs only, per explicit instruction.
- **Existing-test migration risk audit** (must be its own TASKS.md step,
  before any entity annotation change lands): grep the existing test
  suite for fixtures that insert a soft-deleted row (`setDeletedAt(...)`
  or equivalent builder call) on any of the 13 entities and then assert
  that row *is* returned by a plain query/repository call — those tests
  encode the pre-fix (leaky) behavior as "expected" and will fail once
  the filter is on by default. Each hit must be triaged individually:
  if the test was asserting exactly the class of bug this SPEC fixes,
  update its expectation (soft-deleted row now correctly absent); if the
  test had a legitimate reason to see the deleted row, decide whether
  it should instead use the new `@AllowDeletedForOversight` escape hatch
  or move to raw SQL/native query outside the entity manager. This is a
  discovery task, not a fixed list — TASKS.md should not assume a
  specific count of affected tests up front.
