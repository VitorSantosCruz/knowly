# TASKS — observability-stack

> Atomic, sequential, verifiable tasks derived from PLAN.md.

- [x] 1. Discover and document the pre-existing observability plumbing
      (`grafana-lgtm` in `compose.yaml`, `spring-boot-starter-opentelemetry`
      already in `pom.xml`) instead of duplicating it — confirmed live via
      Grafana's datasource proxy that `knowly` metrics/traces already
      flowed to Prometheus/Tempo.
- [x] 2. Add `opentelemetry-logback-appender-1.0` + `logback-spring.xml`
      + `OpenTelemetryLogbackConfig` for log export to Loki (REQ-3).
- [x] 3. Fix the `opentelemetry-api-incubator` version mismatch this
      surfaced (`NoClassDefFoundError`), caught by
      `KnowlyApplicationTests#contextLoads` (Red), fixed by pinning the
      dependency (Green).
- [x] 4. Fix `CorrelationIdFilter` to use the real OTel span's trace/span
      id when one is active, falling back to the pre-existing random-hex
      generation otherwise (REQ-4/REQ-5).
- [x] 5. Run `./mvnw spotless:apply` then the affected test
      (`KnowlyApplicationTests`) — green.
- [x] 6. Update `PROJECT_STATUS.md` (new observability section) and
      `DECISIONS.md` (Tier 3 tooling choices already made by the owner,
      same style as the existing PrimeNG-removal entry).
- [x] 7. Commit.
- [ ] 8. Follow-up (not this feature): triage the pre-existing AWS
      SDK 2.49.3/MinIO `headBucket` 403 that currently blocks any fresh
      `./mvnw spring-boot:run` locally.
- [ ] 9. Follow-up (not this feature): put `actorUserId`/`tenantId` into
      MDC somewhere in the request pipeline — `logback-spring.xml`
      already lists them in `captureMdcAttributes` so they'll show up in
      Loki for free once that lands, but nothing currently populates
      those MDC keys.
