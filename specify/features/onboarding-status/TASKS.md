# TASKS — Onboarding status

> Atomic, sequential, verifiable tasks derived from PLAN.md.
> Each "Implement" task ends with `./mvnw spotless:apply && ./mvnw verify`
> and a small Conventional Commit before moving on.

- [ ] 1. `V6__add_onboarding_completed_at_to_users.sql` — add the column
      to `users` and `users_aud`.
- [ ] 2. Add `onboardingCompletedAt` to `User` (Green — no test needed,
      it's a plain mapped field; covered by task 3's test).
- [ ] 3. Test: a freshly created user's `GET
      /api/users/me/onboarding-status` returns `{ completed: false }`
      (Red).
- [ ] 4. Implement `OnboardingController#getStatus` (Green).
- [ ] 5. Test: `POST /api/users/me/onboarding-complete` then `GET
      .../onboarding-status` returns `{ completed: true }` (Red).
- [ ] 6. Implement `OnboardingController#markComplete` (Green).
- [ ] 7. Test: calling mark-complete twice in a row does not error
      (REQ-3) (Red — should already pass given task 6's implementation
      has no branching; write it anyway as a regression guard).
- [ ] 8. (Green — no code change expected.)
- [ ] 9. Test: both endpoints return `401` when the caller isn't
      authenticated (Red).
- [ ] 10. (Green — should already pass via the existing
       `anyRequest().authenticated()` rule; write it anyway as a
       regression guard against a future change to `SecurityConfig`.)
- [ ] 11. Test: a user with two active tenant memberships still has one
       onboarding status, not one per tenant (REQ-4) (Red — should
       already pass since the field lives on `User`; write it anyway to
       make the guarantee explicit).
- [ ] 12. (Green — no code change expected.)
- [ ] 13. Test: an `AuditEvent` is recorded for both endpoints (REQ-5)
       (Red).
- [ ] 14. Implement `@AuditLog` on both endpoints (Green).
- [ ] 15. Run the full `./mvnw spotless:apply && ./mvnw verify` and
       confirm the entire suite (auth + tenancy + onboarding-status) is
       green.
- [ ] 16. Update SPEC.md's acceptance-criteria checkboxes to reflect
       what's now verified by tests.
