# TASKS — tenant-pagination-search

> Atomic, sequential, verifiable tasks derived from PLAN.md.
> Each task should be small enough for a single implementation iteration.
> TDAD: test first (Red), then minimal code (Green), then the full
> verification pass at the end.

## Repository — search + pagination at the DB level

- [ ] 1. Write repository-level test(s) on `TenantRepository` (Red)
      covering: unfiltered `search(null, pageable)` returns a page
      alphabetical by `name`; `search` matching only `name`; only
      `cnpj`; only `razaoSocial` (each case-insensitive, each verified
      independently); a page beyond the last page returns empty
      `content` with correct `totalElements`/`totalPages`.
- [ ] 2. Implement `Page<Tenant> search(String search, Pageable
      pageable)` as a `@Query` method on `TenantRepository` (Green) to
      make task 1 green.
- [ ] 3. Write a repository-level test proving DB-level (not in-memory)
      filtering/pagination — e.g. seed enough rows that a
      `findAll()`-based implementation would still functionally pass,
      and assert `Page.getContent().size()` never exceeds the requested
      `size` directly off the repository call. This is the NFR's
      "proven at the query level" acceptance criterion; should already
      be green given task 2's implementation, no further code change
      expected.

## DTO — response envelope

- [ ] 4. Add `PageResponseDto<T>` record (`content`, `page`, `size`,
      `totalElements`, `totalPages`) with `static <T>
      PageResponseDto<T> from(Page<T>)` to
      `br.com.conectabyte.knowly.tenancy.dto`. (No standalone test — a
      plain mapping record, exercised indirectly by the service/
      controller tests below.)

## Exception + handler — REQ-4 validation

- [ ] 5. Write a `TenancyExceptionHandler` test (Red) asserting
      `InvalidPaginationException` maps to `400` +
      `TenancyErrorResponseDto("INVALID_PAGINATION")`.
- [ ] 6. Add `InvalidPaginationException` (mirrors
      `InvalidPeriodException`'s message-only constructor shape) and the
      corresponding `@ExceptionHandler` method on
      `TenancyExceptionHandler` (Green) to make task 5 green.

## TenantService.listAllTenants — validation, clamping, search, mapping

- [ ] 7. Write `TenantServiceTest` case (Red) for REQ-1: no `page`/`size`
      supplied defaults to `page=0`, `size=20`.
- [ ] 8. Write `TenantServiceTest` case (Red) for REQ-3: `size` above
      `100` is clamped to `100`, not rejected.
- [ ] 9. Write `TenantServiceTest` cases (Red) for REQ-4: negative
      `page`, negative `size`, and `size=0` each throw
      `InvalidPaginationException`; assert clamping does not run before
      rejection (e.g. `size=-500` is rejected, not clamped to `100`).
- [ ] 10. Write `TenantServiceTest` case (Red) for REQ-9: a `search`
      value combined with pagination filters first, then paginates —
      `totalElements`/`totalPages` reflect the filtered count, not the
      full unfiltered tenant count.
- [ ] 11. Write `TenantServiceTest` case (Red) for REQ-8 (no
      authorization regression): `STAFF_ADMIN` succeeds unconditionally;
      `STAFF` without `TENANT_ACT_AS_ANY` still throws
      `PermissionDeniedException`, reusing the existing
      `listAllTenants`-authorization test setup already in the suite.
- [ ] 12. Change `TenantService.listAllTenants` signature to `(User
      actor, int page, int size, String search):
      PageResponseDto<TenantSummaryDto>` and implement the
      validate-then-clamp-then-query-then-map logic (Green) to make
      tasks 7–11 green.

## Controller — endpoint contract

- [ ] 13. Write a controller/integration test (Red) covering the full
      `GET /api/tenants[?page=][&size=][&search=]` contract end-to-end
      per the SPEC's acceptance-criteria list item-for-item: default
      envelope shape and field names; next-page slice
      (`?page=1&size=5`); `size=500` clamped to `100` (200, not
      rejected); `page=-1` and `size=0` each `400`; `search` matching
      `name`-only, `cnpj`-only, `razaoSocial`-only; filtered
      `totalElements` reflecting only the matched rows; a past-the-end
      page returning empty `content` with correct totals; `STAFF_ADMIN`
      unconditional success; ungranted `STAFF` still `403` (no
      regression).
- [ ] 14. Change `TenantController.listAllTenants` to accept
      `@RequestParam(defaultValue = "0") int page`,
      `@RequestParam(defaultValue = "20") int size`,
      `@RequestParam(required = false) String search`, delegate to the
      changed `tenantService.listAllTenants(...)`, and return
      `ResponseEntity<PageResponseDto<TenantSummaryDto>>` (Green) to
      make task 13 green.

## Final pass

- [ ] 15. Run `./mvnw spotless:apply` then `./mvnw verify` for the full
      suite (this feature's new/changed tests plus every pre-existing
      test, including any existing `TenantController`/`TenantService`
      test that referenced the old `List<TenantSummaryDto>`
      signature/response shape — update those call sites to the new
      contract rather than leaving them broken) and fix any regression
      surfaced.
- [ ] 16. Hand off to `qa-test-automation` and `appsec` for review of
      this feature during that same final pass.
- [ ] 17. Update `PROJECT_STATUS.md` to reflect this endpoint's new
      paginated/searchable contract (it's referenced there as backlog
      item 11) and note the started-but-not-yet-done companion frontend
      SPEC for `/select-tenant`.
- [ ] 18. Commit the completed, verified work (Conventional Commits),
      once task 15's full suite is green and task 16's reviews are
      addressed.
