# Project status

> **Read this before starting any work in this repo — in any conversation,
> with any AI assistant.** This file exists so that a fresh conversation
> (no memory of prior sessions) can pick up exactly where the last one left
> off, without re-deriving context from scratch. It is checked into git,
> so it travels with the repo regardless of which tool or model opens it.
>
> **You must also update it before finishing your work.** This is not
> optional and not just for Claude — any AI assistant (Claude, GPT, Gemini,
> whatever) that implements or changes a feature in this repo is expected
> to edit this file as part of that task, the same way it's expected to
> run the test suite. Concretely, before considering a task done:
> - Update the feature's row in the table below (status, one-line note)
>   if you finished, started, or changed the shape of a feature.
> - Add a bullet to "Known operational notes" if you hit and fixed an
>   infra/tooling gotcha someone else would otherwise waste time on again.
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
> backlog table above. Whoever finishes a task (any AI) updates this
> section before signing off, so the *next* conversation — possibly
> opened cold, possibly by a different AI — knows exactly what to do
> without the user having to re-explain anything.
>
> Protocol for handling a direction-less request with no other context:
> 1. Read this section. If it names a concrete next action, do that (or
>    propose it and ask for a quick go-ahead if it's a meaningfully sized
>    new feature) — following SDD (SPEC → PLAN → TASKS → TDAD) as normal.
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
`staff-user-provisioning`, and `navigation-menu` (frontend) all done.
Confirmed roadmap in progress — next up is user management screens.**
The user confirmed this order for the next several features
(2026-07-25):

1. ~~Bootstrap staff-admin one-shot user~~ — done, see
   `specify/features/staff-bootstrap-user/`.
2. ~~RBAC split~~ — done, see `specify/features/staff-rbac-split/`.
   `GlobalRole` is now `STAFF_ADMIN` (unrestricted, was the only value
   before) / `STAFF` (permission-gated, mirrors `tenancy`'s
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
   `specify/features/staff-user-provisioning/`. New
   `GlobalPermission.STAFF_USER_CREATE` (independent from
   `STAFF_PERMISSION_MANAGE`) gates `POST /api/staff/users`, which
   creates a `GlobalRole.STAFF` user (never `STAFF_ADMIN`) and emails
   them a one-time password via the existing
   `OneTimePasswordService`/`MailService` mechanism. Tenant member
   provisioning (`addMember`) needed no change — it already worked via
   the passwordless login-code flow. Promoting/demoting `STAFF_ADMIN`
   and deactivating a staff user are explicitly out of scope (see that
   SPEC) — flag if either becomes needed later.
4. ~~Navigation menus~~ — done, frontend-only, see
   `knowly-app/specify/features/navigation-menu/`. No backend change was
   needed (consumed the existing `GET /api/tenants/permissions` and
   `staff-rbac-split`'s `GET /api/staff/permissions` as-is). Also fixed a
   frontend bug this uncovered: `staff.guard.ts` inferred "is staff"
   from `GET /api/tenants` succeeding, which broke once
   `staff-rbac-split` made staff access individually granted (a `STAFF`
   user granted only `TENANT_CREATE`, not `TENANT_ACT_AS_ANY`, was
   wrongly blocked from tenant creation).
5. **User management screens** (staff user management globally; tenant
   user management per-tenant). **This is the next feature to SPEC** —
   likely split into a backend SPEC here (any missing endpoints, e.g.
   listing/searching all staff users — `staff-rbac-split` only added
   per-user detail/grant endpoints, not a listing one) and a frontend
   SPEC in `knowly-app` for the screens themselves, per the cross-repo
   SPEC placement rule in both repos' `constitution.md`.
6. Expanded metrics dashboard.

Backend and frontend work can proceed in parallel per feature once each
one has an approved SPEC/PLAN that defines the API contract — see
`knowly-app`'s `PROJECT_STATUS.md` for the frontend side of this same
roadmap.

## How to work in this repo

This project follows **Spec-Driven Development (SDD)** — see
[`CLAUDE.md`](CLAUDE.md) and
[`specify/memory/constitution.md`](specify/memory/constitution.md) for the
full process (SPEC → PLAN → TASKS → TDAD implementation). In short:

1. Never implement from a vague request. If `specify/features/<name>/SPEC.md`
   doesn't exist for what's being asked, write it first (EARS/GEARS syntax)
   and get it approved.
2. Then PLAN.md (technical decisions) and TASKS.md (atomic, checkbox-tracked
   steps).
3. Implement task by task: test first (Red), minimal code (Green),
   `./mvnw test`.
4. Before calling a task done: `./mvnw spotless:apply && ./mvnw verify`.

## Feature status

Every feature below has its own `specify/features/<name>/{SPEC,PLAN,TASKS}.md`
— read those for the actual requirements and decisions. This table is only a
map of *what exists* and *how done it is*; it is not a substitute for reading
the feature's own SPEC.

| Feature | Status | Notes |
|---|---|---|
| `authentication` | ✅ Done | Login-code (passwordless) flow, sessions, logout (`POST /api/auth/logout`). |
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

**As of the last working session:** test suite speed (`forkCount=2` +
JTE precompiled-templates fix, see `DECISIONS.md`), `staff-bootstrap-user`,
`staff-rbac-split`, `staff-user-provisioning`, and (frontend-only)
`navigation-menu` are all done. Next: user management screens (see
"Next up" above) — write its SPEC(s) before implementing.

## Known operational notes worth knowing before touching infra/CI

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
  README's Prerequisites) for `ArticleExtractionListenerTest`'s OCR test to
  pass; the runtime Docker image already has it.

## Companion repo

The frontend lives in a sibling repo, `knowly-app` — it has its own
`PROJECT_STATUS.md` with the same kind of map for its features (which
mostly mirror this repo's: `login`, `select-tenant`,
`onboarding-dashboard`, `article-management`, `conversations`,
`user-management`; plus `tags-list`, also reference-only).
