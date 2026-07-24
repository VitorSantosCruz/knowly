# PLAN — Multi-tenant authorization

> The how. Translates the SPEC into concrete technical decisions.
> References SPEC.md.

## Architectural decisions

### Packages

- New package `br.com.conectabyte.knowly.tenancy`: `Tenant`,
  `TenantMembership`, `AccessGroup`, `AccessGroupPermission`,
  `DirectPermissionGrant`, `Permission` (enum), `TenantContext`,
  repositories, `TenantService`, `TenantController`, DTOs, exceptions.
- New package `br.com.conectabyte.knowly.audit`: `AuditEvent`,
  `AuditEventRepository`, the `@AuditLog` annotation + its aspect, the
  `@RequiresPermission` annotation + its aspect. Kept separate from
  `tenancy` because both `auth` and future features (articles, bot
  config) will depend on it — it must not be nested under a package
  that also holds tenant-management-specific code.

### Entity model

- `Tenant`: id, name, `@Audited` + JPA Auditing (same convention as
  `User`).
- `User` gains a nullable `globalRole` column (`STAFF` or `null`).
  `null` means "an ordinary person whose access is entirely
  tenant-scoped via memberships" (REQ-3) — there is no third state to
  confuse with a database default.
- `TenantMembership`: the join between `User` and `Tenant`, but a full
  entity (not a plain many-to-many table) because it carries a role
  (`ADMIN`/`MEMBER`), an `active` flag (REQ-19's soft removal — flipped
  to `false` rather than deleting the row), and its own audit history.
  Unique constraint on `(user_id, tenant_id)`: at most one membership
  row ever exists per user/tenant pair. Removing and re-adding the same
  person reactivates that row (`active = true` again) instead of
  inserting a duplicate — Envers then shows the full membership
  lifecycle (added → removed → re-added) against one identity, which is
  what "queryable indefinitely" (REQ-19) is for.
- `Permission`: a Java enum, not a database table. Permissions are
  code-defined — each feature that needs one adds an enum constant, no
  migration required. The constant's name is what's persisted (as
  `varchar`) in the grant tables below. (Trade-off: renaming a constant
  needs a one-off data migration for the stored strings. Accepted:
  renaming a shipped permission should be rare and deliberate anyway.)
- `AccessGroup`: tenant-scoped, admin-named bundle. `@Audited`.
- `AccessGroupPermission`: join (`access_group_id`, `permission`),
  `@Audited`.
- `DirectPermissionGrant`: join (`tenant_membership_id`, `permission`)
  — permissions granted straight to a person within one tenant,
  bypassing groups. Tied to the membership row (not directly to
  `user_id`) so a grant is inherently scoped to one membership and
  naturally goes stale if that membership is ever removed. `@Audited`.
- `UserAccessGroup`: join (`tenant_membership_id`, `access_group_id`).
  Same membership-scoping reasoning as above. `@Audited`.
- REQ-15's "effective permission set" = the union of
  `DirectPermissionGrant` rows for the membership, plus every
  `AccessGroupPermission` row for every group in `UserAccessGroup` for
  that membership. Computed in `PermissionService`, not cached across
  requests (correctness over micro-optimization; REQ-14 requires
  changes to take effect immediately, and this project doesn't have
  request volume yet to justify caching this).

### Active tenant context and session

- `TenantContext`: a request-scoped bean (`@RequestScope`) exposing
  `getActiveTenantId()` / `isStaffMode()`, populated once per request by
  a new `TenantContextFilter` (a plain `OncePerRequestFilter`, registered
  after Spring Security's filter chain) that reads the active tenant id
  from the `HttpSession` attribute `ACTIVE_TENANT_ID` — set at login and
  updated on tenant switch (below). No active tenant set (multi-membership
  user who hasn't picked one yet, REQ-5) leaves `TenantContext` empty,
  which the Hibernate filter below turns into "queries return nothing"
  rather than an exception a future feature might accidentally swallow.
- Login (`AuthController.establishSession`, existing): after building the
  `SecurityContext`, resolves the user's active `TenantMembership` rows.
  Zero memberships + `globalRole == STAFF` → session stays in "staff,
  no active tenant" mode. Exactly one → set as active tenant
  immediately (REQ-4). More than one → session is marked
  "pending-tenant-selection"; `TenantContextFilter` rejects any
  tenant-scoped endpoint with `409 TENANT_SELECTION_REQUIRED` until
  `POST /api/tenants/active` is called (REQ-5).
- `POST /api/tenants/active` `{ tenantId }`: verifies an active
  `TenantMembership` exists for the caller (REQ-7 — otherwise `403
  TENANT_ACCESS_DENIED`, logged as an audit event same as other
  authorization failures), then updates the session attribute and
  rebuilds the `Authentication`'s authorities (below) — same session,
  no new login (REQ-6).
- Authorities on the `Authentication` token are no longer the empty
  `List.of()` from the auth feature — they're derived from the active
  tenant's membership role (`ROLE_TENANT_ADMIN` / `ROLE_TENANT_MEMBER`)
  plus every granted `Permission` (`PERM_<name>`), or just `ROLE_STAFF`
  in staff/no-active-tenant mode. Switching tenants re-derives and
  re-sets these on the existing `SecurityContext` — this is *why*
  REQ-6 can avoid a new login: the session and its cookie don't change,
  only what the `Authentication` inside it grants.

### Enforcing isolation and permissions (the two aspects)

- `@RequiresPermission(Permission.X)` on a controller/service method +
  `PermissionAspect` (`@Around`): if `globalRole == STAFF`, allow
  (staff aren't tenant-permission-scoped, REQ-8). Otherwise resolve the
  active tenant membership from `TenantContext`, compute the effective
  permission set, and proceed only if `X` is in it — otherwise throw
  `PermissionDeniedException` (→ `403 PERMISSION_DENIED`, and always
  logged, REQ-17). This is how REQ-12's "deny by default" actually
  holds: a method with no `@RequiresPermission` and no other check
  denies nothing by *design* — the convention this PLAN establishes is
  that every tenant-scoped write/read handler must carry one.
- Hibernate `@Filter` (`tenantFilter`, parameter `tenantId`, defined once
  via `@FilterDef` on `TenantMembership`, referenced by name on
  `AccessGroup` and every entity future features add — `Article`
  included, once it exists) — implemented and verified in
  `TenantIsolationIntegrationTest`. If an active tenant exists, the
  filter is enabled with that id; if not (pending-selection), it's
  enabled with an id that can never match a real tenant (`-1`,
  `TenantFilter.NO_ACTIVE_TENANT_SENTINEL`) — belt-and-suspenders under
  the `@RequiresPermission` check: even a handler that forgot its
  annotation still can't read another tenant's rows, because the ORM
  itself won't return them. Staff with no active tenant get the filter
  *disabled* entirely (cross-tenant support access); staff who set one
  are scoped identically to a normal member. This is what the SPEC's
  "fails closed... not by remembering to add `WHERE tenant_id = ?`" NFR
  means concretely.
  - **Enforcement point, corrected during implementation**: the filter
    is enabled by `TenantFilterAspect`, an `@Around` advice on
    `@Transactional`-annotated **service** methods — not on repository
    interface executions as originally planned here. Tested and
    confirmed: Spring Data repository proxies get their transactional
    behavior from their own dedicated proxy-creation pipeline
    (`RepositoryFactorySupport`), a layer that always sits *inside* the
    general Spring AOP auto-proxy chain regardless of `@Order`. An
    aspect targeting repository executions runs *before* that inner
    transaction opens, so `entityManager.unwrap(Session.class)` at that
    point resolves to a throwaway, non-transactional session that's
    discarded before the real query runs — the filter setting silently
    has zero effect. Targeting `@Transactional` service methods instead
    puts this aspect on the same, single, `@Order`-controlled proxy as
    the transaction advisor (`TransactionManagementConfig`,
    `@EnableTransactionManagement(order = 0)` forces the transaction to
    be outermost), so ordering actually holds. **Consequence for every
    future feature**: any code path that reads/writes a
    `@Filter`-annotated entity must go through a `@Transactional`
    service method — a bare repository call from a controller or test
    is not filtered.
- `@AuditLog(action = "...", resourceType = "...")` + `AuditLogAspect`
  (`@Around`): writes one `AuditEvent` row after the method returns
  (`outcome = SUCCESS`) or throws (`outcome = ERROR`/`DENIED` depending
  on the exception type), with `actorUserId`/`tenantId` pulled from
  `SecurityContext`/`TenantContext`, and `resourceId` from a SpEL
  expression the annotation carries over the method's arguments/return
  value (e.g. `resourceIdExpression = "#id"`). This covers REQ-20 for
  *both* reads and writes from one mechanism, instead of hand-writing a
  log call in every handler (which is exactly the kind of thing that
  gets forgotten under the SPEC's "nothing can happen without a log"
  bar). Distinct annotation name from Hibernate's own `@Audited` to
  avoid collision/confusion — this one is the append-only action log
  (REQ-20), Hibernate's is entity-state history (already used for
  `User`, and now `Tenant`/`TenantMembership`/etc).
- `spring-boot-starter-aspectj` (Spring Boot 4's renamed AOP starter) is
  a new dependency — nothing in the project uses AOP yet.
- Gotcha confirmed while testing `PermissionAspect`: this starter
  defaults `spring.aop.proxy-target-class=true` (CGLIB), unlike the old
  `spring-boot-starter-aop`'s JDK-proxy default. CGLIB can't subclass a
  lambda (it's a final class), so a `@Bean` method returning a lambda
  implementation of an annotated interface silently gets **no proxy at
  all** — the aspect never runs, no error, no log. Every
  `@RequiresPermission`/`@AuditLog`-annotated bean in this codebase (and
  any test doubles for them) must be a real class, never a lambda.

### Audit event storage

- `AuditEvent` is its own table, deliberately **not** `@Audited` via
  Envers — Envers tracks *changes to an entity's own state*; an audit
  event doesn't have state that changes, it's the record of something
  happening. It's also never updated or deleted after being written
  (enforced at the repository level: no update/delete methods exposed).
- Columns: `occurred_at`, `actor_user_id` (nullable — reserved for
  future system-initiated events), `tenant_id` (nullable — staff
  actions aren't always tied to one tenant), `action` (namespaced
  string like `tenant.member.add`, extensible without migrations, same
  reasoning as `Permission`), `resource_type`/`resource_id` (nullable,
  `resource_id` stored as text since different resource types have
  different id types), `outcome`, `metadata` (`jsonb`, nullable —
  free-form extra context, e.g. what changed).
- Indexes on `(tenant_id, occurred_at)` and `(actor_user_id,
  occurred_at)` — both are the query shapes the future user-facing and
  support dashboards/reports will need (SPEC's "this is the dataset
  future reporting features will read from").

## Data schema

New Flyway migrations (`V3`–`V5`; `V1`/`V2` already exist from the auth
feature):

`V3__create_tenancy_tables.sql`:

```sql
ALTER TABLE users ADD COLUMN global_role VARCHAR(20);

CREATE TABLE tenants (
  id BIGSERIAL PRIMARY KEY,
  name VARCHAR(255) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by VARCHAR(255) NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by VARCHAR(255) NOT NULL
);

CREATE TABLE tenant_memberships (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id),
  tenant_id BIGINT NOT NULL REFERENCES tenants(id),
  role VARCHAR(20) NOT NULL,
  active BOOLEAN NOT NULL DEFAULT true,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by VARCHAR(255) NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by VARCHAR(255) NOT NULL,
  UNIQUE (user_id, tenant_id)
);

CREATE TABLE access_groups (
  id BIGSERIAL PRIMARY KEY,
  tenant_id BIGINT NOT NULL REFERENCES tenants(id),
  name VARCHAR(255) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by VARCHAR(255) NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by VARCHAR(255) NOT NULL,
  UNIQUE (tenant_id, name)
);

CREATE TABLE access_group_permissions (
  id BIGSERIAL PRIMARY KEY,
  access_group_id BIGINT NOT NULL REFERENCES access_groups(id),
  permission VARCHAR(100) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by VARCHAR(255) NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by VARCHAR(255) NOT NULL,
  UNIQUE (access_group_id, permission)
);

CREATE TABLE direct_permission_grants (
  id BIGSERIAL PRIMARY KEY,
  tenant_membership_id BIGINT NOT NULL REFERENCES tenant_memberships(id),
  permission VARCHAR(100) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by VARCHAR(255) NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by VARCHAR(255) NOT NULL,
  UNIQUE (tenant_membership_id, permission)
);

CREATE TABLE user_access_groups (
  id BIGSERIAL PRIMARY KEY,
  tenant_membership_id BIGINT NOT NULL REFERENCES tenant_memberships(id),
  access_group_id BIGINT NOT NULL REFERENCES access_groups(id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by VARCHAR(255) NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by VARCHAR(255) NOT NULL,
  UNIQUE (tenant_membership_id, access_group_id)
);
```

`V4__create_tenancy_envers_audit_tables.sql`: `users_aud` gains
`global_role`; new `tenants_aud`, `tenant_memberships_aud`,
`access_groups_aud`, `access_group_permissions_aud`,
`direct_permission_grants_aud`, `user_access_groups_aud` mirroring each
table + `rev`/`revtype`, per the `V2` migration's established pattern
(same `revinfo`/`revinfo_seq` — REQ-11's Envers side reuses the auth
feature's existing revision infrastructure, no changes needed there).

`V5__create_audit_events_table.sql`:

```sql
CREATE TABLE audit_events (
  id BIGSERIAL PRIMARY KEY,
  occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  actor_user_id BIGINT REFERENCES users(id),
  tenant_id BIGINT REFERENCES tenants(id),
  action VARCHAR(150) NOT NULL,
  resource_type VARCHAR(100),
  resource_id VARCHAR(100),
  outcome VARCHAR(20) NOT NULL,
  metadata JSONB
);
CREATE INDEX ix_audit_events_tenant_time ON audit_events (tenant_id, occurred_at);
CREATE INDEX ix_audit_events_actor_time ON audit_events (actor_user_id, occurred_at);
```

No `@Audited`/Envers table for `audit_events` — see "Audit event
storage" above.

## API contracts

All under `/api/tenants`. Error shape matches the auth feature's
`{ "code": "<STABLE_CODE>" }` convention.

- `GET /api/tenants/memberships`
  - Returns the caller's own active memberships (id, tenant name, role)
    — powers the tenant picker (REQ-5) and a "switch tenant" menu.
- `POST /api/tenants/active`
  - Body: `{ "tenantId": number }`
  - `200`: session's active tenant updated, authorities refreshed
    (REQ-6).
  - `403` `TENANT_ACCESS_DENIED`: caller has no active membership in
    that tenant (REQ-7).
- `POST /api/tenants` *(staff only)*
  - Body: `{ "name": string, "adminEmail": string }`
  - `200`: tenant created with its first membership row (role=ADMIN)
    for the given email in one transaction (REQ-10). If no `User` exists
    for that email yet, one is created exactly as the auth feature
    already does for any first-time login.
  - `403` `PERMISSION_DENIED`: caller is not a staff user.
- `POST /api/tenants/{tenantId}/members` *(tenant admin of that tenant,
  or staff)*
  - Body: `{ "email": string, "role": "ADMIN" | "MEMBER" }`
  - `200`/`403 PERMISSION_DENIED` (REQ-9/REQ-16).
- `DELETE /api/tenants/{tenantId}/members/{membershipId}` *(tenant
  admin of that tenant, or staff)*
  - Soft-removes (REQ-19): sets `active = false`, never deletes the row.
- `POST /api/tenants/{tenantId}/members/{membershipId}/permissions`
  - Body: `{ "permission": string }` — direct grant (REQ-14/16).
- `DELETE /api/tenants/{tenantId}/members/{membershipId}/permissions/{permission}`
- `POST /api/tenants/{tenantId}/access-groups`
  - Body: `{ "name": string, "permissions": string[] }` (REQ-13).
- `POST /api/tenants/{tenantId}/members/{membershipId}/access-groups/{accessGroupId}`
  - Assigns the membership to the group (REQ-14/15).

All of the above (except `GET /api/tenants/memberships`, which is about
the caller's own memberships) carry `@RequiresPermission`/`@AuditLog`
per the mechanisms above; the specific `Permission` enum constants they
require are listed in TASKS.md as they're implemented.

## Dependencies

- `org.springframework.boot:spring-boot-starter-aspectj` — new (Spring
  Boot 4 renamed the AOP starter from `spring-boot-starter-aop`). Backs
  both `PermissionAspect` and `AuditLogAspect`. No version pin needed
  (inherited from `spring-boot-starter-parent`).

## Package/file structure

```
src/main/java/br/com/conectabyte/knowly/tenancy/
  Tenant.java
  TenantMembership.java                # + MembershipRole enum (ADMIN, MEMBER)
  AccessGroup.java
  AccessGroupPermission.java
  DirectPermissionGrant.java
  UserAccessGroup.java
  Permission.java                      # enum, grows as features add permissions
  GlobalRole.java                      # enum (STAFF) — lives here, User references it
  TenantContext.java                   # @RequestScope
  TenantRepository.java
  TenantMembershipRepository.java
  AccessGroupRepository.java
  PermissionService.java               # effective-permission-set computation
  TenantService.java                   # create tenant+admin, add/remove member, switch active tenant
  TenantController.java
  dto/CreateTenantRequestDto.java
  dto/AddMemberRequestDto.java
  dto/SwitchActiveTenantRequestDto.java
  dto/TenantMembershipDto.java
  dto/TenancyErrorResponseDto.java
  exception/TenantAccessDeniedException.java
  exception/PermissionDeniedException.java
  exception/TenantSelectionRequiredException.java
  exception/TenancyExceptionHandler.java
src/main/java/br/com/conectabyte/knowly/audit/
  AuditEvent.java
  AuditOutcome.java                    # enum (SUCCESS, DENIED, ERROR)
  AuditEventRepository.java            # no update/delete methods exposed
  AuditLog.java                        # annotation
  AuditLogAspect.java
  RequiresPermission.java              # annotation
  PermissionAspect.java
config/
  TenantContextFilter.java             # resolves TenantContext, enables the Hibernate filter
  TenantFilterEntityListener or HibernateConfig.java  # registers @FilterDef globally, if needed
src/main/resources/db/migration/
  V3__create_tenancy_tables.sql
  V4__create_tenancy_envers_audit_tables.sql
  V5__create_audit_events_table.sql
```

## Testing strategy

- `TenantServiceTest`: unit/integration (Testcontainers Postgres) —
  atomic tenant+admin creation, add/remove/reactivate membership,
  duplicate-membership constraint, grant/revoke direct permission,
  access-group CRUD and assignment, effective-permission-set union
  logic (REQ-15, including a case combining a direct grant with an
  overlapping group grant — still just the union, no double-counting
  weirdness to assert against, but worth a test making that explicit).
- `PermissionAspectTest`: a small test-only service annotated with
  `@RequiresPermission` — asserts allowed when granted (direct or via
  group), denied (with `PermissionDeniedException`) when not, and
  always allowed for `globalRole == STAFF` regardless of tenant
  context.
- `AuditLogAspectTest`: asserts a row lands in `audit_events` on
  success and on a thrown exception, with the right actor/tenant/action/
  resource id/outcome — including confirming a *read-only* annotated
  method (no state change at all) still produces a row, which is the
  entire point of REQ-20 existing as a separate mechanism from Envers.
- `TenantIsolationIntegrationTest`: the direct regression test for the
  SPEC's core promise — seed two tenants with their own
  `AccessGroup` rows (or another `@Filter`-annotated entity), assert a
  request scoped to tenant A never returns tenant B's rows, and that a
  request with *no* active tenant returns nothing rather than erroring
  or returning everything.
- `AuthControllerIntegrationTest` additions: single-membership login
  auto-selects the tenant; multi-membership login leaves the session
  pending until `POST /api/tenants/active`; a tenant-scoped endpoint hit
  before selection returns `409 TENANT_SELECTION_REQUIRED`; switching to
  a tenant the user isn't a member of returns `403
  TENANT_ACCESS_DENIED` and is present in `audit_events`; switching
  tenants updates the session's authorities without a new `SESSION`
  cookie being issued (proving REQ-6's "no re-authentication").
- `TenantControllerIntegrationTest` (new): full endpoint coverage —
  tenant creation restricted to staff, member add/remove restricted to
  that tenant's admin (or staff) and rejected for another tenant's
  admin, permission/access-group grant-and-immediate-effect (REQ-14 —
  grant a permission, then in the same test session call the
  now-permitted action and see it succeed with no re-login in between).
