# Agent ecosystem — knowly / knowly-app

> Read this before adding a new agent or skill. This maps every specialist
> subagent (`.claude/agents/*.md`) and every deterministic skill
> (`.claude/skills/*/SKILL.md`) to the actual stack in this repo pair —
> **not** a generic template. If a piece of advice below doesn't match
> what's actually in `pom.xml`/`package.json`, the code is the source of
> truth, fix this doc.

## Stack this ecosystem is built for (not the generic default)

- Backend (`knowly`): Java 25, Spring Boot, Maven, PostgreSQL + pgvector,
  Flyway migrations, Hibernate Envers, Testcontainers, RabbitMQ, Redis,
  JTE templates, GitHub Actions CI, Docker image → GHCR. **No
  Kubernetes** — deploy target is a plain Docker image.
- Frontend (`knowly-app`): Angular (standalone components, signals,
  zoneless), Tailwind CSS, Transloco (i18n), Vitest. **Not React/Next.js.**
- Process: Spec-Driven Development (SDD) — SPEC → PLAN → TASKS → TDAD,
  see `specify/memory/constitution.md` in both repos, and
  `specify/memory/sdd-methodology.md` (backend repo) for the deeper
  reasoning behind every rule there. Every agent below operates *inside*
  that process, not instead of it — none of them may skip SPEC approval,
  expand an approved SPEC's scope, or relax a Tier 3 rule from
  `DECISIONS.md` on their own authority.
- Cross-repo rule: a feature's SPEC/PLAN/TASKS live in the repo that owns
  the behavior it describes (see both `constitution.md` files, "Cross-repo
  SPEC placement").

## Orchestration flow

```
[PO / Product Owner]
        |  writes SPEC.md (EARS/GEARS), gets it approved
        v
[Software Architect]  ------------------->  [Data Architect / DBA]
   writes PLAN.md (API contracts,                writes schema/migration
   package structure, resilience)                 plan, indexing, tx boundaries
        |                                          |
        +--------------------+---------------------+
                             v
                  [AppSec] reviews the PLAN
                  (authZ model, PII, secrets,
                   new attack surface) BEFORE
                  any TASKS.md is written
                             |
                             v
                       writes TASKS.md
                             |
              +--------------+---------------+
              v                               v
     [Backend Engineer]              [Frontend Engineer]
     implements task-by-task,        implements task-by-task,
     TDAD (Red -> Green),            consumes the PLAN's API
     consults Design System           contract, consults
     agent only for shared            [Design System & UI/UX]
     tokens/contracts                 for components/tokens
              |                               |
              +--------------+----------------+
                             v
                   [QA & Test Automation]
              expands coverage beyond the task's
              own Red/Green test, regression pass
                             |
                             v
                     [AppSec] final pass
              (SAST/SCA results, dependency check,
               secrets scan) before merge
                             |
                             v
                  [DevOps / SRE & Observability]
              CI green, image builds, traces/metrics/
                logs wired, deploy
```

Every arrow is a **handoff point where the next agent can bounce work
back** — e.g. AppSec can reject a PLAN before TASKS.md exists, QA can
reopen a task if its test only covers the happy path. This is not a
waterfall; it's SDD's Red/Green loop with named reviewers at each gate.

## Agents and skills — which repo each actually lives in

Per this project's cross-repo placement rule (same reasoning as
SPEC/PLAN/TASKS placement): an agent/skill that's specific to one
repo's stack lives *only* there; one that genuinely applies to both is
**duplicated** (identical content, path references adjusted) into both
repos' `.claude/` — never shared by reference across repos, and never
pasted into the wrong repo "because it was easy to copy."

| Agent | Lives in | Primary skill |
|---|---|---|
| `po-product-owner.md` | **both** (duplicated) | `user-story-ears-writer` (both) |
| `software-architect.md` | **both** (duplicated, repo-specific architecture notes) | `adr-writer` (both) |
| `data-architect-dba.md` | `knowly` only (Postgres/Flyway is backend-only) | `db-migration-validator` (`knowly` only) |
| `design-system-ui-ux.md` | `knowly-app` only (no design system on the backend) | `design-token-audit` (`knowly-app` only) |
| `frontend-engineer.md` | `knowly-app` only | `angular-component-builder` (`knowly-app` only) |
| `backend-engineer.md` | `knowly` only | `spring-endpoint-scaffold` (`knowly` only) |
| `qa-test-automation.md` | **both** (duplicated, dual test pyramid) | `tdad-red-green-cycle` (both) |
| `appsec.md` | **both** (duplicated, repo-specific security surface) | `owasp-sanitization-check` (both) |
| `devops-sre.md` | **both** (duplicated, shared CI/observability) | `ci-pipeline-guard` (both) |

If you ever find a frontend-specific agent/skill's file sitting in
`knowly/.claude/` (or vice versa), that's a placement bug — move it,
don't just note it (this happened once already during this ecosystem's
initial setup and was corrected the same session).

## How to invoke

- As named subagents via the `Agent` tool (`subagent_type: "<file-stem>"`)
  once Claude Code loads `.claude/agents/`.
- Skills are invoked directly by name (`Skill` tool) or auto-triggered by
  their `description` matching the task at hand — see each
  `SKILL.md`'s frontmatter.

## Maintenance rule

Whenever the stack changes (a new major framework version, a new
datastore, a CI provider swap), update this file and the affected
agent/skill files in the same commit — a stale agent definition
actively misleads whoever (human or AI) invokes it next, the same
principle `DECISIONS.md` already applies to architectural decisions.
