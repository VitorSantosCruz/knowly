# PLAN — tenant-pagination-search

> The how. Translates the SPEC into concrete technical decisions.
> References SPEC.md.

## Architectural decisions

- **DB-level filter + pagination via a single `@Query` + `Pageable` on
  `TenantRepository`, not a `Specification`.** `staff-user-listing`
  chose two explicit derived methods over a `Specification` for a
  single optional filter; here there are three OR'd fields plus an
  optional presence check, which derived-method naming can't express
  cleanly, but a `Specification` is still unwarranted machinery for one
  query shape with no dynamic combination of criteria (search is either
  present or absent, never partial). A single JPQL `@Query` covers it
  in one method:

  ```java
  @Query("""
      SELECT t FROM Tenant t
      WHERE :search IS NULL
         OR LOWER(t.name) LIKE LOWER(CONCAT('%', :search, '%'))
         OR LOWER(t.cnpj) LIKE LOWER(CONCAT('%', :search, '%'))
         OR LOWER(t.razaoSocial) LIKE LOWER(CONCAT('%', :search, '%'))
      """)
  Page<Tenant> search(@Param("search") String search, Pageable pageable);
  ```

  `LIKE` against a `null` `cnpj`/`razaoSocial` column evaluates to
  `NULL` (not a match), which is the correct behavior — a tenant with no
  CNPJ on file simply can't match on that field, it doesn't error or
  false-positive. Passing `null` for `search` (no filter supplied) short-
  circuits the `WHERE` to true via the `:search IS NULL OR` branch,
  giving one method for both the filtered and unfiltered case rather
  than two.
- **Sort is fixed server-side, never client-supplied.** The service
  builds `PageRequest.of(page, size, Sort.by("name").ascending())`
  itself; there is no `sort` request parameter anywhere in the
  controller, matching the SPEC's explicit out-of-scope item ("no
  custom sort orders"). Spring Data appends the `Pageable`'s `Sort` as
  an `ORDER BY` automatically for a `@Query` method that doesn't declare
  its own `ORDER BY` clause, so this doesn't need to be hardcoded a
  second time inside the JPQL string — one source of truth for sort
  order, in the service.
- **Validation (REQ-3/REQ-4) happens in `TenantService`, before building
  the `Pageable`, mirroring `MetricsService`'s `MetricsPeriod.from(...)`
  precedent of centralizing parameter validation in the service layer
  rather than the controller.** Order: negative `page` or `size <= 0`
  throws a new `InvalidPaginationException` immediately (REQ-4); only
  then is `size` clamped to `100` if it exceeds that (REQ-3) — clamping
  happens after rejection, not before, so an out-of-range-and-negative
  value like `size=-500` is rejected, not silently clamped to a
  negative-turned-positive number.
- **New `InvalidPaginationException`, handled by the existing
  `TenancyExceptionHandler`** (not a new handler class) — `tenancy`
  already owns exactly one `@RestControllerAdvice`
  (`TenancyExceptionHandler`) covering every exception in this module;
  `dashboard-analytics`'s `MetricsExceptionHandler` is a *different*
  module's own handler, not a second-handler-per-module precedent. Adds
  one more `@ExceptionHandler` method returning `400` +
  `TenancyErrorResponseDto("INVALID_PAGINATION")`, consistent with the
  existing `TenancyErrorResponseDto(String code)` shape already used by
  every other exception in that class.
- **New `PageResponseDto<T>` generic record, placed in
  `br.com.conectabyte.knowly.tenancy.dto` for now, not a new top-level
  `common`/`shared` package.** The SPEC notes this envelope shape is
  intended as the future default template for other paginated
  endpoints, but no second paginated endpoint exists yet anywhere in
  this codebase, and there is no existing `common`/`shared` package to
  extend (verified: `knowly` package root has only per-domain packages,
  no shared one). Introducing a new top-level package for a type used
  by exactly one endpoint is premature structure; if/when a second
  paginated endpoint is planned, that's the natural trigger to move
  `PageResponseDto` to a shared location and update this endpoint's
  import — a mechanical follow-up, not a redesign. Noting this here so
  the next paginated-endpoint PLAN doesn't have to rediscover the
  reasoning.

  ```java
  public record PageResponseDto<T>(
          List<T> content, int page, int size, long totalElements, int totalPages) {

      public static <T> PageResponseDto<T> from(Page<T> page) {
          return new PageResponseDto<>(
                  page.getContent(),
                  page.getNumber(),
                  page.getSize(),
                  page.getTotalElements(),
                  page.getTotalPages());
      }
  }
  ```
- **`TenantService.listAllTenants` signature changes to accept
  `(User actor, int page, int size, String search)` and return
  `PageResponseDto<TenantSummaryDto>`**, replacing the current
  `List<TenantSummaryDto> listAllTenants(User actor)`. This is a
  breaking method-signature change confined to one call site
  (`TenantController.listAllTenants` is its only caller, verified via
  the file read for this PLAN) — no facade/overload needed to preserve
  the old signature, since nothing else in the codebase depends on it
  and the SPEC explicitly accepts this as a breaking contract change.
  `TenantSummaryDto::from` mapping stays exactly as-is per row —
  `Page<Tenant>.map(TenantSummaryDto::from)` before wrapping in
  `PageResponseDto.from(...)`, so the per-item DTO shape is untouched
  (REQ-6).
- **Controller adds three `@RequestParam(required = false)` /
  `defaultValue` params** (`page` default `"0"`, `size` default `"20"`,
  `search` with no default, `null` when absent), all typed at the
  controller boundary the same way `MetricsController` types `period`
  as a `String` and defers parsing/validation to the service — keeping
  the controller thin and validation logic in one place
  (`TenantService`), consistent with this codebase's existing
  controller/service split.
- **No `@AuditLog`** on the endpoint — unchanged from today (this SPEC's
  own Observability NFR: matches `staff-user-listing`'s equivalent
  decision for a read-only listing endpoint).
- **Authorization unchanged**: `requireStaff(actor,
  GlobalPermission.TENANT_ACT_AS_ANY)` call stays exactly where and how
  it is today, first line of the method, before any pagination/search
  logic runs (REQ-8) — no reordering relative to the new validation
  logic matters for authorization since both throw distinctly-typed
  exceptions the client can't influence into swapping precedence
  (validation of an unauthenticated-for-this-permission caller's junk
  `page`/`size` value would still correctly 403 first, since
  `requireStaff` runs before the new validation code path).

## Data schema

**No Flyway migration.** No new column, table, or index. `Tenant.name`,
`Tenant.cnpj`, `Tenant.razaoSocial` all already exist and are already
present, unmasked, in `TenantSummaryDto`'s current response shape — this
SPEC only changes how they're queried, not what's stored or exposed.
(A future performance pass could add a case-insensitive/functional index
on these three columns if `EXPLAIN` shows a sequential scan becoming a
problem at real tenant-count scale, but the SPEC has no NFR requiring
that now, and adding one speculatively without a measured need would be
a Tier 2 judgment call this PLAN isn't going to make unprompted — flagged
here as a known future follow-up, not a blocker.)

## API contracts

| Method | Path | Request | Response | Status |
|--------|------|---------|----------|--------|
| GET | `/api/tenants` | none (defaults: `page=0`, `size=20`, no search) | `PageResponseDto<TenantSummaryDto>` | 200 |
| GET | `/api/tenants?page=<n>&size=<n>` | query params `page`, `size` | `PageResponseDto<TenantSummaryDto>`, page-sliced | 200 |
| GET | `/api/tenants?size=<n over 100>` | query param `size` | same envelope, `size` clamped to `100` | 200 |
| GET | `/api/tenants?page=-1` or `?size=0` | invalid query param | `TenancyErrorResponseDto("INVALID_PAGINATION")` | 400 |
| GET | `/api/tenants?search=<substring>` | query param `search` | `PageResponseDto<TenantSummaryDto>` filtered across `name`/`cnpj`/`razaoSocial`, case-insensitive | 200 |
| GET | `/api/tenants` (any of the above) | caller is `STAFF` without `TENANT_ACT_AS_ANY` | `TenancyErrorResponseDto("PERMISSION_DENIED")` | 403 (unchanged) |

`PageResponseDto<TenantSummaryDto>` shape:

```json
{
  "content": [ { "id": 1, "name": "...", "cnpj": "...", "razaoSocial": "...", "nomeFantasia": "...", "inscricaoEstadual": "..." } ],
  "page": 0,
  "size": 20,
  "totalElements": 42,
  "totalPages": 3
}
```

## Dependencies

None. No new `pom.xml` dependency — `Page`/`Pageable`/`PageRequest` are
already part of `spring-data-jpa`, already a dependency.

## Package/file structure

- `br.com.conectabyte.knowly.tenancy.TenantRepository` — add
  `search(String search, Pageable pageable): Page<Tenant>` `@Query`
  method.
- `br.com.conectabyte.knowly.tenancy.dto.PageResponseDto` — new generic
  record + `static <T> from(Page<T>)` factory.
- `br.com.conectabyte.knowly.tenancy.exception.InvalidPaginationException`
  — new, mirrors `InvalidPeriodException`'s shape (message-only
  constructor).
- `br.com.conectabyte.knowly.tenancy.exception.TenancyExceptionHandler`
  — add one `@ExceptionHandler(InvalidPaginationException.class)`
  method.
- `br.com.conectabyte.knowly.tenancy.TenantService` — change
  `listAllTenants` signature to `(User actor, int page, int size,
  String search): PageResponseDto<TenantSummaryDto>`; add private
  validation/clamping logic.
- `br.com.conectabyte.knowly.tenancy.TenantController` — change
  `listAllTenants` to accept `page`/`size`/`search` `@RequestParam`s and
  return `ResponseEntity<PageResponseDto<TenantSummaryDto>>`.

## Testing strategy

- Repository-level test (Testcontainers, matching existing
  `TenantRepository`/similar repository test convention) covering:
  - unfiltered search (`search=null`) returns a page, alphabetical by
    `name`.
  - `search` matching only `name`, only `cnpj`, only `razaoSocial`,
    case-insensitively, each verified independently (mirrors the SPEC's
    three separate acceptance criteria for this).
  - a page beyond the last page returns empty `content` with correct
    `totalElements`/`totalPages` (REQ-7).
  - proves query-level filtering: assert against a seeded row count
    large enough that an in-memory `findAll()`-based implementation
    would still "pass" functionally, but assert on `Page.getContent()`
    size never exceeding the requested `size` directly from the
    repository call (not via any Java-side truncation) — this is the
    NFR's "proven at the query level" acceptance criterion.
- Service-level test (`TenantServiceTest`) covering:
  - default `page=0`/`size=20` when not supplied (REQ-1).
  - `size` clamped to `100` when requesting more (REQ-3).
  - negative `page`, negative `size`, and `size=0` each throw
    `InvalidPaginationException` (REQ-4).
  - `STAFF_ADMIN` succeeds unconditionally; `STAFF` without
    `TENANT_ACT_AS_ANY` still throws `PermissionDeniedException`, exactly
    as today (REQ-8, no authorization regression) — reusing the existing
    `listAllTenants` test setup pattern already in the current suite.
- Controller/integration test (Testcontainers,
  `TenantControllerIT`-style, matching existing convention) covering the
  full `GET /api/tenants[?page=][&size=][&search=]` contract end-to-end
  per the SPEC's acceptance-criteria list, including the `400` and `403`
  paths and the envelope's exact field names.
