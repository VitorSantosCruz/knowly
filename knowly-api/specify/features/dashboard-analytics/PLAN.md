# PLAN — dashboard-analytics (backend)

> The how. Translates the SPEC into concrete technical decisions.
> References SPEC.md.

## Architectural decisions

- **Date bucketing is UTC calendar-day, not tenant-local.** `Conversation.createdAt`
  and `Message.createdAt` are both `java.time.Instant` (UTC instants) set via
  JPA auditing/manual assignment; `Tenant` has no timezone column or concept
  anywhere in the schema. Introducing tenant-local bucketing would require a
  new `Tenant.timezone` field and DST-aware conversion logic that nothing in
  this SPEC asks for. UTC calendar-day (`date_trunc('day', created_at at time
  zone 'UTC')` at the Postgres layer) is therefore the correct default: it's
  what the data already is, it's what every other timestamp in this codebase
  already assumes, and it avoids inventing tenant-timezone schema/config that
  is out of this SPEC's scope. This is a Tier 2 judgment call (no exact
  precedent in `DECISIONS.md`) — documented here and mirrored as a new
  `DECISIONS.md` entry (see bottom of this file's companion entry below).
- **`period` is parsed/validated once, in a shared value type, not repeated
  per controller method.** A `MetricsPeriod` enum (`SEVEN_DAYS`, `THIRTY_DAYS`,
  `NINETY_DAYS`, `ALL`) with a static `MetricsPeriod.from(String raw)` factory
  that throws a new `InvalidPeriodException` on anything else (including
  `null`, which is treated as absent — Spring's `@RequestParam(defaultValue =
  "all")` already covers "no parameter supplied"). *Why*: every endpoint in
  this SPEC needs identical parsing/validation; centralizing it avoids six
  copies of the same `switch` and keeps the 400 behavior consistent, matching
  this codebase's existing per-module `*ExceptionHandler` + `*ErrorResponseDto`
  convention (see `TenancyExceptionHandler`/`TenancyErrorResponseDto`,
  `ArticleExceptionHandler`/`ArticleErrorResponseDto`).
- **`InvalidPeriodException` is handled by a new `MetricsExceptionHandler`
  (`@RestControllerAdvice`) returning `400` + `MetricsErrorResponseDto("INVALID_PERIOD")`.**
  *Why*: mirrors the exact per-module handler/DTO pattern already used by
  every other module in this codebase (`ArticleExceptionHandler`,
  `AuthExceptionHandler`, `ConversationExceptionHandler`,
  `TenancyExceptionHandler`) rather than inventing a shared/global handler,
  which nothing else in this codebase does.
- **Articles time-series is the same shape as conversations/messages
  time-series, added per the SPEC's amended requirement 6.**
  `Article.createdAt` is a `java.time.Instant` set via `@CreatedDate`
  (verified — identical shape to `Conversation.createdAt`/
  `Message.createdAt`), so it uses the exact same UTC-bucketing/zero-fill
  approach as the other two time-series endpoints, no new pattern needed.
  It counts only **active** articles per day
  (`active = true`), matching the existing
  `ArticleRepository#countByTenantIdAndActiveTrue` convention already used
  by the point-in-time `/articles` endpoint — an inactive (soft-deleted)
  article should not count toward "articles created," consistent with how
  the existing article count already excludes them.
- **Time-series queries are a single native/JPQL aggregate query per endpoint,
  bucketed at the database layer, with zero-count days merged in the
  application (Java) layer.** *Why*: the SPEC's NFR explicitly requires a
  single date-bucketed query (no N+1 per-day queries, bounded time even for
  `period=all`). SQL `GROUP BY` naturally omits days with no rows, so the
  service method generates the full requested date range in Java (a
  `List<LocalDate>` from `period.startDate()` to "today", both computed in
  UTC) and left-merges the query result into it, defaulting missing days to
  zero. This follows the same repository-returns-raw-aggregate,
  service-shapes-response split already used by
  `MessageArticleCitationRepository#usageByTenant` +
  `MetricsService#articleUsage`.
- **New endpoints and DTOs live in the existing `metrics` package**, extending
  `MetricsController`/`MetricsService` — no new top-level package. *Why*:
  these are metrics endpoints in every sense the existing four already are;
  splitting them into a separate package would fragment one cohesive concern
  for no benefit, and would break the "read the existing class, match its
  style" instruction this task was given.
- **Every new/changed endpoint keeps `@RequiresPermission(Permission.DASHBOARD_VIEW)`
  and `@AuditLog(action = "metrics.<resource>.<view>", resourceType = "Metrics")`**,
  matching the exact existing convention — no new `Permission` value (per
  SPEC's non-functional requirements and the user's confirmation).
- **CSV export uses a hand-built CSV string via `ResponseEntity<byte[]>`,
  no new dependency.** `pom.xml` has no CSV library today (checked: no
  `opencsv`/`commons-csv`/`super-csv`). The export's column set is small,
  fixed, and fully controlled by this service (no user-supplied free text
  needing escaping beyond article titles, which may contain commas — those
  are wrapped in double quotes with `"` doubled per RFC 4180, using a small
  private helper method rather than a library). *Why*: adding a CSV library
  for ~7 known columns is disproportionate; if a future export needs
  nested/quoted-edge-case-heavy data, that's the trigger to revisit and add
  `commons-csv` as a flagged Tier 3 dependency then, not now.
- **Membership metric endpoint (`/api/tenants/metrics/members`) is a new
  repository method + service method, not period-filtered** (per SPEC
  requirement 6 — membership `active` state has no created-per-day
  semantics). `TenantMembershipRepository` gets
  `countByTenantIdAndActive(Long tenantId, boolean active)`, called twice
  (once per boolean) from the service, matching the existing pattern of two
  `countBy...` calls in `messagesMetric()`.
- **Existing point-in-time endpoints (`/conversations`, `/messages`) gain
  `period` filtering via new repository methods that add a `createdAt`
  lower-bound**, not by filtering in Java. `ConversationRepository` gets
  `countByTenantIdAndCreatedAtGreaterThanEqual(Long tenantId, Instant from)`;
  `MessageRepository` gets
  `countByConversation_Tenant_IdAndRoleAndCreatedAtGreaterThanEqual(Long
  tenantId, MessageRole role, Instant from)`. When `period == ALL`, the
  service calls the existing unfiltered methods unchanged (preserving current
  behavior/response shape exactly, per SPEC requirement 8) — no lower bound
  is computed or applied.
- **`MetricsPeriod` computes its UTC start boundary as
  `Instant.now(clock).minus(N, DAYS)` for 7d/30d/90d, and `Optional.empty()`
  for `all`**, so "period filtering" and "no filtering" are represented
  distinctly rather than by a sentinel date — avoids an off-by-one/epoch
  hack for `all`. A single injected `java.time.Clock` bean (UTC) is used
  wherever "now" is computed, for testability (matches
  `TASKS.md`'s test strategy needing deterministic day boundaries in
  integration tests). If no `Clock` bean already exists in this codebase, one
  is added (`@Bean Clock.systemUTC()`) — checked as part of task 1.

## Data schema

No new tables/entities/migrations. This feature is read-only against
existing `conversations`, `messages`, and `tenant_memberships` tables. No
column additions. Two new repository query methods (see above) generate
extra derived SQL, not schema changes.

## API contracts

| Method | Path | Request | Response (200) | Errors |
|---|---|---|---|---|
| GET | `/api/tenants/metrics/conversations/timeseries` | Query: `period` (`7d`\|`30d`\|`90d`\|`all`, default `all`) | `ConversationsTimeseriesDto(List<DailyCountDto> days)` where `DailyCountDto(LocalDate date, long count)`, chronological, zero-count days included | 400 `MetricsErrorResponseDto("INVALID_PERIOD")`; 403 `TenancyErrorResponseDto("PERMISSION_DENIED")`; 403 `TenancyErrorResponseDto("TENANT_ACCESS_DENIED")` |
| GET | `/api/tenants/metrics/messages/timeseries` | Query: `period` (same) | `MessagesTimeseriesDto(List<DailyRoleCountDto> days)` where `DailyRoleCountDto(LocalDate date, long userCount, long assistantCount)`, chronological, zero-count days included | same as above |
| GET | `/api/tenants/metrics/articles/timeseries` | Query: `period` (same) | `ArticlesTimeseriesDto(List<DailyCountDto> days)` — reuses the same `DailyCountDto(LocalDate date, long count)` shape as conversations timeseries, counting only active articles created that day, chronological, zero-count days included | same as above |
| GET | `/api/tenants/metrics/members` | none | `MembersMetricDto(long activeCount, long inactiveCount)` | 403 (permission/tenant) |
| GET | `/api/tenants/metrics/export` | Query: `period` (same, default `all`) | `text/csv` file attachment (`Content-Disposition: attachment; filename="dashboard-metrics.csv"`), body per column set below | 400/403 as above |
| GET | `/api/tenants/metrics/conversations` | Query: `period` (same, default `all`, **new**, optional) | `ConversationsMetricDto(long startedCount)` — unchanged shape | 400/403 as above |
| GET | `/api/tenants/metrics/messages` | Query: `period` (same, **new**, optional) | `MessagesMetricDto(long sentCount, long receivedCount)` — unchanged shape | 400/403 as above |

CSV column set for `/export` (per SPEC requirement 7 — aggregates only, no
raw content):

```
metric,value
active_article_count,<n>
conversation_count,<n>
user_message_count,<n>
assistant_message_count,<n>
member_active_count,<n>
member_inactive_count,<n>

date,article_count
<yyyy-MM-dd>,<n>
...

date,conversation_count
<yyyy-MM-dd>,<n>
...

date,user_message_count,assistant_message_count
<yyyy-MM-dd>,<n>,<n>
...
```
Four sections (aggregate totals, articles/day, conversations/day,
messages/day) separated by a blank line, mirroring the four logical widgets
the SPEC lists (including the article-count tile's new sparkline). Exact
header text is a Tier 1 implementation detail — the shape above is fixed by
this PLAN; the frontend PLAN (`knowly-app/specify/features/dashboard-analytics/PLAN.md`)
should treat this section as the contract for its export-consuming code
(likely just "trigger download," since a CSV file doesn't need
client-side parsing).

## Dependencies

None added. Checked `pom.xml`: no CSV library present; none needed per the
"Architectural decisions" CSV approach above. If a future iteration needs a
robust CSV library, that's a new Tier 3 dependency decision at that time, not
now.

## Package/file structure

New files (all in `br.com.conectabyte.knowly.metrics` unless noted):

- `MetricsPeriod.java` — enum + `from(String)` factory + `startInstant(Clock)`.
- `InvalidPeriodException.java` (or `exception/` subpackage if this module
  gains an `exception/` subpackage like `article`/`conversation`/`tenancy`
  already have — checked at task 1, follow whichever convention is found).
- `MetricsExceptionHandler.java` + `MetricsErrorResponseDto.java` (in
  `dto/` subpackage if that's the established pattern — checked at task 1).
- `ConversationsTimeseriesDto.java`, `DailyCountDto.java` (shared by
  conversations and articles time-series — same shape).
- `MessagesTimeseriesDto.java`, `DailyRoleCountDto.java`.
- `ArticlesTimeseriesDto.java` (reuses `DailyCountDto`).
- `MembersMetricDto.java`.
- `ClockConfig.java` (only if no `Clock` bean already exists anywhere in
  `br.com.conectabyte.knowly` — checked at task 1 before creating).

Changed files:

- `MetricsController.java` — 5 new `@GetMapping` methods (`timeseries` x3,
  `members`, `export`), `period` param added to the 2 existing methods.
- `MetricsService.java` — corresponding new/changed methods.
- `ConversationRepository.java` — new derived query method.
- `MessageRepository.java` — new derived query method; new native/JPQL
  day-bucketed aggregate query method.
- `ArticleRepository.java` — new native/JPQL day-bucketed aggregate query
  method, filtered to `active = true` (same filter as
  `countByTenantIdAndActiveTrue`).
- `TenantMembershipRepository.java` — new `countByTenantIdAndActive` method.

## Testing strategy

Integration tests extend `MetricsControllerIntegrationTest.java`'s existing
pattern exactly (Testcontainers, `MockMvcTester`, `memberWithPermissions`
helper, tenant-A/tenant-B isolation style already used by
`articleUsageRanksByCitationCountAndExcludesOtherTenants` and
`conversationsMetricIsTenantWideNotJustTheCallersOwn`). Per new endpoint,
minimum coverage:

- **Tenant isolation**: tenant B's conversations/messages/memberships never
  appear in tenant A's response while tenant A is active (same style as
  existing tests).
- **Permission denial**: 403 without `DASHBOARD_VIEW`, added to the existing
  `eachMetricsEndpointRequiresDashboardViewPermissionIndependently` test
  (extend it with the 4 new paths, rather than a new test method, to keep one
  place that enumerates "every metrics endpoint").
- **Period validation**: one shared test asserting `?period=bogus` returns
  400 with `INVALID_PERIOD` on at least the three new timeseries endpoints
  (conversations, messages, articles) and one existing endpoint
  (`/conversations`) — not required on every single
  endpoint since the parsing is centralized and already unit-tested at the
  `MetricsPeriod` level.
- **Zero-count-day behavior**: a dedicated test seeding conversations/messages
  on only some days within a 7d window, asserting the response includes every
  day in the window (including zero-count ones) in chronological order — this
  is the one behavior with no existing precedent in
  `MetricsControllerIntegrationTest`, so it needs its own test, not a
  reused pattern.
- **`MetricsPeriod` unit test** (plain JUnit, no Spring context): valid
  values parse, invalid values throw `InvalidPeriodException`, `all` produces
  no lower bound, and 7d/30d/90d produce the expected `Instant` given an
  injected fixed `Clock` — this is where the bulk of "is period math right"
  coverage belongs, cheaper and more precise than asserting it only through
  HTTP integration tests.
- **CSV export test**: asserts `Content-Type`/`Content-Disposition`, asserts
  the response body contains the expected aggregate lines and per-day
  sections for a seeded tenant, and asserts it does NOT contain any message
  `content` or article title substring used as raw content elsewhere in the
  test (guards SPEC requirement 7's "no raw content" constraint).
- Full suite: `./mvnw spotless:apply && ./mvnw verify` must stay green
  (final TASKS.md step).
