# SPEC — global-staff-dashboard-metrics (backend)

## Context and motivation

`dashboard-analytics` provides metrics strictly scoped to a single
active tenant, gated by tenant `Permission.DASHBOARD_VIEW`. Per
`PROJECT_STATUS.md` item 6 ("Outside any tenant (staff global view)",
confirmed by the user 2026-07-26), staff need a *different*, additional
dashboard when not acting inside any tenant: a small set of counts
aggregated across every tenant, for ConectaByte's own internal
operational visibility — not a customer-facing "compare your usage to
others" feature.

This is the **global counterpart** to `dashboard-analytics`: same
overall shape, but reading across all tenants instead of the one active
tenant, gated by a `GlobalPermission` instead of a tenant `Permission`.

**Scope clarification** (see `dashboard-analytics` SPEC's amended "Out of
scope"): that SPEC's exclusion of cross-tenant aggregation, and
`VISION.md`'s "not yet decided" cross-tenant analytics item, both refer
to a customer-facing benchmarking product. This feature is a staff-only
internal operations view, never exposed to any tenant, gated by its own
new `GlobalPermission` — a distinct capability.

The staff welcome screen and member-listing/profile-open screen
mentioned alongside this in `PROJECT_STATUS.md` item 6 are frontend
concerns and/or covered by other features already in flight
(`identity-profile-model`, `staff-user-listing`) — out of scope here.

## User stories

- As a `STAFF`/`STAFF_ADMIN` holding the new global dashboard
  permission, I want to see total tenant count, so I can gauge how many
  customers exist.
- As a `STAFF`/`STAFF_ADMIN` holding the permission, I want new tenants
  this calendar month, to gauge recent growth.
- As a `STAFF`/`STAFF_ADMIN` holding the permission, I want total
  articles read across every tenant, to gauge overall product usage.
- As a `STAFF`/`STAFF_ADMIN` holding the permission, I want total staff
  member count, to gauge team size.
- As a `STAFF`/`STAFF_ADMIN`, I want the response to clearly indicate a
  support-ticket metric is planned but not yet available, rather than
  silently omitted with no explanation.

## Requirements (EARS/GEARS)

1. **[Ubiquitous]** The system shall expose `GET /api/staff/metrics/global`
   returning: total tenant count, new-tenants-this-calendar-month count,
   total articles-read count (across every tenant), and total staff
   member count.
2. **[Ubiquitous]** The system shall gate every endpoint with a new
   `GlobalPermission.DASHBOARD_VIEW_GLOBAL`, via the existing
   `@RequiresGlobalPermission` mechanism (`STAFF_ADMIN` always passes).
3. **[Ubiquitous]** "Total tenant count" is a count of every `Tenant`
   row, no active/inactive filter (no such state exists on `Tenant`).
4. **[Ubiquitous]** "New tenants this calendar month" is the count of
   `Tenant` rows whose `createdAt` falls within `[start of current UTC
   calendar month, now)` — consistent with `dashboard-analytics`'s UTC
   calendar-day convention, one level up. Rolling "so far this month,"
   not a trailing 30-day window.
5. **[Ubiquitous]** "Total articles read" is a count of every
   `MessageArticleCitation` row system-wide, no tenant/date filter.
6. **[Ubiquitous]** "Staff member count" is a count of every `User` row
   whose `globalRole` is `STAFF` or `STAFF_ADMIN`.
7. **[Optional Feature]** Where a future support-channel feature (item
   14) introduces support tickets, the response shall gain a fifth field
   for total support-ticket count — **not implemented by this SPEC**.
8. **[Event-Driven]** When called by a caller holding
   `DASHBOARD_VIEW_GLOBAL` (or `STAFF_ADMIN`), the system shall return
   `200 OK` with the four counts above.
9. **[Unwanted Behavior]** If a caller lacks `DASHBOARD_VIEW_GLOBAL` (and
   isn't `STAFF_ADMIN`), then the system shall respond `403 Forbidden`.
10. **[Unwanted Behavior]** If the caller is a tenant `MEMBER`/
    `MEMBER_ADMIN` with no `GlobalRole`, then the system shall respond
    `403 Forbidden` regardless of tenant-side permissions.
11. **[Ubiquitous]** The system shall never scope this feature's queries
    through `TenantFilter`/`TenantContext.getActiveTenantId()` — global
    aggregation by design, the one place this is correct rather than an
    isolation bypass (it returns counts only, never row-level tenant
    content).

## Non-functional requirements

- Security: gated exclusively by `GlobalPermission.DASHBOARD_VIEW_GLOBAL`
  — no tenant `Permission` involved anywhere.
- Performance: each count is a single aggregate query — no N+1, no
  loading rows into the application just to count them.
- Observability: `@AuditLog`, action `metrics.global.view`,
  `resourceType = "Metrics"`.

## Acceptance criteria

- [ ] `GlobalPermission.DASHBOARD_VIEW_GLOBAL` exists.
- [ ] `GET /api/staff/metrics/global` returns the four counts.
- [ ] A caller without the permission (and not `STAFF_ADMIN`) gets 403.
- [ ] A tenant member with no `GlobalRole` gets 403.
- [ ] `STAFF_ADMIN` always succeeds without an explicit grant.
- [ ] "New tenants this month" excludes a tenant created in the previous
      UTC calendar month, includes one created in the current month.
- [ ] Queries reflect all tenants, no `TenantFilter` scoping.
- [ ] Support-ticket metric is documented as not-yet-available, not
      silently dropped or faked as zero.
- [ ] `./mvnw spotless:apply && ./mvnw verify` passes.

## Out of scope

- Support-ticket count — depends on item 14.
- Staff welcome screen / member-listing screen — frontend/other features.
- Any per-tenant breakdown of these counts.
- Historical/time-series version of these counts.
- Any customer-facing cross-tenant benchmarking — remains excluded per
  `VISION.md`; this feature must not be repurposed into a tenant-facing
  comparison feature without a fresh Tier 3 decision.
- Reuse of tenant-scoped `Permission.DASHBOARD_VIEW` — this feature uses
  its own dedicated `GlobalPermission`.
- Changing `dashboard-analytics`'s "Out of scope" line itself (already
  clarified there, not rewritten).

## Notes (judgment calls, Tier 2)

- "Articles read" = `MessageArticleCitation` count (citation/usage, not
  raw `Article` row count) — matches the existing tenant-scoped
  definition (`ArticleUsageDto`/`articleUsage()`).
- UTC calendar-month boundary, not a rolling 30-day window — extends the
  existing UTC calendar-day precedent.
- New dedicated `GlobalPermission.DASHBOARD_VIEW_GLOBAL` rather than
  reusing an existing value — mirrors how every other staff capability
  got its own dedicated permission.
