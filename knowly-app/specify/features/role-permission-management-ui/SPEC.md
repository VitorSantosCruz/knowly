# SPEC — role-permission-management-ui

> The what and the why. No technical implementation details.

## Context and motivation

Permissions today are hard to reason about in the UI in two unrelated
ways this feature fixes together:

1. **Roles are effectively meaningless.** The tenant roles page
   (`tenant-access-group-management-page.component.ts`) only lets an
   admin grant one permission at a time via a bare `<select>` +
   button, with no way to see the full set already granted at a glance
   and no way to revoke. The staff/global roles page
   (`access-group-management-page.component.ts`) has **no** permission
   UI at all today — it only creates roles and assigns/unassigns staff
   members to them.
2. **Direct per-user permission editing is visually poor and mixed in
   with unrelated data.** `member-detail-panel.component.ts` (tenant
   members) and `staff-user-detail-panel.component.ts` (staff/global
   users) each render their user's direct permissions as a dense,
   wrapping grid of small toggle buttons — one per enum value, no
   description of what any of them actually grants — inline among
   other, unrelated personal-data fields (address, etc.), with no tab
   separation.

This SPEC introduces one reusable "permission list" presentational
pattern (one permission per line, name + plain-language description of
what it grants, a granted/not-granted control) and uses it in four
places: the two user detail panels (as a new, separate "Permissions"
tab) and the two role-management pages (viewing/editing a role's
granted permissions). This is the frontend half of a two-SPEC feature —
the backend half (`knowly-api/specify/features/role-permission-revoke/
SPEC.md`) adds the missing revoke-a-permission-from-a-role endpoints
this UI's role-editing screens depend on.

## User stories

- As a tenant admin, I want to see a tenant role's granted permissions
  as a clear, one-per-line list with a name and description, so I can
  understand what that role actually grants without guessing from an
  enum name.
- As a tenant admin, I want to grant or revoke individual permissions
  on a tenant role from that same list, so I can correct what a role
  grants without deleting and recreating it.
- As staff with `STAFF_PERMISSION_MANAGE`, I want the same
  view/grant/revoke capability for staff/global roles, which today has
  no permission UI at all.
- As a tenant admin or staff editing a specific member/staff user, I
  want their direct permissions shown as the same clear one-per-line
  list, in a dedicated "Permissions" tab separate from their personal
  data, so editing a person's access isn't visually mixed in with
  unrelated fields.

## Requirements (EARS/GEARS)

1. **[Ubiquitous]** The system shall provide one reusable permission-list
   component that renders a given set of permission values (either
   `Permission` or `GlobalPermission`, generically) as a vertical list,
   one row per permission, each row showing the permission's
   human-readable name, its description of what it grants, and a
   granted/not-granted state.
2. **[Optional Feature]** Where the permission-list component is used in
   an editable context (role editing, direct-permission editing), the
   system shall let the user toggle each row between granted and
   not-granted, calling the corresponding grant/revoke action for that
   row.
3. **[Optional Feature]** Where the permission-list component is used in
   a read-only context, the system shall render the same one-per-line
   name+description layout without any toggle control.
4. **[Ubiquitous]** `member-detail-panel.component.ts` shall present a
   "Personal data" tab and a "Permissions" tab, in that order, replacing
   the current inline, undivided layout; the "Permissions" tab shall
   show the member's direct tenant `Permission` grants using the
   permission-list component from requirement 1, editable per
   requirement 2.
5. **[Ubiquitous]** `staff-user-detail-panel.component.ts` shall present
   a "Personal data" tab and a "Permissions" tab, in that order,
   mirroring requirement 4 for the staff user's direct `GlobalPermission`
   grants.
6. **[Event-Driven]** When an admin opens the permission-editing view for
   an existing tenant role (`AccessGroup`) on the tenant roles page, the
   system shall show that role's currently granted permissions using the
   permission-list component from requirement 1, editable per
   requirement 2, replacing the current single-`<select>`-plus-button
   grant-only control.
7. **[Event-Driven]** When staff opens the permission-editing view for an
   existing staff/global role (`GlobalAccessGroup`) on the staff/global
   roles page, the system shall show that role's currently granted
   permissions using the same component and editing behavior as
   requirement 6 — this view does not exist today and is being built
   from scratch by this requirement.
8. **[Ubiquitous]** Creating a new tenant role or staff/global role shall
   remain name-only, exactly as it works today — the permission-list
   editing view (requirements 6-7) is only reachable after a role
   already exists, never as part of its creation step.
9. **[Event-Driven]** When a permission is toggled on in the role-editing
   view, the system shall call the existing grant endpoint for that
   scope; when toggled off, the system shall call the new revoke
   endpoint for that scope (see the backend SPEC referenced above).
10. **[Unwanted Behavior]** If a grant or revoke call fails (network
    error, 403, or any other non-2xx response), then the system shall
    surface a visible error to the user and leave that row's displayed
    state unchanged (no optimistic toggle left in an inconsistent
    state), consistent with this app's existing error-handling
    convention for permission/access-group actions.

## Non-functional requirements

- Accessibility: each permission row's toggle control must be reachable
  and operable via keyboard, with the permission name announced as its
  accessible label (not just visually adjacent text).
- Responsiveness: the one-per-line list must remain readable (no
  horizontal overflow, no cramped wrapping) at the app's existing
  supported breakpoints, replacing the current wrapping-grid layout
  specifically because it does not meet this bar today.
- Performance: no new requirement beyond existing detail-panel/
  role-page load times — the permission set size (under 30 values per
  enum today) does not warrant virtualization or pagination.

## Permission copy (drafted for product-owner review before this SPEC is
approved — edit freely; nothing here is final)

Each row in the permission-list component (requirement 1) shows TWO pieces
of text per permission, never the raw enum constant on its own:
- **Name**: the existing human-readable label already in
  `permissions.<ENUM>` (`en.json`/`pt-BR.json`) — e.g.
  `TENANT_PERMISSION_GRANT_VIEW` → "View tenant permission grants" /
  "Visualizar concessões de permissão do tenant". These names already
  exist and are reused as-is, not redesigned by this SPEC.
- **Description**: new copy, drafted in the tables below, added under a
  new i18n key per permission (e.g.
  `permissions.descriptions.<ENUM>`) — explaining in a full sentence
  what granting that permission actually lets someone do.

A row therefore renders as, e.g.:

> **Visualizar concessões de permissão do tenant**
> Ver quais permissões diretas estão concedidas a um membro de tenant.

Never as the bare enum constant (`TENANT_PERMISSION_GRANT_VIEW`) with only
a description underneath — confirmed with the user (2026-08-08) this was
the exact failure mode to avoid.

### Tenant-scoped `Permission`

| Permission | Existing name (`permissions.<ENUM>`) | New description |
|---|---|---|
| `TENANT_MEMBER_MANAGE` | Manage tenant members | Add, remove, and manage members of this tenant, including their roles and permissions. |
| `ARTICLE_VIEW` | View articles | View articles in this tenant's knowledge base. |
| `ARTICLE_CREATE` | Create articles | Create new articles in this tenant's knowledge base. |
| `ARTICLE_EDIT` | Edit articles | Edit existing articles in this tenant's knowledge base. |
| `ARTICLE_DELETE` | Delete articles | Delete articles from this tenant's knowledge base. |
| `CONVERSATION_USE` | Use conversations | Start and take part in chat conversations within this tenant, including chatting with the AI over the knowledge base. |
| `DASHBOARD_VIEW` | View dashboard | View this tenant's usage/activity dashboard. |
| `PROFILE_VIEW` | View profile | View another person's personal profile data. |
| `PROFILE_EDIT` | Edit profile | Approve or directly edit another person's personal profile data. |
| `SUPPORT_CHANNEL_VIEW` | View support channel | View this tenant's support conversation with ConectaByte staff. |

### Staff/global-scoped `GlobalPermission`

| Permission | Existing name (`permissions.<ENUM>`) | New description |
|---|---|---|
| `TENANT_CREATE` | Create tenants | Create new tenants on the platform. |
| `TENANT_ACT_AS_ANY` | Act as any tenant | Act as (operate inside) any tenant without being a member of it. |
| `STAFF_PERMISSION_MANAGE` | Manage staff permissions | Grant or revoke permissions for staff users and staff/global roles. |
| `STAFF_USER_CREATE` | Create staff users | Create new staff user accounts. |
| `STAFF_USER_VIEW` | View staff users | View the list and detail of staff user accounts. |
| `PROFILE_VIEW` | View profile | View another person's personal profile data. |
| `PROFILE_EDIT` | Edit profile | Approve or directly edit another person's personal profile data. |
| `DASHBOARD_VIEW_GLOBAL` | View global dashboard | View the platform-wide (cross-tenant) usage/activity dashboard. |
| `AUDIT_TRAIL_VIEW` | View audit trail | View a user's full audit trail (history of actions taken by or on their account). |
| `STAFF_SUPPORT_HANDLE` | Handle staff support | Claim, transfer, and close tenant support tickets on behalf of ConectaByte. |
| `TENANT_VIEW` | View tenants | View the list and detail of tenants on the platform. |
| `TENANT_EDIT` | Edit tenants | Edit a tenant's own details (name, legal name, etc.). |
| `TENANT_DELETE` | Delete tenants | Delete a tenant. |
| `STAFF_USER_EDIT` | Edit staff users | Edit a staff user account's details. |
| `STAFF_USER_DELETE` | Delete staff users | Delete a staff user account. |
| `TENANT_MEMBER_VIEW` | View tenant members | View the members of any tenant. |
| `TENANT_MEMBER_CREATE` | Create tenant members | Add a member to any tenant. |
| `TENANT_MEMBER_EDIT` | Edit tenant members | Edit a member's details in any tenant. |
| `TENANT_MEMBER_DELETE` | Delete tenant members | Remove a member from any tenant. |
| `TENANT_ACCESS_GROUP_VIEW` | View tenant roles | View the roles defined for any tenant. |
| `TENANT_ACCESS_GROUP_CREATE` | Create tenant roles | Create a new role for any tenant. |
| `TENANT_ACCESS_GROUP_EDIT` | Edit tenant roles | Edit a role's granted permissions for any tenant. |
| `TENANT_ACCESS_GROUP_DELETE` | Delete tenant roles | Delete a role from any tenant. |
| `TENANT_PERMISSION_GRANT_VIEW` | View tenant permission grants | View which direct permissions are granted to a tenant member. |
| `TENANT_PERMISSION_GRANT_CREATE` | Create tenant permission grants | Grant a direct permission to a tenant member. |
| `TENANT_PERMISSION_GRANT_DELETE` | Delete tenant permission grants | Revoke a direct permission from a tenant member. |

Note: `PROFILE_VIEW`/`PROFILE_EDIT` are the same string value in both enums
(tenant-scoped and staff/global-scoped), and already share a single,
scope-neutral name (`permissions.PROFILE_VIEW`/`permissions.PROFILE_EDIT`)
today via `translatePermissionLabel`'s flat `permissions.<value>` lookup —
this SPEC does not introduce scoped/duplicate i18n keys to disambiguate
them. Their descriptions above are deliberately worded generically ("another
person's") for the same reason, per the user's explicit call (2026-08-08):
the tenant-scoped and staff/global-scoped permission lists are only ever
shown in mutually exclusive contexts (tenant-active vs. no-tenant-staff, the
same rule the nav "Roles" items already follow — see `nav-menu.component.ts`
`canSeeStaffAccessGroups`/`canSeeTenantAccessGroups`), so the two never
render side by side and identical wording causes no confusion. `DASHBOARD_VIEW`
(tenant) and `DASHBOARD_VIEW_GLOBAL` (staff/global) do not collide — they are
already distinct enum string values, each with its own existing name.

## Acceptance criteria

- [ ] Reusable permission-list component exists, used by all four
      consumers below, not duplicated per consumer.
- [ ] `member-detail-panel.component.ts` has "Personal data" and
      "Permissions" tabs (in that order); the toggle-button grid it uses
      today is gone.
- [ ] `staff-user-detail-panel.component.ts` has "Personal data" and
      "Permissions" tabs (in that order); the toggle-button grid it uses
      today is gone.
- [ ] Tenant roles page's permission editing uses the permission-list
      component; the old single-`<select>`-plus-button control is gone.
- [ ] Staff/global roles page gains a permission-editing view using the
      permission-list component (new — did not exist before).
- [ ] Role creation (both scopes) remains name-only.
- [ ] Toggling a permission off calls the new backend revoke endpoint;
      toggling on calls the existing grant endpoint; a failed call
      leaves the row's prior state visible and shows an error.
- [ ] Every `Permission`/`GlobalPermission` value has a name + description
      in both `en`/`pt-BR` locale files (translated from the drafted
      English copy above once approved), no permission renders with a
      blank description.
- [ ] `npm run format:check && npm test && npm run build && npm run lint`
      all green.

## Out of scope

- Any change to which permissions exist, what they're named at the
  backend enum level, or their authorization semantics — this feature
  only changes how permissions are displayed and toggled in the UI plus
  the new backend revoke endpoint it depends on.
- Bulk/multi-select granting or revoking of several permissions in one
  action — each row toggles independently, one request per toggle,
  matching the backend SPEC's single-`(role, permission)`-per-request
  shape.
- Any change to role/member/staff-user assignment (which members belong
  to which role) — untouched, this SPEC only concerns permissions
  granted *to* a role or *directly to* a person, not membership in a
  role.
- Any change to role creation beyond leaving it exactly as it is today
  (name-only) — no new fields added to role creation.
- Any additional detail-panel tab beyond "Personal data" and
  "Permissions" (e.g. an audit-history tab) — not requested and not
  added here; if one is wanted later, that is a separate SPEC change.
- Translating the drafted copy above into `pt-BR` is included in this
  SPEC's acceptance criteria once the English copy is approved, but the
  approval of the English wording itself happens before implementation
  starts, not as part of it.
