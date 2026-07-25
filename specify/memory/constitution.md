# Constitution — knowly-app (frontend)

Non-negotiable rules for any human or AI agent implementing code in this
repository. If a plan or task conflicts with this document, this document
wins.

**Read [`../knowly/specify/memory/sdd-methodology.md`](../knowly/specify/memory/sdd-methodology.md)
alongside this file** — it's the deeper reasoning behind every rule
below (why SDD prevents "vibe coding," the Builder/Verifier pattern TDAD
already implements, why scope drift and over-specification are both
failure modes, concrete anti-patterns already observed in this project).
It lives in the backend repo (same reason `VISION.md`/`DECISIONS.md` do)
but applies to both. It never overrides this file; it explains it.

## Why Spec-Driven Development (read this before the mechanics below)

The sections further down describe *how* to follow SDD (SPEC → PLAN →
TASKS → TDAD). This section exists so that any AI agent reading this
file — even one that has never seen the reasoning behind SDD before —
understands *why* the process is this way, well enough to make good
judgment calls in situations the mechanics don't explicitly cover.

- **The core problem SDD solves is memory loss, not process for its own
  sake.** Neither humans nor AI assistants reliably carry full context
  from one work session to the next — and in this project specifically,
  work is deliberately split across many separate, short conversations
  (see `PROJECT_STATUS.md`), possibly with different AI tools entirely,
  none of which share memory with each other. A SPEC/PLAN/TASKS set is
  the thing that *does* persist. It has to fully replace whatever context
  a human might otherwise carry in their head or a chat history might
  otherwise contain — that's the bar for "is this SPEC good enough,"
  not "did it satisfy whoever wrote it."
- **SPEC.md is implementation-agnostic on purpose.** It says what the
  system must do and why, never how. This lets the PLAN (or even the
  whole tech stack) change later without re-litigating whether the
  underlying requirement is still correct — the SPEC is the part that's
  expensive to get wrong and cheap to keep stable.
- **EARS/GEARS syntax is mandatory because plain-language requirements
  are usually too vague to implement or test against consistently.**
  "The screen should handle errors gracefully" gives an AI agent nothing
  to write a test against and nothing to verify "done" means. Forcing
  every requirement into a trigger → condition → action shape
  (Ubiquitous/Event-Driven/State-Driven/Optional/Unwanted
  Behavior/Complex) makes each one directly translatable into a test —
  which is what makes TDAD (test-first) possible at all, and what makes
  a checked acceptance-criteria box mean something concrete rather than
  a vibe.
- **PLAN.md is kept separate from SPEC.md** so "what/why" and "how" can
  evolve independently — a requirement doesn't need to change just
  because an implementation detail does, and vice versa.
- **TASKS.md is atomic and ordered specifically because an AI agent
  (or a human picking up cold) needs to execute one task with high
  confidence in a single pass**, without needing the full feature's
  context loaded at once. This is an AI-development-friendly grain size,
  not just generic good practice — a task too large invites drift or
  partial completion that's hard to detect later.
- **Every task is test-first (TDAD) because "the code looks right" is
  not a verifiable claim, and self-reported completion by any agent —
  including this one — isn't trustworthy on its own.** A red test that
  turns green against the exact requirement in the SPEC is the actual
  evidence a task is done.
- **None of this replaces judgment.** When a SPEC is silent on an edge
  case, or a PLAN decision turns out to be wrong once implementation
  starts, the right move is to update the SPEC/PLAN (with reasoning),
  not to quietly deviate from it in code while leaving the documents
  stale — a stale spec is worse than no spec, because it actively
  misleads whoever (or whatever) reads it next.

## Stack and technical conventions

- Angular (version pinned in `package.json`) with strict TypeScript. Do not
  introduce another UI framework or state management library without
  updating this constitution first.
- Package manager: npm (see `packageManager` in `package.json`). Do not use
  yarn/pnpm.
- Styling with Tailwind CSS (`.postcssrc.json`, `tailwind`). Avoid loose CSS
  outside the utility pattern unless justified.
- Tests with Vitest (`npm test`).
- Formatting via Prettier (`.prettierrc`) — never reformat by hand, always
  run `npm run format`. `npm run format:check` runs in CI and fails the
  build if a file isn't formatted.
- Node version pinned in `.nvmrc` (active LTS). Update it together with any
  Angular bump that requires a newer Node.
- CI runs on GitHub Actions (`.github/workflows/ci.yml`): formatting, tests
  (Vitest), build, and Docker image build on every push/PR to `main`.
- Multi-stage `Dockerfile` (build with Node, runtime served by
  `nginx:*-alpine`, non-root user). Routing `/api` in production is the
  responsibility of the environment's reverse proxy/gateway, not the
  image's Nginx (see `nginx.conf`). CI pushes the image to the
  **GitHub Container Registry** on every push to `main` (tags: `latest`
  and `sha-<commit>`).

## Observability

This project must be fully auditable end to end. The backend owns the
audit trail (JPA Auditing, Envers, structured logs — see the backend
constitution), but the frontend has a role too:

- Every backend response includes a trace id (via OpenTelemetry
  propagation). The frontend must surface it in error states (e.g. in a
  support-facing error detail) so a user's report can be correlated with
  backend logs/traces.
- Never log or display sensitive data (codes, one-time passwords, session
  identifiers) in the browser console or in error messages shown to the
  user.

## Global UI conventions

- Language selection and light/dark theme are global, persistent user
  preferences: once set, they apply across every screen in the app, not
  just where they were changed (persist in `localStorage`, restored on
  load).
- The backend always returns messages/error codes in English. The backend
  contract is stable identifiers (error codes), never free text meant for
  end users — the frontend is the only layer that renders user-facing text,
  localized to the user's selected language.

## Integration with the backend (knowly)

- In development, all API calls use the `/api` prefix and are proxied to
  `http://localhost:8080` via `proxy.conf.json` (`ng serve` already uses
  this proxy by default — see `angular.json`).
- Do not call `localhost:8080` directly from application code: always use
  relative paths (`/api/...`), so production (same origin, behind a reverse
  proxy) and development work without configuration branching or the need
  for open CORS on the backend.

## Code rules

- Never expose secrets, keys, or API tokens in client-side code.
- Strict TypeScript typing: avoid `any`; prefer explicit types/interfaces.
- Prefer component composition over inheritance.
- No speculative abstractions: implement only what the spec/task asks for.
- No comments that describe the obvious; comment only the non-obvious.
- Fix the root cause of linter/IDE warnings instead of suppressing them.

## Workflow (Spec-Driven Development)

1. **constitution.md** (this file) — permanent rules of the project.
2. **SPEC.md** — the what and the why. Requirements in EARS/GEARS syntax,
   implementation-agnostic. Source of truth for expected behavior.
3. **PLAN.md** — the how. Concrete technical decisions (components, routes,
   services, consumed API contracts) derived from the SPEC.
4. **TASKS.md** — list of atomic, verifiable tasks derived from the PLAN.
5. **Implement** — tasks executed in order, test-first (TDAD), each small
   enough to be executed with high confidence in a single iteration.
6. **Analyze** — a mandatory closing gate before a *feature* (not just a
   task) is done: re-read constitution.md, the feature's SPEC.md,
   PLAN.md, and TASKS.md together and confirm they're still mutually
   consistent, then re-check every SPEC.md acceptance-criterion checkbox
   against the finished implementation one by one. See
   `../knowly/specify/memory/sdd-methodology.md` for why this step
   exists and what it catches that per-task TDAD structurally cannot.

No implementation should start without an approved SPEC for the feature in
question. Behavior changes always update the SPEC first.

**Cross-repo SPEC placement (2026-07-25): a feature's SPEC/PLAN/TASKS
live in the repo that owns the behavior they describe — frontend
behavior's SPEC lives here (`knowly-app/specify/features/<name>/`),
backend behavior's SPEC lives in the backend repo
(`knowly/specify/features/<name>/`). This applies regardless of which
repo an AI session happens to be invoked from, and regardless of which
repo turns out to be more convenient to write in.** A feature that
spans both (e.g. a new screen backed by a new endpoint) gets **two**
separate SPECs, one per repo, each covering only its own side, cross-
referencing each other's API contract rather than one shared SPEC
duplicated or misplaced in one repo. If you find a SPEC describing the
other repo's behavior sitting in this one (or vice versa), that's a
mistake to fix — relocate it to the repo it actually belongs to (moving
file contents, not just adding a pointer) rather than leaving it
as-is or duplicating it in both places. Any AI assistant
working in either repo, on discovering a misplaced SPEC, should treat
this paragraph as the instruction to correct it, not just note it.

**"Approved" applies to changing an existing SPEC's scope, not just
writing a brand-new one.** Never silently expand a SPEC's scope, add a
requirement it didn't have, or — especially — remove/reverse something
listed under its "Out of scope" section, and then proceed to implement
it in the same breath. This already happened once, on the backend repo
(see `knowly/DECISIONS.md`'s "Decision-making authority" section for the
full incident and why it matters): a SPEC explicitly said a capability
was "not addressed here," and an AI assistant edited that line out and
implemented it anyway, without ever pausing to ask. The code was fine;
the process wasn't. If a task reveals that the SPEC needs to grow, stop
and propose the change — don't treat "the SPEC didn't cover this" as
permission to decide the scope yourself. See `knowly/DECISIONS.md` for
the full framework on what an AI can decide autonomously versus what
always requires asking first.

## Mandatory EARS/GEARS syntax for requirements

Every requirement in SPEC.md must follow one of the patterns below (see
`specify/templates/spec-template.md` for full examples):

- **Ubiquitous**: "The `<system>` shall `<action/property>`."
- **Event-Driven**: "When `<trigger>`, the `<system>` shall `<action>`."
- **State-Driven**: "While `<state>`, the `<system>` shall `<action>`."
- **Optional Feature**: "Where `<feature/config>`, the `<system>` shall
  `<action>`."
- **Unwanted Behavior**: "If `<error condition>`, then the `<system>` shall
  `<action>`."
- **Complex**: combination of state + trigger + condition.

## Test-Driven Agentic Development (TDAD)

For every implementation task:

1. Read the corresponding requirement in SPEC.md.
2. Write/generate the test (component/service) that validates the
   requirement before implementation (Red state).
3. Implement the minimum code for the test to pass (Green state).
4. Run `npm test` and confirm it passes before marking the task done.

## Commits and branches

- **Commit as you go — do not leave finished work uncommitted.** Once a
  task (or a small, coherent group of related tasks) reaches Green and
  passes `npm run format:check && npm test && npm run build`, commit it
  before moving to the next task. Uncommitted work is invisible to Git
  history, to `PROJECT_STATUS.md`'s "Next up" section, and to whoever
  (human or AI) opens this repo next — it doesn't matter how correct the
  code is if it only exists as an uncommitted working-tree diff that the
  next session might not even notice, or might discard by accident. This
  holds regardless of whether the user explicitly asked for a commit in
  that message — finishing a task includes committing it, the same way
  it includes running the tests.
- Commit messages follow [Conventional Commits](https://www.conventionalcommits.org/)
  (`feat:`, `fix:`, `refactor:`, `test:`, `chore:`, `docs:`).
- Branches named as `<type>/<short-description>` (e.g. `feat/tags-list`,
  `fix/proxy-config`).
- One feature = one SPEC = ideally one branch/PR, to keep traceability
  between `specify/features/<name>/` and Git history. Within that, commit
  per atomic task/checkpoint rather than one giant commit at the end —
  smaller commits are what make it possible to tell, from history alone,
  which task introduced which change.
