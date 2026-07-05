# Constitution — knowly (backend)

Non-negotiable rules for any human or AI agent implementing code in this
repository. If a plan or task conflicts with this document, this document
wins.

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
5. Implementation follows the tasks in order; each task should be small
   enough to be executed with high confidence in a single iteration.

No implementation should start without an approved SPEC for the feature in
question. Behavior changes always update the SPEC first.

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

- Commit messages follow [Conventional Commits](https://www.conventionalcommits.org/)
  (`feat:`, `fix:`, `refactor:`, `test:`, `chore:`, `docs:`).
- Branches named as `<type>/<short-description>` (e.g. `feat/tags-crud`,
  `fix/pgvector-volume`).
- One feature = one SPEC = ideally one branch/PR, to keep traceability
  between `specify/features/<name>/` and Git history.
