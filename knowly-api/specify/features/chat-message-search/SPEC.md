# SPEC — chat-message-search (backend)

> The what and the why. No technical implementation details.
>
> **Amended (2026-08-10) — unified search backend contract, companion
> to `knowly-app/specify/features/chat-message-search/SPEC.md`'s own
> "Amended (2026-08-10)" section and
> `knowly-app/specify/features/chat-unified-ui/SPEC.md`'s "Amended (5)"
> section.** Both frontend documents were amended to replace the shipped
> message-content-only search dialog with a Slack-style unified search
> bar that also finds people, groups, Support, and RAG ("Base de
> artigos") conversations, plus a "recent places" list on an empty
> query — and both explicitly flagged that no backend contract exists
> for any of that beyond message content. This amendment is that
> contract. **REQ-1 through REQ-15 below are unchanged and remain fully
> authoritative** — the shipped `GET /api/chat/messages/search` endpoint
> is not being replaced, only supplemented by a new, separate endpoint
> for entity search (REQ-16 onward). Nothing about message-content
> search's own access-control posture (REQ-2/REQ-5: re-derived per
> request, no oversight bypass) is relaxed anywhere in this amendment —
> see "Unified entity search" below, which applies the identical rule to
> every new result kind.
>
> **Same-file amendment, not a new feature — the call, and why.** This
> extends an already-shipped, already-scoped feature's own capability
> (searching from the same product surface, described as one thing by
> the product owner: "uma barra única que encontra canais, pessoas e
> trechos de conversas") rather than being a conceptually separate
> capability. It follows the exact precedent the frontend side already
> established for the same reason (`chat-message-search`/`chat-unified-ui`
> frontend SPECs amended in place, not forked into new documents). A new
> top-level feature would also incorrectly imply the shipped message-
> search endpoint and access-control model are being reconsidered from
> scratch, when in fact REQ-1 through REQ-15 are being reused and
> extended, not revisited.
>
> **Status: both Tier 3 product/access-control questions this amendment
> depended on are now resolved by the product owner (see "Tier 3 —
> status" at the end of this document) — REQ-19/REQ-20 below are final.
> This document, in full, is ready for read-back and sign-off**,
> together with the two frontend amendments
> (`knowly-app/specify/features/chat-message-search/SPEC.md`'s "Amended
> (2026-08-10)" and `knowly-app/specify/features/chat-unified-ui/SPEC.md`'s
> "Amended (5)") — all three should be approved together before any
> PLAN work starts, since none is a complete, buildable picture alone.

## Context and motivation

(Unchanged from the original document — see below. This amendment adds
a new "Context and motivation" note specific to the unified-search
extension.)

`internal-team-chat` and `chat-group-membership-management` shipped
peer-to-peer 1:1 and group chat, but there is no way to search *inside*
message content — only to browse conversation-by-conversation. The
confirmed product need (deferred from `chat-group-membership-management`
and `chat-unified-ui`, documented in `PROJECT_STATUS.md` item 16, full
technical investigation already done by `data-architect-dba` on
2026-08-08): a Slack-style "I remember roughly what I typed, but not who
I said it to or which group" recall search — free-text `q` plus optional
`senderId`/`conversationId`/date-range filters, returned chronologically,
not ranked by relevance.

This is **lexical recall**, not semantic recall — pgvector/embeddings
solve "about topic X even if reworded"; this feature solves "I typed
roughly this exact word/phrase and want to find it again." Native
Postgres full-text search (`tsvector`/`tsquery` + GIN) is the right tool
per the prior investigation — cheaper than an embedding pipeline, no
per-message API calls, no drift risk, and appropriately sized for this
corpus (thousands–low tens of thousands of rows per tenant, not
Slack-corporate scale). A second search datastore (Elasticsearch) is
rejected for the same reason the prior investigation rejected it: it
would require a sync/dual-write pipeline and re-implementing
`chat_participants` ACL scoping outside Postgres, pure overkill here.

**Status: Tier 3 decisions resolved by the product owner (see below).
Ready for PLAN.md once this document is read back and approved.**

**Amendment context (2026-08-10):** the frontend redesign that consumed
this endpoint (`chat-message-search`/`chat-unified-ui`, both frontend)
was itself amended to unify search into one Slack-style bar that also
finds people, groups, Support, and RAG conversations, not just message
content — see both documents' "Amended (2026-08-10)"/"Amended (5)"
sections. Neither frontend document invents the backend contract this
requires; both explicitly defer it here. This amendment supplies it.

## Relationship to existing SPECs (read before implementing)

- This is a **net-new capability**, not a reversal of any existing "Out
  of scope" line — neither `internal-team-chat` nor
  `chat-group-membership-management` excludes message-content search;
  it was simply never built. `chat-unified-ui`'s own SPEC explicitly
  deferred it ("full-text search over message content... is not part of
  this SPEC... See 'Out of scope / Future work'") at the product owner's
  instruction, which is exactly the deferral this SPEC now picks up.
- **Support channels are explicitly out of scope for this SPEC**
  (product-owner decision, see "Tier 3 — resolved" below) — this SPEC
  covers `PEER_DIRECT` and `PEER_GROUP` conversations only.
  **(Amended 2026-08-10): this exclusion is unchanged for message
  *content* — searching inside Support's messages is still out of
  scope. It does not extend to Support's own row/entity, which the
  unified entity-search amendment below makes findable by design (REQ-19)
  — these are two different things, see that requirement's note.**
- **No oversight/look-in bypass of any kind applies to this endpoint**
  (product-owner decision, see "Tier 3 — resolved" below) — every
  caller, including `STAFF_ADMIN`/`MEMBER_ADMIN`, is scoped strictly to
  conversations they are a current, non-removed participant of. The
  existing `chat.group.oversight_view`/REQ-5a/REQ-5b "one-group,
  explicitly-opened inspection" bypass, and the archived-group
  staff-visibility grants from `chat-group-membership-management`
  (REQ-44/REQ-45), are **not** reused here — reusing either for a
  keyword-search endpoint would let staff search across every
  group/tenant in one request, a materially broader capability the
  product owner explicitly declined to grant. **(Amended 2026-08-10):
  this same "no oversight bypass, re-derived per request" rule governs
  every new entity-search result kind below (REQ-16 through REQ-23) —
  it is not relaxed for people/groups/Support/RAG results just because
  they are a new capability. This is the exact class of constraint an
  AppSec review previously blocked on for this same feature area; it is
  not being reopened here.**
- Must respect `chat-group-membership-management`'s archive (REQ-43) and
  soft-delete (REQ-49) semantics: a conversation the caller is no longer
  a current participant of — because they left, were removed, or the
  conversation was archived/soft-deleted — must never surface in their
  search results, exactly like `chat-group-membership-management`'s
  REQ-46 already establishes for its own read paths.
- Reuses `chat_messages`/`chat_participants` unchanged in shape apart
  from the new indexed column(s) this SPEC adds (see Non-functional
  requirements) — no new entity beyond the search index itself.
- **(Amended 2026-08-10) reuses, unchanged, the exact access-control
  precedents already established elsewhere in this codebase for each new
  result kind**, rather than inventing new scoping logic per kind:
  - Group name matches reuse `ChatConversationService#listDiscoverableGroups`'s
    existing rule (participant groups, plus non-participant `PUBLIC`/
    `REQUEST_TO_JOIN` groups the caller is `ChatEligibilityService`-
    eligible for; `PRIVATE` groups the caller isn't already in are never
    matched) — **confirmed by the product owner, see Tier 3 item A
    below; REQ-19 is final.**
  - Person name matches reuse `ChatEligibilityService`'s existing
    "who can I start a conversation with" rule (the same rule
    `listCandidates(actor, "direct", ...)` already applies) —
    **confirmed by the product owner, see Tier 3 item B below; REQ-20
    is final.**
  - Support row matching reuses the same claim/transfer/close visibility
    rule `SupportChannelController`/`internal-team-chat` REQ-10–REQ-18
    already establish (member sees their own channel; staff with the
    support permission sees the unclaimed inbox/their claimed ticket) —
    not redefined here.
  - RAG ("Base de artigos") conversation title matches reuse
    `conversations`' existing REQ-1 rule (a viewer only ever sees their
    own RAG conversations) — not redefined here, and not extended to
    any cross-user visibility.

## Tier 3 — resolved (product owner, confirmed this conversation)

1. **Support-channel content: excluded entirely from this SPEC.** The
   product owner confirmed peer/group chat only — `PEER_DIRECT` and
   `PEER_GROUP` conversations. If Support-ticket content search is ever
   wanted, that is a separate, future feature with its own access-model
   analysis (Support's claim/transfer/close lifecycle and
   member/staff/`support-view` access shapes don't map onto this SPEC's
   simple "current participant" rule), not folded in here.
2. **No oversight bypass of any kind.** The product owner confirmed
   staff search only conversations they are an actual current
   participant of, the exact same rule as every other caller — no
   `STAFF_ADMIN`/`MEMBER_ADMIN` platform-wide or per-request bypass path
   exists in this SPEC at all.
3. **Locale-aware search, resolved server-side from the app's existing
   locale plumbing, not a client-freely-supplied parameter.** The
   product owner wants both `en` and `pt-BR` users to get effective
   recall, not a single fixed language. This codebase already has a
   working, narrow precedent for exactly this shape:
   `deletion-confirmation-token`'s `DeletionConfirmationLocaleResolver`
   resolves the caller's locale from the raw `Accept-Language` header
   (sent by the frontend's `localeInterceptor`, itself driven by
   `TranslocoService`'s active in-app language — the same source of
   truth already used for every other locale-dependent behavior in this
   app, e.g. language-switcher-driven UI text). This SPEC reuses that
   same shape rather than inventing a new one: the search endpoint
   resolves the caller's locale server-side from the request's
   `Accept-Language` header (highest-priority range, `pt*` → `pt-BR`,
   anything else/missing/unparseable → `en` — identical resolution rule
   to `DeletionConfirmationLocaleResolver`, though the exact
   class/reuse-vs-new-instance decision is a PLAN-level call), never
   from a client-supplied query parameter the caller could set
   arbitrarily. This keeps "which language index gets queried"
   consistent with the same in-app language selector driving every
   other localized string, rather than introducing a second,
   independent notion of "the user's language."

## User stories

- As a user who remembers roughly what they typed but not who they said
  it to or which conversation it was in, I want to search across all my
  conversations by free-text content and find it.
- As a user narrowing a broad memory, I want to optionally filter that
  search by sender, by a specific conversation, and/or by a date range.
- As a user, I want search results ordered chronologically (oldest or
  newest first, consistently) rather than by a relevance score I can't
  predict, since I'm trying to recall roughly *when* I said something.
- As a user, I want search to never surface a message from a
  conversation I've left, been removed from, or that's archived/deleted
  — my search results should only ever reflect conversations I can
  currently actually read.
- As a Portuguese-speaking user, I want my search to find Portuguese
  word forms (plurals, conjugations) the way it would for a native
  Portuguese search tool; as an English-speaking user, I want the same
  for English — without either of us being able to force the other's
  language behavior via a request parameter.
- **(Amended 2026-08-10):** As a user, I want the same search bar that
  finds message content to also find a person, a group, my Support
  conversation, or a "Base de artigos" conversation by name/title, so I
  don't need to know in advance which of those I'm looking for before I
  start typing.
- **(Amended 2026-08-10):** As a user opening the search bar with
  nothing typed yet, I want to see a short list of conversations I've
  recently interacted with, without needing to type anything.

## Requirements (EARS/GEARS)

### Search scope and access control

- **REQ-1 [Ubiquitous]** The system shall provide a message-content
  search capability limited to `PEER_DIRECT` and `PEER_GROUP`
  conversations; `SUPPORT` conversations are never included in results
  or search indexing scope for this capability.
- **REQ-2 [Ubiquitous]** The system shall scope every search request to
  conversations for which the calling user currently holds a
  non-removed, non-soft-deleted `chat_participants` row — re-derived at
  request time from the caller's actual current membership, never from
  a cached or client-supplied list of conversation ids.
- **REQ-3 [Unwanted Behavior]** If a caller supplies a `conversationId`
  filter for a conversation they are not currently a participant of (or
  that does not exist, or is `SUPPORT`, or is archived/soft-deleted),
  then the system shall exclude that conversation from the results
  without revealing whether it exists — behaving identically to "the
  caller has zero matching messages in that conversation," not
  distinguishing "no access" from "no matches" or "no such
  conversation."
- **REQ-4 [Unwanted Behavior]** If a conversation the caller was
  previously a participant of has since been archived (per
  `chat-group-membership-management` REQ-43) or soft-deleted (REQ-49),
  or the caller has left/been removed from it, then the system shall
  exclude that conversation's messages from that caller's search
  results from that point forward, regardless of any earlier
  participation.
- **REQ-5 [Ubiquitous]** The system shall apply no special-cased
  oversight or "look-in" bypass to this capability — every caller,
  including `STAFF_ADMIN`/`MEMBER_ADMIN`, is subject to REQ-2 exactly
  like any other user; the existing `internal-team-chat`/
  `chat-group-membership-management` oversight/archived-group-visibility
  grants do not extend to search.

### Query behavior

- **REQ-6 [Event-Driven]** When an authenticated user submits a search
  request with a non-blank free-text query `q`, the system shall return
  messages, from conversations the caller currently has access to
  (REQ-2), whose content matches `q` using free-text web-search query
  semantics (supporting quoted phrases and `-exclude`d terms as a user
  would type them, not a raw structured query syntax).
- **REQ-7 [Optional Feature]** Where the caller additionally supplies a
  `senderId` filter, the system shall further restrict results to
  messages sent by that specific user.
- **REQ-8 [Optional Feature]** Where the caller additionally supplies a
  `conversationId` filter, the system shall further restrict results to
  that specific conversation, subject to REQ-3's access rule.
- **REQ-9 [Optional Feature]** Where the caller additionally supplies a
  `dateFrom` and/or `dateTo` filter, the system shall further restrict
  results to messages sent within that (inclusive) range.
- **REQ-10 [Ubiquitous]** The system shall order search results
  chronologically (by message send time), not by text-relevance
  ranking, and shall paginate results via a cursor rather than returning
  an unbounded result set.
- **REQ-11 [Unwanted Behavior]** If `q` is blank, missing, or contains no
  usable search terms after parsing, then the system shall reject the
  request rather than returning an unfiltered listing of every message
  the caller can access.
- **REQ-12 [Unwanted Behavior]** If `dateFrom` is later than `dateTo`,
  then the system shall reject the request.

### Locale-aware matching

- **REQ-13 [Complex]** Where the caller's resolved locale (per "Tier 3
  — resolved" item 3 above: derived server-side from the request's
  `Accept-Language` header, `pt*` → Portuguese, anything else → English)
  is Portuguese, when a search request is submitted, the system shall
  match `q` against the Portuguese-language full-text index of message
  content; where the resolved locale is English (or unresolved), the
  system shall match against the English-language full-text index.
- **REQ-14 [Ubiquitous]** The system shall determine which language
  index a search query runs against solely from the caller's
  server-side-resolved locale (REQ-13) — never from a client-supplied
  query parameter, header value the caller could set to something other
  than their actual in-app language, or per-message auto-detected
  language.
- **REQ-15 [Unwanted Behavior]** If the caller's `Accept-Language`
  header is missing, empty, or unparseable, then the system shall
  default to the English-language full-text index (same
  fail-safe-default posture as `DeletionConfirmationLocaleResolver`'s
  existing resolution rule) rather than rejecting the request.

### Unified entity search (Amended 2026-08-10)

> **New section. Fully resolved — REQ-16 through REQ-26 below are all
> final and ready for PLAN.** Backs the frontend's unified search bar's
> non-content result kinds (people, groups, Support, RAG conversations)
> and its "recent places" empty-query state. Message-content search
> (REQ-1 through REQ-15) is unchanged and continues to be served by the
> existing `GET /api/chat/messages/search` endpoint — this section adds
> a **separate, new** endpoint rather than folding entity matching into
> that one (see the architectural rationale in Non-functional
> requirements below); the two are combined client-side by the
> consuming frontend, not server-side.

- **REQ-16 [Ubiquitous]** The system shall provide a unified entity
  search capability, separate from message-content search (REQ-1
  through REQ-15), that matches the caller's query against: (a) people
  the caller could message, by display name; (b) groups, by name; (c)
  the caller's own Support channel/ticket, if the query matches a
  fixed, always-available "Suporte"/"Support" label; (d) the caller's
  own RAG ("Base de artigos") conversations, by title.
- **REQ-17 [Ubiquitous]** The system shall scope every entity-search
  result to what the calling user is actually authorized to see or
  reach, re-derived at request time from the caller's current state
  (tenant membership, group participation/discoverability, Support
  role, RAG conversation ownership) — never from a cached or
  client-supplied access assertion. This mirrors REQ-2/REQ-5's existing
  posture for message content, extended to every new result kind, with
  no exception.
- **REQ-18 [Ubiquitous]** The system shall apply no special-cased
  oversight or "look-in" bypass to any entity-search result kind — a
  `STAFF_ADMIN`/`MEMBER_ADMIN` caller's people/group/RAG results are
  scoped exactly as any other caller's would be; only Support's own
  existing staff-role visibility (unclaimed inbox, claimed ticket —
  `internal-team-chat` REQ-10–REQ-18, unchanged) governs whether a
  Support result appears for a staff caller, which is not a new
  oversight bypass but that feature's own pre-existing, approved access
  model.
- **REQ-19 [Ubiquitous] (final — confirmed by the product owner, see
  Tier 3 item A)** The system shall match group names against groups
  the caller currently participates in, **plus** non-participant
  `PUBLIC`/`REQUEST_TO_JOIN` groups the caller is
  `ChatEligibilityService`-eligible for — the identical rule
  `ChatConversationService#listDiscoverableGroups` already applies for
  column 3's browse list, reused unchanged rather than redefined for
  search. `PRIVATE` groups the caller is not already a participant of
  are never matched. A matched non-participant group opens exactly as
  it already does from column 3 today (join/request-to-join per its
  visibility, `chat-group-membership-management`'s existing flow) — this
  amendment does not change that behavior, only adds a second way to
  reach the same discoverable group by name.
- **REQ-20 [Ubiquitous] (final — confirmed by the product owner, see
  Tier 3 item B)** The system shall match person display names against
  only users the caller is `ChatEligibilityService`-eligible to
  message — the identical rule `listCandidates(actor, "direct", ...)`
  already applies for the existing candidate list, reused unchanged
  rather than redefined for search. A person the caller is not eligible
  to message (no shared tenant/staff-capability anchor) is never
  matched, regardless of how closely their name matches the query — no
  dead-end result is ever returned, and this endpoint reveals no wider a
  set of people than the existing candidate-list endpoint already does.
- **REQ-21 [Ubiquitous]** Support-row matching shall reuse the caller's
  existing, unchanged Support visibility exactly as
  `internal-team-chat` REQ-10 through REQ-18 already establish (a
  member matches their own channel; staff with the support permission
  matches the unclaimed inbox and/or their own claimed ticket; a viewer
  with no Support role/channel gets no Support result) — not redefined
  by this amendment.
- **REQ-22 [Ubiquitous]** RAG conversation title matching shall be
  scoped strictly to the caller's own RAG conversations — a viewer never
  matches another user's "Base de artigos" conversation by title,
  mirroring `conversations`' existing REQ-1 ownership rule unchanged.
- **REQ-23 [Unwanted Behavior]** If a caller's query would otherwise
  match an entity (person, group, Support row, RAG conversation) they
  are not authorized to see or reach per REQ-17 through REQ-22, then the
  system shall omit that entity from the results without revealing that
  a matching-but-inaccessible entity exists — same non-revealing
  posture as REQ-3 already establishes for an inaccessible
  `conversationId` filter.
- **REQ-24 [Event-Driven]** When an authenticated user submits an
  entity-search request with a non-blank free-text query, the system
  shall return matching people, groups, Support row (if any), and RAG
  conversations, each result carrying enough identifying/display data
  (id, display name/title, kind, and — for people — avatar URL, mirroring
  `CandidateUserDto`'s existing shape) for the frontend to render and
  open it directly, without a further lookup round-trip per result.
- **REQ-25 [Optional Feature]** Where the caller's query is blank, the
  system shall return a "recent places" list — conversations (any kind:
  1:1, group, Support, RAG) the caller has recently interacted with —
  scoped by the exact same access rules as REQ-17 (a recent conversation
  the caller is no longer a current participant of, e.g. left/removed/
  archived/soft-deleted, is never included, mirroring REQ-4's existing
  posture for message search).
- **REQ-26 [Ubiquitous]** "Recent places" (REQ-25) shall be served from
  data the caller's existing conversation-listing capability
  (`ChatConversationService#listConversations`) already exposes,
  ordered by that capability's existing recency signal, rather than
  introducing a new backend query or a new persisted "last interaction"
  timestamp for this purpose specifically. **Rationale (Tier 2 call, not
  Tier 3):** `ChatConversationDetailDto` carries no `lastMessageAt`
  field today, and `chat-unified-ui/SPEC.md`'s own REQ-2d already
  documents this as a known, currently-accepted gap — column 1's
  "already talked to" ordering is itself only ever a conversation-id-
  descending proxy for true recency, not real timestamp ordering, until
  a future backend enhancement adds one. Building a new, more-accurate
  "recent places" signal here — while the feature this data is *also*
  used for (column 1) still uses the coarser proxy — would produce two
  inconsistent notions of "recent" on the same screen for no material
  gain to this feature specifically; "recent places" therefore uses the
  identical proxy already accepted for column 1, and a real recency
  signal (if/when built) benefits both at once rather than needing to be
  duplicated per consumer. This is not a scope expansion of that
  already-tracked, already-accepted gap — it is deliberately not
  fixed by this SPEC.

## Non-functional requirements

- Data model: message content search is backed by native Postgres
  full-text search (`tsvector`/GIN index), not pgvector/embeddings and
  not a second search datastore (Elasticsearch or similar) — per the
  prior `data-architect-dba` investigation, this is lexical recall, not
  semantic recall, and this corpus size does not justify a second
  datastore's sync/dual-write/re-implemented-ACL overhead.
- Data model: **two** generated, stored, indexed columns on
  `chat_messages` are needed to satisfy REQ-13 — a
  `content_tsv_pt tsvector GENERATED ALWAYS AS (to_tsvector('portuguese',
  content)) STORED` and a `content_tsv_en tsvector GENERATED ALWAYS AS
  (to_tsvector('english', content)) STORED`, each with its own GIN
  index, rather than a single column keyed by a fixed or per-message
  language. Generated/stored columns are safe here because messages are
  immutable (no edit/delete of content is in scope anywhere in chat
  today, per `internal-team-chat`). Exact migration numbering is a
  PLAN-level detail to confirm against `ls
  knowly-api/src/main/resources/db/migration/` at implementation time
  (next free number as of this SPEC's drafting is `V34`).
- Query parsing: `q` is parsed via Postgres's `websearch_to_tsquery()`
  against whichever of `content_tsv_pt`/`content_tsv_en` REQ-13 selects,
  not raw `to_tsquery()` — so ordinary free-text input (quotes,
  `-exclude`d terms) behaves the way a user typing into a search box
  would expect, per the prior investigation's recommendation.
- Security: REQ-2/REQ-3/REQ-4/REQ-5's access-control rule is the single
  most important constraint in this SPEC — it must be enforced by
  joining through `chat_participants` filtered to the caller (or
  excluded via REQ-3's non-revealing behavior) **before** any other
  filter (`q`, `senderId`, `conversationId`, date range) is applied, not
  layered on afterward as a post-filter that a query-planning shortcut
  could bypass. This mirrors the exact posture
  `chat-group-membership-management`'s own non-functional requirements
  already establish for its own authorization checks ("re-derived from
  the caller's actual current state at request time — never cached,
  never trusted from client input"). **(Amended 2026-08-10): the
  identical "before any other criterion, re-derived per request" posture
  applies to every entity-search result kind's own access check
  (REQ-17/REQ-23) — a group/person/RAG-conversation match must never be
  returned and then filtered client-side; the filtering happens
  server-side, before the result is ever serialized.**
- Locale resolution: follows the same narrow, non-global-`LocaleResolver`
  shape as `DeletionConfirmationLocaleResolver` (see Tier 3 item 3) —
  whether this feature reuses that exact class or introduces its own
  narrowly-scoped resolver with identical resolution logic is a
  PLAN-level decision, not a SPEC-level one; either way, this locale
  resolution must not be registered as a Spring-wide `LocaleResolver`
  bean, consistent with the existing precedent's explicit scoping.
- Performance: the `q`-only-blank rejection (REQ-11) exists partly to
  keep this endpoint from ever being usable as an unbounded "list every
  message I can see" endpoint — pagination (REQ-10) is required on every
  request, not optional.
- Observability: search requests are a reasonable candidate for this
  codebase's existing structured-logging conventions (actor, query
  presence — never raw query text, since that's user-authored free text
  that may contain sensitive content — and outcome), but are not
  security-sensitive state changes and are not required to go through
  `@AuditLog`; exact logging shape is a PLAN-level decision.
- Multi-tenancy: this endpoint does not need its own tenant-scoping
  logic beyond REQ-2 — `chat_participants` membership itself already
  implies the correct tenant/staff-group boundary (a user can only be a
  participant of conversations their existing eligibility rules already
  scoped them into), consistent with how `internal-team-chat`/
  `chat-group-membership-management` already reason about this
  boundary.
- **(Amended 2026-08-10) Architectural call: entity search (REQ-16
  through REQ-26) is a new, separate endpoint, not a parameter added to
  `GET /api/chat/messages/search`.** Message search already has its own
  cursor-based pagination, locale-resolved full-text matching, and
  strict single-result-kind (message) DTO shape; entity search returns
  four structurally different, independently-capped result groups (per
  the frontend's own "grouped by kind, per-group 'see more'"
  requirement) with no natural single cursor across them. Combining both
  into one response would either force entity results to share message
  search's pagination contract (wrong shape for a capped preview list)
  or make message search's contract conditionally different depending
  on which other parameters were sent (implicit, harder-to-audit
  behavior). Two independent endpoints, each with the shape its own
  result type actually needs, is more consistent with this codebase's
  existing pattern of one endpoint per well-defined read shape (e.g.
  `listConversations` vs. `listDiscoverableGroups` are already separate
  endpoints over overlapping data, for the same reason). **This
  directly satisfies the frontend SPEC's own anticipated fallback**
  (`chat-message-search/SPEC.md`'s REQ-30: "if PLAN implements... two
  separate backend calls... show the groups that did succeed[...] a
  partial failure never blank[s] the entire dropdown") — the frontend
  amendment already designed for this exact shape as one of its two
  anticipated outcomes, so this is not introducing a surprise
  incompatibility.
- **(Amended 2026-08-10) Entity-search response shape is grouped and
  per-group-capped, not cursor-paginated like message search.** Each of
  the four result kinds (people, groups, Support, RAG conversations) is
  capped at a small fixed count in the initial response (exact number a
  PLAN-level decision, matching the frontend's own "matches Slack's
  short dropdown" framing); a group with more matches than its cap
  exposes a `hasMore`/count signal the frontend's "see more" action
  (its REQ-22) can act on — the exact "see more" fetch mechanism (a
  `type`+`offset` parameter on the same endpoint vs. a distinct
  expand-one-group endpoint) is a PLAN-level decision, not pinned here.
- **(Amended 2026-08-10) "Recent places" is served by the same new
  entity-search endpoint, triggered by an empty query, not a distinct
  endpoint** — REQ-25/REQ-26 above. This keeps "what does the search bar
  show" as one request shape (present vs. absent query), matching the
  frontend's own REQ-19/REQ-20 framing of "recent places" as what
  appears in the same dropdown before a query exists, not a separate
  screen/surface.

## Acceptance criteria

- [ ] A user can search by free-text query and receive matching
      messages only from `PEER_DIRECT`/`PEER_GROUP` conversations they
      are a current participant of.
- [ ] A user's search never returns a message from a conversation they
      are not currently a participant of, including one they've left,
      been removed from, or that is archived/soft-deleted — verified
      with an integration test asserting exactly this (the SPEC's
      flagged main implementation risk).
- [ ] Supplying a `conversationId` the caller is not a current
      participant of (or that doesn't exist, or is `SUPPORT`, or is
      archived/deleted) yields the same empty-for-that-conversation
      result as "no matches," never a distinguishable
      not-found/forbidden signal.
- [ ] `senderId`, `conversationId`, and `dateFrom`/`dateTo` filters each
      further narrow results correctly, individually and combined.
- [ ] Results are returned chronologically, paginated via cursor, never
      as an unbounded list.
- [ ] A blank/missing/unusable `q` is rejected; `dateFrom` later than
      `dateTo` is rejected.
- [ ] A search from a caller whose resolved locale is Portuguese matches
      against Portuguese word forms (e.g. plural/conjugated forms of a
      query term) that a literal substring match would miss; the same
      holds for an English-resolved caller against English word forms.
- [ ] A missing/unparseable `Accept-Language` header defaults to English
      matching rather than rejecting the request.
- [ ] No client-supplied parameter can force a search to run against the
      other language's index than the one implied by the caller's
      actual `Accept-Language`-resolved locale.
- [ ] A `STAFF_ADMIN`/`MEMBER_ADMIN` caller with no participant row on a
      given conversation gets zero results from that conversation via
      this endpoint, confirming no oversight bypass applies here.
- [ ] **(Amended 2026-08-10)** A unified entity-search query returns
      matching people, groups, Support row, and RAG conversations, each
      scoped by that result kind's own access rule (REQ-19 through
      REQ-22), never including an entity the caller is not authorized to
      see or reach.
- [ ] **(Amended 2026-08-10)** A group-name query matches both a group
      the caller already participates in and a `PUBLIC`/
      `REQUEST_TO_JOIN` group the caller isn't in yet but is eligible
      to discover, exactly matching `listDiscoverableGroups`'s existing
      visibility set; a `PRIVATE` group the caller isn't in is never
      matched (REQ-19).
- [ ] **(Amended 2026-08-10)** A person-name query never matches a user
      the caller is not `ChatEligibilityService`-eligible to message —
      confirmed with a fixture where a matching-by-name user shares no
      tenant/staff-capability anchor with the caller and is asserted
      absent from results (REQ-20).
- [ ] **(Amended 2026-08-10)** A `STAFF_ADMIN`/`MEMBER_ADMIN` caller's
      entity-search results for people/groups/RAG conversations are
      scoped exactly as any other caller's — no oversight bypass for any
      new result kind, confirming REQ-18.
- [ ] **(Amended 2026-08-10)** An entity-search query for an entity the
      caller cannot see/reach (an ineligible person, a `PRIVATE` group
      they're not in, another user's RAG conversation) returns that
      result omitted, not a distinguishable "found but denied" signal.
- [ ] **(Amended 2026-08-10)** A blank entity-search query returns a
      "recent places" list scoped by the same access rule, excluding a
      conversation the caller has since left/been removed from/that's
      archived or soft-deleted.

## Out of scope

- `SUPPORT` conversation content search of any kind — a possible future
  feature with its own access-model analysis, not defined here.
  **(Amended 2026-08-10): still out of scope, unchanged — this refers
  to searching inside Support's messages. Finding/opening the Support
  row itself via unified entity search (REQ-21) is a different thing
  and is now in scope.**
- Any `STAFF_ADMIN`/`MEMBER_ADMIN` oversight/look-in bypass for this
  endpoint — every caller, staff included, is scoped strictly to their
  own current participant conversations. **(Amended 2026-08-10):
  unchanged, and explicitly extended to every entity-search result kind
  — see REQ-18.**
- Relevance-ranked (`ts_rank`) result ordering — v1 is chronological
  only; relevance ordering is a valid future increment, not specified
  here. **(Amended 2026-08-10): entity-search results are also
  unranked beyond whatever natural ordering PLAN chooses (e.g. name
  order, recency) — no relevance scoring for entity matches either.**
- Any language other than English and Portuguese (`pt-BR`) for full-text
  matching.
- Per-message language auto-detection (e.g. a Portuguese message inside
  an otherwise-English conversation matched against the Portuguese
  index specifically) — matching is entirely driven by the *searching
  caller's* resolved locale, never by detecting the language a given
  message was actually written in.
- Editing or deleting message content (unaffected by, and not enabled
  by, this SPEC) — messages remain immutable, consistent with
  `internal-team-chat`.
- Indexing/searching message attachments, if any exist elsewhere in the
  system — this SPEC covers `chat_messages.content` (text) only.
- A UI for this capability — specified separately in
  `knowly-app/specify/features/chat-message-search/SPEC.md` and
  `knowly-app/specify/features/chat-unified-ui/SPEC.md`.
- Real-time/live-updating search results as new messages arrive while a
  search is open — each search request reflects a point-in-time
  snapshot. **(Amended 2026-08-10): applies to entity search and
  "recent places" too.**
- Search analytics/metrics (e.g. "most searched terms") — not requested
  and not defined here.
- **(Amended 2026-08-10) Fuzzy/typo-tolerant matching for entity names**
  (people/group/RAG titles) — matching is exact-substring/prefix
  semantics at PLAN's discretion, not fuzzy/edit-distance matching; not
  requested by either frontend document.
- **(Amended 2026-08-10) A persisted, dedicated "last interaction"
  timestamp for "recent places" ordering** — REQ-26 deliberately reuses
  the existing, coarser id-descending recency proxy rather than adding
  new schema for this feature specifically; see REQ-26's own rationale.
- **(Amended 2026-08-10) Cross-tenant entity search of any kind** — a
  person/group/RAG conversation in a tenant the caller has no
  membership/eligibility in (and, per REQ-18, no staff oversight bypass
  either) is never matched, mirroring the message-search endpoint's own
  existing tenant boundary.
- **(Amended 2026-08-10) A person or group result the caller can find
  by name but cannot actually reach** — explicitly rejected by REQ-20's
  resolution (Tier 3 item B): unlike some directory-search products,
  this endpoint never returns a "dead-end" match deferred to a
  click-time failure; eligibility is enforced at match time, not at
  open time.

## Tier 3 — status

**REQ-1 through REQ-15 (original document): none outstanding — resolved
and shipped, unchanged by this amendment.**

**Amendment (2026-08-10): both items now resolved by the product owner.
Neither was inferred from what was already decided — both were asked
directly, per `DECISIONS.md`'s Tier 3 rules, since each is a genuine
product/access-control decision (who is discoverable via a text query),
not a technical one. REQ-19/REQ-20 above reflect the resolved answers
directly; the conditional phrasing this document originally carried has
been removed.**

- **Item A — group-name match scope (REQ-19). Resolved: include
  joinable-but-not-yet-joined groups.** The product owner confirmed
  unified search matches `PUBLIC`/`REQUEST_TO_JOIN` groups the caller
  isn't a participant of yet, in addition to groups already
  participated in — mirroring the existing `listDiscoverableGroups`
  endpoint's visibility rule exactly, not a narrower participant-only
  scope. REQ-19 above states this directly.
- **Item B — person match scope (REQ-20). Resolved: eligibility-scoped,
  no dead-end results.** The product owner confirmed unified search
  only matches people the caller is `ChatEligibilityService`-eligible
  to message — the same rule already enforced for the candidate list —
  so a found person is always someone the caller can actually open a
  conversation with; there is no "find someone by name but can't
  message them" outcome anywhere in this feature. REQ-20 above states
  this directly.

**This document, in full, is now ready for read-back and sign-off** —
every requirement (REQ-1 through REQ-26), acceptance criterion, and
"Out of scope" line is final, with no remaining open Tier 3 item. Ready
to be approved alongside the two frontend amendments
(`knowly-app/specify/features/chat-message-search/SPEC.md`'s "Amended
(2026-08-10)" and `knowly-app/specify/features/chat-unified-ui/SPEC.md`'s
"Amended (5)") before PLAN.md work starts for any of the three.
