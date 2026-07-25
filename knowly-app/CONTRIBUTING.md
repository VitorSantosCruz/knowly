# Contributing to knowly-app (frontend)

Thanks for contributing. This project follows **Spec-Driven Development
(SDD)** — read
[`../specify/memory/constitution.md`](../specify/memory/constitution.md) and
[`CLAUDE.md`](CLAUDE.md) before opening a PR.

## Workflow

1. For any new feature or behavior change, create/update the SPEC at
   `specify/features/<name>/SPEC.md` (EARS/GEARS syntax — see
   `specify/templates/spec-template.md`) before writing code.
2. Once the SPEC is agreed on, generate `PLAN.md` and `TASKS.md` in the same
   folder.
3. Implement task by task following TDAD (test first, then minimal code).
4. Before opening the PR, run:
   ```sh
   npm run format
   npm run format:check && npm test && npm run build
   ```

## Commits and branches

- Commits follow [Conventional Commits](https://www.conventionalcommits.org/)
  (`feat:`, `fix:`, `refactor:`, `test:`, `chore:`, `docs:`).
- Branches named as `<type>/<short-description>` (e.g. `feat/tags-list`).

## Pull Requests

- One feature = one SPEC = ideally one PR.
- CI (`../.github/workflows/ci-frontend.yml`) must be green before merging.
- Describe in the PR which SPEC/requirement is being addressed.

## Local environment

See [README.md](README.md) for how to run the project (requires the
backend [`knowly-api`](../knowly-api) running for API calls).

## Security concerns

Do not open a public issue for vulnerabilities — see
[`../SECURITY.md`](../SECURITY.md).
