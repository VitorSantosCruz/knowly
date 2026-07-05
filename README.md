# knowly-app (frontend)

knowly's frontend — Angular 22.

This project follows **Spec-Driven Development (SDD)**. Before implementing
any feature, read
[`specify/memory/constitution.md`](specify/memory/constitution.md) and see
[`CLAUDE.md`](CLAUDE.md) for the workflow (SPEC → PLAN → TASKS).

## Prerequisites

- Node.js (version pinned in [`.nvmrc`](.nvmrc)) — use `nvm use` if you have
  nvm installed.
- Backend [`knowly`](../knowly) running on `localhost:8080` (API calls in
  dev go through the proxy configured in `proxy.conf.json`).

## Running locally

```sh
npm install
npm start        # ng serve, with proxy to the backend at /api
```

Open `http://localhost:4200`.

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

GitHub Actions (`.github/workflows/ci.yml`) runs formatting, tests, build,
and a Docker image build on every push/PR to `main`. Dependabot
(`.github/dependabot.yml`) keeps npm dependencies, the `Dockerfile` image,
and GitHub Actions up to date weekly.

On every push to `main` (not on PRs), a second job builds and pushes the
image to the **GitHub Container Registry**
(`ghcr.io/<owner>/knowly-app:latest` and
`ghcr.io/<owner>/knowly-app:sha-<commit>`). No extra account or secret is
needed — it authenticates with the automatically provided `GITHUB_TOKEN`.
Published images show up under the repository's "Packages" tab on GitHub.

## Spec-Driven Development structure

```
specify/
  memory/constitution.md   # project rules — read before coding
  templates/                # SPEC/PLAN/TASKS templates
  features/<name>/           # SPEC.md, PLAN.md, TASKS.md per feature
```

See `specify/features/tags-list/` for a reference example (not
implemented) of the expected format.

## Other documents

- [CONTRIBUTING.md](CONTRIBUTING.md) — how to contribute
- [SECURITY.md](SECURITY.md) — how to report vulnerabilities
- [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md) — code of conduct
- [LICENSE](LICENSE) — project license
