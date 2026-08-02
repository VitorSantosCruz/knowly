# PLAN — user-role-selection-at-creation

> The how. Translates the SPEC into concrete technical decisions.
> References SPEC.md, and coordinates with `staff-user-provisioning/SPEC.md`
> REQ-9/REQ-10 and `tenancy/SPEC.md` REQ-24/REQ-25 (the same contract,
> already folded into those two SPECs) and with
> `staff-rbac-management-operations/SPEC.md` REQ-21/REQ-22 (the
> authorization rule this PLAN extracts and reuses — that feature's own
> PLAN.md does not exist yet; when it is written, it must call the same
> two helper methods introduced here rather than re-deriving the rule).

## Architectural decisions

- **`role` becomes optional on both existing creation DTOs, not a new
  endpoint** — `CreateStaffUserRequestDto` gains an optional `GlobalRole
  role` field; `AddMemberRequestDto.role` (currently `@NotNull
  MembershipRole role`, confirmed by inspection) is **relaxed from
  required to optional**. *Why:* REQ-1/REQ-6 ask for an optional field on
  the existing request shape, not a parallel "create as admin" endpoint;
  and `AddMemberRequestDto.role` being `@NotNull` today is itself a gap
  this SPEC closes — every caller of `addMember` is currently forced to
  pick a role explicitly with **no authorization check on the
  `MEMBER_ADMIN` case at all** (confirmed: `TenantService.addMember` sets
  `membership.setRole(role)` unconditionally). REQ-6/REQ-9/REQ-24 require
  the default (omitted or `MEMBER`) to behave exactly as today, so the
  DTO validation must allow `null` and the service must default to
  `MembershipRole.MEMBER`.
- **Default resolution happens in the service layer, not the DTO** — both
  `StaffService.createStaffUser` and `TenantService.addMember` resolve
  `role == null ? <default> : role` themselves. *Why:* keeps the "what's
  the default" decision next to the code that already owns the rest of
  the creation logic, consistent with how `addMember` already resolves
  `userAlreadyExisted`-dependent defaults inline.
- **Extract one shared helper per scope for "does the caller qualify as
  an admin of the matching tier", reused verbatim by
  `staff-rbac-management-operations`'s demotion/deletion/promotion paths**
  (its own PLAN, not yet written, must call these, not reimplement
  REQ-21/REQ-22's rule a second time):
  - `StaffService.requireCallerIsStaffAdmin()` (new, private): throws
    `PermissionDeniedException` unless `currentActor().getGlobalRole() ==
    GlobalRole.STAFF_ADMIN`. No permission-grant substitution — a `STAFF`
    caller holding `STAFF_USER_CREATE`/`STAFF_PERMISSION_MANAGE`/any grant
    still fails this check, per REQ-3.
  - `TenantService.requireCallerIsAdminOfTenant(User actor, Long
    tenantId)` (new, private): returns normally if `actor.getGlobalRole()
    == GlobalRole.STAFF_ADMIN`, or if `actor` holds an active
    `MEMBER_ADMIN` membership in `tenantId`; throws
    `PermissionDeniedException` otherwise. Deliberately **not** the
    existing `requireAdminOfTenantOrStaff` (that method also lets a
    `STAFF`/`MEMBER` caller through via a granted `GlobalPermission`/
    tenant permission — REQ-8/REQ-25 explicitly reject that substitution
    for the `MEMBER_ADMIN`-target case), so it is a new, narrower method,
    not a parameter tweak to the existing one.

  *Why one method per scope instead of a single cross-scope helper:* the
  two scopes' underlying data differ (`GlobalRole` on `User` vs.
  `MembershipRole` on `TenantMembership` scoped to a specific tenant) and
  the existing codebase already keeps `StaffService`/`TenantService` as
  separate, independent services (no shared base class) — introducing a
  new shared type for two four-line methods would be a heavier change
  than the SPEC calls for. Both methods live at the same layer
  (private helper on the service that owns the relevant entity), matching
  every other guard already in each service (`enforceStaffCeiling`,
  `requireNotSelfTarget`, `requireAdminOfTenantOrStaff`).
- **Call site wiring:**
  - `StaffService.createStaffUser(String email, GlobalRole role)`: when
    the resolved role is `STAFF_ADMIN`, call
    `requireCallerIsStaffAdmin()` before creating the row (REQ-2/REQ-3).
    When resolved role is `STAFF` (default), no extra check — existing
    `@RequiresGlobalPermission(STAFF_USER_CREATE)` gate is unchanged
    (REQ-4).
  - `TenantService.addMember(User actor, Long tenantId, String email,
    MembershipRole role)`: after the existing
    `requireAdminOfTenantOrStaff(actor, tenantId, ...)` call — **AppSec
    correction (2026-08-02):** this call site's `GlobalPermission`
    argument is `TENANT_MEMBER_CREATE`, not `TENANT_MEMBER_MANAGE_ANY`.
    `permission-granularity-model/PLAN.md` (written concurrently,
    approved the same day) removes `TENANT_MEMBER_MANAGE_ANY` from the
    `GlobalPermission` enum entirely and rewrites this exact call site to
    `TENANT_MEMBER_CREATE` as part of its bundled-permission migration.
    Whichever of these two features' TASKS.md lands first determines
    what the other's TASKS.md finds at this call site; neither PLAN
    should re-introduce `TENANT_MEMBER_MANAGE_ANY`. This still gates the
    base "can this caller add anyone at all" question per REQ-8's
    Out-of-scope note — unchanged in effect, only the constant name
    changes. Add: when the resolved role is `MEMBER_ADMIN`, call
    `requireCallerIsAdminOfTenant(actor, tenantId)` (REQ-7/REQ-8). When
    resolved role is `MEMBER` (default), no extra check (REQ-9).
- **No "last admin" check anywhere in this path** — confirmed by
  inspection: no floor/ceiling logic exists in `createStaffUser` or
  `addMember` today, and none is added. REQ-5/REQ-10 are satisfied by
  *absence* of a check, not by adding and then special-casing one; the
  only floor/ceiling logic in the codebase lives in
  `staff-rbac-management-operations`'s demotion/deletion methods (not yet
  implemented), which this PLAN does not touch.
- **Audit events gain the assigned role via a new, small
  `@AuditLog` capability — `metadataExpression`** — today's `@AuditLog`
  only supports `resourceIdExpression` (SpEL over method args) and a
  boolean `captureSourceIp`; there is no existing way to record an
  arbitrary argument's value in `AuditEvent.metadata`. Add `String
  metadataExpression() default "";` to `@AuditLog`, evaluated via the
  same `EXPRESSION_PARSER`/`PARAMETER_NAME_DISCOVERER` already used for
  `resourceIdExpression`, and — when non-empty — written into
  `AuditEvent.metadata` as `{"role": "<result>"}` (merged with the
  `sourceIp` key if `captureSourceIp` is also set on the same method;
  today no method uses both, so this ordering has no existing case to
  regress). `createStaffUser` gets
  `@AuditLog(..., metadataExpression = "#role")`;
  `addMember` gets `@AuditLog(..., metadataExpression = "#role")`.
  *Why not a bespoke field on `AuditEvent` instead:* `metadata` already
  exists precisely for this kind of "extra, action-specific detail" data
  (see `captureSourceIp`'s existing use), so this reuses that column
  rather than widening the entity's schema for one more per-action field.
- **This PLAN does not implement `mandatory-complete-profile`'s
  completeness check** (`staff-user-provisioning` REQ-7/REQ-8, `tenancy`
  REQ-22/REQ-23) even though both host SPECs were amended the same day to
  fold it in alongside role selection — that is a distinct, larger,
  already-separately-specified requirement (new profile fields, a
  different rejection shape) with its own SPEC (`mandatory-complete-
  profile`) and needs its own PLAN/TASKS. Flagging this explicitly so it
  is not silently dropped: **the two amended host SPECs (`staff-user-
  provisioning`, `tenancy`) are only fully satisfied once both this
  feature's PLAN and `mandatory-complete-profile`'s PLAN are implemented
  and merged** — coordinate scheduling that work as its own follow-up
  rather than assuming this PLAN covers it.

## Data schema

None. No new table/column — `GlobalRole`/`MembershipRole` already have
exactly the two values each request needs (`GlobalRole`: `STAFF_ADMIN`,
`STAFF`; `MembershipRole`: `MEMBER_ADMIN`, `MEMBER`), confirmed by
inspection of both enums. `AuditEvent.metadata` (existing `TEXT`/JSON
column, already used by `captureSourceIp`) needs no migration either.

## API contracts

| Method | Path | Request DTO change | Response | Status codes |
|---|---|---|---|---|
| POST | `/api/staff/users` | `CreateStaffUserRequestDto` gains optional `role: "STAFF" \| "STAFF_ADMIN"` (default omitted → `STAFF`) | unchanged (`StaffUserDetailDto`) | 201 created; 403 if `role=STAFF_ADMIN` and caller isn't `STAFF_ADMIN` (REQ-3); 409 if email exists (unchanged, REQ-2 of `staff-user-provisioning`) |
| POST | `/api/tenants/{tenantId}/members` | `AddMemberRequestDto.role` becomes optional (`MEMBER \| MEMBER_ADMIN`, default omitted → `MEMBER`) | unchanged | 201/200 (unchanged shape); 403 if `role=MEMBER_ADMIN` and caller is neither `STAFF_ADMIN` nor that tenant's `MEMBER_ADMIN` (REQ-8); existing 403 for base `addMember` authorization (`requireAdminOfTenantOrStaff`) unchanged |

No new endpoints; no new routes. No CSRF-exemption change (both are
already-authenticated endpoints).

## Dependencies

None new (backend `pom.xml` unchanged).

## Package/file structure

- `src/main/java/br/com/conectabyte/knowly/tenancy/dto/CreateStaffUserRequestDto.java` (modify: add optional `GlobalRole role`)
- `src/main/java/br/com/conectabyte/knowly/tenancy/dto/AddMemberRequestDto.java` (modify: `@NotNull` → optional `MembershipRole role`)
- `src/main/java/br/com/conectabyte/knowly/tenancy/StaffService.java` (modify: `createStaffUser` signature + `requireCallerIsStaffAdmin()` new private method + `@AuditLog` metadataExpression)
- `src/main/java/br/com/conectabyte/knowly/tenancy/StaffController.java` (modify: pass `request.role()` through)
- `src/main/java/br/com/conectabyte/knowly/tenancy/TenantService.java` (modify: `addMember` gains the `MEMBER_ADMIN`-case check + `requireCallerIsAdminOfTenant()` new private method + `@AuditLog` metadataExpression; role defaulting)
- `src/main/java/br/com/conectabyte/knowly/audit/AuditLog.java` (modify: new `metadataExpression` attribute)
- `src/main/java/br/com/conectabyte/knowly/audit/AuditLogAspect.java` (modify: evaluate `metadataExpression`, merge into `metadata` alongside `captureSourceIp`'s existing output)

## Testing strategy (TDAD: Red → Green per requirement)

- Unit tests:
  - `StaffServiceTest`: `createStaffUser` with `role=STAFF_ADMIN` +
    `STAFF_ADMIN` caller → row created as `STAFF_ADMIN` (REQ-2). Same
    call with a `STAFF` caller (with/without `STAFF_USER_CREATE`) →
    `PermissionDeniedException`, no row created (REQ-3). `role=STAFF` or
    omitted → unchanged `STAFF` row (REQ-4). Repeat REQ-2's case with
    zero and with many existing `STAFF_ADMIN`s to confirm no floor/
    ceiling check fires (REQ-5).
  - `TenantServiceTest`: `addMember` with `role=MEMBER_ADMIN` + caller is
    `STAFF_ADMIN` → membership created `MEMBER_ADMIN` (REQ-7). Same with
    caller as that tenant's `MEMBER_ADMIN` → same result (REQ-7). Caller
    is a plain `MEMBER` (with/without any granted permission) →
    `PermissionDeniedException`, no membership created (REQ-8). Caller is
    a `MEMBER_ADMIN` of a *different* tenant attempting `role=MEMBER_ADMIN`
    on this tenant → rejected (REQ-8, cross-tenant case). `role=MEMBER` or
    omitted → unchanged `MEMBER` membership (REQ-9). Repeat the
    authorized case with zero and with many existing `MEMBER_ADMIN`s in
    the tenant to confirm no floor/ceiling check fires (REQ-10).
  - `AuditLogAspectTest`: `metadataExpression = "#role"` produces
    `{"role": "..."}` in `AuditEvent.metadata`; unaffected when the
    attribute is left at its default `""` (regression check for every
    other existing `@AuditLog` use).
- Integration tests (`@SpringBootTest`, Testcontainers):
  - `StaffManagementIntegrationTest` (or existing staff-provisioning
    integration test, extended): `POST /api/staff/users` end-to-end for
    both the `STAFF_ADMIN`-authorized and rejected paths; asserts the
    audit event's metadata includes the role.
  - `TenantManagementIntegrationTest` (extended): `POST
    /api/tenants/{id}/members` end-to-end for the `MEMBER_ADMIN`
    authorized (both `STAFF_ADMIN` and tenant `MEMBER_ADMIN` callers) and
    rejected paths; asserts the audit event's metadata includes the role.
  - Regression: existing `addMember` tests that call it without
    specifying `role` (if any relied on the field being mandatory) are
    updated to reflect the new optional default — confirms REQ-9's "same
    as today" behavior isn't accidentally broken by the `@NotNull`
    removal.
