# TASKS — paginated-audit-trail

> Atomic, sequential, verifiable tasks derived from PLAN.md.
> Each task should be small enough for a single implementation iteration.

## Repository layer

- [x] 1. In `AuditEventRepositoryTest`, replace the existing
      `findTop500ByActorUserIdOrderByOccurredAtDesc`-focused test with a
      new test asserting `findByActorUserIdOrderByOccurredAtDesc(Long,
      Pageable)` returns results ordered `occurredAt` descending across a
      multi-page seed (Red state — method doesn't exist yet).
- [x] 2. In `AuditEventRepository`, remove
      `findTop500ByActorUserIdOrderByOccurredAtDesc(Long)` and add
      `Page<AuditEvent> findByActorUserIdOrderByOccurredAtDesc(Long
      actorUserId, Pageable pageable)` — minimum change for task 1's test
      to pass (Green).
- [x] 3. Add a test to `AuditEventRepositoryTest` asserting a page beyond
      available rows returns empty `content` with correct
      `totalElements`/`totalPages` (Red — confirm behavior is exercised;
      expected to already pass against task 2's method, but must be
      written and run before being treated as covered).
- [x] 4. Add a test to `AuditEventRepositoryTest` asserting
      `Page.getContent()` size never exceeds the requested `Pageable`
      size directly from the repository call, proving query-level (not
      Java-side) bounding (Red — write and run; expected to already pass
      against task 2's method).

## Service layer

- [x] 5. In `StaffServiceTest`, write a test asserting
      `StaffService.getAuditTrail(userId, page, size)` returns a
      `PageResponseDto<AuditEventDto>` with defaults `page=0`/`size=20`
      when not supplied (REQ-1) (Red — signature doesn't exist yet).
- [x] 6. Change `StaffService.getAuditTrail` signature to `(Long userId,
      int page, int size): PageResponseDto<AuditEventDto>`, building a
      `Pageable` via `PageRequest.of(page, size, Sort.by("occurredAt")
      .descending())` and calling the new repository method, mapping the
      result through `AuditEventDto::from` into a `PageResponseDto` —
      minimum code for task 5's test to pass (Green). Update
      `StaffController.auditTrail`'s call site so the project still
      compiles.
- [x] 7. In `StaffServiceTest`, write a test asserting `size` is clamped
      to `100` (`MAX_PAGE_SIZE`) when a caller requests more (REQ-1)
      (Red).
- [x] 8. Add the `MAX_PAGE_SIZE = 100` constant and reject-then-clamp
      validation to `StaffService.getAuditTrail`, copied from
      `TenantService.listAllTenants`'s exact shape — minimum code for
      task 7's test to pass (Green).
- [x] 9. In `StaffServiceTest`, write tests asserting negative `page`,
      negative `size`, and `size=0` each throw
      `InvalidPaginationException` (REQ-1) (Red).
- [x] 10. Add the `page < 0 || size <= 0` rejection to
       `StaffService.getAuditTrail`, checked before the clamp — minimum
       code for task 9's tests to pass (Green).
- [x] 11. In `StaffServiceTest`, write/confirm a test asserting
       `UserNotFoundException` is still thrown for a non-existent
       `userId`, unchanged by the pagination signature change (Red if
       not already covered by the updated signature; otherwise run and
       confirm it still passes against current code before moving on).
- [x] 12. If task 11 exposed a gap, fix `StaffService.getAuditTrail` so
       the existence check still runs ahead of pagination logic (Green).
       If no gap was found, no code change — record that in the task as
       a pass-through.
- [x] 13. In `StaffServiceTest`, write/confirm a test asserting
       `PermissionDeniedException` is still thrown via the
       `AUDIT_TRAIL_VIEW`-gated `@RequiresGlobalPermission` for a caller
       without it (REQ-3), unchanged by pagination parameters (Red if
       not already covered; otherwise run and confirm it still passes).
- [x] 14. If task 13 exposed a gap, fix the permission gate wiring
       (Green). If no gap was found, no code change — record that in the
       task as a pass-through.

## Controller / integration layer

- [x] 15. In `StaffAuditTrailIntegrationTest`, update the existing
       assertions from a bare-array response to the `PageResponseDto`
       envelope shape (`content`, `page`, `size`, `totalElements`,
       `totalPages`) for `GET
       /api/staff/users/{userId}/audit-trail?page=0&size=20` (Red —
       controller doesn't return the envelope yet).
- [x] 16. Change `StaffController.auditTrail` to accept
       `@RequestParam(defaultValue = "0") int page` and
       `@RequestParam(defaultValue = "20") int size`, returning
       `ResponseEntity<PageResponseDto<AuditEventDto>>` — minimum code
       for task 15's test to pass (Green).
- [x] 17. In `StaffAuditTrailIntegrationTest`, write a test asserting
       `?size=<n over 100>` returns the envelope with `size` clamped to
       `100` (REQ-1) (Red if not already covered end-to-end; otherwise
       confirm it passes against task 16's code).
- [x] 18. In `StaffAuditTrailIntegrationTest`, write a test asserting
       `?page=-1` and `?size=0` each return `400` with
       `TenancyErrorResponseDto("INVALID_PAGINATION")` (REQ-1) (Red if
       not already covered; otherwise confirm it passes — handler is
       reused unchanged per PLAN).
- [x] 19. In `StaffAuditTrailIntegrationTest`, write/confirm a test
       asserting results are ordered `occurredAt` descending end-to-end
       (REQ-2) (Red if not already covered; otherwise confirm it
       passes).
- [x] 20. In `StaffAuditTrailIntegrationTest`, write/confirm the existing
       `404 USER_NOT_FOUND` and `403 PERMISSION_DENIED` assertions still
       pass unchanged against the new envelope-returning endpoint
       (regression check, no new behavior expected).

## Final verification

- [x] 21. Run `./mvnw spotless:apply` then the full `./mvnw verify` and
       confirm the suite is green.
- [x] 22. Update `PLAN.md`/`CLAUDE.md`/`PROJECT_STATUS.md` if any
       decision changed during implementation (e.g. an unexpected gap
       found in tasks 11–14).
