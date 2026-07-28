# TASKS — staff-audit-trail-view

> Atomic, sequential, verifiable tasks derived from PLAN.md.
> Each task should be small enough for a single implementation iteration.

## GlobalPermission

- [x] 1. Add `AUDIT_TRAIL_VIEW` to `GlobalPermission` enum. (No test —
      pure enum addition, no behavior yet.)

## AuditEventRepository — 500-row cap

- [x] 2. Write a repository test (Red) for
      `findTop500ByActorUserIdOrderByOccurredAtDesc`: seed >500
      `AuditEvent` rows for one `actorUserId` plus rows for a different
      `actorUserId`, assert exactly 500 rows returned, most-recent-first,
      none belonging to the other actor.
- [x] 3. Implement `findTop500ByActorUserIdOrderByOccurredAtDesc` on
      `AuditEventRepository` (Green).

## StaffService.getAuditTrail

- [x] 4. Write `StaffServiceTest` case (Red) for REQ-8: a nonexistent
      `userId` throws `UserNotFoundException`.
- [x] 5. Write `StaffServiceTest` case (Red) for REQ-6: a `STAFF` caller
      without `AUDIT_TRAIL_VIEW` calling `getAuditTrail` throws
      `PermissionDeniedException`.
- [x] 6. Write `StaffServiceTest` case (Red) for REQ-2: `STAFF_ADMIN`
      calls `getAuditTrail` and succeeds without an explicit grant.
- [x] 7. Write `StaffServiceTest` case (Red) for REQ-5: a `STAFF` caller
      holding `AUDIT_TRAIL_VIEW` (direct grant, matching existing
      `grantPermission` test setup convention) succeeds and gets back
      the target's events, reverse-chronological, mapped to
      `occurredAt/action/resourceType/resourceId/tenantId/outcome/
      metadata`; also cover the target-has-zero-events case returning an
      empty list, not an error.
- [x] 8. Write `StaffServiceTest` case (Red) for the cross-tenant
      acceptance criterion (REQ-4): seed `AuditEvent` rows for the same
      `actorUserId` with two distinct non-null `tenantId` values plus one
      null-`tenantId` row, assert all three come back in a single call
      with no active tenant selected by the caller.
- [x] 9. Write `StaffServiceTest` case (Red) for REQ-9: a `STAFF` caller
      holding `AUDIT_TRAIL_VIEW` can call `getAuditTrail` against a
      `STAFF`/`STAFF_ADMIN`-role target and succeeds (no
      `enforceStaffCeiling` block on this read-only path).
- [x] 10. Implement `StaffService.getAuditTrail(Long userId)`
      (`@Transactional(readOnly = true)`,
      `@RequiresGlobalPermission(GlobalPermission.AUDIT_TRAIL_VIEW)`,
      `@AuditLog(action = "staff.audit_trail.view", resourceType =
      "User", resourceId = ...)`, `userRepository.findById(userId)
      .orElseThrow(UserNotFoundException::new)` before querying, then
      `auditEventRepository.findTop500ByActorUserIdOrderByOccurredAtDesc(userId)`
      mapped to `AuditEventDto`) to make tasks 4–9 green by inspection,
      then run `./mvnw test -Dtest=StaffServiceTest` to confirm.

## DTO + controller

- [x] 11a. Add `AuditEventDto(Instant occurredAt, String action, String
      resourceType, String resourceId, Long tenantId, AuditOutcome
      outcome, String metadata)` record with `static from(AuditEvent)`,
      in `br.com.conectabyte.knowly.tenancy.dto`. (No standalone test —
      a plain mapping record, exercised indirectly by the controller
      test below.)
- [x] 11b. Write a controller/integration test (Red,
      `StaffControllerIT`-style Testcontainers pattern) covering the
      full `GET /api/staff/users/{userId}/audit-trail` contract: 200
      with correctly ordered/mapped body for a `STAFF_ADMIN` caller, 200
      for a `STAFF` caller holding `AUDIT_TRAIL_VIEW`, 403 for an
      ungranted `STAFF` caller, 403 for a tenant `MEMBER`/`MEMBER_ADMIN`
      caller with no `GlobalRole` (REQ-7), 404 for a nonexistent
      `userId` — matching this feature's SPEC acceptance criteria
      item-for-item.
- [x] 11c. Write a controller/integration test (Red) asserting the call
      itself produces a new `AuditEvent` row for the *caller* (action
      `staff.audit_trail.view`, `resourceType = "User"`, `resourceId =
      {userId}`) — query via
      `AuditEventRepository.findByActorUserIdOrderByOccurredAtDesc` on
      the caller's id after the call.
- [x] 11d. Implement `GET /api/staff/users/{userId}/audit-trail` on
      `StaffController` (`@PathVariable Long userId`, delegates to
      `staffService.getAuditTrail(userId)`, returns
      `List<AuditEventDto>`) to make tasks 11b/11c green.
- [x] 12. Run `./mvnw test -Dtest=StaffControllerIT` (or the actual
      integration test class name used) and confirm tasks 11b/11c pass.

**Implementation note (deviation from the letter of tasks 4-9/11b/11c,
not the intent):** this codebase has no precedent of testing
`StaffService`'s `@RequiresGlobalPermission`/`@AuditLog`-annotated
methods with mocked unit tests — every existing `StaffService` method is
exercised exclusively through `MockMvcTester` integration tests (see
`StaffServiceCeilingIntegrationTest`, `StaffUserListingIntegrationTest`),
because the permission/audit aspects only fire inside a real Spring
context. Rather than inventing a new, unprecedented pure-unit test style
for this one feature, tasks 4-9 and 11b/11c were implemented as a single
`StaffAuditTrailIntegrationTest` (`src/test/java/br/com/conectabyte/
knowly/tenancy/StaffAuditTrailIntegrationTest.java`) covering every
REQ/acceptance criterion those tasks describe end-to-end over HTTP —
same coverage, same TDAD Red/Green discipline, existing convention.

## Final pass

- [x] 13. Run `./mvnw spotless:apply` then `./mvnw verify` for the full
      suite (this feature's tests plus every pre-existing test) and fix
      any regression surfaced.
- [x] 14. Hand off to `qa-test-automation` and `appsec` for review of
      this feature — in particular re-confirming REQ-4's cross-tenant
      row-level exposure is being treated as documented/approved (per
      SPEC's "Tier 3 flag" section) and not silently "fixed" back to
      tenant-scoped filtering. **Resolved (2026-07-28):** `qa-test-
      automation` independently ran the full suite (exit 0) and confirmed
      every REQ/acceptance criterion is covered by a real test, including
      the cross-tenant (REQ-4), 500-cap, STAFF-ceiling-not-applied
      (REQ-9), and self-audit cases — no gaps found. `appsec` re-reviewed
      the implementation against the SPEC's confirmed REQ-4 exposure,
      confirmed permission gating, DTO field set, the DB-enforced
      `Top500` cap, and the self-audit `@AuditLog` call all match what
      was approved with no new issue — verdict "ship it," no blocking
      findings.
- [x] 15. ~~Raise the "no index on `audit_events.actor_user_id`" flag~~
      — resolved (`data-architect-dba` review, 2026-07-28): the
      composite index `ix_audit_events_actor_time (actor_user_id,
      occurred_at)` already exists, created in
      `V5__create_audit_events_table.sql`, and already covers this
      feature's `WHERE actor_user_id = ? ORDER BY occurred_at DESC`
      query via a backward index scan. No new migration needed; see
      PLAN.md's "Data schema" section for the corrected reasoning. No
      escalation to the product owner required — this was a research
      gap, not an open design question.
- [x] 16. Commit the completed, verified work (Conventional Commits),
      once — and only once — task 13's full suite is green and tasks 14's
      reviews are addressed.
