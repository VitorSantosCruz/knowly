# SPEC — chat-message-search (frontend)

> The what and the why. No technical implementation details.

## Context and motivation

`chat-unified-ui` shipped one navigation surface for 1:1, group,
Support, and RAG conversations, with a name-only search over the
directory/full-directory columns (its REQ-8/REQ-9) — but explicitly
**deferred** searching *inside* message content: "This SPEC's search
(REQ-8) matches person/group display names only... a future SPEC should
own [message-content search] explicitly rather than it being silently
folded into this one's 'search' requirements." This SPEC is that
follow-up: a Slack-style "I remember roughly what I typed, but not who I
said it to or which group" recall search, backed by the new
`GET /api/chat/messages/search` endpoint specified in
`knowly-api/specify/features/chat-message-search/SPEC.md`.

**This SPEC does not touch `chat-unified-ui`'s existing name-only
directory search (REQ-8/REQ-9 there) at all** — it adds a distinct,
separate search entry point and result surface for message *content*,
consumed the same way this app already consumes any other backend
capability: a new UI surface calling a new endpoint, reflecting whatever
the backend reports, never re-deriving access control client-side.

## Relationship to `chat-unified-ui`'s SPEC

- `chat-unified-ui`'s "Out of scope / Future work" section explicitly
  named this deferral and the reason for it (message-content search
  "needs its own indexing strategy... a materially bigger feature," not
  a small extension of its name-matching filter) — this SPEC is that
  named future work, not a reinterpretation of anything already shipped.
- `chat-unified-ui`'s three-column layout (conversations list, thread,
  full directory) and its existing name-only search fields are
  unaffected by this SPEC — this feature adds a new, separate entry
  point (see REQ-1) rather than overloading either existing search
  field with two different semantics.
- Support conversations are excluded from this feature's results,
  matching the backend SPEC's own scope decision (peer/group chat
  only) — this SPEC does not add a Support-content search UI, and
  Support's row in `chat-unified-ui`'s column 1 is unaffected.
- Consistent with `chat-unified-ui`'s established pattern for every
  admin/capability-gated action (join-request approval, visibility
  change, etc.), this SPEC never re-derives "can this caller see this
  conversation" client-side — it only reflects what the backend's
  search response actually contains (a conversation the caller has lost
  access to simply never appears, per the backend SPEC's REQ-2/REQ-3;
  the frontend does not need, and must not implement, its own filtering
  of results by conversation membership).

## User stories

- As a user who remembers roughly what they typed but not who they said
  it to or which conversation it was in, I want a dedicated way to
  search my message history by content and see matching messages with
  enough context to recognize them.
- As a user narrowing a broad memory, I want to optionally filter that
  search by sender, by a specific conversation, and/or by a date range.
- As a user, I want to click a search result and land directly in that
  conversation, at or near that specific message.
- As a user, I want search results ordered chronologically, matching
  how the backend returns them, rather than a relevance score I can't
  predict.
- As a user, I want clear feedback when my search has no matches, is
  still loading, or fails, distinct from each other.

## Requirements (EARS/GEARS)

### Entry point

- **REQ-1 [Ubiquitous]** The system shall provide a message-content
  search entry point, visually and functionally distinct from
  `chat-unified-ui`'s existing directory-name-only search fields (its
  column 1/column 3 search inputs), reachable from within the unified
  chat screen (`chat-unified-ui`'s REQ-1) without leaving it.
- **REQ-2 [Ubiquitous]** The entry point shall make clear (via label or
  placeholder text) that it searches message content, not
  person/group names — so a user does not confuse it with
  `chat-unified-ui`'s existing directory search fields.

### Query and filters

- **REQ-3 [Event-Driven]** When the user submits a non-blank free-text
  query, the system shall call the backend search endpoint with that
  query and display the returned messages.
- **REQ-4 [Optional Feature]** Where the user has selected a sender
  filter, the system shall include it in the search request, restricting
  results to messages from that sender.
- **REQ-5 [Optional Feature]** Where the user has selected a specific
  conversation filter, the system shall include it in the search
  request, restricting results to that conversation.
- **REQ-6 [Optional Feature]** Where the user has selected a date-range
  filter (from and/or to), the system shall include it in the search
  request, restricting results to messages sent within that range.
- **REQ-7 [Unwanted Behavior]** If the user attempts to submit a search
  with a blank query, then the system shall not call the backend and
  shall indicate that a search term is required.
- **REQ-8 [Unwanted Behavior]** If the user selects a "from" date later
  than the selected "to" date, then the system shall indicate the
  invalid range and not submit the search.

### Results

- **REQ-9 [Ubiquitous]** The system shall display search results in
  the chronological order the backend returns them (not client-side
  re-sorted by relevance or any other criterion), showing, per result,
  at minimum: the message's sender, its conversation (person or group
  name), its timestamp, and enough of its content for the user to
  recognize the match.
- **REQ-10 [Event-Driven]** When the user scrolls to the end of the
  currently loaded results (or otherwise requests more), the system
  shall fetch the next page via the backend's cursor pagination and
  append it to the displayed results.
- **REQ-11 [Event-Driven]** When the user clicks a search result, the
  system shall open that message's conversation in the conversation
  column (`chat-unified-ui`'s REQ-2a), using the existing
  person/group/RAG conversation view for that conversation kind
  unchanged — this feature does not introduce a new conversation-detail
  view.
- **REQ-12 [Unwanted Behavior]** If a search request returns zero
  results, then the system shall show a distinct "no results for
  '<query>'" state, not the generic empty/loading state.
- **REQ-13 [Unwanted Behavior]** If a search request fails (network or
  backend error), then the system shall show an inline error distinct
  from the "no results" state and shall not clear any previously
  displayed results.
- **REQ-14 [State-Driven]** While a search request is in flight, the
  system shall show a loading indication distinct from both the
  "no results" and error states.

## Non-functional requirements

- Accessibility: search entry point, filters, and results list are
  keyboard-navigable and screen-reader-labeled, consistent with this
  app's existing accessibility conventions for other list/filter UIs
  (e.g. `chat-unified-ui`'s own directory columns).
- Performance: results are paginated (REQ-10), never fetched/rendered
  as an unbounded list; filter changes debounce/avoid firing a new
  backend request on every keystroke (exact debounce mechanism is a
  PLAN-level decision).
- Responsiveness: the search entry point and results surface adapt to
  the same breakpoint behavior `chat-unified-ui`'s three-column layout
  already established (REQ-2c) — exact placement within that responsive
  layout (e.g. a fourth collapsible pane vs. a modal/overlay over the
  existing columns) is a PLAN-level decision, not specified here.
- Security: the frontend never filters or re-derives which
  conversations/messages a search result is allowed to include — it
  displays exactly what the backend's response contains and nothing it
  computes independently, consistent with `chat-unified-ui`'s existing
  posture toward every backend-authorized capability.
- Localization: this feature does not add a language selector or expose
  the backend's locale-driven index selection (`chat-message-search`
  backend SPEC's REQ-13/REQ-14) to the user in any way — locale
  continues to be driven entirely by the app's existing in-app language
  selection (`TranslocoService`/`LanguageService`), consumed
  automatically by the existing `localeInterceptor` on every request,
  including this feature's search calls; no new locale UI or parameter
  is introduced.

## Acceptance criteria

- [x] A distinct message-content search entry point is reachable from
      the unified chat screen without leaving it, and is visibly
      different from the existing name-only directory search fields.
      (`ChatSidebarComponent`'s new "Buscar mensagens" icon button,
      opening `chat-search-dialog.component.ts`.)
- [x] Submitting a non-blank query displays matching messages in
      chronological order, each showing sender, conversation, timestamp,
      and enough content to recognize the match. (Order is whatever
      `ChatMessageSearchService.results()` returns, never re-sorted
      client-side.)
- [x] Sender, conversation, and date-range filters each narrow results
      when applied, individually and combined.
- [x] A blank-query submission attempt is blocked with a clear
      indication, no backend call made.
- [x] A "from" date later than "to" date is blocked with a clear
      indication, no backend call made.
- [x] Scrolling to the end of results (or an equivalent "load more"
      action) fetches and appends the next page. (Both an
      `IntersectionObserver` sentinel and an explicit "Load more" button
      call `loadMore()`.)
- [x] Clicking a result opens that message's conversation in the
      existing conversation view for its kind (1:1, group, or RAG),
      unchanged in behavior. **Scroll-to-message/highlighting the
      matched message within that view is explicitly v1-out-of-scope**
      (PLAN.md's "Open dependency on backend feasibility work") — the
      conversation opens at its normal newest-message view, not
      scrolled/highlighted to the matched message; a future increment
      needs a new backend "fetch page containing message X" endpoint
      that does not exist today.
- [x] A zero-result search shows a distinct "no results" state, not the
      generic empty state.
- [x] A failed search shows an inline error distinct from "no results,"
      without clearing previously displayed results.
- [x] A loading state is shown while a search request is in flight,
      distinct from both other states.
- [x] Switching the app's language (existing language switcher) changes
      which language index subsequent searches match against
      (server-side, via the existing `Accept-Language` header already
      sent by `localeInterceptor`) with no additional UI needed for
      this — this feature adds no locale UI/param of its own, consistent
      with the existing interceptor already covering every request.

## Out of scope

- Any change to `chat-unified-ui`'s existing directory-name-only search
  (its REQ-8/REQ-9) — this is a separate, additional search surface, not
  a replacement or modification of that one.
- Relevance-ranked result ordering — matches the backend's v1
  chronological-only ordering; a future increment, not this SPEC.
- Searching Support-channel content — excluded by the backend SPEC's own
  scope decision; no UI for it is specified here.
- Any `STAFF_ADMIN`/`MEMBER_ADMIN` "search across every group" UI or
  affordance — the backend grants no such capability (see backend
  SPEC's REQ-5), so no frontend surface for it exists either.
- Highlighting/snippet-generation of the exact matched term within a
  result's content beyond showing the message content itself — a
  possible future enhancement, not required here.
- Exporting or saving search results/queries.
- Any manual language override for search specifically (e.g. a
  "search in Portuguese" toggle independent of the app's own language
  setting) — locale is derived solely from the existing in-app language
  selection, per the backend SPEC's REQ-14.
- Real-time/live-updating search results as new messages arrive while a
  search is open.
- A dedicated full-page search route — this feature is consumed from
  within the unified chat screen; whether it's a panel, modal, or
  overlay is a PLAN-level layout decision, but it is not a new top-level
  navigation entry replacing or alongside "Conversas."

## Tier 3 — status

**None outstanding for this document.** The three Tier 3 product
decisions this feature depended on (Support-channel scope, oversight
bypass scope, locale handling) were resolved at the backend-SPEC level
(see `knowly-api/specify/features/chat-message-search/SPEC.md`'s "Tier
3 — resolved" section) and are consumed here without re-litigation —
this frontend SPEC has no independent Tier 3 questions of its own
beyond those already answered. Ready for `PLAN.md` once read back and
approved.
