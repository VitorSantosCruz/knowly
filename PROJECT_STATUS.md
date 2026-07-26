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
`dashboard-analytics` backend are all done. Next up (item 5 below) is
user management screens** — no SPEC written for it yet. The
`dashboard-analytics` frontend (item 6) already has an approved
SPEC/PLAN/TASKS in `knowly-app/` and is ready for implementation
whenever picked up.

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
5. **User management screens** (staff user management globally; tenant
   user management per-tenant). **This is the next feature to SPEC** —
   likely split into a backend SPEC (any missing endpoints, e.g.
   listing/searching all staff users — `staff-rbac-split` only added
   per-user detail/grant endpoints, not a listing one) and a frontend
   SPEC in `knowly-app/` for the screens themselves, per the "Feature SPEC
   placement" rule in `specify/memory/constitution.md`. **Rules confirmed
   by the user 2026-07-26**, ahead of the SPEC:
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
6. ~~Expanded metrics dashboard~~ — **backend done**, see
   `knowly-api/specify/features/dashboard-analytics/` and its row in the
   backend feature table above. Frontend SPEC/PLAN/TASKS already exist
   at `knowly-app/specify/features/dashboard-analytics/` (written
   alongside the backend SPEC per the cross-folder placement rule) but
   are **not yet implemented** — that's the next concrete action for
   this item. **New scope confirmed by the user 2026-07-26**, not yet
   covered by the existing SPEC — will need a follow-up backend SPEC
   (new endpoints/metrics) before the frontend work above can be
   considered complete:
   - **Inside a tenant**: dashboard shows that tenant's own numbers —
     article count, most-used/top articles, query count per member,
     usage graphs, and (new, once support tickets exist per item 14)
     support-ticket metrics for that tenant specifically. This is
     largely what today's `dashboard-analytics` backend already
     provides (`members`/timeseries/`export` endpoints) — mostly a
     frontend implementation gap, not a new backend needed, except for
     support-ticket metrics which don't exist until item 14 lands.
   - **Outside any tenant (staff global view)**: a *different* set of
     metrics scoped across all tenants — total tenant count, new
     tenants in the last calendar month, total articles read across
     every tenant, total support tickets across every tenant, staff
     member count — plus a staff-specific welcome screen (distinct from
     the tenant member's welcome screen) and a member-listing screen
     that lets a staff user open a profile, edit it (subject to the
     profile-editing permission rules in item 13), and view that
     person's audit trail. None of this global/staff-scope aggregation
     exists in the backend today — `dashboard-analytics`'s endpoints are
     all tenant-scoped (`TenantFilter`-gated). This needs its own
     backend SPEC for global/cross-tenant metrics endpoints, gated by a
     `GlobalPermission` (not tenant `Permission`), separate from the
     existing tenant-scoped `dashboard-analytics` feature.
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
9. **Staff-joins-tenant requires in-app acceptance (not email) —
   corrected 2026-07-26 after an earlier misread of this rule.** The
   real scenario: a `MEMBER_ADMIN` of some tenant adds an existing
   `STAFF`/`STAFF_ADMIN` user as a `MEMBER` (or `MEMBER_ADMIN`) of their
   tenant. Doing so must NOT silently strip that staff user's global
   powers inside that tenant — it can only take effect once the staff
   user explicitly accepts becoming a member of that tenant, via an
   **in-app request/notification** (not an email-based accept flow, that
   was this analysis's earlier misunderstanding — corrected here).
   Until accepted, the tenant-membership row must not restrict the
   staff user's access within that tenant. Symmetric case: if a user was
   already a plain tenant `MEMBER` *before* becoming `STAFF`/
   `STAFF_ADMIN`, someone must explicitly deactivate that old membership
   — otherwise the old, pre-staff `MEMBER` role keeps silently limiting
   them inside that specific tenant even after gaining staff powers.
   Purpose: stop a tenant from using membership assignment/pre-existing
   membership to blind a staff member to what's happening inside that
   one tenant (staff must consciously accept losing/gaining tenant-local
   scope — nothing about this affects what staff can see/do on knowly's
   own side, only within that specific tenant's data). Needs a backend
   SPEC: new membership state (pending/active?), an in-app
   notification/request-accept mechanism (no email), and rules for what
   authorization a *pending* (not yet accepted) membership grants (none
   — staff keeps their prior effective access until acceptance).
10. **Tenant membership invitation requires acceptance — corrected
    2026-07-26, in-app notification, not email.** `TenantService.addMember`
    currently adds a membership directly and synchronously, with no
    pending/accept state and no notification of any kind (`MailService`
    isn't even wired into `TenantService`). Confirmed rule: adding *any*
    member should create a pending state that requires the invitee to
    accept via an in-app notification/request (not email) before the
    membership becomes active; once accepted, the tenant owner and
    whoever added the member should be notified in-app that it was
    accepted. This overlaps significantly with item 9 above (both need
    the same pending-membership/accept mechanism) — likely one shared
    backend SPEC covering both. **Exception confirmed 2026-07-26**: the
    pending/accept flow only applies when the invitee already has a
    `User` account to notify in-app. If the invited person has no
    account in the system yet, there's no in-app inbox to deliver a
    notification to, so the pending/accept step is skipped entirely for
    that case — the membership is created active immediately, same as
    today's behavior. (This is the existing passwordless/login-code
    flow's normal new-user path — inviting a brand-new email already
    creates the `User` at invite time; that path stays unchanged and
    simply doesn't gain a pending state, since there's nobody yet to ask
    for consent.)
11. Tenant list pagination + search-by-name on `/select-tenant` and the
   backend's `GET /api/tenants` (currently returns everything unbounded
   — will break at scale). Needs a backend SPEC (pagination/search API
   contract) and a frontend SPEC (UI).
12. Boxed/segmented one-time-code input on the login screen (currently a
   single plain text field) — matches the common "one box per digit"
   pattern. Frontend-only.
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
| `tenant-creation` | ✅ Done | Staff-only `/tenants/new` form (name + first admin email) calling `POST /api/tenants`. Originally gated by an `isStaff` heuristic (whether `GET /api/tenants` succeeded); `navigation-menu` replaced that with the real `GlobalPermission.TENANT_CREATE` check (`GET /api/staff/permissions`) after the backend's `staff-rbac-split` made that heuristic wrong for a `STAFF` user granted `TENANT_CREATE` but not `TENANT_ACT_AS_ANY`. |
| `tags-list` | 📄 Reference only | **Not implemented on purpose** — exists solely as the canonical example of the SPEC/PLAN/TASKS format, paired with the backend's `tags-crud` reference. Don't build it unless explicitly asked to turn it into a real feature. |
| `navigation-menu` | ✅ Done | Real app-shell navigation (`nav-menu.component.ts`), links filtered by `PermissionsService`/`GlobalPermissionsService`; "switch tenant" link reusing `/select-tenant`. Fixed the `staffGuard`/create-tenant-link bug above as part of the same feature. |
| `welcome-screen` | ✅ Done | Real `/welcome` landing screen (staff-generic or tenant-branded greeting, no sensitive/permission-gated content) — replaces `/dashboard` as the post-login/tenant-selection/root-redirect target. Fixed two real bugs: login and the root route (`''`) both used to send an already-authenticated session to the wrong place (tenant list, or unconditionally `/login`). Onboarding tour trigger moved here from `dashboard`; tour target ids moved to the global nav menu. |
| `dashboard-analytics` | ✅ Done | Period filter (`period-filter.component.ts`, PrimeNG `SelectButton`) owned by `dashboard-page.component.ts`'s `period` signal; five reusable `metric-tile.component.ts` instances (active articles/conversations/USER messages/ASSISTANT messages/active members, each with a `p-chart` line sparkline + `toSparklineData()`), superseding the old `article-count-card`/`conversations-card`/`messages-card` (all three deleted); `message-split-chart.component.ts` (USER/ASSISTANT donut, `toDonutData()`); `conversations-activity-chart.component.ts` (per-day bar chart, `toBarData()`); every chart paired with a visually-hidden `.sr-only` mirror `<table>` generated from the same tested mapping function; `members-breakdown-card.component.ts` (`GET /api/tenants/metrics/members`); `top-articles-table.component.ts` (`p-table` + built-in global filter, replaces `article-usage-list.component.ts`); `export-button.component.ts` (CSV blob download); `metric-fetcher.ts`'s `load()` extended to accept `params`. **`chart.js@^4.5.1` added as a real dependency** (user-confirmed Tier 3 decision, `DECISIONS.md`) — discovered only during implementation that PrimeNG's `Chart` component (`p-chart`) does `import Chart from 'chart.js/auto'` internally and needs it installed, contrary to the SPEC/PLAN's original "no new npm dependency" assumption; both docs updated to record why. Production bundle budget raised again in `angular.json` (900kB→1.4MB warning, 1.4MB→1.7MB error) to accommodate it — final bundle 1.50MB raw / ~310KB estimated transfer, under the new error threshold. 210/210 frontend tests green, `format:check`/`build` clean. |
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
  — see `DECISIONS.md`). Full suite still takes ~12 minutes; further
  speedup would need reducing per-class Spring Boot context startup cost
  itself (~20-25s/class), not just more parallelism — not yet attempted.
- Tests must run with `gg.jte.use-precompiled-templates: true` /
  `development-mode: false` (`src/test/resources/application-test.yaml`),
  not main's dev-mode hot-reload — see `DECISIONS.md` for why (CWD-shared
  on-demand compile directory races under concurrent Surefire forks).
- `compose.yaml`'s `minio` service depends on a one-shot
  `minio-init-permissions` container to `chown` its data volume — MinIO's
  own entrypoint does not do this itself under the hardened
  `cap_drop: ALL` + non-root setup this project uses.
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
