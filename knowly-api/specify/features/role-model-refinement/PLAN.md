# PLAN — role-model-refinement

> The how. Translates the SPEC into concrete technical decisions.
> References SPEC.md.

## Architectural decisions

- **Enum rename is a pure find/replace, no schema-type change.**
  `MembershipRole.ADMIN` → `MembershipRole.MEMBER_ADMIN`. Confirmed by
  reading `TenantMembership.java:57-59`: `role` is
  `@Enumerated(EnumType.STRING)` over `@Column(length = 20)` — stored as
  the literal string, not an ordinal or a Postgres enum type — so
  renaming the Java constant plus a data-migrating `UPDATE` is
  sufficient; no `ALTER TYPE`/`ALTER COLUMN` needed. `MEMBER_ADMIN` (12
  chars) fits the existing `VARCHAR(20)` with room to spare.
- **All call sites are in `knowly-api/src/main` and `src/test` only** —
  grepped `MembershipRole.ADMIN` across both trees (frontend TypeScript
  DTOs are out of scope for a backend-only SPEC, and this SPEC doesn't
  touch them):
  - `main`: `TenantService.java:146` (`new TenantMembership(admin,
    tenant, MembershipRole.ADMIN)` in tenant creation),
    `TenantService.java:411` (`.filter(membership ->
    membership.getRole() == MembershipRole.ADMIN)` — locating the
    tenant's admin membership for e.g. transfer/lookup logic).
  - `test`: 8 usages in `TenantManagementIntegrationTest.java` (fixture
    setup + one assertion). All become `MEMBER_ADMIN` with no behavior
    change — same fixture shape, new name.
  - No other file references `MembershipRole.ADMIN` specifically (4 DTOs
    — `AddMemberRequestDto`, `MemberDetailDto`, `MemberDto`,
    `TenantMembershipDto` — reference the `MembershipRole` *type*, not
    the `ADMIN` constant, so they need no code change, only their
    serialized values change at runtime, see the contract note below).
- **REQ-4 ceiling lives inside `StaffService`, as a private helper
  called after each method's existing target lookup** — per SPEC's own
  Decision 4 and this being the natural seam: `PermissionAspect`/
  `GlobalPermissionAspect` only see the join point's raw arguments (a
  `Long userId`), not the resolved target `User`/its `GlobalRole` — they
  would need to duplicate `requireUser`'s lookup and couldn't express
  "STAFF_ADMIN-only regardless of grant" as a `@RequiresGlobalPermission`
  value without inventing a permission nobody can hold (rejected by
  SPEC Decision 1). Doing it in `StaffService` itself, right after the
  target is already loaded, is the smallest correct change and matches
  where `requireUser`/`requireAccessGroup` already live.
- **The ceiling is enforced via a new private helper,
  `enforceStaffCeiling(GlobalRole targetGlobalRole)`**, resolving the
  *caller's* `GlobalRole` from `SecurityContextHolder` (same pattern
  `GlobalPermissionAspect` already uses: `getAuthentication().getName()`
  → `userRepository.findByEmailIgnoreCase(...)`), then:
  ```java
  private void enforceStaffCeiling(GlobalRole targetGlobalRole) {
      String email = SecurityContextHolder.getContext().getAuthentication().getName();
      User actor = userRepository.findByEmailIgnoreCase(email)
              .orElseThrow(PermissionDeniedException::new);

      if (actor.getGlobalRole() == GlobalRole.STAFF
              && (targetGlobalRole == GlobalRole.STAFF
                      || targetGlobalRole == GlobalRole.STAFF_ADMIN)) {
          throw new PermissionDeniedException();
      }
  }
  ```
  This never inspects any `GlobalPermission` grant — it's unconditional
  code, satisfying the SPEC's non-functional requirement that the
  ceiling can't be granted away (Tier 1/2 call, not a new tradeoff: this
  is exactly the existing `PermissionDeniedException`/rejection-shape
  precedent `GlobalPermissionAspect` already uses, just invoked from a
  different call site).
- **`PermissionDeniedException` thrown here is caught by the *existing*
  `AuditLogAspect`**, which already wraps every `@AuditLog`-annotated
  method in a try/catch that records `AuditOutcome.DENIED` on exactly
  this exception type (`AuditLogAspect.java:52-53`) — REQ-8 is satisfied
  for free on every method that already carries `@AuditLog`, with one
  gap: **`getStaffUserDetail` currently has no `@AuditLog` at all**
  (only `@RequiresGlobalPermission`). Since REQ-8 explicitly requires an
  audit event for a rejected "view another STAFF/STAFF_ADMIN user's
  permission detail" attempt, `getStaffUserDetail` gets a new
  `@AuditLog(action = "staff.user.detail.view", resourceType = "User",
  resourceIdExpression = "#userId")` — the smallest change that closes
  the gap, reusing the existing mechanism rather than a bespoke audit
  write.
- **Call-site placement**: `enforceStaffCeiling(...)` is called
  immediately after `requireUser(userId)` resolves the target `User`, in
  each of: `getStaffUserDetail`, `grantPermission`, `revokePermission`,
  `assignAccessGroup`, `unassignAccessGroup` — passing
  `user.getGlobalRole()`.
- **`createStaffUser` is the one exception**: there is no pre-existing
  target user to resolve (the whole point is creating one), and per
  SPEC Decision 2, `createStaffUser` only ever produces `GlobalRole.STAFF`
  (`StaffService.java:63`), so REQ-4's "never create a new STAFF" clause
  reduces to "STAFF may never call this at all." Rather than adding a
  new `GlobalPermission` or special-casing the aspect, `createStaffUser`
  calls `enforceStaffCeiling(GlobalRole.STAFF)` unconditionally as its
  first line (a synthetic "target" of `STAFF`, since that's what the
  method always produces) — no other logic changes. The existing
  `@RequiresGlobalPermission(GlobalPermission.STAFF_USER_CREATE)` stays
  in place (so `STAFF_ADMIN` still bypasses via the aspect as today, and
  a `STAFF` denial still gets logged as a permission-denied outcome by
  `AuditLogAspect` either way) — it just becomes permanently
  unsatisfiable for any `STAFF` caller now that the ceiling always fires
  first inside the method body. **This is confirmed intentional per
  SPEC Decision 2 and Acceptance Criteria — not a new judgment call**;
  `GlobalPermission.STAFF_USER_CREATE` itself is left in the enum
  (removing it is out of scope — SPEC doesn't ask for it, and a stray
  now-unreachable direct grant is inert, not a security hole).
- **`STAFF_ADMIN` unaffected (REQ-6)**: `enforceStaffCeiling`'s
  condition only fires when `actor.getGlobalRole() == GlobalRole.STAFF`,
  never `STAFF_ADMIN` — a `STAFF_ADMIN` actor always falls through, and
  separately still bypasses the `@RequiresGlobalPermission` check
  entirely via `GlobalPermissionAspect`'s existing
  `tenantContext.isStaffAdmin()` short-circuit. No new code path for
  `STAFF_ADMIN` is introduced.
- **REQ-7 (ceiling doesn't apply to non-staff targets)**: automatically
  true by construction — `enforceStaffCeiling`'s condition requires
  `targetGlobalRole` to be `STAFF`/`STAFF_ADMIN`; a plain tenant member
  has `GlobalRole == null` (see `User.globalRole`, nullable), so the
  condition never matches and the existing `@RequiresGlobalPermission`
  grant-based check remains the only gate, unchanged.

## Data schema

New migration `V15__rename_membership_role_admin_to_member_admin.sql`
(next available number — `V14` was `staff-rbac-split`'s), rewriting both
the live table and its Envers audit-history counterpart confirmed by
reading `V4__create_tenancy_envers_audit_tables.sql:15-28`
(`tenant_memberships_aud`, `role VARCHAR(20)`, no `NOT NULL` — audit rows
can have `revtype = 2` deletes with other columns null, but `role` is
still just a string column, never null-guarded specially by this
migration):

```sql
-- MembershipRole.ADMIN renamed to MEMBER_ADMIN (see specify/features/role-model-refinement/SPEC.md
-- REQ-1..3) — distinct from GlobalRole.STAFF_ADMIN, never a schema/type change since `role` is
-- stored as a plain VARCHAR(20) via @Enumerated(EnumType.STRING), same precedent as V14.
UPDATE tenant_memberships SET role = 'MEMBER_ADMIN' WHERE role = 'ADMIN';
UPDATE tenant_memberships_aud SET role = 'MEMBER_ADMIN' WHERE role = 'ADMIN';
```

No `ALTER TABLE`/column-width change needed (`MEMBER_ADMIN` is 12
characters, well under `VARCHAR(20)`). No other table persists
`MembershipRole` as a string (confirmed: only `tenant_memberships` and
`tenant_memberships_aud` have a `role` column of this shape — `users`'s
`global_role` is a separate enum, `GlobalRole`, untouched by this SPEC).
Per REQ-3/SPEC's own Non-functional Requirements note, historical
`AuditEvent.metadata`/action payloads that happen to reference the old
`"ADMIN"` string are explicitly out of scope and not rewritten.

## API contracts

No new/changed endpoints — this SPEC changes only enum values already
carried by existing DTOs (`MemberDto`, `MemberDetailDto`,
`TenantMembershipDto`, `AddMemberRequestDto`) and a code-level
authorization ceiling inside `StaffService`, called from
`StaffController`'s existing endpoints (no `StaffController` signature
changes).

| Method | Path | Change |
|---|---|---|
| (all existing `/api/tenants/**` membership endpoints) | — | Response bodies now serialize `"MEMBER_ADMIN"` instead of `"ADMIN"` for the tenant-admin role — **breaking contract change for any consumer**, see note below. |
| POST | `/api/staff/users` (`createStaffUser`) | Now unconditionally rejects a `STAFF` caller (was: permission-gated via `STAFF_USER_CREATE`, so a granted `STAFF` could call it). |
| GET | `/api/staff/users/{userId}/permissions` (`getStaffUserDetail`) | Now rejects a `STAFF` caller when `{userId}`'s `GlobalRole` is `STAFF`/`STAFF_ADMIN`; also now emits an `@AuditLog` event on every call (new — this endpoint previously wasn't audited at all). |
| POST/DELETE | `/api/staff/users/{userId}/permissions[/...]` (`grantPermission`/`revokePermission`) | Same ceiling as above. |
| POST/DELETE | `/api/staff/users/{userId}/access-groups/{id}` (`assignAccessGroup`/`unassignAccessGroup`) | Same ceiling as above. |

**Contract-change flag (per task instructions, not silently absorbed):**
`MembershipRole` is serialized by Jackson's default enum-by-name
behavior (no `@JsonValue`/custom serializer found on `MembershipRole` or
any DTO carrying it) — every API response that includes a tenant-admin
membership will change from `"role": "ADMIN"` to `"role":
"MEMBER_ADMIN"` the moment this ships. This is a real, breaking frontend
contract change even though this SPEC is backend-only. **Flagging for
`PROJECT_STATUS.md`**: `knowly-app/` has its own follow-up work to do
(any TypeScript type/string literal comparing against `'ADMIN'` for
tenant membership role — distinct from any `GlobalRole` staff strings,
which are untouched) — out of scope for this PLAN/TASKS, tracked
separately.

## Dependencies

None new.

## Package/file structure

- `src/main/java/br/com/conectabyte/knowly/tenancy/MembershipRole.java` (modify: `ADMIN` → `MEMBER_ADMIN`)
- `src/main/java/br/com/conectabyte/knowly/tenancy/TenantService.java` (modify: 2 call sites, lines 146 and 411)
- `src/main/java/br/com/conectabyte/knowly/tenancy/StaffService.java` (modify: new `enforceStaffCeiling` private helper; called from `createStaffUser`, `getStaffUserDetail`, `grantPermission`, `revokePermission`, `assignAccessGroup`, `unassignAccessGroup`; `@AuditLog` added to `getStaffUserDetail`)
- `src/test/java/br/com/conectabyte/knowly/tenancy/TenantManagementIntegrationTest.java` (modify: 8 usages renamed)
- `src/main/resources/db/migration/V15__rename_membership_role_admin_to_member_admin.sql` (new)
- New/modified test files (see Testing strategy) for the REQ-4/5/6/7/8 ceiling.

## Testing strategy

- **Rename**: no new tests needed beyond updating existing
  `TenantManagementIntegrationTest` references to compile against
  `MEMBER_ADMIN` — this is a rename, not a behavior change, so the
  existing assertions (now referencing the new constant) remain the
  correct verification that admin-gated behavior is unaffected
  (Acceptance Criterion 3).
- **Migration**: a `@SpringBootTest`-backed integration test (new,
  Testcontainers Postgres, following `TenantSessionIntegrationTest`'s
  pattern) that seeds a `tenant_memberships` row with `role = 'ADMIN'`
  directly via `jdbcTemplate`/native insert *before* Flyway's V15 runs
  is impractical (Flyway runs once at context startup, before any test
  code executes) — instead, verify V15 by asserting, post-migration,
  that querying for `MembershipRole.MEMBER_ADMIN` via the repository
  returns the same seeded admin membership that fixtures already create
  (this is implicitly covered by every existing
  `TenantManagementIntegrationTest` case once renamed, since Testcontainers
  runs every migration including V15 from scratch on every test run —
  if V15 didn't correctly no-op on freshly-seeded `MEMBER_ADMIN` data,
  or if the column type change were wrong, those existing integration
  tests would fail).
- **REQ-4/5/6/7/8 ceiling** (new tests in `StaffServiceTest`/a new
  `StaffServiceCeilingIntegrationTest`, following `PermissionServiceTest`'s
  unit-test shape plus `TenantManagementIntegrationTest`'s integration
  shape for the `@AuditLog`/`AuditEventRepository` assertions):
  - For each of `createStaffUser`, `getStaffUserDetail`,
    `grantPermission`, `revokePermission`, `assignAccessGroup`,
    `unassignAccessGroup`: a `STAFF` actor granted every existing
    `GlobalPermission` (including `STAFF_PERMISSION_MANAGE`/
    `STAFF_USER_CREATE`) is rejected with `PermissionDeniedException`
    when the target user (or, for `createStaffUser`, the operation
    itself) is `STAFF`/`STAFF_ADMIN` (REQ-4/5, Acceptance Criterion 1).
  - The same fully-permissioned `STAFF` actor *can* still perform
    `getStaffUserDetail`/`grantPermission`/etc. against a target user
    with `GlobalRole == null` (plain tenant member) (REQ-7, Acceptance
    Criterion 2).
  - `STAFF_ADMIN` unaffected: can call every method against any target,
    including another `STAFF_ADMIN` (REQ-6, Acceptance Criterion 3).
  - Every REQ-5 rejection produces an `AuditEvent` with
    `outcome = DENIED` (REQ-8, Acceptance Criterion 4) — verified via
    `AuditEventRepository` query, including the new `getStaffUserDetail`
    case that previously emitted nothing at all.
