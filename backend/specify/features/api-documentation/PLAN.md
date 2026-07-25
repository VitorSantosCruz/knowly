# PLAN — API documentation (OpenAPI/Swagger)

## Architectural decisions

- `org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.6` (new
  dependency, latest available on Maven Central at implementation time —
  no 3.x release exists yet) — generates OpenAPI 3 docs from existing
  Spring MVC annotations (`@PostMapping`, `@RequestBody`, DTOs), no
  per-endpoint annotation required for basic coverage. Verified working
  against Spring Boot 4.0.7/Spring Framework 7 via a real integration test.
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

- `ApiDocsSecurityTest`: two `@Nested` `@SpringBootTest` contexts.
  `WhenDisabledByDefault` logs a real user in via `/api/auth/login-code/verify`
  to get an authenticated `SESSION` cookie, then asserts both doc endpoints
  still 404 with that cookie attached — proving REQ-2 holds even for an
  authenticated session, not just unauthenticated ones. `WhenEnabled` (docs
  turned on via `@DynamicPropertySource`) asserts 401 without a session and
  200 with one obtained the same way. Together these confirm REQ-2/3/4 hold
  without relying on manually re-verifying every future endpoint's
  inclusion.
