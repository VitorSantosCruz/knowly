# PLAN — permission-granularity-model

> The how. Translates the SPEC into concrete technical decisions.
> References SPEC.md.

## Architectural decisions

- **View-dependency lives as a single static lookup on each permission
  enum, not a parallel mechanism or per-annotation duplication.** Add
  `Permission#viewDependency(): Optional<Permission>` and
  `GlobalPermission#viewDependency(): Optional<GlobalPermission>`, each
  an explicit `switch` (not a naming-convention string transform — e.g.
  `STAFF_SUPPORT_HANDLE`/`CONVERSATION_USE`/`TENANT_MEMBER_MANAGE` don't
  follow the `_VIEW`/`_EDIT`/`_DELETE` suffix pattern, so deriving the
  companion by string manipulation would silently misfire for anything
  irregular). Why: REQ-2's rule needs exactly one authoritative place a
  reviewer checks when adding a new permission, mirroring `DECISIONS.md`'s
  existing "fails closed, no parallel mechanism" precedent for tenant
  isolation. Cases (initial set, this feature):
  - `Permission`: `ARTICLE_EDIT`→`ARTICLE_VIEW`, `ARTICLE_DELETE`→`ARTICLE_VIEW`.
  - `GlobalPermission`: `TENANT_EDIT`→`TENANT_VIEW`, `TENANT_DELETE`→`TENANT_VIEW`,
    `STAFF_USER_EDIT`→`STAFF_USER_VIEW`, `STAFF_USER_DELETE`→`STAFF_USER_VIEW`,
    `TENANT_MEMBER_EDIT`→`TENANT_MEMBER_VIEW`, `TENANT_MEMBER_DELETE`→`TENANT_MEMBER_VIEW`,
    `TENANT_ACCESS_GROUP_EDIT`→`TENANT_ACCESS_GROUP_VIEW`,
    `TENANT_ACCESS_GROUP_DELETE`→`TENANT_ACCESS_GROUP_VIEW`,
    `TENANT_PERMISSION_GRANT_DELETE`→`TENANT_PERMISSION_GRANT_VIEW`.
    Everything else returns `Optional.empty()` (no dependency), including
    every `_CREATE`/`_VIEW` case per REQ-3.
- **The dependency is enforced in the two existing aspects, not in
  controllers/services.** `PermissionAspect.checkPermission` and
  `GlobalPermissionAspect.checkGlobalPermission`, after the existing
  single-permission check passes, additionally resolve
  `requiresPermission.value().viewDependency()` and — if present — run
  the *same* `hasPermission` check against the companion permission,
  throwing `PermissionDeniedException` if it's missing. Why: REQ-2's own
  non-functional requirement says this must live at "the same
  authorization layer that already enforces every other permission
  check," not duplicated ad hoc per controller — these two aspects
  already are that layer. `STAFF_ADMIN`/`MEMBER_ADMIN` bypasses are
  untouched (they already skip all permission checks, view included).
- **`TenantService#requireAdminOfTenantOrStaff`'s inline `STAFF`-branch
  check gets the same dependency check, reusing `GlobalPermission#viewDependency()`
  rather than re-deriving it.** This helper isn't annotation-driven (it's
  a private method called from within already-`@RequiresPermission`-free
  service methods for the staff-or-tenant-admin either/or), so it can't
  piggyback on `GlobalPermissionAspect` directly — but it must obey the
  same rule. It becomes: `STAFF` passes only if `hasPermission(actor,
  requiredPermission)` **and**, if `requiredPermission.viewDependency()`
  is present, `hasPermission(actor, thatDependency)` too. A tenant admin
  (`MEMBER_ADMIN`) is unaffected — REQ-2 only applies to `Permission`/
  `GlobalPermission`-gated checks, not the tenant-admin ownership
  bypass, which was never permission-shaped. Why: single source of truth
  for the dependency (the enum method) even though the enforcement point
  differs from the aspect, avoiding a second, drifting definition of
  "what requires what."
- **`TenantService`'s staff-branch calls move off the three bundled
  `GlobalPermission`s onto the new granular ones**, one per actual action
  (not "manage any" for everything):
  - `addMember` → `TENANT_MEMBER_CREATE`
  - `listMembers` → `TENANT_MEMBER_VIEW`
  - `removeMember` / `generateMemberRemovalDeletionConfirmationToken` →
    `TENANT_MEMBER_DELETE` (now also requires `TENANT_MEMBER_VIEW` via
    the dependency, so a caller must be able to list members to remove
    one)
  - `createAccessGroup` → `TENANT_ACCESS_GROUP_CREATE`
  - `listAccessGroups` → `TENANT_ACCESS_GROUP_VIEW`
  - `grantAccessGroupPermission` → `TENANT_ACCESS_GROUP_EDIT` (REQ-10:
    "changing which permissions a group grants" is this resource's edit
    action)
  - `grantPermission` / `assignAccessGroup` → `TENANT_PERMISSION_GRANT_CREATE`
  - `revokePermission` / `unassignAccessGroup` / their
    confirmation-token-generation counterparts →
    `TENANT_PERMISSION_GRANT_DELETE`
  - `getMemberDetail` → `TENANT_PERMISSION_GRANT_VIEW`

  There is deliberately no `TENANT_ACCESS_GROUP_DELETE`-gated method and
  no `TENANT_MEMBER_EDIT`-gated method today — `TenantService` has no
  "delete an access group" or "change a member's role after creation"
  capability yet. Both permissions are still added to the enum now (SPEC
  REQ-9/REQ-10 require the permission to exist), so that whichever future
  SPEC adds that business logic finds the permission — and its REQ-2
  dependency — already in place; no controller/service wiring is added
  for a capability that doesn't exist (mirrors REQ-7's `TENANT_EDIT`/
  `TENANT_DELETE` precedent exactly).
- **`TENANT_VIEW`/`TENANT_EDIT`/`TENANT_DELETE` and `STAFF_USER_EDIT`/
  `STAFF_USER_DELETE` are added to the enum with no annotation usage
  anywhere yet** — per SPEC REQ-7/REQ-8's explicit scope note, the
  business capabilities (tenant edit/delete, staff-user delete) are
  future SPECs' responsibility. Adding an unused enum value is safe and
  cheap; adding a controller endpoint or service method for a capability
  that isn't specified would be scope expansion this PLAN must not do.
  `STAFF_USER_DELETE` is a placeholder for `staff-rbac-management-operations`
  SPEC's already-specified deletion logic to consume — that SPEC doesn't
  yet reference a permission constant, so this PLAN reserves it; no
  change to `StaffService`/`StaffController` beyond the enum entry.
- **Article gets only the dependency, no enum/controller change** —
  `ARTICLE_VIEW`/`_CREATE`/`_EDIT`/`_DELETE` already exist and are
  already independently annotated in `ArticleController`; REQ-6 is fully
  satisfied by `Permission#viewDependency()`'s two new cases plus
  `PermissionAspect`'s new check. No `ArticleController`/`ArticleService`
  change needed.
- **Bundled-permission migration is additive-then-subtractive within one
  Flyway migration, not a live dual-read period.** For each of the three
  bundled `GlobalPermission`s, the migration:
  1. Inserts one row per granular replacement for every existing holder
     (`direct_global_permission_grants` and
     `global_access_group_permissions`), using
     `INSERT ... SELECT ... ON CONFLICT DO NOTHING` against each table's
     existing unique constraint, so a holder who — improbably — already
     also held one of the granular permissions isn't duplicated.
  2. Deletes the bundled-permission rows once every replacement is
     inserted.

  Why not keep the bundled value in the enum as a deprecated alias
  instead of migrating data? Because `GlobalPermission` is a Java enum
  backing a `VARCHAR` column with no `@Convert`/lookup-table layer — an
  enum value that stops existing in Java but still exists as a row value
  fails deserialization the moment it's read (`GlobalPermissionService.effectivePermissions`
  maps every row through `DirectGlobalPermissionGrant::getPermission`), so
  removing the enum constant *requires* the data migration to happen in
  the same deploy, not before or after it. This is the same fail-fast
  property Envers audit tables also need to tolerate — the `_aud` copies
  of these two tables keep old bundled-permission rows as historical
  fact (Envers never rewrites history), which is fine: `_aud` rows are
  read only for audit-trail display, not deserialized into the live enum
  the same way.
- **Staff-user audit history isn't touched by the migration** — the
  bundled→granular rewrite is a permission-model change, not an
  authorization event in itself (no grant/revoke actually happened from
  the affected holder's point of view, their effective access is
  unchanged per REQ-9's acceptance criterion), so no `AuditEvent` rows
  are synthesized for it. Existing audit rows referencing the old
  bundled permission name in `resourceType`/free-text fields (if any)
  are left as historical fact, same reasoning as the `_aud` tables above.

## Data schema

New migration `V24__expand_bundled_global_permissions.sql`:

```sql
-- TENANT_MEMBER_MANAGE_ANY -> VIEW, CREATE, EDIT, DELETE
INSERT INTO direct_global_permission_grants (user_id, permission, created_at, created_by, updated_at, updated_by)
SELECT user_id, v.permission, created_at, created_by, updated_at, updated_by
FROM direct_global_permission_grants, LATERAL (
  VALUES ('TENANT_MEMBER_VIEW'), ('TENANT_MEMBER_CREATE'), ('TENANT_MEMBER_EDIT'), ('TENANT_MEMBER_DELETE')
) AS v(permission)
WHERE direct_global_permission_grants.permission = 'TENANT_MEMBER_MANAGE_ANY'
ON CONFLICT (user_id, permission) DO NOTHING;

INSERT INTO global_access_group_permissions (global_access_group_id, permission, created_at, created_by, updated_at, updated_by)
SELECT global_access_group_id, v.permission, created_at, created_by, updated_at, updated_by
FROM global_access_group_permissions, LATERAL (
  VALUES ('TENANT_MEMBER_VIEW'), ('TENANT_MEMBER_CREATE'), ('TENANT_MEMBER_EDIT'), ('TENANT_MEMBER_DELETE')
) AS v(permission)
WHERE global_access_group_permissions.permission = 'TENANT_MEMBER_MANAGE_ANY'
ON CONFLICT (global_access_group_id, permission) DO NOTHING;

DELETE FROM direct_global_permission_grants WHERE permission = 'TENANT_MEMBER_MANAGE_ANY';
DELETE FROM global_access_group_permissions WHERE permission = 'TENANT_MEMBER_MANAGE_ANY';

-- TENANT_ACCESS_GROUP_MANAGE_ANY -> VIEW, CREATE, EDIT, DELETE (same shape, repeated for this permission)
-- TENANT_PERMISSION_GRANT_MANAGE_ANY -> VIEW, CREATE, DELETE (same shape, three replacements not four)
```

(Full migration repeats the same `INSERT ... LATERAL VALUES ...
ON CONFLICT DO NOTHING` / `DELETE` pair for the other two bundled
permissions before being written — this PLAN shows the pattern once to
avoid a 200-line PLAN full of repeated SQL.)

No table/column changes — `Permission`/`GlobalPermission` are plain
`VARCHAR` columns; adding enum values needs no migration, only the row
rewrite above for the three values being *removed*.

## API contracts

No new endpoints. Existing endpoints' `@RequiresPermission`/
`@RequiresGlobalPermission` annotation values change (see architectural
decisions above); request/response shapes are unchanged.

| Method | Path | Permission before | Permission after |
|---|---|---|---|
| POST | `/api/tenants/{tenantId}/members` (staff branch) | `TENANT_MEMBER_MANAGE_ANY` | `TENANT_MEMBER_CREATE` |
| GET | `/api/tenants/{tenantId}/members` (staff branch) | `TENANT_MEMBER_MANAGE_ANY` | `TENANT_MEMBER_VIEW` |
| DELETE | `/api/tenants/{tenantId}/members/{membershipId}` (staff branch) | `TENANT_MEMBER_MANAGE_ANY` | `TENANT_MEMBER_DELETE` (+ `TENANT_MEMBER_VIEW`) |
| POST | `/api/tenants/{tenantId}/access-groups` (staff branch) | `TENANT_ACCESS_GROUP_MANAGE_ANY` | `TENANT_ACCESS_GROUP_CREATE` |
| GET | `/api/tenants/{tenantId}/access-groups` (staff branch) | `TENANT_ACCESS_GROUP_MANAGE_ANY` | `TENANT_ACCESS_GROUP_VIEW` |
| POST | `/api/tenants/{tenantId}/access-groups/{id}/permissions` (staff branch) | `TENANT_ACCESS_GROUP_MANAGE_ANY` | `TENANT_ACCESS_GROUP_EDIT` (+ `TENANT_ACCESS_GROUP_VIEW`) |
| POST | `/api/tenants/{tenantId}/members/{membershipId}/permissions` (staff branch) | `TENANT_PERMISSION_GRANT_MANAGE_ANY` | `TENANT_PERMISSION_GRANT_CREATE` |
| DELETE | `/api/tenants/{tenantId}/members/{membershipId}/permissions/{permission}` (staff branch) | `TENANT_PERMISSION_GRANT_MANAGE_ANY` | `TENANT_PERMISSION_GRANT_DELETE` (+ `TENANT_PERMISSION_GRANT_VIEW`) |
| POST/DELETE | `/api/tenants/{tenantId}/members/{membershipId}/access-groups/{id}` (staff branch) | `TENANT_PERMISSION_GRANT_MANAGE_ANY` | `_CREATE` / `_DELETE` (+ `_VIEW` on delete) |
| GET | `/api/tenants/{tenantId}/members/{membershipId}` (staff branch) | `TENANT_PERMISSION_GRANT_MANAGE_ANY` | `TENANT_PERMISSION_GRANT_VIEW` |
| PUT/DELETE | `/api/tenants/{tenantId}/articles/{articleId}` | `ARTICLE_EDIT` / `ARTICLE_DELETE` (independent) | same, now each also requires `ARTICLE_VIEW` |

Response status codes unchanged (`403` on denial via
`PermissionDeniedException`, same as today).

## Dependencies

None new.

## Package/file structure

- `src/main/java/br/com/conectabyte/knowly/tenancy/Permission.java` (modify: no new values, doc comment only — behavior lives in the new method below)
- `src/main/java/br/com/conectabyte/knowly/tenancy/Permission.java` — add `viewDependency()` static-switch instance method
- `src/main/java/br/com/conectabyte/knowly/tenancy/GlobalPermission.java` (modify: add `TENANT_VIEW`, `TENANT_EDIT`, `TENANT_DELETE`, `STAFF_USER_EDIT`, `STAFF_USER_DELETE`, `TENANT_MEMBER_VIEW`, `TENANT_MEMBER_CREATE`, `TENANT_MEMBER_EDIT`, `TENANT_MEMBER_DELETE`, `TENANT_ACCESS_GROUP_VIEW`, `TENANT_ACCESS_GROUP_CREATE`, `TENANT_ACCESS_GROUP_EDIT`, `TENANT_ACCESS_GROUP_DELETE`, `TENANT_PERMISSION_GRANT_VIEW`, `TENANT_PERMISSION_GRANT_CREATE`, `TENANT_PERMISSION_GRANT_DELETE`; remove `TENANT_MEMBER_MANAGE_ANY`, `TENANT_ACCESS_GROUP_MANAGE_ANY`, `TENANT_PERMISSION_GRANT_MANAGE_ANY`; add `viewDependency()`)
- `src/main/java/br/com/conectabyte/knowly/audit/PermissionAspect.java` (modify: dependency check after the existing single-permission check)
- `src/main/java/br/com/conectabyte/knowly/audit/GlobalPermissionAspect.java` (modify: same)
- `src/main/java/br/com/conectabyte/knowly/tenancy/TenantService.java` (modify: `requireAdminOfTenantOrStaff` gains the same dependency check; every staff-branch call site's `GlobalPermission` argument updated per the table above)
- `src/main/resources/db/migration/V24__expand_bundled_global_permissions.sql` (new)

## Testing strategy

- **Unit — `PermissionAspectTest`/`GlobalPermissionAspectTest`** (extend
  existing test classes, don't create parallel ones): for each of
  `ARTICLE_EDIT`/`ARTICLE_DELETE` (tenant scope) and one representative
  global case (e.g. `TENANT_MEMBER_DELETE`), assert (a) holding both the
  action permission and its view companion → proceeds, (b) holding the
  action permission alone → `PermissionDeniedException`, (c) holding
  only the view companion → `PermissionDeniedException` (unchanged from
  today, view alone never grants edit/delete), (d) a permission with no
  `viewDependency()` (e.g. `ARTICLE_CREATE`) → unaffected by the
  dependency, proceeds on its own permission alone. Also a direct unit
  test of `Permission#viewDependency()`/`GlobalPermission#viewDependency()`
  enumerating every expected pair, so a future enum addition that
  forgets to wire (or mis-wires) a dependency fails a fast, obvious test
  rather than being caught only by an integration gap.
- **Integration — extend `StaffRbacIntegrationTest`** (mirrors its
  existing per-`GlobalPermission` coverage): a `STAFF` user granted
  `TENANT_MEMBER_DELETE` only (no `TENANT_MEMBER_VIEW`) gets `403` on
  `DELETE .../members/{id}`; granted both, gets the expected success
  path. Same shape for `TENANT_ACCESS_GROUP_EDIT`/`TENANT_PERMISSION_GRANT_DELETE`.
  A tenant-scoped equivalent (existing tenant-permission integration
  test, extend rather than duplicate) covers `ARTICLE_EDIT`/`ARTICLE_DELETE`
  without `ARTICLE_VIEW`.
- **Migration regression — new `V24` migration test** (Testcontainers,
  same pattern as any existing Flyway-migration-specific test in this
  codebase, or a `@SpringBootTest` that seeds pre-migration-shaped rows
  via a raw `JdbcTemplate` insert before the migration would apply is
  not viable since Flyway runs at context startup — instead: a
  dedicated `V24MigrationIntegrationTest` that (1) inserts a
  bootstrap-only test row *after* full migration via a raw SQL insert of
  a `TENANT_MEMBER_MANAGE_ANY`-shaped scenario is impossible once the
  enum value is gone from Java, so verification is done at the SQL
  level: a test that runs only the migrations up to `V22` in an
  isolated Testcontainers instance, inserts direct grants/access-group
  permissions using the old bundled values via raw JDBC, then manually
  triggers `V24`, then asserts — again via raw JDBC, not the Java enum —
  that every original holder now has exactly the expected granular set
  and the bundled row is gone.) This is the only place raw JDBC
  assertions (bypassing the Java enum entirely) are appropriate, because
  the whole point is testing a state transition through a value the
  enum no longer models.
- No new test needed for `TENANT_VIEW`/`_EDIT`/`_DELETE` or
  `STAFF_USER_EDIT`/`_DELETE` beyond the `viewDependency()` unit test
  above — there is no business logic gated by them yet (REQ-7/REQ-8
  scope note), so there is nothing to integration-test until a future
  SPEC adds the gated action.
