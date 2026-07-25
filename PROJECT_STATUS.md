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

**Current state: nothing queued.** Every existing feature is done and
verified (see the table below and the backend's `PROJECT_STATUS.md`).
There is no in-flight SPEC/PLAN/TASKS right now. If asked for "the next
step" as of this state, the right response is step 2 of the protocol
above — ask the user which direction to prioritize next, rather than
assuming.

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
| `select-tenant` | ✅ Done | Multi-membership tenant picker; also handles the 0-membership staff case by falling back to the backend's all-tenants listing (`GET /api/tenants`) when the memberships list comes back empty. |
| `onboarding-dashboard` | ✅ Done | First-run tour + dashboard metric widgets. |
| `article-management` | ✅ Done | Upload (with polling for embedding status), inline edit, delete, permission-gated UI. |
| `conversations` | ✅ Done | Chat UI over SSE (hand-rolled parser — native `EventSource` can't POST a body). |
| `user-management` | ✅ Done | Tenant members/roles/permissions/access-groups admin UI. |
| `tags-list` | 📄 Reference only | **Not implemented on purpose** — exists solely as the canonical example of the SPEC/PLAN/TASKS format, paired with the backend's `tags-crud` reference. Don't build it unless explicitly asked to turn it into a real feature. |

**As of the last working session: no open backlog.** Every real feature is
fully implemented, tested, and passing `npm run format:check && npm test &&
npm run build` clean. If you're starting fresh work, it's a *new* feature —
write its SPEC first.

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
