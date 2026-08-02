# SPEC — user-role-selection-at-creation

> The what and the why. No technical implementation details.

## Context and motivation

Today, creating a staff user (`StaffController`/`StaffService.createStaffUser`,
per `staff-user-provisioning`'s REQ-1/REQ-6) always produces a
`GlobalRole.STAFF` row — there is no way to specify a role at all, and
the SPEC explicitly pins the result to `STAFF`, never `STAFF_ADMIN`.
Likewise, adding a tenant member (`TenantController`/`TenantService.addMember`)
has no role parameter today (confirmed by inspection); every added
member becomes a plain `MEMBER`. The only way a `STAFF_ADMIN` or
`MEMBER_ADMIN` account comes into existence today is the
`staff-bootstrap-user` migration (for the very first `STAFF_ADMIN`) or
`staff-rbac-management-operations`'s **demotion** direction working
backwards from an admin that already existed some other way — there is
no direct creation path for either admin tier.

This is deliberately **not** promotion. `staff-rbac-management-operations`
(REQ-6, "Out of scope") already recorded that promoting an existing
`STAFF`/`MEMBER` to an admin tier after the fact is a separate, not-yet-
decided feature, and this SPEC does not touch that boundary or
reinterpret it. This SPEC is about choosing the role **at the moment a
new user/membership is created**, which is a different operation with a
different (and, per Decision 1 below, identical-to-demotion) set of
"who's allowed to do this" rules.

No `mandatory-complete-profile`-shaped feature exists in this repo yet
(confirmed: no matching file under `specify/features/`), so role
selection is specified here as parameters on the two existing creation
endpoints, not folded into some other in-progress mandatory-profile
flow that doesn't exist on disk.

## User stories

- As a `STAFF_ADMIN`, I want to create a new staff user directly as
  `STAFF_ADMIN` (not just `STAFF`), so that I can bring on another
  full administrator without a separate promotion step existing first.
- As a `STAFF_ADMIN` or a tenant's `MEMBER_ADMIN`, I want to add a new
  tenant member directly as `MEMBER_ADMIN` (not just `MEMBER`), so that
  a tenant can be given a second administrator at onboarding time
  without a separate promotion step.
- As a `STAFF` user (however permissioned) or an ordinary `MEMBER`, I
  should not be able to create a new `STAFF_ADMIN`/`MEMBER_ADMIN` by
  simply passing that role on the creation call — only another admin of
  the matching tier can mint a new one.

## Requirements (EARS/GEARS)

### Staff creation

- **REQ-1 [Ubiquitous]** The staff-user creation request
  (`StaffController`/`StaffService.createStaffUser`) shall accept an
  optional `role` field whose only valid values are `STAFF` and
  `STAFF_ADMIN`.
- **REQ-2 [Event-Driven]** When a staff-user creation request specifies
  `role=STAFF_ADMIN`, and the caller is currently a `STAFF_ADMIN`, the
  system shall create the new `User` row with `GlobalRole.STAFF_ADMIN`
  and record an audit event.
- **REQ-3 [Unwanted Behavior]** If a staff-user creation request
  specifies `role=STAFF_ADMIN` and the caller is not currently a
  `STAFF_ADMIN` — including a `STAFF` caller holding
  `STAFF_USER_CREATE` or any other directly-granted or access-group
  permission — then the system shall reject the request outright and
  create no user, mirroring `staff-rbac-management-operations`
  REQ-21's rule that only a `STAFF_ADMIN` may act on an admin-tier
  target (here, the target being created, not an existing one).
- **REQ-4 [Optional Feature]** Where a staff-user creation request omits
  `role`, or explicitly specifies `role=STAFF`, the system shall create
  the new `User` row with `GlobalRole.STAFF`, unchanged from
  `staff-user-provisioning`'s existing REQ-1/REQ-6 behavior (default is
  the least-privileged tier, consistent with that SPEC's existing
  "zero permissions until explicitly granted" default-deny posture).
- **REQ-5 [Ubiquitous]** Creating a new `STAFF_ADMIN` this way shall be
  subject to no "last admin" or any other floor/ceiling check — that
  safeguard (`staff-rbac-management-operations` REQ-2/REQ-8) exists only
  to stop the *count* of admins from reaching zero via demotion/deletion;
  creating an additional admin can only ever increase that count, so no
  such check applies here.

### Tenant member addition

- **REQ-6 [Ubiquitous]** The tenant-member addition request
  (`TenantController`/`TenantService.addMember`) shall accept an optional
  `role` field whose only valid values are `MEMBER` and `MEMBER_ADMIN`.
- **REQ-7 [Event-Driven]** When a tenant-member addition request
  specifies `role=MEMBER_ADMIN`, and the caller is either a
  `STAFF_ADMIN` or that same tenant's `MEMBER_ADMIN`, the system shall
  create the new membership with `MembershipRole.MEMBER_ADMIN` and
  record an audit event.
- **REQ-8 [Unwanted Behavior]** If a tenant-member addition request
  specifies `role=MEMBER_ADMIN` and the caller is neither a
  `STAFF_ADMIN` nor that tenant's `MEMBER_ADMIN` — including a `MEMBER`
  caller holding any directly-granted or access-group permission — then
  the system shall reject the request outright and create no
  membership, mirroring `staff-rbac-management-operations` REQ-22's rule
  for acting on an admin-tier target.
- **REQ-9 [Optional Feature]** Where a tenant-member addition request
  omits `role`, or explicitly specifies `role=MEMBER`, the system shall
  create the new membership with `MembershipRole.MEMBER`, unchanged from
  today's behavior.
- **REQ-10 [Ubiquitous]** Creating a new `MEMBER_ADMIN` this way shall be
  subject to no "last admin" floor check, for the same reason as REQ-5
  — that safeguard exists only for demotion/deletion, never for
  creating an additional admin.

## Non-functional requirements

- Security: REQ-3/REQ-8's authorization check reuses the exact caller-
  identity rule already established and audited by
  `staff-rbac-management-operations` REQ-21/REQ-22 (admin-tier action
  requires an admin-tier caller of the matching scope, permission grants
  never substitute) — no new authorization rule is invented here.
- Security: default is least-privilege (REQ-4/REQ-9) — omitting `role`
  never silently creates an admin.
- Observability: every creation records an audit event including which
  role was assigned (existing `staff-user-provisioning`
  REQ-5/`addMember` audit behavior, extended to include the chosen
  role).

## Acceptance criteria

- [ ] A `STAFF_ADMIN` can create a new staff user with `role=STAFF_ADMIN`;
      the new user has `GlobalRole.STAFF_ADMIN`.
- [ ] A `STAFF` caller (with or without `STAFF_USER_CREATE` or any other
      permission) requesting `role=STAFF_ADMIN` is rejected; no user is
      created.
- [ ] A staff-user creation request with `role=STAFF` or no `role` field
      behaves exactly as `staff-user-provisioning` already specifies
      (new user is `STAFF`).
- [ ] Creating a `STAFF_ADMIN` this way succeeds even when many
      `STAFF_ADMIN`s already exist and even when none exist yet (e.g.
      immediately after bootstrap) — no floor/ceiling check applies.
- [ ] A `STAFF_ADMIN` or a tenant's `MEMBER_ADMIN` can add a new member
      with `role=MEMBER_ADMIN`; the new membership has
      `MembershipRole.MEMBER_ADMIN`.
- [ ] A `MEMBER` caller (with or without any directly-granted or
      access-group permission) requesting `role=MEMBER_ADMIN` is
      rejected; no membership is created.
- [ ] A tenant-member addition request with `role=MEMBER` or no `role`
      field behaves exactly as today (new membership is `MEMBER`).
- [ ] Every creation (staff or tenant member, either role) records an
      audit event that includes the assigned role.

## Out of scope

- **Promotion** of an existing `STAFF`/`MEMBER` to an admin tier after
  creation — no longer out of scope project-wide; the product owner
  confirmed on 2026-08-02 that it's wanted, and it is now specified in
  `staff-rbac-management-operations` (REQ-24–REQ-30). This SPEC still
  does not itself define promotion — it only covers choosing the role at
  the moment of creation — but the two features are no longer in
  tension: creation-time role choice and after-the-fact promotion are
  both covered, each in its own SPEC.
- **Any change to the "last admin" floor/ceiling checks** governing
  demotion or deletion (`staff-rbac-management-operations`
  REQ-2/REQ-4/REQ-8/REQ-10) — those are untouched; REQ-5/REQ-10 above
  only clarify that they don't apply to creation, not that they're
  being relaxed anywhere they currently apply.
- **A `mandatory-complete-profile`-style "complete your profile at
  creation" flow** — no such feature exists in this repo yet; if one is
  built later, whether role selection folds into that flow's form or
  stays a standalone creation parameter is that future feature's
  decision to make, not pre-answered here.
- **UI for role selection** — a separate, later `knowly-app` concern;
  this SPEC is backend-only (per the root constitution's cross-repo
  placement rule, this is a backend-owned behavior change with no
  frontend requirement implied automatically).
- **Changing who's authorized to call the existing (role-less) creation
  endpoints in the first place** — `staff-user-provisioning`'s
  `STAFF_ADMIN`-only gate on `createStaffUser`, and `addMember`'s
  existing `requireAdminOfTenantOrStaff`-style gate, are unchanged;
  this SPEC only adds a stricter check on top, specific to the
  `role=*_ADMIN` case (REQ-3/REQ-8).
