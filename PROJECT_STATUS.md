# Project status

> **Read this before starting any work in this repo — in any conversation,
> with any AI assistant.** This file exists so that a fresh conversation
> (no memory of prior sessions) can pick up exactly where the last one left
> off, without re-deriving context from scratch. It is checked into git,
> so it travels with the repo regardless of which tool or model opens it.
> Since the 2026-07-25 monorepo migration, there is exactly **one** of
> this file, covering both `knowly-api/` (backend) and `knowly-app/`
> (frontend) — there used to be a separate copy per repo; don't recreate
> that split.
>
> **You must also update it before finishing your work.** This is not
> optional and not just for Claude — any AI assistant (Claude, GPT, Gemini,
> whatever) that implements or changes a feature in this repo is expected
> to edit this file as part of that task, the same way it's expected to
> run the test suite. Concretely, before considering a task done:
> - Update the feature's row in the relevant table below (status, one-line
>   note) if you finished, started, or changed the shape of a feature.
> - Add a bullet to "Known operational/tooling notes" if you hit and fixed
>   a gotcha someone else would otherwise waste time on again.
> - If the long-term direction in [`VISION.md`](VISION.md) changed based
>   on something the user said, update that file too — it's meant to stay
>   current, not be a historical snapshot.
> If you finish a session without touching this file and something
> changed, the next conversation (possibly a different AI, possibly the
> user talking to a teammate's assistant) starts from stale information —
> that defeats the entire point of this file existing.

## Next up

> **This section exists specifically for whenever the user opens a
> conversation without specifying what to work on** — regardless of how
> that's phrased or in what language; judge intent, not wording. It must
> always name a concrete, literal next action — not a restatement of the
> backlog tables below. Whoever finishes a task (any AI) updates this
> section before signing off, so the *next* conversation — possibly
> opened cold, possibly by a different AI — knows exactly what to do
> without the user having to re-explain anything.
>
> Protocol for handling a direction-less request with no other context:
> 1. Read this section. If it names a concrete next action, do that (or
>    propose it and ask for a quick go-ahead if it's a meaningfully sized
>    new feature) — following SDD (SPEC → PLAN → TASKS → Implement →
>    Analyze) as normal.
> 2. If this section says there's nothing queued (current state, see
>    below), **do not silently invent a feature and start building it.**
>    Propose 2-4 concrete candidate directions and ask the user to pick —
>    draw them from `VISION.md`'s "What's deliberately not decided yet"
>    section, or from anything not-yet-built that's implied by the
>    product vision. Then update this section with whatever they choose,
>    even before writing the SPEC, so a crash/restart mid-conversation
>    doesn't lose that decision either.
> 3. Once a direction is chosen and there's an in-progress SPEC/PLAN/TASKS
>    for it, this section should say so directly (e.g. "Implementing
>    `<feature>` — TASKS.md items 5-12 remain, currently on item 7:
>    <what it is>"), not just "in progress."

**Current state: `staff-bootstrap-user`, `staff-rbac-split`,
`staff-user-provisioning`, `navigation-menu`, `welcome-screen`, and the
`dashboard-analytics` backend are all done. Item 5 (user management
screens) is now done on both sides**: the backend half
(`staff-user-listing`, `GET /api/staff/users`) is fully done — verified,
`qa-test-automation`/`appsec`-reviewed, and committed; the frontend half
(`user-management-screens`) is fully implemented, tested (253/253
frontend tests green), AppSec-reviewed (no blocking findings), and
committed — see `knowly-app/specify/features/user-management-screens/`.
`tenant-membership-acceptance`, `identity-profile-model`, and
`global-staff-dashboard-metrics` are also now fully done (verified,
reviewed, committed) — see their table rows below. `staff-audit-trail-view`
(`GET /api/staff/users/{userId}/audit-trail`, backend) is now also fully
done — implemented, tested, `./mvnw verify` green,
`qa-test-automation`/`appsec` reviewed with no blocking findings
(cross-tenant REQ-4 exposure re-confirmed as intentional/approved, not a
gap), and committed. The `dashboard-analytics` frontend (item 6's
tenant-scoped half) is now also fully implemented, tested (253/253
frontend tests green), and committed — see its table row below.
**Item 6's remaining staff global-view half is also now done**
(`staff-global-dashboard`, frontend, 2026-07-28): global metrics on
`/dashboard` for staff-outside-tenant, a `/welcome` quick-link, and an
audit-trail section on the staff directory's detail panel — 271/271
frontend tests green, committed, see its table row below and item 6's
detail above. Item 6 is now fully closed on both sides.

**`tenant-pagination-search` is now also fully closed on both sides**
(frontend, 2026-07-28): `/select-tenant`'s 0-membership staff fallback
now consumes the backend's paginated `GET /api/tenants` envelope, with
a debounced search input and prev/next pagination — 284/284 frontend
tests green, `qa-test-automation`/`appsec` reviewed with no blocking
findings, committed. See its table row below.

**`identity-profile-model-v2` retrofit is now fully done on both
sides (2026-07-29), including both backend follow-ups (`c0a817d`).**
Backend (`f9f2426`, `./mvnw verify` green) split personal data into
`UserProfile`/`Address`/`Contact` tables, made `avatarUrl` the only
directly self-editable field (a dedicated multipart `POST
/api/users/me/profile/avatar`), and removed the old
`STAFF_ADMIN`/`MEMBER_ADMIN` self-direct-edit bypass entirely — see
that feature's table row above. Frontend (`user-profile-v2`,
2026-07-29) retrofits `user-profile` in place to match — see its table
row below for the full detail. The two backend follow-ups discovered
during that frontend work — (1) `PUT /api/users/{id}/profile` never
applied `contactChanges`, and (2) `ProfileEditRequestDto.proposedFields
.address` was always `null` in the list/submit responses despite being
persisted — are now both closed (`c0a817d`): direct-edit accepts
`{fields, contactChanges}` and applies them via the same
`UserProfileService#applyFields` choke point the approve path uses,
and `proposedFieldsOf` populates `address` from the persisted request
row in both endpoints (`.contacts` stays `null` by deliberate design —
the proposed contact set is represented via the separate
`proposedContactChanges` field instead). 336/336 frontend tests green,
`format:check`/`build` clean.

**`member-admin-tenant-bypass` is now fully done (backend, 2026-07-29,
appsec-approved-with-notes at PLAN stage).** Closes the long-standing
asymmetry where `MembershipRole.MEMBER_ADMIN` had no equivalent of
`STAFF_ADMIN`'s unconditional `PermissionAspect` bypass: a `MEMBER_ADMIN`
with an active membership in `TenantContext`'s active tenant now
bypasses `@RequiresPermission` checks, scoped strictly to that tenant
via the existing `requireActiveMembership()` lookup (no new DB round
trip, no client-supplied tenant id — REQ-1/2/3/6). A new
`TenantService.requireNotSelfTarget` guard (REQ-4) blocks any caller —
not just `MEMBER_ADMIN` — from targeting their own account via
`addMember`/`grantPermission`/`revokePermission`/`assignAccessGroup`/
`unassignAccessGroup`; denial is recorded as a `DENIED` audit event for
free via the existing `AuditLogAspect`/`@AuditLog` wiring (REQ-5, zero
new audit code). PLAN-time correction of the SPEC's factual premise:
`requireAdminOfTenantOrStaff` already had a `MEMBER_ADMIN` bypass branch
(REQ-1b) before this feature — only `PermissionAspect`'s bypass and the
self-escalation guard were actually missing. Full-suite `./mvnw verify`
green (416/416, spotless-check clean). See
`knowly-api/specify/features/member-admin-tenant-bypass/`.

**Follow-up closed (2026-07-30):** `TenantService.removeMember` now also
calls `requireNotSelfTarget` (same guard, same placement pattern as the
other five methods — after the target membership is resolved, before the
mutation), so no caller, including a `MEMBER_ADMIN` acting via the
bypass, can remove their own membership. Denial recorded as a `DENIED`
`tenant.member.remove` audit event via the existing `@AuditLog`/
`AuditLogAspect` wiring, same as the other five. The previously-noted
gap ("deliberately not covered", appsec follow-up) is now resolved.

**Also queued, independent of the item-5 priority order below:**
`primeng-migration` (2026-07-25) was fully replaced one day later by
`primeng-removal` (2026-07-26) — the owner reverted the PrimeNG
adoption entirely, back to pure Tailwind + hand-rolled Angular
components, with `@lucide/angular` for icons. See `DECISIONS.md`'s two
consecutive dated entries and `knowly-app/specify/features/
primeng-removal/PLAN.md`/`TASKS.md` for the full record (component-by-
component replacement patterns, the Chart.js-direct approach for the
dashboard charts, and two implementation-detail deviations from the
plan — `@lucide/angular` instead of the deprecated `lucide-angular`,
and a `CHART_CTOR` injection token for deterministic Chart.js mocking
across bundled specs). `primeng-migration`'s row below is superseded;
its old follow-ups (Tailwind `cssLayer` integration, `tour-overlay` not
migrated) are moot now that PrimeNG is gone, except `tour-overlay`
itself was never touched by either pass and still needs its own look
whenever the onboarding tour positioning is revisited. 221/221 frontend
tests, `format:check`, and `build` all green after the removal.

The user confirmed this order for the next several features (2026-07-25):

1. ~~Bootstrap staff-admin one-shot user~~ — done, see
   `knowly-api/specify/features/staff-bootstrap-user/`.
2. ~~RBAC split~~ — done, see
   `knowly-api/specify/features/staff-rbac-split/`. `GlobalRole` is now
   `STAFF_ADMIN` (unrestricted, was the only value before) / `STAFF`
   (permission-gated, mirrors `tenancy`'s
   `Permission`/`AccessGroup`/`DirectPermissionGrant` model at the global
   scope via `GlobalPermission`/`GlobalAccessGroup`/
   `GlobalAccessGroupPermission`/`DirectGlobalPermissionGrant`/
   `UserGlobalAccessGroup`, new `/api/staff/**` endpoints). The
   `staff-bootstrap-user` migration's row is mapped to `STAFF_ADMIN` by
   `V14`'s data migration, per that decision. **Known small gap**: the
   two shared gating helpers in `TenantService`
   (`requireStaff`/`requireAdminOfTenantOrStaff`) are integration-tested
   against 2 of their ~11 call sites (`createTenant`/`listAllTenants`);
   the other 9 (`addMember`, `removeMember`, `listMembers`,
   `createAccessGroup`, `listAccessGroups`, `grantPermission`,
   `revokePermission`, `assignAccessGroup`, `unassignAccessGroup`,
   `getMemberDetail`) route through the same tested helpers parameterized
   by a different `GlobalPermission` enum constant, but aren't
   individually re-tested — see `staff-rbac-split/TASKS.md` task 6.
3. ~~Login/provisioning flow completion~~ — done, see
   `knowly-api/specify/features/staff-user-provisioning/`. New
   `GlobalPermission.STAFF_USER_CREATE` (independent from
   `STAFF_PERMISSION_MANAGE`) gates `POST /api/staff/users`, which
   creates a `GlobalRole.STAFF` user (never `STAFF_ADMIN`) and emails
   them a one-time password via the existing
   `OneTimePasswordService`/`MailService` mechanism. Tenant member
   provisioning (`addMember`) needed no change — it already worked via
   the passwordless login-code flow. Promoting/demoting `STAFF_ADMIN`
   and deactivating a staff user are explicitly out of scope (see that
   SPEC) — flag if either becomes needed later.
4. ~~Navigation menus + welcome screen~~ — done, frontend-only, see
   `knowly-app/specify/features/navigation-menu/` and
   `knowly-app/specify/features/welcome-screen/`. No backend change was
   needed (consumed the existing `GET /api/tenants/permissions` and
   `staff-rbac-split`'s `GET /api/staff/permissions` as-is). Fixed
   several real bugs uncovered along the way: `staff.guard.ts` inferred
   "is staff" from `GET /api/tenants` succeeding, which broke once
   `staff-rbac-split` made staff access individually granted (a `STAFF`
   user granted only `TENANT_CREATE`, not `TENANT_ACT_AS_ANY`, was
   wrongly blocked from tenant creation); login and the root route (`''`)
   both used to send an already-authenticated session to the wrong place
   (tenant list, or unconditionally `/login`); staff sessions (0
   memberships) never got nav links needing `PermissionsService`/
   `ActiveTenantService`, since both assumed an *active membership*
   existed.
5. ~~User management screens~~ — **done** (staff user management
   globally; tenant user management per-tenant). Backend gap closed by
   `staff-user-listing` (`GET /api/staff/users`); frontend built as
   `user-management-screens` — see
   `knowly-app/specify/features/user-management-screens/`. One
   `UserManagementPageComponent` switches between the untouched
   tenant-scoped `MembersPageComponent` and a new
   `StaffDirectoryPageComponent` based on `ActiveTenantService`'s active-
   tenant signal, per the "one screen, two contexts" rule below. AppSec
   reviewed with no blocking findings: the `STAFF` ceiling (a `STAFF`
   user, however permissioned, can never manage `STAFF`/`STAFF_ADMIN`
   targets) is enforced server-side by `role-model-refinement`'s
   `enforceStaffCeiling`; the frontend's `viewerIsStaffAdmin` flag only
   hides/disables dead-end actions and cannot itself grant capability.
   253/253 frontend tests green, `format:check`/`build` clean, committed.
   **Rules confirmed by the user 2026-07-26**, applied in this
   implementation:
   - **One screen, two contexts, never both menus at once.** The
     user-management screen exists in both contexts (inside a tenant:
     manage that tenant's members only; outside/staff context: manage
     staff users globally) — but which one a given staff user sees
     depends purely on whether they're currently "inside" a tenant or
     not, mirroring the general nav rule below. Inside a tenant, a staff
     user managing users can only ever act on that tenant's members —
     never staff/global users — precisely to preserve the
     staff-escalation protections already documented in item 8/9 above
     (a tenant-scoped context must never expose staff/global user
     management, or a tenant could indirectly manipulate staff there).
   - **General nav rule this generalizes from**: once a staff user is
     "inside" a specific tenant, every staff-only/global-scope nav
     option (tenant create/list/edit/delete, staff user management,
     global metrics dashboard, etc.) disappears from the menu entirely
     — the UI at that point should look and behave exactly like a
     regular tenant member's UI (plus whatever extra the staff user's
     tenant-level role/permissions grant them within that tenant, same
     as any other member). Those options only reappear once the staff
     user leaves the tenant back to the tenant list. This is a strict
     either/or, not a "staff sees extra options inside a tenant too."
6. ~~Expanded metrics dashboard~~ — **done, both sides**, see
   `knowly-api/specify/features/dashboard-analytics/`,
   `knowly-app/specify/features/dashboard-analytics/`, and
   `knowly-app/specify/features/staff-global-dashboard/`, and their rows
   in the feature tables above/below. The tenant-scoped in-app dashboard
   (period filter, five metric tiles with sparklines, message-split
   donut, conversations bar chart, members breakdown, searchable
   top-articles table, CSV export) is fully implemented, tested
   (253/253 frontend tests green), and committed. The **staff
   global-view half is now also done** (`staff-global-dashboard`,
   frontend, 2026-07-28): `/dashboard` (`DashboardWrapperPageComponent`)
   now branches on `ActiveTenantService.activeTenantResolved()` the same
   "one screen, two contexts" way `/members` already does —
   `DashboardPageComponent` unchanged when a tenant is active, a new
   `GlobalDashboardPageComponent` when staff has no active tenant, one
   page-level fetch to `GET /api/staff/metrics/global` rendering 4
   `metric-tile.component.ts` tiles (extended with an additive,
   backward-compatible pre-fetched-`[value]`/`[disabled]` mode — see
   `DECISIONS.md`) plus a 5th visibly-disabled "support tickets — coming
   soon" tile, `app-no-access-state` on a page-level 403. `/welcome`
   gains one additive quick-link card to `/dashboard` gated on new
   `GlobalPermission.DASHBOARD_VIEW_GLOBAL` (or `STAFF_ADMIN`-shaped),
   without adding any metrics content to `/welcome` itself.
   `StaffUserDetailPanelComponent` gains a new, independent
   `auditTrail`/`auditTrailError` section (own `ngOnChanges`-driven
   `loadAuditTrail()`, same per-section-error pattern as its existing
   permissions/access-groups sections) consuming
   `GET /api/staff/users/{userId}/audit-trail`, gated by new
   `GlobalPermission.AUDIT_TRAIL_VIEW`; a 403 there only affects that
   section. Nav's `nav.dashboard` entry now also shows for
   `DASHBOARD_VIEW_GLOBAL`, mirroring `nav.members`'s existing dual-gate
   shape. 271/271 frontend tests green, `format:check`/`build` clean,
   committed. **Deferred, per that SPEC's "Out of scope"**: profile
   view/edit UI (belongs to item 13's not-yet-built frontend half),
   audit-trail viewing from the tenant-scoped `MembersPageComponent`
   (staff-directory-only for now), support-ticket real data (still a
   placeholder tile — backend doesn't return it), and any
   pagination/filtering of the audit trail beyond the backend's existing
   500-row cap.
   - **Inside a tenant**: done — see above.
   - **Outside any tenant (staff global view)**: done — see above.
   - **Tenant CRUD stays staff-only and only visible outside a
     tenant** — see item 5's "general nav rule" above; staff can
     create/list/edit/delete tenants only from the staff-side (outside
     any tenant) screens, and that capability disappears from the menu
     entirely once the staff user is inside a tenant, even for
     `STAFF_ADMIN`.

**Backlog (user-reported 2026-07-25, not yet SPEC'd) — each needs its own
SPEC before implementation, roughly in this order:**

7. **Auth event audit logging gap.** `AuthController` (login-request,
   login-code/verify, login-password/verify, logout) has zero `@AuditLog`
   coverage today — every other mutating action in the system is
   audit-logged (`TenantService`/`StaffService`/article/conversation/
   onboarding), but authentication events leave no trace of who did what
   (e.g. no record of successful/failed login attempts, lockouts, or
   logout). User wants this closed so everything is traceable. Needs a
   backend SPEC — decide what to log per auth event (success/failure/
   lockout, masked email, IP?) consistent with `PiiMasker` conventions
   already used in `AuthController`'s regular logs.
   **Status (2026-07-26): done.** SPEC/PLAN/TASKS at
   `knowly-api/specify/features/auth-audit-logging/`. Every auth event
   (login-request, code/password verify success/failure, lockout,
   logout) now audited via the existing `@AuditLog`/`AuditLogAspect`
   mechanism (manual `AuditEventRepository`/new `AuditEventWriter` write
   for `logout`/lockout, since `@AuditLog` can't observe post-`proceed()`
   session teardown). An AppSec review flagged the source-IP capture as
   raw/unmasked and scoped system-wide instead of to auth events; the
   product owner delegated the fix choice to the agents, who decided to
   mask (`/24`/`/48` truncation, new `PiiMasker.maskIp`) and scope to
   auth-only (new `AuditLog.captureSourceIp()` opt-in flag, default
   `false`, so every non-auth `@AuditLog` consumer is unaffected). See
   `DECISIONS.md`'s 2026-07-26 entries for the full reasoning. QA and
   AppSec re-verified targeted tests green; full-suite `mvn verify` is
   deliberately deferred until all in-flight backlog work this session
   is done (see item 8 and this section's own note below), then run
   once for the whole batch.
8. **Role model rename + a real 4th tier (`MEMBER_ADMIN`).**
   **Status (2026-07-26): done.** SPEC/PLAN/TASKS at
   `knowly-api/specify/features/role-model-refinement/`.
   `MembershipRole.ADMIN` renamed to `MEMBER_ADMIN` everywhere
   (entity, all call sites, `V15` data migration for existing
   `tenant_memberships`/its Envers audit table). `StaffService`'s
   `createStaffUser`/`getStaffUserDetail`/`grantPermission`/
   `revokePermission`/`assignAccessGroup`/`unassignAccessGroup` now
   route through a new `enforceStaffCeiling` check: a `STAFF` user,
   however permissioned, can never manage a `STAFF`/`STAFF_ADMIN`
   target — `STAFF_ADMIN` is unaffected. A real bug was caught and fixed
   during combined testing: `getStaffUserDetail`'s new `@AuditLog` tried
   to `INSERT` inside that method's `readOnly = true` transaction and
   Postgres rejected it — fixed with a new `AuditEventWriter`
   (`REQUIRES_NEW` propagation) that `AuditLogAspect` now uses for every
   audit write, so no future `@AuditLog` usage on a read-only method can
   hit the same failure. **Known follow-up, not yet done**: `PROJECT_STATUS.md`'s frontend feature table isn't updated yet —
   `MembershipRole` serializes by enum name, so `knowly-app/` will start
   seeing `"MEMBER_ADMIN"` instead of `"ADMIN"` in API responses; this is
   a breaking contract change for the frontend that needs its own
   follow-up task whenever frontend work resumes (flagged, not
   addressed here — this SPEC was backend-only by design). Confirmed
   2026-07-26 with the user: the model becomes exactly `STAFF_ADMIN` /
   `STAFF` / `MEMBER_ADMIN` / `MEMBER` (renaming today's per-tenant
   `MembershipRole.ADMIN` → `MEMBER_ADMIN`, and `MEMBER` stays `MEMBER`
   — no separate "USER" role exists or is needed, "USER" was just
   informal language for a `MEMBER` with no permissions). Needs a backend
   SPEC covering the enum rename (migration + all call sites) plus:
   - **`STAFF` power ceiling**: a `STAFF` user, no matter how many
     `GlobalPermission`s they hold, can NEVER manage `STAFF`/`STAFF_ADMIN`
     users (create, edit, grant/revoke their global permissions, etc.) —
     that stays exclusively `STAFF_ADMIN`-only, specifically so a
     fully-permissioned `STAFF` can't self-escalate, mint another
     `STAFF_ADMIN` under an email they control, or elevate a friend.
     Every other action is fine for `STAFF` to reach, provided some
     `STAFF_ADMIN` explicitly granted the relevant `GlobalPermission` —
     this is the one hardcoded exception carved out of "STAFF fully
     permissioned == STAFF_ADMIN".
   - **`GET /api/tenants/memberships` stays as-is** — no backend
     single-vs-multi-membership restriction. Confirmed 2026-07-26: hiding
     the tenant list when the caller belongs to only one tenant is
     purely a frontend UX decision; calling the endpoint directly
     (outside the app) always returns the caller's own tenants
     regardless of count. Do not add a backend gate for this.
9. ~~Staff-joins-tenant requires in-app acceptance~~ / 10. ~~Tenant
   membership invitation requires acceptance~~ — **both done**, covered
   together by one feature: see `tenant-membership-acceptance`'s row in
   the backend feature table above and
   `knowly-api/specify/features/tenant-membership-acceptance/`
   (SPEC/PLAN/TASKS) for the full rules and implementation record.
11. ~~Tenant list pagination + search-by-name on `/select-tenant` and the
   backend's `GET /api/tenants`~~ — **backend half done**, see
   `tenant-pagination-search`'s row in the backend feature table above
   and `knowly-api/specify/features/tenant-pagination-search/`
   (SPEC/PLAN/TASKS). `GET /api/tenants` now returns a
   `PageResponseDto<TenantSummaryDto>` envelope with `page`/`size`/
   `search` query params instead of an unbounded array (breaking
   response-shape change, accepted per SPEC). **Still needed:** the
   companion frontend SPEC updating `/select-tenant`'s 0-membership
   staff fallback to consume the new envelope shape and add search UI.
12. ~~Boxed/segmented one-time-code input on the login screen~~ — **done**,
   see `boxed-otp-input`'s row in the frontend feature table above and
   `knowly-app/specify/features/boxed-otp-input/` (SPEC/PLAN/TASKS).
13. **Full identity/profile model — big, LGPD-sensitive, needs its own
   SPEC(s) before any code.** **Tier 3 data-protection decision confirmed
   by the user 2026-07-26 — unblocked**: CPF/RG shall be encrypted at
   rest (e.g. a JPA `AttributeConverter` doing the cipher/decipher, key
   managed outside the codebase — a secrets manager or env-injected key,
   never hardcoded/committed), decrypted only in memory when shown to
   someone holding the relevant permission. Retention: **indefinite**
   while the `User` record exists — no automatic expiry/anonymization
   job; deletion is manual/on-demand only (e.g. an LGPD data-subject
   erasure request), not a scheduled process. This SPEC can now proceed.
   Both the `Tenant` (company: CNPJ + other
   legally-unique company fields, tbd) and every `User` (person: email,
   full address, RG, CPF, phone, each enforced unique across all users
   — DB-level uniqueness, not just app validation) need complete
   records. Today's profiles are effectively anonymous — no field
   actually identifies the real-world person behind an account; this
   item is what introduces that (name, address, documents). **Profile
   editing rule, corrected 2026-07-26 (previous write-up here had this
   backwards)**: `MEMBER_ADMIN`/`STAFF_ADMIN` CAN edit their own profile
   — it's part of their general admin power, no exception for self.
   Everyone else needs a specific granted "edit profiles" permission to
   edit personal-data fields (name/address/documents/etc.) at all, and
   for **that non-admin permission holder specifically**, it excludes
   their own record — they can edit everyone else's profile but not
   their own, closing the obvious self-escalation hole (grant yourself
   edit rights, then edit your own record). So the "can't edit own
   profile" restriction applies only to the permission-holder path, not
   to admins. **Self-requested edit, corrected 2026-07-26**: a plain
   user with no edit-profiles permission still isn't fully locked out of
   changing their own data — they can *submit* a change request for
   their own profile (name/document/email/etc.), but it doesn't apply
   immediately; it goes into a pending state and requires explicit
   approval, via an in-app notification, from someone holding the
   edit-profiles permission (or an admin) before it takes effect.
   Purpose: stop a bad actor from unilaterally rewriting their own
   identity fields to pin blame on someone else or dodge accountability
   for something already tied to their profile, while still letting
   people fix a genuine typo/change of address without needing to beg
   someone to type it in for them. This reuses the same in-app
   notification/request-approval infrastructure already needed for
   items 9/10 (tenant-membership acceptance) — worth designing that
   infra once, shared across all three approval flows, rather than
   building it three times. **This "edit profiles" permission is a
   distinct capability
   from user/role management in item 5** — editing someone's personal
   data (name/address/documents) is separate from managing their
   role/tenant-membership/permissions; holding one does not imply the
   other, and — per item 8's `STAFF` ceiling and the general
   nobody-grants-themselves-permissions rule — nobody (staff or member,
   admin or not) can change their *own* role/permission grants regardless
   of what permissions they hold; that's exclusively a `STAFF_ADMIN`/
   `MEMBER_ADMIN`-on-others action. A user only sees their own profile
   and their own chat display nickname; must decide retention/at-rest-
   encryption/
   access-control for CPF/RG *before* writing any migration — this is
   Tier 3 (new kind of sensitive-data exposure) per `DECISIONS.md`, stop
   and confirm the data-protection approach with the user first. Backend:
   new entity/entities, migration(s), permission gating, DB-level
   uniqueness constraints. Frontend: profile view/edit screens.
14. **Internal team chat — big, new product surface, deferred until
    after the identity model above.** 1:1 conversations and group
    conversations between team members (distinct from the existing
    chat-with-the-knowledge-base feature) — uses the profile nickname
    from item 13 to identify people in the UI. Needs its own SPEC(s) in
    both subprojects once prioritized. **Rules confirmed by the user
    2026-07-26** (ahead of the SPEC, for whenever this is picked up):
    - 1:1 and group chat between team members is open to all four roles
      (`STAFF_ADMIN`/`STAFF`/`MEMBER_ADMIN`/`MEMBER`) with **no
      permission required just to talk to another person** — messaging
      itself isn't permission-gated, unlike chatting with the
      documentation/article knowledge base, which stays
      permission-gated for `MEMBER`s (existing `conversations` feature
      behavior, unchanged).
    - **Confirmed 2026-07-26, expanded scope**: the peer-to-peer model
      covers 1:1 staff↔staff, 1:1 member↔member, staff-only groups
      (multiple staff together), and member-only groups (multiple
      tenant members together) — all distinct from the single fixed
      member↔staff support channel described below. So there are three
      shapes in total: (a) open peer-to-peer 1:1/group chat among
      staff-with-staff or member-with-member, (b) member↔staff support
      channel (one fixed thread per member, see below), and (c) the
      pre-existing member↔knowledge-base article chat (already shipped,
      unrelated to this item). Whether staff and members can be mixed
      in the *same* peer-to-peer group (as opposed to a staff-only or
      member-only group) is not yet decided — clarify when writing the
      SPEC.
    - Staff↔member support is **not** a normal 1:1/group conversation —
      it's a single, fixed, per-member support channel: each tenant
      member has exactly one ongoing support thread, and whichever staff
      member is handling support replies through that same thread (not
      one thread per staff person). Needs its own entity/relationship
      design distinct from the peer-to-peer 1:1/group model above.
    - **Confirmed 2026-07-26, visibility rules for the support
      channel**: it's opened by one specific `MEMBER`/`MEMBER_ADMIN` and
      is effectively "that member vs. the staff team" until a staff
      member picks it up. **Tenant side — corrected 2026-07-26**: not
      just the opener + `MEMBER_ADMIN`s — *any* tenant member who holds
      whatever permission gates access to the support/history area can
      see the channel's message history, not only its opener. The exact
      permission gating that "history area" itself (new permission?
      reuse an existing one?) is TBD for the SPEC. **Staff side**: every
      staff user holding the relevant
      support permission can see the channel while it's unclaimed, but
      once a staff member accepts/picks it up, the thread effectively
      becomes a 1:1 between that specific staff member and the member
      who opened it — i.e. "accepting" assigns the conversation to one
      staff person, it isn't simultaneously multi-staff after that point
      (needs the SPEC to define whether other staff retain read access
      after assignment, or lose it, and how reassignment/handoff works).
    - **Confirmed 2026-07-26, view vs. participate are different**:
      everyone covered above (tenant side: any member with the
      support/history permission; staff side: any staff with the
      support permission, pre-acceptance) can only *view* the history —
      the only two people who can actually *send messages* in that
      thread are the member who opened it and the one staff member who
      accepted it. So visibility (who can read) is broader than
      participation (who can write), and participation narrows to
      exactly those two people once a staff member accepts.
    - **Confirmed 2026-07-26, closing is terminal**: a support ticket/
      thread can be closed; once closed, it permanently stops accepting
      new messages and cannot be reopened (a closed ticket stays
      closed forever — no reopen action). If the same member needs
      support again after closing, that requires starting a brand-new
      ticket/thread, not resuming the closed one. History remains
      viewable per the visibility rules above even after closing.
15. ~~Design system overhaul~~ — done (2026-07-25), no SPEC written (pure
    visual/frontend, user explicitly chose full-app scope over
    incremental). Defined knowly's first visual brand identity, "Ink &
    Signal" — no logo exists yet, so it's typographic-only: a deep navy
    `ink-*` palette (Tailwind v4 `@theme` tokens in
    `knowly-app/src/styles.css`) replaces `slate-*`/`indigo-*`/`blue-*`
    everywhere; a warm amber `signal-*` scale is the one accent, reserved
    for primary actions/focus/highlights — never a base surface color;
    Fraunces (serif, `font-display`) for the wordmark (`knowly.`, dot in
    signal) and large headings, Inter for everything else; motion tokens
    `duration-fast/base/slow` + `ease-fluid` and a reusable `.enter-fluid`
    entrance animation, no new npm dependency. Applied across every
    screen (app shell/nav, login, welcome, dashboard widgets, articles,
    conversations, members, select-tenant, tenant-create, shared UI) —
    visual/class changes only, no logic/testid/i18n changes, all 186
    frontend tests + build still green. **If a real logo/mark gets
    designed later, it should adopt this same ink/signal palette rather
    than starting the brand over.**
    **Follow-up correction (2026-07-25)** — the full-app palette/font swap
    above was accepted, but the initial pass read as flat/stiff ("tá tudo
    duro, sem transição"); this pass fixes *feel*, not tokens: (a) app
    shell/nav restyled from a horizontal top bar into a real left sidebar
    (`app-shell.component.ts`, `layout/nav-menu.component.ts`) — permanently
    dark `ink-950` chrome (Linear/Vercel-style, independent of the app's own
    light/dark toggle), grouped nav links with inline SVG icons (no new icon
    dependency), active items get a filled signal-tinted pill with an inset
    signal border rather than a plain background swap, tenant-switch/create
    links visually separated below a divider; (b) primary CTA buttons across
    login/welcome/articles/conversations/members/select-tenant/tenant-create
    gained `hover:-translate-y-0.5` lift + `active:scale-[0.98]` press
    feedback on top of the existing color transition; (c) first brand mark:
    `app-brand-wordmark` (`knowly-app/src/app/shared/brand-wordmark.component.ts`)
    — inline SVG wordmark-only logotype (Fraunces `<text>`, signal-colored
    dot), now the single implementation behind both the nav sidebar and the
    login page (previously duplicated markup); a companion "K" symbol mark
    (`knowly-app/public/favicon.svg`, ink-950 tile / white K / signal
    accent-cut) now backs the favicon and `apple-touch-icon` link in
    `index.html` (replacing the stock Angular favicon) — the stock
    `favicon.ico` binary itself was left in place as a fallback link since
    this environment has no way to rasterize a real `.ico`; all evergreen
    browsers will use the new SVG icon. **Tier 2 judgment calls made without
    asking:** sidebar is always dark regardless of the app's light/dark
    toggle (matches the requested reference dashboards, which all use a
    fixed-dark chrome); the shared utility buttons in the sidebar footer
    (`help-menu`/`language-switcher`/`theme-toggle`/`logout-button`) had
    their light-mode classes replaced with dark-chrome-only classes since
    they're now only ever rendered on that permanently-dark surface — if
    either of those components is reused somewhere with a light background
    in the future, its classes will need reintroducing dark/light variants.
    **Not run this pass:** `npm run format` / `format:check` / `test` /
    `build` — no shell/bash tool was available in this agent invocation;
    a human or `frontend-engineer` must run knowly-app's verification
    commands before trusting this is green.
    **Second follow-up (2026-07-25)**, after the sidebar/motion pass above
    was actually seen running (not just reviewed as a diff): borders were
    too low-contrast to read as real dividers, the welcome screen was a
    small floating card in an otherwise empty canvas, and the
    theme/language/help/logout controls sat oddly at the bottom of the
    sidebar. Fixed: those four controls now live in a horizontal top
    header bar (`app-shell.component.ts`), separate from primary
    navigation, which stays permanently dark chrome like the sidebar;
    sidebar links are grouped under category labels (Overview/Knowledge/
    Team/Workspace, new `nav.category.*` i18n keys) for faster scanning;
    a single `.page-shell` spacing convention (`styles.css`) was
    introduced and adopted by dashboard/articles/conversations/members so
    page gutters and vertical rhythm aren't reinvented per screen; the
    welcome screen gained permission-gated quick-link cards (articles/
    conversations/members) instead of just a lone greeting card. Verified
    this time: `npm run format`, `format:check`, `test` (186 passing),
    `build` all green; committed.

Backend and frontend work can proceed in parallel per feature once each
one has an approved SPEC/PLAN that defines the API contract.

## How to work in this repo

This project follows **Spec-Driven Development (SDD)** — see
[`specify/memory/constitution.md`](specify/memory/constitution.md) and
`sdd-methodology.md` (in the same folder) for the full process (SPEC →
PLAN → TASKS → Implement → Analyze). In short:

1. Never implement from a vague request. If
   `<subproject>/specify/features/<name>/SPEC.md` doesn't exist for
   what's being asked, write it first (EARS/GEARS syntax) and get it
   approved.
2. Then PLAN.md (technical decisions) and TASKS.md (atomic,
   checkbox-tracked steps).
3. Implement task by task: test first (Red), minimal code (Green),
   `./mvnw test` (backend, from `knowly-api/`) or `npm test` (frontend,
   from `knowly-app/`).
4. Before calling a task done: `./mvnw spotless:apply && ./mvnw verify`
   (backend) or `npm run format:check && npm test && npm run build`
   (frontend).
5. Analyze: re-check every SPEC.md acceptance-criterion checkbox against
   the finished implementation before calling the *feature* (not just
   the task) done.

## Feature status — backend (`knowly-api/`)

Every feature below has its own
`knowly-api/specify/features/<name>/{SPEC,PLAN,TASKS}.md` — read those
for the actual requirements and decisions. This table is only a map of
*what exists* and *how done it is*; it is not a substitute for reading
the feature's own SPEC.

| Feature | Status | Notes |
|---|---|---|
| `authentication` | ✅ Done | Login-code (passwordless) flow, sessions, logout (`POST /api/auth/logout`). **Known minor gap (2026-07-26, reviewed, no action taken):** `/login-code/verify` and `/login-password/verify` already compare against a constant dummy hash when no real credential exists (`LoginCodeService.verify`, `OneTimePasswordService.verifyAndRotate`), so the hash-compare step itself doesn't leak email existence. However, the success branch does strictly more work than the failure branch (extra DB lookup(s), possible OTP regeneration, email dispatch, session creation) with no artificial padding, so a correct vs. incorrect credential attempt is distinguishable by response latency — this doesn't leak whether an email exists (only reachable after a valid credential), just success/failure. User reviewed fix options (fixed latency floor vs. deferring success-path side-effects to after the response) and explicitly chose not to implement either for now, prioritizing frontend work instead. Revisit if this ever needs to be closed. |
| `tenancy` | ✅ Done | Multi-tenant session model, memberships, roles, permissions, access groups, audit log. Staff (global-admin) users can list every tenant and act as any of them without holding a membership (added after a live bug where a staff account with 0 memberships got stuck behind `TENANT_SELECTION_REQUIRED`). |
| `article-management` | ✅ Done | Upload (text/image/PDF, OCR via tesseract), embeddings (pgvector), permission-gated CRUD. |
| `conversations` | ✅ Done | Chat over the tenant's articles, SSE streaming, citations. |
| `dashboard-metrics` | ✅ Done | Usage widgets backed by `MessageArticleCitation`. |
| `onboarding-status` | ✅ Done | Tracks first-run completion server-side. |
| `api-documentation` | ✅ Done | OpenAPI/Swagger exposure. |
| `tags-crud` | 📄 Reference only | **Not implemented on purpose** — exists solely as the canonical example of the SPEC/PLAN/TASKS format. Don't build it unless explicitly asked to turn it into a real feature. |
| `staff-bootstrap-user` | ✅ Done | One migration-created staff `User` (email via required `KNOWLY_BOOTSTRAP_STAFF_EMAIL` env var, no password) so a fresh deployment has a first login via the existing login-code flow. No new mechanism, no freeze/expiry — see SPEC's "Out of scope" for why. |
| `staff-rbac-split` | ✅ Done | `GlobalRole` splits into `STAFF_ADMIN` (unrestricted) / `STAFF` (permission-gated via `GlobalPermission`, mirrors tenant-side `Permission`/`AccessGroup` model at global scope). New `/api/staff/**` endpoints. Small known test-coverage gap — see "Next up" above. |
| `staff-user-provisioning` | ✅ Done | `POST /api/staff/users` lets `STAFF_ADMIN` (or a granted `STAFF`) create a new `STAFF` user, gated by its own `GlobalPermission.STAFF_USER_CREATE`; emails a one-time password via the existing mechanism. Tenant member provisioning needed no change. |
| `dashboard-analytics` | ✅ Done (backend) | Extends `metrics` with date-bucketed time-series (`/conversations/timeseries`, `/messages/timeseries`, `/articles/timeseries`, UTC calendar-day, zero-count days included), a tenant membership active/inactive snapshot (`/members`), `period` filtering (`7d`/`30d`/`90d`/`all`, default `all`) on every metrics endpoint via a new `MetricsPeriod` enum + `InvalidPeriodException`/`MetricsExceptionHandler`, and a hand-built CSV export (`/export`, no new dependency). All still `DASHBOARD_VIEW`-gated, tenant-isolated via `TenantFilter`. Frontend consuming these is a separate SPEC (`knowly-app/specify/features/dashboard-analytics/`). See `DECISIONS.md` for the UTC-bucketing rationale. |
| `staff-user-listing` | ✅ Done | `GET /api/staff/users` (optional `?email=` case-insensitive substring filter) lists every `STAFF`/`STAFF_ADMIN` user, gated by new `GlobalPermission.STAFF_USER_VIEW` (independent of `STAFF_USER_CREATE`/management ceiling checks). `StaffController.listStaffUsers`/`StaffService.listStaffUsers`/`StaffUserSummaryDto` implemented and tested (`StaffUserListingIntegrationTest`). Full-suite `./mvnw verify` green (339/339), `qa-test-automation`/`appsec` reviewed with no blocking findings. |
| `tenant-membership-acceptance` | ✅ Done | New `Notification`/`NotificationType` model (`V16` migration) plus `NotificationController`/`NotificationService`/`NotificationDto` (`/api/notifications`) for accept/decline-style tenant membership notifications, with `NotificationAlreadyResolvedException`/`NotificationNotFoundException` wired into the existing exception-handling convention. Confirmed `removeMember` needs no code change. Full-suite `./mvnw verify` green (339/339), `qa-test-automation`/`appsec` reviewed with no blocking findings (IDOR/replay/privilege-escalation checks all confirmed clean). |
| `identity-profile-model` | ⛔ Superseded by `identity-profile-model-v2` | Was: encrypted `cpf`/`rg` identity fields directly on `User` (`CpfRgEncryptionConverter`, blind-index lookup via `BlindIndexService`) plus a profile-edit-request flow. Retrofitted 2026-07-28 — see `identity-profile-model-v2` below and `DECISIONS.md`'s "`identity-profile-model` retrofit" entry. Row kept only as history; do not treat any detail below as current for the shipped shape (the encryption/blind-index mechanism itself is unchanged, just relocated). |
| `identity-profile-model-v2` | ✅ Done (backend) | Retrofits `identity-profile-model`: personal data split into new `UserProfile` (1:1, eager row per `User`, `cpf`/`rg`/`rgOrgaoEmissor`/`birthDate`/`avatarUrl`), `Address` (1:1, lazy), and `Contact` (1:n, max 5, one-primary-per-type, `ContactService`) tables/entities, replacing `User`'s old flat `fullName`/`address`/`rg`/`cpf`/`phone` columns (still present on `User`/`users` — dropped only in a later `V19` migration, deliberately deferred per PLAN.md until this code path is verified running in production, not bundled into this session). `V18__retrofit_identity_profile_tables.sql` creates the new tables + `profile_edit_request_contacts` (1:n proposed contact changes), backfills `full_name`/`cpf`/`rg`/`phone` from `users`, cancels any in-flight `PENDING` request (new `ProfileEditRequestStatus.CANCELLED`), and adds a DB-level `CHECK` blocking self-approval (defense-in-depth alongside the existing service-layer guard). REQ-11 is a genuine behavior *removal* from the shipped feature: `directEdit`'s self-exclusion is now unconditional — `STAFF_ADMIN`/`MEMBER_ADMIN` can no longer self-edit even fields they could edit on someone else; only the new dedicated `POST /api/users/me/profile/avatar` (multipart, reuses `ArticleStorageService`'s MinIO/S3 pattern via new `AvatarStorageService`/`AvatarProperties`, distinct `avatarBucket`) is self-editable, unconditionally, no approval step. `ContactType` (`PHONE`/`WHATSAPP`/`EMAIL`/`OTHER`) format validation is a plain service-layer `if` in `ContactService`, not a custom Bean Validation `@Constraint` (documented Tier 2 call in `DECISIONS.md` — first feature that could have reached for one and deliberately didn't). Full-suite `./mvnw verify` green. Companion frontend SPEC (`knowly-app/specify/features/user-profile-v2/`) not yet started — backend contract (DTOs/endpoints) is final and ready to build against. |
| `global-staff-dashboard-metrics` | ✅ Done | `GET /api/staff/metrics/global` (`GlobalMetricsController`/`GlobalMetricsService`/`GlobalMetricsDto`) exposes global counts for `STAFF_ADMIN` or a `STAFF` caller holding `GlobalPermission.DASHBOARD_VIEW_GLOBAL`, including a "new tenants this month" UTC-calendar-month boundary case (tightened during QA review to assert the exact millisecond boundary, not just a ±1-day margin). Full-suite `./mvnw verify` green (339/339), `qa-test-automation`/`appsec` reviewed with no blocking findings. |
| `tenant-pagination-search` | ✅ Done (backend) | `GET /api/tenants` breaking-changed from an unbounded `List<TenantSummaryDto>` to a paginated `PageResponseDto<TenantSummaryDto>` envelope (`content`/`page`/`size`/`totalElements`/`totalPages`). New `page`/`size` query params (defaults `0`/`20`, `size` clamped to `100`, negative `page` or `size<=0` rejected with `400 INVALID_PAGINATION` via new `InvalidPaginationException`), plus an optional `search` param matching `Tenant.name`/`cnpj`/`razaoSocial` case-insensitively (OR'd) via a new DB-level `TenantRepository.search(String, Pageable)` `@Query`, sorted server-side by `name` ascending only (no client-supplied sort). Authorization unchanged (`requireStaff`/`GlobalPermission.TENANT_ACT_AS_ANY`). This is the first page/size pagination contract in this codebase — see `DECISIONS.md` for the `@Query`-over-`Specification`/fixed-sort/`PageResponseDto`-placement judgment calls, intended as the default template for future paginated endpoints. Full-suite `./mvnw verify` green (377/377, ~21 min real elapsed — the ~12 min figure previously in this file was stale). Companion frontend SPEC for `/select-tenant`'s consumption of the new envelope shape is not yet started — backend half only. |
| `staff-audit-trail-view` | ✅ Done | `GET /api/staff/users/{userId}/audit-trail` (`StaffController.auditTrail`/`StaffService.getAuditTrail`/`AuditEventDto`) returns a target user's full audit history — deliberately **cross-tenant, `TenantFilter`-bypassing by design** (`AuditEvent` isn't a `TenantAwareEntity`, so no special plumbing was needed) — capped at the 500 most recent rows via a new `AuditEventRepository.findTop500ByActorUserIdOrderByOccurredAtDesc` (DB-enforced `LIMIT`, backed by the pre-existing `ix_audit_events_actor_time` composite index, no new migration). Gated by new `GlobalPermission.AUDIT_TRAIL_VIEW`, ceiling-independent (REQ-9: viewing a `STAFF`/`STAFF_ADMIN` target's trail is unaffected by the `role-model-refinement` management ceiling). The call itself is audited (`staff.audit_trail.view`). Full-suite `./mvnw verify` green; `qa-test-automation` independently confirmed every REQ/acceptance criterion (including the cross-tenant, 500-cap, ceiling-independence, and self-audit cases) is covered by a real passing test; `appsec` re-reviewed the implementation against the SPEC's confirmed REQ-4 exposure and found no new issue — verdict "ship it," no blocking findings. |
| `member-admin-tenant-bypass` | ✅ Done | `MembershipRole.MEMBER_ADMIN` now gets an unconditional `PermissionAspect.checkPermission` bypass in their own active tenant (mirrors `STAFF_ADMIN`'s global bypass), scoped via the existing `requireActiveMembership()` lookup — no new DB round trip, no client-supplied tenant id. New `TenantService.requireNotSelfTarget` guard blocks any caller (role-agnostic) from targeting their own account via `addMember`/`grantPermission`/`revokePermission`/`assignAccessGroup`/`unassignAccessGroup`; denial is a `DENIED` audit event via the pre-existing `AuditLogAspect`, no new audit code. Full-suite `./mvnw verify` green (416/416). Follow-up (2026-07-30): `removeMember` now also covered by the same guard — see above. |

## Feature status — frontend (`knowly-app/`)

Every feature below has its own
`knowly-app/specify/features/<name>/{SPEC,PLAN,TASKS}.md` — read those
for the actual requirements and decisions.

| Feature | Status | Notes |
|---|---|---|
| `login` | ✅ Done | Login-code (passwordless) flow. |
| `logout` | ✅ Done | Logout in the app shell nav; calls `POST /api/auth/logout`. Introduced this app's first CSRF token wiring (`withXsrfConfiguration()`), since logout is the first authenticated, non-CSRF-exempt POST the frontend makes. |
| `select-tenant` | ✅ Done | Multi-membership tenant picker; also handles the 0-membership staff case by falling back to the backend's all-tenants listing (`GET /api/tenants`) when the memberships list comes back empty. Amended REQ-5: 0-membership sessions no longer redirect to `/select-tenant`. |
| `onboarding-dashboard` | ✅ Done | First-run tour + dashboard metric widgets. Tour auto-start moved to `welcome-screen`. |
| `article-management` | ✅ Done | Upload (with polling + an animated status badge for processing/ready/failed), inline edit, delete, permission-gated UI. |
| `conversations` | ✅ Done | Chat UI over SSE (hand-rolled parser — native `EventSource` can't POST a body). |
| `user-management` | ✅ Done | Tenant members/roles/permissions/access-groups admin UI. |
| `user-management-screens` | ✅ Done | Adds the staff-side global user management context alongside `user-management`'s existing tenant-scoped one. `UserManagementPageComponent` (mounted at `/members`) switches between the untouched `MembersPageComponent` and a new `StaffDirectoryPageComponent` (list/search via `staff-user-listing`'s `GET /api/staff/users`, create via `staff-user-provisioning`) based on `ActiveTenantService`'s active-tenant signal — never both at once. `StaffUserDetailPanelComponent` shows direct/access-group/effective global permissions and grants/revokes/assigns via `staff-rbac-split`'s endpoints. The `role-model-refinement` `STAFF` ceiling is UI-reflected via a `viewerIsStaffAdmin` flag (hides/disables actions against `STAFF`/`STAFF_ADMIN` rows) — AppSec-confirmed this is cosmetic only, real enforcement stays server-side (`enforceStaffCeiling`). Nav entry (`nav.members`) now also shows for `STAFF_USER_VIEW`. |
| `tenant-creation` | ✅ Done | Staff-only `/tenants/new` form (name + first admin email) calling `POST /api/tenants`. Originally gated by an `isStaff` heuristic (whether `GET /api/tenants` succeeded); `navigation-menu` replaced that with the real `GlobalPermission.TENANT_CREATE` check (`GET /api/staff/permissions`) after the backend's `staff-rbac-split` made that heuristic wrong for a `STAFF` user granted `TENANT_CREATE` but not `TENANT_ACT_AS_ANY`. |
| `tags-list` | 📄 Reference only | **Not implemented on purpose** — exists solely as the canonical example of the SPEC/PLAN/TASKS format, paired with the backend's `tags-crud` reference. Don't build it unless explicitly asked to turn it into a real feature. |
| `navigation-menu` | ✅ Done | Real app-shell navigation (`nav-menu.component.ts`), links filtered by `PermissionsService`/`GlobalPermissionsService`; "switch tenant" link reusing `/select-tenant`. Fixed the `staffGuard`/create-tenant-link bug above as part of the same feature. |
| `welcome-screen` | ✅ Done | Real `/welcome` landing screen (staff-generic or tenant-branded greeting, no sensitive/permission-gated content) — replaces `/dashboard` as the post-login/tenant-selection/root-redirect target. Fixed two real bugs: login and the root route (`''`) both used to send an already-authenticated session to the wrong place (tenant list, or unconditionally `/login`). Onboarding tour trigger moved here from `dashboard`; tour target ids moved to the global nav menu. |
| `dashboard-analytics` | ✅ Done | Period filter (`period-filter.component.ts`, native Tailwind toggle-button group post-`primeng-removal`) owned by `dashboard-page.component.ts`'s `period` signal; five reusable `metric-tile.component.ts` instances (active articles/conversations/USER messages/ASSISTANT messages/active members, each with a line sparkline via the shared `chart-canvas.component.ts` + `toSparklineData()`), superseding the old `article-count-card`/`conversations-card`/`messages-card` (all three deleted); `message-split-chart.component.ts` (USER/ASSISTANT donut, `toDonutData()`); `conversations-activity-chart.component.ts` (per-day bar chart, `toBarData()`); every chart paired with a visually-hidden `.sr-only` mirror `<table>` generated from the same tested mapping function; `members-breakdown-card.component.ts` (`GET /api/tenants/metrics/members`); `top-articles-table.component.ts` (native `<table>` + a local `computed()` filter signal, replaces `article-usage-list.component.ts`); `export-button.component.ts` (native button + CSV blob download); `metric-fetcher.ts`'s `load()` extended to accept `params`. Originally built against PrimeNG (`p-chart`/`SelectButton`/`p-table`) per the SPEC written before `primeng-removal`; every widget was subsequently migrated to the current native-Tailwind conventions (`chart-canvas.component.ts` + its `CHART_CTOR` injection token, native `<table>`/toggle group) as part of `primeng-removal`'s cleanup — `chart.js@^4.5.1` (user-confirmed Tier 3 dependency, `DECISIONS.md`) stays a direct dependency of `chart-canvas.component.ts`, not PrimeNG's `Chart` wrapper. 253/253 frontend tests green, `format:check`/`build` clean (production bundle 577KB raw / ~138KB estimated transfer — well under budget, no PrimeNG chrome anymore). |
| `staff-global-dashboard` | ✅ Done | Closes item 6's remaining staff global-view half. `DashboardWrapperPageComponent` (mounted at `/dashboard`, replacing the direct `DashboardPageComponent` route mapping) branches on `ActiveTenantService.activeTenantResolved()`, same "one screen, two contexts" shape as `UserManagementPageComponent`: `DashboardPageComponent` unchanged when a tenant is active, a new `GlobalDashboardPageComponent` when staff has no active tenant. `GlobalDashboardPageComponent` makes one page-level fetch to `GET /api/staff/metrics/global`, rendering 4 `metric-tile.component.ts` tiles (total tenants, new tenants this month, total articles read, staff count) plus a 5th visibly-disabled "support tickets — coming soon" tile, `app-no-access-state` on a page-level 403 (not per-tile). `metric-tile.component.ts` gained an additive, backward-compatible pre-fetched-`[value]`/`[disabled]` mode (`url`/`valueSelector`/`period` now optional, self-fetch `effect()` gated on `url()` being defined) — every existing self-fetching tenant tile stays byte-for-byte unchanged; see `DECISIONS.md`. (A `[loading]` input was initially added too but removed during `qa-test-automation`'s final review as dead code — the page already gates tile rendering on its own loading state before mounting them.) `/welcome` gains one additive quick-link card to `/dashboard`, gated on new `GlobalPermission.DASHBOARD_VIEW_GLOBAL` (or `STAFF_ADMIN`-shaped, via a page-local `viewerIsStaffAdmin` computed matching `StaffDirectoryPageComponent`'s existing pattern) — no other content added to `/welcome` itself. `StaffUserDetailPanelComponent` gains a 4th, independent `<section data-testid="staff-audit-trail">` (own `auditTrail`/`auditTrailError` signals, own `loadAuditTrail()` wired into the existing `ngOnChanges`), consuming `GET /api/staff/users/{userId}/audit-trail` (new `AuditEvent` type + `getAuditTrail()` on `StaffUserService`), gated by new `GlobalPermission.AUDIT_TRAIL_VIEW`; a 403 there only shows `app-no-access-state` in that section, permissions/access-groups sections keep rendering. `nav-menu.component.ts`'s `nav.dashboard` entry now also shows for `DASHBOARD_VIEW_GLOBAL`, mirroring `nav.members`'s existing `TENANT_MEMBER_MANAGE`-OR-`STAFF_USER_VIEW` dual-gate shape. 271/271 frontend tests green, `format:check`/`build` clean, 9 atomic commits. `qa-test-automation` independently confirmed every REQ covered by a real test (and flagged/fixed the dead `[loading]` input above); `appsec` confirmed nav/link hiding is cosmetic-only (server-side 403 is the real boundary), no new cross-tenant exposure, no XSS surface, and frontend `GlobalPermission` values match the backend exactly — no blocking findings. |
| `tenant-pagination-search` | ✅ Done (frontend) | Adapts `/select-tenant`'s 0-membership staff fallback to the backend's new paginated `GET /api/tenants` envelope (`content`/`page`/`size`/`totalElements`/`totalPages`, `tenant-pagination-search` backend feature). `ActiveTenantService.listAllTenants` signature changed to `(page, size, search?)`, built via `HttpParams`, returning a new local `PageResponse<T>` interface (no shared/generic paginated type introduced — only paginated envelope in this frontend today, per SPEC's explicit scope). `SelectTenantPageComponent` gained a debounced (300ms, `Subject`+`debounceTime`+`distinctUntilChanged`) search input and prev/next pagination buttons, both funneled through one shared `fetchFallbackTenants()` request-builder so page/search state can't drift apart; a `fallbackError: 'network' | null` signal keeps the existing request-failure empty state (`selectTenant.empty`) visually distinct from a new genuine-zero-results state (`selectTenant.noSearchResults`). New Transloco keys (`selectTenant.searchLabel`/`searchPlaceholder`/`previousPage`/`nextPage`/`noSearchResults`) in `en`/`pt-BR`. 284/284 frontend tests green (51 files), `format:check`/`build` clean. `qa-test-automation` independently confirmed every REQ (including the debounce-timing and REQ-7-vs-REQ-8 visible-distinctness cases) is covered by a real test; `appsec` confirmed `search` only ever travels via `HttpParams` (never string-concatenated/`innerHTML`), no new field exposure beyond the old `TenantSummary` shape, and no new authorization surface (staff-only `TENANT_ACT_AS_ANY` gating stays entirely server-side) — no blocking findings. |
| `user-profile` | ⛔ Superseded by `user-profile-v2` | Was: frontend half of item 13 against `identity-profile-model`'s old flat contract (`fullName`/`address` string/`rg`/`cpf`/single `phone`). Retrofitted 2026-07-29 — see `user-profile-v2` below. Row kept only as history; do not treat any detail below as current for the shipped shape. |
| `user-profile-v2` | ✅ Done (frontend) | Retrofits `user-profile` to `identity-profile-model-v2`'s new contract. `ProfileService` (`core/profile.service.ts`) types rewritten: `UserProfile` now composes `fields: ProfileFields` as a **nested** object (`{userId, email, fields, avatarUrl}`, not flattened — a real deviation from PLAN.md's assumed shape, see that PLAN's "Deviations" section), `ProfileFields` gains `rgOrgaoEmissor`/`birthDate`/structured `Address`/`Contact[]`; new `uploadAvatar(file)` → `POST /api/users/me/profile/avatar` (multipart); `submitEditRequest(fields, contactChanges)` posts `{fields, contactChanges}`. `ProfileFieldsFormComponent` retrofitted in place: 8-field structured address fieldset, a repeatable contacts editor (add/edit/remove, 5-cap enforced client-side with a clear message, one-primary-per-type), diffs contacts against their loaded snapshot at submit time into `ContactChange[]` (`ADD`/`UPDATE`/`REMOVE`); new `showContacts` input (default `true`) — **the shipped `PUT /api/users/{id}/profile` never applies contact changes at all** (`UserProfileService#directEdit` hardcodes an empty `contactChanges` list), a real backend contract gap discovered during this implementation, so `ProfileSectionComponent`'s inline edit of an *other* user sets `[showContacts]="false"` rather than showing controls that would silently no-op. New `AvatarUploadComponent` (`shared/avatar-upload.component.ts`, presentational, current avatar or placeholder + native file input) wired into `OwnProfilePageComponent` only (self-only, always-direct upload, independent of the non-avatar form's pending state) — `OwnProfilePageComponent`'s old `hasDirectEditRight`/admin-shortcut branching is deleted entirely; every session now always calls `POST .../edit-requests` for non-avatar fields (no direct-edit path remains for anyone editing their own record, per `identity-profile-model-v2` REQ-11). `ProfileSectionComponent` gains a new `ownUserId` input; `canEdit` narrows to `canEdit() && userId !== ownUserId()` so the inline-edit affordance is hidden (not just disabled) on the viewer's own row — closes `user-profile/PLAN.md`'s previously-accepted self-exclusion gap, now a hard requirement since REQ-11 removed the admin self-edit bypass entirely. Both `StaffUserDetailPanelComponent`/`MemberDetailPanelComponent` now make one `getOwnProfile()` call per panel-open and thread the result down as `ownUserId`. **Real bug caught by TDAD during this work:** `ProfileSectionComponent` was `implements OnChanges`, which re-ran `loadProfile()` on *any* input change (not just `userId`) — once `ownUserId` started arriving asynchronously after initial render, this caused a spurious duplicate `GET /api/users/{id}/profile`; fixed by moving to a constructor `effect()` scoped only to `userId()`. `ProfileEditRequestsInboxPageComponent` row rendering extended for the structured proposed address and a `proposedContactChanges` list — **however, the backend's `GET /api/profile-edit-requests`/submit-request response always returns `proposedFields.address`/`.contacts` as `null`** (`UserProfileController`/`ProfileEditRequestController#toDto` hardcode `null` there even though the address is genuinely persisted and used internally on approval) — a confirmed backend response-mapping bug, not a frontend gap; the frontend UI is ready and will render correctly once that's fixed. `MembersPageComponent`'s member-detail panel now resolves `ProfileSectionComponent` via `MemberDetail.userId` (that field's earlier absence, flagged in `user-profile`'s row, is confirmed already resolved upstream). 336/336 frontend tests green, `format:check`/`build` clean. **Two backend follow-ups to file, not resolvable from this frontend-only feature:** (1) `PUT /api/users/{id}/profile` should apply `contactChanges` the same way the edit-request approval path does, so an admin/permission-holder editing someone else's contacts directly is actually possible; (2) `ProfileEditRequestDto.proposedFields.address`/`.contacts` should be populated in the list/submit responses instead of hardcoded `null`. Previously-flagged rough edges reconfirmed still accurate and not resolved by this retrofit: the edit-request inbox still shows requesters as `"User #{id}"` only (no display name/email in `ProfileEditRequestDto`), and inbox nav-gating still only reflects the *active* tenant's `PROFILE_EDIT` grant. **Follow-up (1) closed**: `knowly-api` `c0a817d` changed `PUT /api/users/{id}/profile` to accept `{fields, contactChanges}` (`ProfileEditRequestFieldsDto`, the same shape `submitEditRequest` already used) and genuinely apply the contact changes; the frontend closed the loop by restoring `ProfileService#directEdit`'s `contactChanges` parameter (sends the `{fields, contactChanges}` body), dropping `ProfileSectionComponent`'s `[showContacts]="false"` (back to the form's default), and threading `contactChanges` from the form submission through to `directEdit` — mirroring `OwnProfilePageComponent`'s existing `submitEditRequest(fields, contactChanges)` wiring. `StaffUserDetailPanelComponent`/`MemberDetailPanelComponent` needed no change (neither passes `[showContacts]`, so both already inherit the default). 337/337 frontend tests green, `format:check`/`build` clean. **Follow-up (2) also closed** (same `c0a817d`): `proposedFields.address` is now genuinely populated in both `GET /api/profile-edit-requests` and the submit response; `proposedFields.contacts` stays `null` by deliberate Tier 2 scoping (documented in `identity-profile-model-v2`'s code) — the proposed contact add/update/remove set has no defined `ContactDto` (snapshot) shape, and is already correctly surfaced via the separate `proposedContactChanges` (`List<ContactChangeDto>`) field, which the frontend inbox already consumes — not a gap. Both `identity-profile-model-v2` follow-ups are now closed; only the two previously-reconfirmed rough edges above (`"User #{id}"` display, active-tenant-only nav gating) remain open. |
| `boxed-otp-input` | ✅ Done | Login screen's Code tab: single free-text 6-digit input replaced with six individually-boxed digit `<input>`s, inline markup in `login-page.component.ts` (no new `shared/` component — YAGNI, confirmed with user, no second numeric-OTP flow exists today). `digits = signal<string[]>(Array(6).fill(''))` + `code = computed(() => this.digits().join(''))` replaces the old plain `code` signal 1:1; `onSubmitCode`'s call to `AuthService.verifyCode` unchanged. Keydown-level digit rejection (`onDigitKeydown`, REQ-3) keeps commit/advance (`onDigitInput`, REQ-2) and filter/navigate responsibilities disjoint; Backspace-on-empty-box and Left/Right arrow navigation are imperative `document.getElementById('otp-digit-{i}')?.focus()` calls, matching this component's existing `onTabKeydown` focus idiom (no `ViewChildren`/`ElementRef`). One group-level `(paste)` listener (`onPaste`) extracts the first 6 digits from pasted text and distributes them from box 0 regardless of paste-focus position. Submission validation relies on native per-box `required` (REQ-8) — no manual "all six filled" JS check, matching PLAN's explicit no-redundant-validation-path decision. Two new Transloco keys (`login.codeGroupLabel`, `login.codeDigitLabel`) in `en`/`pt-BR`; unused `login.codeLabel` key removed (grep-confirmed no other reference). Existing spec's `input[name="code"]`-based assertions (six call sites, not the ~four originally estimated — see PLAN's "Deviations" section) rewritten with a new `fillOtpBoxes(fixture, code)` test helper simulating real per-box typing. 279/279 frontend tests green (11 new for this feature), `format:check`/`build` clean, single commit (SPEC/PLAN/TASKS were drafted but never separately committed before implementation — landed together with the code in one commit per user's explicit "implement it" instruction covering the whole feature). |
| `primeng-migration` | ⛔ Superseded by `primeng-removal` | Was: full replacement of hand-rolled Tailwind components with PrimeNG + PrimeIcons (2026-07-25). Reverted one day later — see `primeng-removal` below and `DECISIONS.md`'s two consecutive dated entries. Row kept only as history; do not treat any detail below as current. Setup phase: `primeng@22.0.0`/`@primeuix/themes@3.0.0`/`primeicons@8.0.0`/`@angular/cdk@22.0.0` added; `core/prime-theme.ts` preset maps `ink-*`/`signal-*` onto PrimeNG's tokens for light/dark; `providePrimeNG()` wired in `app.config.ts` with `darkModeSelector: '.dark'`. 2026-07-25 chrome/menu pass (items 1-3): `nav-menu.component.ts` rebuilt with per-category inline `p-menu`s (custom `#submenuheader`/`#item` templates keep every `data-testid`/`data-tour-id`/permission gate), PrimeIcons replace its inline SVGs; `app-shell.component.ts`'s `<aside>`/`<header>` get a static `class="dark"` so PrimeNG components in the permanently-dark chrome always render dark tokens regardless of `ThemeService`'s toggle; `logout-button`/`language-switcher`/`help-menu` migrated to `[pButton]`/`p-menu`. 2026-07-25 final pass (items 4-7, all feature screens): `error-state` → `p-message`; `welcome-page`'s quick-link cards → `p-card`; `login-page`'s inputs/buttons → `pInputText`/`pPassword`/`pButton` directives on the same native elements (no DOM restructuring, so all `querySelector('input[...]')`-based specs kept passing unchanged); `articles-page`'s upload/detail panels → `p-card`, text inputs → `pInputText`/`pTextarea`, buttons → `pButton` (article-list rows left as native markup — two independent actions per row, not a clean `Listbox` fit); `conversations-page`'s conversation list → `p-listbox` with a custom `#item` template (chat bubbles and the new-conversation/send buttons use `pButton`/`pInputText`; bubbles themselves stay bespoke `<div>`s, no PrimeNG fit); `members-page`'s member list → `p-table`; `select-tenant-page`'s tenant list → `p-listbox`, create-tenant link → `pButton`; `tenant-create-page`'s form → `pInputText`/`pButton`. `no-access-state` (single `<p>`) and login's tab UI stayed hand-rolled — no real PrimeNG component fit either. `angular.json`'s budget raised 800kB→900kB warning, 1MB→1.4MB error (bundle reached 1.27MB after the full migration; still not lazy-route-split — flagged as a follow-up, not solved here). |
| `primeng-removal` | ✅ Done | Reverts `primeng-migration` (see `DECISIONS.md`) — PrimeNG, `@primeuix/themes`, `primeicons`, `@angular/cdk` fully removed; back to pure Tailwind + hand-rolled Angular standalone components. Icons: `@lucide/angular` (not the deprecated `lucide-angular`, which has no Angular 22-compatible peer range) — each icon is its own standalone component with an attribute selector (e.g. `LucideSun` → `<svg lucideSun>`), imported directly per-component, no central provider wiring. New shared `button-classes.ts` (severity/variant class helper) and `chart-canvas.component.ts` (direct Chart.js wrapper, replacing PrimeNG's `p-chart`/`UIChart` — uses a `CHART_CTOR` injection token so specs can mock Chart.js deterministically despite Angular's bundled-spec test runner sharing module instances). All 20 former PrimeNG consumers migrated to native HTML + Tailwind (menus → `<ul role="menu">`, tables → native `<table>` + local `computed()` filter signal, listbox → `<ul role="listbox">`, forms/buttons → native elements + `button-classes.ts`). 26 atomic tasks, one commit each — see `knowly-app/specify/features/primeng-removal/PLAN.md`/`TASKS.md` (including a "Deviations" section for the two implementation-detail corrections above). 221/221 tests, `format:check`, `build` all green. |

**As of the last working session:** test suite speed (`forkCount=2` +
JTE precompiled-templates fix, see `DECISIONS.md`), all six backend
features above, and `navigation-menu`/`welcome-screen` on the frontend
are done. Next: user management screens (see "Next up" above) — write
its SPEC(s) first, split by subproject per the "Feature SPEC placement"
rule.

## Known operational/tooling notes worth knowing

### Backend (`knowly-api/`)

- Maven Surefire is deliberately configured for **full isolation per test
  class** (`reuseForks=false`, `spring.test.context.cache.maxSize=1`) —
  this was A/B tested live: disabling it to speed up the suite produced
  flaky failures (shared Redis captcha counters, cross-test-class DB
  collisions from context reuse). Keep `reuseForks=false` as-is unless
  re-validated. `forkCount=2` (concurrent isolated forks) was re-validated
  2026-07-25 — full suite ~14m10s → ~12m (two clean runs); `forkCount=4`
  was rejected (no further speedup, intermittent JTE template-compile race
  — see `DECISIONS.md`). **Re-measured 2026-07-28** (`tenant-pagination-search`,
  377 tests, real elapsed clocked end-to-end): **~21 minutes** — the ~12m
  figure above no longer holds as the suite has grown since 2026-07-25;
  treat "~12 minutes" as stale and budget closer to ~20+ minutes for a
  full `./mvnw verify` going forward. Further speedup would need reducing
  per-class Spring Boot context startup cost itself (~20-25s/class), not
  just more parallelism — not yet attempted.
- Tests must run with `gg.jte.use-precompiled-templates: true` /
  `development-mode: false` (`src/test/resources/application-test.yaml`),
  not main's dev-mode hot-reload — see `DECISIONS.md` for why (CWD-shared
  on-demand compile directory races under concurrent Surefire forks).
- `compose.yaml`'s `minio` service depends on a one-shot
  `minio-init-permissions` container to `chown` its data volume — MinIO's
  own entrypoint does not do this itself under the hardened
  `cap_drop: ALL` + non-root setup this project uses.
- `identity-profile-model`'s `cpf`/`rg` encryption (`CpfRgEncryptionConverter`)
  has no key-rotation strategy: `users_aud` (envers audit history) mirrors
  the same ciphertext as the live column, so rotating the encryption key
  would need to also re-encrypt every historical audit row, not just
  `users`. Out of scope for that feature (appsec-reviewed, no blocking
  finding — the blind-index equality-revealing tradeoff is deliberate per
  its SPEC), but flagged here so a future key-rotation task doesn't
  discover the audit-table coupling from scratch.
- `spring.ai.vectorstore.pgvector.dimensions` is pinned explicitly (1536,
  matching `text-embedding-3-small`) in both test and production config —
  without it, Spring AI calls the real OpenAI embeddings endpoint just to
  infer the dimension at every startup.
- `tesseract-ocr` must be installed on the local dev machine (see
  `knowly-api/README.md`'s Prerequisites) for
  `ArticleExtractionListenerTest`'s OCR test to pass; the runtime Docker
  image already has it.

### Frontend (`knowly-app/`)

- Angular is **zoneless** (no zone.js) — tests must use Vitest's
  `vi.useFakeTimers()`, not Angular's `fakeAsync`/`tick`.
- Node version is pinned in `.nvmrc` (use `nvm use` before `npm test`/
  `npm run build` — running with the wrong Node major fails immediately).
- API calls always go through `/api/...`, proxied to the backend in dev
  (`proxy.conf.json`) — no open CORS on the backend side.

### Monorepo / CI

- The two subprojects each get their own path-filtered GitHub Actions
  workflow (`ci-backend.yml`, `ci-frontend.yml`) plus a shared
  `codeql.yml` — none of these block each other; a change to
  `knowly-app/**` never triggers the backend's Maven build, and CodeQL
  runs as its own independent workflow, not a step the build waits on.
