# SPEC — tenant-pagination-search

## Context and motivation

`GET /api/tenants` (`TenantController.listAllTenants` /
`TenantService.listAllTenants`) currently returns every `Tenant` in the
system, unbounded (`tenantRepository.findAll()`), gated by
`requireStaff(actor, GlobalPermission.TENANT_ACT_AS_ANY)`. This endpoint
backs the staff "act as this tenant" picker on `/select-tenant` (used
whenever a staff user has zero memberships of their own to pick from).
`PROJECT_STATUS.md` backlog item 11 flags this as unbounded and will
break at scale. This SPEC introduces page/size-style pagination and a
search filter across tenant-identifying fields on this endpoint. It is
the **backend** half only — the `/select-tenant` UI change is a
separate, companion frontend SPEC in `knowly-app/specify/features/`.

This is the first page/size pagination contract introduced anywhere in
this codebase (no existing precedent to copy — `staff-user-listing`
stayed deliberately unpaginated, `staff-audit-trail-view` uses a hard
row cap, not page/size). The shape decided here is intended to be the
default template for any future paginated list endpoint in this
project, absent a reason to diverge.

## User stories

- As a staff user picking a tenant to act as, I want the tenant list to
  load quickly and not degrade as the number of tenants grows, so the
  picker stays usable at scale.
- As a staff user, I want to search tenants by name, CNPJ, or razão
  social so I can find a specific tenant regardless of which identifier
  I remember, without scrolling through an unbounded list.

## Requirements (EARS/GEARS)

- **REQ-1 [Ubiquitous]** The system shall accept optional `page` (zero-
  indexed) and `size` query parameters on `GET /api/tenants`, defaulting
  to `page=0` and `size=20` when omitted.
- **REQ-2 [Ubiquitous]** The system shall return at most `size` tenants
  for the requested `page`, ordered alphabetically by `name` ascending
  (case-insensitive), as the default and only supported sort.
- **REQ-3 [Unwanted Behavior]** If `size` exceeds `100`, then the system
  shall clamp it to `100` rather than rejecting the request.
- **REQ-4 [Unwanted Behavior]** If `page` or `size` is negative, or
  `size` is `0`, then the system shall reject the request with a `400`
  validation error.
- **REQ-5 [Optional Feature]** Where an optional `search` query
  parameter is supplied, the system shall filter the result to tenants
  where `Tenant.name` **or** `Tenant.cnpj` **or** `Tenant.razaoSocial`
  contains that value, case-insensitively (substring match on each
  field, OR'd together), before pagination is applied.
- **REQ-6 [Ubiquitous]** The system shall return a response containing:
  the page's tenant list (`content`, using the existing
  `TenantSummaryDto` shape unchanged per-item), the requested `page`,
  the effective `size`, the total number of matching tenants
  (`totalElements`), and the total number of pages (`totalPages`).
- **REQ-7 [Event-Driven]** When `page` requests a page beyond the last
  available page, the system shall return an empty `content` list (not
  an error), with `totalElements`/`totalPages` still reflecting the true
  totals.
- **REQ-8 [Ubiquitous]** The system shall continue to gate this endpoint
  exactly as today — `STAFF_ADMIN` unconditionally, `STAFF` only when
  holding `GlobalPermission.TENANT_ACT_AS_ANY` — with no change to that
  authorization logic.
- **REQ-9 [State-Driven]** While a `search` filter is supplied together
  with pagination, the system shall apply the filter first and paginate
  only the filtered result set (i.e. `totalElements`/`totalPages`
  reflect the filtered count, not the full unfiltered tenant count).

## Non-functional requirements

- Security: no change to authorization — same `requireStaff`/
  `GlobalPermission.TENANT_ACT_AS_ANY` gating as today; this SPEC does
  not touch who can call this endpoint, only how much data one call
  returns and which fields it can be searched by.
- Performance: filtering and pagination must be applied at the database
  query level (not `findAll()` + in-memory `Stream` filter/skip/limit),
  since the entire point is avoiding loading every `Tenant` row into
  memory per call.
- Compatibility: this is a **breaking response-shape change** — today's
  bare `List<TenantSummaryDto>` becomes a paginated envelope object. The
  current frontend consumer (`select-tenant`'s 0-membership staff
  fallback) must be updated in its own companion frontend SPEC; this
  backend SPEC does not attempt backward compatibility with the old
  unwrapped-array shape.
- Observability: read-only listing endpoint, consistent with other
  read-only list endpoints — no `@AuditLog` needed for the call itself
  (matches `staff-user-listing`'s equivalent decision).

## Acceptance criteria

- [ ] `GET /api/tenants` with no query params returns the first 20
      tenants (alphabetical by name), plus `page=0`, `size=20`,
      `totalElements`, `totalPages`.
- [ ] `GET /api/tenants?page=1&size=5` returns the correct next-page
      slice.
- [ ] `GET /api/tenants?size=500` is clamped to `size=100`, not
      rejected.
- [ ] `GET /api/tenants?page=-1` and `GET /api/tenants?size=0` are
      rejected with `400`.
- [ ] `GET /api/tenants?search=<substring>` matching a tenant's `name`
      returns that tenant.
- [ ] `GET /api/tenants?search=<substring>` matching a tenant's `cnpj`
      (and not its name) returns that tenant.
- [ ] `GET /api/tenants?search=<substring>` matching a tenant's
      `razaoSocial` (and not its name or cnpj) returns that tenant.
- [ ] Pagination applies to the filtered set (verified via
      `totalElements` matching only the filtered count across all three
      fields combined, not the full tenant count).
- [ ] Requesting a page past the last page returns an empty `content`
      array, not an error, with correct `totalElements`/`totalPages`.
- [ ] `STAFF_ADMIN` can call this endpoint unconditionally; a `STAFF`
      user without `TENANT_ACT_AS_ANY` is rejected exactly as today
      (no authorization regression).
- [ ] Filtering/pagination is proven to happen at the query level (e.g.
      an integration test asserting the repository issues a bounded
      query, or equivalent evidence it's not `findAll()` + in-memory
      slicing) — matches the Non-functional performance requirement.

## Out of scope

- **Any UI change** — `/select-tenant` frontend work is a separate,
  companion SPEC in `knowly-app/specify/features/`.
- **Search on any field other than `name`/`cnpj`/`razaoSocial`** — no
  matching against `nomeFantasia`, `inscricaoEstadual`, or any other
  `Tenant` field.
- **Custom/alternate sort orders** — alphabetical-by-name is the only
  supported order; no `sort` query parameter.
- **Cursor-based pagination** — offset (`page`/`size`) only.
- **Any change to `GET /api/tenants`'s authorization model** — gating
  stays exactly `requireStaff(actor, GlobalPermission.TENANT_ACT_AS_ANY)`.
- **Pagination on any other list endpoint** (`listMembers`,
  `listAccessGroups`, `staff-user-listing`'s `GET /api/staff/users`,
  etc.) — this SPEC covers `GET /api/tenants` only; other endpoints stay
  unpaginated unless a future SPEC deliberately extends this pattern to
  them.

## Decisions (confirmed by product owner 2026-07-28)

1. Page/size envelope shape (`content`/`page`/`size`/`totalElements`/
   `totalPages`), with `page`/`size` as query params, confirmed as
   proposed — chosen as the most conventional offset-pagination
   contract, absent any existing precedent in this codebase to follow
   instead.
2. `size` clamped rather than rejected when it exceeds the max (100),
   mirroring the spirit of `staff-audit-trail-view`'s hard-cap
   ("silently cap" rather than "error") over strict rejection.
3. **Search scope expanded, confirmed 2026-07-28**: matches across
   `Tenant.name`, `Tenant.cnpj`, and `Tenant.razaoSocial` (verified
   against the real entity, `knowly-api/src/main/java/br/com/
   conectabyte/knowly/tenancy/Tenant.java`), case-insensitive substring
   on each, OR'd together — not name-only as originally drafted. Param
   renamed from `name` to `search` to reflect the broader match surface.

## Tier 3 flag

None identified as a security/scope violation — this is a pure
performance/pagination/search addition to an existing staff-only,
already-gated endpoint, with no change to who can call it or what data
shape each row (`TenantSummaryDto`) exposes; the fields now searchable
(`name`/`cnpj`/`razaoSocial`) were already present, unmasked, in that
same DTO's response today, so this doesn't introduce a new data
exposure — it only changes how an already-visible field can be queried.
The three "Decisions" above are Tier 2 judgment calls (no existing
precedent to follow) plus one product-owner-confirmed scope expansion
(search fields), not unilateral choices.
