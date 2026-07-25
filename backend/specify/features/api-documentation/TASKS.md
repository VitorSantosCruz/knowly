# TASKS — API documentation (OpenAPI/Swagger)

- [x] 1. Add `springdoc-openapi-starter-webmvc-ui` dependency to `pom.xml`.
- [x] 2. Add `springdoc.api-docs.enabled`/`springdoc.swagger-ui.enabled`
      (both bound to `API_DOCS_ENABLED`, default `false`) to
      `application.yaml`; add `API_DOCS_ENABLED` to `.env.example`.
- [x] 3. Write `ApiDocsSecurityTest` covering REQ-2/3/4 (Red), confirm it
      passes with the config from task 2 (Green) — no new production code
      expected beyond configuration, since authentication-gating already
      falls out of the existing `SecurityConfig`.
- [x] 4. Run `./mvnw verify` and confirm it's green.
