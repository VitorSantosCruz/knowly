# PLAN — observability-stack

> The how. Translates the SPEC into concrete technical decisions.
> References SPEC.md.

## What already existed (discovered, not built here)

Before this feature was scoped, the repo already had, live and running:

- `compose.yaml`'s `grafana-lgtm` service (`grafana/otel-lgtm:0.29.2`),
  bundling Grafana + Prometheus + Loki + Tempo + an OTel Collector, ports
  `127.0.0.1:3000` (Grafana UI), `127.0.0.1:4317`/`4318` (OTLP gRPC/HTTP
  ingest) — already provisioned with Prometheus/Loki/Tempo datasources
  and cross-links out of the box (that's what the `otel-lgtm` image
  does).
- `pom.xml`: `spring-boot-starter-opentelemetry` (Spring Boot 4.1's own
  OTel starter — pulls `micrometer-tracing-bridge-otel`,
  `opentelemetry-sdk`, `opentelemetry-exporter-otlp`),
  `micrometer-registry-otlp`, `micrometer-registry-prometheus`.
- Verified live during this feature's work (before any code change):
  Prometheus (`service_name="knowly"`) and Tempo
  (`rootServiceName="knowly"`) both already had real data from the
  developer's own manual local testing — metrics and traces were
  already flowing, unannounced, via the OTel starter's default OTLP
  exporter (default endpoint `localhost:4317`, which happens to match
  `grafana-lgtm`'s exposed port).
- `CorrelationIdFilter` (MDC `traceId` + `traceparent` response header) —
  pre-existing, but its own javadoc was stale (claimed "no real tracing
  bridge on the classpath", no longer true once the OTel starter
  landed), and it generated a random id independent of the real OTel
  trace, so Grafana's Loki→Tempo trace-id link-out would never resolve.

None of this was documented anywhere in `PROJECT_STATUS.md`/
`DECISIONS.md` prior to this feature.

## What this feature actually adds

1. **Log export to Loki** (SPEC REQ-3). Spring Boot 4.1's own
   `spring-boot-starter-opentelemetry` has **no** auto-configured
   logging bridge (verified: no `Otlp*Logging*AutoConfiguration` class
   in `spring-boot-actuator-autoconfigure:4.1.0`'s
   `AutoConfiguration.imports`) — metrics/traces are auto-wired, logs
   are not. Chose the OTel Logback appender
   (`io.opentelemetry.instrumentation:opentelemetry-logback-appender-1.0`)
   over a Promtail/file-tailing setup: `knowly-api` isn't containerized
   in local dev (it runs via `./mvnw spring-boot:run` against the
   compose-only infra stack), so there's no container log stream for
   Promtail to tail, and this keeps every telemetry signal (metrics,
   traces, logs) on the same push-based OTLP path, sharing the same
   `OpenTelemetry` SDK instance/resource attributes Spring already
   builds.
   - New `logback-spring.xml`: keeps Spring Boot's default console
     appender, adds an `OTEL` appender.
   - New `OpenTelemetryLogbackConfig` (`config` package): a
     `ApplicationListener<ContextRefreshedEvent>` that calls
     `OpenTelemetryAppender.install(openTelemetry)` once the real
     `OpenTelemetry` bean exists (Logback itself initializes before the
     Spring context, so the appender can't be handed the real bean at
     Logback-config time).
2. **Real trace-id correlation** (SPEC REQ-4/REQ-5). `CorrelationIdFilter`
   now prefers `Span.current().getSpanContext()` when it's valid (a real
   OTel span is active), falling back to the pre-existing random-hex
   generation only when it isn't. Javadoc updated to stop claiming no
   tracing bridge exists.
3. **Dependency version pin.** The logback appender
   (`2.29.0-alpha`) transitively pulls
   `opentelemetry-instrumentation-api:2.29.0` →
   `opentelemetry-api-incubator:1.63.0-alpha`, one minor ahead of the
   `opentelemetry-sdk-logs:1.62.0` Spring Boot's own dependency
   management already fixes. That mismatch is a real,
   reproduced-during-this-work `NoClassDefFoundError:
   io/opentelemetry/api/incubator/common/ExtendedAttributeKey` on the
   very first log line at runtime (caught by
   `KnowlyApplicationTests#contextLoads`, not by compilation — the
   class exists at compile time, just not the same shape at runtime).
   Fixed by explicitly pinning `io.opentelemetry:opentelemetry-api-incubator`
   to `1.62.0-alpha` in `pom.xml`, matching the SDK version.

## Known blocker (pre-existing, orthogonal, NOT fixed by this feature)

A fresh `./mvnw spring-boot:run` currently fails at startup —
`ArticleStorageService#ensureBucketExists`'s `s3Client.headBucket(...)`
call gets a `403 Forbidden` from the local MinIO container, even though
`mc stat`/`mc ls` against the same MinIO container with the same
credentials succeed. Bisected (via `git log -p -- pom.xml`) to
`06d1fac` — a Dependabot bump of `software.amazon.awssdk:bom` from
`2.44.9` to `2.49.3` (merged 2026-07-26), a version range where AWS SDK
v2 changed default request/response checksum behavior in ways known to
break against older/self-hosted S3-compatible stores (this repo's pinned
`minio/minio:RELEASE.2025-04-08T15-41-24Z`). Setting
`AWS_REQUEST_CHECKSUM_CALCULATION=when_required`/
`AWS_RESPONSE_CHECKSUM_VALIDATION=when_required` (the commonly-cited
workaround for that class of bug) did **not** resolve it here, so the
actual root cause needs dedicated triage — out of scope for this
feature (unrelated dependency, unrelated subsystem). Flagged in
`PROJECT_STATUS.md`'s known-issues section; this blocked a live
fresh-boot demonstration of REQ-3 (Loki log delivery) during this
feature's own work — verified instead via `KnowlyApplicationTests`
(full Spring context, including the new `OpenTelemetryLogbackConfig`
bean, loads and runs cleanly with the version-pin fix in place).

## Dependencies

- `io.opentelemetry.instrumentation:opentelemetry-logback-appender-1.0:2.29.0-alpha`
  (compile scope — main code references
  `io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender`
  directly).
- `io.opentelemetry:opentelemetry-api-incubator:1.62.0-alpha` (explicit
  pin, see above).

## Package/file structure

- `knowly-api/pom.xml` — 2 new dependencies (see above).
- `knowly-api/src/main/resources/logback-spring.xml` — new.
- `knowly-api/src/main/java/br/com/conectabyte/knowly/config/OpenTelemetryLogbackConfig.java` — new.
- `knowly-api/src/main/java/br/com/conectabyte/knowly/config/CorrelationIdFilter.java` — modified.

## Testing strategy

- No new unit test class: this is infra/config wiring, not business
  logic (per `CLAUDE.md`'s TDAD guidance, "for pure YAML/compose config
  there's no 'test' in the traditional sense" — the same applies to this
  Logback/OTel config wiring).
- `KnowlyApplicationTests#contextLoads` (pre-existing) is the
  regression guard: it caught the real version-mismatch
  `NoClassDefFoundError` above and now passes clean with the fix.
- Manual/live verification performed during implementation: real
  `knowly` metrics confirmed in Prometheus (via Grafana's datasource
  proxy), real `knowly` traces confirmed in Tempo (same), Loki confirmed
  empty before this change and the mechanism proven not to crash after
  it — full live confirmation blocked by the unrelated MinIO/AWS-SDK
  startup bug above.
