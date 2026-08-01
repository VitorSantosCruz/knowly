# SPEC — staff-leave-tenant

> The what and the why. No technical implementation details.

## Context and motivation

Per `tenancy`'s existing session model, a ConectaByte staff user with no
real `TenantMembership` row can switch into any tenant via
`POST /api/tenants/active` (`switchActiveTenant`), which replaces the
session's `SecurityContext` authorities with that tenant's and stores
`ACTIVE_TENANT_ID` in the HTTP session. There is currently no endpoint
that reverses this: once a staff user has switched into a tenant, the
only way to change context again is switching to a *different* tenant
(re-`POST /api/tenants/active`) — there is no way to return the session
to the tenant-less, global-scope staff state it had immediately after
login, without logging out entirely.

This matters because the active-tenant context and its authorities are
server-side session state (`HttpSession` attribute +
`SecurityContextHolder`), not something the client can clear on its own
by discarding a local signal — the same reasoning already documented for
why `switchActiveTenant` itself must run server-side. This SPEC adds the
missing "clear active tenant" endpoint, the server-side mirror of what
`switchActiveTenant` does when a staff session is first established
after login (no tenant selected yet).

This SPEC is the backend half of a two-subproject feature; see the
companion frontend SPEC at
`knowly-app/specify/features/staff-leave-tenant/SPEC.md` for the UI half
(where the "leave tenant" action is surfaced and how it's confirmed).

## User stories

- As a ConectaByte staff user currently acting as a tenant, I want to
  clear my active-tenant context so that my session returns to the
  global, no-tenant staff view without switching straight into a
  different tenant or logging out.

## Requirements (EARS/GEARS)

1. **[Ubiquitous]** The system shall expose `POST
   /api/tenants/active/clear`, restricted to callers whose session
   currently identifies as staff (`TenantContext.isStaff()` true at the
   moment of the request).
2. **[Event-Driven]** When a staff caller invokes `POST
   /api/tenants/active/clear` while their session has an active tenant
   selected, the system shall remove the session's `ACTIVE_TENANT_ID`
   attribute and replace the session's `SecurityContext` authorities with
   the same tenant-less, global-scope staff authorities
   (`TenantAuthorityFactory.forStaff(globalRole)`) established at login
   for a staff user with no active tenant.
3. **[Event-Driven]** When `POST /api/tenants/active/clear` succeeds, the
   system shall record an audit event (`tenant.active_tenant.clear`)
   capturing the tenant id that was active immediately before the call,
   consistent with how `switchActiveTenant` already records
   `tenant.active_tenant.switch`.
4. **[State-Driven]** While a staff caller's session has no active tenant
   selected, the system shall still accept `POST
   /api/tenants/active/clear` as a no-op that succeeds (200/204) without
   requiring a prior active tenant, since the caller may not know the
   current session state before calling it.
5. **[Unwanted Behavior]** If `POST /api/tenants/active/clear` is invoked
   by a caller whose session does not identify as staff (a regular tenant
   member, including `MEMBER_ADMIN`), then the system shall reject the
   request with 403 and make no session/authority change — leaving a
   tenant this way is staff-only; a regular member has no "outside any
   tenant" view to return to (see the frontend SPEC's Out of scope for
   why this is staff-only, confirmed with the product owner).
6. **[Unwanted Behavior]** If the caller has no authenticated session at
   all, then the system shall reject the request the same way any other
   authenticated endpoint does (401), with no state change.

## Non-functional requirements

- Security: this endpoint changes `SecurityContext` authorities
  server-side exactly like the existing `switchActiveTenant` endpoint —
  it must go through the same CSRF protection as any other authenticated
  mutating endpoint (see `DECISIONS.md`'s "CSRF exemption is granted only
  to pre-authentication endpoints" — this endpoint is reachable only by
  an already-authenticated staff session, so it does **not** qualify for
  the `/api/tenants/active`-adjacent CSRF exemption list; do not add it
  there).
- Performance/SLA: no new external calls or heavy queries — this is a
  session-attribute mutation plus one audit-log write, same cost profile
  as `switchActiveTenant`.
- Observability: covered by the existing `@AuditLog`/`AuditLogAspect`
  mechanism (requirement 3); no new logging infrastructure needed.

## Acceptance criteria

- [ ] A staff session with an active tenant selected, calling `POST
      /api/tenants/active/clear`, ends up with authorities equivalent to
      a freshly-logged-in staff session with no active tenant (verified
      by a subsequent request to a staff-only, global-scope endpoint
      succeeding, and a subsequent request to a tenant-scoped endpoint
      behaving as if no tenant is selected).
- [ ] The session's `ACTIVE_TENANT_ID` attribute is absent after the
      call.
- [ ] `tenant.active_tenant.clear` is recorded as an audit event with the
      previously-active tenant id.
- [ ] A staff session with no active tenant selected can call this
      endpoint without error (no-op).
- [ ] A non-staff (regular tenant member) session calling this endpoint
      receives 403 and its session/authorities are unchanged.
- [ ] An unauthenticated caller receives 401.
- [ ] This endpoint is not added to the CSRF-exempt matcher list.

## Out of scope

- Any change to how a staff user *enters* a tenant (`switchActiveTenant`/
  `POST /api/tenants/active` itself) — unchanged by this feature.
- Any equivalent "leave" capability for a regular tenant member
  (`MEMBER`/`MEMBER_ADMIN`) — per REQ-5, this is staff-only; a regular
  member always belongs to their tenant(s) and has no tenant-less global
  view to return to. If that assumption changes later, it needs its own
  SPEC, not a silent extension of this one.
- Logging out / ending the session entirely — already covered by the
  existing `/api/auth/logout` endpoint and unaffected by this feature.
- Any UI surface for this action — covered entirely by the companion
  frontend SPEC (`knowly-app/specify/features/staff-leave-tenant/SPEC.md`).
