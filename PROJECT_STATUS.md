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
> - Add a bullet to "Known operational/tooling notes" if you hit and fixed
>   a gotcha someone else would otherwise waste time on again.
> - The product vision lives in the backend repo's
>   [`knowly/VISION.md`](../knowly/VISION.md) — if the long-term direction
>   changed based on something the user said, update that file too.
> If you finish a session without touching this file and something
> changed, the next conversation (possibly a different AI, possibly the
> user talking to a teammate's assistant) starts from stale information —
> that defeats the entire point of this file existing.

## Next up

> **This section exists specifically for whenever the user opens a
> conversation without specifying what to work on** — regardless of how
> that's phrased or in what language; judge intent, not wording. It must
> always name a concrete, literal next action for *this* repo — not a
> restatement of the backlog table above. Whoever finishes a task (any
> AI) updates this section before signing off. The authoritative
> cross-repo picture (since the next step may be backend-only,
> frontend-only, or both) lives in `knowly/PROJECT_STATUS.md`'s own
> "Next up" section — check there too if the request could plausibly
> involve the backend.
>
> Protocol for handling a direction-less request with no other context:
> 1. Read this section (and the backend's, if relevant). If it names a
>    concrete next action, do that — following SDD (SPEC → PLAN → TASKS →
>    TDAD) as normal.
> 2. If nothing is queued (current state, see below), **do not silently
>    invent a feature and start building it.** Propose candidate
>    directions and ask the user to pick — see `knowly/VISION.md` for
>    what's deliberately undecided so far. Then update this section with
>    whatever they choose.
> 3. Once a direction is chosen, this section should name the concrete
>    in-flight SPEC/PLAN/TASKS and which item is next, not just "in
>    progress."

**Current state: `navigation-menu` and `welcome-screen` done
(2026-07-25).** The backend's `PROJECT_STATUS.md` has a confirmed
multi-feature roadmap in progress — next up (item 5) is user management
screens. Check the backend's `PROJECT_STATUS.md` "Next up" before
starting anything, since the next item may need backend work first.

**Backlog (reported by the user 2026-07-25, not yet scoped/SPEC'd) —
each needs its own SPEC before implementation, roughly in this order:**

1. Tenant list pagination + search-by-name on `/select-tenant` and the
   backend's `GET /api/tenants` (currently returns everything
   unbounded — will break at scale). Needs a backend SPEC (pagination/
   search API contract) and a frontend SPEC (UI).
2. Boxed/segmented one-time-code input on the login screen (currently a
   single plain text field) — matches the common "one box per digit"
   pattern. Frontend-only.
3. **Full identity/profile model — big, LGPD-sensitive, needs its own
   SPEC(s) before any code.** Per the user: both the tenant (company)
   and every user (person) need complete, non-duplicable identity data.
   - Tenant: full company registration (CNPJ + whatever else is legally
     unique per company — needs confirming which fields), enforced
     unique.
   - User: email, full address, RG, CPF, phone number, etc., each
     enforced unique across all users (no two users may share a CPF,
     RG, email, or phone). CPF/RG are sensitive personal data under
     Brazil's LGPD — before implementing, must decide retention,
     at-rest encryption, and who can access raw values vs. just the
     audit trail.
   - Self-edit is restricted: a user cannot edit their own profile
     fields — only someone holding the relevant permission can. A user
     only sees their own profile and their own display nickname (the
     name shown to others in chat — see item 4).
   - Backend: new entity/entities in `knowly`, migration(s), permission
     gating, uniqueness constraints at the DB level (not just app-level
     validation). Frontend: profile view/edit screens in `knowly-app`.
4. **Internal team chat — big, new product surface, deferred until
   after the identity model above.** 1:1 conversations and group
   conversations between team members (distinct from the existing
   chat-with-the-knowledge-base feature) — uses the profile nickname
   from item 3 to identify people in the UI. Needs its own SPEC(s) in
   both repos once prioritized.
5. **Design system overhaul — no animations today, "muito simplório."**
   Needs scoping before a SPEC makes sense: which screens first, what
   motion/interaction language, whether it's a new design-system
   reference doc or per-screen polish. Ask the user to prioritize
   specific screens rather than attempting a big-bang redesign.

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
   `npm test`.
4. Before calling a task done: `npm run format:check && npm test && npm run build`.

## Feature status

Every feature below has its own `specify/features/<name>/{SPEC,PLAN,TASKS}.md`
— read those for the actual requirements and decisions. This table is only a
map of *what exists* and *how done it is*; it is not a substitute for reading
the feature's own SPEC.

| Feature | Status | Notes |
|---|---|---|
| `login` | ✅ Done | Login-code (passwordless) flow. |
| `logout` | ✅ Done | Logout button in the app shell's fixed corner cluster; calls `POST /api/auth/logout`. Also introduced this app's first CSRF token wiring (`withXsrfConfiguration()`), since logout is the first authenticated, non-CSRF-exempt POST the frontend makes. |
| `select-tenant` | ✅ Done | Multi-membership tenant picker; also handles the 0-membership staff case by falling back to the backend's all-tenants listing (`GET /api/tenants`) when the memberships list comes back empty. |
| `onboarding-dashboard` | ✅ Done | First-run tour + dashboard metric widgets. |
| `article-management` | ✅ Done | Upload (with polling for embedding status), inline edit, delete, permission-gated UI. |
| `conversations` | ✅ Done | Chat UI over SSE (hand-rolled parser — native `EventSource` can't POST a body). |
| `user-management` | ✅ Done | Tenant members/roles/permissions/access-groups admin UI. |
| `tenant-creation` | ✅ Done | Staff-only `/tenants/new` form (name + first admin email) calling `POST /api/tenants`. Originally gated by an `isStaff` heuristic (whether `GET /api/tenants` succeeded); `navigation-menu` replaced that with the real `GlobalPermission.TENANT_CREATE` check (`GET /api/staff/permissions`) after the backend's `staff-rbac-split` made that heuristic wrong for a `STAFF` user granted `TENANT_CREATE` but not `TENANT_ACT_AS_ANY`. |
| `tags-list` | 📄 Reference only | **Not implemented on purpose** — exists solely as the canonical example of the SPEC/PLAN/TASKS format, paired with the backend's `tags-crud` reference. Don't build it unless explicitly asked to turn it into a real feature. |
| `navigation-menu` | ✅ Done | Real app-shell navigation (`nav-menu.component.ts`), links filtered by `PermissionsService`/`GlobalPermissionsService`; "switch tenant" link reusing `/select-tenant`. Fixed the `staffGuard`/create-tenant-link bug above as part of the same feature. |
| `welcome-screen` | ✅ Done | Real `/welcome` landing screen (staff-generic or tenant-branded greeting, no sensitive/permission-gated content) — replaces `/dashboard` as the post-login/tenant-selection/root-redirect target. Fixed two real bugs: login and the root route (`''`) both used to send an already-authenticated session to the wrong place (tenant list, or unconditionally `/login`). Onboarding tour trigger moved here from `dashboard`; tour target ids moved to the global nav menu. See `select-tenant` SPEC's amended REQ-5 (0-membership sessions no longer redirect to `/select-tenant`). |

**As of the last working session:** `navigation-menu` and
`welcome-screen` are done — see this file's "Backlog" section above for
several bigger items reported by the user (tenant pagination/search,
segmented OTP input, full identity/profile model with CPF/RG, internal
team chat, design overhaul), none yet SPEC'd. Next: either pick up that
backlog in the stated order, or whatever the backend's
`PROJECT_STATUS.md` "Next up" names (currently item 5 — user management
screens) — write SPEC(s) first, split by repo per the cross-repo SPEC
placement rule.

## Known operational/tooling notes worth knowing

- Angular is **zoneless** (no zone.js) — tests must use Vitest's
  `vi.useFakeTimers()`, not Angular's `fakeAsync`/`tick`.
- Node version is pinned in `.nvmrc` (use `nvm use` before `npm test`/
  `npm run build` — running with the wrong Node major fails immediately).
- API calls always go through `/api/...`, proxied to the backend in dev
  (`proxy.conf.json`) — no open CORS on the backend side.

## Companion repo

The backend lives in a sibling repo, `knowly` — it has its own
`PROJECT_STATUS.md` with the same kind of map for its features (which
mostly mirror this repo's: `authentication`, `tenancy`,
`article-management`, `conversations`, `dashboard-metrics`,
`onboarding-status`, `api-documentation`; plus `tags-crud`, also
reference-only).
