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

## Agents (`.claude/agents/`)

| File | Role | Primary skills it invokes |
|---|---|---|
| `po-product-owner.md` | Requirements, EARS/GEARS SPEC.md, acceptance criteria | `user-story-ears-writer` |
| `software-architect.md` | PLAN.md, ADRs, API contracts, resilience | `adr-writer` |
| `data-architect-dba.md` | Schema, migrations, indexing, tx boundaries | `db-migration-validator` |
| `design-system-ui-ux.md` | Tokens, components, accessibility, motion | `design-token-audit` |
| `frontend-engineer.md` | Angular implementation | `angular-component-builder` |
| `backend-engineer.md` | Spring Boot implementation | `spring-endpoint-scaffold` |
| `qa-test-automation.md` | Test pyramid, fixtures, regression | `tdad-red-green-cycle` |
| `appsec.md` | SAST/SCA, OWASP, authZ, PII/secrets | `owasp-sanitization-check` |
| `devops-sre.md` | CI/CD, Docker, observability | `ci-pipeline-guard` |

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
