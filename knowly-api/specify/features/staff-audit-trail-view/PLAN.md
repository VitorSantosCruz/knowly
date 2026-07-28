# PLAN — staff-audit-trail-view

> The how. Translates the SPEC into concrete technical decisions.
> References SPEC.md.

## Architectural decisions

- **New `GlobalPermission.AUDIT_TRAIL_VIEW` enum value**, appended to the
  existing `br.com.conectabyte.knowly.tenancy.GlobalPermission` enum. No
  migration — `GlobalPermission` is a plain Java enum with no DB-backed
  lookup table, same precedent as every prior addition this session
  (`STAFF_USER_VIEW`, `DASHBOARD_VIEW_GLOBAL`); confirmed again by reading
  `staff-user-listing`/`global-staff-dashboard-metrics`'s own PLANs.
- **Endpoint lives on the existing `StaffController`**, not a new
  controller — per the task brief, this is a `/api/staff/users/{userId}/
  ...` sub-resource, matching that controller's existing convention
  (`/users/{userId}/permissions`, `/users/{userId}/access-groups/
  {accessGroupId}`), unlike `global-staff-dashboard-metrics` which
  deliberately got its own `GlobalMetricsController` because that
  endpoint isn't a `{userId}` sub-resource at all. Confirmed by task
  brief, not re-litigated here.
- **New `StaffService.getAuditTrail(Long userId)` method**, not a new
  service class — this is a single read method operating on the same
  `User`/`GlobalPermission` domain `StaffService` already owns (it
  already has `getStaffUserDetail(Long userId)` doing the equivalent
  "look up one staff-adjacent thing about a `userId`" shape), so it's
  added alongside that method rather than spun into a dedicated
  `AuditTrailService`. Annotated `@Transactional(readOnly = true)`,
  `@RequiresGlobalPermission(GlobalPermission.AUDIT_TRAIL_VIEW)`,
  `@AuditLog(action = "staff.audit_trail.view", resourceType = "User")`
  per REQ-2/SPEC's Observability NFR — same three-annotation composition
  already proven safe in `GlobalMetricsService.globalMetrics()`
  (`readOnly` + `@AuditLog` is safe because `AuditEventWriter.write` runs
  in its own `REQUIRES_NEW` transaction).
  - `resourceId` for the `@AuditLog` annotation needs the path
    `{userId}` value: `AuditLogAspect` already supports resolving
    `resourceId` from a method parameter (confirmed by reading
    `AuditLogAspect`/existing `@AuditLog(resourceId = ...)` usages in
    `StaffService`, e.g. `grantPermission`/`revokePermission` which
    already key off a `userId`/`accessGroupId` parameter) — this method
    follows that same existing pattern, not a new mechanism.
  - **404 check happens first, inside `getAuditTrail`, before the
    500-row query runs**: `userRepository.findById(userId)
    .orElseThrow(UserNotFoundException::new)` — reusing
    `br.com.conectabyte.knowly.identity.exception.UserNotFoundException`
    as-is rather than inventing a new "user not found" exception. Why:
    it's already mapped to `404 Not Found` / `USER_NOT_FOUND` by the
    existing `IdentityExceptionHandler` (`@RestControllerAdvice` is
    global, not package-scoped — confirmed by reading it), and
    `UserProfileService.getProfile` already reuses the identical
    `orElseThrow(UserNotFoundException::new)` shape against the same
    `UserRepository`. No new exception class or handler entry needed.
  - **Permission check runs before the 404 check**, because
    `@RequiresGlobalPermission` is an aspect wrapping the whole method
    call (`GlobalPermissionAspect`, confirmed by reading it) — the
    existence check inside the method body can only run after the aspect
    has already let the call through. This matches REQ-6/REQ-7's ordering
    implicitly (a caller without the permission is rejected regardless of
    whether `{userId}` exists) and needs no special handling to achieve;
    it falls out of the existing aspect-around-method mechanism for free.
- **Defensive 500-row cap implemented as a new derived query method**,
  not a post-fetch `.subList(0, 500)` on the existing unbounded
  `findByActorUserIdOrderByOccurredAtDesc`: add
  `List<AuditEvent> findTop500ByActorUserIdOrderByOccurredAtDesc(Long
  actorUserId)` to `AuditEventRepository`. Why a new method over reusing
  the existing one: Spring Data's `Top<N>` keyword pushes the `LIMIT`
  into the generated SQL, so the DB — not the JVM — enforces the cap;
  truncating an already-fully-materialized unbounded `List<AuditEvent>`
  in application code is exactly the "unbounded response size / large
  single-shot exfiltration surface" NFR calls out as the risk to defend
  against, and doing the cap at the query layer avoids ever pulling more
  than 500 rows into memory in the first place. The existing
  `findByActorUserIdOrderByOccurredAtDesc` method is left untouched (no
  other caller depends on it being uncapped, confirmed by grep, but
  removing/repurposing it is out of scope for this feature).
- **New `AuditEventDto` record** (see API contracts) with a `static
  from(AuditEvent)` factory, mirroring `StaffUserSummaryDto`'s
  `from(User)` shape — placed in
  `br.com.conectabyte.knowly.tenancy.dto` alongside the other
  `Staff*Dto` types this endpoint's response sits next to, not in the
  `audit` package itself (the `audit` package holds the write-path
  primitives — `AuditEvent`/`AuditLog`/`AuditEventWriter` — and has no
  existing precedent of holding a read-side DTO; every other feature's
  audit-adjacent response DTO, i.e. this one, is the first, so it follows
  the calling controller's own package convention instead).
- **No new DB index added — one already exists and already covers this
  query.** See "Data schema" below.

## Data schema

**No Flyway migration for `GlobalPermission.AUDIT_TRAIL_VIEW` itself** —
same reasoning as `STAFF_USER_VIEW`/`DASHBOARD_VIEW_GLOBAL`: no DB-backed
lookup/enum table exists for `GlobalPermission` anywhere in this
codebase (verified again by reading `V14__create_global_permission_
tables.sql`, referenced in `staff-user-listing/PLAN.md`).

**Index on `audit_events.actor_user_id`: decided — no new migration
needed.** (`data-architect-dba` review, 2026-07-28.)

The SPEC's Performance NFR left this open with "none exists today,"
which was **incorrect** — re-reading `V5__create_audit_events_table.sql`
(the migration that created `audit_events`) directly shows:

```sql
CREATE INDEX ix_audit_events_tenant_time ON audit_events (tenant_id, occurred_at);
CREATE INDEX ix_audit_events_actor_time ON audit_events (actor_user_id, occurred_at);
```

`ix_audit_events_actor_time` is a composite `(actor_user_id,
occurred_at)` btree index that already covers exactly this feature's
access pattern: `WHERE actor_user_id = ? ORDER BY occurred_at DESC LIMIT
500`. Postgres satisfies an equality-on-leading-column /
order-by-second-column query straight from this index — including
`DESC`, since a btree index can be scanned backwards at no extra cost —
so `findTop500ByActorUserIdOrderByOccurredAtDesc` gets an index-only
`Index Scan Backward`, not a sequential scan, with zero new schema
changes. (The sibling `ix_audit_events_tenant_time` exists for the
symmetric tenant-scoped read path used elsewhere, e.g.
`global-staff-dashboard-metrics`; not relevant to this feature but
explains why both were added together in `V5`.)

**Decision: no `V<N>__*.sql` migration is added by this feature.** The
PLAN's earlier "flagged, not decided" framing (and TASKS.md's task 15
asking to raise this with the product owner) is now closed — this was a
research gap (an incomplete grep across migrations), not a genuine open
design question, and needed no Tier 3 escalation once corrected.

## API contracts

| Method | Path | Request | Response | Status |
|---|---|---|---|---|
| GET | `/api/staff/users/{userId}/audit-trail` | — | `List<AuditEventDto>`, reverse-chronological, capped at 500 rows | 200 OK (caller holds `AUDIT_TRAIL_VIEW` or is `STAFF_ADMIN`, `{userId}` exists — empty list if no events) |
| GET | `/api/staff/users/{userId}/audit-trail` | — | — (no body) | 403 Forbidden (`PERMISSION_DENIED`) — caller lacks `AUDIT_TRAIL_VIEW` and isn't `STAFF_ADMIN`, including a tenant `MEMBER`/`MEMBER_ADMIN` with no `GlobalRole` |
| GET | `/api/staff/users/{userId}/audit-trail` | — | — (no body) | 404 Not Found (`USER_NOT_FOUND`) — `{userId}` does not correspond to any existing `User` |

`AuditEventDto` shape:

```json
{
  "occurredAt": "2026-07-28T12:34:56Z",
  "action": "staff.permission.grant",
  "resourceType": "DirectGlobalPermissionGrant",
  "resourceId": "42",
  "tenantId": null,
  "outcome": "SUCCESS",
  "metadata": "{...}"
}
```

`tenantId` is nullable per REQ-1 (global/staff-level events carry no
tenant). `metadata` is passed through as already stored (already
`PiiMasker`-masked at write time where applicable, per SPEC's Data
exposure NFR) — this DTO applies no additional transformation to it.

## Dependencies

None. No new `pom.xml` dependency.

## Package/file structure

- `br.com.conectabyte.knowly.tenancy.GlobalPermission` — add
  `AUDIT_TRAIL_VIEW` value.
- `br.com.conectabyte.knowly.audit.AuditEventRepository` — add
  `findTop500ByActorUserIdOrderByOccurredAtDesc(Long actorUserId)`.
- `br.com.conectabyte.knowly.tenancy.StaffService` — add
  `getAuditTrail(Long userId)`.
- `br.com.conectabyte.knowly.tenancy.dto.AuditEventDto` — new record +
  `from(AuditEvent)` factory.
- `br.com.conectabyte.knowly.tenancy.StaffController` — add
  `GET /api/staff/users/{userId}/audit-trail` (`getAuditTrail`) method.

No changes to `AuditEvent`, `AuditEventWriter`, `AuditLogAspect`,
`GlobalPermissionAspect`, `IdentityExceptionHandler`, or
`TenancyExceptionHandler` — all reused as-is.

## Testing strategy

- **Repository test** (`AuditEventRepositoryTest` or equivalent,
  Testcontainers, matching existing suite convention) for
  `findTop500ByActorUserIdOrderByOccurredAtDesc`: covers
  reverse-chronological ordering, only rows for the given
  `actorUserId`, and the 500-row cap itself (seed >500 rows, assert
  exactly 500 returned, most-recent-first) — this is the one place the
  cap's correctness can be verified without going through the full HTTP
  stack.
- **Service test** (`StaffServiceTest`) covering:
  - `STAFF_ADMIN` calls `getAuditTrail` and succeeds without an explicit
    grant (REQ-2, `STAFF_ADMIN` bypass).
  - `STAFF` without `AUDIT_TRAIL_VIEW` is rejected
    (`PermissionDeniedException`) (REQ-6).
  - `STAFF` holding `AUDIT_TRAIL_VIEW` (via direct grant, matching
    existing `grantPermission` test setup convention) succeeds (REQ-5).
  - Nonexistent `userId` throws `UserNotFoundException` (REQ-8).
  - Target user with zero audit events returns an empty list, not an
    error (REQ-5).
  - `STAFF` holding `AUDIT_TRAIL_VIEW` can view a `STAFF`/`STAFF_ADMIN`
    target's trail (REQ-9) — no `enforceStaffCeiling` call blocks it,
    proven by asserting success against a `STAFF_ADMIN`-role target.
  - Cross-tenant assertion (REQ-4/acceptance criteria): seed
    `AuditEvent` rows with two different non-null `tenantId` values plus
    a null-`tenantId` row for the same `actorUserId`, assert all three
    come back in one call with no active tenant selected.
- **Controller/integration test** (Testcontainers,
  `StaffControllerIT`-style, matching `staff-user-listing`'s established
  pattern) covering the full `GET /api/staff/users/{userId}/audit-trail`
  contract end-to-end: 200 with correct field mapping and ordering, 403
  for an ungranted `STAFF` caller, 403 for a tenant `MEMBER`/
  `MEMBER_ADMIN` caller with no `GlobalRole` (REQ-7), 404 for a
  nonexistent `userId`, and that the call itself produces a new
  `AuditEvent` row (`staff.audit_trail.view`) — asserted via
  `AuditEventRepository.findByActorUserIdOrderByOccurredAtDesc` on the
  *caller's* id after the call.
- No pagination test needed (out of scope, confirmed by SPEC — only the
  500-row cap is tested, not true pagination).
