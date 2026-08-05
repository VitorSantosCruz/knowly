# SPEC — observability-stack

> The what and the why. No technical implementation details.

## Context and motivation

`knowly-api` needs local observability (metrics, logs, traces) so an
engineer can diagnose a real request/error without attaching a debugger.
Most of the plumbing already existed before this feature was scoped
(discovered, not built from scratch — see PLAN.md's "What already
existed" section): a `grafana-lgtm` (Grafana + Loki + Tempo +
Prometheus, all-in-one) service in `compose.yaml`, and
`spring-boot-starter-opentelemetry` + `micrometer-registry-otlp` on the
classpath, already pushing real metrics and traces for service `knowly`
into that stack. What was missing, and is this feature's actual scope,
was: application **logs** never reached Loki (no log-export bridge
existed), and the pre-existing `traceId` correlation-id filter generated
a random id disconnected from the real OTel trace, breaking Grafana's
log→trace correlation.

## User stories

- As a backend engineer running `knowly-api` locally, I want my
  application's metrics, traces, and logs all visible in one Grafana
  instance so I can correlate a slow/failed request across all three
  signals without extra tooling.
- As a backend engineer, I want a log line's `traceId` to be the *real*
  OTel trace id so Grafana's Loki→Tempo "trace_id" link-out actually
  resolves to a real trace.

## Requirements (EARS/GEARS)

- **REQ-1 [Ubiquitous]** The `knowly-api` application shall export
  request metrics (rate, latency, error count) via OTLP to the local
  `grafana-lgtm` stack.
- **REQ-2 [Ubiquitous]** The `knowly-api` application shall export
  distributed traces via OTLP to the local `grafana-lgtm` stack.
- **REQ-3 [Event-Driven]** When the application logs a line, the
  `knowly-api` application shall also export that log record via OTLP
  to the local `grafana-lgtm` stack's Loki.
- **REQ-4 [Event-Driven]** When a request is being processed and a real
  OTel span is active, the correlation-id filter shall use that span's
  real trace id/span id for the `traceId` MDC key and `traceparent`
  response header, instead of a locally-generated random id.
- **REQ-5 [Unwanted Behavior]** If no real OTel span is active for a
  request (e.g. tracing sampled out), then the correlation-id filter
  shall fall back to a locally-generated random id, so every response
  still carries a correlatable `traceparent` header.
- **REQ-6 [Ubiquitous]** Grafana shall be provisioned (via
  `grafana-lgtm`'s own built-in provisioning, not manual clicking) with
  Prometheus/Loki/Tempo datasources already wired and cross-linked
  (trace id derived field on Loki, exemplars on Prometheus).

## Non-functional requirements

- Security: local-dev only. Grafana/Prometheus/Loki/Tempo ports stay
  bound to `127.0.0.1` (already true of `grafana-lgtm` in
  `compose.yaml`); no auth hardening, no TLS, no retention tuning — not
  a production-ready stack, and must not be mistaken for one.
- Observability: this feature *is* the observability layer; its own
  correctness is verified by checking real data lands in Prometheus/
  Loki/Tempo, not just that containers start.

## Acceptance criteria

- [x] Prometheus (inside `grafana-lgtm`) shows real `knowly` service
      metrics (was already true before this feature; reconfirmed).
- [x] Tempo (inside `grafana-lgtm`) shows real `knowly` service traces
      (was already true before this feature; reconfirmed).
- [x] Loki (inside `grafana-lgtm`) receives real `knowly` application
      logs (new: `opentelemetry-logback-appender-1.0` +
      `OpenTelemetryLogbackConfig` + `logback-spring.xml`).
- [x] `CorrelationIdFilter`'s `traceId` MDC value matches a real Tempo
      trace id when one is active (new).
- [ ] A live, end-to-end demonstration of a fresh `./mvnw spring-boot:run`
      producing a Loki-visible log line — **blocked**, see PLAN.md's
      "Known blocker" section (pre-existing, unrelated AWS SDK/MinIO
      startup failure, not caused by this feature).

## Out of scope

- Building a second metrics/logs/traces stack — `grafana-lgtm` already
  covers Prometheus+Loki+Tempo+Grafana; introducing Promtail/a separate
  OTel Collector would duplicate it.
- Actor-user-id/tenant-id in MDC — referenced as a forward-compatible
  `captureMdcAttributes` entry in `logback-spring.xml`, but no code
  currently puts those keys into MDC anywhere in the codebase (a gap in
  the existing convention this feature did not introduce and does not
  close — flagged as a follow-up).
- Fixing the pre-existing AWS SDK 2.49.3/MinIO `headBucket` 403 that
  currently blocks any fresh `./mvnw spring-boot:run` — orthogonal bug,
  flagged in PROJECT_STATUS.md, not fixed here.
- Frontend telemetry/error-logging hook — noted as a follow-up in
  PROJECT_STATUS.md, not implemented.
