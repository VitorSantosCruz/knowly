# SPEC — chat-message-search (frontend)

> The what and the why. No technical implementation details.
>
> **Amended (2026-08-10) — replaced by a unified, Slack-style search bar.
> This is a genuine scope pivot on a shipped feature (product owner
> feedback, not a bug), not a reinterpretation — see "Amendment
> (2026-08-10)" below for the full context and the five Tier 3 answers
> it depends on. `chat-search-dialog.component.ts` and its dedicated
> Sender/Conversation/From/To filter form are retired entirely — REQ-1
> through REQ-14 below are marked superseded inline (kept for history,
> per this repo's own convention — see `chat-unified-ui/SPEC.md`'s
> amendment style) and replaced by REQ-15 through REQ-31 in the new
> "Unified search" section. This amendment is companion to
> `knowly-app/specify/features/chat-unified-ui/SPEC.md`'s own "Amended
> (5)" section, which owns the shell/layout side of the same change (a
> persistent top search bar replacing the per-column search fields) —
> this document owns the search *behavior* (query semantics, result
> types, grouping, recent places), that one owns *where it lives on
> screen*. Read both together; neither is a full picture alone.**

## Context and motivation

`chat-unified-ui` shipped one navigation surface for 1:1, group,
Support, and RAG conversations, with a name-only search over the
directory/full-directory columns (its REQ-8/REQ-9) — but explicitly
**deferred** searching *inside* message content: "This SPEC's search
(REQ-8) matches person/group display names only... a future SPEC should
own [message-content search] explicitly rather than it being silently
folded into this one's 'search' requirements." This SPEC was originally
that follow-up: a Slack-style "I remember roughly what I typed, but not
who I said it to or which group" recall search, backed by the new
`GET /api/chat/messages/search` endpoint specified in
`knowly-api/specify/features/chat-message-search/SPEC.md`.

**Original framing (superseded — kept for history):** this SPEC does
not touch `chat-unified-ui`'s existing name-only directory search
(REQ-8/REQ-9 there) at all — it adds a distinct, separate search entry
point and result surface for message *content*, consumed the same way
this app already consumes any other backend capability.

## Amendment (2026-08-10) — unified search bar

**Trigger:** direct product-owner feedback after using the shipped
feature, comparing it against two screenshots of Slack's search — a
single top-of-screen bar that, as the user types, shows grouped results
(channels, people with avatars, "Recent places") in one dropdown,
versus knowly's shipped separate modal with distinct filter fields that
only matched message content ("No results for 'conforme'" — a literal,
content-only query with no entity matching at all). Their words: *"A
pesquisa precisa ser como a do slack, uma barra única que encontra
canais, pessoas e trechos de conversas."*

This is a Tier 3 change (reverses this SPEC's own shipped scope, and —
per the product owner's explicit confirmation below — also reopens
`chat-unified-ui`'s already-approved column-search requirements) and
was resolved by asking five direct questions before drafting any
requirement text, per `DECISIONS.md`'s decision-making authority
section. All five are now answered by the product owner:

1. **Result types — all four kinds.** People, groups, Support, and RAG
   ("Base de artigos") conversations are all searchable/browsable from
   the one bar — not just message content, and not just people/groups.
   "Canais" in the product owner's own phrasing maps to knowly's groups
   (`PEER_GROUP`) and RAG conversations — this app has no separate
   channel concept; nothing new is being modeled, this is a UI-level
   unification of already-existing entity kinds.
2. **Replace vs. layer — replace entirely.** The Sender/Conversation/
   From/To filter fields are removed completely; there is no separate
   "advanced search" surface. The unified bar is the *only* search
   surface for both message content and entity (person/group/Support/
   RAG) lookup.
3. **Entry point — persistent top bar**, not a sidebar icon opening a
   modal. Always visible across the chat screen, matching Slack's own
   placement. **This is a layout change to `chat-unified-ui`'s
   already-approved 3-column shell, not something this SPEC alone can
   fully own** — see that document's "Amended (5)" section for the
   shell-side requirements; this document assumes the bar exists there
   and specifies what happens inside it.
4. **Quick access — in scope.** Opening the bar with an empty query
   shows a short "recent places" list (recently/frequently interacted
   conversations), matching the Slack reference screenshot exactly.
5. **Result presentation — grouped by type**, not a flat ranked list.
   *(Tier 2 call, not explicitly re-asked: every reference the product
   owner gave — the original screenshot description and the "recent
   places" confirmation above — depicts Slack's grouped layout
   specifically; per `DECISIONS.md`'s Tier 2 process, this is decided
   here with the reasoning recorded, not silently assumed. If the
   product owner intended a flat list instead, flag it at sign-off and
   this call reverses cheaply — no code exists yet.)*

**Backend dependency, not yet specified:** REQ-15 through REQ-22 below
need the unified bar to search **entities** (people/groups by display
name, Support's own single row, RAG conversations by title) inside the
*same* request/response shape the content search already added
(`GET /api/chat/messages/search`, per
`knowly-api/specify/features/chat-message-search/SPEC.md`), or via a
new, separate backend contract combining all four result kinds. Neither
exists today — the current backend endpoint only ever returns message
rows. **This frontend SPEC does not invent that contract** — see "Out
of scope" below; a backend SPEC amendment (same feature or a new one)
is required before PLAN can build the entity-search half of this
document.

## Relationship to `chat-unified-ui`'s SPEC

- **(Superseded 2026-08-10 by the amendment above)** ~~This SPEC does
  not touch `chat-unified-ui`'s existing name-only directory search
  (REQ-8/REQ-9 there) at all — it adds a distinct, separate search
  entry point and result surface for message content, consumed the
  same way this app already consumes any other backend capability: a
  new UI surface calling a new endpoint, reflecting whatever the
  backend reports, never re-deriving access control client-side.~~ The
  unified bar now **does** touch and supersede `chat-unified-ui`'s
  REQ-8/REQ-9 — see that document's "Amended (5)" section.
- `chat-unified-ui`'s three-column layout is not otherwise restructured
  by this amendment beyond gaining the persistent top bar (its own
  "Amended (5)" section) — the conversation column (REQ-2a) still opens
  whichever conversation kind is selected, unchanged.
- Support conversations are excluded from the *content-search* half of
  results (REQ-16, unchanged from the original REQ-9's backend scope
  decision — peer/group chat content only), but Support's own row (the
  entity itself, not its content) **is** now findable/openable via the
  unified bar's entity results (REQ-15), consistent with question 1's
  answer above. These are two different things: "search inside
  Support's messages" (still out of scope) vs. "find/open the Support
  row from the search bar" (now in scope).
- Consistent with `chat-unified-ui`'s established pattern for every
  admin/capability-gated action, this SPEC never re-derives "can this
  caller see this conversation/person/group" client-side — it only
  reflects what the backend's response actually contains.

## User stories

- As a user, I want one search bar, always visible, where I can find a
  person, a group, my Support conversation, a "Base de artigos"
  conversation, or a remembered snippet of what someone said — without
  needing to know in advance which of those I'm looking for.
- As a user who remembers roughly what I typed but not who I said it to
  or which conversation it was in, I want that same bar to also search
  by message content and show matching messages with enough context to
  recognize them.
- As a user, I want results grouped by kind (e.g. Groups, People,
  Messages), each with a way to see more if there are more than fit in
  the dropdown — mirroring Slack's own grouped layout.
- As a user opening the search bar with nothing typed yet, I want to see
  a short list of conversations I've recently or frequently interacted
  with, so I don't have to type at all for the common case of "go back
  to where I just was."
- As a user, I want clicking any result — person, group, Support, RAG
  conversation, or a message — to open that conversation directly in the
  conversation column, the same way clicking a directory row already
  does today.
- As a user, I want clear, distinct feedback for "still searching,"
  "nothing matched," and "the search failed."

## Requirements (EARS/GEARS)

### Original entry point and query/filter requirements (Superseded 2026-08-10)

> Kept for history, per this repo's convention of marking superseded
> requirements in place rather than deleting them (see
> `chat-unified-ui/SPEC.md`'s amendment style). **None of REQ-1 through
> REQ-14 below are authoritative anymore** — see "Unified search" below
> for their replacements. `chat-search-dialog.component.ts` (the
> component these requirements described) is retired, not extended.

- ~~**REQ-1 [Ubiquitous]** The system shall provide a message-content
  search entry point, visually and functionally distinct from
  `chat-unified-ui`'s existing directory-name-only search fields...~~
  **Superseded by REQ-15/REQ-23 (Amended 2026-08-10)** — there is now
  exactly one search entry point for both content and entity search,
  the opposite of "distinct."
- ~~**REQ-2 [Ubiquitous]** The entry point shall make clear... that it
  searches message content, not person/group names...~~ **Superseded**
  — the one bar now searches both, by design (question 1).
- ~~**REQ-3 [Event-Driven]** When the user submits a non-blank free-text
  query, the system shall call the backend search endpoint with that
  query and display the returned messages.~~ **Superseded by REQ-17
  (Amended 2026-08-10)** — results now include entities, not only
  messages, and firing is per-keystroke/debounced (REQ-17), not
  submit-triggered.
- ~~**REQ-4 [Optional Feature]** Where the user has selected a sender
  filter...~~ **Superseded — removed entirely (question 2).** No sender
  filter field exists in the unified bar.
- ~~**REQ-5 [Optional Feature]** Where the user has selected a specific
  conversation filter...~~ **Superseded — removed entirely (question
  2).**
- ~~**REQ-6 [Optional Feature]** Where the user has selected a
  date-range filter...~~ **Superseded — removed entirely (question
  2).**
- ~~**REQ-7 [Unwanted Behavior]** If the user attempts to submit a
  search with a blank query, then the system shall not call the backend
  and shall indicate that a search term is required.~~ **Superseded by
  REQ-19/REQ-20 (Amended 2026-08-10)** — a blank query is no longer an
  error state; it shows "recent places" instead (question 4).
- ~~**REQ-8 [Unwanted Behavior]** If the user selects a "from" date
  later than the selected "to" date...~~ **Superseded — moot, no
  date-range field exists anymore (question 2).**
- ~~**REQ-9 [Ubiquitous]** The system shall display search results in
  the chronological order the backend returns them...~~ **Superseded by
  REQ-21 (Amended 2026-08-10)** — results are grouped by type (question
  5), not a single chronological list.
- ~~**REQ-10 [Event-Driven]** When the user scrolls to the end of the
  currently loaded results..., the system shall fetch the next page...~~
  **Superseded by REQ-22 (Amended 2026-08-10)** — per-group "see more,"
  not one global scroll-to-load-more list.
- ~~**REQ-11 [Event-Driven]** When the user clicks a search result, the
  system shall open that message's conversation...~~ **Carried forward
  unchanged in substance as REQ-24 (Amended 2026-08-10)**, extended to
  every result kind, not just messages.
- ~~**REQ-12 [Unwanted Behavior]** If a search request returns zero
  results, then the system shall show a distinct "no results for
  '<query>'" state...~~ **Carried forward unchanged in substance as
  REQ-27 (Amended 2026-08-10).**
- ~~**REQ-13 [Unwanted Behavior]** If a search request fails..., then
  the system shall show an inline error...~~ **Carried forward
  unchanged in substance as REQ-28 (Amended 2026-08-10).**
- ~~**REQ-14 [State-Driven]** While a search request is in flight, the
  system shall show a loading indication...~~ **Carried forward
  unchanged in substance as REQ-29 (Amended 2026-08-10).**

### Unified search (Amended 2026-08-10)

> **New section — this is now the authoritative requirement set for
> this feature.** Depends on `chat-unified-ui/SPEC.md`'s "Amended (5)"
> section for the persistent top bar's placement/layout (REQ-42
> onward there); depends on a not-yet-specified backend contract for
> entity search (see "Out of scope" below) for REQ-15/REQ-16/REQ-23.

#### Entry point

- **REQ-15 [Ubiquitous]** The system shall provide exactly one search
  entry point — the persistent top search bar specified by
  `chat-unified-ui/SPEC.md`'s "Amended (5)" section — for both message
  content and entity (person/group/Support/RAG conversation) search.
  No other search field exists anywhere in the chat screen once this
  amendment lands (this also supersedes `chat-unified-ui`'s own
  column-level REQ-8/REQ-9 — see that document).
- **REQ-16 [Ubiquitous]** The entry point's placeholder/label shall
  reflect that it searches everything (e.g. "Buscar pessoas, grupos ou
  mensagens"), not message content alone — since it is, by design
  (question 1/2), no longer a content-only surface.

#### Query and results

- **REQ-17 [Event-Driven]** When the user types a non-blank query into
  the bar, the system shall (debounced, exact interval a PLAN-level
  decision) call the backend with that query and populate results
  across every applicable group (REQ-21) as they arrive — no explicit
  submit action is required, matching Slack's type-ahead behavior and
  question 2's "replace entirely" direction (the old submit-triggered,
  filter-form flow no longer exists).
- **REQ-18 [Unwanted Behavior]** If entity search's backend dependency
  (see "Out of scope") is not yet available at implementation time,
  then the system shall degrade to content-only results (the prior
  behavior) rather than showing a broken/empty entity group — this is a
  PLAN-level sequencing note, not a permanent behavior; flag it as
  temporary in the PLAN if used.
- **REQ-19 [State-Driven]** While the query is blank and the bar is
  open/focused, the system shall show a "recent places" group — a short
  list of conversations (any kind: 1:1, group, Support, RAG) the viewer
  has recently or frequently interacted with — instead of an empty
  state or a "type to search" placeholder alone.
- **REQ-20 [Ubiquitous]** "Recent places" (REQ-19) shall be capped to a
  small, fixed number of entries (exact count a PLAN-level decision,
  matching Slack's own short list) and shall be replaced by live search
  groups (REQ-21) the moment the query becomes non-blank — it is not
  merged into a mixed empty+results view.
- **REQ-21 [Complex]** When search results are available for a non-blank
  query, the system shall render them grouped by kind — at minimum
  "Groups" (matching group names), "People" (matching person display
  names), and "Messages" (matching message content) — with Support and
  RAG conversation matches folding into "Groups"/a dedicated group at
  PLAN's discretion, so long as every one of the four entity kinds
  named in question 1 is represented somewhere in the grouped output;
  a group with zero matches for the current query is omitted entirely,
  not shown empty.
- **REQ-22 [Optional Feature]** Where a group (REQ-21) has more matches
  than fit in the dropdown's initial per-group cap, the system shall
  offer a "see more"/"ver mais" action for that group specifically,
  expanding only that group's results (mirroring Slack's "See 13
  more") — never a single global "load more" across every group at
  once (that shape is retired along with REQ-10/its scroll-pagination
  behavior).

#### Opening a result

- **REQ-23 [Event-Driven]** When the user clicks a person, group,
  Support, or RAG-conversation result, the system shall open that
  conversation in the conversation column, using the existing
  person/group/Support/RAG conversation view for that kind unchanged —
  identical behavior to clicking the equivalent row in
  `chat-unified-ui`'s column 1/column 3 today, just reachable from the
  search bar as well.
- **REQ-24 [Event-Driven]** When the user clicks a message result, the
  system shall open that message's conversation in the conversation
  column, using the existing conversation view for its kind unchanged
  — carried forward from the original REQ-11 unchanged in substance.
  **(Superseded 2026-08-10 by REQ-32 through REQ-36 below — the
  "remains v1-out-of-scope" note no longer applies.)**
- **REQ-25 [Event-Driven]** When the user clicks a "recent places"
  entry (REQ-19), the system shall open that conversation the same way
  REQ-23 does for its kind.
- **REQ-26 [Ubiquitous]** Clicking any result (REQ-23/REQ-24/REQ-25)
  shall close the search dropdown and return focus to the conversation
  column, mirroring Slack's own behavior of dismissing the results
  panel once a destination is chosen.

#### Feedback states

- **REQ-27 [Unwanted Behavior]** If a non-blank query's search returns
  zero results across every group, then the system shall show a
  distinct "no results for '<query>'" state, not the generic empty/
  "recent places" state — carried forward unchanged in substance from
  the original REQ-12.
- **REQ-28 [Unwanted Behavior]** If a search request fails (network or
  backend error), then the system shall show an inline error distinct
  from the "no results" state and shall not clear any previously
  displayed results — carried forward unchanged in substance from the
  original REQ-13.
- **REQ-29 [State-Driven]** While a search request is in flight, the
  system shall show a loading indication distinct from both the
  "no results" and error states — carried forward unchanged in
  substance from the original REQ-14.
- **REQ-30 [Unwanted Behavior]** If the entity-search half of a query
  fails while the message-content half succeeds (or vice versa — two
  independent backend calls, per the "Out of scope" note on the
  not-yet-defined combined contract), then the system shall show the
  groups that did succeed and an inline, group-scoped error only for
  the group(s) that failed, never blanking the entire dropdown for a
  partial failure. **PLAN-level note:** this requirement only applies
  if PLAN ends up implementing entity and content search as two
  separate backend calls rather than one combined contract; if a single
  combined endpoint is specified instead, this requirement collapses
  into REQ-28.
- **REQ-31 [Ubiquitous]** Dismissing the search bar (e.g. clicking
  away, pressing Escape) shall clear the dropdown without submitting a
  new search on reopen — reopening starts from "recent places" (REQ-19)
  again, not the last query's stale results.

## Amendment (2026-08-10) — highlight matched text + jump-to-message

**Trigger:** direct product-owner feedback after using the shipped
unified search — clicking a message result opens the right
conversation but gives no visual confirmation of *what* matched or
*where* in the thread it is, undercutting the "I remember roughly what
I typed" recall use case this SPEC's own motivation section describes.
This closes REQ-24's "remains v1-out-of-scope" note above. Resolved
directly by the product owner's own message (no further Tier 3
questions needed — see `PROJECT_STATUS.md`'s dated entry for this
amendment for the full instruction); implementation-detail choices
(exact timing/color) are Tier 2, decided here with reasoning recorded,
same convention as REQ-1(5)'s "grouped by type" call above.

- **REQ-32 [Ubiquitous]** Wherever a message search result's content
  (REQ-21/`ChatSearchResultRowComponent`'s message-kind row) contains
  the current query as a case-insensitive substring, the system shall
  visually mark the matched substring within that row's content
  (e.g. `<mark>`), leaving the rest of the row's text unstyled. A query
  that does not literally substring-match the row's own `content`
  (e.g. the backend matched on a different tokenization) shows the
  content unmarked, same as today — this does not change what counts
  as a result, only how an already-returned result's text is rendered.
- **REQ-33 [Event-Driven]** When the user clicks a message result
  (REQ-24), in addition to opening the conversation, the system shall
  scroll the conversation column's message thread to the specific
  matched message once it is loaded into the thread.
- **REQ-34 [Complex]** While the matched message is not yet among the
  conversation's currently-loaded messages, the system shall load
  older pages (the same "Load older messages" mechanism REQ-2 already
  exposes) automatically and repeatedly until the matched message is
  found or the conversation reports no further older pages, showing a
  loading state for the duration; if the message is never found (e.g.
  it was since deleted, or belongs to a page beyond a bounded lookback
  the PLAN defines), the system shall stop after that bound and leave
  the thread scrolled to its current top, with no error state (an
  unwanted-but-non-fatal outcome, not a failure of the search feature
  itself).
- **REQ-35 [Event-Driven]** When the matched message becomes visible
  in the thread (REQ-33/REQ-34), the system shall briefly flash that
  message's bubble (a short, finite background-color pulse using the
  app's existing accent color, 2–3 iterations totaling roughly 1.5–2s)
  to draw the eye to it, and shall respect `prefers-reduced-motion` by
  skipping the pulse animation entirely (the bubble is still scrolled
  into view and still gets the persistent highlight from REQ-36) for a
  viewer who has that preference set.
- **REQ-36 [Ubiquitous]** After the flash (REQ-35) ends, the matched
  substring shall remain visually marked within that message bubble
  in the thread (same treatment as REQ-32's result-row marking) for as
  long as that conversation/thread stays open — persistent, not part
  of the transient flash — so the viewer can still see exactly what
  was found after the animation ends.
- **REQ-37 [Unwanted Behavior]** If the user opens a message result for
  a conversation kind whose viewer relation is `LOOKING_IN` (no
  composer, oversight-only — REQ-4 in `chat-unified-ui`) or otherwise
  read-only, the scroll/flash/highlight behavior (REQ-33 through
  REQ-36) shall still apply unchanged — this is a read-affordance, not
  a participant-only one.

### Acceptance criteria (amendment)

- [x] A message result row with the query appearing literally inside
      its `content` shows that substring `<mark>`-wrapped (or
      equivalent styled span); a row where the query does not literally
      substring-match shows plain text.
- [x] Clicking a message result whose message is already loaded in the
      open conversation scrolls to it and flashes it without an
      additional network request beyond opening the conversation.
- [x] Clicking a message result whose message is *not* yet loaded
      triggers one or more automatic "load older" calls, then scrolls/
      flashes once found.
- [x] The flash animation is finite (does not loop indefinitely) and is
      skipped under `prefers-reduced-motion`, while the scroll and the
      persistent highlight still happen.
- [x] The matched substring stays highlighted in the bubble after the
      flash ends, until the user navigates away from that conversation.

### Out of scope (amendment)

- Deep-linking a message via a shareable URL (e.g. `?message=123`) —
  this amendment's jump-to-message is triggered only from an
  in-session search-result click, not from a URL a user could
  bookmark/share. A future SPEC can add that separately.
- Fuzzy/stemmed/tokenized highlight matching — REQ-32 is a literal,
  case-insensitive substring match against the exact query string, not
  the backend's own (potentially fuzzier) match logic.
- Any bound tuning beyond "a PLAN-level finite cap" for REQ-34's
  repeated-load-older loop — the exact page count is a PLAN decision.

## Non-functional requirements

- Accessibility: the search bar, its result groups, "see more" actions,
  and "recent places" list are keyboard-navigable and screen-reader-
  labeled (each group announced, each result an accessible name),
  consistent with this app's existing accessibility conventions —
  carried forward from the original document, extended to the new
  grouped/type-ahead shape.
- Performance: results remain paginated per-group (REQ-22), never an
  unbounded list; the query debounces to avoid firing a backend request
  on every keystroke (exact debounce mechanism a PLAN-level decision,
  unchanged framing from the original document).
- Responsiveness: the search bar's placement and collapse behavior on
  narrow viewports is owned by `chat-unified-ui/SPEC.md`'s "Amended
  (5)" section, not redefined here.
- Security: the frontend never filters or re-derives which
  conversations/messages/people/groups a search result is allowed to
  include — it displays exactly what the backend's response(s) contain,
  unchanged posture from the original document.
- Localization: unchanged from the original document — locale continues
  to be driven entirely by the app's existing in-app language selection,
  consumed automatically by the existing `localeInterceptor`; no new
  locale UI is introduced by this amendment either.

## Acceptance criteria

> Original acceptance criteria (message-content-only, filter-form
> shape) are retired along with `chat-search-dialog.component.ts` —
> not reproduced here as checkable items since the surface they
> describe no longer exists once this amendment ships; they remain
> readable in this document's git history for anyone auditing what
> changed. The list below is the new, authoritative set — verified by
> `chat-unified-search.component.spec.ts` and companion specs
> (2026-08-10), except where noted.

- [x] Exactly one search entry point exists (the persistent top bar) —
      no sidebar icon/modal, no separate filter fields.
- [x] Typing a non-blank query (no explicit submit) triggers search and
      populates grouped results as they arrive.
- [x] Opening the bar with a blank query shows a capped "recent places"
      list instead of an empty/placeholder state.
- [x] Results render grouped by kind (at least Groups, People,
      Messages), each group omitted entirely when it has zero matches
      for the current query.
- [x] A group with more matches than its initial cap offers a "see
      more" action that expands only that group.
- [x] Clicking a person/group/Support/RAG result opens that
      conversation in the conversation column, unchanged behavior for
      that conversation kind.
- [x] Clicking a message result opens its conversation the same way,
      without scroll-to-message (still v1-out-of-scope, carried
      forward from the original PLAN's decision).
- [x] Clicking a "recent places" entry opens that conversation.
- [x] Any result click closes the dropdown and returns focus to the
      conversation column.
- [x] A zero-result non-blank query shows a distinct "no results for
      '<query>'" state.
- [x] A failed search shows an inline error distinct from "no results,"
      without clearing previously displayed results — **known gap,
      REQ-30**: this is verified only at the PLAN's documented
      two-domain granularity (entities vs. messages); a true per-
      entity-kind partial failure is indistinguishable from zero
      matches under the finalized backend contract (see PLAN.md's
      "Partial failure" decision) — not a bug, an accepted narrowing
      pending a possible backend DTO amendment.
- [x] A loading state is shown while a search request is in flight.
- [x] Dismissing the bar and reopening it shows "recent places" again,
      not the previous query's stale results.

## Out of scope

- **The combined entity+content backend contract this amendment
  depends on (REQ-15/REQ-16/REQ-17/REQ-21/REQ-23) does not exist yet.**
  `GET /api/chat/messages/search` (the currently shipped backend
  endpoint) only ever returns message rows — it has no concept of
  matching a person/group/Support/RAG conversation by name. This
  frontend SPEC does not invent that backend contract; a backend SPEC
  amendment (to `knowly-api/specify/features/chat-message-search/SPEC.md`
  or a new backend feature) is required before PLAN can build
  REQ-15/REQ-17/REQ-21/REQ-23's entity-search half. REQ-18 documents
  the interim fallback if PLAN needs to sequence around this gap.
- Any change to `chat-unified-ui`'s own conversation-kind behaviors
  (1:1, group, Support, RAG) beyond how they're opened from search
  results — unchanged, per that document's existing "Out of scope."
- Relevance-ranked ordering *within* a group beyond whatever the
  backend returns — this amendment does not specify a client-side
  re-ranking algorithm.
- Searching Support-channel *content* — still excluded, per the
  backend SPEC's own scope decision (unchanged from the original
  document); only Support's own row/entity is now findable (REQ-15).
- Any `STAFF_ADMIN`/`MEMBER_ADMIN` "search across every group" UI or
  affordance — unchanged from the original document; no such backend
  capability exists.
- Highlighting/snippet-generation of the exact matched term.
- Exporting or saving search results/queries.
- Any manual language override for search — unchanged from the
  original document.
- Real-time/live-updating search results as new messages arrive while
  the dropdown is open.
- The persistent top bar's own layout/placement/responsive collapse
  behavior — owned entirely by `chat-unified-ui/SPEC.md`'s "Amended
  (5)" section, not this document.
- The exact count/algorithm behind "recent places" (REQ-19) beyond
  "recently or frequently interacted with" — whether it's pure
  recency, a frequency-weighted score, or reuses `chat-unified-ui`'s
  own column-3 cross-surface-recency signal (REQ-2d there, itself
  still PLAN-blocked on backend feasibility) is a PLAN-level decision,
  not specified here.

## Tier 3 — status

**All five questions this amendment depended on are resolved (product
owner, 2026-08-10) — see "Amendment (2026-08-10)" above for the exact
answers.** One open item remains before PLAN can start on the
entity-search requirements specifically (REQ-15/REQ-17/REQ-21/REQ-23):
the backend contract gap named in "Out of scope" above is not a Tier 3
product question, but it is a hard PLAN blocker — flag it explicitly
when this document is brought back for sign-off, since approving this
SPEC does not by itself make that backend work exist. **This document
is ready for the product owner's final read-back and sign-off**,
together with `chat-unified-ui/SPEC.md`'s "Amended (5)" section — they
should be approved as a pair, not independently, since neither is a
complete, buildable picture alone.
