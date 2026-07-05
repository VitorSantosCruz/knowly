# knowly (backend)

knowly's backend API — Spring Boot + Java 25.

This project follows **Spec-Driven Development (SDD)**. Before implementing
any feature, read
[`specify/memory/constitution.md`](specify/memory/constitution.md) and see
[`CLAUDE.md`](CLAUDE.md) for the workflow (SPEC → PLAN → TASKS).

## Prerequisites

- Java 25 (version pinned in [`.java-version`](.java-version))
- Docker + Docker Compose (for dev infrastructure)

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
3. Run the application:
   ```sh
   ./mvnw spring-boot:run
   ```

## Tests

```sh
./mvnw test      # tests
./mvnw verify    # tests + formatting (Spotless)
```

Integration tests use [Testcontainers](https://testcontainers.com/) and
require Docker to be available.

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

GitHub Actions (`.github/workflows/ci.yml`) runs `spotless:check`,
`./mvnw verify`, and a Docker image build on every push/PR to `main`.
Dependabot (`.github/dependabot.yml`) keeps Maven dependencies,
`compose.yaml`/`Dockerfile` images, and GitHub Actions up to date weekly.

On every push to `main` (not on PRs), a second job builds and pushes the
image to the **GitHub Container Registry**
(`ghcr.io/<owner>/knowly:latest` and `ghcr.io/<owner>/knowly:sha-<commit>`).
No extra account or secret is needed — it authenticates with the
automatically provided `GITHUB_TOKEN`. Published images show up under the
repository's "Packages" tab on GitHub.

## Spec-Driven Development structure

```
specify/
  memory/constitution.md   # project rules — read before coding
  templates/                # SPEC/PLAN/TASKS templates
  features/<name>/           # SPEC.md, PLAN.md, TASKS.md per feature
```

See `specify/features/tags-crud/` for a reference example (not
implemented) of the expected format.

## Other documents

- [CONTRIBUTING.md](CONTRIBUTING.md) — how to contribute
- [SECURITY.md](SECURITY.md) — how to report vulnerabilities
- [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md) — code of conduct
- [LICENSE](LICENSE) — project license
