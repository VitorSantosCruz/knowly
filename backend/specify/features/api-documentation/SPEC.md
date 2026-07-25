# SPEC — API documentation (OpenAPI/Swagger)

## Context and motivation

The project needs interactive, always-up-to-date documentation covering
every backend endpoint (starting with the authentication endpoints, and
automatically covering every endpoint added afterward). Publicly exposing
this documentation is a real security risk: it hands an attacker a
ready-made map of the API surface (paths, parameters, request/response
shapes) with no reconnaissance effort on their part. This SPEC makes the
documentation available without that exposure.

## User stories

- As a developer (internal), I want to browse and try out every endpoint
  from a single page, without hand-maintaining a separate document.
- As anyone outside the team, I should not be able to see the API's
  documentation at all, by default.

## Requirements (EARS/GEARS)

- **REQ-1 [Ubiquitous]** The system shall generate OpenAPI documentation
  covering every REST endpoint in the application, without requiring
  manual per-endpoint documentation upkeep.
- **REQ-2 [Ubiquitous]** The documentation endpoints (OpenAPI JSON and the
  Swagger UI) shall be disabled by default, regardless of environment.
- **REQ-3 [Optional Feature]** Where the `API_DOCS_ENABLED` environment
  variable is set to `true`, the system shall serve the documentation
  endpoints.
- **REQ-4 [Unwanted Behavior]** If a request for a documentation endpoint
  is not authenticated, then the system shall reject it the same way as
  any other protected endpoint (401) — enabling the documentation must
  never make it publicly readable, even by accident.

## Non-functional requirements

- Security: two independent layers — disabled by default (REQ-2/3), and
  authentication-gated even when enabled (REQ-4). Both must fail closed:
  an operator forgetting to set `API_DOCS_ENABLED` results in the docs
  being unavailable, not available.
- No new infrastructure: this only adds a library dependency
  (springdoc-openapi), no new service/container.

## Acceptance criteria

- [x] With no environment variable set, `/v3/api-docs` and `/swagger-ui/**`
      are not served (404 or disabled response) regardless of
      authentication.
- [x] With `API_DOCS_ENABLED=true` and no authenticated session, the
      documentation endpoints respond 401.
- [x] With `API_DOCS_ENABLED=true` and an authenticated session, the
      Swagger UI renders and lists every existing controller's endpoints.
      (springdoc auto-discovers every `@RestController` with no
      per-controller opt-in, so this holds architecturally rather than
      needing a per-controller test; `ApiDocsSecurityTest` verifies the
      200/401/404 gating.)

## Out of scope

- Publishing documentation to an external/public developer portal.
- Per-endpoint fine-grained authorization for who can view docs (any
  authenticated session is sufficient for now — this project has no
  roles yet, see the authentication SPEC's "Out of scope").
