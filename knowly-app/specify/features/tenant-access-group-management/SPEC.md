# SPEC — tenant-access-group-management

> The what and the why. No technical implementation details.

## Context and motivation

Tenant-scoped access groups (`AccessGroup`, distinct from the *global*
staff access groups already managed at `/staff/access-groups`) today
have no screen of their own. A tenant's `MEMBER_ADMIN` (or a staff
caller acting with the tenant's granular permissions) can only:

- create a group, and assign/unassign it to one member at a time — both
  buried inside `member-detail-panel.component.ts`'s "Access groups"
  section, one member's detail view at a time.
- grant a permission to a group (`POST
  /api/tenants/{tenantId}/access-groups/{accessGroupId}/permissions`
  already exists server-side) — but nothing in the frontend calls it;
  there is no UI for it at all today.
- there is no way to see a group's full member roster in one place, no
  way to assign a member to several groups without repeating the
  per-member flow once per group, and no way to delete a group at all.

This mirrors the exact problem `staff-members-management-redesign`
already solved for *global* access groups — access groups as their own
manageable entity, not something you can only see one member's slice of
at a time — applied here to the tenant-scoped equivalent. This feature
gives tenant access groups their own screen (`/tenants/access-groups` or
equivalent), following the same list/detail pattern as
`access-group-management-page.component.ts`, plus two capabilities that
screen doesn't need but this one does: assigning one member to several
groups in a single action, and deleting a group outright.

**Two new backend (`knowly-api`) capabilities do not exist yet** and are
therefore **dependencies of this SPEC, not something this SPEC
designs**: a bulk multi-group assignment endpoint, and a group delete
endpoint with cascading soft-delete. See "Dependencies" below. This
SPEC's requirements that build on them are written against the *shape*
of what's needed, not a finished contract — the actual backend SPEC is
separate work, owned by `knowly-api`.

## User stories

- As a tenant `MEMBER_ADMIN` (or a staff caller with the equivalent
  granular tenant permissions), I want to see all of a tenant's access
  groups and each group's member roster in one screen, so that I don't
  have to open every member's detail panel to understand who's in which
  group.
- As a tenant `MEMBER_ADMIN`, I want to create a new access group from
  this screen, so that I don't need to go through a member's detail
  panel to do it.
- As a tenant `MEMBER_ADMIN`, I want to grant a permission to an access
  group, so that every member of that group inherits it, instead of
  granting the same permission to each member individually.
- As a tenant `MEMBER_ADMIN`, I want to assign a member to several
  access groups in one action, so that onboarding a member into multiple
  groups doesn't take one confirmation per group.
- As a tenant `MEMBER_ADMIN`, I want to remove a member from a group
  from this screen (not just from the member's own detail panel), so
  that group management is possible from either direction.
- As a tenant `MEMBER_ADMIN`, I want to delete an access group that's no
  longer needed, so that stale groups don't accumulate and keep granting
  permissions or showing up as an assignment option.
- As a tenant `MEMBER_ADMIN` without `TENANT_ACCESS_GROUP_VIEW`, I do not
  want to be able to open this screen at all, so that the permission
  model stays consistent with the rest of the app (mirroring
  `TENANT_MEMBER_VIEW` gating the Members screen).

## Dependencies

- **REQ-9, REQ-10, REQ-11** (bulk assignment) depend on a new
  `knowly-api` endpoint, not yet specified, that accepts a single
  membership and a set of access-group ids and assigns all of them in
  one call (proposed shape only, for illustration — the backend SPEC
  owns the final contract: `POST
  /api/tenants/{tenantId}/members/{membershipId}/access-groups:batch`
  with a body such as `{ "accessGroupIds": [1, 2, 3] }`). Client-side
  N-sequential-calls was explicitly rejected as the solution (confirmed
  by the user) — this frontend feature cannot ship the bulk-assign
  requirements until that backend endpoint exists.
- **REQ-13, REQ-14, REQ-15** (delete group) depend on a new `knowly-api`
  endpoint, not yet specified, that deletes an access group and
  cascades a soft-delete to its `UserAccessGroup` (member-assignment)
  and `AccessGroupPermission` (permission-grant) rows, per
  `DECISIONS.md`'s standing system-wide logical-delete rule (proposed
  shape only: `DELETE
  /api/tenants/{tenantId}/access-groups/{accessGroupId}`, likely with
  the same delete-confirmation-token pattern already used for member
  removal and group unassignment elsewhere in this tenant). This
  frontend feature cannot ship the delete requirements until that
  backend endpoint exists.
- All other requirements in this SPEC (list, create, grant permission,
  single assign/unassign) depend only on `knowly-api` endpoints that
  already exist today (see `TenantController.java`'s
  `/{tenantId}/access-groups*` and
  `/{tenantId}/members/{membershipId}/access-groups*` mappings).

## Requirements (EARS/GEARS)

1. **[Ubiquitous]** The system shall provide a dedicated screen listing
   every access group belonging to the tenant currently active in the
   caller's session.
2. **[State-Driven]** While the caller lacks `TENANT_ACCESS_GROUP_VIEW`
   for the active tenant, the system shall prevent the screen from
   opening and shall not issue any of this screen's list/detail
   requests, mirroring how `TENANT_MEMBER_VIEW` gates the Members
   screen.
3. **[Event-Driven]** When the caller selects a group from the list, the
   system shall display that group's full member roster (every tenant
   member currently assigned to it) and, separately, the tenant members
   not currently assigned to it.
4. **[Event-Driven]** When the caller holding
   `TENANT_ACCESS_GROUP_CREATE` submits a new group name, the system
   shall create the group and add it to the list.
5. **[Unwanted Behavior]** If a caller without
   `TENANT_ACCESS_GROUP_CREATE` attempts to create a group, then the
   system shall not perform the creation and shall surface the
   permission-denied outcome the same way every other gated action in
   this app already does.
6. **[Event-Driven]** When the caller holding
   `TENANT_PERMISSION_GRANT_CREATE` grants a permission to a selected
   group, the system shall submit that grant and reflect it against the
   group.
7. **[Event-Driven]** When the caller holding
   `TENANT_PERMISSION_GRANT_CREATE` assigns a tenant member to a single
   selected group, the system shall submit that single assignment using
   the existing `POST
   /api/tenants/{tenantId}/members/{membershipId}/access-groups/{accessGroupId}`
   endpoint.
8. **[Event-Driven]** When the caller holding
   `TENANT_PERMISSION_GRANT_DELETE` unassigns a member from a group on
   this screen, the system shall require the same delete-confirmation
   token/phrase flow already used for group unassignment in
   `member-detail-panel.component.ts`, and shall submit the unassignment
   only once confirmed.
9. **[Event-Driven]** When the caller holding
   `TENANT_PERMISSION_GRANT_CREATE` selects more than one access group
   to assign a single tenant member to in one action, the system shall
   submit all selected group ids in a single request to the new
   bulk-assignment endpoint (see "Dependencies"), not one request per
   group.
10. **[Unwanted Behavior]** If the bulk-assignment request partially or
    fully fails, then the system shall report the failure without
    silently treating any subset of the requested groups as assigned,
    and shall reflect on-screen only the assignments the backend
    confirms actually happened (re-fetch group membership after the
    call rather than optimistically assuming success).
11. **[Unwanted Behavior]** If a caller without
    `TENANT_PERMISSION_GRANT_CREATE` attempts a bulk assignment, then the
    system shall not perform it and shall surface the permission-denied
    outcome consistently with every other gated action in this app.
12. **[State-Driven]** While no access group is selected, the system
    shall not display the member roster/assignment section at all
    (mirroring `access-group-management-page.component.ts`'s existing
    `selectedGroup()`-gated pattern).
13. **[Event-Driven]** When the caller holding
    `TENANT_ACCESS_GROUP_DELETE` requests to delete a group, the system
    shall require the same delete-confirmation token/phrase flow already
    used elsewhere in this tenant (e.g. member removal, group
    unassignment) before submitting the delete to the new backend
    delete endpoint (see "Dependencies").
14. **[Event-Driven]** When a group delete is confirmed and submitted
    successfully, the system shall remove the group from the list and,
    if it was the currently selected group, clear the selection and
    close the member roster/assignment section.
15. **[Unwanted Behavior]** If a caller without
    `TENANT_ACCESS_GROUP_DELETE` attempts to delete a group, then the
    system shall not perform the deletion and shall surface the
    permission-denied outcome consistently with every other gated action
    in this app.
16. **[Unwanted Behavior]** If any request on this screen (list groups,
    load roster, create, grant permission, assign, unassign, bulk-assign,
    delete) fails with a network error rather than a permission denial,
    then the system shall surface the app's existing generic network
    error state, consistent with every other screen in this app.
17. **[Ubiquitous]** The system shall gate every individual action on
    this screen by that action's own existing granular permission
    (`TENANT_ACCESS_GROUP_CREATE`, `TENANT_ACCESS_GROUP_DELETE`,
    `TENANT_PERMISSION_GRANT_CREATE`, `TENANT_PERMISSION_GRANT_DELETE`)
    exactly as those permissions already gate the equivalent actions in
    `member-detail-panel.component.ts` — holding
    `TENANT_ACCESS_GROUP_VIEW` (REQ-2) never implicitly grants any other
    action's permission; it only governs whether the screen/list itself
    is reachable, the same relationship `TENANT_MEMBER_VIEW` already has
    to the Members screen's own per-action permissions. This is not a
    new convention: it's the existing house-wide rule from
    `../../../../DECISIONS.md`'s "Permission granularity model reversed"
    entry (2026-08-02) — view/list and create stay independent,
    edit/delete additionally require view/list on the same resource,
    already named there as applying to access groups specifically. This
    requirement exists only to make that dependency explicit for this
    screen's own acceptance criteria, not to introduce a new rule.

## Non-functional requirements

- Accessibility: keyboard-operable list selection, group creation form,
  and confirm dialogs; WCAG AA contrast on all new UI, consistent with
  the rest of the app's existing components (`SharedListComponent`,
  `ConfirmDialogComponent`).
- Performance: the roster fetch for a selected group must not require
  more requests than the tenant already makes for its member list
  today; no new N+1 pattern beyond what `listMembers`/`getDetail`
  already require, since (unlike the global staff screen's forced N+1)
  a tenant-scoped member roster can be derived from data already
  fetchable per-member without one call per candidate, if the PLAN opts
  to reuse `getMember`/`listMembers` accordingly — that per-request
  shape is a PLAN decision, not fixed here.
- Responsiveness: same breakpoints as the rest of the app (mobile,
  tablet, desktop), matching `access-group-management-page.component.ts`'s
  existing layout conventions.

## Acceptance criteria

- [ ] A caller with `TENANT_ACCESS_GROUP_VIEW` can open the screen and
      see every access group in the active tenant.
- [ ] A caller without `TENANT_ACCESS_GROUP_VIEW` cannot open the screen
      (route-guarded, mirroring `TENANT_MEMBER_VIEW`/Members).
- [ ] Selecting a group shows its current members and the tenant members
      not yet in it.
- [ ] A caller with `TENANT_ACCESS_GROUP_CREATE` can create a new group;
      a caller without it cannot.
- [ ] A caller with `TENANT_PERMISSION_GRANT_CREATE` can grant a
      permission to a group.
- [ ] A caller with `TENANT_PERMISSION_GRANT_CREATE` can assign a member
      to one group, and to several groups in a single bulk action (once
      the backend bulk endpoint exists); a caller without that permission
      can do neither.
- [ ] A caller with `TENANT_PERMISSION_GRANT_DELETE` can unassign a
      member from a group, behind the existing confirmation-phrase flow.
- [ ] A caller with `TENANT_ACCESS_GROUP_DELETE` can delete a group
      behind a confirmation-phrase flow (once the backend delete endpoint
      exists); the deleted group and its assignments/permission grants no
      longer appear anywhere in the tenant's active data.
- [ ] A caller without `TENANT_ACCESS_GROUP_DELETE` cannot delete a
      group.
- [ ] Every action's permission check is independent — no single
      permission implicitly grants access to another action beyond the
      view/list prerequisite already established for this pattern.

## Out of scope

- Renaming/editing an access group's name. `TENANT_ACCESS_GROUP_EDIT`
  already exists as a defined permission but no edit capability was
  requested for this feature and none is designed here — a future SPEC
  change, not a silent addition to this one.
- Revoking a permission previously granted to an access group. No
  backend endpoint exists for this today (confirmed: only
  `grantAccessGroupPermission` exists server-side, no corresponding
  revoke), and it was not part of what was asked for this feature.
- Designing the backend bulk-assignment or delete-with-cascade
  endpoints themselves — that is separate `knowly-api` SPEC work (see
  "Dependencies"). This SPEC only names the frontend requirements that
  depend on them.
- Any change to the *global* staff access-group screen
  (`/staff/access-groups`, `access-group-management-page.component.ts`)
  — that screen and its data model (`GlobalAccessGroup`) are unrelated
  to tenant-scoped `AccessGroup`s and are not touched by this feature.
- Bulk actions other than multi-group assignment (e.g. bulk member
  removal, bulk group deletion) — not requested, not designed here.
- Any new umbrella "manage access groups" permission that would grant
  more than one of the existing granular actions at once. This feature
  deliberately keeps using the existing per-action permissions
  (REQ-17); no new permission is introduced.
