# Agent ecosystem — knowly (monorepo)

> Read this before adding a new agent or skill. This maps every specialist
> subagent (`.claude/agents/*.md`) and every deterministic skill
> (`.claude/skills/*/SKILL.md`) to the actual stack in this monorepo —
> **not** a generic template. If a piece of advice below doesn't match
> what's actually in `knowly-api/pom.xml`/`knowly-app/package.json`, the
> code is the source of truth, fix this doc.

## Stack this ecosystem is built for (not the generic default)

- Backend (`knowly-api/`): Java 25, Spring Boot, Maven, PostgreSQL +
  pgvector, Flyway migrations, Hibernate Envers, Testcontainers,
  RabbitMQ, Redis, JTE templates, GitHub Actions CI, Docker image → GHCR.
  **No Kubernetes** — deploy target is a plain Docker image.
- Frontend (`knowly-app/`): Angular (standalone components, signals,
  zoneless), Tailwind CSS, Transloco (i18n), Vitest. **Not React/Next.js.**
- Process: Spec-Driven Development (SDD) — SPEC → PLAN → TASKS →
  Implement (TDAD) → Analyze, see `specify/memory/constitution.md` (this
  monorepo's stack-specific application of SDD) and
  `specify/memory/sdd-methodology.md` (the authoritative deep-dive on
  SDD methodology itself — where the two diverge, `constitution.md` is
  what gets corrected). Every agent below operates *inside* that
  process, not instead of it — none of them may skip SPEC approval,
  expand an approved SPEC's scope, or relax a Tier 3 rule from
  `DECISIONS.md` on their own authority.
- Subproject rule: a feature's SPEC/PLAN/TASKS live in the subproject
  that owns the behavior it describes — `knowly-api/specify/features/<name>/`
  for backend behavior, `knowly-app/specify/features/<name>/` for
  frontend behavior (see `constitution.md`, "Feature SPEC placement").
  Cross-cutting features still get two SPECs, one per subproject.

## Orchestration flow

```
[PO / Product Owner]
        |  writes SPEC.md (EARS/GEARS), gets it approved
        v
[Software Architect]  ------------------->  [Data Architect / DBA]
   writes PLAN.md (API contracts,                writes schema/migration
   package structure, resilience,                 plan, indexing, tx boundaries
   OR routing/state/component plan)               (knowly-api only)
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

## Agents and skills — one copy each, root `.claude/`

Now that both subprojects live in one repo, there is no cross-repo
duplication to track: each agent/skill exists exactly once at the root
`.claude/agents/`/`.claude/skills/`. Agents whose guidance differs by
subproject (`software-architect.md`, `appsec.md`) contain clearly
labeled backend/frontend sections in the same file rather than two
separate files.

| Agent | Scope | Primary skill |
|---|---|---|
| `po-product-owner.md` | both subprojects | `user-story-ears-writer` |
| `software-architect.md` | both (backend + frontend sections) | `adr-writer` |
| `data-architect-dba.md` | `knowly-api` only (Postgres/Flyway is backend-only) | `db-migration-validator` |
| `design-system-ui-ux.md` | `knowly-app` only (no design system on the backend) | `design-token-audit` |
| `frontend-engineer.md` | `knowly-app` only | `angular-component-builder` |
| `backend-engineer.md` | `knowly-api` only | `spring-endpoint-scaffold` |
| `qa-test-automation.md` | both (dual test pyramid) | `tdad-red-green-cycle` |
| `appsec.md` | both (backend + frontend sections) | `owasp-sanitization-check` |
| `devops-sre.md` | both (shared CI/observability) | `ci-pipeline-guard` |

If you ever find yourself about to create a second copy of an agent or
skill inside `knowly-api/.claude/` or `knowly-app/.claude/`, stop — that
directory split no longer exists. Everything lives once, at the
monorepo root.

## How to invoke

- As named subagents via the `Agent` tool (`subagent_type: "<file-stem>"`)
  once Claude Code loads `.claude/agents/`.
- Skills are invoked directly by name (`Skill` tool) or auto-triggered by
  their `description` matching the task at hand — see each
  `SKILL.md`'s frontmatter.

## Orchestrator ("Delivery Lead" role)

There's no separate invokable subagent for this — it's the main Claude
Code session itself, acting as the one thread that stays with the user
across an entire SDD cycle and drives the flow above one phase at a time
(PO → Architect/DBA → AppSec → TASKS → Engineer(s) → QA → AppSec → DevOps).
Concretely, this means:

- **Default mode: sequential handoff, not a group chat.** Call one
  specialist agent per phase, read its output, decide the next step, call
  the next specialist. This is by far the common case — most decisions
  (a PLAN's technical shape, a TASKS breakdown, an implementation
  approach) belong to exactly one role and don't need anyone else's input.
- **Only convene a multi-agent "roundtable"** (several specialists
  invoked to weigh in on the *same* open question, in parallel) **when a
  decision is genuinely mutual** — it doesn't cleanly belong to one role,
  or two roles' constraints conflict (e.g. AppSec wants a stricter
  control that Architect says breaks an existing contract; PO's priority
  call depends on both feasibility and security cost). This spends
  meaningfully more tokens than a sequential handoff, so don't reach for
  it out of caution — reach for it only when a single agent's answer
  would just be a guess about another role's constraints.
- **Tell each agent its own lane when convening a roundtable.** State
  plainly in each prompt which role it's playing and what it should (and
  should not) decide — e.g. "you are Architect here: judge feasibility
  and technical tradeoffs, not business priority; PO is deciding
  priority separately." Without this, agents drift into re-deciding
  things outside their scope and the roundtable's answers stop being
  usable as independent input.
- **Tier 3 items from `DECISIONS.md` still always stop and ask the
  human** — no roundtable, however convened, substitutes for that. A
  roundtable resolves disagreement *between agents*; it never grants
  itself authority the process reserves for the human.
- **Never re-run or block on a verify/build/test command a delegated
  agent is already running as part of its own task.** When Backend
  Engineer/Frontend Engineer/etc. run `./mvnw verify`,
  `npm test`/`ng build`, or similar as part of TDAD, that agent owns
  waiting for and reporting that result — the orchestrator does not
  also run the same command in the main thread and sit blocked on it.
  This happened for real (2026-07-28/29): the orchestrator ran
  `./mvnw verify` itself while a background `backend-engineer` agent
  was already running its own, and got stuck waiting on a duplicate,
  pointless run. If you need to check whether a delegated agent's long
  task is still alive, use a lightweight liveness check (process list,
  file mtime on its output) at a reasonable poll interval — never a
  second full verify run, and never continuous polling.

## Maintenance rule

Whenever the stack changes (a new major framework version, a new
datastore, a CI provider swap), update this file and the affected
agent/skill files in the same commit — a stale agent definition
actively misleads whoever (human or AI) invokes it next, the same
principle `DECISIONS.md` already applies to architectural decisions.
