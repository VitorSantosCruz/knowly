# SPEC — tenant-pagination-search (frontend)

> The what and the why. No technical implementation details.

## Context and motivation

The backend's `tenant-pagination-search` feature (see
`knowly-api/specify/features/tenant-pagination-search/`) changed
`GET /api/tenants` from an unbounded `List<TenantSummaryDto>` to a
paginated envelope (`content`/`page`/`size`/`totalElements`/`totalPages`),
plus an optional `search` query param matching a tenant's `name`,
`cnpj`, or `razaoSocial`. This is a **breaking response-shape change**
that the frontend has not yet adapted to.

The only current consumer of this endpoint is `/select-tenant`'s
0-membership staff fallback (`ActiveTenantService.listAllTenants()`,
`select-tenant-page.component.ts`) — used whenever a staff user with no
tenant memberships reaches `/select-tenant` (typically via the nav
menu's "switch tenant" link, per `select-tenant`'s SPEC REQ-6). Today
that fallback still calls the old bare-array shape and renders every
tenant in one unpaginated list, with no search input — it will break
against the new response shape as soon as it's hit, and `PROJECT_STATUS.md`
already flags the unbounded list as a scale problem this backend change
was meant to fix.

This SPEC is the frontend half only: adapt `/select-tenant`'s staff
fallback to the new paginated envelope, and add a search box so a staff
user can find a specific tenant instead of paging through all of them.

## User stories

- As a staff user with no tenant memberships, I want the "act as a
  tenant" list on `/select-tenant` to keep working now that the backend
  response shape changed, so switching tenants doesn't break.
- As a staff user picking a tenant to act as, I want to search by name,
  CNPJ, or razão social and page through results, so I can find one
  tenant without scrolling an ever-growing list.

## Requirements (EARS/GEARS)

- **REQ-1 [Ubiquitous]** The `/select-tenant` staff (0-membership)
  fallback shall consume the new paginated envelope shape
  (`content`/`page`/`size`/`totalElements`/`totalPages`) from
  `GET /api/tenants`, rendering `content` as the visible tenant list.
- **REQ-2 [Ubiquitous]** The screen shall display pagination controls
  (next/previous, or equivalent) reflecting `page`/`totalPages`,
  enabled/disabled appropriately at the first and last page.
- **REQ-3 [Ubiquitous]** The screen shall include a search input that
  filters the fallback tenant list by name, CNPJ, or razão social,
  matching the backend's `search` param semantics (case-insensitive
  substring, OR'd across all three fields).
- **REQ-4 [Event-Driven]** When the user types into the search input,
  the system shall debounce the request (not fire one request per
  keystroke) before calling `GET /api/tenants?search=...`.
- **REQ-5 [Event-Driven]** When the search input changes, the system
  shall reset the visible page back to the first page of the new
  filtered result set.
- **REQ-6 [Event-Driven]** When the user navigates to a different page,
  the system shall request that page (`page`/`size` query params) and
  replace the visible list with that page's `content`, keeping the
  current search term applied if one is set.
- **REQ-7 [Unwanted Behavior]** If the all-tenants fallback request
  fails (matches `select-tenant`'s existing REQ-7), then the screen
  shall show an empty state rather than an unhandled error, regardless
  of whether a page/search request or the initial load failed.
- **REQ-8 [State-Driven]** While the filtered/paginated result set is
  empty (`totalElements = 0`, e.g. no tenant matches the search term),
  the system shall show an explicit "no results" message distinct from
  the request-failure empty state in REQ-7.

## Non-functional requirements

- Accessibility: pagination controls and the search input must be
  keyboard-operable and labeled (matches this codebase's existing
  `Transloco` i18n convention — new label keys in `en`/`pt-BR`).
- Performance: no client-side full-list fetch-then-filter — every
  search/page change must be a fresh request to the backend's already-
  paginated/filtered endpoint (the entire motivation for this SPEC is
  avoiding the current unbounded fetch).
- Compatibility: this is the frontend adaptation to a backend breaking
  change already shipped — no backward compatibility with the old bare-
  array response is needed or attempted.

## Acceptance criteria

- [ ] The 0-membership staff fallback on `/select-tenant` renders
      correctly against the new paginated envelope shape (unit test with
      a mocked envelope response).
- [ ] Pagination controls step through pages, updating the visible list
      each time, disabled at the first and last page respectively.
- [ ] Typing in the search input, after the debounce interval, issues a
      request with the typed term as `search` and updates the list to
      the filtered results.
- [ ] Changing the search term resets pagination to the first page.
- [ ] Selecting a tenant from a filtered/paginated result still sets it
      active and navigates to `/dashboard`, unchanged from today
      (`select-tenant`'s existing REQ-3).
- [ ] A failed fallback request shows the existing empty state
      (REQ-7, unchanged behavior).
- [ ] A search with zero matching tenants shows a distinct "no results"
      message (REQ-8), not the request-failure empty state.
- [ ] A single-membership or already-active-tenant session still never
      reaches this fallback at all (unchanged — `select-tenant`'s
      existing REQ-4).

## Out of scope

- **Any change to the multi-membership (non-staff) tenant list** on
  `/select-tenant` — that list comes from `GET /api/tenants/memberships`
  (or equivalent), is unaffected by the backend's pagination change, and
  is not addressed here.
- **Any change to `/select-tenant`'s routing/guard logic** (when the
  screen is reached at all) — that's `select-tenant`'s existing SPEC,
  untouched by this one.
- **A generic reusable paginated-list UI component** — no existing
  precedent for one in this codebase (verified: every other list screen
  uses a plain array); this SPEC builds pagination/search specific to
  this screen only. Extracting a shared component is a future decision
  if a second frontend list adopts backend pagination, not this one.
- **A generic reusable debounced-search input component** — same
  reasoning; `staff-directory-page.component.ts`'s existing search input
  is not debounced today, and this SPEC does not retrofit it. A future
  feature may extract a shared debounce pattern once two call sites
  exist.
- **Sort controls** — matches the backend's own out-of-scope: fixed
  alphabetical-by-name sort only, no `sort` param exposed in the UI.

## Tier 3 flag

None identified. This SPEC is a straightforward frontend adaptation to
an already-approved, already-shipped backend contract change
(`tenant-pagination-search`, backend) — no new product/business
decision, no new security/privacy tradeoff, no new external dependency,
and nothing hard to reverse. Debounce interval and pagination-control
visual style are Tier 2 judgment calls left to PLAN.md.
