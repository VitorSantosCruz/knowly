# Security Policy

## Reporting a vulnerability

If you find a security vulnerability in this project, **do not open a
public issue**. Report it directly to:

- **Email:** knowly-security@conectabyte.com.br

Please include, if possible:

- Description of the vulnerability and its potential impact.
- Steps to reproduce (PoC, if applicable).
- Affected version/commit.

You will receive an acknowledgement of receipt and be kept informed of the
progress of the fix.

## Scope

This project follows the practices documented in
[`specify/memory/constitution.md`](specify/memory/constitution.md),
including:

- Never expose secrets, keys, or API tokens in client-side code.
- API calls always go through a proxy (`/api/...`), no open CORS on the
  backend.
- Dependencies kept up to date via Dependabot
  (`.github/dependabot.yml`).

## Supported versions

Only the `main` branch receives security fixes. There is no support for
older versions/pinned tags at this time.
