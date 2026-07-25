# CLAUDE.md — knowly-api (backend)

**First read the root [`CLAUDE.md`](../CLAUDE.md), [`../VISION.md`](../VISION.md),
[`../PROJECT_STATUS.md`](../PROJECT_STATUS.md), and
[`../DECISIONS.md`](../DECISIONS.md)** — all four live at the monorepo
root and cover this subproject. This file only adds backend-specific
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
   code (Green), then `./mvnw test`.
5. Before considering a task done, run `./mvnw spotless:apply` (formats)
   and `./mvnw verify` (formatting + tests) — that's what CI checks.
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

- Configuration in `src/main/resources/application.yaml` (YAML, not
  `.properties`).
- Testcontainers use pinned image tags, never `latest`.
- Dev `compose.yaml` follows hardening practices: no hardcoded secrets
  (`.env` outside Git), `cap_drop: ALL` + minimal capabilities, ports on
  `127.0.0.1`, healthchecks, resource limits, named volumes.
- Fix the root cause of linter warnings (e.g. resource leaks) instead of
  suppressing them.
- Java version in `.java-version`; formatting enforced via Spotless
  (`./mvnw spotless:apply` / `spotless:check`); CI in
  `../.github/workflows/ci-backend.yml` (root-level, path-filtered).
- APIs under `/api`; no open CORS — the frontend accesses it via proxy in
  dev (see the root constitution, "Integration between backend and
  frontend" section).
- Versioned pre-commit hook in `.githooks/` (runs Spotless); enable with
  `git config core.hooksPath .githooks`.
- Dependencies (Maven, `compose.yaml` images, GitHub Actions) are kept
  up to date automatically via Dependabot (`../.github/dependabot.yml`,
  root-level).

## Where SDD artifacts live

```
specify/
  templates/                  # SPEC/PLAN/TASKS templates
  features/<name>/            # SPEC.md, PLAN.md, TASKS.md per feature
```

Project-wide rules live at `../specify/memory/constitution.md` and
`../specify/memory/sdd-methodology.md` — this subproject has no
`specify/memory/` of its own anymore.
