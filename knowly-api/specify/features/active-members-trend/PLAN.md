# PLAN — active-members-trend (backend)

> The how. Translates the SPEC into concrete technical decisions.
> References SPEC.md.

## Architectural decisions

- **Snapshot mechanism: a daily `@Scheduled` job, not a write-time
  upsert on every `TenantMembership.active` toggle.** This is the first
  `@Scheduled` job in this codebase (`grep -rn "@Scheduled"
  knowly-api/src/main/java` returns nothing today), so it is a genuine
  new pattern, not a precedent-following one — recorded in
  `DECISIONS.md`. Two options were weighed:
  - *Write-time upsert*: hook a snapshot write into every place
    `TenantMembership.active` is set. Checked how narrow that surface
    actually is — only 5 call sites, all in
    `br.com.conectabyte.knowly.tenancy.NotificationService`
    (`acceptInvite`/`declineInvite`, 2 sites) and
    `br.com.conectabyte.knowly.tenancy.TenantService` (`removeMember`,
    member reactivation, 3 sites) — so "scattered call sites" was not
    the deciding factor against it.
  - *Scheduled job* (chosen): the SPEC's NFR explicitly says the
    snapshot mechanism "must not add a per-request cost to any existing
    tenant-membership mutation path" — a write-time upsert runs directly
    inside `addMember`/`removeMember`'s request path, which is exactly
    what that NFR is steering away from. More importantly, a write-time
    upsert only records a row on a day where *some* membership actually
    changed state — on the (common) day where no membership changes for
    a tenant, no row would be written, silently defeating REQ-2's "when
    a calendar UTC day completes... record exactly one snapshot row per
    tenant for that day" (which is unconditional, not conditional on
    activity). A scheduled job produces a correct row every day for
    every tenant, unconditionally, matching REQ-2 literally.
  - **No new Maven dependency required** — Spring's `@Scheduled`
    support ships in `spring-context`, already a transitive dependency
    of `spring-boot-starter`; only `@EnableScheduling` needs adding to
    `KnowlyApplication`. Not Tier 3.
- **Schedule**: `@Scheduled(cron = "0 5 0 * * *", zone = "UTC")` — runs
  once daily at 00:05 UTC, snapshotting the UTC calendar day that just
  completed (`LocalDate.now(clock).minusDays(1)`), computed via a single
  cross-tenant grouped native query (mirrors
  `TenantRepository.countTenantsByDay()`'s deliberate no-`tenant_id`-
  predicate shape for a systemwide job, not a per-request read) that
  counts `tenant_memberships` rows with `active = true`, grouped by
  `tenant_id`, and upserts one row per tenant via Postgres
  `INSERT ... ON CONFLICT (tenant_id, snapshot_date) DO UPDATE`. The
  `ON CONFLICT` upsert (not a `SELECT`-then-`INSERT`/`UPDATE` branch) is
  what satisfies REQ-3 ("if a snapshot is recorded more than once... at
  most one row exists per tenant per day") — a retried job run is
  naturally idempotent, no extra locking/dedup logic needed.
  **Accepted gap, consistent with the SPEC's own no-backfill stance**:
  if the app is down at 00:05 UTC, that day's snapshot is permanently
  skipped (no catch-up-on-startup or backfill job) — this mirrors the
  SPEC's already-accepted "no history before this feature's rollout"
  tradeoff, just applied to a single missed day instead of the whole
  pre-rollout history, so it does not need a fresh product decision.
- **New JPA entity `ActiveMemberSnapshot`** in
  `br.com.conectabyte.knowly.metrics`, tenant-owned, so it carries the
  same `@Filter(name = TenantFilter.NAME, ...)` every other tenant-owned
  entity carries (`TenantMembership` is the direct model) — even though
  the scheduled job's own writes and the timeseries read both use
  explicit native queries that don't traverse the Hibernate filter
  (matching `ArticleRepository`'s existing native-query convention of an
  explicit `tenant_id = :tenantId` predicate), the entity itself still
  gets the `@Filter` annotation so that any future JPQL/`findBy...`
  usage against it fails closed by default, per this codebase's
  standing tenancy convention — never a second, parallel scoping
  mechanism.
- **New `MetricsService.membersTimeseries(MetricsPeriod)`** mirrors
  `articlesTimeseries`/`conversationsTimeseries` exactly: resolves the
  active tenant via the existing `requireActiveTenant()`, reads
  `ActiveMemberSnapshotRepository`'s day-bucketed rows (a real stored
  count, not a `date_trunc` aggregate over `created_at`, since the count
  itself — not a row's timestamp — is the value being bucketed), and
  reuses the existing `mergeZeroCountDays` helper unchanged for
  `7d`/`30d`/`90d` zero-fill and `all`'s sparse-days behavior (REQ-6/7).
- **New endpoint `GET /api/tenants/metrics/members/timeseries`** on the
  existing `MetricsController`, same `@RequiresPermission(DASHBOARD_VIEW)`
  + `@AuditLog(action = "metrics.members.timeseries.view", ...)`
  convention as every sibling timeseries endpoint. The existing
  `GET /api/tenants/metrics/members` endpoint/`MembersMetricDto` are
  untouched (REQ-11).
- **`ActiveMemberSnapshotScheduler`** is a thin `@Component` in
  `br.com.conectabyte.knowly.metrics`, injecting the repository and
  `Clock` (existing `ClockConfig` bean, `Clock.systemUTC()`) — no new
  Clock/config pattern.

## Data schema

New migration `V22__create_active_member_snapshots_table.sql`:

```sql
CREATE TABLE active_member_snapshots (
  id BIGSERIAL PRIMARY KEY,
  tenant_id BIGINT NOT NULL REFERENCES tenants (id),
  snapshot_date DATE NOT NULL,
  active_count BIGINT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by VARCHAR(255) NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by VARCHAR(255) NOT NULL,
  UNIQUE (tenant_id, snapshot_date)
);

CREATE INDEX ix_active_member_snapshots_tenant_date
  ON active_member_snapshots (tenant_id, snapshot_date);
```

No `_aud` (Envers) counterpart: this table is itself an append/upsert
history log, not a point-in-time entity whose own edit history needs
auditing — matching the reasoning already applied to
`audit_events`/`daily-count`-style tables elsewhere in this codebase
(none of `articles`/`conversations`/`messages`' timeseries queries read
from an audited table either; they aggregate the underlying business
tables directly). `created_by`/`updated_by` are still populated (as
`"system:active-member-snapshot-scheduler"`, a literal, since
`@CreatedBy`/`@LastModifiedBy`'s Spring Data auditing resolves the
authenticated principal, which does not exist inside a `@Scheduled` job)
to keep the column `NOT NULL` consistent with every other tenant-owned
table's shape, not because this table needs its own revision history.

## API contracts

| Method | Path | Request | Response | Status codes |
|---|---|---|---|---|
| GET | `/api/tenants/metrics/members/timeseries` | query: `period` (`7d`\|`30d`\|`90d`\|`all`, optional, default `all`) | `{ days: [{ date: string (ISO date), count: number }] }` — identical shape to `ArticlesTimeseriesDto`/`ConversationsTimeseriesDto` | 200, 400 (invalid `period`), 403 (missing `DASHBOARD_VIEW`), 409 (`TenantSelectionRequiredException`, no active tenant) |

`GET /api/tenants/metrics/members` (existing) is unchanged — no row in
this table.

## Dependencies

None new. `@Scheduled`/`@EnableScheduling` are already part of
`spring-context` (transitive via `spring-boot-starter`, already in
`pom.xml`) — only a new annotation on `KnowlyApplication`, not a new
artifact.

## Package/file structure

- `src/main/java/br/com/conectabyte/knowly/metrics/ActiveMemberSnapshot.java` (new entity)
- `src/main/java/br/com/conectabyte/knowly/metrics/ActiveMemberSnapshotRepository.java` (new — upsert native query + day-bucketed read query, mirrors `ArticleRepository`'s `countActiveByDayForTenant(Since)` shape: **two** read methods (bounded `...Since(Long tenantId, Instant from)` and unbounded `...(Long tenantId)`), each with its own explicit `where tenant_id = :tenantId` predicate — appsec review flagged this explicitly: don't collapse the two variants into one query that only applies the predicate on one branch, since the read side is the one place a cross-tenant leak could actually happen)
- `src/main/java/br/com/conectabyte/knowly/metrics/ActiveMemberSnapshotScheduler.java` (new)
- `src/main/java/br/com/conectabyte/knowly/metrics/MembersTimeseriesDto.java` (new, `record MembersTimeseriesDto(List<DailyCountDto> days)`, same shape as `ArticlesTimeseriesDto`)
- `src/main/java/br/com/conectabyte/knowly/metrics/MetricsService.java` (modify: add `membersTimeseries(MetricsPeriod)`)
- `src/main/java/br/com/conectabyte/knowly/metrics/MetricsController.java` (modify: add `GET /members/timeseries`)
- `src/main/java/br/com/conectabyte/knowly/KnowlyApplication.java` (modify: add `@EnableScheduling`)
- `src/main/resources/db/migration/V22__create_active_member_snapshots_table.sql` (new)

## Testing strategy

- Unit: `ActiveMemberSnapshotScheduler` — captures the correct "day that
  just completed" date from a fixed `Clock`, and calls the repository's
  upsert once per tenant present in the aggregate query's result set (a
  Mockito-style unit test, not `@SpringBootTest`, mirroring how other
  simple service-level unit tests in this codebase are structured).
- Integration (Testcontainers, `@SpringBootTest`, mirrors
  `MetricsControllerIntegrationTest`):
  - Running the scheduler twice for the same day produces exactly one
    row per tenant per day (REQ-3, upsert idempotency) — call the
    scheduler method directly rather than waiting on the real cron
    trigger.
  - `GET /api/tenants/metrics/members/timeseries` returns correct
    per-day counts for the active tenant only, zero-filled for
    `7d`/`30d`/`90d`, sparse for `all` (REQ-4/6/7) — seed rows directly
    via the repository rather than running the scheduler N times.
  - Invalid `period` → `400` (REQ-5, existing `MetricsPeriod.from`
    behavior, no new test needed beyond confirming the new endpoint
    wires to the same enum).
  - Missing `DASHBOARD_VIEW` → `403` (REQ-9); no active tenant → the
    existing `TenantSelectionRequiredException` behavior (REQ-10).
  - Tenant isolation: tenant A's snapshot rows never appear while tenant
    B is active (REQ-8), same two-tenant-seed pattern already used by
    `MetricsControllerIntegrationTest`.
  - `GET /api/tenants/metrics/members` (existing endpoint) still returns
    its existing shape, unaffected (REQ-11, regression guard).
