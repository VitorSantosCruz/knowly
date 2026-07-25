# TASKS — staff-user-provisioning

> Atomic, sequential, verifiable tasks derived from PLAN.md.
> Each task should be small enough for a single implementation iteration.

- [x] 1. `GlobalPermission`: add `STAFF_USER_CREATE`.
- [x] 2. `StaffUserAlreadyExistsException` +
      `TenancyExceptionHandler` entry (409, `STAFF_USER_ALREADY_EXISTS`).
- [x] 3. `CreateStaffUserRequestDto`.
- [x] 4. `StaffService.createStaffUser(String email)` per PLAN.md
      (permission check, existing-email rejection, `User` creation,
      one-time-password generation + email, audit log). Test: creates a
      `GlobalRole.STAFF` user with no permissions and sends the OTP
      email; rejects an existing email with the new exception.
- [x] 5. `POST /api/staff/users` on `StaffController`. Integration tests
      per PLAN.md's Testing strategy: `STAFF_ADMIN` succeeds; granted
      `STAFF` succeeds; ungranted `STAFF` rejected; `STAFF` holding only
      `STAFF_PERMISSION_MANAGE` rejected (permission independence);
      duplicate email rejected with 409; new user logs in via OTP and
      via login-code; audit event recorded.
- [x] 6. Run `./mvnw spotless:apply && ./mvnw verify` and confirm the
      full suite is green.
- [x] 7. Update `PROJECT_STATUS.md` (feature table + "Next up" pointing
      at the next confirmed roadmap item — navigation menus) and commit.
