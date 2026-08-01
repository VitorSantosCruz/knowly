# PLAN — staff-leave-tenant (backend)

> The how. Translates the SPEC into concrete technical decisions.
> References SPEC.md.

## Architectural decisions

- New endpoint `POST /api/tenants/active/clear` added to the existing
  `br.com.conectabyte.knowly.tenancy.TenantController` (not a new
  controller) — it is the direct mirror of `switchActiveTenant` on the
  same resource (`/api/tenants/active`), so it belongs next to it, same
  as every other tenant-scoped mutation already lives in this
  controller.
- No request DTO — unlike `switchActiveTenant` (`SwitchActiveTenantRequestDto`),
  this endpoint takes no body; there is nothing to select. Signature:
  `ResponseEntity<Void> clearActiveTenant(HttpServletRequest httpRequest, HttpServletResponse httpResponse)`.
- Staff-only check reuses the existing mechanism: `tenantContext.isStaff()`,
  exactly like `switchActiveTenant`'s own `if (tenantContext.isStaff())`
  branch — no new authorization mechanism. When `!isStaff()`, throw the
  existing `TenantAccessDeniedException` (already mapped to 403
  `TENANT_ACCESS_DENIED` by `TenancyExceptionHandler`), the same exception
  `tenantService.requireTenant`/`requireActiveMembership` throw for the
  analogous case on `switchActiveTenant` — no parallel 403 mechanism
  invented for this one endpoint.
- Unauthenticated callers get 401 for free from the existing Spring
  Security filter chain (same as every other authenticated endpoint) —
  no explicit check needed in the controller, mirroring
  `switchActiveTenant`'s own lack of one.
- Session/authority mutation mirrors `switchActiveTenant` exactly:
  build `TenantAuthorityFactory.forStaff(user.getGlobalRole())`
  authorities, wrap in a fresh `UsernamePasswordAuthenticationToken`,
  install via `SecurityContextHolder.setContext(...)`, persist via
  `new HttpSessionSecurityContextRepository().saveContext(...)`, then
  `session.removeAttribute(TenantSessionKeys.ACTIVE_TENANT_ID)`. This
  is the same authority-replacement shape `switchActiveTenant` uses for
  its staff branch, just without picking a tenant id to store.
- REQ-4 no-op: the method does not branch on whether an active tenant
  is currently set — it always removes the attribute (a no-op removal
  if already absent) and always rebuilds the tenant-less staff
  authorities (idempotent: rebuilding the same authorities a session
  already has is harmless). This keeps the method free of a second
  code path to test/maintain, rather than inventing an early-return
  "already clear" branch.
- **Audit resourceId capture (novel, needs its own note — see below):**
  `AuditLogAspect.record(...)` runs its `resourceIdExpression` SpEL
  evaluation *after* `joinPoint.proceed()` returns (see
  `AuditLogAspect#logAudit`), and evaluates that SpEL only against the
  method's own arguments. That means an expression like
  `#httpRequest.session.getAttribute(...)` would observe the session
  **after** the controller has already removed
  `ACTIVE_TENANT_ID` — too late to capture "the tenant that *was*
  active", which REQ-3 requires. Fix: the controller reads the
  previously-active tenant id from the session *before* removing it,
  then stores it via `httpRequest.setAttribute(PREVIOUS_TENANT_ID_ATTR, previousTenantId)`
  — an `HttpServletRequest` attribute (not the session attribute being
  cleared), which is unaffected by the session mutation and is still
  readable by SpEL when the aspect evaluates it post-`proceed()`,
  because `httpRequest` is already one of the method's real parameters
  the aspect binds by name. `resourceIdExpression =
  "#httpRequest.getAttribute('" + PREVIOUS_TENANT_ID_ATTR + "')"`.
  This reuses `AuditLog`'s existing SpEL-over-arguments contract as
  documented (no change to `AuditLog`/`AuditLogAspect` itself) — it is
  a controller-local trick (a private `static final String
  PREVIOUS_TENANT_ID_ATTR = "clearActiveTenant.previousTenantId"`
  constant in `TenantController`, not a new session key in
  `TenantSessionKeys`, since it is request-scoped transport for the
  aspect, not session state). Flagging this explicitly because it is
  the first `@AuditLog` use in this codebase needing "value as it was
  *before* the mutation" rather than "value as passed in the request" —
  if a second such case shows up, the pattern (request attribute as a
  transport slot for pre-mutation state) should be pulled into a
  documented convention rather than re-derived per call site; not yet
  a `DECISIONS.md` entry on its own since it doesn't change any other
  file's behavior and introduces no new mechanism, only a use of one
  that already exists.
- CSRF: this endpoint is **not** added to `SecurityConfig`'s
  `ignoringRequestMatchers` list. Per the existing comment there,
  `/api/tenants/active` itself is exempted only because it runs in the
  same request sequence as login, before a full session is considered
  established — `/api/tenants/active/clear` is reachable only by an
  already-fully-authenticated, already-tenant-selected session (or a
  tenant-less staff session past login), so it does not qualify and
  must go through CSRF protection like every other authenticated
  mutating endpoint under `/api/tenants/**` (e.g. `createTenant`,
  `addMember`). This is called out explicitly per the SPEC's own
  non-functional requirement and per `DECISIONS.md`'s "CSRF exemption
  is granted only to pre-authentication endpoints".

## Data schema

No schema change. No new entity, no migration. Uses the existing
`HttpSession` attribute `TenantSessionKeys.ACTIVE_TENANT_ID` and the
existing `AuditEvent` table (via `AuditLogAspect`/`AuditEventWriter`,
unchanged).

## API contracts

| Method | Path | Request | Response | Status codes |
|---|---|---|---|---|
| POST | `/api/tenants/active/clear` | none (no body) | none (empty body) | `200 OK` on success (staff, with or without an active tenant selected — REQ-2/REQ-4); `403 FORBIDDEN` with `TenancyErrorResponseDto("TENANT_ACCESS_DENIED")` when the caller is not staff (REQ-5); `401 UNAUTHORIZED` when unauthenticated (REQ-6); `403 FORBIDDEN` (Spring Security's own CSRF filter response, no body from `TenancyExceptionHandler`) when the CSRF token is missing/invalid, same as any other CSRF-protected mutating endpoint. |

Mirrors `switchActiveTenant`'s own contract shape (`POST
/api/tenants/active` → `200 OK` / `Void`) minus the request body.

## Dependencies

None. No new `pom.xml` dependency — reuses `spring-security-core`,
`spring-webmvc`, and the existing `AuditLog`/`AuditLogAspect` already in
the project.

## Package/file structure

- `br.com.conectabyte.knowly.tenancy.TenantController` — add
  `clearActiveTenant(...)` method, annotated
  `@PostMapping("/active/clear")` and
  `@AuditLog(action = "tenant.active_tenant.clear", resourceType = "Tenant", resourceIdExpression = "#httpRequest.getAttribute('clearActiveTenant.previousTenantId')")`,
  plus the private `PREVIOUS_TENANT_ID_ATTR` constant described above.
  No other class changes — `TenantAuthorityFactory`,
  `TenantSessionKeys`, `TenancyExceptionHandler`,
  `HttpSessionSecurityContextRepository` usage are all reused as-is.
- `knowly-api/src/main/java/br/com/conectabyte/knowly/config/SecurityConfig.java` —
  explicitly *not* touched (see CSRF decision above); PLAN calls this
  out so a reviewer doesn't go looking for a diff there.

## Testing strategy

All integration (Testcontainers-backed `@SpringBootTest` +
`MockMvcTester`), added to the existing
`br.com.conectabyte.knowly.tenancy.TenantSessionIntegrationTest` (same
suite `switchActiveTenant` is tested in, same `logIn`/`obtainCsrfCookie`
helper conventions already used by `TenantManagementIntegrationTest` for
CSRF assertions). One test per acceptance criterion:

1. **AC1** — staff switches into a tenant via `/api/tenants/active`,
   then calls `/api/tenants/active/clear` (with CSRF cookie/header);
   asserts `200 OK`, then asserts a subsequent call to a staff-only
   global-scope endpoint (`GET /api/tenants` — already staff-gated,
   see `nonStaffCannotListAllTenants`) succeeds, and a subsequent call
   to `GET /api/tenants/permissions` (tenant-scoped) returns `403
   TENANT_ACCESS_DENIED`, matching `switchActiveTenant`'s own
   "freshly-logged-in staff, no active tenant" behavior (asserted the
   same way `aFreshLoginClearsAStaleActiveTenantIdLeftBehindByAPriorLoginOnTheSameSession`
   already asserts "no active tenant" via this endpoint).
2. **AC2** — folded into AC1's assertion (there is no direct HTTP
   introspection of `HttpSession` attributes from the test's side;
   absence of `ACTIVE_TENANT_ID` is verified behaviorally via the same
   `GET /api/tenants/permissions` → 403 check).
3. **AC3** — staff switches into tenant A, clears, then asserts (via
   `auditEventRepository.findByActorUserIdOrderByOccurredAtDesc`) the
   most recent event has `action = "tenant.active_tenant.clear"`,
   `outcome = SUCCESS`, and `resourceId` equal to tenant A's id (proving
   the pre-mutation capture works).
4. **AC4** — staff with no active tenant (fresh login, never switched)
   calls `/api/tenants/active/clear` with a valid CSRF token; asserts
   `200 OK` and no exception/500.
5. **AC5** — a regular tenant member (`MEMBER`, real
   `TenantMembership`) calls the endpoint; asserts `403` body contains
   `TENANT_ACCESS_DENIED`, and that a subsequent tenant-scoped call
   (e.g. `GET /api/tenants/memberships`) still reflects their
   membership unchanged (proving no session/authority mutation
   happened).
6. **AC6** — no session cookie at all; asserts `401`.
7. **CSRF (NFR)** — calls the endpoint with a valid session cookie but
   **no** CSRF cookie/header; asserts `403` (Spring Security's CSRF
   filter rejection), proving the endpoint was not added to the
   exemption list — same pattern as
   `TenantManagementIntegrationTest`'s CSRF-guarded `createTenant`
   assertions, adapted to this endpoint.

No new unit tests needed beyond these integration tests —
`TenantAuthorityFactory.forStaff` and `TenancyExceptionHandler` are
already unit/integration-tested elsewhere and unchanged by this
feature.
