# CLAUDE.md — knowly (monorepo)

This is a **monorepo**: `knowly-api/` (Spring Boot backend) and
`knowly-app/` (Angular frontend) are plain folders in one Git history,
not separate repositories or submodules.

**First, read [`VISION.md`](VISION.md), [`PROJECT_STATUS.md`](PROJECT_STATUS.md),
and [`DECISIONS.md`](DECISIONS.md)** — all three live once, here at the
root, and cover both subprojects. `VISION.md` explains what knowly is
*for* and why the architecture looks the way it does; `PROJECT_STATUS.md`
tracks what's already been built (in both subprojects), what's in
progress, and operational gotchas; `DECISIONS.md` explains the reasoning
behind specific architectural/code decisions **and, critically, which
kinds of decisions you can make on your own versus which always require
asking the user first** — read that section before doing anything that
isn't a straightforward continuation of an already-approved
SPEC/PLAN/TASKS. Together these let a new conversation (with any AI,
with or without prior context) pick up correctly without rediscovering
everything from scratch. **Keep all three updated as work lands — this
applies to any AI assistant working in this repo, not just Claude.**

**Whenever the user opens a conversation without specifying what to work
on — regardless of how that's phrased or in what language — treat it as
a request for direction**, not as an instruction to invent something.
Go straight to `PROJECT_STATUS.md`'s "Next up" section and follow its
protocol exactly. Do not try to pattern-match the message against any
fixed set of phrases — judge intent, not wording: if the user hasn't told
you which feature, bug, or concern to address, that's the signal,
independent of how they express it.

This project follows Spec-Driven Development (SDD). Always read
`specify/memory/constitution.md` (root-level, covers both subprojects)
before implementing anything — it contains the project's non-negotiable
rules (stack, conventions, security), split into backend and frontend
sections. `specify/memory/sdd-methodology.md`, in the same folder, is
the authoritative deep-dive on SDD methodology itself — where
`constitution.md`'s process mechanics diverge from it, that document is
what "correct" is measured against.

## Which subproject am I working in?

Pick the right one before writing any code — each has its own
`CLAUDE.md` with subproject-specific commands and conventions:

- Backend work (Java/Spring, `/api/**` endpoints, migrations, RabbitMQ,
  tenancy/RBAC): read [`knowly-api/CLAUDE.md`](knowly-api/CLAUDE.md).
- Frontend work (Angular, routing, components, UI): read
  [`knowly-app/CLAUDE.md`](knowly-app/CLAUDE.md).
- A feature spanning both: read both, and see `constitution.md`'s
  "Feature SPEC placement" section — it gets **two** SPECs, one per
  subproject, not one shared SPEC.

## Before coding

1. Do not implement from a vague request. If there is no SPEC for the
   feature at `<subproject>/specify/features/<name>/SPEC.md`, ask enough
   questions to write one (using
   `<subproject>/specify/templates/spec-template.md`) and get approval
   before moving on to the PLAN.
2. Every SPEC uses EARS/GEARS syntax (Ubiquitous, Event-Driven,
   State-Driven, Optional Feature, Unwanted Behavior, Complex) — see the
   root constitution for the exact syntax.
3. Once the SPEC is approved, generate PLAN.md and TASKS.md in the same
   feature folder.
4. Implement task by task following TDAD: test first (Red), then minimal
   code (Green), then the subproject's test command.
5. Before considering a task done, run the subproject's full verification
   (see its own `CLAUDE.md`) — that's what CI checks.
6. **Commit it.** A task isn't done at green tests — it's done once it's
   committed (Conventional Commits, see `constitution.md`'s "Commits and
   branches"). This is a standing, pre-authorized instruction for this
   repo: commit each completed task/checkpoint as you go, without
   needing the user to separately ask "commit that" every time — the
   same way you don't need to be asked to run the tests. Leaving green,
   verified work sitting uncommitted defeats the entire point of
   `PROJECT_STATUS.md`/Git history being the thing that survives between
   conversations.

## Agent/skill ecosystem

`.claude/agents/` and `.claude/skills/` at the repo root hold every
specialist subagent and deterministic skill for this project — one copy
each, covering both subprojects (agents whose guidance differs by stack,
like `software-architect`/`appsec`, contain clearly labeled backend and
frontend sections in the same file). See
[`.claude/AGENTS_ECOSYSTEM.md`](.claude/AGENTS_ECOSYSTEM.md) for the
full orchestration flow and which skill backs which agent.

## Where SDD artifacts live

```
specify/
  memory/constitution.md      # project rules (root, both subprojects)
  memory/sdd-methodology.md   # authoritative SDD methodology deep-dive

knowly-api/specify/
  templates/                  # SPEC/PLAN/TASKS templates (backend)
  features/<name>/            # SPEC.md, PLAN.md, TASKS.md per backend feature

knowly-app/specify/
  templates/                  # SPEC/PLAN/TASKS templates (frontend)
  features/<name>/            # SPEC.md, PLAN.md, TASKS.md per frontend feature
```
