# SPEC — Multi-tenant authorization

> The what and the why. No technical implementation details.

## Context and motivation

Every feature planned from here on (file upload, search, usage metrics,
dashboards) needs to know *whose* data it's touching. Retrofitting tenant
isolation after those features exist would mean revisiting every query,
every log line, and every metric. This feature establishes the tenant
model and session-level tenant context now, before anything else is
built on top of it.

Two kinds of people use the system:

- **Tenant users**: employees or partners of a client company (a
  "tenant"). Their data and actions are scoped to the tenant(s) they
  belong to.
- **Staff users**: ConectaByte's own employees/partners. They have no
  tenant of their own — they operate across tenants, with elevated,
  support-oriented access (e.g. the future support/account dashboard).

A single person (email) can be a member of more than one tenant at the
same time (e.g. a partner who works with several clients), so "which
tenant am I acting as right now" is a per-session choice, not a fixed
property of the account.

Within a tenant, "admin" vs. "member" (REQ-2) is a coarse distinction —
it does not by itself grant a member access to anything. Every
tenant-scoped feature is **deny-by-default** for members: a plain
member has no access to a capability until a tenant admin (or staff)
explicitly grants it, either straight to that user or by assigning them
to an **access group** (an admin-defined, named bundle of permissions).
This is deliberate: every feature built from here on — however small —
must ship behind an explicit permission check, not open by default.

## User stories

- As a tenant user who belongs to only one tenant, I want to log in and
  land directly in that tenant's context, without an extra step.
- As a tenant user who belongs to more than one tenant, I want to choose
  which tenant I'm acting in, and switch later without logging in again.
- As a tenant admin, I want to manage who has access to my own tenant
  and to specific features within it, without needing to contact
  support for routine membership or permission changes.
- As a tenant member, I only see and can act on what I've actually been
  granted — a brand-new feature doesn't quietly become available to me
  just because it shipped.
- As ConectaByte staff, I want to operate without being scoped to a
  single tenant, since my job is supporting all of them.
- As anyone, I want it to be impossible — not just disallowed by
  convention — for one tenant's data to leak into another tenant's view.

## Requirements (EARS/GEARS)

- **REQ-1 [Ubiquitous]** The system shall represent a tenant as a
  distinct entity (one client organization).
- **REQ-2 [Ubiquitous]** The system shall allow a user to hold
  membership in zero, one, or multiple tenants. Each membership shall
  carry exactly one role: tenant admin or tenant member.
- **REQ-3 [Ubiquitous]** The system shall support users with no tenant
  membership at all ("staff"), distinguished by a global, non-tenant
  role rather than any tenant membership.
- **REQ-4 [Event-Driven]** When a user with exactly one tenant
  membership logs in, the system shall set that tenant as the active
  tenant for the session automatically.
- **REQ-5 [Event-Driven]** When a user with more than one tenant
  membership logs in, the system shall require them to select an active
  tenant before reaching any tenant-scoped screen or data.
- **REQ-6 [Event-Driven]** When a user requests to switch their active
  tenant to one they hold membership in, the system shall update the
  session's active tenant without requiring re-authentication.
- **REQ-7 [Unwanted Behavior]** If a user requests to switch to (or
  otherwise access data under) a tenant they do not hold membership in,
  then the system shall reject the request and log it as a security
  event, the same way other authorization failures are logged.
- **REQ-8 [Ubiquitous]** Every tenant-scoped query, log line, and future
  metric shall carry the active tenant's identity. No code path may
  read tenant-scoped data without an active tenant in context, except
  for staff users acting in their cross-tenant capacity.
- **REQ-9 [Optional Feature]** Where a user holds the tenant-admin role
  for a tenant, the system shall let them manage (add/remove/change the
  role of) other users' membership within that tenant only — never
  another tenant's — including managing another tenant admin (a tenant
  is never left unable to manage itself just because one admin can't
  touch another's membership).
- **REQ-10 [Ubiquitous]** Only staff users shall be able to create a new
  tenant. There is no self-service tenant signup. Creating a tenant
  shall always include designating its first user as tenant admin in
  the same action — a tenant is never created without an admin.
- **REQ-11 [Ubiquitous]** The system shall audit every tenant membership
  change (grant, revoke, role change) and every active-tenant switch,
  per the existing audit conventions (actor, action, outcome).
- **REQ-12 [Ubiquitous]** A tenant member shall have no access to any
  tenant-scoped feature or action by default. Access exists only where
  explicitly granted (REQ-14).
- **REQ-13 [Ubiquitous]** The system shall support tenant-scoped access
  groups: an admin-defined, named bundle of permissions that can be
  assigned to any number of users within that tenant.
- **REQ-14 [Event-Driven]** When a tenant admin (or a staff user) grants
  a permission directly to a user, or assigns/removes an access group,
  the system shall update that user's effective access immediately,
  without requiring them to log in again.
- **REQ-15 [Ubiquitous]** A user's effective permission set for a tenant
  shall be the union of permissions granted directly to them and
  permissions granted via every access group they belong to in that
  tenant.
- **REQ-16 [Ubiquitous]** Only tenant admins (within their own tenant)
  and staff users (across any tenant) shall be able to grant/revoke
  permissions, direct or via access group. A plain member can never
  grant access to themselves or anyone else.
- **REQ-17 [Unwanted Behavior]** If a user attempts an action they have
  not been explicitly granted (directly or via an access group), then
  the system shall deny it and log it as an authorization failure, the
  same way other authorization failures are logged.
- **REQ-18 [Ubiquitous]** Permissions shall be independent per action
  (e.g. view/create/edit/delete on a given resource type are each
  granted separately). No permission implies any other, and having
  created or otherwise being associated with a specific record confers
  no additional right over it — access is always exactly what was
  explicitly granted, never inferred from ownership.
- **REQ-19 [Ubiquitous]** Removing a user's tenant membership shall
  always be a soft removal: the user loses access and stops appearing
  as an active member, but their record and their entire audit history
  remain in the system, queryable indefinitely.
- **REQ-20 [Ubiquitous]** The system shall record an audit event for
  every read (view, list) and every write (create, edit, delete,
  permission grant/revoke, membership change) performed by any user —
  not writes only. This is the dataset future reporting features will
  read from, not just a debugging aid.

## Non-functional requirements

- Security: tenant isolation must be enforced at a layer that fails
  closed and is hard to bypass by accident (e.g. an ORM-level filter
  applied globally), not by remembering to add a `WHERE tenant_id = ?`
  in every new query as features get built.
- Observability: the active tenant id becomes a first-class dimension
  everywhere the constitution already requires structured logging (see
  "Observability and audit" in the constitution) — this feature's real
  deliverable is making that dimension exist and be trustworthy.
- Auditability: tenant membership itself follows the same Envers + JPA
  Auditing convention already used for `User`. Envers/JPA Auditing only
  captures entity *state changes* — it has no way to record that
  someone merely viewed or listed something. REQ-20's read-plus-write
  coverage therefore needs its own append-only audit event record,
  independent of Envers, populated on every read and write alike.
- Extensibility: every new tenant-scoped feature must ship with an
  explicit permission check that defaults to "no access" for tenant
  members. Access is opt-in (direct grant or access group), never
  opt-out — a feature with no permission check wired up must behave as
  if members are denied, not as if they're allowed.

## Acceptance criteria

- [x] A user with one tenant membership logs in and lands directly in
      that tenant's context. (`TenantSessionIntegrationTest`)
- [x] A user with multiple tenant memberships is prompted to choose an
      active tenant before seeing any tenant-scoped data.
      (`TenantSessionIntegrationTest`)
- [x] Switching active tenant works without a new login, and only
      between tenants the user actually belongs to.
      (`TenantSessionIntegrationTest`)
- [x] Attempting to act under a tenant the user doesn't belong to is
      rejected and produces an audit log entry.
      (`TenantSessionIntegrationTest`)
- [x] A tenant admin can add/remove members within their own tenant;
      attempting this on another tenant is rejected.
      (`TenantManagementIntegrationTest`) — role *change* on an
      existing member reuses the same `addMember` path but isn't
      covered by its own dedicated test yet.
- [x] Only staff users can create a tenant; the endpoint/action rejects
      tenant users regardless of role. (`TenantManagementIntegrationTest`)
- [x] A query for tenant-scoped data with no active tenant in context
      returns nothing (fails closed), rather than erroring in a way
      that could be caught and ignored.
      (`TenantIsolationIntegrationTest`)
- [x] A plain tenant member has no access to a tenant-scoped feature
      until a tenant admin or staff grants it, directly or via an
      access group. (`PermissionAspectTest`)
- [x] Granting/revoking an access group immediately changes access for
      every member currently assigned to it — guaranteed by
      `PermissionService.effectivePermissions` never caching across
      requests. (`PermissionServiceTest`)
- [x] A plain member cannot grant themselves or anyone else any
      permission or access group membership; only tenant admins (own
      tenant) and staff (any tenant) can. (`TenantManagementIntegrationTest`)
- [x] A user who has created a record but lacks the specific permission
      for an action (e.g. delete) on that record type is still denied
      — permissions carry no ownership override anywhere in the model
      (no "owner" concept exists on any grant). (`PermissionServiceTest`)
- [x] Removing a user's membership leaves their record and audit
      history queryable; no membership removal path hard-deletes a
      user. (`TenantManagementIntegrationTest`)
- [x] Viewing or listing tenant-scoped data produces an audit event,
      the same as creating, editing, or deleting it — proven for the
      general `@AuditLog` mechanism (`AuditLogAspectTest`); no real
      tenant-scoped read/listing feature exists yet to attach it to
      (that's for the next feature, article management, to wire up).

## Out of scope

- Self-service tenant signup/onboarding (staff-provisioned only, per
  REQ-10).
- Billing/plan differences per tenant.
- The support/account dashboard UI itself (this feature only builds the
  data model and session mechanics it depends on).
- File upload, search, usage metrics (later features that will build on
  top of the tenant context and permission model this feature
  establishes) — this feature defines *how* permissions work, not the
  specific permissions those future features will need.
- Per-resource sharing rules (e.g. "share this one file with this one
  user") — access groups and direct grants are feature/action-level,
  not per-record; per-record sharing isn't requested yet.
