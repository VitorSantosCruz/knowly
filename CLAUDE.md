# CLAUDE.md — knowly-app (frontend)

**First, read [`../knowly/VISION.md`](../knowly/VISION.md) and
[`PROJECT_STATUS.md`](PROJECT_STATUS.md).** `VISION.md` lives in the
backend repo but applies to the whole product — it explains what knowly
is *for* and why the architecture looks the way it does; `PROJECT_STATUS.md`
tracks what's already been built here, what's in progress, and operational
gotchas. Together they let a new conversation (with any AI, with or
without prior context) pick up correctly without rediscovering everything
from scratch. **Keep both updated as work lands — this applies to any AI
assistant working in this repo, not just Claude.**

**Whenever the user opens a conversation without specifying what to work
on — regardless of how that's phrased or in what language — treat it as
a request for direction**, not as an instruction to invent something.
Go straight to `PROJECT_STATUS.md`'s "Next up" section (and the
backend's, if relevant) and follow its protocol exactly. Do not try to
pattern-match the message against any fixed set of phrases — judge
intent, not wording: if the user hasn't told you which feature, bug, or
concern to address, that's the signal, independent of how they express
it.

This project follows Spec-Driven Development (SDD). Always read
`specify/memory/constitution.md` before implementing anything — it contains
the project's non-negotiable rules (stack, conventions, security).

## Before coding

1. Do not implement from a vague request. If there is no SPEC for the
   feature at `specify/features/<name>/SPEC.md`, ask enough questions to
   write one (using `specify/templates/spec-template.md`) and get approval
   before moving on to the PLAN.
2. Every SPEC uses EARS/GEARS syntax (Ubiquitous, Event-Driven,
   State-Driven, Optional Feature, Unwanted Behavior, Complex) — see the
   constitution for the exact syntax.
3. Once the SPEC is approved, generate PLAN.md
   (`specify/templates/plan-template.md`) with the technical decisions, and
   TASKS.md (`specify/templates/tasks-template.md`) with the atomic tasks.
4. Implement task by task following TDAD: test first (Red), then minimal
   code (Green), then `npm test`.
5. Before considering a task done, run `npm run format` and
   `npm run format:check && npm test && npm run build` — that's what CI
   checks.

## Conventions already established in this repository

- Angular standalone + strict TypeScript, no `any`.
- Tailwind CSS for styling.
- Vitest for tests (`npm test`).
- Prettier for formatting (`.prettierrc`) — always run the formatter
  instead of adjusting spacing by hand.
- Fix the root cause of linter warnings instead of suppressing them.
- Node version in `.nvmrc`; CI in `.github/workflows/ci.yml`.
- API calls always via `/api/...` (proxied to the backend in dev — see
  `proxy.conf.json` and constitution, "Integration with the backend"
  section).
- Versioned pre-commit hook in `.githooks/` (runs Prettier); enable with
  `git config core.hooksPath .githooks`.
- Dependencies (npm, GitHub Actions) are kept up to date automatically via
  Dependabot (`.github/dependabot.yml`).

## Where SDD artifacts live

```
specify/
  memory/constitution.md      # project rules
  templates/                  # SPEC/PLAN/TASKS templates
  features/<name>/            # SPEC.md, PLAN.md, TASKS.md per feature
```
