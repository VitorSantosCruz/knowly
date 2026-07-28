# PLAN — tenant-pagination-search (frontend)

> The how. Translates the SPEC into concrete technical decisions.
> References SPEC.md.

## Architectural decisions

- **`ActiveTenantService.listAllTenants` signature changes to
  `(page: number, size: number, search?: string): Observable<PageResponse<TenantSummary>>`**,
  replacing the current `(): Observable<TenantSummary[]>`. This is a
  breaking method-signature change confined to its one call site
  (`SelectTenantPageComponent` — verified via the file read for this
  PLAN, no other consumer exists); no overload preserving the old
  signature is needed since the backend response shape it wrapped no
  longer exists either (matches the backend PLAN's equivalent call on
  `TenantService.listAllTenants`).
- **New local `PageResponse<T>` interface added to `active-tenant.service.ts`
  itself, not a new shared `core/`-level type.** The SPEC's out-of-scope
  section explicitly rules out a generic reusable paginated-list
  abstraction, and this is the only paginated envelope consumed anywhere
  in this frontend today. Field names/types mirror the backend's
  `PageResponseDto` exactly (`content`, `page`, `size`, `totalElements`,
  `totalPages`) so no field-renaming/mapping layer is needed between the
  wire shape and the type used in the component. If a second endpoint
  ever returns this same envelope shape, promoting this interface to a
  shared location is the natural trigger — not anticipated now.
- **Query params built with `HttpParams`, following `StaffUserService.list`'s
  existing precedent** (`staff-user.service.ts`), rather than a raw
  query-string template literal — keeps this new method consistent with
  the one other service in this codebase that already sends an optional
  string filter as a query param.
- **Debounce lives in the component (`SelectTenantPageComponent`), not the
  service**, using a `Subject<string>` piped through `debounceTime(300)`
  + `distinctUntilChanged()` before triggering `listAllTenants(...)` —
  mirrors this SPEC's own out-of-scope note that no shared debounce
  utility exists or should be built yet; ordinary RxJS operators inline
  are sufficient for a single call site. **300ms** is the Angular-
  idiomatic default (no measured UX complaint motivates a different
  value, and it's the value most commonly cited in Angular's own search-
  autocomplete guidance) — a Tier 2 judgment call, noted here per this
  SPEC's Tier 3 flag section rather than left silent.
- **Search-input debounce and pagination click handlers both call one
  shared private `fetchFallbackTenants()` method**, parameterized by the
  component's own `page`/`searchTerm` signals, rather than two separate
  request-building code paths — keeps REQ-6's "keep current search term
  applied across page navigation" correct by construction (there's only
  one place a request is ever built, so page and search can never drift
  out of sync with each other).
- **Component distinguishes three list-empty states with one signal
  (`fallbackError: 'network' | null`) plus the existing `loaded`/`totalElements`
  state, not a single collapsed "empty" boolean** — mirrors
  `StaffDirectoryPageComponent`'s existing `error: '...' | null` pattern
  (`staff-directory-page.component.ts`) for the request-failure case, and
  reuses the envelope's own `totalElements === 0` for the genuine
  zero-results case (REQ-7 vs REQ-8 need to render different messages,
  which a single boolean can't express).
- **Pagination controls are two plain Tailwind buttons ("previous"/"next")
  showing `page + 1` of `totalPages`, not a numbered page-link list** —
  keeps visual style consistent with this app's existing native-Tailwind,
  hand-rolled-component convention post-`primeng-removal` (no page-count
  precedent anywhere else in this app to match instead); a numbered
  control can be added later if a real usability complaint arises, but
  nothing in the SPEC requires it (REQ-2 only requires "next/previous, or
  equivalent").
- **`select-tenant`'s routing/guard logic is untouched** — matches the
  SPEC's explicit out-of-scope item; this PLAN only changes what
  `SelectTenantPageComponent` does once it's already reached, not how it's
  reached.

## Components and routes

- `knowly-app/src/app/features/select-tenant/select-tenant-page.component.ts`
  (modified, no new component/route):
  - Adds a search `<input type="search">` (labeled via a new
    `selectTenant.searchPlaceholder`/`selectTenant.searchLabel` Transloco
    key, `en`/`pt-BR`), visible only in the 0-membership staff-fallback
    branch (never for the multi-membership list, per out-of-scope).
  - Adds "previous"/"next" pagination buttons in the same fallback
    branch, disabled at `page === 0` / `page === totalPages - 1`
    respectively, each labeled via new Transloco keys
    (`selectTenant.previousPage`/`selectTenant.nextPage`).
  - Replaces the `TenantSummary[]`-shaped local mapping with one over
    `PageResponse<TenantSummary>.content`.
  - Adds a distinct "no results" message
    (`selectTenant.noSearchResults`, new Transloco key) rendered when
    `loaded() && totalElements() === 0 && fallbackError() === null` — as
    opposed to the pre-existing `selectTenant.empty` message, now scoped
    to the `fallbackError() === 'network'` case only.
  - No new route; `/select-tenant`'s guard chain
    (`tenantSelectionGuard`, per this app's routing convention) is
    unchanged.

## Consumed API contracts

Cross-referencing `knowly-api/specify/features/tenant-pagination-search/PLAN.md`
for the authoritative contract (not re-derived here):

| Method | Path | Request | Response | Status |
|--------|------|---------|----------|--------|
| GET | `/api/tenants` | none (defaults `page=0`, `size=20`, no search) | `PageResponseDto<TenantSummaryDto>` | 200 |
| GET | `/api/tenants?page=<n>&size=<n>` | query params `page`, `size` | same envelope, page-sliced | 200 |
| GET | `/api/tenants?search=<term>` | query param `search` | same envelope, filtered on `name`/`cnpj`/`razaoSocial` | 200 |
| GET | `/api/tenants` (any of the above) | caller lacks `TENANT_ACT_AS_ANY` | `TenancyErrorResponseDto` | 403 |

The frontend only ever calls this endpoint from the existing
0-membership staff fallback; the 403 case is already handled today via
the existing `catchError` → empty-state path (REQ-7), unchanged by this
PLAN except for reusing the same handler for the new page/search calls
too.

## State and data

- `ActiveTenantService` (`active-tenant.service.ts`) gains:
  - `interface PageResponse<T> { content: T[]; page: number; size: number; totalElements: number; totalPages: number; }`
  - `listAllTenants(page: number, size: number, search?: string): Observable<PageResponse<TenantSummary>>`
    built via `HttpParams` (`page`, `size`, and `search` only when
    truthy — mirrors `StaffUserService.list`'s `email ? ... : undefined`
    pattern for an optional param).
  - No new signal-based state added to the service itself — this
    fallback list is transient, request-scoped UI state, not shared
    session state the rest of the app needs to read (unlike
    `activeTenantId`/`activeTenantName`, which genuinely are shared).
    Matches this service's existing split between durable
    signal-backed state and plain pass-through `Observable`-returning
    methods (`list()`, `createTenant()` are already the latter).
- `SelectTenantPageComponent` gains local signals, all owned by the
  component (this is view-local state, not shared — no new service
  needed per this SPEC's scope):
  - `page = signal(0)`, `totalPages = signal(0)`, `totalElements = signal(0)`
  - `searchTerm = signal('')` (bound to the input's value)
  - `fallbackError = signal<'network' | null>(null)`
  - a `private readonly searchInput$ = new Subject<string>()`, wired in
    `ngOnInit` through `.pipe(debounceTime(300), distinctUntilChanged())`
    to: reset `page` to `0` (REQ-5) then call `fetchFallbackTenants()`.
  - `onPageChange(delta: -1 | 1)` updates `page` and calls
    `fetchFallbackTenants()` directly (no debounce needed — a button
    click is already a single discrete event, not a keystroke stream).

## Dependencies

None. `debounceTime`/`distinctUntilChanged`/`Subject` are all part of
`rxjs`, already a dependency; `HttpParams` is part of
`@angular/common/http`, already used elsewhere in this file's neighbor
service (`staff-user.service.ts`). No new `package.json` entry.

## Testing strategy

Vitest, extending `select-tenant-page.component.spec.ts`'s existing
suite (component-level, mocking `ActiveTenantService`) plus a small
addition to `active-tenant.service.spec.ts`:

- `active-tenant.service.spec.ts`: `listAllTenants(page, size, search)`
  issues `GET /api/tenants` with the expected `HttpParams` (`page`,
  `size`, and `search` present only when supplied), returning the
  envelope shape unchanged (no client-side mapping to verify beyond
  pass-through).
- `select-tenant-page.component.spec.ts`, all within the existing
  0-membership fallback test group:
  - renders `content` from a mocked `PageResponse` envelope (REQ-1,
    replaces the current bare-array mock).
  - pagination buttons: clicking "next" calls `listAllTenants` with
    `page + 1`; disabled at `page === 0` (previous) and
    `page === totalPages - 1` (next) (REQ-2).
  - typing into the search input, advancing fake timers past the
    debounce window, asserts exactly one `listAllTenants` call with the
    typed `search` term and `page: 0` — and that no call fires before
    the debounce window elapses (REQ-3, REQ-4).
  - a second search after an already-paged-forward state resets `page`
    back to `0` in the next request (REQ-5).
  - navigating pages after a search keeps the same `search` term on
    the request (REQ-6).
  - a failed fallback request (mocked `throwError`) shows the existing
    `selectTenant.empty` state, not the new no-results message (REQ-7).
  - a mocked envelope with `totalElements: 0` (and no error) shows
    `selectTenant.noSearchResults` distinctly from `selectTenant.empty`
    (REQ-8).
  - unchanged: a non-empty membership list still short-circuits before
    ever calling `listAllTenants` at all (existing test, asserts no
    regression to `select-tenant`'s REQ-4).

## Deviations

- The PLAN's pre-existing test list included a case titled "shows an
  empty state when the memberships and all-tenants fallback are both
  empty" (a genuine `totalElements: 0`, no error). Per REQ-8 that case
  is actually the *no-results* state, not the *network-failure* empty
  state — the two were conflated in the original test name/expectation
  because it predates this SPEC. Renamed and re-asserted to expect
  `selectTenant.noSearchResults` instead of `selectTenant.empty`, since
  a genuine zero-tenant system is a real "nothing matched" case, not a
  request failure. No REQ/PLAN semantics changed — this is a test-title
  correction to match REQ-7 vs REQ-8's already-specified distinction.
- Pagination controls are additionally hidden entirely (not merely
  disabled) when `totalPages() <= 1`, so a small tenant list that fits
  on one page doesn't show a useless disabled prev/next pair. This
  wasn't explicitly called out in the PLAN's component description but
  follows directly from REQ-2 ("reflecting page/totalPages") and this
  app's existing convention of not rendering controls that can never do
  anything.
- `fallbackError` is reset to `null` at the start of every
  `fetchFallbackTenants()` call (not only set on failure), so a search
  or page change made after a prior failed request correctly clears the
  stale error state on its next attempt. Implicit in the PLAN's
  three-state design but worth noting explicitly since the PLAN's prose
  only described the failure-set path.
