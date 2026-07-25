# PLAN — staff-user-provisioning

> The how. Translates the SPEC into concrete technical decisions.
> References SPEC.md.

## Architectural decisions

- New `GlobalPermission.STAFF_USER_CREATE` value, alongside the existing
  five from `staff-rbac-split` — no migration needed for the enum itself
  (stored as `VARCHAR(100)` already), just a new Java constant.
- New `StaffService.createStaffUser(String email)` method:
  - `@RequiresGlobalPermission(GlobalPermission.STAFF_USER_CREATE)` (REQ-4)
    — reuses the existing `GlobalPermissionAspect` from `staff-rbac-split`,
    no new aspect needed.
  - Rejects (REQ-2) if `userRepository.findByEmailIgnoreCase(email)` is
    already present — a new `StaffUserAlreadyExistsException` (mirrors
    the existing `tenancy.exception` package's shape, e.g.
    `TenantAccessDeniedException`), mapped to `409 Conflict` via
    `GlobalExceptionHandler` (or wherever existing tenancy exceptions are
    mapped — confirm the existing mapping mechanism during
    implementation and follow the same pattern, not a new one).
  - Creates `new User(email)` with `globalRole = GlobalRole.STAFF` (REQ-1,
    REQ-6) and saves it.
  - Calls `oneTimePasswordService.generateFor(user)` (existing method,
    unchanged) and `mailService.sendNewOneTimePassword(email, password)`
    (existing method, unchanged) — REQ-3, reusing exactly what
    `AuthController` already does elsewhere for this same mechanism, not
    a new code path.
  - `@AuditLog(action = "staff.user.create", resourceType = "User")`
    (REQ-5).
- New endpoint on the existing `StaffController`:
  `POST /api/staff/users` → `{ "email": "..." }` → `201 Created` with
  `StaffUserDetailDto` (already exists from `staff-rbac-split`, reused
  as-is — a freshly created user has empty direct/group/effective
  permission lists, which the existing DTO already represents fine).
- No change to `TenantService.addMember` — per SPEC's Context section,
  tenant provisioning already has no gap.

## Data schema

None — no new table, no new column. `GlobalPermission` is a Java enum
backed by `VARCHAR(100)`, already wide enough.

## API contracts

| Method | Path | Request | Response |
|---|---|---|---|
| POST | `/api/staff/users` | `CreateStaffUserRequestDto(String email)` | `201` `StaffUserDetailDto` |

`409 Conflict` (via the existing tenancy-exception-mapping mechanism) if
the email already exists.

## Dependencies

None new.

## Package/file structure

- `src/main/java/br/com/conectabyte/knowly/tenancy/GlobalPermission.java` (modify: add `STAFF_USER_CREATE`)
- `src/main/java/br/com/conectabyte/knowly/tenancy/exception/StaffUserAlreadyExistsException.java` (new)
- `src/main/java/br/com/conectabyte/knowly/tenancy/StaffService.java` (modify: add `createStaffUser`)
- `src/main/java/br/com/conectabyte/knowly/tenancy/StaffController.java` (modify: add `POST /api/staff/users`)
- `src/main/java/br/com/conectabyte/knowly/tenancy/dto/CreateStaffUserRequestDto.java` (new)

## Testing strategy

- Integration test (mirrors `StaffRbacIntegrationTest`'s style):
  - `STAFF_ADMIN` can create a staff user; the new user has
    `GlobalRole.STAFF` and no permissions (acceptance criterion 1).
  - A `STAFF` user granted `STAFF_USER_CREATE` can do the same; without
    it, rejected (criterion 2).
  - A `STAFF` user holding only `STAFF_PERMISSION_MANAGE` is rejected
    from creating a staff user — confirms the two permissions are truly
    independent, not that one implies the other (criterion 3, REQ-4).
  - Creating with an already-existing email (staff or tenant member) is
    rejected with `409` (criterion 4, REQ-2).
  - The new user can log in via one-time password (from the captured
    mail) and, separately, via ordinary login-code (criterion 5).
  - An `AuditEvent` with action `staff.user.create` is recorded
    (criterion 6, REQ-5).
