# TASKS — staff-user-listing

> Atomic, sequential, verifiable tasks derived from PLAN.md.
> Each task should be small enough for a single implementation iteration.

## Preamble — test execution deferred (ONE-TIME exception, not standing policy)

**This is a one-time, dated exception the human product owner gave
directly, in conversation, on 2026-07-26 — it is NOT a change to this
project's standing process** (`constitution.md`'s TDAD Red/Green cycle,
and the "commit each completed task as you go" rule in the root
`CLAUDE.md`/`PROJECT_STATUS.md` conventions, remain the project's actual
policy going forward). It applies only to this specific batch of
backlog features implemented in this one session and must not be read
by any future session/agent as license to skip test execution or batch
commits by default.

For this session only: every task below that touches behavior still
requires a test written **first** (Red before Green) — test-first
authorship is mandatory and not skipped. However, **backend-engineer
must NOT run `./mvnw test`, `./mvnw verify`, or any other test-execution
command while working through these tasks.** Write the test, write the
minimal code to make it pass by inspection/reasoning, move on. All test
execution — this feature's suite and the full project suite — happens
in a single final pass (task 11) once all currently planned backlog work
(not just this feature) is done. Do not skip writing the tests
themselves; only the *running* of them is deferred, and only for this
2026-07-26 batch.

- [x] 0. Acknowledge the above: write tests for every task first, but do
      not execute any test command until task 11.

## GlobalPermission + repository

- [x] 1. Add `STAFF_USER_VIEW` to `GlobalPermission` enum. (No test —
      pure enum addition, no behavior yet.)
- [x] 2. Write repository-level test(s) for `findByGlobalRoleIn` and
      `findByGlobalRoleInAndEmailContainingIgnoreCase` on
      `UserRepository` (Red) — cover: returns only matching roles,
      email substring match is case-insensitive, empty result when no
      match.
- [x] 3. Implement `findByGlobalRoleIn` and
      `findByGlobalRoleInAndEmailContainingIgnoreCase` on
      `UserRepository` (Green, by inspection — do not run tests yet).

## StaffService.listStaffUsers

- [x] 4. Write `StaffServiceTest` cases (Red) for REQ-1/REQ-3: a
      `STAFF_ADMIN` caller gets every `STAFF`/`STAFF_ADMIN` user back
      regardless of any grant.
- [x] 5. Write `StaffServiceTest` case (Red) for REQ-2: email substring
      filter, case-insensitive, applied on top of the role filter.
- [x] 6. Write `StaffServiceTest` case (Red) for REQ-5: a `STAFF` caller
      with zero grants calling `listStaffUsers` throws
      `PermissionDeniedException`.
- [x] 7. Write `StaffServiceTest` case (Red) for REQ-4: a `STAFF` caller
      holding `STAFF_USER_VIEW` (via direct grant, matching existing
      `grantPermission` test setup convention) succeeds and sees
      `STAFF`/`STAFF_ADMIN` rows including ones other than itself.
- [x] 8. Write `StaffServiceTest` case (Red) for REQ-6: that same
      `STAFF_USER_VIEW`-holding `STAFF` caller is still rejected by an
      existing ceiling-protected method (e.g. `grantPermission` against
      a `STAFF`/`STAFF_ADMIN` target) — proves listing and management
      authorization are independent.
- [x] 9. Implement `StaffService.listStaffUsers(String emailFilter)`
      (`@Transactional(readOnly = true)`,
      `@RequiresGlobalPermission(GlobalPermission.STAFF_USER_VIEW)`, no
      `@AuditLog`, no `enforceStaffCeiling` call) to make tasks 4–8
      green by inspection — do not run tests yet.

## DTO + controller

- [x] 10a. Add `StaffUserSummaryDto(Long id, String email, GlobalRole
      globalRole)` record with `static from(User)`, mirroring
      `MemberDto`. (No standalone test — a plain mapping record,
      exercised indirectly by the controller test below.)
- [x] 10b. Write a controller/integration test (Red) covering the full
      `GET /api/staff/users` contract: unpaginated list for
      `STAFF_ADMIN`, `?email=` substring filter, 403 for an ungranted
      `STAFF` caller, 200 with visible rows for a `STAFF_USER_VIEW`-
      granted `STAFF` caller — matching this feature's SPEC acceptance
      criteria list item-for-item.
- [x] 10c. Implement `GET /api/staff/users` on `StaffController`
      (optional `email` `@RequestParam`, delegates to
      `staffService.listStaffUsers(email)`, maps to
      `List<StaffUserSummaryDto>`) to make task 10b green by inspection
      — do not run tests yet.

## Final pass (only once all currently planned backlog work, not just
## this feature, is done)

- [ ] 11. Run `./mvnw spotless:apply` then `./mvnw verify` for the full
      suite (this feature's tests plus every pre-existing test) and fix
      any regression surfaced.
- [ ] 12. Hand off to `qa-test-automation` and `appsec` for review of
      this feature during that same final pass — not before, and not
      as a substitute for task 11's own green run.
- [ ] 13. Commit the completed, verified work (Conventional Commits),
      once — and only once — task 11's full suite is green and tasks 12's
      reviews are addressed.
