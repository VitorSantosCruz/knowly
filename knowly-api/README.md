# knowly-api (backend)

knowly's backend API — Spring Boot + Java 25. Part of the
[`knowly`](..) monorepo.

This project follows **Spec-Driven Development (SDD)**. Before implementing
any feature, read
[`../specify/memory/constitution.md`](../specify/memory/constitution.md) and see
[`CLAUDE.md`](CLAUDE.md) for the workflow (SPEC → PLAN → TASKS).

## Prerequisites

- Java 25 (version pinned in [`.java-version`](.java-version))
- Docker + Docker Compose (for dev infrastructure)
- `tesseract-ocr` (for image-article OCR, see `article-management`) —
  install once, system-wide:
  ```sh
  # Debian/Ubuntu
  sudo apt-get install -y tesseract-ocr tesseract-ocr-eng tesseract-ocr-por
  # macOS
  brew install tesseract tesseract-lang
  ```
  The runtime Docker image already installs it (`Dockerfile`); this is
  only needed to run `./mvnw test`/`verify` locally, so
  `ArticleExtractionListenerTest`'s OCR test can actually invoke the
  real `tesseract` binary. Without it, that one test fails (no
  skip/mock fallback, by design — see that test class); the rest of
  the suite is unaffected.

## Starting the development environment

1. Copy the environment variables file and adjust the passwords:
   ```sh
   cp .env.example .env
   ```
2. Start the infrastructure (Postgres, pgvector, RabbitMQ, Redis, Grafana
   LGTM):
   ```sh
   docker compose up -d
   ```
3. Export the same variables into your shell, then run the application.
   `docker compose` auto-loads `.env` for the *containers* it starts,
   but that does not export those variables into your own shell/Maven
   process — without this step, Spring resolves e.g.
   `${MINIO_ROOT_USER}` to the literal, unresolved placeholder string
   instead of failing loudly, which MinIO then rejects as an invalid
   access key (surfaces as a generic `403 Forbidden` from
   `ArticleStorageService#ensureBucketExists` at startup, not an
   obviously-credentials-related error):
   ```sh
   set -a; source .env; set +a
   ./mvnw spring-boot:run
   ```

## Tests

```sh
./mvnw test      # tests
./mvnw verify    # tests + formatting (Spotless)
```

Integration tests use [Testcontainers](https://testcontainers.com/) and
require Docker to be available.

All 100+ integration test classes import a single shared
`TestcontainersConfiguration`, which starts one Postgres/RabbitMQ/Redis/LGTM
stack per JVM (via `static` fields), so container startup only happens once
even though Spring may create several distinct test `ApplicationContext`s
across the suite (different `@MockBean`/`@ActiveProfiles`/`@TestPropertySource`
combinations produce different context-cache keys, but they all reuse the
same underlying containers).

For local development, you can additionally opt into **cross-run** container
reuse (containers surviving between separate `./mvnw test` invocations, not
just within one run) via Testcontainers' own reuse mechanism:

1. Add `testcontainers.reuse.enable=true` to `~/.testcontainers.properties`
   (this file lives outside the repo and is not committed).
2. Export `TESTCONTAINERS_REUSE_ENABLE=true` (or pass
   `-Dtestcontainers.reuse.enable=true` to Maven) when running tests.

This is a local-only speed optimization — CI runners are ephemeral, so
leave it disabled there (it is off by default unless both of the above are
set).

## Code formatting

The project uses [Spotless](https://github.com/diffplug/spotless) with
`google-java-format`. Format before committing:

```sh
./mvnw spotless:apply
```

To format automatically on every commit, enable this repository's Git hook
(once per clone):

```sh
git config core.hooksPath .githooks
```

## Application Docker image build

```sh
docker build -t knowly .
```

Multi-stage build (JDK only in the build stage, runtime on
`eclipse-temurin:25-jre-alpine` with a non-root user). Does not include the
infrastructure (Postgres, Redis...) — that stays in `compose.yaml`, for dev
only.

## CI

GitHub Actions (`../.github/workflows/ci-backend.yml`, root-level,
path-filtered to `knowly-api/**`) runs `spotless:check`, `./mvnw verify`,
and a Docker image build on every push/PR to `main` that touches this
subproject. Dependabot (`../.github/dependabot.yml`) keeps Maven
dependencies, `compose.yaml`/`Dockerfile` images, and GitHub Actions up
to date weekly. `../.github/workflows/codeql.yml` runs SAST on this
subproject independently, in parallel — it doesn't block the build.

On every push to `main` (not on PRs), a second job builds and pushes the
image to the **GitHub Container Registry**
(`ghcr.io/<owner>/knowly-backend:latest` and
`ghcr.io/<owner>/knowly-backend:sha-<commit>`). No extra account or
secret is needed — it authenticates with the automatically provided
`GITHUB_TOKEN`. Published images show up under the repository's
"Packages" tab on GitHub.

## Spec-Driven Development structure

```
specify/
  templates/                # SPEC/PLAN/TASKS templates
  features/<name>/           # SPEC.md, PLAN.md, TASKS.md per feature
```

Project-wide rules live at `../specify/memory/constitution.md` (root of
the monorepo, covers both subprojects).

See `specify/features/tags-crud/` for a reference example (not
implemented) of the expected format.

## Other documents

- [CONTRIBUTING.md](CONTRIBUTING.md) — how to contribute
- [`../SECURITY.md`](../SECURITY.md) — how to report vulnerabilities
- [`../CODE_OF_CONDUCT.md`](../CODE_OF_CONDUCT.md) — code of conduct
- [LICENSE](LICENSE) — project license
