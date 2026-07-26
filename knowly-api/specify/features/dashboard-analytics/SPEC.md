# SPEC — dashboard-analytics (backend)

> The what and the why. No technical implementation details.

## Context and motivation

The current dashboard (`br.com.conectabyte.knowly.metrics`) only exposes
point-in-time counts (active article count, top-used articles,
conversation count, USER-vs-ASSISTANT message counts) for the active
tenant. The user wants an enterprise-analytics-style dashboard on the
frontend (metric tiles with trend sparklines, a donut chart, a bar chart
with a time-range switcher, an active-users breakdown, a searchable/
exportable table) — none of which the backend can currently support,
because every existing query is "as of now," with no date-bucketing and
no period filter. This SPEC adds the backend data needed for that:
date-bucketed time-series metrics, a tenant membership/activity metric,
a period filter applicable across metrics, and a CSV export of the
dashboard's metric values. The corresponding UI is a separate SPEC in
`knowly-app/specify/features/dashboard-analytics/SPEC.md`, per this
project's cross-folder SPEC placement rule.

This dashboard remains strictly scoped to the caller's single active
tenant, consistent with the existing Hibernate tenant-isolation filter
(`TenantFilter`) — it never aggregates or compares across tenants, even
for staff acting as a tenant. Cross-tenant analytics/benchmarking is
explicitly listed in `VISION.md`'s "What's deliberately not decided yet"
and is not addressed by this feature.

## User stories

- As a tenant admin/manager with `DASHBOARD_VIEW`, I want to see
  conversation and message activity broken down by day over a selectable
  period (7/30/90 days, or all time), so I can see usage trends, not just
  a running total.
- As a tenant admin/manager with `DASHBOARD_VIEW`, I want to see how many
  members of my tenant are currently active vs. inactive, so I can gauge
  real adoption, not just headcount.
- As a tenant admin/manager with `DASHBOARD_VIEW`, I want to export the
  current dashboard's metrics as a CSV file, so I can share/archive them
  outside the app.
- As a tenant admin/manager with `DASHBOARD_VIEW`, I want to apply the
  same period filter (7d/30d/90d/all) to the existing point-in-time
  metrics (conversation count, message counts, article usage), so every
  widget on the dashboard reflects the same time window consistently.

## Requirements (EARS/GEARS)

1. **[Ubiquitous]** The `MetricsService` shall scope every metric query
   (existing and new) to the caller's single active tenant, via the same
   tenant-resolution mechanism already used by `requireActiveTenant()` —
   never a mechanism that queries across tenants.

2. **[Ubiquitous]** The system shall accept an optional `period` query
   parameter with allowed values `7d`, `30d`, `90d`, `all` on every
   metrics endpoint listed in this SPEC (new and existing); a request
   with no `period` parameter shall default to `all`.

3. **[Unwanted Behavior]** If `period` is present but is not one of
   `7d`, `30d`, `90d`, `all`, then the system shall respond `400 Bad
   Request` with a stable error code (never a stack trace or a silently
   ignored value).

4. **[Event-Driven]** When `GET /api/tenants/metrics/conversations/timeseries?period=<period>`
   is called by a caller holding `DASHBOARD_VIEW` for the active tenant,
   the system shall return one count of conversations created per
   calendar day (tenant-local or UTC — see PLAN for the pinned choice)
   within the requested period, including days with a zero count, ordered
   chronologically.

5. **[Event-Driven]** When `GET /api/tenants/metrics/messages/timeseries?period=<period>`
   is called by a caller holding `DASHBOARD_VIEW` for the active tenant,
   the system shall return, per calendar day within the requested period,
   both the USER-authored and ASSISTANT-authored message counts for that
   day (including zero-count days), ordered chronologically.

6. **[Event-Driven]** When `GET /api/tenants/metrics/articles/timeseries?period=<period>`
   is called by a caller holding `DASHBOARD_VIEW` for the active tenant,
   the system shall return one count of active articles created per
   calendar day within the requested period, including zero-count days,
   ordered chronologically — added specifically so the frontend's
   article-count metric tile can show a real trend sparkline like the
   other four tiles, instead of shipping without one.

7. **[Event-Driven]** When `GET /api/tenants/metrics/members` is called
   by a caller holding `DASHBOARD_VIEW` for the active tenant, the system
   shall return the count of `TenantMembership` rows for that tenant with
   `active = true` and the count with `active = false`, as of the moment
   of the call (this endpoint is a point-in-time snapshot, not
   period-filtered, since membership activation state has no created-per-day
   semantics today).

8. **[Event-Driven]** When `GET /api/tenants/metrics/export?period=<period>`
   is called by a caller holding `DASHBOARD_VIEW` for the active tenant,
   the system shall return a CSV file (as an HTTP file attachment)
   containing the same aggregate values shown on the dashboard for that
   tenant and period: active article count, conversation count, USER/
   ASSISTANT message counts, member active/inactive counts, and the
   per-day time-series rows (articles/day, conversations/day,
   messages/day by role) within the requested period. It shall not
   include raw per-user, per-conversation, or per-message content (no
   transcripts, no article contents) — only the aggregate metric values
   already exposed by this feature's read endpoints.

9. **[Optional Feature]** Where `period` is applied to
   `GET /api/tenants/metrics/conversations` and
   `GET /api/tenants/metrics/messages` (the existing point-in-time
   endpoints), the system shall count only conversations/messages created
   within that period, instead of the tenant's all-time total, while
   preserving the current response shape.

10. **[Unwanted Behavior]** If a caller does not hold `DASHBOARD_VIEW` for
    the active tenant, then every endpoint in this SPEC (existing and new)
    shall respond `403 Forbidden`, consistent with the existing
    `@RequiresPermission(Permission.DASHBOARD_VIEW)` gating.

11. **[Unwanted Behavior]** If no tenant is active in the caller's
    session, then every endpoint in this SPEC shall respond with the same
    tenant-access-denied behavior already used by
    `requireActiveTenant()` (`TenantAccessDeniedException`), never expose
    another tenant's data.

## Non-functional requirements

- Security: every new endpoint is gated by the existing
  `Permission.DASHBOARD_VIEW` (same permission as the rest of the
  dashboard) — no new `Permission` enum value is introduced by this
  feature. Tenant isolation is enforced identically to every existing
  metrics endpoint (Hibernate `TenantFilter`, never a manual
  `WHERE tenant_id = ?`).
- Performance/SLA: time-series queries must be backed by a single
  date-bucketed aggregate query per endpoint (e.g. `GROUP BY
  date_trunc('day', created_at)`), not N+1 per-day queries; a `period=all`
  request must still complete in bounded time for a tenant with a large
  history — bucket at the database layer, not by loading all rows into
  the application.
- Observability: every new endpoint follows the existing `@AuditLog`
  convention (action name following the `metrics.<resource>.view`
  pattern already used, e.g. `metrics.conversations.timeseries.view`,
  `metrics.members.view`, `metrics.export.view`).

## Acceptance criteria

- [ ] `GET /api/tenants/metrics/conversations/timeseries` returns
      per-day conversation counts for the active tenant only, honoring
      `period`, including zero-count days, for all four `period` values.
- [ ] `GET /api/tenants/metrics/messages/timeseries` returns per-day
      USER/ASSISTANT message counts for the active tenant only, honoring
      `period`, including zero-count days.
- [ ] `GET /api/tenants/metrics/articles/timeseries` returns per-day
      active-article-creation counts for the active tenant only,
      honoring `period`, including zero-count days.
- [ ] `GET /api/tenants/metrics/members` returns active/inactive
      membership counts for the active tenant only.
- [ ] `GET /api/tenants/metrics/export` returns a downloadable CSV
      containing the aggregate values above for the requested period, and
      no raw article/message/conversation content.
- [ ] `GET /api/tenants/metrics/conversations` and
      `GET /api/tenants/metrics/messages` honor an optional `period`
      query parameter without breaking their current default (`all`)
      behavior or response shape.
- [ ] An invalid `period` value returns `400`, never a silent fallback or
      stack trace.
- [ ] Every endpoint in this SPEC is denied with `403` to a caller
      lacking `DASHBOARD_VIEW`, and is tenant-isolated (verified with an
      integration test asserting tenant A's data is never visible while
      tenant B is active), matching `MetricsControllerIntegrationTest`'s
      existing pattern.
- [ ] `./mvnw spotless:apply && ./mvnw verify` passes.

## Out of scope

- PDF export (only CSV is in scope for this SPEC — see the "Open items"
  note in the accompanying report; add a follow-up SPEC if PDF is later
  requested).
- Any new `Permission`/`GlobalPermission` value — this feature reuses
  `DASHBOARD_VIEW` exclusively, including for the new membership metric.
- A concept of "pending invites" — no such state exists in
  `TenantMembership` today (membership rows are created immediately, with
  only an `active` boolean); this feature exposes active/inactive member
  counts instead. Introducing an actual invite-pending state is a
  separate, larger feature (new entity/workflow) and is not addressed
  here.
- Cross-tenant or global (all-tenants) aggregation of any metric, for
  staff or otherwise — remains explicitly excluded per `VISION.md`.
- Any change to how `TenantMembership.active` is set/unset (this feature
  only reads that field, it does not add new ways to change it).
- Raw/row-level export (individual messages, conversations, or article
  usage events) — export is aggregate-metrics-only, as stated above.
- Any new time granularity other than "per day" (no hourly/weekly
  bucketing).

## Confirmed by the user (2026-07-26)

1. **"Pending invites" doesn't exist in the data model.** `TenantMembership`
   has only a boolean `active` flag (set at creation, no invite/pending
   state). This SPEC substitutes **active-vs-inactive member counts** for
   that backlog item — confirmed, a real pending-invite workflow is
   deferred as a separate, larger feature.
2. **Export format**: CSV only for v1 (PDF deferred, listed as out of
   scope/future) — confirmed.
3. **Permission gating for the membership metric**: reuses `DASHBOARD_VIEW`
   (not `TENANT_MEMBER_MANAGE`) — confirmed.
4. **Articles time-series endpoint**: added (requirement 6 above) so the
   frontend's article-count tile gets a real trend sparkline like the
   other four tiles — confirmed, rather than shipping that tile without
   one.
