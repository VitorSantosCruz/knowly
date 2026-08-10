# TASKS — chat-message-search (frontend)

> Atomic, sequential, verifiable tasks derived from PLAN.md (approved,
> AppSec-passed). Each "Implement" task ends with `npm run format` and a
> small Conventional Commit before moving on. Consumes
> `GET /api/chat/messages/search` verbatim from
> `knowly-api/specify/features/chat-message-search/PLAN.md` (closed).

## 1. Shared prerequisites (models)

- [x] 1. Add `ChatMessageSearchResultDto`, `ChatMessageSearchPageDto`,
      `ChatMessageSearchFilters`, and the `ChatMessageSearchStatus`
      (`'idle' | 'loading' | 'results' | 'no-results' | 'error'`) type to
      `core/chat.model.ts`, matching PLAN.md's "Consumed API contracts"
      and "State and data" sections verbatim. No test needed (pure type
      change); run `npm run build` after this task to confirm no existing
      fixture breaks under `strict`.

## 2. `ChatMessageSearchService` (signals)

- [x] 2. Test: `ChatMessageSearchService.search({ q })` calls
      `GET /api/chat/messages/search` with only `q` as a query param
      (`senderId`/`conversationId`/`dateFrom`/`dateTo` entirely omitted,
      not sent as `null`/empty string, when unset) (Red).
- [x] 3. Implement `search()`'s param-building + HTTP call, setting
      `_status` to `'loading'` before the request fires (Green).
- [x] 4. Test: `search()` with every optional filter set includes all of
      `senderId`/`conversationId`/`dateFrom`/`dateTo` as query params,
      individually and in combination (a combined-filter case, not just
      each alone) (Red).
- [x] 5. Implement that full param composition (Green).
- [x] 6. Test: `search()` success with a non-empty `results[]` replaces
      `_results`, sets `_status` to `'results'`, and stores `_nextCursor`
      from the response's `nextCursor` (Red).
- [x] 7. Implement that success path (Green).
- [x] 8. Test: `search()` success with `results: []` sets `_status` to
      `'no-results'`, not `'results'`, and sets `_lastQuery` to the
      submitted `q` (for the "no results for '<query>'" message) (Red).
- [x] 9. Implement that branch (Green).
- [x] 10. Test: `search()` failure (network/5xx) sets `_status` to
      `'error'` **without clearing** a prior non-empty `_results`
      (REQ-13's core regression test) (Red).
- [x] 11. Implement that failure path (Green).
- [x] 12. Test: `loadMore()` sends the correct `cursor` param (from
      `_nextCursor()`) alongside the same filters as the last `search()`
      call, and **appends** the new page's results to `_results` rather
      than replacing them, updating `_nextCursor` from the response
      (Red).
- [x] 13. Implement `loadMore()`'s happy path (Green).
- [x] 14. Test: `loadMore()` no-ops (no HTTP call) when `_nextCursor()`
      is `null`, and no-ops when `_status()` is already `'loading'`
      (Red).
- [x] 15. Implement those two guards (Green).
- [x] 16. Test: `reset()` returns `_status` to `'idle'`, empties
      `_results`, and sets `_nextCursor`/`_lastQuery` back to their
      initial values (Red).
- [x] 17. Implement `reset()` (Green).
- [x] 18. Test: `hasMore()` is a `computed()` that is `true` only when
      `_nextCursor()` is non-null (Red).
- [x] 19. Implement `hasMore` (Green).

## 3. `chat-search-result-row.component.ts`

- [x] 20. Test: renders sender nickname, conversation title, formatted
      timestamp, and content snippet from an input `ChatMessageSearchResultDto`
      (Red).
- [x] 21. Implement the presentational component (Green).
- [x] 22. Test: emits its output (the row's `conversationId`) on click
      and on `Enter`/`Space` keydown, per NFR keyboard-navigability
      (Red).
- [x] 23. Implement that emit wiring, `tabindex`, and `role="button"`
      (Green).
- [x] 24. Test: renders the interpolated `chat.search.resultA11yLabel`
      `aria-label` composing sender + conversation + timestamp (Red).
- [x] 25. Implement that `aria-label` (Green).

## 4. `chat-search-dialog.component.ts` — query, validation, debounce

- [x] 26. Test: `ChatSearchDialogComponent` renders a `<dialog>` closed
      by default, opened via a public `open()` method (mirroring
      `create-group-dialog.component.ts`'s existing `<dialog>` precedent)
      (Red).
- [x] 27. Implement that open/close scaffold (Green).
- [x] 28. Test: submitting a blank/whitespace-only query shows
      `chat.search.blankQueryError` and makes zero HTTP calls (REQ-7)
      (Red).
- [x] 29. Implement that client-side guard (Green).
- [x] 30. Test: typing rapidly into the query field triggers exactly one
      `ChatMessageSearchService.search()` call, 400ms after the last
      keystroke, not one per keystroke (fake timers, RxJS `Subject` +
      `debounceTime`/`distinctUntilChanged`) (Red).
- [x] 31. Implement that debounced-`Subject` plumbing (Green).
- [x] 32. Test: an unchanged query resubmitted (same trimmed string)
      does not trigger a second `search()` call (`distinctUntilChanged`)
      (Red).
- [x] 33. Confirm task 32 passes with the task-31 implementation as-is
      (Green — no additional code should be needed).

## 5. `chat-search-dialog.component.ts` — filters

- [x] 34. Test: the sender `<select>`'s options come from
      `ChatDirectoryRowsService`'s already-fetched people rows
      (`eligibleParticipants` + conversation participants, deduplicated
      by user id), with no new HTTP call fired to populate it (Red).
- [x] 35. Implement that sender-options `computed()` (Green).
- [x] 36. Test: the conversation `<select>`'s options come from
      `ChatService.conversations()`, filtered to `PEER_DIRECT`/
      `PEER_GROUP` only (`SUPPORT` excluded client-side), with no new
      HTTP call fired to populate it (Red).
- [x] 37. Implement that conversation-options `computed()` (Green).
- [x] 38. Test: selecting a sender, a conversation, or a date individually
      includes that filter in the next `search()` call; selecting all
      four together (query + sender + conversation + date range) includes
      all of them combined in one call (REQ-4/5/6, individually and
      combined) (Red).
- [x] 39. Implement that filter-to-`search()` wiring (Green).
- [x] 40. Test: setting `dateFrom` after `dateTo` shows
      `chat.search.invalidDateRangeError` and makes zero HTTP calls
      (REQ-8) (Red).
- [x] 41. Implement that client-side guard (Green).

## 6. `chat-search-dialog.component.ts` — results, states, pagination

- [x] 42. Test: the four `status` values (`'idle' | 'loading' |
      'results' | 'no-results' | 'error'`) each render their own
      distinct, mutually exclusive block — a table-driven test asserting
      exactly one status block is present in the DOM per state, not just
      that the right text exists alongside a stale other-state block
      (REQ-12/13/14) (Red).
- [x] 43. Implement those four template branches (Green).
- [x] 44. Test: the `'no-results'` block interpolates the submitted query
      into `chat.search.noResults` (REQ-12) (Red).
- [x] 45. Implement that interpolation (Green).
- [x] 46. Test: REQ-9 — results render in the exact order
      `ChatMessageSearchService.results()` returns them (no client-side
      re-sort), each row showing sender/conversation/timestamp/content
      via `chat-search-result-row.component.ts` (Red).
- [x] 47. Implement that results list rendering (Green).
- [x] 48. Test: scrolling to the sentinel element (`IntersectionObserver`-
      based, mirroring `message-thread.component.ts`'s existing
      pagination-on-scroll trigger) calls `loadMore()`; clicking the
      `chat.search.loadMore` button also calls it (REQ-10, both triggers)
      (Red).
- [x] 49. Implement that sentinel + button wiring (Green).
- [x] 50. Test: clicking a result row calls `Router.navigate(['/chat',
      conversationId])` for that result's `conversationId` and closes the
      dialog (REQ-11), asserted via a `Router` spy matching
      `chat-directory.component.spec.ts`'s existing navigation-assertion
      pattern — explicitly asserts **no** `?highlight=`/scroll-to-message
      query param is added, confirming the PLAN's documented v1 scope
      decision (Red).
- [x] 51. Implement that navigation + dialog-close wiring (Green).
- [x] 52. Test: closing the dialog (via its close control) calls
      `ChatMessageSearchService.reset()` (confirms the "fresh search on
      reopen" decision) (Red).
- [x] 53. Implement that close-triggers-reset wiring (Green).

## 7. `chat-sidebar.component.ts` integration

- [x] 54. Test: `ChatSidebarComponent` renders a new search icon button
      (`@lucide/angular`, already a dependency) alongside its existing 3
      action buttons, keyboard-reachable, with the
      `chat.search.entryPointLabel` `aria-label`/tooltip (REQ-1/REQ-2)
      (Red).
- [x] 55. Implement that button (Green).
- [x] 56. Test: clicking it opens `chat-search-dialog.component.ts` (via
      its `open()` method) (Red).
- [x] 57. Implement that click wiring (Green).

## 8. i18n

- [x] 58. Add all `chat.search.*` keys listed in PLAN.md's "i18n keys"
      section to `public/i18n/en.json`: `entryPointLabel`, `dialogTitle`,
      `queryPlaceholder`, `filterSenderLabel`, `filterConversationLabel`,
      `filterDateFromLabel`, `filterDateToLabel`, `blankQueryError`,
      `invalidDateRangeError`, `loading`, `noResults` (interpolates
      `{{ query }}`), `error`, `loadMore`, `resultA11yLabel`
      (interpolated).
- [x] 59. Add the same key set, translated, to `public/i18n/pt-BR.json`.

## 9. Final verification

- [x] 60. Run
      `npm run format:check && npm test && npm run build && npm run lint`
      and confirm everything is green — `npm run lint` is mandatory per
      this subproject's `CLAUDE.md`. Confirm (by omission) that
      `chat-directory.component.spec.ts`/
      `chat-full-directory.component.spec.ts` remain unmodified by this
      feature, per PLAN.md's "Testing strategy" regression note.
- [x] 61. Update `SPEC.md`'s acceptance-criteria checkboxes to reflect
      what's now verified by tests, leaving REQ-11's scroll-to-message
      user-story aspect explicitly noted as v1-out-of-scope (per PLAN.md's
      "Open dependency on backend feasibility work" section) rather than
      checked.
