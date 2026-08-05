# PLAN — paginated-audit-trail

> The how. Translates the SPEC into concrete technical decisions.
> References SPEC.md.

## Architectural decisions

- **Reuse `PageResponseDto<T>` and `InvalidPaginationException` verbatim
  from `tenant-pagination-search`, no new types.** Both already live in
  `br.com.conectabyte.knowly.tenancy` (`dto.PageResponseDto`,
  `exception.InvalidPaginationException`), the same package
  `StaffController`/`StaffService` are in, and `PageResponseDto`'s own
  PLAN already flagged "if/when a second paginated endpoint is planned,
  that's the natural trigger" — this is that trigger, and no move is
  needed since it's already in a location this feature can import from
  directly. `TenancyExceptionHandler` already has an
  `@ExceptionHandler(InvalidPaginationException.class)` → 400 mapping,
  so no handler change is needed either.
- **Validation/clamping logic in `StaffService.getAuditTrail`, copied
  from `TenantService.listAllTenants`'s exact shape**: reject
  `page < 0 || size <= 0` with `InvalidPaginationException` first, only
  then clamp `size` to a `MAX_PAGE_SIZE = 100` constant — same order as
  the precedent, for the same reason (an out-of-range-and-negative
  `size=-500` must be rejected, not silently clamped into a positive
  number). `StaffService` gets its own private `MAX_PAGE_SIZE` constant
  rather than referencing `TenantService`'s (different class, no shared
  base — a public shared constant for a two-line literal is
  over-engineering for what it saves).
- **`AuditEventRepository` gets one new derived method,
  `Page<AuditEvent> findByActorUserIdOrderByOccurredAtDesc(Long
  actorUserId, Pageable pageable)`, replacing
  `findTop500ByActorUserIdOrderByOccurredAtDesc` as `StaffService`'s
  query.** Spring Data supports a `Page<T>` return type on a derived
  query method regardless of whether the repository extends the bare
  `Repository` marker interface (as `AuditEventRepository` does here) or
  `JpaRepository` — no interface change needed. The existing
  `ix_audit_events_actor_time (actor_user_id, occurred_at)` composite
  index (already relied on by the method being replaced) continues to
  back the `ORDER BY occurred_at DESC` + offset/limit this generates, so
  the SPEC's indexed-performance NFR is satisfied with no new migration.
  The `findTop500...` method is removed rather than kept alongside the
  new one: it existed solely as this endpoint's defensive cap
  (`staff-audit-trail-view/PLAN.md`), genuine `Pageable`-bounded
  pagination (max `size=100` per page, enforced above) supersedes that
  cap outright, and an unused, superseded repository method left in
  place would be dead code. The distinct, unrelated
  `findByActorUserIdOrderByOccurredAtDesc(Long)` (unpaginated, no
  `Pageable`) stays exactly as-is — it's exercised by dozens of
  unrelated tests asserting audit events were written by other features
  entirely, not this endpoint, and this SPEC is explicitly a read/
  pagination change only for this one endpoint.
- **`StaffService.getAuditTrail` signature changes to `(Long userId, int
  page, int size)`, returning `PageResponseDto<AuditEventDto>`**,
  replacing today's `(Long userId): List<AuditEventDto>`. Breaking
  change confined to one call site
  (`StaffController.auditTrail`, verified via the file read for this
  PLAN) — the SPEC's own NFR explicitly accepts this since the only
  caller is the frontend also being changed in the same effort.
  `AuditEventDto::from` per-row mapping is untouched (unaffected by
  pagination), same as `TenantSummaryDto::from` in the precedent.
  Existing behavior kept as-is, unchanged, ahead of the new pagination
  logic: the `UserNotFoundException` existence check on `userId`, the
  `@RequiresGlobalPermission(GlobalPermission.AUDIT_TRAIL_VIEW)` gate,
  the `@AuditLog(action = "staff.audit_trail.view", ...)` annotation,
  and the "deliberately does not call `enforceStaffCeiling`" comment
  (REQ-3 of this SPEC maps to that existing gate + existence check,
  neither of which pagination parameters can affect or bypass).
- **`StaffController.auditTrail` adds `@RequestParam(defaultValue =
  "0") int page` and `@RequestParam(defaultValue = "20") int size`**,
  same defaults as `TenantController.listAllTenants`, keeping the
  controller thin and all validation in the service — identical
  controller/service split to the precedent. No `search`/`sort`
  parameter: the SPEC has no requirement for either (REQ-1/REQ-2 only
  cover `page`/`size` and a fixed `occurredAt DESC` order), so none is
  added.
- **Sort stays fixed server-side (`occurredAt` descending), never
  client-supplied** — mirrors `tenant-pagination-search`'s "no `sort`
  request parameter" decision for the same reason: the SPEC doesn't ask
  for configurable sort (REQ-2 says descending, full stop), so none is
  exposed. The `Pageable` built in `StaffService` carries
  `Sort.by("occurredAt").descending()` explicitly rather than relying on
  the repository method name's implicit `OrderByOccurredAtDesc` alone —
  keeping this explicit in the service (like `TenantService` does) means
  the sort is visible at the call site building the `Pageable`, not only
  buried in a derived-method name.

## Data schema

**No Flyway migration.** No new column, table, or index — the composite
index this query already relies on
(`ix_audit_events_actor_time (actor_user_id, occurred_at)`) predates this
feature and already backs the descending scan; only the repository query
method signature changes (`List` → `Page`, `Pageable` parameter added).

## API contracts

| Method | Path | Request | Response | Status |
|--------|------|---------|----------|--------|
| GET | `/api/staff/users/{userId}/audit-trail` | none (defaults: `page=0`, `size=20`) | `PageResponseDto<AuditEventDto>` | 200 |
| GET | `/api/staff/users/{userId}/audit-trail?page=<n>&size=<n>` | query params `page`, `size` | `PageResponseDto<AuditEventDto>`, page-sliced, `occurredAt` descending | 200 |
| GET | `/api/staff/users/{userId}/audit-trail?size=<n over 100>` | query param `size` | same envelope, `size` clamped to `100` | 200 |
| GET | `/api/staff/users/{userId}/audit-trail?page=-1` or `?size=0` | invalid query param | `TenancyErrorResponseDto("INVALID_PAGINATION")` (unchanged handler) | 400 |
| GET | `/api/staff/users/{userId}/audit-trail` (any of the above) | `userId` doesn't exist | `TenancyErrorResponseDto("USER_NOT_FOUND")` (unchanged, existing check) | 404 |
| GET | `/api/staff/users/{userId}/audit-trail` (any of the above) | caller lacks `AUDIT_TRAIL_VIEW` | `TenancyErrorResponseDto("PERMISSION_DENIED")` (unchanged) | 403 |

`PageResponseDto<AuditEventDto>` shape (identical envelope fields to the
`tenant-pagination-search` precedent, `content` items are
`AuditEventDto`'s existing per-row shape, untouched):

```json
{
  "content": [
    {
      "occurredAt": "2026-08-05T12:00:00Z",
      "action": "staff.permission.grant",
      "resourceType": "DirectGlobalPermissionGrant",
      "resourceId": "42",
      "tenantId": null,
      "outcome": "SUCCESS",
      "metadata": "..."
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 137,
  "totalPages": 7
}
```

## Dependencies

None. No new `pom.xml` dependency — `Page`/`Pageable`/`PageRequest` are
already part of `spring-data-jpa`, already in use by the
`tenant-pagination-search` precedent this PLAN reuses directly.

## Package/file structure

- `br.com.conectabyte.knowly.audit.AuditEventRepository` — replace
  `findTop500ByActorUserIdOrderByOccurredAtDesc(Long)` with
  `Page<AuditEvent> findByActorUserIdOrderByOccurredAtDesc(Long
  actorUserId, Pageable pageable)`.
- `br.com.conectabyte.knowly.tenancy.StaffService` — change
  `getAuditTrail` signature to `(Long userId, int page, int size):
  PageResponseDto<AuditEventDto>`; add `MAX_PAGE_SIZE = 100` constant
  and the same reject-then-clamp validation as
  `TenantService.listAllTenants`.
- `br.com.conectabyte.knowly.tenancy.StaffController` — change
  `auditTrail` to accept `page`/`size` `@RequestParam`s (defaults `"0"`/
  `"20"`) and return `ResponseEntity<PageResponseDto<AuditEventDto>>`.
- No changes to `PageResponseDto`, `InvalidPaginationException`, or
  `TenancyExceptionHandler` — all three are reused as-is.
- No changes to `AuditEventDto` — per-row shape is untouched.

## Testing strategy

- Repository-level test
  (`AuditEventRepositoryTest`, replacing today's
  `findTop500ByActorUserIdOrderByOccurredAtDesc`-focused test) covering:
  - a page beyond available rows returns empty `content` with correct
    `totalElements`/`totalPages`.
  - `Page.getContent()` size never exceeds the requested `Pageable`
    size directly from the repository call (proves query-level, not
    Java-side, bounding — same acceptance style as the precedent's NFR
    proof).
  - results ordered `occurredAt` descending across a multi-page seed.
- Service-level test (`StaffServiceTest`, extending existing
  `getAuditTrail` coverage) covering:
  - default `page=0`/`size=20` when not supplied (REQ-1).
  - `size` clamped to `100` when requesting more (REQ-1).
  - negative `page`, negative `size`, and `size=0` each throw
    `InvalidPaginationException` (REQ-1).
  - `UserNotFoundException` still thrown for a non-existent `userId`,
    unchanged (existing behavior, unaffected by pagination).
  - `AUDIT_TRAIL_VIEW`-gated `PermissionDeniedException` still thrown
    for a caller without it, unchanged (REQ-3, no authorization
    regression).
- Controller/integration test (`StaffAuditTrailIntegrationTest`,
  updating its existing assertions from a bare-array response to the
  `PageResponseDto` envelope) covering the full
  `GET /api/staff/users/{userId}/audit-trail[?page=][&size=]` contract
  end-to-end per the SPEC's acceptance criteria, including the `400`
  path and the envelope's exact field names, matching
  `TenantControllerIT`-style Testcontainers convention.
