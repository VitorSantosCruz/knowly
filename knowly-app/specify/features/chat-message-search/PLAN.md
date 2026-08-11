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

## Amended (2026-08-10) — unified Slack-style search bar

> Everything above this heading describes the retired filter-dialog
> design (REQ-1 through REQ-14, now superseded per SPEC.md's own
> amendment) and is kept for history/context, not deleted, per this
> repo's convention. This section is the authoritative PLAN for
> REQ-15 through REQ-31. **Backend contract consumed verbatim from
> `knowly-api/specify/features/chat-message-search/PLAN.md`'s own
> "Amended (2026-08-10)" section (closed, source of truth) for the new
> `GET /api/chat/search` endpoint; `GET /api/chat/messages/search`'s
> contract (copied above, unchanged) is still consumed as-is for the
> "Messages" result group.** Companion to
> `knowly-app/specify/features/chat-unified-ui/PLAN.md`'s own amendment
> for this same date — that document owns where the bar lives in the
> 3-column shell; this section owns what happens inside it.

### Confirming the "does unified search still cover message content?" question

**Yes — re-read directly from SPEC.md, not assumed.** REQ-21 lists
"Messages" (matching message content) as one of the at-minimum three
result groups; REQ-17's "populate results across every applicable
group (REQ-21)" includes it; REQ-24 (click a message result) and REQ-30
(partial-failure note, "two independent backend calls... per the
not-yet-defined combined contract") both explicitly anticipate message
content staying a live, separately-sourced result kind, not folded into
the new `GET /api/chat/search` entity endpoint (which the backend PLAN
confirms has no message-content section at all — only
people/groups/Support/RAG/recent-places). **Conclusion: the unified bar
fires two backend calls per non-blank query** — `GET /api/chat/search`
(entities) and `GET /api/chat/messages/search` (message content, exact
same contract this feature already shipped) — never one. REQ-30
therefore does **not** collapse into REQ-28 (the "single combined
endpoint" branch of its own PLAN-level note) — it stays the two-call,
per-group-error-isolation case, since that is what the finalized
backend contract actually delivered.

### Architectural decisions

- **Retiring `chat-search-dialog.component.ts` entirely, not just its
  filter form.** SPEC's question 3 answer (persistent top bar, not a
  modal opened from a sidebar icon) removes the entry point this
  component existed to host, not only the Sender/Conversation/From/To
  fields inside it — REQ-15's "exactly one search entry point... no
  other search field exists anywhere" leaves no place for a
  dialog-triggered-by-icon-button pattern to survive alongside the bar.
  The file and its spec are deleted; `chat-sidebar.component.ts`'s
  "Buscar mensagens" icon button (added by the original PLAN) is
  removed in the same change, since the bar it opened no longer exists
  — `chat-unified-ui/PLAN.md`'s companion amendment tracks that removal
  on its side (`ChatSidebarComponent` itself is being restructured
  there for other reasons too).
- **New component, `chat-unified-search.component.ts`, replaces it —
  extends this feature's ownership of "search behavior" rather than
  becoming a `chat-unified-ui`-owned component**, even though it
  physically renders inside `ChatShellComponent`'s new header region.
  Why this split: SPEC.md's own framing draws the line at "this
  document owns search behavior (query semantics, result types,
  grouping, recent places)... that one owns where it lives on screen" —
  a component boundary that puts the *type-ahead/dropdown* logic here
  and only the *host slot* in `chat-unified-ui` keeps that same
  ownership line in the code, not just the docs. `chat-unified-ui/PLAN.md`
  places a `<app-chat-unified-search>` selector inside its new header
  region and passes it nothing beyond structural CSS context (no inputs
  needed — the component is self-contained, reading `ActiveTenantService`/
  `AuthService` directly the way `ChatSidebarComponent` already does for
  its own tenant-gating). File lives in `features/chat/` (this feature's
  established location for chat-search-area components), not a new
  folder.
- **Two services, not one, mirroring the backend's own service split**
  (`ChatEntitySearchService` kept separate from `ChatMessageSearchService`
  on the backend, "materially different authorization shape"):
  - **`ChatMessageSearchService` is kept, not renamed, with its
    Sender/Conversation/From/To filter-building removed** — its
    `search(filters)` signature narrows to `search(q: string)` (no more
    `senderId`/`conversationId`/`dateFrom`/`dateTo`), everything else
    (cursor pagination, `status` signal, `reset()`) is unchanged, since
    it still calls the exact same `GET /api/chat/messages/search`
    contract, just always with only `q` set. Kept as the same service
    (not retired and rebuilt) because REQ-15/REQ-17 don't change what
    message-content search *is* — they change how many other kinds of
    result appear alongside it and how it's triggered (type-ahead vs.
    submit). This is "the same feature being redesigned, not a parallel
    one," per the task's own framing — extending in place, not
    introducing a new parallel service for the exact same backend call.
  - **New `ChatEntitySearchService`** (signals, new file
    `chat-entity-search.service.ts`), owning `GET /api/chat/search`:
    `search(q: string)` (non-blank query → grouped entity sections),
    `recentPlaces()` (blank query → REQ-19/20), `expandSection(type:
    'people'|'groups'|'rag', offset: number)` (REQ-22's "see more").
    Kept separate from `ChatMessageSearchService` rather than merged
    into one "unified search service," mirroring the backend's own
    stated reasoning (four independent sub-queries against different
    tables, no shared query shape) — plus a frontend-specific reason:
    the two services have genuinely different trigger conditions
    (`ChatEntitySearchService.recentPlaces()` fires *only* on blank
    query, `ChatMessageSearchService.search()` never fires on a blank
    query at all, REQ-17's "non-blank query" precondition), so merging
    them would need an internal blank/non-blank branch duplicating what
    the orchestrating component already has to do anyway.
  - **`chat-unified-search.component.ts` orchestrates both**: one
    debounced `Subject` (see below) feeds both services' `search(q)` in
    parallel on every non-blank keystroke-settle, and
    `ChatEntitySearchService.recentPlaces()` alone on blank/open. This
    is the seam where REQ-21's grouping and REQ-30's per-group partial
    failure actually get composed into one dropdown — neither service
    knows about the other's status, only the component merges their
    `status`/`results` signals into the five rendered groups (People,
    Groups, Support, RAG, Messages) plus the "recent places" state.
- **Debounce: reuse the shipped 400ms RxJS `Subject` +
  `debounceTime`/`distinctUntilChanged` pattern from
  `chat-search-dialog.component.ts`, moved into
  `chat-unified-search.component.ts` unchanged in mechanism** — REQ-17
  explicitly leaves the interval a PLAN-level decision and gives no
  reason to deviate from the already-established, already-tested
  constant; the pattern fires **both** services' `search(q)` calls off
  the same debounced emission (one shared `Subject`, two `subscribe`-side
  calls), not two independently-debounced pipelines, so the two result
  sets settle together rather than flickering in at different times for
  the same keystroke.
- **Result grouping (REQ-21): five sections, not the SPEC's stated
  minimum of three, since Support and RAG get their own groups rather
  than folding into "Groups"** — SPEC explicitly leaves this choice to
  "PLAN's discretion" as long as all four entity kinds are represented
  somewhere. Decision: Support renders as its own single-row group
  (mirrors the backend DTO's own shape — `support` is a nullable single
  object, not a list, so treating it as a list-of-zero-or-one inside
  "Groups" would need an artificial cast) and RAG conversations get
  their own "Base de artigos" group rather than folding into "Groups" —
  folding RAG into Groups would conflate two conceptually distinct
  kinds the product owner explicitly named as separate in SPEC's
  question 1 ("channels" maps to *both* groups and RAG, not one
  combined bucket), and REQ-2 of `chat-unified-ui` already reserves
  "Base de artigos" as its own kind of row everywhere else in this app
  — consistency with that existing mental model outweighs the minor
  extra group. Order: People, Groups, Base de artigos, Support,
  Messages — entity kinds first (mirroring the backend response's own
  people/groups/support/rag field order), Messages last since it's the
  "recall, not browse" kind of result, matching Slack's own convention
  of content matches trailing entity matches.
- **"See more" (REQ-22) is per-group, calling
  `ChatEntitySearchService.expandSection(type, offset)` for
  People/Groups/RAG only** — Support never gets a "see more" (backend
  DTO caps it at one-or-none, no `hasMore` concept exists for it) and
  Messages' own "see more" reuses `ChatMessageSearchService.loadMore()`
  unchanged (cursor-based, already shipped, no change needed — REQ-22's
  "never a single global load more" is satisfied by each group calling
  its own independent expand mechanism, cursor for Messages, offset for
  the other three, exactly matching each backend contract's own
  pagination style rather than forcing one shape onto both). Expanding
  one group's results appends to only that group's own signal, the
  other four groups' state is untouched.
- **"Recent places" (REQ-19/20): capped at 8 entries client-side**
  (SPEC leaves the exact count to PLAN; 8 matches this app's other
  "short list" precedents — e.g. `tenant-pagination-search`'s own
  default page size floor — and comfortably fits one screen's dropdown
  height without scrolling on the smallest supported viewport). The
  backend already returns a capped, merged, ordered list (backend
  PLAN's k-way merge of `listConversations` + RAG `list`) — the
  frontend does not re-sort or re-slice beyond trusting that cap, it
  only renders `ChatEntitySearchService.recentPlaces()`'s `recentPlaces`
  array as-is.
- **Partial failure (REQ-30, two-call case, confirmed above): each of
  the (up to) five groups tracks its own `status` independently** —
  `ChatEntitySearchService` exposes one `status` **per section**
  (`peopleStatus`, `groupsStatus`, `supportStatus`, `ragStatus`, all
  `'loading' | 'ok' | 'error'`, derived from the backend's own
  per-section try/catch degrade-to-empty behavior plus a frontend-level
  distinction: the backend PLAN degrades a failed section to an *empty*
  result with no error signal at the HTTP layer, so `status` here is
  actually driven by **HTTP-request-level** failure only — a 5xx/network
  failure on the whole `GET /api/chat/search` call marks all four entity
  sections `'error'` simultaneously (there's no way to fail one section
  without the others at the transport level, since it's one response),
  while `ChatMessageSearchService`'s own `status` fails independently
  since it's a wholly separate HTTP call. **This narrows REQ-30's
  "group-scoped error" to two failure domains, not five** — "entities
  (all four)" vs. "messages" — which is the actual granularity the
  finalized one-endpoint-for-entities design allows; a true per-entity-
  kind partial failure (e.g. only Groups failing while People succeeds)
  cannot be distinguished by the frontend under this contract, since the
  backend already silently degrades that case to an empty section with
  no error flag rather than surfacing it. This is flagged here as a
  known granularity gap versus REQ-30's literal five-way reading, not
  silently narrowed — if the product owner wants true five-way partial
  failure visibility, the backend DTO needs a per-section
  `error: boolean` flag added, which is not in the finalized contract
  and would be a further backend PLAN amendment, not something this
  frontend PLAN can invent.
- **Loading/error/no-results/idle states**: `chat-unified-search
  .component.ts` derives one top-level `status: 'idle' | 'loading' |
  'results' | 'no-results' | 'error'` (REQ-27/28/29) as a `computed()`
  over the two services' combined section statuses — `'loading'` while
  any in-flight section is loading, `'error'` only when **every**
  section that was queried failed (a true global failure, matching
  REQ-28's "distinct from no results" framing), `'no-results'` when
  every queried section succeeded with zero rows, `'results'` otherwise
  — mirrors the shipped dialog's existing explicit-status-enum
  convention (not an implicit `results.length === 0` check) rather than
  inventing a new pattern.
- **Dismiss/reopen (REQ-31)**: closing the dropdown (click-away,
  Escape, or a result click per REQ-26) calls `reset()` on **both**
  services — `ChatMessageSearchService.reset()` (already shipped,
  unchanged) and a new `ChatEntitySearchService.reset()` (clears all
  four section signals and `recentPlaces`) — so reopening always starts
  from a fresh `recentPlaces()` fetch (REQ-19), never stale results from
  the prior session, matching REQ-31 exactly.
- **Opening a result (REQ-23/24/25/26)**: `chat-unified-search
  .component.ts` reuses `ChatShellComponent`'s existing
  `/chat/:conversationId` / `/chat/support/:channelId` /
  `/chat/articles/:conversationId` navigation (the same three-path-space
  routing `chat-unified-ui/PLAN.md` already establishes) — a person or
  group result navigates to `/chat/:conversationId` (backend's
  `ChatGroupSearchResultDto.id`/derived direct-conversation id, same as
  today's directory-row click), a Support result to
  `/chat/support/:channelId` (`ChatSupportSearchResultDto.channelId`), a
  RAG result to `/chat/articles/:conversationId`
  (`ChatRagConversationSearchResultDto.id`), a message result to
  `/chat/:conversationId` for `ChatMessageSearchResultDto.conversationId`
  (the already-shipped v1 "no scroll-to-message" decision above, carried
  forward unchanged), and a "recent places" entry (REQ-25) dispatches on
  `ChatRecentPlaceDto.kind` (`PEER_DIRECT`/`PEER_GROUP` →
  `/chat/:conversationId`, `SUPPORT` → `/chat/support/:conversationId`
  — the backend's `ChatRecentPlaceDto` reuses `conversationId` as the
  field name for all four kinds, the frontend maps it to whichever path
  segment that kind needs — `RAG` → `/chat/articles/:conversationId`).
  Every click also calls both services' `reset()` (REQ-26's "closes the
  dropdown") before navigating.
- **A "person" search result opening a 1:1 with no existing
  conversation yet** (a name match with no `chat_participants` row)
  reuses REQ-3's existing create-and-open behavior from
  `chat-unified-ui` (`ChatDirectoryComponent`'s row click already does
  this) — `chat-unified-search.component.ts` calls the same
  `ChatService`/`ChatDirectoryRowsService`-backed click handler
  `chat-unified-ui`'s column 1/3 rows already use (extracted to a small
  shared `openPersonConversation(userId)` helper on `ChatService`, if
  not already one, rather than duplicating the create-or-open logic a
  third time), not a new code path.

### Components and routes

```
core/
  chat-message-search.service.ts      // CHANGED — search(q) only, filter
                                       //   params removed; cursor/status/
                                       //   reset unchanged
  chat-entity-search.service.ts       // NEW — search(q)/recentPlaces()/
                                       //   expandSection(type, offset)/
                                       //   reset(); per-section status
  chat.model.ts                       // + ChatPersonSearchResultDto,
                                       //   ChatGroupSearchResultDto,
                                       //   ChatSupportSearchResultDto,
                                       //   ChatRagConversationSearchResultDto,
                                       //   ChatRecentPlaceDto,
                                       //   ChatEntitySearchSectionStatus
                                       //   types (mirroring backend DTOs
                                       //   verbatim); ChatMessageSearchFilters
                                       //   narrowed to `{ q: string }`

features/chat/
  chat-unified-search.component.ts    // NEW — replaces chat-search-dialog:
                                       //   the bar's dropdown content
                                       //   (debounced input owned by
                                       //   chat-unified-ui's header slot,
                                       //   or owned here — see
                                       //   chat-unified-ui/PLAN.md's
                                       //   amendment for the exact split),
                                       //   5-group results, recent places,
                                       //   see-more, 5-state status
  chat-search-result-row.component.ts // CHANGED — gains a `kind: 'person'|
                                       //   'group'|'support'|'rag'|
                                       //   'message'` discriminator input
                                       //   (was message-only), renders the
                                       //   right icon/avatar/subtitle per
                                       //   kind
  chat-search-dialog.component.ts     // REMOVED
  chat-sidebar.component.ts           // CHANGED (in chat-unified-ui's own
                                       //   restructuring) — "Buscar
                                       //   mensagens" icon button removed
```

### Consumed API contracts

Copied verbatim from `knowly-api/specify/features/chat-message-search/PLAN.md`'s
"Amended (2026-08-10)" section ("API contracts" table, closed/final) —
not re-derived:

| Method | Path | Request | Response | Status |
|---|---|---|---|---|
| GET | `/api/chat/search` | `q` (optional — blank triggers recent places), `type` (optional, `people`\|`groups`\|`rag`, "see more" expand), `offset` (optional, int, paired with `type`) | `ChatEntitySearchResultDto` (blank `q`) or `ChatEntitySearchResponseDto` (non-blank `q`), or `ChatEntitySearchSectionDto<T>` (expand form) | 200 |
| GET | `/api/chat/messages/search` | `q` (required, non-blank) only — `senderId`/`conversationId`/`dateFrom`/`dateTo` never sent by the unified bar | `ChatMessageSearchPageDto` (unchanged from the original PLAN) | 200 |

```ts
interface ChatPersonSearchResultDto {
  userId: number;
  nickname: string;
  avatarUrl: string | null;
}
interface ChatGroupSearchResultDto {
  id: number;
  title: string;
  isParticipant: boolean;
  visibility: 'PRIVATE' | 'REQUEST_TO_JOIN' | 'PUBLIC';
}
interface ChatSupportSearchResultDto {
  channelId: number;
}
interface ChatRagConversationSearchResultDto {
  id: number;
  title: string;
}
interface ChatEntitySearchSectionDto<T> {
  results: T[];
  hasMore: boolean;
}
interface ChatEntitySearchResponseDto {
  people: ChatEntitySearchSectionDto<ChatPersonSearchResultDto>;
  groups: ChatEntitySearchSectionDto<ChatGroupSearchResultDto>;
  support: ChatSupportSearchResultDto | null;
  rag: ChatEntitySearchSectionDto<ChatRagConversationSearchResultDto>;
}
interface ChatRecentPlaceDto {
  conversationId: number;
  kind: 'PEER_DIRECT' | 'PEER_GROUP' | 'SUPPORT' | 'RAG';
  title: string;
  orderingTimestamp: string; // ISO-8601 instant
}
interface ChatEntitySearchResultDto {
  recentPlaces: ChatRecentPlaceDto[];
}
```

**Error cases** (`ChatEntitySearchService`):

| Condition | Status | Code | Frontend handling |
|---|---|---|---|
| `type` without `offset` or vice versa, invalid `type` | 400 | `CHAT_SEARCH_INVALID_EXPAND_PARAM` | Only reachable via an internal bug (params are never user-typed) — treated as that section's generic `'error'`, same as a network failure. |
| network/5xx on `GET /api/chat/search` | — | — | All four entity sections marked `'error'` simultaneously (see "Partial failure" decision above); Messages section unaffected (separate call). |
| network/5xx on `GET /api/chat/messages/search` | — | — | `ChatMessageSearchService.status → 'error'` (unchanged from shipped behavior); entity sections unaffected. |

No `403`/`404` for any inaccessible match of any kind (backend's
"omit, never reveal" posture) — the frontend never attempts to detect
or special-case this, same as the shipped feature's existing
`conversationId` filter precedent.

### State and data

- **`ChatMessageSearchService`** (changed): `search(q: string)`
  replaces `search(filters: ChatMessageSearchFilters)`;
  `ChatMessageSearchFilters` narrows to `{ q: string }`. `_results`,
  `_status`, `_nextCursor`, `_lastQuery`, `loadMore()`, `reset()` are
  otherwise byte-for-byte unchanged from the shipped implementation.
- **`ChatEntitySearchService`** (new):
  - `_people: Signal<ChatPersonSearchResultDto[]>`,
    `_peopleHasMore: Signal<boolean>`, `_peopleStatus: Signal<'idle' |
    'loading' | 'ok' | 'error'>` — and the same trio for `groups`/`rag`.
  - `_support: Signal<ChatSupportSearchResultDto | null>`,
    `_supportStatus` (no `hasMore` — REQ-22 exempts Support).
  - `_recentPlaces: Signal<ChatRecentPlaceDto[]>`, `_recentPlacesStatus`.
  - `search(q: string): void` — calls `GET /api/chat/search?q=...`,
    fans the one response out into all four section signals + statuses
    in one write (matches the backend's single-HTTP-call design — there
    is genuinely one response to unpack, not four independent
    sub-requests).
  - `recentPlaces(): void` — calls `GET /api/chat/search` with no `q`
    (or `q=''`), populates `_recentPlaces`/`_recentPlacesStatus` only;
    leaves the four entity sections untouched (REQ-20's "replaced by
    live search groups the moment the query becomes non-blank... not
    merged into a mixed empty+results view" — the component, not this
    service, decides which signal set to render based on whether the
    query is blank, so both can coexist in the service without
    conflicting).
  - `expandSection(type: 'people' | 'groups' | 'rag', currentQuery:
    string): void` — calls `GET /api/chat/search?q=...&type=...
    &offset=<current section's results.length>`, **appends** to that
    section's own results array and updates only that section's
    `hasMore`, leaving the other three untouched.
  - `reset(): void` — clears every section back to `idle`/empty (REQ-31).
- **`chat-unified-search.component.ts`** owns: the debounced `Subject`
  (400ms), the raw (not-yet-debounced) query input value, the derived
  top-level `status` computed described above, and the merge of both
  services' data into the five rendered `<section>` blocks + the
  "recent places" block. No new shared state is promoted onto either
  service beyond what's listed above — this mirrors the shipped
  dialog's own "form/orchestration state local to the component,
  request/result state in the service(s)" split.

### Dependencies

None. Same RxJS operators already in use by the shipped feature; no new
`package.json` entry.

### i18n keys

New/changed keys under `chat.search.*`, both `en` and `pt-BR` (content
sketch, not final copy):

- `chat.search.barPlaceholder` — REQ-16 ("Buscar pessoas, grupos ou
  mensagens" / "Search people, groups, or messages") — **replaces**
  the retired `chat.search.entryPointLabel`/`queryPlaceholder` (those
  described a content-only surface, no longer accurate).
- `chat.search.groupLabelPeople` / `.groupLabelGroups` /
  `.groupLabelSupport` / `.groupLabelRag` / `.groupLabelMessages` —
  REQ-21's five section headers.
- `chat.search.recentPlacesLabel` — REQ-19's section header.
- `chat.search.seeMore` — REQ-22, per-group (interpolates a group-name
  param so screen readers announce which group is expanding, e.g. "Ver
  mais em Grupos").
- `chat.search.noResults` — REQ-27 (interpolates `{{ query }}`,
  retained unchanged from shipped).
- `chat.search.error` — REQ-28 (retained unchanged).
- `chat.search.loading` — REQ-29 (retained unchanged).
- `chat.search.resultA11yLabelPerson` / `.resultA11yLabelGroup` /
  `.resultA11yLabelSupport` / `.resultA11yLabelRag` /
  `.resultA11yLabelMessage` — replace the single, message-only
  `chat.search.resultA11yLabel`, one per kind since each composes
  different fields into its accessible name.
- **Removed**: `chat.search.dialogTitle`, `.filterSenderLabel`,
  `.filterConversationLabel`, `.filterDateFromLabel`,
  `.filterDateToLabel`, `.blankQueryError`, `.invalidDateRangeError`,
  `.loadMore` (Messages now reuses the manual scroll trigger without a
  distinct label — folded into `.seeMore` with a `messages`
  group-name param) — all described the retired filter-form/dialog
  surface.

### Testing strategy (Vitest)

- `chat-message-search.service.spec.ts` (extended, mostly unchanged):
  removes the filter-param assertions (senderId/conversationId/date
  range no longer exist to test); keeps the `q`-only request shape,
  `'no-results'`/`'error'`/`loadMore()`/`reset()` cases verbatim.
- `chat-entity-search.service.spec.ts` (new, `HttpTestingController`):
  - `search(q)` populates all four sections from one response; a
    `support: null` response leaves `_support` `null`, not an error.
  - `recentPlaces()` calls with blank `q` and populates only
    `_recentPlaces`, leaving entity sections at their prior state.
  - `expandSection('groups', ...)` sends the correct `type`/`offset`
    pair and **appends** to `_groups`, not replaces; a second call
    right after asserts cumulative growth (regression against an
    accidental "replace" bug).
  - A simulated network failure on `search(q)` marks all four entity
    section statuses `'error'` simultaneously — direct regression test
    for the "two failure domains, not five" decision above, so this
    known granularity gap is pinned by a test rather than left
    implicit.
  - `reset()` returns every section to `idle`/empty.
- `chat-unified-search.component.spec.ts` (new, replaces
  `chat-search-dialog.component.spec.ts`):
  - REQ-17: typing a non-blank query debounces 400ms then fires **both**
    `ChatMessageSearchService.search()` and
    `ChatEntitySearchService.search()` exactly once per settled
    keystroke burst (fake timers), not per-keystroke, not one service
    without the other.
  - REQ-19/20: opening with a blank query shows `recentPlaces()`'s
    result, capped at 8, replaced entirely (not merged) the instant a
    non-blank query is typed.
  - REQ-21: results render in the five-group order decided above; a
    group with zero matches for the current query is entirely absent
    from the DOM (not rendered empty).
  - REQ-22: a group's "see more" action calls only that group's
    `expandSection`/`loadMore`, verified via spies that the other four
    groups' fetch methods are not called.
  - REQ-23/24/25: clicking a person/group/Support/RAG/message/recent-
    place result navigates to the correct path per the routing table
    above (table-driven test, one row per kind) and calls `reset()` on
    both services (REQ-26).
  - REQ-27/28/29: the five-state `status` computed renders the right
    exclusive block per combination of section statuses, including the
    "all entity sections error, Messages still succeeds" case (asserts
    the top-level status is **not** `'error'` in that case, since REQ-28
    requires "distinct from no results," and a mixed success/failure
    state is neither pure error nor pure success — resolves to
    `'results'` showing Messages plus an inline error badge on the
    failed entity groups, per REQ-30).
  - REQ-31: Escape/click-away calls `reset()` on both services; a
    reopen re-fetches `recentPlaces()`, not the last query's results.
  - Accessibility: each group has its own `role="group"`/labelled
    heading, each result row is keyboard-reachable and has the correct
    per-kind `aria-label` (table-driven against the i18n keys above).
- `chat-search-result-row.component.spec.ts` (extended): one test per
  `kind` value asserting the right icon/subtitle/`aria-label`
  combination renders; keyboard activation (`Enter`/`Space`) unchanged
  from shipped.
- Regression: `chat-search-dialog.component.spec.ts` deleted alongside
  its component; no dangling spec against a removed file.
- Regression: `chat-directory.component.spec.ts`/
  `chat-full-directory.component.spec.ts` — their own search-field
  removal is `chat-unified-ui/PLAN.md`'s amendment's test surface, not
  this feature's; not duplicated here.

### Open items carried forward

- Scroll-to-message-on-open remains v1-out-of-scope, unchanged from the
  original PLAN's decision (still needs a "fetch page centered on
  message id X" backend endpoint that does not exist).
- **REQ-30's "five-way partial failure" granularity gap** (see
  "Partial failure" decision above) — the finalized single-endpoint
  backend contract can only report failure at "all four entity
  sections" or "Messages" granularity, not per-entity-kind. This should
  be read back to the product owner/PO agent alongside the backend
  PLAN's own REQ-26 Tier-2 flag before TASKS.md, since it's a real,
  user-visible narrowing of what REQ-30 literally describes (a group
  that individually failed inside the backend's per-section try/catch
  is indistinguishable, from the frontend's vantage point, from a group
  that simply had zero matches) — not a blocker, but worth an explicit
  sign-off alongside the backend's own already-flagged Tier-2 item.

## Amended (2026-08-10) — highlight matched text + jump-to-message

> Authoritative PLAN for REQ-32 through REQ-37 (SPEC.md's second
> 2026-08-10 amendment). No backend dependency — purely frontend
> rendering/navigation, no new API contract. Closes the "Scroll-to-
> message-on-open... not implemented in this PLAN" gap noted above and
> in "Open dependency on backend feasibility work," on different terms
> than originally assumed: no "fetch page centered on message X"
> backend endpoint is needed, because `ChatService.loadOlderMessages`
> already exists and repeated calls are sufficient — that endpoint was
> only ever required for an *efficient single-call* jump, not a
> functionally correct one.

### Architectural decisions

- **New pure function `splitOnMatch(content: string, query: string):
  { before: string; match: string; after: string } | null` in
  `chat.model.ts`** (co-located with the other pure helpers there,
  e.g. `deriveViewerRelation`), first-occurrence-only,
  case-insensitive substring match via `content.toLowerCase()
  .indexOf(query.toLowerCase())`. **First match only, not all
  occurrences** — REQ-32/REQ-36 both say "the matched substring"
  (singular) and the SPEC's own framing is "confirm what matched,"
  not "audit every occurrence"; marking every occurrence in a long
  message body risks visual noise disproportionate to the ask, and
  nothing in REQ-32/36 or the acceptance criteria asks for it. If the
  product owner wants all-occurrences highlighting later, this is a
  one-line change inside `splitOnMatch` (return an array of segments
  instead of a single triple) — flagged here as the cheap reversal
  path, not built as an option now (YAGNI, no requirement calls for
  it). A `null` return (no literal substring match) means the caller
  renders the content unstyled — REQ-32's explicit "does not change
  what counts as a result" carve-out.
- **`chat-search-result-row.component.ts` (already `kind`-discriminated
  per the prior amendment) consumes `splitOnMatch` directly in its
  template for `kind === 'message'` rows only** via a `highlighted =
  computed(() => splitOnMatch(this.result().content, this.query()))`
  and renders `{{ before }}<mark>{{ match }}</mark>{{ after }}` when
  non-null, plain `{{ content }}` when null. `query` becomes a new
  required input (`input.required<string>()`) on the row component —
  it previously had no need for the raw query string; now REQ-32
  requires it.
- **Routing (REQ-33/34): router state, not query params, not a new
  route.** `onEntitySelect`'s `'message'` case changes from
  `this.router.navigate(['/chat', result.conversationId])` to
  `this.router.navigate(['/chat', result.conversationId], { state: {
  jumpToMessageId: result.id, jumpToQuery: this.query() } })`.
  Confirmed over query params: SPEC's own "Out of scope" explicitly
  excludes deep-linking (`?message=123` is called out by name as *not*
  wanted), and `Router` state is exactly the mechanism this codebase
  already reaches for when data needs to survive one navigation
  without becoming a bookmarkable/shareable URL or polluting browser
  history entries — no existing precedent for `state:` elsewhere in
  this codebase yet, but it's a built-in `@angular/router` capability
  (already a dependency), not a new one, and it's the direct
  counter-shape to what "Out of scope" rules out. `ConversationDetail
  Component` reads it via `this.router.getCurrentNavigation()?.extras
  .state` inside its constructor (state is only available synchronously
  during navigation, not reliably via `history.state` read later in
  `ngOnInit` after Angular's own post-navigation `history.replaceState`
  calls may have run) — captured into two local signals,
  `jumpToMessageId = signal<number | undefined>(undefined)` and
  `jumpToQuery = signal<string | undefined>(undefined)`, cleared
  (`set(undefined)`) once consumed so a same-conversation re-render
  (e.g. polling) doesn't repeatedly attempt to re-jump.
- **Load-older loop (REQ-34): reactive `effect()` over
  `ChatService.entryOf(id)`, not a manual polling/awaiting loop.**
  Rejecting the awaited-repeated-call sketch in favor of: an
  `effect()` in `ConversationDetailComponent`, scoped to run only while
  `jumpToMessageId()` is set and the target id is not found in
  `chatService.entryOf(conversationId()).messages`, that (a) checks
  membership first — no-op immediately if already loaded (REQ-33's
  "already loaded" fast path, zero extra network calls, matching the
  acceptance criteria's explicit "without an additional network
  request" line); (b) otherwise, if `entry.hasMore && !entry.loading`,
  calls `chatService.loadOlderMessages(id)` once and returns — the
  effect **re-runs automatically** the next time `entryOf(id)` changes
  (messages array or `loading` flag), because `entryOf` reads the
  same `_messageCache` signal `loadOlderMessages`'s `patchEntry` calls
  write to; no explicit "await completion" plumbing is needed, Angular's
  own signal-effect re-scheduling *is* the polling mechanism, already
  idiomatic in this codebase's signal-first convention (no new pattern
  invented). This is cleaner than the awaited-loop sketch in the task
  because it needs no manual iteration counter living outside signal
  graph — the counter becomes a plain local variable captured by the
  effect closure across its re-invocations (see bound below), and
  because it composes for free with `ChatService`'s existing
  `patchEntry`/`entryOf` shape rather than adding a second, parallel
  "wait for signal to settle" mechanism.
  - **Bound: capped at 20 `loadOlderMessages()` invocations per jump
    (an in-closure counter, reset when `jumpToMessageId` changes),
    matching the task's own proposed figure** — the SPEC only requires
    "a PLAN-level finite cap," not a specific number; 20 pages is
    generous relative to `MessageCacheEntry`'s existing default page
    size (confirmed same as `loadOlderMessages`'s already-shipped
    pagination, no change there) while still bounded — a message
    old enough to need more than 20 pages of lookback is treated the
    same as REQ-34's other terminal case (`hasMore === false`): stop,
    leave the thread scrolled to its current top, no error state.
    Once the cap or `hasMore === false` is hit, the effect calls
    `jumpToMessageId.set(undefined)` to stop re-triggering itself.
  - **Race with `pollNewMessages`'s existing 5s interval (real risk,
    flagged explicitly):** `pollNewMessages` and the jump-loop's
    `loadOlderMessages` both call `patchEntry` on the same cache
    entry, but touch disjoint ends of the message window (newest vs.
    older) and neither is gated on the other today (`loadOlderMessages`
    already runs concurrently with `pollNewMessages` for the existing
    "scroll up while new messages arrive" case, pre-dating this
    amendment) — no new race is introduced by reusing the same method,
    only the same class of interleaving that already exists in
    production. The one genuinely new risk: if `pollNewMessages` fires
    while `entry.loading` is `true` from a jump-loop-issued
    `loadOlderMessages` call, `ChatService`'s own `loading` guard inside
    `pollNewMessages` (confirmed present, same short-circuit pattern as
    `loadOlderMessages`'s own reentrancy guard) already prevents a
    double in-flight request — this is existing, tested behavior, not
    something this amendment needs to add. No fix needed here, but
    called out per the task's explicit ask.
- **Scroll + flash + persistent highlight target: `MessageThread
  Component` gains two new optional inputs**, `highlightMessageId:
  number | undefined` (was `jumpTargetMessageId` in the task's sketch —
  renamed for symmetry with the new `highlightQuery` input, both
  describing "what to highlight," not "what to scroll to," since REQ-36
  says the highlight persists after the scroll/flash is long done) and
  `highlightQuery: string | undefined`. Internally: a `computed()`
  finds the target `DisplayMessage` by id; each `<li>` gets a
  **template reference conditionally bound** via a directive-free
  approach — an `effect()` that runs after the target message is
  present in `messages()` and calls `document.getElementById(
  'msg-' + id)?.scrollIntoView({ block: 'center', behavior: reduceMotion
  ? 'auto' : 'smooth' })`, using a stable `id="msg-{{ m.id }}"` attribute
  already addressable per-row (new, one line, no new child component).
  This is simpler than a `#messageEl` template-ref + `ViewChildren`
  query for a *list* the task's sketch implied, since only one row at a
  time is ever a scroll target and `document.getElementById` scoped to
  a `:host`-rendered list is already how this component's DOM is
  structured — no new query/directive machinery needed for a single-
  target lookup.
- **Flash animation (REQ-35): plain CSS `@keyframes` appended to
  `MessageThreadComponent`'s existing `styles: [...]` array** (that
  array today holds one rule, `:host { display: block; flex: 1 1 0%;
  min-height: 0; }` — extending the existing array, not introducing a
  second styling mechanism), a `.chat-flash` class applying
  `animation: chat-flash-pulse 0.6s ease-in-out 3;` (3 iterations ×
  0.6s ≈ 1.8s, inside REQ-35's "roughly 1.5–2s" band) with the
  keyframe animating `background-color` between the bubble's own
  resting color and the `signal-600`/`signal-700` accent already used
  for outgoing bubbles (same accent token, per the task's own
  instruction, reused rather than a new color introduced) —
  **`@media (prefers-reduced-motion: reduce) { .chat-flash { animation:
  none; } }`** in the same stylesheet block. The class is applied via
  `[class.chat-flash]="isFlashTarget(m.id)"` on the target `<li>`,
  where `isFlashTarget` is a signal set `true` by the same effect that
  triggers `scrollIntoView` and cleared via `setTimeout(…, 1800)` back
  to `false` — a finite, self-clearing flag, matching REQ-35's "finite,
  does not loop indefinitely." **No existing `prefers-reduced-motion`
  precedent exists elsewhere in this codebase** (confirmed via search)
  — this is the first use of the media query; noted so a future
  grep for "how do we already do this" doesn't come up empty and
  re-invent a second convention.
- **Persistent highlight (REQ-36) reuses `splitOnMatch` directly inside
  `MessageThreadComponent`'s own template**, exactly the same function
  REQ-32 introduced for the result row — `highlightMessageId()`/
  `highlightQuery()` feed a `computed()` per rendered message (only
  the matching id computes a non-null split; every other message's
  computed short-circuits to `null` via an id check before calling
  `splitOnMatch`, avoiding running the substring search against every
  message in the thread on every render). This is the reuse the task's
  own framing anticipated (REQ-36 "same treatment as REQ-32's
  result-row marking") — one function, two call sites, not a
  duplicated implementation.
- **REQ-37 (read-only/`LOOKING_IN` viewers): no special-casing needed —
  confirmed, not newly decided.** Scroll/flash/highlight are rendering
  concerns inside `MessageThreadComponent`, which today already renders
  identically regardless of `viewerRelation` (the composer, not the
  thread, is what's conditionally omitted for `LOOKING_IN` in
  `ConversationDetailComponent`) — this amendment adds no new
  `viewerRelation` branch, satisfying REQ-37 by construction rather
  than by an explicit check.

### Components changed

```
core/
  chat.model.ts                       // + splitOnMatch(content, query)

features/chat/
  chat-unified-search.component.ts    // CHANGED — 'message' case of
                                       //   onEntitySelect passes router
                                       //   state (jumpToMessageId,
                                       //   jumpToQuery)
  chat-search-result-row.component.ts // CHANGED — new required `query`
                                       //   input; message-kind rows
                                       //   render via splitOnMatch
  conversation-detail.component.ts    // CHANGED — reads router-state
                                       //   via getCurrentNavigation(),
                                       //   effect() drives the bounded
                                       //   loadOlderMessages loop,
                                       //   passes highlightMessageId/
                                       //   highlightQuery down

shared/chat/
  message-thread.component.ts         // CHANGED — new optional inputs
                                       //   highlightMessageId/
                                       //   highlightQuery; scrollIntoView
                                       //   + chat-flash effect; new
                                       //   @keyframes + prefers-reduced-
                                       //   motion block in styles[]
```

### Dependencies

None. `Router`'s `state`/`getCurrentNavigation()` and CSS
`prefers-reduced-motion` are both platform-native (Angular Router,
CSS media queries) — no `package.json` change.

### i18n keys

None new — no user-visible copy is introduced (`<mark>` styling and a
scroll/flash are non-textual affordances); existing row/thread labels
are unchanged.

### Testing strategy (Vitest)

- `chat.model.spec.ts` (extended): `splitOnMatch` — case-insensitive
  match, first-occurrence-only against a string with the query
  appearing twice (asserts only the first is split out), `null` on no
  literal match, empty-string query returns `null` (no false-positive
  "match everything").
- `chat-search-result-row.component.spec.ts` (extended): message-kind
  row wraps the matched substring in `<mark>` given a matching `query`
  input; renders plain text when `query` doesn't literally substring-
  match `content`; non-message kinds are unaffected (query input
  present but unused in their templates).
- `chat-unified-search.component.spec.ts` (extended): clicking a
  message result calls `router.navigate` with `state: { jumpToMessageId,
  jumpToQuery }` set to the clicked result's id/current query (spy-based,
  same assertion shape as the existing routing-table test).
- `conversation-detail.component.spec.ts` (extended):
  - Target message already in `entryOf(id).messages` — no
    `loadOlderMessages` call, `highlightMessageId`/`highlightQuery`
    passed to `MessageThreadComponent` immediately.
  - Target message not yet loaded — asserts `loadOlderMessages` is
    called, and called again after a simulated `entryOf` update that
    still doesn't contain the target (regression for the effect
    re-triggering itself); once found, no further calls.
  - Bound: simulate 20 `loadOlderMessages` calls that never surface the
    target and `hasMore` still `true` — asserts a 21st call is never
    made and `jumpToMessageId` resets to `undefined` (no infinite loop
    regression).
  - `hasMore === false` before the target is found — asserts the loop
    stops immediately at that point, no error state rendered.
- `message-thread.component.spec.ts` (extended):
  - Given `highlightMessageId`/`highlightQuery` matching a rendered
    message, asserts `scrollIntoView` is called (mocked) on that
    message's element and the `chat-flash` class is applied, then
    removed after the timer (fake timers).
  - `window.matchMedia('(prefers-reduced-motion: reduce)').matches ===
    true` (mocked) — asserts `scrollIntoView` is still called and the
    persistent highlight (`<mark>`) is still present, but `chat-flash`
    is never applied (or applied with `animation: none` verified via
    computed style, whichever this codebase's existing a11y test
    convention already uses elsewhere).
  - The persistent `<mark>` remains present in a re-render after the
    flash's `setTimeout` clears `isFlashTarget`, confirming REQ-36's
    "stays highlighted... until navigating away."

### Open items carried forward

- None new. This amendment closes the "Scroll-to-message-on-open"
  open item from the original PLAN and its own later "unified search
  bar" amendment — no further backend work is required for it, contrary
  to those documents' original assumption that a "fetch page centered
  on message X" endpoint was a hard prerequisite.

## Amended (2026-08-11, RAG conversation turn-content search)

> Authoritative PLAN for SPEC.md's own "Amended (2026-08-11, RAG
> conversation turn-content search)" section (REQ-38 through REQ-43).
> **Low-risk, additive amendment — no new endpoint, no new route, no
> new service.** Backend contract consumed verbatim from
> `knowly-api/specify/features/chat-message-search/PLAN.md`'s
> "Amended (2026-08-11, RAG conversation turn-content search)" section
> (shipped): `ChatRagConversationSearchResultDto` gains two optional
> fields, both nullable/absent on a title-only match:
>
> ```ts
> interface ChatRagConversationSearchResultDto {
>   id: number;
>   title: string;
>   matchedSnippet?: string | null; // NEW, ≤150 chars, plain text
>   matchedRole?: 'USER' | 'ASSISTANT' | null; // NEW
> }
> ```
>
> No change to `GET /api/chat/search`'s request shape, status codes, or
> any other field — this is a pure response-shape widening, additive
> and backward-compatible with every existing consumer of this DTO.

### Architectural decisions

- **No new component, no new service.** This reuses the existing
  `kind`-discriminated `chat-search-result-row.component.ts` (already
  rendering `kind === 'rag'` rows per the 2026-08-10 unified-bar
  amendment) and the existing `splitOnMatch` pure function (already
  introduced by the "highlight matched text" amendment for
  `kind === 'message'` rows) — REQ-39's snippet-highlight requirement is
  satisfied by calling the exact same function against
  `result().matchedSnippet` instead of `result().content`, not a second
  highlight implementation. `ChatEntitySearchService` needs no code
  change at all: it already fans the backend response straight into
  `_rag` without transforming individual fields, so the two new optional
  DTO fields flow through automatically once `ChatRagConversationSearchResultDto`
  is widened in `chat.model.ts`.
- **`chat-search-result-row.component.ts`'s `kind === 'rag'` branch
  gains a conditional sub-block**, structurally mirroring the existing
  `kind === 'message'` branch:
  - `snippet = computed(() => splitOnMatch(this.result().matchedSnippet
    ?? '', this.query()))` — reuses `splitOnMatch` unchanged; a `null`/
    absent `matchedSnippet` naturally short-circuits (empty string has
    no match against a non-empty query, and the template's own
    `*ngIf="result().matchedSnippet"` guard — see below — means this
    computed is never even rendered for a title-only result).
  - Template: `@if (result().matchedSnippet) { <p class="...">{{ before
    }}<mark>{{ match }}</mark>{{ after }}</p> <span class="...">{{
    roleLabel() }}</span> }` — the snippet `<p>` and the role `<span>`
    are siblings inside the same conditional block, not two independent
    `@if`s, so REQ-41's "snippet renders even without a role" case is
    handled by nesting the role span in its *own* inner `@if
    (result().matchedRole)`, not the outer one — this is the concrete
    template shape backing REQ-41's independent-degradation
    requirement.
  - `roleLabel = computed(() => result().matchedRole === 'USER' ?
    i18n('chat.search.ragMatchedByUser') : result().matchedRole ===
    'ASSISTANT' ? i18n('chat.search.ragMatchedByAssistant') : null)` —
    a plain computed string, not a child component, since it's one
    short translated label, consistent with how every other per-row
    label in this component is already produced.
- **Role indicator uses a Lucide icon *plus* text, never icon/color
  alone (REQ-42)** — `lucideUser` (a shape already conceptually
  distinct from any AI/assistant icon in this codebase's existing set)
  paired with the translated text label for `USER`, and `lucideBot` (or
  this codebase's existing "AI/assistant" icon if one is already in use
  elsewhere — confirm via a quick grep for an existing bot/AI icon
  usage before adding a new Lucide import, to avoid introducing a
  second visual vocabulary for "the AI" if one already exists) paired
  with the translated text label for `ASSISTANT`. **Exact icon choice,
  spacing, and color are explicitly deferred** per SPEC's own REQ-40
  framing — if a genuine visual-design ambiguity remains once a
  first pass is built, route it to `design-system-ui-ux` before this
  amendment's tasks are marked done, rather than the PLAN author
  guessing at final pixel values.
- **No routing/navigation change.** REQ-43 confirms a turn-content RAG
  result opens exactly like a title-match RAG result already does
  (`/chat/articles/:conversationId`, unchanged from the 2026-08-10
  amendment's routing table) — no new code path, no new test beyond
  confirming the existing one still passes with the widened DTO.

### Components changed

```
core/
  chat.model.ts                       // CHANGED — ChatRagConversationSearchResultDto
                                       //   gains matchedSnippet?/matchedRole?

features/chat/
  chat-search-result-row.component.ts // CHANGED — kind === 'rag' branch
                                       //   gains snippet+role sub-block,
                                       //   reusing splitOnMatch unchanged
```

No change to `chat-entity-search.service.ts`, `chat-unified-search
.component.ts`'s routing/reset/debounce logic, or any backend contract
beyond the additive DTO fields already shipped.

### i18n keys

- `chat.search.ragMatchedByUser` — REQ-40 (e.g. "Você perguntou" /
  "You asked").
- `chat.search.ragMatchedByAssistant` — REQ-40 (e.g. "A IA respondeu" /
  "The assistant answered").
- `chat.search.resultA11yLabelRag` (existing key, from the 2026-08-10
  amendment) is **extended, not replaced**, to optionally interpolate
  the role label when present, so the accessible name for a
  turn-content match includes the same distinction a sighted user gets
  from the visible indicator (REQ-42's "included in that row's
  accessible name" NFR).

### Testing strategy (Vitest)

- `chat.model.spec.ts`: no new tests needed for `splitOnMatch` itself
  (unchanged function, already covered) — confirm (by omission) no
  regression to its existing test suite.
- `chat-search-result-row.component.spec.ts` (extended):
  - A `kind === 'rag'` result with a non-empty `matchedSnippet` renders
    the snippet `<p>`; one with `matchedSnippet` absent/null renders
    exactly as the pre-amendment RAG row did (title only, no snippet
    `<p>`, no role indicator) — direct regression test for REQ-38's
    "renders exactly as before" half.
  - A snippet containing the current query (case-insensitive) renders
    it `<mark>`-wrapped; a snippet that doesn't literally substring-
    match renders unmarked (REQ-39) — table-driven alongside the
    existing message-kind highlight test, reusing the same fixture
    shape.
  - `matchedRole === 'USER'` renders the "Você perguntou"-shaped
    indicator with both icon and text; `matchedRole === 'ASSISTANT'`
    renders the distinct "A IA respondeu"-shaped one; asserted via
    text content, not just a CSS class, so a color-only implementation
    would fail this test (REQ-40/REQ-42 regression guard).
  - A result with `matchedSnippet` set but `matchedRole` null/absent
    (the defensive, off-contract case) renders the snippet but omits
    the role indicator entirely — no thrown error, no fallback icon
    (REQ-41).
  - The row's `aria-label` for a turn-content RAG match includes the
    role-label text, confirmed via the extended
    `chat.search.resultA11yLabelRag` interpolation (REQ-42's
    accessible-name requirement).
- `chat-unified-search.component.spec.ts`: no new test needed for
  navigation — the existing per-kind routing-table test (from the
  2026-08-10 amendment) already covers `kind === 'rag'` and is
  unaffected by the widened DTO; confirm (by omission) it still passes
  unchanged.
- Regression: `chat-entity-search.service.spec.ts` — confirm (by
  omission) no test needs updating, since the service performs no
  per-field transformation on the `rag` section's results.

### Open items carried forward

- None new. This amendment closes the RAG half of the general
  "highlighting/snippet-generation... Base de artigos" out-of-scope
  line the 2026-08-10 unified-bar PLAN never explicitly carried (that
  PLAN's own scope was message-content highlighting only) — no
  outstanding backend or cross-repo dependency remains for REQ-38
  through REQ-43.
