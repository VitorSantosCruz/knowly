# Constitution — knowly (backend)

Non-negotiable rules for any human or AI agent implementing code in this
repository. If a plan or task conflicts with this document, this document
wins.

**Read `specify/memory/sdd-methodology.md` alongside this file** — it's
the deeper reasoning behind every rule below (why SDD prevents "vibe
coding," the Builder/Verifier pattern TDAD already implements, why
scope drift and over-specification are both failure modes, concrete
anti-patterns already observed in this project). It never overrides
this file; it explains it.

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
  "The system should handle errors gracefully" gives an AI agent nothing
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

- Java (version pinned in `pom.xml`) + Spring Boot. Do not introduce another
  web or persistence framework without updating this constitution first.
- Build via Maven Wrapper (`./mvnw`). Never assume a global Maven install.
- Application configuration in `application.yaml` (YAML), never
  `.properties`.
- Dev infrastructure containers live in `compose.yaml` at the repo root and
  must follow the security practices already established: no hardcoded
  secrets (use `${VAR:?...}` + `.env` outside Git), `cap_drop: ALL` with
  only the minimum required capabilities, ports published on `127.0.0.1`,
  real healthchecks, CPU/memory limits, and named volumes (not host bind
  mounts).
- Integration tests use Testcontainers (`TestcontainersConfiguration.java`).
  Test container images must use pinned tags (never `latest`).
- Java version pinned in `.java-version` (read by `actions/setup-java` in
  CI). Update this file together with `java.version` in `pom.xml`.
- Code formatting is mandatory and automated via Spotless
  (`spotless-maven-plugin`, `google-java-format` formatter). Run
  `./mvnw spotless:apply` before committing; `./mvnw spotless:check` runs in
  the `verify` phase and fails the build (and CI) if code isn't formatted.
- CI runs on GitHub Actions (`.github/workflows/ci.yml`): `spotless:check` +
  `./mvnw verify` on every push/PR to `main`.
- Actuator endpoints: only `/actuator/health` is public
  (`management.endpoints.web.exposure.include`). Any newly exposed Actuator
  endpoint must be evaluated for information leakage before enabling it.
- Multi-stage `Dockerfile` (build with JDK, runtime on
  `eclipse-temurin:*-jre-alpine`, non-root user, no secrets baked into the
  image). CI builds the image on every push/PR to catch packaging breakage
  early, and pushes it to the **GitHub Container Registry** on every push
  to `main` (tags: `latest` and `sha-<commit>`).
- Every `@SpringBootTest` must use `@ActiveProfiles("test")`, which loads
  `src/test/resources/application-test.yaml`. That file contains a dummy
  `spring.ai.openai.api-key` — it only satisfies client validation at
  context startup, it never makes a real call to OpenAI. The production key
  still comes from an environment variable (`.env`/secret), never
  committed.

## Observability and audit (non-negotiable)

This project must be fully auditable: for any user, whether they made 300
or 300,000 requests, it must be possible to reconstruct their complete
activity timeline — what they did, when, and across which requests and
sessions.

- **JPA Auditing** (`@CreatedBy`/`@CreatedDate`/`@LastModifiedBy`/`@LastModifiedDate`,
  via `@EnableJpaAuditing`) is mandatory on every entity that represents
  user-modifiable state.
- **Hibernate Envers** (`@Audited`) is mandatory on every entity holding
  security-sensitive or business-critical state (starting with `User` and
  anything related to authentication), so every historical revision is
  queryable.
- **Distributed tracing** via OpenTelemetry (already provisioned:
  `spring-boot-starter-opentelemetry` + the Grafana LGTM stack in
  `compose.yaml` — Tempo for traces, Loki for logs, Prometheus for
  metrics). No new infrastructure should be introduced for this; use what's
  already there.
- **Structured logs**: every log line must carry, via MDC/context
  propagation, the trace id, the authenticated user id (when there is one),
  and the tenant id (once multi-tenancy exists). This is what makes it
  possible to filter "everything user X did" in Loki regardless of how many
  separate requests/connections were involved.
- **What must log** (replaces any vague "log coverage" percentage, which
  isn't a measurable metric): every state-changing action (create, update,
  delete) and every authentication/authorization decision (success,
  failure, lockout, permission denial) must emit a structured log/audit
  event with the actor, the action, and the outcome. This is reviewable in
  code review today, and should eventually be enforced with an
  architecture test (e.g. ArchUnit) that flags service-layer methods with
  no corresponding log/audit call.
- **PII in logs**: never log a raw email (or other direct PII) — use
  `br.com.conectabyte.knowly.observability.PiiMasker#maskEmail`, which
  keeps a stable per-address fingerprint (so "everything user X did" is
  still filterable in Loki) without ever printing the address itself.
  This matters beyond the auth log lines it started with: it applies to
  every future feature that logs anything tied to a person, including
  conversation/chat history once that exists.

## Security conventions for authentication and abuse prevention

- Never lock out or throttle based solely on IP address. IP is a weak
  signal: shared/NAT'd networks (corporate, mobile carriers) cause
  legitimate users to suffer collateral damage, and attackers trivially
  rotate IPs to bypass it.
- Brute-force protection on a known account (wrong OTP/password) must be
  keyed by the **account identifier** (e.g. email), not by IP — this stops
  the attack regardless of how many source IPs are used.
- Enumeration protection (many different, mostly non-existent, identifiers
  being tried) cannot be solved by per-account counters, since each guess
  only happens once. Use a human-verification challenge (CAPTCHA — Cloudflare
  Turnstile, no new paid infrastructure) triggered by request volume/velocity
  instead of a hard block.
- Responses must never reveal whether an identifier (e.g. email) exists in
  the system. Timing, error messages, and lockout behavior must be
  indistinguishable between an existing and a non-existing account.
- One-time secrets (codes, single-use passwords) are always stored hashed,
  never in plaintext, and are single-use by construction (invalidated on
  first use or expiry, whichever comes first).

## Integration with the frontend (knowly-app)

- The backend exposes its APIs under the `/api` prefix (convention, not a
  wide-open CORS policy). In dev, `knowly-app` reaches the backend through
  the Angular proxy (`proxy.conf.json`), so CORS for `localhost:4200` is not
  needed — this avoids widening the attack surface with globally allowed
  origins.
- If direct (non-proxied) consumption is ever needed, any CORS
  configuration must be explicit per origin (never `*`) and documented
  here.

## Code rules

- Never expose secrets, keys, or credentials in source code or commits.
- Prefer composition over inheritance.
- No speculative abstractions: implement only what the spec/task asks for.
- No comments that describe the obvious; comment only the non-obvious
  (reason, invariant, workaround).
- Fix the root cause of linter/IDE warnings (e.g. resource leaks) instead of
  suppressing them.

## Workflow (Spec-Driven Development)

1. **constitution.md** (this file) — permanent rules of the project.
2. **SPEC.md** — the what and the why. Requirements in EARS/GEARS syntax,
   implementation-agnostic. Source of truth for expected behavior.
3. **PLAN.md** — the how. Concrete technical decisions (stack, schema, API
   contracts, package structure) derived from the SPEC.
4. **TASKS.md** — list of atomic, verifiable tasks derived from the PLAN.
5. **Implement** — tasks executed in order, test-first (TDAD), each small
   enough to be executed with high confidence in a single iteration.
6. **Analyze** — a mandatory closing gate before a *feature* (not just a
   task) is done: re-read constitution.md, the feature's SPEC.md,
   PLAN.md, and TASKS.md together and confirm they're still mutually
   consistent, then re-check every SPEC.md acceptance-criterion checkbox
   against the finished implementation one by one. See
   `specify/memory/sdd-methodology.md` for why this step exists and
   what it catches that per-task TDAD structurally cannot.

No implementation should start without an approved SPEC for the feature in
question. Behavior changes always update the SPEC first.

**Cross-repo SPEC placement (2026-07-25): a feature's SPEC/PLAN/TASKS
live in the repo that owns the behavior they describe — backend
behavior's SPEC lives here (`knowly/specify/features/<name>/`), frontend
behavior's SPEC lives in the frontend repo
(`knowly-app/specify/features/<name>/`). This applies regardless of
which repo an AI session happens to be invoked from, and regardless of
which repo turns out to be more convenient to write in.** A feature that
spans both (e.g. a new endpoint backing a new screen) gets **two**
separate SPECs, one per repo, each covering only its own side, cross-
referencing each other's API contract rather than one shared SPEC
duplicated or misplaced in one repo. If you find a SPEC describing the
other repo's behavior sitting in this one (or vice versa), that's a
mistake to fix — relocate it to the repo it actually belongs to (moving
file contents, not just adding a pointer) rather than leaving it as-is
or duplicating it in both places. Any AI assistant working in either
repo, on discovering a misplaced SPEC, should treat this paragraph as
the instruction to correct it, not just note it.

**"Approved" applies to changing an existing SPEC's scope, not just
writing a brand-new one.** Never silently expand a SPEC's scope, add a
requirement it didn't have, or — especially — remove/reverse something
listed under its "Out of scope" section, and then proceed to implement
it in the same breath. This already happened once (see `DECISIONS.md`'s
"Decision-making authority" section for the full incident and why it
matters): a SPEC explicitly said "Logout... not addressed here," and an
AI assistant edited that line out and implemented logout anyway, without
ever pausing to ask. The code was fine; the process wasn't. If a task
reveals that the SPEC needs to grow, stop and propose the change —
don't treat "the SPEC didn't cover this" as permission to decide the
scope yourself. See `DECISIONS.md` for the full framework on what an AI
can decide autonomously versus what always requires asking first.

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
2. Write/generate the test (unit or integration) that validates the
   requirement before implementation (Red state).
3. Implement the minimum code for the test to pass (Green state).
4. Run `./mvnw test` and confirm it passes before marking the task done.

## Commits and branches

- **Commit as you go — do not leave finished work uncommitted.** Once a
  task (or a small, coherent group of related tasks) reaches Green and
  passes `./mvnw spotless:apply && ./mvnw verify`, commit it before
  moving to the next task. Uncommitted work is invisible to Git history,
  to `PROJECT_STATUS.md`'s "Next up" section, and to whoever (human or
  AI) opens this repo next — it doesn't matter how correct the code is if
  it only exists as an uncommitted working-tree diff that the next
  session might not even notice, or might discard by accident. This
  holds regardless of whether the user explicitly asked for a commit in
  that message — finishing a task includes committing it, the same way
  it includes running the tests.
- Commit messages follow [Conventional Commits](https://www.conventionalcommits.org/)
  (`feat:`, `fix:`, `refactor:`, `test:`, `chore:`, `docs:`).
- Branches named as `<type>/<short-description>` (e.g. `feat/tags-crud`,
  `fix/pgvector-volume`).
- One feature = one SPEC = ideally one branch/PR, to keep traceability
  between `specify/features/<name>/` and Git history. Within that, commit
  per atomic task/checkpoint rather than one giant commit at the end —
  smaller commits are what make it possible to tell, from history alone,
  which task introduced which change.
