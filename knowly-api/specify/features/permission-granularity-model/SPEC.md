# SPEC — permission-granularity-model

> The what and the why. No technical implementation details.

## Context and motivation

`tenancy` SPEC's REQ-18 and `staff-rbac-split` SPEC's REQ-3 both
originally said the same thing at their respective scopes: every
CRUD-shaped action on a resource (view/list, create, edit, delete) is an
independent, separately-grantable permission, and **no permission implies
any other**. That was a deliberate design choice at the time (see the
original REQ-18 wording), meant to prevent the opposite failure mode —
coarse permissions silently bundling unrelated capabilities.

The product owner has now explicitly reversed part of that decision
(confirmed 2026-08-02): the "fully independent" model was judged
illogical specifically for edit and delete, because **a user cannot
meaningfully identify what to edit or delete without first being able to
see/locate it.** Concretely: it made no sense for a permission set to
allow "delete article #42" while disallowing "see that article #42
exists" — there is no way to act on a resource you cannot locate.

This SPEC is the single canonical statement of the corrected rule,
referenced (not duplicated) by `tenancy` SPEC REQ-18 and
`staff-rbac-split` SPEC REQ-3, and it inventories every resource in the
codebase today that does not yet comply with it — including resources
where the required view/create/edit/delete permissions don't fully
exist yet.

This is a Tier 3 reversal, explicitly confirmed by the product owner,
not an AI-driven reinterpretation — see `../../../DECISIONS.md`'s new
2026-08-02 entry for the full record of that confirmation.

## User stories

- As a product owner, I want "can edit/delete X" to always require "can
  view X" as well, for every resource in the system, so that granting
  edit/delete access without view access — which was previously possible
  and made no operational sense — can no longer happen.
- As a platform engineer, I want one authoritative place that defines
  this dependency rule, so every other SPEC that talks about permissions
  references it instead of re-stating (and risking drifting from) it.
- As a platform engineer, I want a concrete, resource-by-resource list of
  what's out of compliance with the new rule today, so implementation
  work can be planned and tracked without re-deriving the gap analysis
  from scratch.

## Requirements (EARS/GEARS)

### Canonical rule (referenced by `tenancy` REQ-18, `staff-rbac-split` REQ-3)

- **REQ-1 [Ubiquitous]** For every resource in the system that exposes
  view/list, create, edit, and delete as distinct actions (tenant-scoped
  `Permission` or global `GlobalPermission`), each of the four actions
  shall remain its own separately-grantable permission — granularity
  itself is unchanged from the original model.
- **REQ-2 [Complex]** Where a resource has both an edit or delete
  permission and a corresponding view/list permission defined, while a
  caller is being evaluated for authorization to perform the edit or
  delete action, when the check runs, the system shall require the
  caller to hold **both** the edit/delete permission **and** the
  corresponding view/list permission for that same resource — denying
  the action if either is missing, even if the other is present.
- **REQ-3 [Ubiquitous]** View/list and create permissions shall remain
  fully independent: holding a view/list permission shall not require or
  imply any other permission, and holding a create permission shall not
  require or imply view/list, edit, or delete for that same resource.
- **REQ-4 [Ubiquitous]** Granting or revoking edit/delete alone (without
  touching the corresponding view/list grant) shall never itself
  implicitly grant or revoke that view/list permission — REQ-2's
  dependency is enforced only at authorization-check time, not by
  auto-granting or auto-revoking the view/list permission as a side
  effect of an edit/delete grant/revoke.
- **REQ-5 [Unwanted Behavior]** If a caller holds an edit or delete
  permission for a resource but not the corresponding view/list
  permission, then any attempt to perform that edit or delete action
  shall be denied and logged as an authorization failure, the same way
  other authorization failures are logged — regardless of how the
  edit/delete permission was granted (directly, via access group, via
  global permission group).

### Per-resource gap analysis (2026-08-02 codebase state)

The following resources exist today; each is assessed against REQ-1
through REQ-3 as of this SPEC's writing.

- **REQ-6 [Ubiquitous]** `Article` (tenant-scoped `Permission.ARTICLE_VIEW`/
  `ARTICLE_CREATE`/`ARTICLE_EDIT`/`ARTICLE_DELETE`) already has all four
  permissions as independent grants (`ArticleController`,
  `PermissionAspect`) — the only gap is that REQ-2's dependency
  (edit/delete requiring view) does not exist anywhere in the
  authorization path (neither the aspect nor the controller). The system
  shall enforce that a caller performing `ARTICLE_EDIT` or
  `ARTICLE_DELETE` on a tenant's article also holds `ARTICLE_VIEW` for
  that tenant.
- **REQ-7 [Ubiquitous]** `Tenant` (global scope) today has only
  `GlobalPermission.TENANT_CREATE` — no `TENANT_VIEW`, `TENANT_EDIT`, or
  `TENANT_DELETE` exist, and the domain itself has no tenant-edit or
  tenant-delete capability at all yet (`TenantService` has no `editTenant`/
  `deleteTenant` method). The system shall gain `GlobalPermission.TENANT_VIEW`,
  `TENANT_EDIT`, and `TENANT_DELETE` as independent global permissions,
  with `TENANT_EDIT`/`TENANT_DELETE` each requiring `TENANT_VIEW` per
  REQ-2. **Decision (made here, not silently expanded elsewhere):**
  because tenant edit/delete are net-new business capabilities — not
  just new permission plumbing around an existing action, unlike every
  other gap in this SPEC — the actual edit/delete operations (what
  fields are editable, what a tenant "delete" even means given REQ-19's
  existing soft-delete precedent for memberships, cascading effects on
  members/articles/conversations) are **out of scope for this SPEC** and
  belong to their own future "tenant CRUD" business-logic SPEC. This SPEC
  only commits to the *permission* shape (`TENANT_VIEW`/`_EDIT`/`_DELETE`
  existing and obeying REQ-2) so that whenever that future SPEC is
  written, the permission model it needs already exists and already
  follows the house rule; it does not itself add tenant edit/delete
  capability to the product.
- **REQ-8 [Ubiquitous]** Staff users (`GlobalPermission.STAFF_USER_CREATE`/
  `STAFF_USER_VIEW` exist today; `STAFF_USER_EDIT`/`STAFF_USER_DELETE` do
  not). The system shall gain `GlobalPermission.STAFF_USER_EDIT` and
  `STAFF_USER_DELETE` as independent global permissions, each requiring
  `STAFF_USER_VIEW` per REQ-2. `staff-rbac-management-operations` SPEC
  already specifies the staff-user-deletion business logic and its
  `STAFF` ceiling (a `STAFF` user can never target a `STAFF`/`STAFF_ADMIN`
  account) — this SPEC does not restate or change that logic, it only
  requires that whatever permission gates that deletion (once
  `STAFF_USER_DELETE` exists) also requires `STAFF_USER_VIEW` on the same
  check.
- **REQ-9 [Ubiquitous]** Tenant members (`GlobalPermission.TENANT_MEMBER_MANAGE_ANY`
  today bundles add/edit(role change)/remove into one permission). The
  system shall replace `TENANT_MEMBER_MANAGE_ANY` with four independent
  permissions — `TENANT_MEMBER_VIEW`, `TENANT_MEMBER_CREATE` (adding a
  member), `TENANT_MEMBER_EDIT` (role change), `TENANT_MEMBER_DELETE`
  (removal, still a soft removal per `tenancy` REQ-19) — with
  `TENANT_MEMBER_EDIT`/`TENANT_MEMBER_DELETE` each requiring
  `TENANT_MEMBER_VIEW` per REQ-2, and `TENANT_MEMBER_CREATE` remaining
  fully independent per REQ-3.
- **REQ-10 [Ubiquitous]** Access groups (`GlobalPermission.TENANT_ACCESS_GROUP_MANAGE_ANY`
  today bundles create/assign-permission/list into one permission). The
  system shall replace `TENANT_ACCESS_GROUP_MANAGE_ANY` with four
  independent permissions — `TENANT_ACCESS_GROUP_VIEW` (listing groups
  and their assigned permissions), `TENANT_ACCESS_GROUP_CREATE`,
  `TENANT_ACCESS_GROUP_EDIT` (covering both renaming a group and
  changing which permissions it grants), `TENANT_ACCESS_GROUP_DELETE` —
  with `TENANT_ACCESS_GROUP_EDIT`/`TENANT_ACCESS_GROUP_DELETE` each
  requiring `TENANT_ACCESS_GROUP_VIEW` per REQ-2, and
  `TENANT_ACCESS_GROUP_CREATE` remaining fully independent per REQ-3.
- **REQ-11 [Ubiquitous]** Permission grants (`GlobalPermission.TENANT_PERMISSION_GRANT_MANAGE_ANY`
  today bundles granting, revoking, assigning/unassigning access groups
  to members, and viewing a member's effective permissions into one
  permission). Granting a permission (or assigning an access group to a
  member) is treated as this resource's "create" action; revoking a
  permission (or unassigning an access group from a member) is treated
  as this resource's "delete" action. The system shall replace
  `TENANT_PERMISSION_GRANT_MANAGE_ANY` with `TENANT_PERMISSION_GRANT_VIEW`
  (viewing a member's current direct grants/access-group assignments —
  `getMemberDetail`'s existing capability, currently folded into
  `TENANT_PERMISSION_GRANT_MANAGE_ANY` itself and needing to become its
  own permission), `TENANT_PERMISSION_GRANT_CREATE` (granting a
  permission directly, or assigning an access group), and
  `TENANT_PERMISSION_GRANT_DELETE` (revoking a permission directly, or
  unassigning an access group) — with `TENANT_PERMISSION_GRANT_DELETE`
  requiring `TENANT_PERMISSION_GRANT_VIEW` per REQ-2 (deleting/revoking a
  grant requires being able to see current grants), and
  `TENANT_PERMISSION_GRANT_CREATE` remaining fully independent per
  REQ-3 (granting a new permission does not require having first viewed
  existing grants).

## Non-functional requirements

- Security: REQ-2's dependency must be enforced at the same
  authorization layer that already enforces every other permission check
  (`PermissionAspect`/`@RequiresPermission` for tenant-scoped resources,
  the equivalent global-permission check for staff-scoped resources) —
  not duplicated ad hoc per controller, to avoid the same
  easy-to-forget-in-one-place failure mode `DECISIONS.md` already warns
  about for tenant isolation.
- Backward compatibility: every existing direct grant or access-group
  membership that already includes both view and edit/delete for a
  resource is unaffected by this change — REQ-2 only changes the outcome
  for the (previously valid, now invalid) case of holding edit/delete
  without view.
- Migration safety: replacing a bundled permission (e.g.
  `TENANT_MEMBER_MANAGE_ANY`) with four granular ones must not silently
  strip access from an existing holder of the bundled permission — any
  migration must grant the holder all four new permissions in place of
  the one bundled one, preserving today's effective access at cutover
  time (narrowing access later, if desired, is a separate, deliberate
  admin action, not a side effect of this migration).
- Observability: every new permission introduced by this SPEC is subject
  to the same audit-on-grant/revoke and audit-on-denial requirements
  already established (`tenancy` REQ-11/REQ-17/REQ-20,
  `staff-rbac-split` REQ-6).

## Acceptance criteria

- [ ] A caller granted `ARTICLE_EDIT` (or `ARTICLE_DELETE`) without
      `ARTICLE_VIEW` for a tenant is denied editing (or deleting) an
      article in that tenant.
- [ ] A caller granted only `ARTICLE_CREATE` (no `ARTICLE_VIEW`) can
      still create an article — REQ-3's independence for create is
      unaffected by REQ-2.
- [ ] `GlobalPermission.TENANT_VIEW`/`TENANT_EDIT`/`TENANT_DELETE` exist;
      a `STAFF` user granted `TENANT_EDIT`/`TENANT_DELETE` without
      `TENANT_VIEW` is denied the corresponding action once that action
      exists (tracked, not built, per REQ-7's scope note).
- [ ] `GlobalPermission.STAFF_USER_EDIT`/`STAFF_USER_DELETE` exist; a
      `STAFF` user granted either without `STAFF_USER_VIEW` is denied.
- [ ] `TENANT_MEMBER_MANAGE_ANY` is replaced by
      `TENANT_MEMBER_VIEW`/`_CREATE`/`_EDIT`/`_DELETE`; a caller granted
      `TENANT_MEMBER_EDIT` (role change) or `TENANT_MEMBER_DELETE`
      (removal) without `TENANT_MEMBER_VIEW` is denied; a caller granted
      only `TENANT_MEMBER_CREATE` can still add a member without
      `TENANT_MEMBER_VIEW`.
- [ ] `TENANT_ACCESS_GROUP_MANAGE_ANY` is replaced by
      `TENANT_ACCESS_GROUP_VIEW`/`_CREATE`/`_EDIT`/`_DELETE`; a caller
      granted `TENANT_ACCESS_GROUP_EDIT` or `_DELETE` without
      `TENANT_ACCESS_GROUP_VIEW` is denied; a caller granted only
      `TENANT_ACCESS_GROUP_CREATE` can still create a group.
- [ ] `TENANT_PERMISSION_GRANT_MANAGE_ANY` is replaced by
      `TENANT_PERMISSION_GRANT_VIEW`/`_CREATE`/`_DELETE`; a caller
      granted `TENANT_PERMISSION_GRANT_DELETE` (revoke a grant/unassign
      an access group) without `TENANT_PERMISSION_GRANT_VIEW` is denied;
      a caller granted only `TENANT_PERMISSION_GRANT_CREATE` can still
      grant a permission/assign an access group.
- [ ] A migration existing for every bundled-permission replacement above
      grants all four (or three, for permission-grants) new permissions
      to every existing holder of the old bundled permission, verified by
      a test asserting effective access is unchanged immediately after
      migration.
- [ ] Every denial produced by REQ-2/REQ-5 is logged as an authorization
      failure via the existing audit mechanism, same shape as any other
      permission denial.

## Out of scope

- The actual tenant edit/delete business logic (what fields are
  editable, cascading effects, whether delete is soft or hard) — see
  REQ-7's explicit scope note; that's a future "tenant CRUD" SPEC's
  responsibility, this SPEC only pre-builds the permission shape.
- The actual staff-user deletion business logic and its `STAFF` ceiling
  — already specified in `staff-rbac-management-operations` SPEC; this
  SPEC only adds the view-dependency to whatever permission gates it.
- Any change to the four Article permissions' existence or naming — they
  already exist independently; this SPEC only adds the view-dependency
  for edit/delete (REQ-6).
- Any frontend UI change (permission-picker screens, access-group
  management UI) — this is a backend-only permission-model SPEC; a
  frontend SPEC will follow separately if any UI needs to change to
  reflect the new granular permissions replacing the old bundled ones.
- Retroactively re-evaluating whether any *other*, not-yet-built future
  resource should follow this same view-implies-edit/delete pattern —
  REQ-2 is stated generally enough to apply automatically to any future
  resource that ships with both a view/list and an edit/delete
  permission, so no separate per-future-resource SPEC amendment should
  be needed, but this SPEC does not itself audit resources that don't
  exist yet.
