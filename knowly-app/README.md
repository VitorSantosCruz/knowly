# knowly-app (frontend)

knowly's frontend — Angular 22. Part of the [`knowly`](..) monorepo.

This project follows **Spec-Driven Development (SDD)**. Before implementing
any feature, read
[`../specify/memory/constitution.md`](../specify/memory/constitution.md) and see
[`CLAUDE.md`](CLAUDE.md) for the workflow (SPEC → PLAN → TASKS).

## Prerequisites

- Node.js (version pinned in [`.nvmrc`](.nvmrc)) — use `nvm use` if you have
  nvm installed.
- Backend [`knowly-api`](../knowly-api) running on `localhost:8080` (API
  calls in dev go through the proxy configured in `proxy.conf.json`).

## Running locally

```sh
npm install
cp public/config.example.json public/config.json   # fill in real values
npm start        # ng serve, with proxy to the backend at /api
```

Open `http://localhost:4200`.

## Runtime configuration

Per-environment public values (currently just the Cloudflare Turnstile site
key — public by design, unlike the backend's secret key) are **not** baked
into the JS bundle at build time. Instead, the app fetches `/config.json` at
startup:

- **Locally**: copy `public/config.example.json` to `public/config.json`
  (gitignored) and fill in real values.
- **Docker**: `public/config.json` is generated at container start from
  `public/config.template.json` via `envsubst`, reading environment
  variables passed to the container (`docker run -e TURNSTILE_SITE_KEY=...`)
  — see `docker-entrypoint.sh`. This means the same built image can be
  promoted across environments with different keys, without a rebuild.

## Tests and build

```sh
npm test           # tests (Vitest)
npm run build       # production build
```

## Code formatting

The project uses [Prettier](https://prettier.io/). Format before
committing:

```sh
npm run format
```

To format automatically on every commit, enable this repository's Git hook
(once per clone):

```sh
git config core.hooksPath .githooks
```

## Application Docker image build

```sh
docker build -t knowly-app .
```

Multi-stage build (Node only in the build stage, runtime served by
`nginx:1.31-alpine` with a non-root user, on port 8080). Routing `/api` to
the backend in production is the responsibility of the deploy
environment's reverse proxy/gateway — see the comment in `nginx.conf`.

## CI

GitHub Actions (`../.github/workflows/ci-frontend.yml`, root-level,
path-filtered to `knowly-app/**`) runs formatting, tests, build, and a
Docker image build on every push/PR to `main` that touches this
subproject. Dependabot (`../.github/dependabot.yml`) keeps npm
dependencies, the `Dockerfile` image, and GitHub Actions up to date
weekly.

On every push to `main` (not on PRs), a second job builds and pushes the
image to the **GitHub Container Registry**
(`ghcr.io/<owner>/knowly-frontend:latest` and
`ghcr.io/<owner>/knowly-frontend:sha-<commit>`). No extra account or
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

See `specify/features/tags-list/` for a reference example (not
implemented) of the expected format.

## Other documents

- [CONTRIBUTING.md](CONTRIBUTING.md) — how to contribute
- [`../SECURITY.md`](../SECURITY.md) — how to report vulnerabilities
- [`../CODE_OF_CONDUCT.md`](../CODE_OF_CONDUCT.md) — code of conduct
- [LICENSE](LICENSE) — project license
