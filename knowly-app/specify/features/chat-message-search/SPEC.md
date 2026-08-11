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
>
> **Amended (2026-08-11, RAG conversation turn-content search) — this
> amendment is companion to
> `knowly-api/specify/features/chat-message-search/SPEC.md`'s own
> "Amended (2026-08-11, RAG conversation turn-content search)" section
> (backend, shipped — `matchedSnippet`/`matchedRole` are now returned on
> `ChatRagConversationSearchResultDto`).** This document's REQ-21 (RAG
> results render inside a "Base de artigos" group) and REQ-32/REQ-36's
> already-shipped substring-highlight pattern are extended, not
> reversed, by new REQ-38 through REQ-43 at the end of the "Unified
> search" section below — a "Base de artigos" result that matched by
> turn content now shows the matched snippet (with the query
> highlighted, reusing REQ-32's own `<mark>` mechanism) and a
> role indicator ("Você perguntou" / "A IA respondeu"), while a result
> that still only matched by title renders exactly as before. See that
> section for the full ruleset.
>
> **Amended (2026-08-11, message-result participancy routing fix) — this
> amendment is companion to
> `knowly-api/specify/features/chat-message-search/SPEC.md`'s own
> "Amended (2026-08-11, message-result participancy/visibility signal)"
> section (backend — `ChatMessageSearchResultDto` now carries
> `isParticipant`/`visibility`, the identical shape
> `ChatGroupSearchResultDto` already carries for entity-search group
> results).** Closes a real, confirmed UX/contract gap, not a new
> feature: `onMessageSelect` navigates unconditionally to
> `/chat/{conversationId}`, but a message result can legitimately point
> to a `PUBLIC`/`REQUEST_TO_JOIN` group conversation the caller has not
> yet joined (an intentional discoverability carve-out on the backend's
> message-content search, REQ-5l/REQ-5n/REQ-5o/REQ-5p there) — and
> `ChatConversationService#requireReadableConversation` correctly
> rejects that with an untreated 403. `onEntitySelect`'s `'group'` case
> already handles the identical situation correctly today; this
> amendment makes `onMessageSelect` do the same thing. See the new
> "Message-result participancy routing" subsection near the end of the
> "Unified search" section for the full ruleset.

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
- **(Amended 2026-08-11, RAG conversation turn-content search):** As a
  user who remembers roughly what I asked the AI assistant, or roughly
  what it answered, but not which "Base de artigos" conversation that
  was in, I want the search result itself to show me the matching
  snippet of that conversation and whether it was my own question or
  the assistant's reply that matched — the same kind of "confirm what
  matched" confidence REQ-32/REQ-36 already give me for a message
  result — instead of only the conversation's title, which I may never
  have set to anything memorable.
- **(Amended 2026-08-11, message-result participancy routing fix):** As
  a user who finds a message result inside a public/joinable group I
  haven't joined yet, I want clicking that result to offer me the same
  join/request-to-join step I'd get finding that same group by name —
  not a raw error I have no way to recover from.

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
  "remains v1-out-of-scope" note no longer applies.) (Amended 2026-08-11,
  message-result participancy routing fix): further extended, not
  reversed, by REQ-47 through REQ-51 below — REQ-24's "open that
  message's conversation" now applies only when the result's
  `isParticipant` is `true`; see that subsection for the
  not-yet-joined-group case.**
- **REQ-25 [Event-Driven]** When the user clicks a "recent places"
  entry (REQ-19), the system shall open that conversation the same way
  REQ-23 does for its kind.
- **REQ-26 [Ubiquitous]** Clicking any result (REQ-23/REQ-24/REQ-25)
  shall close the search dropdown and return focus to the conversation
  column, mirroring Slack's own behavior of dismissing the results
  panel once a destination is chosen. **(Amended 2026-08-11,
  message-result participancy routing fix): where REQ-49 routes a
  message result through the join/request-to-join flow instead of a
  direct navigation, the dropdown still closes exactly as this
  requirement specifies — only the destination action differs, not the
  dismissal behavior.**

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

### RAG conversation turn-content match rendering (Amended 2026-08-11, RAG conversation turn-content search)

> **New subsection.** Backend companion:
> `knowly-api/specify/features/chat-message-search/SPEC.md`'s REQ-27
> through REQ-33 (shipped) — `ChatRagConversationSearchResultDto` now
> carries two optional, additive fields, `matchedSnippet` (a
> plain-text, ≤150-char, HTML-free excerpt of whichever turn matched)
> and `matchedRole` (`"USER"` or `"ASSISTANT"`), populated only when the
> match came from turn *content* rather than the conversation's title.
> This subsection defines how the "Base de artigos" group (REQ-21)
> renders a result depending on whether those two fields are present.
> It extends REQ-21's existing rendering, and reuses REQ-32's already-
> shipped substring-highlight mechanism — it does not reverse or
> redefine either.

- **REQ-38 [Complex]** Where a "Base de artigos" group result (REQ-21)
  is returned with a non-null, non-empty `matchedSnippet`, the system
  shall render that snippet beneath the conversation's title within
  that result row; where a "Base de artigos" group result is returned
  with `matchedSnippet` absent/null/empty (a title-only match, today's
  existing behavior), the system shall render that row exactly as it
  does today — title only, no snippet, no role indicator. This is the
  same "reflect exactly what the backend response contains, never
  re-derive" posture this document already establishes elsewhere (see
  "Relationship to `chat-unified-ui`'s SPEC").
- **REQ-39 [Ubiquitous]** Wherever a rendered `matchedSnippet` (REQ-38)
  contains the current query as a case-insensitive substring, the
  system shall visually mark the matched substring within the snippet
  (e.g. `<mark>`), reusing REQ-32's existing highlight mechanism
  unchanged rather than introducing a second one — a snippet that does
  not literally substring-match the current query (e.g. the backend
  matched on a different tokenization) renders unmarked, same
  "no highlight ≠ not a real match" posture REQ-32 already establishes
  for message results.
- **REQ-40 [Complex]** Where a "Base de artigos" group result carries a
  non-null `matchedRole`, the system shall render a distinct,
  human-readable indicator alongside the snippet showing whether the
  caller's own question matched (`matchedRole === "USER"`, e.g. "Você
  perguntou") or the AI assistant's reply matched (`matchedRole ===
  "ASSISTANT"`, e.g. "A IA respondeu") — the exact label wording/icon
  is a PLAN-level (and, if genuinely ambiguous on the visual side, a
  `design-system-ui-ux`-level) decision; this requirement only pins the
  functional distinction that must be conveyed, not the pixel design.
- **REQ-41 [Unwanted Behavior]** If a "Base de artigos" group result
  carries a `matchedSnippet` but a null/missing `matchedRole` (should
  not happen per the backend contract, which always pairs the two, but
  not structurally guaranteed by the DTO's own optionality), then the
  system shall still render the snippet (REQ-38/REQ-39) and simply omit
  the role indicator (REQ-40), rather than hiding the snippet entirely
  or throwing — the two fields degrade independently, not as an
  all-or-nothing pair.
- **REQ-42 [Ubiquitous]** The role indicator (REQ-40) shall convey the
  question/answer distinction through text and/or an icon with a
  discernible shape difference, never through color alone — consistent
  with this app's existing accessibility conventions (see "Non-
  functional requirements" below) and with the fact that a
  screen-reader user gets no benefit from a color-only cue.
- **REQ-43 [Ubiquitous]** Clicking a "Base de artigos" result that
  matched by turn content (REQ-38 through REQ-40) opens that
  conversation exactly as REQ-23 already specifies for any RAG result
  — this amendment does not add scroll-to-turn/highlight-in-thread
  behavior analogous to REQ-33 through REQ-36's message-result jump; see
  "Out of scope" below.

### Message-result participancy routing (Amended 2026-08-11, message-result participancy routing fix)

> **New subsection.** Backend companion:
> `knowly-api/specify/features/chat-message-search/SPEC.md`'s new REQ-44
> through REQ-46 — `ChatMessageSearchResultDto` now carries
> `isParticipant` (boolean) and `visibility` (`ChatGroupVisibility`,
> nullable), the identical shape `ChatGroupSearchResultDto` already
> carries for entity-search group results (REQ-19 there). This
> subsection closes a real, confirmed bug: `onEntitySelect`'s `'group'`
> case (`chat-unified-search.component.ts`) already checks
> `result.isParticipant` and routes to `rowsService.onGroupClick` — the
> existing join/request-to-join flow — when `false`, but
> `onMessageSelect` navigates to `/chat/{conversationId}`
> unconditionally, with no such check. Since backend message-content
> search intentionally, correctly includes results from
> `PUBLIC`/`REQUEST_TO_JOIN` group conversations the caller has not yet
> joined (the discoverability carve-out documented in
> `knowly-api/specify/features/chat-message-search/SPEC.md`'s
> REQ-5l/REQ-5n/REQ-5o/REQ-5p, "mirroring the same discoverability I
> already get browsing groups"), and
> `ChatConversationService#requireReadableConversation` correctly
> rejects a non-participant's direct open with a 403, a user clicking
> such a message result today gets an untreated 403 with no recovery
> path — discovered because the message points into a group the user
> never joined. This subsection extends REQ-24's unconditional
> "open that message's conversation" behavior to branch on the new
> `isParticipant` field, reusing `onEntitySelect`'s existing flow rather
> than inventing a second one.

- **REQ-47 [Ubiquitous]** The frontend's message-result model (whatever
  type backs `ChatSearchRowResult`'s message-kind variant) shall carry
  the new `isParticipant`/`visibility` fields the backend now returns
  on `ChatMessageSearchResultDto` (backend REQ-44), consumed verbatim —
  the frontend never re-derives or infers either value client-side,
  the same "reflect exactly what the backend response contains" posture
  this document already establishes elsewhere (see "Relationship to
  `chat-unified-ui`'s SPEC").
- **REQ-48 [Event-Driven]** When the user clicks a message result whose
  `isParticipant` is `true` (the common case — a `PEER_DIRECT` result,
  or a `PEER_GROUP` result the caller already participates in), the
  system shall behave exactly as REQ-24/REQ-33 already specify: open
  the conversation directly and jump to the matched message. This is
  unchanged from today's behavior for every result that was already
  reachable without a 403.
- **REQ-49 [Event-Driven]** When the user clicks a message result whose
  `isParticipant` is `false` (a `PUBLIC`/`REQUEST_TO_JOIN` group the
  caller has not yet joined, surfaced only via the backend's
  message-content discoverability carve-out), the system shall route
  through the same join/request-to-join flow `onEntitySelect`'s
  `'group'` case already uses for the identical situation
  (`rowsService.onGroupClick`, built from the result's
  `conversationId`/`conversationTitle`/`visibility`), instead of
  navigating directly to the conversation — the system shall never
  attempt a direct navigation for a message result whose `isParticipant`
  is `false`.
- **REQ-50 [Unwanted Behavior]** If a message result is routed through
  REQ-49's join/request-to-join flow, then the jump-to-message behavior
  (REQ-33/REQ-34's automatic scroll-and-flash) is not required to fire
  for that click — joining, or requesting to join, a group does not by
  itself grant the same immediate in-thread context that opening an
  already-joined conversation does, and `rowsService.onGroupClick`'s own
  existing post-join/post-request navigation (unchanged by this
  amendment) governs what the user sees next, not this document's
  jump-to-message mechanism. A future amendment could add
  jump-to-message-after-join symmetrically if the product owner wants
  it; it is not requested here and not assumed by this amendment.
- **REQ-51 [Unwanted Behavior]** If a message result's `isParticipant`
  field is absent/undefined (an off-contract, defensive case — should
  not occur once backend REQ-44 ships, since it is always populated),
  then the system shall treat it identically to `isParticipant: true`
  (attempt the direct-navigation path, REQ-48) rather than throwing or
  silently doing nothing — consistent with this document's existing
  "degrade to the safer/older behavior on an unexpected but plausible
  off-contract shape" posture (see REQ-41's `matchedRole`-absent
  handling), since direct navigation is what every message result did
  before this amendment and remains the correct outcome for any caller
  who does, in fact, already have participant access.

**Acceptance criteria (this subsection):**

- [x] A message result whose `isParticipant` is `true` opens its
      conversation directly and jumps to the matched message, unchanged
      from today's behavior.
- [x] A message result whose `isParticipant` is `false` never navigates
      directly to `/chat/{conversationId}` — clicking it routes through
      the same join/request-to-join UI `onEntitySelect`'s `'group'`
      case already uses, confirmed with a fixture asserting no 403 is
      ever surfaced to the user for this click path.
- [x] **Regression, whole-feature scope:** no search result of any kind
      (person, group, Support, RAG, message) ever produces a raw,
      untreated 403 when clicked — every result whose target may not
      yet be directly open-able (currently: a non-participant group
      result via `onEntitySelect`'s `'group'` case, and, per this
      amendment, a non-participant message result via `onMessageSelect`)
      is routed through the appropriate join/request/handled flow
      instead of a bare navigation.
- [x] A message result with `isParticipant` absent/undefined still
      opens directly (REQ-51's fail-open default), not blocked or
      errored.

**Out of scope (this subsection):**

- Any change to `onEntitySelect`'s `'group'` case itself — it is
  already correct today and is reused unchanged, not modified.
- Any change to which messages/conversations the backend returns for a
  search — backend REQ-1 through REQ-15/REQ-5r through REQ-5v are
  unaffected; this is purely a client-side routing fix consuming a new,
  additive backend field.
- Jump-to-message behavior after a successful join/request initiated
  from a message result (REQ-50) — explicitly deferred, not assumed by
  this amendment.
- Any equivalent fix for a hypothetical non-participant Support or RAG
  message-content result — REQ-1 (backend) already scopes
  message-content search to `PEER_DIRECT`/`PEER_GROUP` only, and RAG
  turn-content matches (REQ-27–REQ-33 backend, REQ-38–REQ-43 here) are
  strictly owner-scoped with no discoverability carve-out (backend
  REQ-29) — so this class of bug structurally cannot occur for either
  of those result kinds, and no equivalent branch is added for them.

### Original acceptance criteria (message-content-only, filter-form shape) are retired along with `chat-search-dialog.component.ts`

> Kept for history, not reproduced as checkable items — see "Acceptance
> criteria" further below for the authoritative list.

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
  matched message once it is loaded into the thread. **(Amended
  2026-08-11, message-result participancy routing fix): this applies
  only to the REQ-48 (`isParticipant: true`) path — see REQ-50 for the
  not-yet-joined-group case.**
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

### Acceptance criteria (2026-08-11 RAG turn-content amendment)

- [x] A "Base de artigos" result returned with a non-empty
      `matchedSnippet` renders that snippet beneath the conversation
      title; a "Base de artigos" result with no `matchedSnippet`
      renders exactly as before (title only).
- [x] A rendered snippet that literally, case-insensitively contains the
      current query shows that substring `<mark>`-wrapped, reusing
      REQ-32's existing mechanism; a snippet that doesn't literally
      substring-match renders unmarked.
- [x] A result with `matchedRole === "USER"` shows a distinct
      "Você perguntou"/"you asked"-shaped indicator; one with
      `matchedRole === "ASSISTANT"` shows a distinct
      "A IA respondeu"/"the assistant answered"-shaped indicator; the
      two are visually and textually distinguishable without relying on
      color alone.
- [x] A result with a snippet but no role (a defensive, off-contract
      case) still renders the snippet, only omitting the role
      indicator.
- [x] Clicking a turn-content "Base de artigos" result opens that
      conversation exactly like any other RAG result — no scroll-to-turn
      behavior is expected or tested.

### Out of scope (2026-08-11 RAG turn-content amendment)

- **Scroll-to-turn / flash-highlight inside the RAG conversation view**,
  analogous to REQ-33 through REQ-36's message-result jump-to-message —
  not requested by the product owner for this amendment and not implied
  by the backend contract (which returns a snippet for display in the
  search dropdown only, not a turn id the RAG conversation view has any
  existing mechanism to scroll to). A future amendment could add this
  symmetrically to REQ-33–36 if wanted, but it is a materially different
  scope decision, not a natural extension assumed here.
- **Highlighting more than the first literal substring match inside a
  snippet** — mirrors REQ-32/`splitOnMatch`'s existing "first match
  only" decision; not revisited by this amendment.
- **Any change to how a "Base de artigos" result opens** (REQ-23/
  REQ-43) — a turn-content match opens the same conversation view a
  title match already opens, unchanged.
- **The exact visual treatment (icon choice, color, spacing) of the
  role indicator** — REQ-40 pins the functional requirement only; the
  pixel-level design is explicitly deferred to PLAN/`design-system-
  ui-ux` if genuinely ambiguous, not decided in this document.

## Non-functional requirements

- Accessibility: the search bar, its result groups, "see more" actions,
  and "recent places" list are keyboard-navigable and screen-reader-
  labeled (each group announced, each result an accessible name),
  consistent with this app's existing accessibility conventions —
  carried forward from the original document, extended to the new
  grouped/type-ahead shape. **(Amended 2026-08-11):** a "Base de
  artigos" result's role indicator (REQ-40) is included in that row's
  accessible name/label, and never relies on color alone to convey the
  question/answer distinction (REQ-42).
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
  unchanged posture from the original document. **(Amended
  2026-08-11):** this includes `matchedSnippet`/`matchedRole` — the
  frontend never infers or re-derives which turn matched, only displays
  what the backend already resolved and truncated. **(Amended 2026-08-11,
  message-result participancy routing fix):** this also includes
  `isParticipant`/`visibility` on a message result — the frontend never
  infers participancy client-side (e.g. by guessing from conversation
  kind), it only branches on what the backend response already
  resolved.
- Localization: unchanged from the original document — locale continues
  to be driven entirely by the app's existing in-app language selection,
  consumed automatically by the existing `localeInterceptor`; no new
  locale UI is introduced by this amendment either. The role-indicator
  label text (REQ-40) is a translated i18n string like every other
  user-facing label in this document, not hardcoded.

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
- [x] **(New, 2026-08-11)** See "Acceptance criteria (2026-08-11 RAG
      turn-content amendment)" above for REQ-38 through REQ-43's own
      checklist — implemented and verified by tests as of this SPEC
      amendment.
- [x] **(New, 2026-08-11, message-result participancy routing fix)**
      See "Acceptance criteria (this subsection)" under "Message-result
      participancy routing" above for REQ-47 through REQ-51's own
      checklist — implemented and verified by tests as of this SPEC
      amendment. Note: the admin/oversight-access routing gap identified
      during AppSec review (a `STAFF_ADMIN`/`MEMBER_ADMIN` reaching a
      conversation via oversight, not genuine non-participation) is
      **not** covered by REQ-47–51 as written and remains open — see
      `PROJECT_STATUS.md`'s "Next up" section for tracking; a future
      SPEC amendment is needed before that case is addressed.

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
  **(Amended 2026-08-11): this gap is now closed for the RAG
  turn-content half specifically — see the new subsection above; the
  general entity-search contract itself has been shipped since
  2026-08-10.**
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
- Highlighting/snippet-generation of the exact matched term for
  message results — unchanged (this is REQ-32's job, already shipped);
  **(Amended 2026-08-11): "Base de artigos" results now get an
  equivalent, backend-supplied snippet too, per the new subsection
  above — this bullet no longer applies to that result kind.**
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
- **(New, 2026-08-11)** See "Out of scope (2026-08-11 RAG turn-content
  amendment)" above for the scope boundaries specific to REQ-38 through
  REQ-43 (no scroll-to-turn, no all-occurrences highlighting, no pixel
  design decisions).
- **(New, 2026-08-11, message-result participancy routing fix)** See
  "Out of scope (this subsection)" under "Message-result participancy
  routing" above for the scope boundaries specific to REQ-47 through
  REQ-51 (no change to `onEntitySelect`'s already-correct `'group'`
  case, no jump-to-message-after-join, no equivalent fix for Support/RAG
  message-content results since that class of bug cannot occur for
  either).

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

**Amended (2026-08-11, RAG conversation turn-content search) — status:**
REQ-38 through REQ-43 above have **no open Tier 3 question** — the
backend contract they depend on is already shipped
(`matchedSnippet`/`matchedRole`, additive/optional fields), and the two
genuinely ambiguous presentation choices (exact snippet placement
styling, exact role-indicator wording/icon) are explicitly deferred to
PLAN/`design-system-ui-ux` rather than blocking this SPEC — consistent
with how REQ-1(5)'s "grouped by type" call and REQ-35's flash-timing
call were both handled earlier in this same document as Tier 2 calls
recorded with reasoning, not Tier 3 stops. **This subsection is ready
for PLAN** — see the companion PLAN.md/TASKS.md amendment for the
low-risk, additive task breakdown (consuming two new optional DTO
fields, no route/contract change).

**Amended (2026-08-11, message-result participancy routing fix) —
status:** REQ-47 through REQ-51 above have **no open Tier 3 question**
— this is a bug fix, not a product/business decision: the intended
behavior (route a not-yet-joined group's message result through the
existing join/request-to-join flow, exactly like the already-correct
`onEntitySelect`'s `'group'` case) follows directly from decisions
already made and confirmed (REQ-19's `isParticipant`/`visibility`
shape; the backend's own REQ-5l/REQ-5n/REQ-5o/REQ-5p discoverability
carve-out, which was itself a confirmed product decision, not
reopened here). **This subsection is blocked on the companion backend
amendment landing first** (`ChatMessageSearchResultDto`'s
`isParticipant`/`visibility` fields, backend REQ-44 through REQ-46) —
PLAN/TASKS.md for this subsection should not be scheduled ahead of
that backend work. Once both are read back and approved, this is a
low-risk, additive PLAN (one new field pair consumed, one new
conditional branch reusing an existing flow, no new endpoint, no new
UI component).
