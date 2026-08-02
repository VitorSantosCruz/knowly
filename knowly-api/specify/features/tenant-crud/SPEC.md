# SPEC — Tenant CRUD: edit and delete a tenant

> The what and the why. No technical implementation details.

## Context and motivation

`permission-granularity-model` SPEC's REQ-7 already reserved
`GlobalPermission.TENANT_VIEW`/`TENANT_EDIT`/`TENANT_DELETE` as
independent global permissions (with `TENANT_EDIT`/`TENANT_DELETE` each
requiring `TENANT_VIEW`, per that SPEC's REQ-2 house rule), but
explicitly deferred the actual business capability — "what does editing
or deleting a tenant even do" — to "a future 'tenant CRUD' SPEC," on the
grounds that this was net-new business logic, not just permission
plumbing around an existing action.

The product owner has now confirmed (2026-08-02) that this future work
happens now, in this same round, not later: this SPEC specifies that
real capability. It does **not** reopen or reinterpret anything already
decided in `permission-granularity-model` (the permission names and the
edit/delete-requires-view rule are reused verbatim, per that SPEC's own
instruction), `tenancy` (staff-only tenant creation, soft-removal
precedent for memberships), or `tenant-creation` (the full company
identification field set captured at creation) — this SPEC only adds
the missing edit/delete operations on top of those.

Today, `TenantService`/`TenantController` expose only tenant creation
and read (`listAllTenants`/`getTenant` equivalents) — there is no
`editTenant` or `deleteTenant` method anywhere in the codebase.

## Scope corrections made while writing this SPEC (documented, not silently assumed)

- **Who may edit/delete is not hardcoded to `STAFF_ADMIN` — it follows
  the same `GlobalPermission`-gated model every other staff capability
  already uses.** The request that prompted this SPEC assumed tenant
  edit/delete should default to `STAFF_ADMIN`-only because it's a
  global/platform-scope operation, not a `MEMBER_ADMIN`-inside-a-tenant
  one. That framing is correct at the *tenant-vs-member* boundary (a
  `MEMBER_ADMIN` can never edit/delete the tenant itself — only staff
  can, exactly like `tenancy` REQ-10 already restricts tenant
  *creation*), but incorrect at the *STAFF-vs-STAFF_ADMIN* boundary:
  `TENANT_CREATE` (the existing, shipped precedent for this exact
  resource) is already grantable to a plain `STAFF` user, not reserved
  to `STAFF_ADMIN` (see `PROJECT_STATUS.md`'s item-4 note: "a `STAFF`
  user granted only `TENANT_CREATE` ... was wrongly blocked from tenant
  creation" — a bug that was fixed specifically because `STAFF` +
  granted permission is the intended, working shape). `STAFF_ADMIN`
  bypasses every `GlobalPermission` check unconditionally
  (`PermissionAspect`), so `STAFF_ADMIN` can always edit/delete any
  tenant regardless of explicit grants — but a `STAFF` user explicitly
  granted `TENANT_EDIT`/`TENANT_DELETE` (which, per REQ-2's house rule,
  also requires `TENANT_VIEW`) can too. Reserving this to `STAFF_ADMIN`
  only would be a new, undocumented exception to the "fully permissioned
  `STAFF` == `STAFF_ADMIN`, except the one hardcoded staff-management
  ceiling" rule (`PROJECT_STATUS.md` item 8) — no such exception is
  requested or justified here, so this SPEC does not introduce one.
- **`taxId` is immutable after creation** (see REQ-3below) — decided
  here, not assumed away, because `tenant-creation` SPEC's own
  non-functional requirements note `taxId` is the tenant's uniquely
  identifying fiscal document, comparable in role (not sensitivity) to a
  user's CPF: it is what makes the tenant *this specific company* rather
  than some other one. Letting it be silently editable would let a
  tenant's row be repointed at a different company's legal identity
  post-hoc, with no equivalent of `identity-profile-model`'s
  self-request-plus-approval friction. This SPEC does not invent a
  correction workflow for a wrong `taxId` — see "Out of scope."
- **Delete is soft, mirroring `tenancy` REQ-19's already-established
  precedent for tenant memberships**, not a hard delete — see REQ-8
  below for the full reasoning.

## User stories

- As a `STAFF_ADMIN`, or a `STAFF` user explicitly granted
  `TENANT_EDIT` (and, per the house rule, `TENANT_VIEW`), I want to
  correct a tenant's editable identification fields (trade name, legal
  name, contact details, address) after creation, so a typo or a genuine
  business change (address move, new contact) doesn't require deleting
  and recreating the tenant.
- As a `STAFF_ADMIN`, or a `STAFF` user explicitly granted
  `TENANT_DELETE` (and `TENANT_VIEW`), I want to deactivate a tenant
  that should no longer operate (e.g. a cancelled contract), with the
  same deliberate, hard-to-misclick confirmation step every other
  deletion in this system already requires, so a single click can't
  take down an entire company's access by accident.
- As a `MEMBER_ADMIN` acting only within their own tenant, I should
  never see or be able to reach tenant edit/delete at all — that stays
  exclusively a staff/global-scope capability, exactly as tenant
  creation already is.
- As anyone relying on this system's data, I want a deleted tenant's
  members, articles, conversations, and permission model to remain
  intact and auditable, not destroyed, so the deletion is reversible in
  principle (even if this SPEC doesn't build a restore flow yet) and
  nothing downstream (billing history, audit trail, compliance record)
  silently disappears.

## Requirements (EARS/GEARS)

### Editing a tenant

- **REQ-1 [Ubiquitous]** The system shall allow updating a tenant's
  `name` (trade name / "nome fantasia"), `legalName`, `contactEmail`,
  `contactPhone`, and every structured address field (`postalCode`,
  `street`, `number`, `complement`, `neighborhood`, `city`,
  `state`/`province`, `country`) introduced by `tenant-creation` SPEC's
  REQ-1 — each independently editable, none required to be resubmitted
  together with the others in a single all-fields-mandatory payload.
- **REQ-2 [Unwanted Behavior]** If an edit request supplies a new
  `contactEmail` that is not a valid email format, or omits a
  currently-required address sub-field down to an empty/invalid value
  (any of `tenant-creation` REQ-2's mandatory fields, except
  `complement`), then the system shall reject the request with a 400
  validation error identifying every invalid/missing field and shall
  apply no change.
- **REQ-3 [Ubiquitous]** `taxId` and `country`-as-fiscal-jurisdiction
  (the value that determined which fiscal-document format was validated
  at creation, per `tenant-creation` REQ-6) shall be immutable after
  creation — no edit endpoint shall accept or apply a change to `taxId`.
- **REQ-4 [Event-Driven]** When a caller who holds `TENANT_EDIT` (and,
  per the house rule, `TENANT_VIEW`) submits a valid edit request for an
  existing tenant, the system shall apply the change and return the
  tenant's updated identification data.
- **REQ-5 [Unwanted Behavior]** If a caller without both `TENANT_EDIT`
  and `TENANT_VIEW` (or without `STAFF_ADMIN`'s unconditional bypass)
  attempts to edit a tenant, then the system shall reject the request
  (403) and apply no change.
- **REQ-6 [Unwanted Behavior]** If an edit request targets a tenant id
  that does not exist, or that has been soft-deleted (per REQ-8), then
  the system shall reject the request (404) and apply no change — a
  soft-deleted tenant is not editable back to life through this
  endpoint (see "Out of scope" for restoring a deleted tenant).
- **REQ-7 [Ubiquitous]** The system shall audit every tenant edit
  (successful or rejected) the same way every other write is audited
  today (actor, action, outcome, changed fields) — no new audit
  mechanism is introduced.

### Deleting a tenant

- **REQ-8 [Ubiquitous]** Deleting a tenant shall be a soft delete: the
  tenant is marked inactive/deleted (a new timestamp column, mirroring
  `tenancy` REQ-19's established soft-removal shape for memberships) —
  its row, its members' rows, its articles, its conversations, its
  access groups, and its permission grants are never hard-deleted by
  this action. **Why:** this is the only deletion precedent this
  codebase already has for a resource with this much attached data
  (`tenancy` REQ-19), it preserves the audit trail this project treats
  as load-bearing (`VISION.md`: "Everything is audited... because ...
  'who saw what, and when' ... will come up eventually"), and it avoids
  an irreversible, cascading hard delete across every tenant-owned table
  in the schema for an action that — unlike, say, revoking one
  permission grant — cannot be undone by simply re-granting something.
- **REQ-9 [Event-Driven]** When a tenant is soft-deleted, the system
  shall also mark every one of that tenant's currently-active
  `TenantMembership` rows as removed, using the exact same soft-removal
  mechanism `tenancy` REQ-19 already applies to individual member
  removal (no new membership-state concept) — a soft-deleted tenant has
  no active members afterward, consistent with a live tenant never
  having members with access to a dead tenant.
- **REQ-10 [Ubiquitous]** Soft-deleting a tenant shall not modify,
  archive, or delete any `Article`, `Conversation`, `AccessGroup`, or
  permission-grant row belonging to that tenant — those remain exactly
  as they were at the moment of deletion, inert only because tenant
  isolation (per `tenancy` REQ-8) and REQ-11 below make them
  unreachable, not because they were themselves altered.
- **REQ-11 [Unwanted Behavior]** If any caller — including a `STAFF`/
  `STAFF_ADMIN` acting via the existing cross-tenant "act as any tenant"
  capability (`tenancy` REQ-21) — attempts to switch their active tenant
  to, or otherwise read/act on data scoped to, a soft-deleted tenant,
  then the system shall reject the request the same way it already
  rejects a tenant the caller has no access to (per `tenancy` REQ-7),
  and log it as a security/authorization event.
- **REQ-12 [Complex]** Where a tenant is soft-deleted, while
  `POST /api/tenants` is later submitted with a `name`/`taxId` that
  matches the deleted tenant's, the system shall treat that submission
  as a **new**, independent tenant creation — `tenant-creation` SPEC's
  REQ-4/REQ-5 `taxId` uniqueness constraint applies only among
  **active** (not soft-deleted) tenants, so a legitimately closed and
  later re-onboarded company is not permanently blocked from ever having
  a `knowly` tenant again under the same fiscal identity.
- **REQ-13 [Ubiquitous]** Deleting a tenant shall require the same
  deletion-confirmation-token mechanism (`deletion-confirmation-token`
  SPEC) already required for every other delete endpoint in this
  system — generation gated by `TENANT_DELETE` (and `TENANT_VIEW`),
  validation scoped to that specific tenant instance and the calling
  user, mirroring that SPEC's REQ-13/REQ-14/REQ-15 pattern exactly
  (this is the "separate SPEC when a future delete endpoint is
  introduced" that `deletion-confirmation-token` SPEC's own "Out of
  scope" section flagged as needed).
- **REQ-14 [Event-Driven]** When a caller who holds `TENANT_DELETE` (and
  `TENANT_VIEW`) supplies a valid, unexpired, unused confirmation token
  scoped to a specific tenant, the system shall perform the soft
  deletion (REQ-8 through REQ-10) and invalidate that token, per the
  deletion-confirmation-token mechanism's own rules.
- **REQ-15 [Unwanted Behavior]** If a caller without both `TENANT_DELETE`
  and `TENANT_VIEW` (or without `STAFF_ADMIN`'s bypass) requests a
  deletion confirmation token for a tenant, or attempts the delete call
  itself, then the system shall reject the request (403) and neither
  generate a token nor perform any deletion.
- **REQ-16 [Unwanted Behavior]** If a delete request targets a tenant id
  that does not exist, or is already soft-deleted, then the system shall
  reject the request (404) and take no action.
- **REQ-17 [Ubiquitous]** The system shall audit every tenant deletion
  attempt (successful, rejected for missing/invalid confirmation, or
  rejected for insufficient permission) the same way every other
  deletion is audited today.
- **REQ-18 [Ubiquitous]** No volume-based blocking rule (e.g. "refuse to
  delete a tenant with more than N members/articles") is introduced —
  soft deletion is safe regardless of how much data the tenant owns,
  since nothing is destroyed (REQ-10); a hard-delete-shaped concern like
  "too much data to safely remove" does not apply to a mechanism that
  removes nothing.

### Visibility of a deleted tenant in staff listings

> Amendment, product owner decision (2026-08-02), resolving what
> `PLAN.md` task 16 originally left open: a soft-deleted tenant leaves
> the normal/active tenant listing entirely, but is not left with no
> trace — it surfaces in a separate, dedicated "deactivated tenants"
> listing instead. This does not reopen "Out of scope"'s "no restore
> endpoint" line below — a deactivated tenant being *listable* is not
> the same as it being *reachable* (REQ-11 already governs
> reachability, unchanged by this amendment) or *restorable*.

- **REQ-19 [Ubiquitous]** The system's normal/active tenant listing
  shall exclude every soft-deleted tenant (per REQ-8) by default — a
  soft-deleted tenant shall never appear mixed into the same listing as
  active tenants.
- **REQ-20 [Ubiquitous]** The system shall provide a separate listing of
  soft-deleted tenants, showing at least each deactivated tenant's
  identification data and the deletion timestamp (REQ-8's `deleted_at`),
  so a deleted tenant is auditable/discoverable rather than silently
  disappearing from every view.
- **REQ-21 [Unwanted Behavior]** If a caller without the permission this
  PLAN designates for viewing the deactivated-tenants listing (or
  without `STAFF_ADMIN`'s unconditional bypass) requests it, then the
  system shall reject the request (403) and return no data.

## Non-functional requirements

- Security: `TENANT_EDIT`/`TENANT_DELETE` continue to require
  `TENANT_VIEW` per `permission-granularity-model` REQ-2, enforced at
  the same `PermissionAspect`/`@RequiresGlobalPermission` layer as every
  other global-scope check — no ad hoc per-controller enforcement.
- Security: soft-deleted-tenant unreachability (REQ-11) must be enforced
  at the same fails-closed layer `tenancy` REQ-8 already relies on for
  tenant isolation (the active-tenant resolution / membership lookup
  path), not as a separate, easy-to-forget check bolted onto individual
  endpoints.
- Auditability: tenant edit/delete follow the exact same audit
  conventions (`AuditLogAspect`/`@AuditLog`, `deletion-confirmation-token`
  SPEC's audit requirements for generation/validation) already
  established for every other mutating action — no new audit mechanism.
- Migration safety: adding the soft-delete column(s) to `tenants` and
  cascading membership soft-removal must not affect any existing active
  tenant's current state — a new Flyway migration adds the column(s)
  with a safe default (not deleted) for all existing rows.

## Acceptance criteria

- [ ] A caller with `STAFF_ADMIN`, or `STAFF` + `TENANT_EDIT` +
      `TENANT_VIEW`, can update a tenant's `name`, `legalName`,
      `contactEmail`, `contactPhone`, and address fields; the change is
      persisted and returned.
- [ ] An edit request cannot change `taxId` — the field is either
      rejected if present-and-different, or silently ignored (PLAN's
      call which, not a scope decision) but never applied.
- [ ] An edit request with an invalid `contactEmail` or a blanked
      mandatory address field is rejected with 400, naming the
      offending field(s), with no partial update applied.
- [ ] A caller granted only `TENANT_EDIT` without `TENANT_VIEW` is
      denied editing a tenant (per `permission-granularity-model`
      REQ-2).
- [ ] A caller with `STAFF_ADMIN`, or `STAFF` + `TENANT_DELETE` +
      `TENANT_VIEW`, can request a deletion confirmation token for a
      tenant and, supplying it, soft-delete that tenant.
- [ ] A caller granted only `TENANT_DELETE` without `TENANT_VIEW` is
      denied both token generation and deletion.
- [ ] Soft-deleting a tenant marks every currently-active membership in
      that tenant as removed (same shape as an individual member
      removal), while leaving `Article`/`Conversation`/`AccessGroup`/
      permission-grant rows completely untouched.
- [ ] After soft-deletion, no caller (including staff via "act as any
      tenant") can switch into or read data scoped to that tenant;
      attempts are rejected and logged the same way an unauthorized
      tenant access attempt already is.
- [ ] A soft-deleted tenant does not block creating a new tenant with
      the same `taxId` later.
- [ ] Tenant deletion is gated by the same deletion-confirmation-token
      mechanism as every other delete endpoint — no confirmation token,
      no deletion.
- [ ] Every tenant edit and delete attempt (success or rejection) is
      audit-logged.
- [ ] A `MEMBER_ADMIN` (tenant-scoped role) cannot edit or delete a
      tenant under any circumstance, regardless of tenant-level
      permissions they hold within that tenant — this capability is
      exclusively global/staff-scope, mirroring `tenancy` REQ-10's
      staff-only tenant creation rule.
- [ ] After a tenant is soft-deleted, it no longer appears in the normal
      active-tenant listing, but does appear in the separate deactivated
      listing with its `deleted_at` timestamp visible.
- [ ] A caller without the deactivated-listing permission (or without
      `STAFF_ADMIN`'s bypass) requesting the deactivated listing is
      rejected with 403.

## Out of scope

- **A "restore a soft-deleted tenant" endpoint.** This SPEC only builds
  deletion; reactivating a previously soft-deleted tenant (and what
  happens to its previously-soft-removed memberships when it's
  restored) is a separate, future decision if the need arises. REQ-20's
  deactivated-tenants listing (added by the 2026-08-02 amendment above)
  makes a deleted tenant *visible* for audit/discovery purposes only —
  it does not add any action a caller can take from that listing, and in
  particular does not reopen restore as in-scope.
- **A `taxId` correction workflow.** `taxId` is immutable here (REQ-3);
  if a tenant's `taxId` was genuinely entered wrong at creation, fixing
  that today requires deleting and recreating the tenant (accepting the
  soft-delete's member/article/conversation implications) — a dedicated
  "correct an immutable identifying field" flow (comparable to how
  `identity-profile-model` handles CPF/RG corrections) is new scope, not
  built here.
- **Editing `taxId`'s fiscal-jurisdiction (`country`) or re-validating
  `taxId`'s format against a changed jurisdiction.** Since `taxId` itself
  is immutable, there is no scenario this SPEC creates where the
  jurisdiction used to validate it at creation needs re-checking.
- **A hard-delete / permanent-purge capability**, for any reason
  (GDPR/LGPD-style "right to erasure" request, storage reclamation,
  etc.) — soft delete is the only deletion mode this SPEC defines; a
  genuine hard-delete need is a new, separate Tier-3 decision given the
  amount of cross-cutting data involved, not an extension of this SPEC.
- **Any volume/size-based restriction on deletion** — see REQ-18;
  explicitly decided against, not merely unaddressed.
- **Frontend UI for tenant edit/delete** — this is a backend-only SPEC;
  `knowly-app` needs its own SPEC to build the corresponding screens,
  per this monorepo's cross-repo SPEC placement rule. `PROJECT_STATUS.md`
  already establishes that tenant CRUD stays staff-only and disappears
  from the menu entirely once staff is "inside" a tenant — that frontend
  SPEC should follow that existing rule, not redecide it.
- **Bulk edit/delete of multiple tenants in one call** — every
  requirement here is single-tenant-instance scoped; a bulk operation is
  new scope if ever requested.
- **Notifying tenant members that their tenant was deleted** — no
  notification mechanism is specified here; if this matters later, it's
  a separate SPEC (the same way `deletion-confirmation-token` SPEC
  explicitly left "notify a user whose token was invalidated" out of
  scope).
