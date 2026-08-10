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

## 10. Amended (2026-08-10) — unified Slack-style search bar

> Consumes PLAN.md's "Amended (2026-08-10)" section (approved,
> AppSec-passed). Tasks continue numbering from 62. Backend contract
> for `GET /api/chat/search` consumed verbatim from
> `knowly-api/specify/features/chat-message-search/PLAN.md`'s own
> "Amended (2026-08-10)" section (closed). Companion to whatever
> `chat-unified-ui/TASKS.md`'s own amendment does with
> `ChatShellComponent`'s header slot — that side owns where
> `<app-chat-unified-search>` is mounted, this side owns everything
> inside it.

### 10.1 Retiring `chat-search-dialog.component.ts`

- [ ] 62. Test: assert `chat-search-dialog.component.ts` is no longer
      imported/referenced anywhere in `features/chat/` (grep-based
      assertion in a small spec, or a build-level check) — establishes
      the Red state before deletion (Red).
- [ ] 63. Delete `chat-search-dialog.component.ts` and
      `chat-search-dialog.component.spec.ts`; remove the "Buscar
      mensagens" icon button and its click handler from
      `chat-sidebar.component.ts` (coordinate with
      `chat-unified-ui/TASKS.md`'s own `ChatSidebarComponent`
      restructuring task if it lands first — this removal should not
      conflict, only the icon button is in scope here) (Green — task 62
      now passes by omission).

### 10.2 Narrow `ChatMessageSearchService`

- [ ] 64. Test: `ChatMessageSearchService.search(q: string)` calls
      `GET /api/chat/messages/search` with only `q` as a query param —
      the `senderId`/`conversationId`/`dateFrom`/`dateTo` filter-param
      tests from task 4 are removed (no longer applicable, the params
      no longer exist), the `q`-only shape from task 2 is kept and
      updated to the new single-argument signature (Red).
- [ ] 65. Narrow `search()`'s signature to `search(q: string)`, delete
      the filter-param composition code from tasks 5/39; `_results`/
      `_status`/`_nextCursor`/`_lastQuery`/`loadMore()`/`reset()`/
      `hasMore()` are otherwise unchanged (Green).
- [ ] 66. Narrow `ChatMessageSearchFilters` in `core/chat.model.ts` to
      `{ q: string }`; remove the now-dead `senderId`/`conversationId`/
      `dateFrom`/`dateTo` fields. Run `npm run build` to confirm no
      stale caller (e.g. the deleted dialog) still references the wider
      shape.

### 10.3 `ChatEntitySearchService` (new)

- [ ] 67. Add `ChatPersonSearchResultDto`, `ChatGroupSearchResultDto`,
      `ChatSupportSearchResultDto`, `ChatRagConversationSearchResultDto`,
      `ChatEntitySearchSectionDto<T>`, `ChatEntitySearchResponseDto`,
      `ChatRecentPlaceDto`, `ChatEntitySearchResultDto`, and a
      `ChatEntitySearchSectionStatus` (`'idle' | 'loading' | 'ok' |
      'error'`) type to `core/chat.model.ts`, matching PLAN.md's
      "Consumed API contracts" verbatim. No test needed (pure type
      change); `npm run build` after.
- [ ] 68. Test: `ChatEntitySearchService.search(q)` calls
      `GET /api/chat/search?q=...` and, on a response containing all
      four sections populated, fans it out into `_people`/`_groups`/
      `_support`/`_rag` and marks all four section statuses `'ok'` in
      one write (Red).
- [ ] 69. Implement `search()`'s happy path (Green).
- [ ] 70. Test: a `support: null` response leaves `_support` `null` and
      `_supportStatus` `'ok'`, not `'error'` (Red).
- [ ] 71. Confirm task 70 passes with the task-69 implementation as-is,
      or adjust the null-handling branch if needed (Green).
- [ ] 72. Test: a network/5xx failure on `search(q)` marks all four
      entity section statuses `'error'` simultaneously — direct
      regression test for PLAN.md's "two failure domains, not five"
      decision (Red).
- [ ] 73. Implement that failure path (Green).
- [ ] 74. Test: `recentPlaces()` calls `GET /api/chat/search` with a
      blank/absent `q`, populates `_recentPlaces`/`_recentPlacesStatus`
      only, and leaves the four entity sections at whatever prior state
      they held (no cross-contamination) (Red).
- [ ] 75. Implement `recentPlaces()` (Green).
- [ ] 76. Test: `expandSection('groups', currentQuery)` sends
      `type=groups&offset=<current _groups.length>&q=<currentQuery>`
      and **appends** the new page to `_groups`, updating only
      `_groupsHasMore`; a second call right after asserts cumulative
      growth (regression against an accidental "replace" bug) (Red).
- [ ] 77. Implement `expandSection()` for `'groups'` (Green).
- [ ] 78. Test: `expandSection('people', ...)` and `expandSection('rag',
      ...)` behave identically (append-only, correct `type`/`offset`,
      only that section's signals touched) (Red).
- [ ] 79. Implement `expandSection()` for `'people'`/`'rag'` (reusing
      task 77's shared implementation, parameterized by `type`) (Green).
- [ ] 80. Test: `reset()` returns every section (`people`/`groups`/
      `support`/`rag`/`recentPlaces`) to `idle`/empty (Red).
- [ ] 81. Implement `reset()` (Green).

### 10.4 `chat-unified-search.component.ts` — debounce and dual-fetch

- [ ] 82. Test: `ChatUnifiedSearchComponent` renders closed/collapsed by
      default; typing a non-blank query debounces 400ms (fake timers,
      RxJS `Subject` + `debounceTime`/`distinctUntilChanged`, reusing the
      shipped mechanism) then fires **both**
      `ChatMessageSearchService.search(q)` and
      `ChatEntitySearchService.search(q)` exactly once per settled
      keystroke burst — not per-keystroke, not one service without the
      other (REQ-17) (Red).
- [ ] 83. Implement that shared-`Subject` dual-fetch wiring (Green).
- [ ] 84. Test: an unchanged query resubmitted (same trimmed string)
      triggers neither service a second time (`distinctUntilChanged`)
      (Red).
- [ ] 85. Confirm task 84 passes with the task-83 implementation as-is
      (Green — no additional code should be needed).
- [ ] 86. Test: opening the bar (or clearing the query back to blank)
      calls `ChatEntitySearchService.recentPlaces()` and does **not**
      call either `search()` method (REQ-19/20) (Red).
- [ ] 87. Implement that blank-query branch (Green).
- [ ] 88. Test: the moment the query becomes non-blank, the rendered
      "recent places" block is fully replaced by the five result groups
      (not merged into a mixed empty+results view) (REQ-20) (Red).
- [ ] 89. Implement that replace-not-merge rendering (Green).

### 10.5 `chat-unified-search.component.ts` — five-group rendering

- [ ] 90. Test: results render in the five-group order People, Groups,
      Base de artigos, Support, Messages (REQ-21); a group with zero
      matches for the current query is entirely absent from the DOM,
      not rendered empty (Red).
- [ ] 91. Implement that five-`<section>` template structure and
      empty-group omission (Green).
- [ ] 92. Test: each group has its own `role="group"` and a labelled
      heading sourced from `chat.search.groupLabelPeople`/
      `.groupLabelGroups`/`.groupLabelSupport`/`.groupLabelRag`/
      `.groupLabelMessages` (Red).
- [ ] 93. Implement those headings (Green).
- [ ] 94. Test: `chat-search-result-row.component.ts` gains a `kind:
      'person' | 'group' | 'support' | 'rag' | 'message'` discriminator
      input; one test per `kind` asserting the right icon/subtitle
      combination renders (was message-only) (Red).
- [ ] 95. Implement that discriminator and the four new per-kind render
      branches, keeping the existing message-kind rendering behavior
      from tasks 20-25 unchanged (Green).
- [ ] 96. Test: each row's `aria-label` composes the correct per-kind
      i18n key — `chat.search.resultA11yLabelPerson`/`.resultA11yLabelGroup`/
      `.resultA11yLabelSupport`/`.resultA11yLabelRag`/
      `.resultA11yLabelMessage` — table-driven, one row per kind (Red).
- [ ] 97. Implement those per-kind `aria-label`s, replacing the single
      `resultA11yLabel` from task 25 (Green).

### 10.6 Per-group "see more"

- [ ] 98. Test: a group's "see more" action (`chat.search.seeMore`,
      interpolating that group's name) calls only that group's own
      expand mechanism — `ChatEntitySearchService.expandSection('people'
      | 'groups' | 'rag', ...)` for those three, or
      `ChatMessageSearchService.loadMore()` for Messages — verified via
      spies that the other four groups' fetch methods are **not** called
      (REQ-22) (Red).
- [ ] 99. Implement that per-group "see more" wiring (Green).
- [ ] 100. Test: Support never renders a "see more" control (backend DTO
      caps it at one-or-none, no `hasMore` concept) (Red).
- [ ] 101. Confirm task 100 passes with the task-99 implementation as-is
      (Green — no additional code should be needed if Support's template
      branch simply omits the control).

### 10.7 Status derivation (two-domain partial failure)

- [ ] 102. Test: the component's derived top-level `status: 'idle' |
      'loading' | 'results' | 'no-results' | 'error'` is `'loading'`
      while any queried section (entities or messages) is still
      in-flight (Red).
- [ ] 103. Implement that `'loading'` branch of the `computed()` (Green).
- [ ] 104. Test: `status` is `'error'` only when **every** queried
      section failed (true global failure) — a mixed case where all
      four entity sections fail but Messages succeeds resolves to
      `'results'` (showing Messages plus an inline error badge on the
      failed entity groups), **not** `'error'` (REQ-28/30's two-domain
      partial-failure granularity, entities vs. messages, not five-way)
      (Red).
- [ ] 105. Implement that `'error'`-vs-`'results'` branch, including the
      inline per-group error badge for a failed entity domain (Green).
- [ ] 106. Test: `status` is `'no-results'` when every queried section
      succeeded with zero rows across all five groups (Red).
- [ ] 107. Implement that `'no-results'` branch (Green).
- [ ] 108. Test: the four `status` values (`'loading'`/`'error'`/
      `'no-results'`/`'results'`) each render their own distinct,
      mutually exclusive top-level block, mirroring the shipped dialog's
      table-driven status test (task 42) (Red).
- [ ] 109. Implement those template branches (Green).

### 10.8 Navigation and dismiss/reopen

- [ ] 110. Test: clicking a person/group/Support/RAG/message result
      navigates to its PLAN-documented path (`/chat/:conversationId`,
      `/chat/support/:channelId`, `/chat/articles/:conversationId`,
      `/chat/:conversationId` respectively) — table-driven, one row per
      kind — and calls `reset()` on **both**
      `ChatMessageSearchService` and `ChatEntitySearchService` before
      navigating (REQ-23/24/25/26) (Red).
- [ ] 111. Implement that per-kind navigation + dual-reset wiring
      (Green).
- [ ] 112. Test: clicking a "recent places" entry dispatches on its
      `kind` (`PEER_DIRECT`/`PEER_GROUP` → `/chat/:conversationId`,
      `SUPPORT` → `/chat/support/:conversationId`, `RAG` →
      `/chat/articles/:conversationId`) and also resets both services
      (Red).
- [ ] 113. Implement that recent-place navigation branch (Green).
- [ ] 114. Test: clicking a "person" result with no existing
      conversation reuses the existing create-and-open behavior (calls
      the same shared handler `ChatDirectoryComponent`'s own row click
      uses today), not a duplicated code path — extract to a shared
      `openPersonConversation(userId)` helper on `ChatService` first if
      one doesn't already exist (Red).
- [ ] 115. Implement/extract that shared helper and wire it in (Green).
- [ ] 116. Test: Escape or click-away calls `reset()` on both services;
      a subsequent reopen re-fetches `recentPlaces()`, not the last
      query's stale results (REQ-31) (Red).
- [ ] 117. Implement that dismiss/reopen wiring (Green).

### 10.9 i18n

- [ ] 118. Add the new/changed `chat.search.*` keys listed in PLAN.md's
      amended "i18n keys" section to `public/i18n/en.json`:
      `barPlaceholder`, `groupLabelPeople`, `groupLabelGroups`,
      `groupLabelSupport`, `groupLabelRag`, `groupLabelMessages`,
      `recentPlacesLabel`, `seeMore` (interpolated), `resultA11yLabelPerson`,
      `resultA11yLabelGroup`, `resultA11yLabelSupport`,
      `resultA11yLabelRag`, `resultA11yLabelMessage`. Keep `noResults`/
      `error`/`loading` unchanged.
- [ ] 119. Add the same key set, translated, to `public/i18n/pt-BR.json`.
- [ ] 120. Remove the now-dead keys from both `en.json` and `pt-BR.json`:
      `entryPointLabel`, `dialogTitle`, `queryPlaceholder`,
      `filterSenderLabel`, `filterConversationLabel`,
      `filterDateFromLabel`, `filterDateToLabel`, `blankQueryError`,
      `invalidDateRangeError`, `loadMore`, `resultA11yLabel` — all
      described the retired filter-form/dialog surface and have no
      remaining reference after section 10.1's deletion.

### 10.10 Final verification

- [ ] 121. Run
      `npm run format:check && npm test && npm run build && npm run lint`
      and confirm everything is green. Confirm (by omission) that
      `chat-search-dialog.component.spec.ts` no longer exists and no
      other spec references it; confirm
      `chat-directory.component.spec.ts`/
      `chat-full-directory.component.spec.ts` remain unmodified by this
      amendment, per PLAN.md's regression notes.
- [ ] 122. Update `../../../../PROJECT_STATUS.md` to reflect the
      unified search bar shipping (replacing the earlier filter-dialog
      entry for this feature), noting the REQ-30 two-domain
      partial-failure granularity gap flagged in PLAN.md as a known,
      accepted limitation pending a possible backend DTO amendment.
      Coordinate wording with the backend feature's own
      `PROJECT_STATUS.md` task and `chat-unified-ui`'s TASKS.md if they
      land around the same time — write a reasonable entry now, resolve
      any merge conflicts at execution time rather than blocking on
      those other tasks.
- [ ] 123. Update `SPEC.md`'s acceptance-criteria checkboxes for
      REQ-15 through REQ-31 to reflect what's now verified by tests,
      leaving the REQ-30 five-way-granularity gap and the
      REQ-11-carried-forward scroll-to-message gap both explicitly noted
      as out-of-scope/known-gap rather than checked.
