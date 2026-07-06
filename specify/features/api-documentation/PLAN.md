# PLAN — API documentation (OpenAPI/Swagger)

## Architectural decisions

- `org.springdoc:springdoc-openapi-starter-webmvc-ui` (new dependency) —
  generates OpenAPI 3 docs from existing Spring MVC annotations
  (`@PostMapping`, `@RequestBody`, DTOs), no per-endpoint annotation
  required for basic coverage.
- Disabled by default via `springdoc.api-docs.enabled` and
  `springdoc.swagger-ui.enabled`, both bound to a single
  `API_DOCS_ENABLED` env var (default `false`) — one switch, not two to
  keep in sync.
- No new Spring Security rule needed: `SecurityConfig`'s existing
  `.anyRequest().authenticated()` catch-all already covers
  `/v3/api-docs/**` and `/swagger-ui/**`, since neither is added to the
  `permitAll` list. Authentication-gating is "free" — the risk was only
  ever forgetting to disable the docs themselves.

## Configuration (application.yaml)

```yaml
springdoc:
  api-docs:
    enabled: ${API_DOCS_ENABLED:false}
  swagger-ui:
    enabled: ${API_DOCS_ENABLED:false}
```

## Package/file structure

```
pom.xml                          # + springdoc-openapi-starter-webmvc-ui
src/main/resources/application.yaml  # + springdoc.* config
.env.example                     # + API_DOCS_ENABLED
```

## Testing strategy

- `ApiDocsSecurityTest`: with default config (docs disabled), asserts
  `/v3/api-docs` and `/swagger-ui/index.html` are not served. With
  `API_DOCS_ENABLED=true` (via `@TestPropertySource`/context override) and
  no authentication, asserts 401. Confirms REQ-2/3/4 hold without relying
  on manually re-verifying every future endpoint's inclusion.
