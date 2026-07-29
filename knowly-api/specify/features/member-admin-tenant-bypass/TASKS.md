# TASKS — member-admin-tenant-bypass

> Atomic, sequential, verifiable tasks derived from PLAN.md.
> Each task should be small enough for a single implementation iteration.

## REQ-1(a)/REQ-2/REQ-3/REQ-6 — `PermissionAspect` bypass

- [ ] 1. Write `PermissionAspectTest`: `MEMBER_ADMIN` (active membership,
      active tenant) with zero explicit `Permission`/`AccessGroup` grants
      proceeds through `checkPermission` for a `@RequiresPermission`-gated
      action (Red — fails against current code, which has no bypass
      branch).
- [ ] 2. Add the `membership.getRole() == MembershipRole.MEMBER_ADMIN`
      bypass branch to `PermissionAspect.checkPermission`, placed right
      after `requireActiveMembership()`, before the
      `permissionService.hasPermission` check (Green).
- [ ] 3. Write `PermissionAspectTest`: same `MEMBER_ADMIN` user, active
      tenant switched to a tenant where they hold a plain `MEMBER`
      membership (or none) → rejected on the identical action, same as a
      non-admin with no grant (Red — should already pass once task 2 is
      scoped correctly via `requireActiveMembership()`'s existing
      active-tenant lookup; if it fails, the bypass was placed/scoped
      wrong).
- [ ] 4. Confirm task 3 passes without further code change (Green) — if
      it doesn't, fix the bypass scoping (should never need a second
      lookup or a client-supplied tenant id).
- [ ] 5. Write `PermissionAspectTest`: `MEMBER_ADMIN` role present but the
      only matching `TenantMembership` has `isActive() == false` → no
      bypass, falls through to the ordinary `hasPermission` check and is
      rejected absent an explicit grant (Red, since
      `requireActiveMembership()` already filters `isActive()`, this
      should already pass — confirms REQ-6 rather than introducing new
      code).
- [ ] 6. Confirm task 5 passes (Green, no code change expected).
- [ ] 7. Run existing `PermissionAspectTest` `STAFF_ADMIN` case(s) and
      confirm unchanged (regression check, acceptance criterion 7).

## REQ-4/REQ-5 — `TenantService` self-escalation guard

- [ ] 8. Write `TenantServiceTest`: `addMember(actor, tenantId,
      actor's-own-email, newRole)` where actor is `MEMBER_ADMIN` of
      `tenantId` → expect `PermissionDeniedException` (Red — currently
      succeeds, no self-escalation guard exists).
- [ ] 9. Add `private void requireNotSelfTarget(User actor, Long
      targetUserId)` to `TenantService`; call it from `addMember`
      immediately after resolving `existingUser`, passing
      `existingUser.map(User::getId).orElse(null)` (Green for task 8).
- [ ] 10. Write `TenantServiceTest`: `grantPermission(actor, tenantId,
      actor's-own-membershipId, permission)` where actor is
      `MEMBER_ADMIN` of `tenantId` → expect `PermissionDeniedException`
      (Red).
- [ ] 11. Call `requireNotSelfTarget(actor, membership.getUser().getId())`
      in `grantPermission`, right after the existing
      `tenantMembershipRepository.findById(membershipId)` fetch, before
      the grant mutation (Green for task 10).
- [ ] 12. Repeat tasks 10-11's Red/Green pair for `revokePermission`
      (same self-membershipId shape).
- [ ] 13. Repeat tasks 10-11's Red/Green pair for `assignAccessGroup`.
- [ ] 14. Repeat tasks 10-11's Red/Green pair for `unassignAccessGroup`.
- [ ] 15. Write `TenantServiceTest`: each of the five methods
      (`addMember`/`grantPermission`/`revokePermission`/
      `assignAccessGroup`/`unassignAccessGroup`) called by a
      `MEMBER_ADMIN` actor targeting a *different* user/membership in the
      same tenant → succeeds (Red only if task 9/11/etc. over-scoped the
      guard to reject all targets, not just self; otherwise already
      Green — confirms the bypass still applies to on-others actions,
      acceptance criterion 5).
- [ ] 16. Write a test (extend `AuditLogAspectTest` or add a
      `TenantService`-level test asserting on a mocked/spied
      `AuditEventWriter`) confirming that a self-escalation rejection
      from task 8/10/12/13/14 produces an `AuditEvent` with
      `outcome = DENIED`, the correct `action` (matching the method's
      existing `@AuditLog(action = "...")`), and the actor's user id
      (Red only if `@AuditLog` wiring is somehow bypassed; expected
      Green immediately, since `AuditLogAspect` already wraps these
      methods and already catches `PermissionDeniedException` — this
      task is verification, not new production code, per REQ-5).
- [ ] 17. Confirm task 16 passes with no production code change (Green).

## Regression and wrap-up

- [ ] 18. Run existing `UserProfileServiceTest` and
      `ProfileEditRequestServiceTest` unmodified; confirm still green
      (acceptance criterion 8 — these services' hardcoded `MEMBER_ADMIN`
      checks are untouched and must not be affected by the
      `PermissionAspect` bypass).
- [ ] 19. Run the full `./mvnw spotless:apply` then `./mvnw verify` and
      confirm the whole suite is green.
- [ ] 20. Update `PROJECT_STATUS.md` to reflect this feature's
      completion; note in `PLAN.md` (already done above) the two
      PLAN-time corrections to the SPEC's factual premises (REQ-1(b)
      already implemented; `TenantService`'s `tenantId` being
      path-variable-sourced is not a REQ-2/REQ-3 violation) in case a
      future SPEC amendment is warranted.
</content>
