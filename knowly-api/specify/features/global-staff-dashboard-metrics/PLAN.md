# PLAN — global-staff-dashboard-metrics

> The how. Translates the SPEC into concrete technical decisions.
> References SPEC.md.

## Architectural decisions

- `GlobalPermission` gains one new value: `DASHBOARD_VIEW_GLOBAL`,
  appended to the existing enum (`br.com.conectabyte.knowly.tenancy`).
  No migration — `GlobalPermission` is a plain Java enum with no
  DB-backed lookup table, same as every prior addition to this enum
  this session (`STAFF_USER_CREATE`, `STAFF_USER_VIEW` in
  `staff-user-listing`); `DirectGlobalPermissionGrant`/
  `GlobalAccessGroupPermission` store it as a string/ordinal column with
  no FK to a values table, so a new constant needs no schema change.
- New package: `br.com.conectabyte.knowly.metrics.global`, mirroring how
  `metrics` already holds the tenant-scoped equivalent, rather than
  dropping global-metrics types into the existing tenant-scoped
  `metrics` package or into `tenancy` — this keeps "global staff
  aggregation" visually distinct from "tenant-scoped dashboard" the same
  way `StaffController`/`TenantController` are already visually distinct
  packages/controllers for the same reason (different authorization
  model entirely: `GlobalPermission` vs `Permission`).
- New repository query methods (added to existing repositories, not new
  repositories — mirrors how `MetricsService` reuses
  `ArticleRepository`/`ConversationRepository` etc. rather than spinning
  up metrics-only repositories):
  - `TenantRepository.count()` — already provided free by
    `JpaRepository`, no new method needed. Confirmed `Tenant` carries no
    `@Filter`/tenant-scoping annotation itself (it *is* the tenant), so
    this is a plain global count, no bypass needed.
  - `TenantRepository.countByCreatedAtGreaterThanEqual(Instant from)` —
    new derived query method for "new tenants this month."
  - `MessageArticleCitationRepository.count()` — already provided free
    by `JpaRepository`; SPEC REQ-5 wants a system-wide count with no
    filter, so no new method needed. (Note: `MessageArticleCitation`
    itself has no direct tenant FK — it cites through
    `Article`/`Conversation` — so an unfiltered `count()` is correct and
    requires no `@Filter` disabling.)
  - `UserRepository.countByGlobalRoleIn(List<GlobalRole> globalRoles)` —
    new derived count method, sibling to the existing
    `findByGlobalRoleIn` added in `staff-user-listing`; a dedicated
    `count*` avoids loading every staff `User` row into memory just to
    call `.size()` (SPEC's "no N+1, no loading rows just to count"
    non-functional requirement).
- New `GlobalMetricsService` (new class, not added to the existing
  `MetricsService`) in the new `metrics.global` package. Kept separate
  rather than added as a method on `MetricsService` because
  `MetricsService`'s constructor/every existing method is built around
  `requireActiveTenant()`/`TenantContext` — mixing in a method that must
  *never* call that would be an easy place for a future edit to
  accidentally scope a global query, and SPEC REQ-11 explicitly calls
  out this is the one place doing unscoped aggregation is correct rather
  than a bug. A separate service makes "this class never touches
  `TenantContext`" true by construction, not by convention.
  - Single method: `globalMetrics(): GlobalMetricsDto`, annotated
    `@Transactional(readOnly = true)`,
    `@RequiresGlobalPermission(GlobalPermission.DASHBOARD_VIEW_GLOBAL)`,
    `@AuditLog(action = "metrics.global.view", resourceType =
    "Metrics")` — same three-annotation shape used throughout
    `StaffService`/`MetricsService`, `readOnly` is safe to combine with
    `@AuditLog` because `AuditEventWriter.write` already runs in its own
    `REQUIRES_NEW` transaction (see `AuditEventWriter`'s class Javadoc);
    no new work needed to avoid that bug class, just don't remove that
    annotation.
  - Computes `startOfCurrentUtcMonth` via `LocalDate.now(clock)
    .withDayOfMonth(1).atStartOfDay(ZoneOffset.UTC).toInstant()`,
    reusing the injected `Clock` bean the same way `MetricsService` does
    for testability (no new `Clock.systemUTC()` call site).
- `GlobalMetricsDto(long tenantCount, long newTenantsThisMonth, long
  articlesReadTotal, long staffCount)` — a plain record in
  `metrics.global`, four `long` fields per SPEC REQ-1, no
  support-ticket field (SPEC REQ-7 explicitly defers that; adding a
  placeholder/null field now would be scope creep on a Tier 3 boundary
  this SPEC deliberately didn't cross — see "Out of scope").
- New `GlobalMetricsController` (new class, not a method added to
  `StaffController`) — mirrors the existing `MetricsController` vs
  `TenantController` split: `TenantController` handles tenant/member/
  access-group CRUD while `MetricsController` is a dedicated
  read-only reporting controller in its own package; `StaffController`
  is the equivalent CRUD-style controller for global staff/permission
  management, so a reporting-only endpoint gets its own controller for
  the same reason, not bolted onto `StaffController`.
  - `@RestController`, `@RequestMapping("/api/staff/metrics")`,
    single `@GetMapping("/global")` method, delegating straight to
    `GlobalMetricsService.globalMetrics()` — the permission check and
    audit log live on the service method (per
    `RequiresGlobalPermission`/`AuditLog`'s existing convention of being
    service-layer annotations, e.g. `StaffService`), not duplicated on
    the controller method.

## API contract

| Method | Path | Request | Response | Status |
|---|---|---|---|---|
| GET | `/api/staff/metrics/global` | — | `GlobalMetricsDto` (`tenantCount`, `newTenantsThisMonth`, `articlesReadTotal`, `staffCount`) | 200 OK (caller holds `DASHBOARD_VIEW_GLOBAL` or is `STAFF_ADMIN`); 403 Forbidden (no `GlobalRole`, or `STAFF` without the permission) |

## Decisions requiring no new DECISIONS.md entry

Every decision above is a direct application of already-documented
precedent (plain-enum `GlobalPermission` additions need no migration;
`@RequiresGlobalPermission` + `@AuditLog` + `@Transactional(readOnly =
true)` composition already proven safe via `AuditEventWriter`'s
`REQUIRES_NEW`; controller/service package split already established
by `MetricsController`/`MetricsService` vs `TenantController`/
`TenantService`). No genuinely novel Tier 3 tradeoff was found; nothing
added to `DECISIONS.md` for this feature.

## Open questions / flags

None. The SPEC is sufficiently complete to implement as written; no
scope gap was found that would require flagging back to the PO.
