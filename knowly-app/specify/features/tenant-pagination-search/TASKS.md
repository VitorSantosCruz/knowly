# TASKS — tenant-pagination-search (frontend)

> Atomic, sequential, verifiable tasks derived from PLAN.md.
> Each task should be small enough for a single implementation iteration.

- [x] 1. Write `active-tenant.service.spec.ts` test: `listAllTenants(page, size, search)`
      issues `GET /api/tenants` with `HttpParams` `page`/`size` always
      present and `search` only when supplied, returning the
      `PageResponse<TenantSummary>` envelope unchanged (Red — REQ-1).
- [x] 2. Add `PageResponse<T>` interface and change
      `ActiveTenantService.listAllTenants` signature to
      `(page: number, size: number, search?: string): Observable<PageResponse<TenantSummary>>`
      built via `HttpParams` (Green for task 1).
- [x] 3. Write `select-tenant-page.component.spec.ts` test: the
      0-membership fallback renders `content` from a mocked
      `PageResponse` envelope instead of a bare array (Red — REQ-1).
- [x] 4. Update `SelectTenantPageComponent`'s fallback branch to consume
      `PageResponse<TenantSummary>.content` and add `page`/`totalPages`/
      `totalElements` signals (Green for task 3).
- [x] 5. Write test: "next"/"previous" pagination buttons call
      `listAllTenants` with the incremented/decremented `page`, disabled
      at `page === 0` and `page === totalPages - 1` respectively (Red —
      REQ-2).
- [x] 6. Add the pagination buttons + `onPageChange(delta)` handler and
      new Transloco keys (`selectTenant.previousPage`/`nextPage`) to
      `en`/`pt-BR` (Green for task 5).
- [x] 7. Write test: a search `<input>` exists in the fallback branch,
      typing debounces (fake timers) before calling `listAllTenants`
      with the typed `search` term and `page: 0` — no call fires before
      the debounce window elapses (Red — REQ-3, REQ-4).
- [x] 8. Add the search input, `searchTerm` signal, `Subject<string>` +
      `debounceTime(300)`/`distinctUntilChanged()` pipeline, and new
      Transloco keys (`selectTenant.searchLabel`/`searchPlaceholder`) to
      `en`/`pt-BR` (Green for task 7).
- [x] 9. Write test: a second search issued while on a later page resets
      `page` back to `0` in the next request (Red — REQ-5).
- [x] 10. Wire the debounced search pipeline to reset `page` to `0`
       before calling `fetchFallbackTenants()` (Green for task 9).
- [x] 11. Write test: navigating pages after a search keeps the same
       `search` term on the subsequent request (Red — REQ-6).
- [x] 12. Consolidate request-building into one shared private
       `fetchFallbackTenants()` method called by both the debounce
       pipeline and `onPageChange`, parameterized by the component's own
       `page`/`searchTerm` signals (Green for task 11).
- [x] 13. Write test: a failed fallback request (mocked `throwError`)
       shows the existing `selectTenant.empty` message, not a new
       no-results message (Red — REQ-7).
- [x] 14. Add/confirm `fallbackError` signal set on `catchError`,
       rendering the existing `selectTenant.empty` state only when
       `fallbackError() === 'network'` (Green for task 13).
- [x] 15. Write test: a mocked envelope with `totalElements: 0` (no
       error) shows a distinct `selectTenant.noSearchResults` message,
       not `selectTenant.empty` (Red — REQ-8).
- [x] 16. Add the `selectTenant.noSearchResults` Transloco key
       (`en`/`pt-BR`) and the template condition
       (`loaded() && totalElements() === 0 && fallbackError() === null`)
       (Green for task 15).
- [x] 17. Write/confirm regression test: a non-empty membership list
       still short-circuits before ever calling `listAllTenants`
       (existing `select-tenant` REQ-4 behavior, unchanged).
- [x] 18. Run `npm run format:check && npm test && npm run build` and
       confirm everything is green.
- [x] 19. `qa-test-automation` independent review: confirm every REQ/
       acceptance criterion in SPEC.md is covered by a real passing
       test.
- [x] 20. `appsec` review: confirm no new client-side exposure (search
       term reflected only in query params to the same already-gated
       endpoint, no new data rendered beyond what `TenantSummaryDto`
       already exposed today).
- [x] 21. Update `PLAN.md`'s "Deviations" section if any decision
       changed during implementation; update `PROJECT_STATUS.md`'s
       `tenant-pagination-search` frontend row.
- [x] 22. Commit.
