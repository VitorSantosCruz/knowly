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
- `shared/confirm-dialog.component.ts` (+ `.spec.ts`) — new, for
  REQ-11–13 (see below).

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

## REQ-10–17 — UX fixes to `ArticlesPageComponent`

All four fixes touch a single existing component,
`features/articles/articles-page.component.ts` (+ its `.spec.ts`); no
new route, no new backend contract. One new shared component is added
(`ConfirmDialogComponent`), reused by nothing else yet but written
generically since REQ-11 is the first destructive-action confirmation
in this codebase (see "Consumed pattern" below).

- **REQ-10 (non-flickering poll)** — the flicker has two independent
  causes in the current implementation, both fixed without touching the
  `@for (article of articles(); track article.id)` loop (its `track` is
  already correct and is what keeps per-row DOM nodes stable once the
  other two problems are gone):
  - `loadArticles()` currently calls `this.loading.set(true)`
    unconditionally, including from `schedulePollIfNeeded`'s
    `setTimeout` callback — this is what causes the full-page `…`
    loading branch (`@if (loading())`) to replace the entire list on
    every poll tick. Fix: split into `loadArticles(tenantId,
    { isInitialLoad })`; only the initial call (from the `effect` in
    the constructor) sets `loading`. Poll-triggered refetches never
    touch `loading`, so the `@if (loading())`/`@else` branch structure
    itself never re-evaluates during a poll, which is what actually
    prevents the full-page spinner and the associated scroll reset (the
    scroll position is only lost because the browser has to lay out a
    freshly-mounted subtree when Angular swaps `@if` branches — keeping
    the same branch mounted throughout a poll is sufficient, no manual
    scroll save/restore needed).
  - Poll-triggered fetches currently always call `this.articles.set(articles)`
    even when the response is identical to the current list (e.g. two
    consecutive polls where nothing changed status yet) — this forces
    Angular's reactivity graph to re-evaluate every row's bindings for
    no reason and is wasteful, though not itself a visible-flicker bug
    given `track article.id`. Fix: poll-triggered fetches (not the
    initial load, not the post-upload/edit/delete local updates) do a
    shallow compare (`id`+`title`+`status` per row, same length, same
    order — the list is always tenant-scoped and small, no need for a
    library) against the current `articles()` value before calling
    `.set()`; identical responses are dropped silently. This is the
    "only update changed rows" mechanism the SPEC asks for: because
    `.set()` is skipped entirely when nothing changed, and `track
    article.id` means Angular only re-renders bindings for rows whose
    object reference/content actually differs when it *is* called,
    together these satisfy "no full-list flicker on a no-op poll" and
    "only affected rows update on a real change" without hand-rolled
    DOM diffing.
- **REQ-11–13 (delete confirmation)** — `onDelete(articleId)` no longer
  calls `articleService.remove` directly. It sets a new
  `pendingDelete: Signal<ArticleSummary | null>` from the clicked
  article (need the title to display in the prompt, not just the id).
  The template conditionally renders a new shared
  `<app-confirm-dialog>` (see below) bound to
  `pendingDelete()!.title` when `pendingDelete()` is non-null;
  its `(confirm)` output calls the existing removal logic (now in a
  private `performDelete(tenantId, articleId)` extracted from the old
  `onDelete` body) and clears `pendingDelete`; its `(dismissed)` output
  (fired on explicit Cancel, backdrop dismiss, or `Escape`) only clears
  `pendingDelete` — REQ-13 is satisfied by construction since no path
  to `performDelete` exists except through `(confirm)`.
  - **New shared component: `shared/confirm-dialog.component.ts`**
    (+ `.spec.ts`). No existing confirm/modal pattern exists anywhere
    in this codebase to reuse (`new-conversation-dialog.component.ts`
    is a route-level page, not an overlay — it has no backdrop, no
    focus trap, no `Escape` handling; checked before writing this).
    This is therefore a **Tier 2, no-exact-precedent decision** — see
    the `DECISIONS.md` entry added alongside this PLAN
    ("First modal/confirmation-dialog pattern uses native `<dialog>`,
    not a hand-rolled overlay").
    Built on the native `<dialog>` element (`ElementRef` +
    `showModal()`/`close()` in an `effect` reacting to an `open` input),
    because `<dialog>` gives focus-trapping, `Escape`-to-dismiss, and
    `::backdrop` click-outside-to-dismiss for free from the browser —
    exactly what the SPEC's NFR asks for ("keyboard-operable and
    focus-trapped/dismissible with `Escape`") — with zero new
    dependency (Tier 3 would apply to a library like `@angular/cdk`'s
    overlay or a headless-UI package; native `<dialog>` needs neither).
    API: `message: Input<string>` (the "Delete '{title}'?" text,
    composed by the caller via i18n interpolation so the component
    stays generic), `(confirm)`/`(dismissed)` outputs — **deviation from
    the original `(cancel)` naming**: `@angular-eslint/no-output-native`
    rejects an output literally named `cancel` (it collides with the
    native DOM `cancel` event this component also listens for on the
    `<dialog>` itself), so the Angular output is `dismissed` instead;
    it still fires for explicit "Cancel", backdrop dismiss, and
    `Escape` per REQ-13, just under a different binding name.
    `ArticlesPageComponent` is the first and only consumer for now; any
    future destructive action (member removal, etc.) reuses this
    component rather than inventing another confirmation pattern, per
    the DECISIONS.md entry.
- **REQ-14/15 (upload button enabled state)** — `selectedFile` is
  currently a plain private field (`private selectedFile: File | null`),
  not a signal, so nothing reactive can depend on it. It becomes
  `protected readonly selectedFile = signal<File | null>(null)`
  (`onFileSelected` calls `.set()` instead of assigning), and a new
  `protected readonly canUpload = computed(() =>
  this.uploadTitle().trim().length > 0 && this.selectedFile() !== null)`
  is added. The submit button gets `[disabled]="!canUpload()"` (native
  `disabled` — satisfies "shall not submit an upload if clicked" without
  a separate JS guard, since a `disabled` submit button doesn't fire
  `(submit)`) plus a `disabled:opacity-50 disabled:cursor-not-allowed`
  Tailwind pair on `uploadButtonClass` for the visible state REQ-14
  asks for (the existing `buttonClass()` helper already supports
  arbitrary extra classes appended, same as `deleteButtonClass`'s
  ` shrink-0` suffix — no change to `shared/button-classes.ts` needed).
  `onUpload` keeps its existing `if (... || !title || !file) return;`
  guard as defense-in-depth (consistent with this feature's established
  "every handler re-validates independently" posture from the Emergent
  decisions below), but it becomes dead code in the normal click path
  once the button is natively disabled.
- **REQ-16/17 (two-state layout)** — the current template always
  renders `<aside class="w-80 shrink-0">` (fixed narrow width) and
  always renders `<section class="flex-1">` (present in the DOM even
  with nothing selected, just visually empty) — i.e. today's layout is
  permanently in the "narrow + reserved content column" state, which is
  what REQ-16 says is wrong for the no-selection case. Fix:
  - `<aside>`'s width class becomes a binding:
    `[class.w-full]="selectedDetail() === null"`
    `[class.w-80]="selectedDetail() !== null"` (mutually exclusive,
    `shrink-0` stays unconditional), so the upload panel + list occupy
    the full content width until something is selected (REQ-16).
  - `<section>` (the content panel) changes from always-rendered-but-
    visually-empty to `@if (selectedDetail(); as detail) { <section
    class="flex-1"> ... </section> }` at the top level (the existing
    `@if (selectedDetail(); as detail)` inside `<section>` moves up to
    gate `<section>` itself) — so the column isn't reserved in the DOM
    at all while nothing is selected, and appears alongside the now-
    narrow `<aside>` the moment `onSelect` populates `selectedDetail`
    (REQ-17). No new CSS breakpoint/media query is needed — this is a
    selection-state toggle, not a viewport-size one, so it's a plain
    class/structural binding on the existing flex layout, not a new
    Tailwind `@container`/breakpoint utility.

### REQ-10–17 testing strategy additions

- `ConfirmDialogComponent`: `showModal()`/`close()` are called on the
  underlying `<dialog>` in response to the `open` input toggling
  (assert via the element's `open` property) **when available** — this
  project's pinned jsdom version does *not* implement
  `HTMLDialogElement.showModal()`/`close()` (verified: calling them
  throws `TypeError: dialog.showModal is not a function`), so the
  component falls back to toggling the `open` attribute directly when
  those methods are missing, and tests assert against the `open`
  property either way, not the method calls themselves; `(confirm)`/
  `(dismissed)` fire on their respective buttons; `(dismissed)` also
  fires on the native `dialog` `cancel` event (jsdom does dispatch a
  synthetic `cancel` `Event` when the test dispatches one directly at
  the `<dialog>` element, which is what the test does — this stands in
  for a real browser's Escape-triggered `cancel` event).
- `ArticlesPageComponent`: a poll tick whose response is
  identical to the current `articles()` does not call `loading.set(true)`
  and does not replace the `article-list` DOM nodes (asserted by
  capturing element references before/after the tick and comparing
  identity, not just re-querying text); a poll tick with one changed
  row updates only that row's status badge; clicking "Delete" renders
  `app-confirm-dialog` and does *not* call `ArticleService.remove`
  until `(confirm)` fires; `(dismissed)` leaves the row and does not
  call `remove`; the upload submit button has `[disabled]="true"` with only
  a title, only a file, or neither, and `false` once both are set, and
  clicking it while disabled does not call `ArticleService.upload`;
  with `selectedDetail()` null the root layout carries the full-width
  class and no `app-content-panel`/`<section>` is in the DOM; selecting
  an article flips the class and mounts `<section>`.

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
