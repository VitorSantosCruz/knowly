# SPEC — staff-global-dashboard (frontend)

## Context and motivation

`PROJECT_STATUS.md` item 6 ("Outside any tenant (staff global view)")
calls for three things once a staff user has no active tenant selected:
cross-tenant metrics, a staff-specific welcome screen, and a
member-listing → profile → audit-trail navigation path. The backend
halves of all three already exist and are done:

- `GET /api/staff/metrics/global` (`global-staff-dashboard-metrics`) —
  total tenant count, new-tenants-this-month, total articles read,
  staff member count.
- `GET /api/staff/users/{userId}/audit-trail` (`staff-audit-trail-view`)
  — a target user's full, cross-tenant audit history, capped at 500 rows.
- `welcome-screen` (frontend, already done) already gives a
  0-membership/staff session a distinct, generic, tenant-independent
  greeting on `/welcome` (REQ-1) — this already satisfies "a
  staff-specific welcome screen distinct from the tenant member's
  welcome screen" as literally requested; nothing new is needed there
  beyond a link (see REQ-8 below).
- `user-management-screens` (frontend, already done) already ships the
  staff directory (`StaffDirectoryPageComponent`, list/search/create)
  and a staff user's permission detail (`StaffUserDetailPanelComponent`,
  direct/group/effective permissions) — that SPEC's own "Out of scope"
  explicitly deferred audit-trail viewing as "a distinct, later feature,
  not this one." This is that feature.

Nothing in `knowly-app/` consumes the global metrics endpoint or the
audit-trail endpoint yet. This SPEC closes both gaps and adds the
missing navigation link, without reopening `welcome-screen`'s or
`user-management-screens`' own SPECs' scope.

**Naming note:** despite the feature name, this SPEC does not touch
`/welcome` itself beyond adding a link — see "Judgment calls" below for
why.

## User stories

- As a `STAFF`/`STAFF_ADMIN` with no active tenant selected, I want to
  see cross-tenant operational metrics (total tenants, new tenants this
  month, total articles read, staff count) so I can gauge the platform's
  overall state without needing to open every tenant individually.
- As that same staff user, I want a link from my welcome screen straight
  to that global view, the same way a tenant member's welcome screen
  links to their tenant's dashboard.
- As a staff user viewing the staff directory, I want to open a specific
  person's audit trail from their detail view, so I can investigate or
  support an account without leaving the screen I'm already on.
- As a staff user without the relevant global permission, I want the
  global dashboard link/route and the audit-trail section to be
  invisible rather than visible-but-broken, consistent with this
  project's established "hidden, not shown-then-blocked" nav rule.
- As a tenant member (or a staff user currently inside a tenant), I want
  none of this to appear anywhere in my UI — matches the general nav
  rule already established (`PROJECT_STATUS.md` item 5): staff-only/
  global-scope options exist only outside any tenant.

## Requirements (EARS/GEARS)

### Global metrics dashboard

- **REQ-1 [State-Driven]** While the session is staff with no active
  tenant selected, the screen mounted at `/dashboard` shall render a new
  global metrics view instead of the existing tenant-scoped
  `dashboard-analytics` view — same "one screen, two contexts" pattern
  already established for `/members` (`UserManagementPageComponent`
  branching on `ActiveTenantService.activeTenantId()`), not a new route.
- **REQ-2 [State-Driven]** While the session has an active tenant
  selected, `/dashboard` shall render the existing tenant-scoped
  `dashboard-analytics` view exactly as it works today — unchanged.
- **REQ-3 [Ubiquitous]** The global metrics view shall display, sourced
  from `GET /api/staff/metrics/global`: total tenant count, new tenants
  this calendar month, total articles read across every tenant, and
  total staff member count — as four tiles (reusing the existing
  `metric-tile.component.ts` shape without a sparkline, since the
  backend endpoint returns point-in-time counts only, no time series).
- **REQ-4 [Ubiquitous]** The global metrics view shall display a fifth,
  visibly disabled/"coming soon" tile for total support tickets, per the
  backend's own documented not-yet-available field (`global-staff-
  dashboard-metrics` REQ-7) — not silently omitted, not faked as zero.
- **REQ-5 [Unwanted Behavior]** If the caller lacks
  `GlobalPermission.DASHBOARD_VIEW_GLOBAL` (and isn't `STAFF_ADMIN`) —
  detected via a 403 from `GET /api/staff/metrics/global` — then the
  global metrics view shall show the existing non-technical
  permission-denied state (`app-no-access-state`), the same component
  `user-management-screens` already uses for its own 403s.
- **REQ-6 [Unwanted Behavior]** If the nav link to `/dashboard` would
  route the staff-outside-tenant session into the global view, and the
  caller does not hold `DASHBOARD_VIEW_GLOBAL` and isn't `STAFF_ADMIN`,
  then the nav link itself shall not be shown — consistent with
  `navigation-menu`'s existing "hidden, not shown-then-blocked" rule
  (this requires the nav link's own permission check to add
  `DASHBOARD_VIEW_GLOBAL` as an additional gate alongside whatever
  already gates the tenant-scoped dashboard link).
- **REQ-7 [Unwanted Behavior]** If a staff user switches into a tenant
  while on the global metrics view, then the screen shall follow the
  same "never both contexts" rule as `/members` — leaving the tenant
  immediately hides the global view and shows the tenant-scoped one
  instead (REQ-1/REQ-2 already express this as a state-driven pair; this
  clarifies the transition itself, not just the two steady states).

### Welcome-screen link (additive only)

- **REQ-8 [Event-Driven]** When a session has no active tenant (the
  staff case) and the caller holds `DASHBOARD_VIEW_GLOBAL` (or is
  `STAFF_ADMIN`), `/welcome`'s existing generic greeting shall gain one
  additional quick-link card to `/dashboard` (the global view from
  REQ-1) — mirroring the tenant-branded welcome's existing link to its
  own dashboard, without adding any metrics/content to `/welcome`
  itself.
- **REQ-9 [Unwanted Behavior]** If the caller lacks `DASHBOARD_VIEW_GLOBAL`
  and isn't `STAFF_ADMIN`, then `/welcome`'s generic greeting shall show
  no such link, unchanged from `welcome-screen`'s existing behavior.

### Audit-trail navigation from the staff directory

- **REQ-10 [Ubiquitous]** `StaffUserDetailPanelComponent` shall gain a
  new section showing the target user's audit trail, sourced from
  `GET /api/staff/users/{userId}/audit-trail`, displaying for each row:
  occurred-at timestamp, action, resource type, resource id, tenant id
  (or "global" when null), and outcome — reverse-chronological, matching
  the backend's own ordering.
- **REQ-11 [Event-Driven]** When a staff user opens a target user's
  detail panel (existing `ngOnChanges` load pattern, same as the
  permissions/access-groups sections already do), the system shall also
  fetch and render that user's audit trail.
- **REQ-12 [Unwanted Behavior]** If the caller lacks
  `GlobalPermission.AUDIT_TRAIL_VIEW` (and isn't `STAFF_ADMIN`) —
  detected via a 403 from the audit-trail endpoint — then only the
  audit-trail section shall show the existing non-technical
  permission-denied state; the permissions/access-groups sections
  already on the panel shall continue to render normally if the caller
  holds their own separate permissions for those (the failure is
  section-scoped, not panel-wide, matching how `staff-user-detail-panel`
  already treats each section's data independently).
- **REQ-13 [Ubiquitous]** The audit-trail section shall render the
  message "no audit history" (or equivalent) when the target user has no
  audit events, distinct from the permission-denied and network-error
  states.
- **REQ-14 [Ubiquitous]** The audit-trail section shall render no more
  than the rows the backend returns (up to its 500-row cap) — this
  screen implements no client-side pagination beyond what the endpoint
  itself already caps.

## Non-functional requirements

- Design: follows the established "Ink & Signal" design system, reuses
  existing `metric-tile.component.ts`, `error-state.component.ts`,
  `no-access-state.component.ts`, and the existing detail-panel section
  pattern (`data-testid`-tagged `<section>`s) — no new component library,
  no new dependency (see `DECISIONS.md`'s PrimeNG-removal entry).
- Accessibility: the audit-trail table and global metric tiles are
  operable/readable the same way `dashboard-analytics`'s existing tiles
  and tables already are (keyboard focus, `.sr-only` table mirrors for
  any chart-like element — though REQ-3's tiles have no sparkline here,
  so no chart mirror is needed for this feature specifically).
- Security: REQ-5/REQ-6/REQ-9/REQ-12 are UI-only hiding/error-state
  behavior — every underlying call is independently re-enforced
  server-side (`@RequiresGlobalPermission`); this SPEC is never the real
  authorization boundary, matching the principle already established in
  `navigation-menu` and `user-management-screens`.
- No pagination beyond the backend's existing 500-row audit cap (REQ-14)
  — matches the backend SPEC's own "Out of scope" line; a future
  pagination/filtering feature would need its own SPEC on both sides.

## Acceptance criteria

- [x] Outside any tenant (staff session), `/dashboard` shows the new
      global metrics view instead of the tenant-scoped one; inside a
      tenant, `/dashboard` is unchanged.
- [x] The global view shows four live tiles (total tenants, new tenants
      this month, total articles read, staff count) from
      `GET /api/staff/metrics/global`, plus one visibly-disabled
      "support tickets — coming soon" tile.
- [x] A caller without `DASHBOARD_VIEW_GLOBAL` (and not `STAFF_ADMIN`)
      sees the existing non-technical permission-denied state on the
      global view, and does not see the `/dashboard` nav link at all
      while staff-outside-tenant.
- [x] Switching into a tenant while on the global view immediately shows
      the tenant-scoped view instead (never both).
- [x] A staff session with `DASHBOARD_VIEW_GLOBAL` (or `STAFF_ADMIN`)
      sees one additional quick-link card on `/welcome` pointing to
      `/dashboard`; a staff session without it does not. `/welcome`
      itself shows no metrics content.
- [x] Opening a staff user's detail panel now also shows an audit-trail
      section (timestamp, action, resource type/id, tenant id or
      "global", outcome), reverse-chronological.
- [x] A caller without `AUDIT_TRAIL_VIEW` (and not `STAFF_ADMIN`) sees
      the permission-denied state only in the audit-trail section, while
      the permissions/access-groups sections continue to work normally
      if separately permitted.
- [x] A target user with no audit events shows a distinct "no audit
      history" message, not an empty table with no explanation.
- [x] `npm run format:check && npm test && npm run build` all pass.

## Out of scope

- **Profile view/edit UI** (name, address, CPF/RG, phone, etc.) — belongs
  to `identity-profile-model`'s not-yet-built frontend half
  (`PROJECT_STATUS.md` item 13); this SPEC only adds an audit-trail
  section to the existing permission-detail panel, it does not build a
  profile screen. "Open a profile, edit it" from the original backlog
  text is explicitly deferred.
- **Audit-trail viewing from the tenant-scoped `MembersPageComponent`**
  — this SPEC scopes audit-trail navigation to the staff directory
  (`StaffUserDetailPanelComponent`) only, per where the original backlog
  text places it ("Outside any tenant (staff global view)"). Extending
  it to tenant members viewed from inside a tenant is a possible future
  extension, not addressed here.
- **Any content, metrics, or widgets added directly to `/welcome`** —
  `welcome-screen`'s own SPEC explicitly excludes this; this SPEC adds
  only a navigational link, consistent with that boundary.
- **Historical/time-series version of the global metrics** — matches
  `global-staff-dashboard-metrics`'s own "Out of scope."
- **Support-ticket metrics** — the backend doesn't return real data for
  this yet (see REQ-4); the tile is a visible placeholder only.
- **Pagination or filtering of the audit trail** — matches
  `staff-audit-trail-view`'s own "Out of scope"; this screen renders
  exactly what the endpoint returns, up to its existing 500-row cap.
- **Self-service audit-trail viewing** (a user viewing their own
  history) — the backend endpoint doesn't support this; not addressed
  here.
- **Any change to `global-staff-dashboard-metrics`'s or
  `staff-audit-trail-view`'s backend endpoints** — both are consumed
  exactly as they exist today; no backend SPEC accompanies this one.
- **Any change to the tenant-scoped `dashboard-analytics` view's own
  content/behavior** — reused unchanged for the "inside a tenant" branch.
- **Any change to `user-management-screens`' existing permission/
  access-group sections or their own out-of-scope lines** — this feature
  only adds a new section alongside them.

## Judgment calls (Tier 2 — flagged for explicit confirmation)

1. **`/dashboard` reuses the "one screen, two contexts" pattern already
   confirmed for `/members`** (`UserManagementPageComponent`), rather
   than a new dedicated route (e.g. `/staff/dashboard`). Chosen because
   this repo already has exactly one precedent for this shape and the
   nav/context rules ("staff-only/global-scope options disappear inside
   a tenant") are identical between the two screens.
2. **The welcome-screen change is additive only** (one new quick-link
   card, gated by `DASHBOARD_VIEW_GLOBAL`), not a reopening of
   `welcome-screen`'s own SPEC or its "no staff-specific dashboard
   content on `/welcome`" out-of-scope line — a link is not content, and
   mirrors the tenant-branded welcome's existing dashboard link exactly.
3. **Audit-trail navigation is scoped to the staff directory only**, not
   the tenant-scoped members screen, based on where the original
   backlog text (`PROJECT_STATUS.md` item 6) places this requirement.

If any of these three should instead be a blocking question rather than
a documented judgment call, flag it before PLAN.md work starts.
