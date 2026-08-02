# PLAN — Tenant CRUD: edit and delete a tenant

> The how. Translates SPEC.md into concrete technical decisions.
> References SPEC.md, `tenant-creation/PLAN.md` (field names, and its own
> explicit note that this PLAN owns the `ux_tenants_tax_id` → partial
> index change), and `permission-granularity-model/PLAN.md` (`TENANT_VIEW`/
> `TENANT_EDIT`/`TENANT_DELETE`, the view-dependency mechanism).

## Blocking prerequisite (not this PLAN's to resolve — flagged, not decided)

**Neither `tenant-creation/PLAN.md` nor `permission-granularity-model/PLAN.md`
has been implemented yet** (verified by inspection, 2026-08-02): `Tenant.java`
still has `cnpj`/`razaoSocial`/`nomeFantasia`/`inscricaoEstadual`, not the
`legalName`/`taxId`/... field set; `GlobalPermission.java` still has
`TENANT_MEMBER_MANAGE_ANY`-shaped bundled values, not `TENANT_VIEW`/
`TENANT_EDIT`/`TENANT_DELETE`; the latest migration on disk is `V22`. Both
of those PLANs independently claim `V23` for their own migration — a
numbering collision the orchestrator must resolve by implementation order
(whichever lands second renumbers to the next free `V`), not by either
PLAN's own authors. **This PLAN cannot be implemented until both land**,
since it requires the finalized field names and the `TENANT_VIEW`/
`TENANT_EDIT`/`TENANT_DELETE` enum values plus their `viewDependency()`
wiring to already exist. This PLAN's own migration is written below as
`V25` under the assumption both predecessors occupy `V23`/one bumped
slot before it — TASKS.md's first task must re-confirm the actual next
free migration number at execution time rather than trust this assumption
blindly.

## Architectural decisions

- **Soft-delete column is `deleted_at TIMESTAMP`, not a boolean**, even
  though `TenantMembership`'s own established soft-removal shape (REQ-9's
  precedent) is a boolean `active` flag. *Why the divergence*: SPEC REQ-8
  itself specifies "a new timestamp column" for the tenant row (a
  `deleted_at` capturing *when*, useful for REQ-12's "closed and later
  re-onboarded company" story and any future audit/restore work), while
  REQ-9 only asks the cascaded *memberships* to use "the exact same
  soft-removal mechanism ... already applies to individual member
  removal" — i.e. `TenantMembership.active = false`, unchanged, no new
  membership-level column. `tenant-creation/PLAN.md`'s own migration note
  already assumes this exact name (`WHERE deleted_at IS NULL` for the
  `taxId` partial index), so `deleted_at` is not a new independent choice
  here, it's completing an assumption the prerequisite PLAN already made.
- **CORRECTION (AppSec review, 2026-08-02): the original claim that
  `requireActiveMembership`/`requireTenant`/`getActiveTenant` are "the two
  [sic, three] existing chokepoints every tenant-selection path already
  funnels through" is false for the ongoing-session case, and REQ-11 as
  originally scoped by this PLAN did not actually cover it.** Those three
  methods only run at the *moment a session picks/re-confirms* an active
  tenant (`switchActiveTenant`, staff act-as, `GET /api/tenants/active`).
  Once a session's active tenant is set, `TenantContextFilter` re-derives
  `TenantContext`'s active-tenant id from the **session attribute** on
  *every subsequent request* (verified by reading
  `TenantContextFilter.doFilterInternal` — it reads
  `TenantSessionKeys.ACTIVE_TENANT_ID` straight from `HttpSession` with no
  DB lookup), and `TenantFilterAspect` enables the Hibernate tenant filter
  for that id on every `@Transactional` service method with no check that
  the tenant still exists or isn't soft-deleted. Concretely: a member who
  switched into tenant X *before* it was soft-deleted keeps full read/write
  access to every article/conversation/etc. scoped to X for the rest of
  their session (which can be arbitrarily long-lived) — REQ-11's "or
  otherwise read/act on data scoped to it" is not satisfied by the two
  switch-time chokepoints alone. This is the same class of gap the
  multi-tenancy "fails closed" posture exists to prevent, just discovered
  a layer higher (session-cached authorization state, not a missing
  filter).
  **Fix, applying the already-established fails-closed pattern rather
  than inventing a new one**: `TenantFilterAspect#enableTenantFilter`
  gains a `tenantRepository.findById(activeTenantId)` check (its own new
  read-only lookup — this aspect already runs on every `@Transactional`
  method, so it is in fact the true single chokepoint, not
  `requireActiveMembership`/`requireTenant`) — when the resolved tenant is
  missing or `deletedAt != null`, the filter is enabled with
  `TenantFilter.NO_ACTIVE_TENANT_SENTINEL` (same fail-closed branch
  already used for "staff, no active tenant") instead of the real tenant
  id, so every tenant-scoped query for the remainder of that request
  returns nothing — exactly the existing "no active tenant in context
  returns nothing, never a bypassable error" guarantee, extended to cover
  "active tenant that no longer exists." This is a per-request extra
  `SELECT`, acceptable given it's a PK lookup and the alternative is an
  open authorization gap. `requireActiveMembership`/`requireTenant`/
  `getActiveTenant` still gain their own `deletedAt != null` check as
  originally planned below, so the *switch* attempt itself is rejected
  with `TenantAccessDeniedException` (400/403, audited) — the
  `TenantFilterAspect` change is what closes the gap for requests made
  *within* a session that predates the deletion. **This correction
  supersedes the "Tier 2, `DECISIONS.md` entry below" framing that
  followed** — the two/three service methods are necessary but were
  never sufficient; `TenantFilterAspect` is the actual authoritative gate
  and the `DECISIONS.md` entry (below) must say so.
- **REQ-11 (soft-deleted tenant unreachable) is enforced at
  `TenantService#requireActiveMembership` and `TenantService#requireTenant`**
  — the two existing chokepoints every tenant-selection path already
  funnels through (`switchActiveTenant`'s member branch and staff
  "act as tenant" branch respectively; `getActiveTenant` calls
  `tenantRepository.findById` directly today and gains the same check).
  Both already throw `TenantAccessDeniedException` for "no access" today;
  they gain one more condition — `tenant.getDeletedAt() != null` — which
  throws the same exception, so REQ-11's "reject the request the same way
  it already rejects a tenant the caller has no access to" is satisfied
  by construction for the *switch* moment, not a parallel check. No
  change needed to `resolveSessionOutcome`: a soft-deleted tenant's
  memberships are all already `active = false` per REQ-9's cascade, so
  `findByUserAndActiveTrue` already excludes them for free. **These two
  methods are necessary but not sufficient** — see the correction above:
  `TenantFilterAspect` is the actual authoritative gate for the
  ongoing-session case, and the `DECISIONS.md` entry (below) must
  document both layers, not just these two methods, so a future reader
  adding a new "is this tenant reachable" path knows both where the
  switch-time check lives and why a session-lifetime check exists at all.
- **Audit for REQ-11's "log it as a security/authorization event" needs no
  new code** — `TenantController#switchActiveTenant` is already
  `@AuditLog`-annotated, and `AuditLogAspect` already special-cases
  `TenantAccessDeniedException` (and `PermissionDeniedException`) thrown
  from within an `@AuditLog` method as `AuditOutcome.DENIED` (verified by
  reading `AuditLogAspect.logAudit`). Since the soft-delete check above
  throws that exact exception, the existing aspect logs it automatically.
  `TenantController#getActiveTenant` has no `@AuditLog` today and this
  PLAN does not add one — it's a read of the caller's *own* session state,
  not an attempt to reach another tenant's data, so REQ-11 doesn't apply
  to it the same way (a caller can't "switch into" a tenant through a GET
  that only echoes back their already-established session).
- **Edit is `PATCH`, not `PUT`** — REQ-1 explicitly requires "each
  independently editable, none required to be resubmitted together," i.e.
  partial-update semantics; `PUT`'s whole-resource-replacement semantics
  would contradict that. `EditTenantRequestDto` has every field
  `@Nullable`/unannotated-optional except where present-and-invalid must
  400 (REQ-2) — bean validation runs conditionally per non-null field via
  each field's own annotation (`@Email` on `contactEmail` still fires
  when the field is present, is skipped when absent — standard Jakarta
  Validation behavior, no custom logic needed).
- **`taxId` immutability (REQ-3) is enforced by never accepting a `taxId`
  field on `EditTenantRequestDto` at all**, not by accepting-then-rejecting
  it. SPEC's own acceptance criterion leaves the choice open ("rejected if
  present-and-different, or silently ignored ... not a scope decision") —
  this PLAN picks omission-from-the-DTO over silent-ignore-if-present:
  silently accepting and dropping a `taxId` field a caller believes they
  just changed is a worse failure mode (a client bug could send it and
  never notice it had no effect) than that field never being part of the
  contract in the first place, which a client discovers immediately as a
  400 (unknown field, if `FAIL_ON_UNKNOWN_PROPERTIES` is enabled — this
  codebase's default Jackson config, unverified here but assumed
  consistent with every other strict DTO in this codebase) or is simply
  never sent because it isn't documented as accepted.
- **`TENANT_EDIT`/`TENANT_DELETE` (and their token-generation siblings) are
  gated via `@RequiresGlobalPermission`, not the existing manual
  `requireStaff` helper `createTenant` uses for `TENANT_CREATE`.** *Why
  the divergence from the existing `TENANT_CREATE` precedent*:
  `permission-granularity-model/PLAN.md` wired its new view-dependency
  check into exactly two places — `PermissionAspect`/`GlobalPermissionAspect`
  (annotation-driven) and `TenantService#requireAdminOfTenantOrStaff`
  (the admin-or-staff either/or helper) — deliberately *not* into
  `requireStaff` (the staff-only, no-tenant-admin-bypass helper
  `TENANT_CREATE`/`TENANT_ACT_AS_ANY` use), because at the time that PLAN
  was written no staff-only-gated permission needed a view dependency yet.
  `TENANT_EDIT`/`TENANT_DELETE` are exactly that: staff-only (no
  `MEMBER_ADMIN` bypass, per this SPEC's own explicit acceptance
  criterion) *and* view-dependent. Reusing `requireStaff` as-is would
  silently skip the `TENANT_VIEW` dependency check REQ-4/REQ-5 require.
  Rather than duplicating `GlobalPermission#viewDependency()`'s dependency
  logic a third time inside `requireStaff`, this PLAN uses the
  already-dependency-aware `GlobalPermissionAspect` directly via
  `@RequiresGlobalPermission(GlobalPermission.TENANT_EDIT)` /
  `@RequiresGlobalPermission(GlobalPermission.TENANT_DELETE)` on the new
  service methods — the same pattern `StaffService`/`GlobalMetricsService`
  already use. **This is a Tier 2 call** (documented here, not in
  `DECISIONS.md`, since it's a direct, single-precedent application of
  `permission-granularity-model`'s own established mechanism, not a new
  one) — `TENANT_CREATE`/`TENANT_ACT_AS_ANY` are left on `requireStaff`
  unchanged, since neither has a view dependency and changing their
  enforcement mechanism is out of this PLAN's scope.
- **Deletion reuses `DeletionConfirmationTokenService` verbatim**, adding
  a sixth `resourceType` tag `"tenant"` (single scalar `resourceId` = the
  tenant id, no compound key needed — mirrors `article`/`tenant-member`'s
  shape, not the compound `tenant-permission`/`tenant-access-group`
  shape). `TenantService` gains
  `generateTenantDeletionConfirmationToken(User actor, Long tenantId,
  String acceptLanguageHeaderValue)` and `deleteTenant(User actor, Long
  tenantId, String word)`, following the exact structure every one of the
  six already-wired endpoints uses (`deletion-confirmation-token/PLAN.md`'s
  own established shape) — no new mechanism, no new package.
- **Soft-deleting a tenant cascades to `TenantMembership.active = false`
  via a bulk repository update, not a per-row Java loop.** `tenants`-scale
  membership counts can be large (REQ-18 explicitly rules out any
  volume-based blocking rule, implying this must stay cheap regardless of
  size); `TenantMembershipRepository` gains a
  `@Modifying @Query("update TenantMembership m set m.active = false where
  m.tenant = :tenant and m.active = true") void deactivateAllByTenant(Tenant
  tenant)`, called from `deleteTenant` inside the same `@Transactional`
  boundary as the tenant's own `deletedAt` write, both committing or
  rolling back together (REQ-9's "when a tenant is soft-deleted" is a
  single atomic outcome, not two independently-failable steps).
  `TenantMembershipRepository` is `@Filter`-scoped by `TenantFilter`
  (`Tenant`-owned entity) — `TenantFilterAspect` already enables that
  filter for every `@Transactional` service method using the caller's
  active tenant, which for a staff caller deleting a tenant they are not
  "in" resolves via the sentinel/staff-no-active-tenant branch (filter
  disabled), so the bulk update is not itself filtered out; this matches
  how `removeMember`/other staff-branch mutations on arbitrary tenants
  already work today (no special-casing needed, same as every existing
  staff cross-tenant write).
- **REQ-10 (articles/conversations/access-groups/grants untouched) needs
  no code** — nothing in `deleteTenant` touches those tables; REQ-10 is
  satisfied by omission, verified by the testing strategy below (assert
  row counts unchanged).
- **REQ-12 (a new tenant can reuse a soft-deleted tenant's `taxId`) is
  satisfied entirely by this PLAN's migration** (partial unique index
  `WHERE deleted_at IS NULL`, replacing `tenant-creation/PLAN.md`'s
  unconditional one) — no service-layer change to `createTenant` needed;
  the database constraint alone determines uniqueness scope.
- **REQ-19/REQ-20 (active-vs-deactivated listing split, product owner
  decision 2026-08-02) resolves the "flagged, not decided" item this
  PLAN previously left open** — `TenantService#listAllTenants` gains a
  `tenantRepository.search(...)` filter for `deletedAt IS NULL` (a
  `WHERE t.deletedAt IS NULL` clause added to the existing `@Query`
  backing `search`, not a second query method, so every caller of
  `listAllTenants` gets the exclusion for free with no new branch to
  forget). A **new, separate** `TenantService#listDeactivatedTenants(User
  actor, int page, int size, String search)` method mirrors
  `listAllTenants`'s pagination/search/sort shape exactly (same
  `MAX_PAGE_SIZE`, same `Sort.by("name").ascending()`, same
  `TenantSummaryDto`/`PageResponseDto` response shape, widened with a
  `deletedAt` field so the deletion timestamp REQ-20 requires is visible)
  but queries `WHERE t.deletedAt IS NOT NULL` instead. **Why two methods,
  not one method with a status flag**: mixing "list active" and "list
  deactivated" behind a single endpoint gated by one query parameter
  would require branching *inside* the permission check itself (which
  permission applies depends on the parameter's value) — a shape this
  codebase has no precedent for and that is easy to get wrong (a caller
  could probe whether a given `status` value bypasses the intended
  gate). Two distinct methods/endpoints each get one fixed, unambiguous
  `@RequiresGlobalPermission` annotation instead.
- **The deactivated listing is gated by `TENANT_DELETE`, not
  `TENANT_VIEW` alone and not the existing listing's `TENANT_ACT_AS_ANY`.**
  *Why*: `TENANT_ACT_AS_ANY` (what `listAllTenants` already uses) powers
  the staff "act as this tenant" picker — semantically wrong for
  deactivated tenants, since REQ-11 makes them unreachable regardless of
  this listing's existence; reusing it here would let an
  act-as-only-granted `STAFF` user see deletion history they have no
  other reason to see. `TENANT_VIEW` alone is this codebase's broadest,
  least-privileged view permission (a dependency of both `TENANT_EDIT`
  and `TENANT_DELETE`) — granting it is expected to stay common among
  staff who just need to look up tenant info, and "which tenants got
  deleted, and when" is closer to an audit/deletion-history concern than
  a general lookup one. `TENANT_DELETE` (which per the house rule already
  requires `TENANT_VIEW`) is the permission most tightly coupled to *why*
  a tenant would be deactivated in the first place, so this PLAN gates
  the deactivated listing on it. **This is a Tier 2 judgment call**
  (documented here, not `DECISIONS.md`, since it directly reuses
  `permission-granularity-model`'s existing three-permission set with no
  new mechanism — see that PLAN before introducing a fourth `TENANT_*`
  permission for a future, similarly narrow visibility question).

## Data schema

New migration (numbered per the "Blocking prerequisite" note above,
written here as `V25__add_tenant_soft_delete.sql`):

```sql
ALTER TABLE tenants ADD COLUMN deleted_at TIMESTAMP;
ALTER TABLE tenants_aud ADD COLUMN deleted_at TIMESTAMP;

-- tenant-creation/PLAN.md's V23 created ux_tenants_tax_id as a plain,
-- unconditional unique index; replace it with a partial one so a
-- soft-deleted tenant's taxId can be reused by a later, independent
-- tenant creation (REQ-12).
DROP INDEX IF EXISTS ux_tenants_tax_id;
CREATE UNIQUE INDEX ux_tenants_tax_id ON tenants (tax_id) WHERE deleted_at IS NULL;
```

`Tenant.java` gains `private Instant deletedAt;` (`@Column(name =
"deleted_at")`, nullable, no default) — no other schema change; edit
touches only existing columns.

## API contracts

| Method | Path | Request | Response | Status codes |
|---|---|---|---|---|
| PATCH | `/api/tenants/{tenantId}` | `EditTenantRequestDto` (below) | `TenantDetailDto` (updated identification data) | 200, 400 (invalid field(s), named), 403 (missing `TENANT_EDIT`+`TENANT_VIEW`, or `MEMBER_ADMIN`/non-staff caller), 404 (tenant doesn't exist or is soft-deleted) |
| POST | `/api/tenants/{tenantId}/deletion-confirmation-token` | — | `DeletionConfirmationTokenDto` (`{ "word": string }`, existing shared shape) | 200, 403 (missing `TENANT_DELETE`+`TENANT_VIEW`) |
| DELETE | `/api/tenants/{tenantId}` | `DeleteConfirmationRequestDto` (`{ "word": string }`, existing shared shape) | — | 200, 400 (missing/invalid/expired confirmation), 403 (missing `TENANT_DELETE`+`TENANT_VIEW`), 404 (tenant doesn't exist or already soft-deleted) |
| GET | `/api/tenants/deactivated?page=&size=&search=` | — | `PageResponseDto<TenantSummaryDto>` (widened with `deletedAt`) | 200, 403 (missing `TENANT_DELETE`+`TENANT_VIEW`, or `MEMBER_ADMIN`/non-staff caller) |

```java
record EditTenantRequestDto(
    String name,
    String legalName,
    @Email String contactEmail,
    String contactPhone,
    String postalCode,
    String street,
    String number,
    String complement,
    String neighborhood,
    String city,
    String state,
    String country
) {}
```

No `@NotBlank` on any field (all optional/partial per REQ-1) — but a
field that *is* present and is one of REQ-2's mandatory-when-creating
fields (everything except `complement`) must not be blankable down to
empty: enforced by a service-layer check (`if (request.name() != null &&
request.name().isBlank()) throw ...`), not Bean Validation, since
`@NotBlank` on an `Optional`-shaped nullable field would reject "field
omitted" the same as "field blank," which REQ-1/REQ-2 require to be
different outcomes (omitted = unchanged, blank = 400). This mirrors the
`identity-profile-model`/`ProfileEditRequestController`-style
"partial-update DTO, explicit per-field service-layer presence-vs-blank
distinction" shape rather than inventing a new one — confirm against that
controller's actual code at implementation time as the concrete pattern
to copy.

`TenantDetailDto` (new, `br.com.conectabyte.knowly.tenancy.dto`) — every
editable field plus `id`/`taxId`/`country`/`createdAt`/`updatedAt`,
mirroring `TenantSummaryDto`'s existing `from(Tenant)` static-factory
shape (extended, not duplicated, since `TenantSummaryDto` already exists
for the list view — implementation may choose to widen that DTO instead
of introducing a second one if the field sets end up identical; not
decided here, implementation's call, Tier 1).

`TenantNotFoundException` (new, 404) — thrown by `editTenant`/
`deleteTenant`/the token-generation method when `tenantRepository.findById`
misses **or** the found tenant's `deletedAt != null` (REQ-6/REQ-16) —
mapped in `TenancyExceptionHandler` alongside the existing handlers, same
pattern as `NotificationNotFoundException`.

## Dependencies

None new.

## Package/file structure

Modified:
- `Tenant.java` — add `deletedAt`.
- `TenantService.java` — add `editTenant`, `generateTenantDeletionConfirmationToken`,
  `deleteTenant`; both permission-gated methods use
  `@RequiresGlobalPermission(GlobalPermission.TENANT_EDIT)` /
  `@RequiresGlobalPermission(GlobalPermission.TENANT_DELETE)`; both
  gain `@AuditLog(action = "tenant.edit", resourceType = "Tenant")` /
  `@AuditLog(action = "tenant.delete", resourceType = "Tenant")`;
  `requireActiveMembership`/`requireTenant`/`getActiveTenant` gain the
  `deletedAt != null` → `TenantAccessDeniedException` check.
- `TenantController.java` — new `PATCH /{tenantId}`, `POST
  /{tenantId}/deletion-confirmation-token`, `DELETE /{tenantId}` handlers,
  following the exact structure of the existing member-removal trio.
- `TenantMembershipRepository.java` — add `deactivateAllByTenant(Tenant)`.
- `TenantFilterAspect.java` — **(added by AppSec review correction above,
  not in the original PLAN)** `enableTenantFilter` gains a
  `tenantRepository.findById(activeTenantId)` check; missing or
  soft-deleted tenant → enable the filter with
  `TenantFilter.NO_ACTIVE_TENANT_SENTINEL` instead of the real id
  (fail-closed, same branch already used for "staff, no active tenant").
- `TenancyExceptionHandler.java` — new `@ExceptionHandler
  (TenantNotFoundException.class)` → 404.
- `TenantRepository` — `search` query gains a `WHERE t.deletedAt IS
  NULL` clause (used by `listAllTenants`, REQ-19); new
  `searchDeactivated(String search, Pageable pageable)` query, same
  shape with `WHERE t.deletedAt IS NOT NULL` (used by the new
  `listDeactivatedTenants`, REQ-20/REQ-21 — resolved per the
  "Architectural decisions" entry above; this PLAN no longer leaves this
  open).
- `TenantService.java` also gains `listDeactivatedTenants(User actor,
  int page, int size, String search)`
  (`@RequiresGlobalPermission(GlobalPermission.TENANT_DELETE)`, same
  pagination/validation shape as `listAllTenants`).
- `TenantController.java` also gains `GET /deactivated` (must be
  registered **before** `GET /{tenantId}` mapping-wise is not a concern
  here since there is no single-tenant `GET /{tenantId}` route defined
  by this PLAN or the existing code — no path-matching ambiguity to
  worry about, but keep it visually grouped next to `listAllTenants` in
  the controller for readability).
- `TenantSummaryDto` — widened with a nullable `deletedAt` field
  (`null` for every row returned by `listAllTenants`/REQ-19's filtered
  query, populated for every row returned by `listDeactivatedTenants`),
  reusing the single DTO rather than introducing a second one, per this
  PLAN's own stated preference (see `TenantDetailDto` note above) to
  widen an existing DTO when the field sets are this close.

New:
- `br.com.conectabyte.knowly.tenancy.dto.EditTenantRequestDto`
- `br.com.conectabyte.knowly.tenancy.dto.TenantDetailDto` (or widened
  `TenantSummaryDto`, see above)
- `br.com.conectabyte.knowly.tenancy.exception.TenantNotFoundException`
- `src/main/resources/db/migration/V25__add_tenant_soft_delete.sql`
  (number pending confirmation, see "Blocking prerequisite")

## Testing strategy

- Unit — `TenantServiceTest`: `editTenant` updates only supplied fields,
  leaves others untouched; blank (not omitted) mandatory field → 400,
  no partial update persisted (mocked repository, assert `save` never
  called on the blank-field path); `taxId` never accepted/never
  persisted-if-present (DTO has no such field — compile-time guarantee,
  no runtime test needed beyond a DTO-shape assertion); edit against a
  soft-deleted tenant → `TenantNotFoundException`. `deleteTenant` sets
  `deletedAt`, calls `deactivateAllByTenant` once, both inside one
  transaction (verify via a Testcontainers integration test that a
  simulated failure after the tenant-row update rolls back the membership
  cascade too — mirrors `tenant-creation`'s atomicity test shape).
  `requireActiveMembership`/`requireTenant` reject a soft-deleted tenant's
  id the same way they reject "no access" (existing test class extended,
  not duplicated).
- Integration (`@SpringBootTest`, Testcontainers): `STAFF_ADMIN` edits
  every editable field, response reflects the change, `taxId` unchanged;
  `STAFF` granted only `TENANT_EDIT` (no `TENANT_VIEW`) → 403 on both
  generate-token and edit; `STAFF` granted both → success; `MEMBER_ADMIN`
  of any tenant → 403 on edit/delete regardless of their tenant-level
  permissions (REQ acceptance criterion, direct assertion); full
  delete flow (generate token → `DELETE` with word) soft-deletes the
  tenant, deactivates every active membership, leaves an `Article`/
  `Conversation`/`AccessGroup`/permission-grant row created before
  deletion completely unchanged (row-for-row comparison, REQ-10); a
  regular member attempting `switchActiveTenant`/staff attempting
  `requireTenant` (act-as) against the now-soft-deleted tenant → 403,
  and an `AuditEvent` with `outcome = DENIED` exists for that attempt
  (REQ-11, verifies the existing `AuditLogAspect` special-case actually
  fires here, not just asserted by code-reading); **(added by AppSec
  review) a member who already switched into a tenant *before* it was
  soft-deleted, then makes a further tenant-scoped request (e.g. `GET
  /api/articles`) within the same still-valid session, gets an empty/no-
  access result for that request** — direct integration test of the
  `TenantFilterAspect` fail-closed change, since this is the actual gap
  the switch-time checks alone don't cover; a second, independent
  `POST /api/tenants` with the same `taxId` as the now-soft-deleted
  tenant succeeds (REQ-12); delete against an already-soft-deleted or
  nonexistent tenant id → 404, no confirmation token generated/consumed;
  no volume-based rejection regardless of membership count (seed a
  tenant with many memberships, assert deletion still succeeds — direct
  REQ-18 coverage).
- Unit/Integration — REQ-19/REQ-20/REQ-21: `listAllTenants` (existing
  test class extended) excludes a soft-deleted tenant from its results
  once one exists; `listDeactivatedTenants` returns only soft-deleted
  tenants, each with a non-null `deletedAt`, and excludes active ones;
  `STAFF` without `TENANT_DELETE`+`TENANT_VIEW` (or with only
  `TENANT_ACT_AS_ANY`) → 403 on `GET /api/tenants/deactivated`;
  `STAFF_ADMIN` and `STAFF` granted `TENANT_DELETE`+`TENANT_VIEW` →
  200 with the expected rows.
- Migration regression: `V25MigrationTest` (mirrors `V17MigrationTest`/
  `V23MigrationTest` shape) — asserts `deleted_at` exists nullable on both
  `tenants` and `tenants_aud`, the old unconditional `ux_tenants_tax_id`
  index is gone, the new partial one exists and actually permits two rows
  with the same `tax_id` where one has `deleted_at` set and the other
  doesn't (raw JDBC insert, not through the JPA entity, to test the
  constraint itself rather than service-layer logic).

## Deviations from this PLAN (discovered during implementation)

- **Migration number confirmed as `V25`**, exactly as this PLAN assumed
  (`tenant-creation`'s `V23` and `permission-granularity-model`'s `V24`
  had both already landed by the time this feature started).
- **REQ-12 does require a service-layer change, contra this PLAN's
  original claim** ("no service-layer change to `createTenant` needed;
  the database constraint alone determines uniqueness scope").
  `TenantService#createTenant`'s existing proactive uniqueness check
  (`tenantRepository.existsByTaxId(...)`, run *before* any insert, to
  avoid a corrupted-persistence-context failure mode documented on that
  method) has no `deletedAt` awareness of its own — it kept rejecting a
  new tenant's `taxId` even when the only colliding row was
  soft-deleted, entirely independent of the V25 partial unique index
  this PLAN added. Fixed by renaming/scoping the derived query to
  `existsByTaxIdAndDeletedAtIsNull`, mirroring the partial index's own
  scope. Caught by task 14's REQ-12 API-level integration test (the
  SQL-level migration test alone did not catch this, since it exercises
  the constraint directly, not `createTenant`'s proactive check).
