# PLAN — chat-message-search (frontend)

> The how. Translates SPEC.md into concrete technical decisions.
> References SPEC.md (approved). API contract consumed verbatim from
> `knowly-api/specify/features/chat-message-search/PLAN.md` (closed,
> source of truth) — not re-derived here.

## Architectural decisions

- **New overlay entry point: `chat-search-dialog.component.ts`, opened
  from a new icon button in `ChatSidebarComponent`'s existing action
  row, not a fourth persistent column and not a route.** SPEC's "Out of
  scope" explicitly rules out a dedicated route, and REQ-1/REQ-2 only
  require the entry point be reachable "without leaving" `/chat` and
  "visually distinct" from the two existing name-only search fields
  (column 1's `unifiedQuery` in `ChatDirectoryComponent`, column 3's own
  query in `ChatFullDirectoryComponent`). A native `<dialog>` overlay
  (this feature area's established modal precedent —
  `deletion-confirmation-token`'s `ConfirmDialogComponent`,
  `create-group-dialog.component.ts`) is chosen over a fourth column
  because: (a) it needs no interaction with the 3-column desktop/
  1-column-mobile breakpoint logic `ChatShellComponent` already carries
  (Amendment (3)'s documented complexity) — a modal is breakpoint-
  agnostic by construction; (b) unlike columns 1/3, this search is not a
  "browse a list, always visible" surface — it's an occasional, query-
  driven lookup, which is exactly the access pattern a modal fits, not a
  persistent pane competing for the same screen width Amendment (3)
  already fought hard to make viable at 1280px. The icon button lives in
  `ChatSidebarComponent` (already the home of the 3 direct actions: new
  conversation, create group, etc.) rather than `ChatDirectoryComponent`
  or `ChatFullDirectoryComponent`, since it's not scoped to either
  column's row set — it searches across everything, matching where the
  sidebar's other "cross-cutting" actions already live.
- **New service `ChatMessageSearchService`, signals-based, mirroring
  `ChatDirectoryService`'s shape** (private signal + `.asReadonly()` +
  fetch methods owning the HTTP call), kept separate from `ChatService`.
  Why not extend `ChatService`: `ChatService` owns "my conversations +
  their message history," addressed by `conversationId` — this feature's
  query shape is fundamentally different (cross-conversation, filtered,
  cursor-paginated over a distinct backend resource,
  `GET /api/chat/messages/search`, not `.../conversations/{id}/
  messages`), the same reasoning this PLAN's own precedent
  (`chat-unified-ui`'s "keep `ChatGroupService` separate from
  `ChatService`... materially different concern, own error/loading
  shape") already applies to governance vs. messaging. Mixing search
  result state (which needs its own `loading`/`error`/`noResults`
  states per REQ-12/13/14, none of which `ChatService` currently
  models) into `ChatService` would blur two concerns with different
  lifecycles: search state resets every time the dialog closes/reopens,
  conversation state does not.
- **Result-row click opens the conversation via `ChatShellComponent`'s
  existing routing (`/chat/:conversationId`, `/chat/support/...` is
  unreachable here since Support is excluded from search results per
  REQ-1/backend SPEC scope), closing the dialog first, with the target
  message id passed as a query param (`?highlight=<messageId>`) rather
  than true scroll-to-message in v1 — flagged as infeasible to do
  properly in this increment, see below.** Scroll-to-message is not
  implementable without deeper `MessageThreadComponent` changes:
  `ChatService`'s message cache is a cursor-paginated, most-recent-
  first window (`MessageCacheEntry`, `oldestCursor`/`newestCursor`) with
  no "fetch a page centered on message id X" method or backend endpoint
  to back one — `ChatMessageRepository`'s existing `findBeforeCursor`/
  `findAfterCursor` pair (confirmed via the backend PLAN) has no
  "fetch around" variant, and adding one is out of this frontend
  feature's authority to invent (a new backend endpoint, Tier 3/
  cross-repo). **Decision: v1 opens the conversation at its normal
  newest-message view (current behavior, unchanged) and does not
  attempt to scroll to or highlight the matched message** — REQ-11 only
  requires "open that message's conversation... using the existing
  conversation view unchanged," which this satisfies exactly; the
  `?highlight=` query param is **not** added in v1 (removed from the
  above — see "Emergent decision" note), since a query param with no
  consumer would be dead plumbing. This is flagged explicitly, per the
  task's instruction, as a v1 gap rather than silently dropped: a
  fast-follow needs a backend "fetch page containing message X" endpoint
  before real scroll-to-message is possible.
- **Result rows render via a new, small presentational component
  `chat-search-result-row.component.ts`**, not inlined into the dialog
  — mirrors `conversation-list-item.component.ts`'s existing "list row
  is its own component" precedent in this feature area, keeping the
  dialog component itself focused on query/filter/pagination
  orchestration rather than per-row markup.
- **Free-text query is debounced 400ms client-side before firing a
  request, via RxJS `Subject` + `debounceTime`/`distinctUntilChanged` in
  `chat-search-dialog.component.ts` (not `ChatMessageSearchService`)**,
  unlike `chat-directory.component.ts`'s existing (deliberately
  debounce-free) directory search. Why different from that established
  precedent: the SPEC's own non-functional requirements section already
  distinguishes the two cases explicitly — directory search filters an
  already-fetched, in-memory list (REQ-8, no network call per keystroke,
  correctly debounce-free), while this feature's REQ-3 fires an actual
  backend request (`websearch_to_tsquery` + a GIN-indexed query) per
  submission. Debouncing here isn't optional polish, it's what REQ-7
  ("blank query blocked") and the NFR's explicit "debounce/avoid firing
  a new backend request on every keystroke" call for. 400ms matches this
  codebase's existing debounce precedent for text-input-driven backend
  calls (`tenant-pagination-search`'s own search field) rather than
  inventing a new constant. This is a narrow, single-purpose use of
  RxJS operators already available via Angular's `HttpClient`/`rxjs`
  dependency (already in `package.json`) — not a new dependency, and not
  an RxJS *store*, so it doesn't trip the "no RxJS store libraries"
  constraint (constraint targets state-management libraries, not the
  `rxjs` operators Angular's own `HttpClient` already depends on and
  this codebase already imports elsewhere, e.g. `chat.service.ts`'s
  polling logic).
- **Sender/conversation filters are `<select>` dropdowns backed by
  data already available client-side (no new fetch)**: sender options
  come from `ChatDirectoryRowsService`'s already-fetched people rows
  (`eligibleParticipants` + conversation participants, deduplicated by
  user id — the caller's own reachable people, a reasonable bound per
  this feature's own "recall search" framing, not a full tenant user
  directory); conversation options come from `ChatService.conversations()`
  (the caller's own conversation list, `PEER_DIRECT`/`PEER_GROUP` only —
  filtering out `SUPPORT` client-side for the dropdown's candidate set,
  matching the backend's own scope exclusion so no dead option can ever
  be selected that would silently return nothing). No new backend
  lookup endpoint is introduced for populating either filter — reusing
  already-fetched signals keeps this feature from adding request volume
  beyond the search call itself.
- **Date range uses two native `<input type="date">` fields**, not a
  custom date-picker component — no existing date-picker precedent
  exists anywhere in this codebase to reuse or extend, and introducing
  one purely for this feature's two fields would be a disproportionate
  new-UI investment for a Tier-3-adjacent call better deferred until a
  second feature actually needs a shared date-picker. REQ-8 (from > to
  invalid) is validated client-side in the dialog component before
  calling the service, mirroring REQ-7's blank-query guard — both are
  synchronous, form-level checks, no backend round-trip needed to
  detect either.
- **Loading/empty/error/no-results states are four explicit, mutually
  exclusive template branches in `chat-search-dialog.component.ts`**,
  driven by a `status: 'idle' | 'loading' | 'results' | 'no-results' |
  'error'` signal on `ChatMessageSearchService` (not derived
  implicitly from `results().length === 0`, which cannot distinguish
  "never searched yet" from "searched, zero matches," REQ-12's
  explicit distinct-state requirement) — mirrors this app's existing
  convention of an explicit status enum over booleans-that-can-
  contradict-each-other (e.g. `MessageSendState` in `chat.model.ts`).

## Components and routes

```
core/
  chat-message-search.service.ts      // NEW — signals: query/filter state,
                                       //   results, status, cursor; search()/
                                       //   loadMore()/reset() methods
  chat.model.ts                       // + ChatMessageSearchResultDto,
                                       //   ChatMessageSearchFilters,
                                       //   ChatMessageSearchStatus types

features/chat/
  chat-sidebar.component.ts           // CHANGED — one new icon button
                                       //   ("Buscar mensagens"), opens
                                       //   chat-search-dialog
  chat-search-dialog.component.ts     // NEW — native <dialog>: query input
                                       //   (debounced), sender/conversation/
                                       //   date filters, results list,
                                       //   infinite-scroll "load more",
                                       //   4-state status branches
  chat-search-result-row.component.ts // NEW — one result: sender, avatar,
                                       //   conversation title, timestamp,
                                       //   content snippet; click emits
                                       //   (conversationId) to the dialog
  chat-shell.component.ts             // unchanged — result click reuses its
                                       //   existing /chat/:conversationId
                                       //   navigation, no new route needed
```

No routing changes. No new entries in `app.routes.ts` — per SPEC's
explicit "no dedicated full-page search route" exclusion, this is
consumed entirely as an overlay within the existing `/chat` shell; a
result click calls `Router.navigate(['/chat', conversationId])` (or the
group-conversation-id equivalent, already the shape `ChatDirectoryComponent`'s
own row click handler uses today) and closes the dialog, nothing new is
added to `app.routes.ts`.

## Consumed API contracts

Copied verbatim from `knowly-api/specify/features/chat-message-search/PLAN.md`
("API contracts" section, closed/final) — not re-derived:

| Method | Path | Request | Response | Status |
|---|---|---|---|---|
| GET | `/api/chat/messages/search` | Query params: `q` (required, non-blank), `senderId` (optional, number), `conversationId` (optional, number), `dateFrom`/`dateTo` (optional, ISO-8601 instant string), `cursor` (optional, opaque string), `size` (optional, int). `Accept-Language` sent automatically by the existing `localeInterceptor` — the frontend never sets it explicitly for this call. | `ChatMessageSearchPageDto` | 200 |

```ts
interface ChatMessageSearchResultDto {
  id: number;
  conversationId: number;
  conversationTitle: string;
  senderUserId: number;
  senderNickname: string;
  content: string;
  createdAt: string; // ISO-8601 instant
}

interface ChatMessageSearchPageDto {
  results: ChatMessageSearchResultDto[];
  nextCursor: string | null;
}
```

**Error cases** (all surfaced as the dialog's `'error'` status, no
attempt to branch UI per error code beyond REQ-7/REQ-8's own
client-side pre-checks, which prevent two of the three from ever
reaching the backend):

| Condition | Status | Code | Frontend handling |
|---|---|---|---|
| `q` blank | 400 | `CHAT_SEARCH_QUERY_BLANK` | Never sent — REQ-7 blocks client-side first. |
| `dateFrom` after `dateTo` | 400 | `CHAT_SEARCH_INVALID_DATE_RANGE` | Never sent — REQ-8 blocks client-side first. |
| malformed `cursor` | 400 | `CHAT_INVALID_CURSOR` | Only reachable via an internal bug (cursor is opaque, never user-edited) — treated as the generic `'error'` status, same as a network failure; no bespoke UI for this one code. |
| network/5xx | — | — | Generic `'error'` status (REQ-13), previous results left untouched. |

**Inaccessible/nonexistent `conversationId` filter** (backend PLAN's own
explicit note: no distinguishable 403/404, returns `200` with an
empty/short page) — the frontend does **not** attempt to detect or
special-case this; it renders exactly the same "no results for '<query>'"
state (REQ-12) as a genuine zero-match search, per the task's explicit
constraint against inferring that distinction.

## State and data

- **`ChatMessageSearchService`** (signals, new):
  - `_results: Signal<ChatMessageSearchResultDto[]>`, appended to (not
    replaced) on `loadMore()`, replaced on a fresh `search()` call.
  - `_status: Signal<'idle' | 'loading' | 'results' | 'no-results' |
    'error'>`.
  - `_nextCursor: Signal<string | null>` — `null` both when there is no
    next page and before any search has run; `hasMore()` is a
    `computed()` of `_nextCursor() !== null`.
  - `_lastQuery: Signal<string>` — retained only to compose the
    "no results for '<query>'" message (REQ-12), not sent back to the
    backend on `loadMore()` beyond what the cursor already implies.
  - `search(filters: ChatMessageSearchFilters): void` — validates
    nothing itself (REQ-7/8's blank/invalid-range checks live in the
    dialog component, since they're pure input validation with no
    service-state dependency), replaces `_results`, resolves `_status`
    from the response (`results.length === 0 ? 'no-results' :
    'results'`), sets `'error'` on failure **without clearing
    `_results`** (REQ-13's explicit "shall not clear any previously
    displayed results").
  - `loadMore(): void` — no-ops if `_nextCursor()` is `null` or
    `_status()` is already `'loading'`; appends the new page's results
    to `_results`, updates `_nextCursor`.
  - `reset(): void` — called by `chat-search-dialog.component.ts` on
    close, so reopening the dialog starts from `'idle'`, not stale
    results from a prior session (REQ-1/REQ-2's "distinct entry point"
    implies a fresh search each time it's opened, not a persistent
    session — no SPEC requirement calls for search-session persistence
    across dialog close/reopen, and persisting it would need explicit
    justification this SPEC doesn't provide).
  - `ChatMessageSearchFilters`: `{ q: string; senderId?: number;
    conversationId?: number; dateFrom?: string; dateTo?: string }` — no
    `cursor`/`size` fields; `loadMore()` derives `cursor` from
    `_nextCursor()` internally, `size` is omitted (backend default).
- **`chat-search-dialog.component.ts`** owns transient UI-only state not
  worth promoting to the service: the debounced-`Subject` plumbing, the
  raw (not-yet-submitted) filter form values, and the REQ-7/REQ-8
  client-side validation-error messages. This mirrors
  `create-group-dialog.component.ts`'s existing split (form state local
  to the dialog, submitted-result state in a service) rather than a new
  pattern.
- Infinite scroll (REQ-10) reuses this app's existing
  `IntersectionObserver`-based "load more" trigger already established
  by `message-thread.component.ts`'s own pagination-on-scroll behavior,
  not a new scroll-listener implementation.

## Dependencies

None. RxJS `debounceTime`/`distinctUntilChanged`/`Subject` are already
transitive dependencies of Angular's `HttpClient` and already imported
elsewhere in this codebase (`chat.service.ts`'s polling) — no
`package.json` change. No new UI library — the dialog reuses the native
`<dialog>` element precedent, `@lucide/angular` for the new search icon
(already a dependency), and existing Tailwind utility classes.

## i18n keys

New keys under `chat.search.*`, both `en` and `pt-BR` locale files
(content sketched, not final copy — that's a PO/UX concern, not this
PLAN's):

- `chat.search.entryPointLabel` — icon button `aria-label`/tooltip
  (REQ-1/REQ-2: "Search messages" / "Buscar mensagens").
- `chat.search.dialogTitle`
- `chat.search.queryPlaceholder` — REQ-2's "make clear it searches
  content, not names" (e.g. "Search message content..." /
  "Buscar no conteúdo das mensagens...").
- `chat.search.filterSenderLabel`
- `chat.search.filterConversationLabel`
- `chat.search.filterDateFromLabel`
- `chat.search.filterDateToLabel`
- `chat.search.blankQueryError` — REQ-7.
- `chat.search.invalidDateRangeError` — REQ-8.
- `chat.search.loading` — REQ-14.
- `chat.search.noResults` — REQ-12 (interpolates `{{ query }}`).
- `chat.search.error` — REQ-13.
- `chat.search.loadMore` — REQ-10's manual fallback trigger (in
  addition to the scroll-based one, matching this codebase's existing
  a11y-friendly "also expose a button, not scroll-only" convention).
- `chat.search.resultA11yLabel` — per-row `aria-label` composing sender
  + conversation + timestamp (interpolated), for `chat-search-result-
  row.component.ts`'s accessibility per the SPEC's NFR.

## Testing strategy (Vitest)

- `chat-message-search.service.spec.ts` (`HttpTestingController`-based):
  - `search()` calls `GET /api/chat/messages/search` with `q` and only
    the filters actually set (omits `senderId`/`conversationId`/
    `dateFrom`/`dateTo` entirely when unset, not `null`/empty-string
    params).
  - `search()` success with `results: []` sets status `'no-results'`,
    not `'results'`.
  - `search()` failure leaves a prior non-empty `_results` untouched
    (REQ-13's core regression test) and sets status `'error'`.
  - `loadMore()` appends to, not replaces, existing results, and sends
    the correct `cursor` param; no-ops when `_nextCursor()` is `null`.
  - `reset()` returns to `'idle'` with empty results and `null` cursor.
- `chat-search-dialog.component.spec.ts`:
  - REQ-7: submitting a blank/whitespace-only query shows
    `blankQueryError` and makes zero HTTP calls.
  - REQ-8: `dateFrom` after `dateTo` shows `invalidDateRangeError` and
    makes zero HTTP calls.
  - Debounce: typing rapidly into the query field triggers exactly one
    request after the 400ms window, not one per keystroke (fake timers).
  - REQ-4/5/6: selecting sender/conversation/date filters includes them
    in the next `search()` call, individually and combined (a
    combined-filter case, not just each alone).
  - REQ-9: results render in the order the service returns them (no
    client-side re-sort), each row showing sender/conversation/
    timestamp/content.
  - REQ-10: scrolling to the sentinel element (or clicking
    `chat.search.loadMore`) calls `loadMore()`.
  - REQ-11: clicking a result row navigates to
    `/chat/:conversationId` for that result's `conversationId` and
    closes the dialog (asserted via a `Router` spy, matching
    `chat-directory.component.spec.ts`'s existing navigation-assertion
    pattern) — explicitly asserts **no** scroll-to-message/highlight
    param is added, confirming the documented v1 scope decision above
    rather than silently reintroducing dead plumbing later.
  - REQ-12/13/14: the four `status` values each render their own
    distinct, mutually exclusive block (a table-driven test asserting
    exactly one status block is present in the DOM per state, not
    just that the right text exists alongside a stale other-state
    block).
  - Dialog close calls `ChatMessageSearchService.reset()` (confirms the
    "fresh search on reopen" decision above).
- `chat-search-result-row.component.spec.ts`: renders sender/
  conversation/timestamp/content; emits its output on click and on
  `Enter`/`Space` keydown (keyboard-navigable per NFR); has the
  interpolated `aria-label`.
- `chat-sidebar.component.spec.ts` (extended): new search icon button
  present, keyboard-reachable, `aria-label`d, opens the dialog on
  click — same assertion shape already used for the sidebar's 3
  existing action buttons.
- Regression: existing `chat-directory.component.spec.ts`/
  `chat-full-directory.component.spec.ts` suites are unmodified by this
  feature (their own name-only search fields and state are untouched,
  per SPEC's explicit "does not touch chat-unified-ui's existing
  search" framing) — no new test needed there beyond confirming (by
  omission) that neither file is touched by this feature's tasks.

## Open dependency on backend feasibility work

Scroll-to-message-on-open (referenced in REQ-11's user story) is
explicitly **not implemented in this PLAN** — see the "Result-row click"
decision above. If a future increment wants it, it needs a new backend
capability (a "fetch a message page centered on id X" endpoint) that
does not exist today per the closed backend PLAN; that is a new
cross-repo feature, not a gap in this PLAN's scope, and is flagged here
rather than silently omitted.
