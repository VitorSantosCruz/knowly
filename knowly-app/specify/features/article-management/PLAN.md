# PLAN — Article management (UI)

> The how. Translates the SPEC into concrete technical decisions.
> References SPEC.md.

## Architectural decisions

- New route `/articles`, `ArticlesPageComponent` — list + detail split,
  same shape as `members`/`conversations`: one page component owning
  both the list sidebar/table and the selected article's detail panel.
- `ArticleService` (`core/article.service.ts`): `list`, `upload`
  (multipart `FormData`), `getDetail`, `update`, `remove` — thin
  `HttpClient` wrappers matching the backend's exact contract, same
  convention as `MemberService`/`ConversationService`.
- `PermissionsService` (`core/permissions.service.ts`, new): wraps the
  newly-added `GET /api/tenants/permissions`, exposing a
  `permissions: Signal<Permission[] | null>` and a `has(permission)`
  helper. `ArticlesPageComponent` calls `fetch()` once (alongside
  `ActiveTenantService.fetch()`) and uses `has('ARTICLE_CREATE')`/
  `has('ARTICLE_EDIT')`/`has('ARTICLE_DELETE')` to conditionally render
  the upload form / edit controls / delete button (REQ-9) — a small new
  core service rather than one-off logic in this page, since any future
  screen gating an action by permission can reuse it.
- Polling (REQ-3): while `articles()` contains any `PROCESSING` status,
  a `setInterval`-driven refetch of the list runs every 4s; cleared once
  none remain `PROCESSING`, and on component destroy
  (`DestroyRef`/`ngOnDestroy`). No WebSocket/SSE — this mirrors the
  project's existing polling-free-by-default posture and keeps the
  mechanism identical to a plain manual refresh, just automatic.
- Reuses `NoAccessStateComponent`/`ErrorStateComponent` for REQ-8, same
  convention as every other feature.

## State and data

- `articles: Signal<ArticleSummary[]>`
- `selectedArticleId: Signal<number | null>`
- `selectedArticleDetail: Signal<ArticleDetail | null>`
- `permissions: Signal<Permission[] | null>` (via `PermissionsService`)
- `uploading: Signal<boolean>`, `uploadError: Signal<string | null>`
- `loading`/`error` (`'network' | 'permission-denied' | null`), same
  shape as every other feature's page component

## Consumed API contracts

All already implemented in `knowly` (`specify/features/article-management/`
there), plus one addition to `tenancy` made alongside this feature:

- `POST /api/tenants/{tenantId}/articles` (multipart: `title`, `file`) →
  `202 { id, title, status: "PROCESSING" }`; `400 UNSUPPORTED_FILE_TYPE`
  / `400 FILE_TOO_LARGE`.
- `GET /api/tenants/{tenantId}/articles` → `200
  Array<{ id, title, status }>`
- `GET /api/tenants/{tenantId}/articles/{id}` → `200
  { id, title, text, status, failureReason, originalFileUrl }`
- `PUT /api/tenants/{tenantId}/articles/{id}` (`{ title, text }`) → `200`
  same shape as detail.
- `DELETE /api/tenants/{tenantId}/articles/{id}` → `200`
- `403 { code: 'PERMISSION_DENIED' }` on any of the above without the
  matching `ARTICLE_*` permission.
- `GET /api/tenants/permissions` → `200 { permissions: Permission[] }`
  — **new**, added to the backend's `tenancy` feature specifically to
  unblock this screen's REQ-9 (hiding actions, not just catching their
  403s); see that feature's PLAN.md for the backend side.

## Package/file structure

- `core/article.service.ts` (+ `.spec.ts`)
- `core/permissions.service.ts` (+ `.spec.ts`)
- `features/articles/articles-page.component.ts` (+ `.spec.ts`)
- `app.routes.ts`: register `/articles`.

## Testing strategy

- `ArticleService`: HTTP method/URL/body/multipart assertions via
  `HttpTestingController`.
- `PermissionsService`: fetches and exposes the permission list;
  `has()` returns correctly before and after the fetch resolves.
- `ArticlesPageComponent`: renders the list on load; upload adds a
  "processing" row and the list polls until it clears; an
  unsupported/oversized upload shows an error and adds nothing;
  selecting an article shows its text/failure reason/file link; editing
  persists and updates the shown text; deleting removes the row; a 403
  on any action shows `NoAccessStateComponent`; upload/edit/delete
  controls are absent (not just disabled) when the corresponding
  permission is missing from `PermissionsService`.

## Emergent decisions

- Every article action (list, upload, select, edit, delete) catches its
  own 403 independently, not just the initial list load — the same gap
  class found and fixed in `members`/`conversations`. It's a
  defense-in-depth backstop behind REQ-9's hide-not-fail approach: a
  permission revoked mid-session (before `PermissionsService` next
  refetches) would otherwise surface as an unhandled HTTP error instead
  of the shared permission-denied state.
- Angular's `fakeAsync`/`tick()` require `zone.js/testing`, which this
  app doesn't load (it's a zoneless, signals-based app). The polling
  test uses Vitest's `vi.useFakeTimers()`/`vi.advanceTimersByTime()`
  instead, against the component's plain `setTimeout`-based poll loop.
- File selection in tests: `DataTransfer` isn't available in this
  project's jsdom test environment, so tests set a file input's
  `files` property directly via
  `Object.defineProperty(input, 'files', { value: [file] })` rather
  than building a `DataTransfer`.
