# CLAUDE.md — knowly-app (frontend)

**First read the root [`CLAUDE.md`](../CLAUDE.md), [`../VISION.md`](../VISION.md),
[`../PROJECT_STATUS.md`](../PROJECT_STATUS.md), and
[`../DECISIONS.md`](../DECISIONS.md)** — all four live at the monorepo
root and cover this subproject. This file only adds frontend-specific
commands and conventions on top of them.

This project follows Spec-Driven Development (SDD). Always read
`../specify/memory/constitution.md` before implementing anything — it
contains the project's non-negotiable rules (stack, conventions,
security), including this subproject's own section.

## Before coding

1. Do not implement from a vague request. If there is no SPEC for the
   feature at `specify/features/<name>/SPEC.md`, ask enough questions to
   write one (using `specify/templates/spec-template.md`) and get approval
   before moving on to the PLAN.
2. Every SPEC uses EARS/GEARS syntax (Ubiquitous, Event-Driven,
   State-Driven, Optional Feature, Unwanted Behavior, Complex) — see the
   root constitution for the exact syntax.
3. Once the SPEC is approved, generate PLAN.md
   (`specify/templates/plan-template.md`) with the technical decisions, and
   TASKS.md (`specify/templates/tasks-template.md`) with the atomic tasks.
4. Implement task by task following TDAD: test first (Red), then minimal
   code (Green), then `npm test`.
5. Before considering a task done, run `npm run format` and
   `npm run format:check && npm test && npm run build` — that's what CI
   checks.
6. **Commit it.** A task isn't done at green tests — it's done once it's
   committed (Conventional Commits, see the root `constitution.md`'s
   "Commits and branches"). This is a standing, pre-authorized instruction
   for this repo: commit each completed task/checkpoint as you go,
   without needing the user to separately ask "commit that" every time —
   the same way you don't need to be asked to run the tests. Leaving
   green, verified work sitting uncommitted defeats the entire point of
   `PROJECT_STATUS.md`/Git history being the thing that survives between
   conversations.

## Conventions already established in this subproject

- Angular standalone + strict TypeScript, no `any`.
- Tailwind CSS for styling.
- Vitest for tests (`npm test`).
- Prettier for formatting (`.prettierrc`) — always run the formatter
  instead of adjusting spacing by hand.
- Fix the root cause of linter warnings instead of suppressing them.
- Node version in `.nvmrc`; CI in `../.github/workflows/ci-frontend.yml`
  (root-level, path-filtered).
- API calls always via `/api/...` (proxied to the backend in dev — see
  `proxy.conf.json` and the root constitution, "Integration between
  backend and frontend" section).
- Versioned pre-commit hook in `.githooks/` (runs Prettier); enable with
  `git config core.hooksPath .githooks`.
- Dependencies (npm, GitHub Actions) are kept up to date automatically via
  Dependabot (`../.github/dependabot.yml`, root-level).

## Where SDD artifacts live

```
specify/
  templates/                  # SPEC/PLAN/TASKS templates
  features/<name>/            # SPEC.md, PLAN.md, TASKS.md per feature
```

Project-wide rules live at `../specify/memory/constitution.md` and
`../specify/memory/sdd-methodology.md` — this subproject has no
`specify/memory/` of its own anymore.
