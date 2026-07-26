# PLAN — staff-user-listing

> The how. Translates the SPEC into concrete technical decisions.
> References SPEC.md.

## Architectural decisions

- **New `GlobalPermission.STAFF_USER_VIEW` enum value**, appended to the
  existing `br.com.conectabyte.knowly.tenancy.GlobalPermission` enum
  (currently `TENANT_CREATE, TENANT_ACT_AS_ANY,
  TENANT_MEMBER_MANAGE_ANY, TENANT_ACCESS_GROUP_MANAGE_ANY,
  TENANT_PERMISSION_GRANT_MANAGE_ANY, STAFF_PERMISSION_MANAGE,
  STAFF_USER_CREATE`). Why: mirrors how `STAFF_USER_CREATE` itself was
  added in `staff-user-provisioning` — a plain additive enum value, no
  precedent of a distinct table listing valid permission values (see
  "Data schema" below for the verification).
- **Deliberately not gated by `STAFF_PERMISSION_MANAGE` or the
  `role-model-refinement` ceiling** — a separate, standalone permission,
  per SPEC's own "Decisions" section (already approved there, restated
  here for traceability). Why: listing exposes no more than
  `id/email/globalRole`, and every mutating action independently
  re-checks `enforceStaffCeiling`; folding this into an existing
  ceiling-adjacent permission would over-restrict a legitimate narrower
  use case (a support lead who should see the directory without gaining
  any grant/revoke capability).
- **New `StaffService.listStaffUsers(String emailFilter)` method**,
  annotated `@RequiresGlobalPermission(GlobalPermission.STAFF_USER_VIEW)`
  and `@Transactional(readOnly = true)` — no `@AuditLog`, consistent with
  SPEC's "Observability" NFR (read-only listing, no audit trail needed,
  matching `listAccessGroups`' existing precedent which also carries no
  `@AuditLog`). `STAFF_ADMIN` bypass is automatic and unconditional via
  `GlobalPermissionAspect.checkGlobalPermission`'s
  `tenantContext.isStaffAdmin()` short-circuit (verified by reading the
  aspect directly — the annotation alone is sufficient, no extra
  STAFF_ADMIN branch needed in the service method itself).
- **No `enforceStaffCeiling` call in `listStaffUsers`** — this is the one
  method in `StaffService` acting on `STAFF`/`STAFF_ADMIN` `User` rows
  that intentionally omits the ceiling check, per SPEC REQ-6. Every other
  method touching a staff `User` (create/detail/grant/revoke/assign)
  keeps its existing `enforceStaffCeiling` call unchanged — this PLAN
  does not touch those.
- **New repository query on `UserRepository`**: two explicit derived
  query methods rather than one method with a nullable/optional param,
  matching this codebase's existing convention of `findByEmailIgnoreCase`
  as a single-purpose derived method rather than a generic filter method:
  - `List<User> findByGlobalRoleIn(List<GlobalRole> globalRoles)`
  - `List<User> findByGlobalRoleInAndEmailContainingIgnoreCase(List<GlobalRole> globalRoles, String email)`

  `listStaffUsers` picks between them based on whether `emailFilter` is
  blank. Why two methods instead of one with a null-checked email: Spring
  Data derived queries don't cleanly express "optional `LIKE`" without a
  `@Query`/`Specification`, and this codebase has no existing
  `Specification`-based dynamic query precedent to extend — two explicit
  derived methods stays consistent with the simpler, explicit style
  already used throughout `UserRepository`/`DirectGlobalPermissionGrantRepository`/etc.
  If a third filter dimension is added later, this should be revisited
  toward a `Specification`, but that's premature for a single optional
  param.
- **New `StaffUserSummaryDto(Long id, String email, GlobalRole globalRole)`**
  record with a `static from(User)` factory, mirroring `MemberDto`'s
  exact shape (`membershipId, email, role` → `from(TenantMembership)`).
  Kept separate from `StaffUserDetailDto` (which additionally carries
  direct/group/effective permissions) — SPEC's NFR explicitly requires
  the list response to carry no permission/access-group detail.
- **New `GET /api/staff/users` controller method** with an optional
  `@RequestParam(required = false) String email`, added to the existing
  `StaffController` (which already owns `/api/staff/users` for
  `POST`-create). Delegates directly to
  `staffService.listStaffUsers(email)`, following the same thin-controller
  pattern as `listMembers`/`listAccessGroups`.

## Data schema

**No Flyway migration needed.** Verified by reading
`User.java`: `globalRole` is `@Enumerated(EnumType.STRING)` on a plain
`VARCHAR(20)` column with no `CHECK` constraint — Postgres has no notion
of the Java enum's value set at the DB level. Separately verified by
reading `V14__create_global_permission_tables.sql`
(`staff-rbac-split`'s migration): `direct_global_permission_grants` and
`global_access_group_permissions` both store `permission` as a bare
`VARCHAR(100)`, not a foreign key into any `global_permissions` lookup
table — unlike the tenant-side `Permission`, there is no seeded
lookup/enum table anywhere in this codebase for either `Permission` or
`GlobalPermission`. Adding `STAFF_USER_VIEW` is therefore a pure Java/code
change with zero schema impact, exactly as `STAFF_USER_CREATE` was when
it was added in `staff-user-provisioning`.

## API contracts

| Method | Path                | Request                          | Response                              | Status |
|--------|---------------------|-----------------------------------|----------------------------------------|--------|
| GET    | `/api/staff/users`  | none                               | `List<StaffUserSummaryDto>`            | 200 (all STAFF/STAFF_ADMIN rows) |
| GET    | `/api/staff/users?email=<substring>` | query param `email` (optional) | `List<StaffUserSummaryDto>` filtered case-insensitively | 200 |
| GET    | `/api/staff/users`  | caller is `STAFF` without `STAFF_USER_VIEW` | — (no body) | 403 (via existing `PermissionDeniedException` → global exception handler, same as every other `@RequiresGlobalPermission` rejection) |

`StaffUserSummaryDto` shape:

```json
{ "id": 1, "email": "someone@example.com", "globalRole": "STAFF" }
```

## Dependencies

None. No new `pom.xml` dependency.

## Package/file structure

- `br.com.conectabyte.knowly.tenancy.GlobalPermission` — add
  `STAFF_USER_VIEW` value.
- `br.com.conectabyte.knowly.auth.UserRepository` — add
  `findByGlobalRoleIn` and
  `findByGlobalRoleInAndEmailContainingIgnoreCase`.
- `br.com.conectabyte.knowly.tenancy.StaffService` — add
  `listStaffUsers(String emailFilter)`.
- `br.com.conectabyte.knowly.tenancy.dto.StaffUserSummaryDto` — new
  record + `from(User)` factory.
- `br.com.conectabyte.knowly.tenancy.StaffController` — add
  `GET /api/staff/users` (`listStaffUsers`) method.

## Testing strategy

- Unit-ish service test (`StaffServiceTest` or equivalent, matching
  existing suite convention) covering:
  - `STAFF_ADMIN` calls `listStaffUsers` and gets all STAFF/STAFF_ADMIN
    rows regardless of grants (REQ-1, REQ-3).
  - Email substring filter, case-insensitive (REQ-2).
  - `STAFF` without `STAFF_USER_VIEW` is rejected
    (`PermissionDeniedException`) (REQ-5).
  - `STAFF` holding `STAFF_USER_VIEW` succeeds and sees other
    `STAFF`/`STAFF_ADMIN` rows (REQ-4).
  - That same `STAFF_USER_VIEW`-holding user is still rejected by an
    existing ceiling-protected method (e.g. `grantPermission` against a
    `STAFF` target) — proving REQ-6's independence claim, reusing
    existing `enforceStaffCeiling` test patterns already in the suite.
- Integration/controller test (Testcontainers, matching
  `StaffControllerIT`-style convention if one exists, else the
  established `@SpringBootTest` + Testcontainers pattern used elsewhere)
  covering the full `GET /api/staff/users[?email=]` contract end-to-end,
  including the 403 path.
- No pagination test needed (out of scope, confirmed by SPEC).
