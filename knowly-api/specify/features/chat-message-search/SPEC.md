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
  not being reopened here. (Amended 2026-08-10, role-based scoping
  completion): this "no oversight bypass" statement is refined, not
  reversed, by the role-based scoping ruleset in "Amended (2026-08-10,
  role-based scoping) — REQ-5 completion" below — an *admin* role
  (`GlobalRole.STAFF_ADMIN` or `MembershipRole.MEMBER_ADMIN`) does get an
  unrestricted-within-scope search grant, but that grant is an explicit,
  bounded, product-confirmed role privilege (never cross-tenant for a
  tenant admin), not the kind of unbounded staff "look-in"/oversight
  bypass this line was written to rule out. See that section for the
  full ruleset and the reasoning for why this is not the same thing.**
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
   exists in this SPEC at all. **(Amended 2026-08-10, role-based scoping
   completion): this item is refined by the role-based scoping ruleset
   below — see that section's own framing note for why an explicit,
   bounded admin-role search grant is not the same thing as the
   unbounded oversight bypass this item rules out.**
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
- **(Amended 2026-08-10, staff-chat parity fix):** As a staff user with
  no active tenant selected, I want to search the content of my own
  staff-to-staff/support-adjacent 1:1 and group conversations exactly
  like a tenant-scoped user can search theirs, instead of always getting
  an empty result just because I have no active tenant.
- **(Amended 2026-08-10, role-based scoping completion):** As a staff
  admin, I want to search across all conversations platform-wide,
  without the public/request-to-join/private-membership restriction that
  applies to a non-admin, since an admin's oversight need is broader by
  design. As a tenant member who holds the admin role in my active
  tenant, I want the same unrestricted search within my own tenant only
  — never another tenant's conversations. As a non-admin (staff or
  tenant member), I want my search to additionally reach public and
  request-to-join groups I haven't joined yet, not just groups I'm
  already a participant of, mirroring the same discoverability I already
  get browsing groups.

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
  a cached or client-supplied list of conversation ids. **(Amended
  2026-08-10, role-based scoping completion): this baseline rule is the
  non-admin rule and the admin exception both build on — see "Amended
  (2026-08-10, role-based scoping) — REQ-5 completion" below for the
  complete, final ruleset (REQ-5e through REQ-5j), which supersedes
  REQ-5c/REQ-5d in full.**
- **REQ-3 [Unwanted Behavior]** If a caller supplies a `conversationId`
  filter for a conversation they are not currently a participant of (or
  that does not exist, or is `SUPPORT`, or is archived/soft-deleted),
  then the system shall exclude that conversation from the results
  without revealing whether it exists — behaving identically to "the
  caller has zero matching messages in that conversation," not
  distinguishing "no access" from "no matches" or "no such
  conversation." **(Amended 2026-08-10, role-based scoping completion):
  "not currently a participant of" here is read as "not currently
  in-scope per REQ-5e through REQ-5j" — for a non-admin caller this
  includes a `PUBLIC`/`REQUEST_TO_JOIN` group they are eligible for but
  haven't joined, per REQ-5h/REQ-5i; it still excludes a `PRIVATE` group
  they're not a member of.**
- **REQ-4 [Unwanted Behavior]** If a conversation the caller was
  previously a participant of has since been archived (per
  `chat-group-membership-management` REQ-43) or soft-deleted (REQ-49),
  or the caller has left/been removed from it, then the system shall
  exclude that conversation's messages from that caller's search
  results from that point forward, regardless of any earlier
  participation.
- **REQ-5 [Ubiquitous] (Amended 2026-08-10, role-based scoping
  completion — see "Amended (2026-08-10, role-based scoping) — REQ-5
  completion" below for the final, complete ruleset).** The system's
  access-control rule for this capability is role-based: a caller
  holding an admin role (`GlobalRole.STAFF_ADMIN` for staff, or
  `MembershipRole.MEMBER_ADMIN` for a tenant member, each within their
  own bounded scope) searches without the participancy/visibility
  restriction REQ-2 otherwise applies, per REQ-5e/REQ-5g below; every
  other caller remains subject to REQ-2 as refined by REQ-5f/REQ-5h/
  REQ-5i/REQ-5j below. **This supersedes both the original unqualified
  "no bypass for anyone" wording and the earlier partial
  no-active-tenant-only correction (REQ-5c/REQ-5d) — see the superseding
  section for the full four-case ruleset and why it is not the same
  thing as the "oversight bypass" this document elsewhere rules out.**

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
>
> **(Amended 2026-08-10, role-based scoping completion): the role-based
> admin/non-admin ruleset below (REQ-5e through REQ-5j) applies only to
> message-content search (REQ-1 through REQ-15), not to this entity-search
> section.** Entity search's own REQ-17/REQ-18 ("no oversight bypass,
> re-derived per request, no exception for `STAFF_ADMIN`/`MEMBER_ADMIN`")
> are unchanged and remain in force exactly as originally written — the
> product owner's role-based clarification was scoped to message-content
> search only ("busca" in the messages that prompted it referred to the
> content-search gap under discussion, REQ-5's own subject). If entity
> search is later meant to carry the same admin exception, that is a
> separate, future product decision, not implied by this amendment.

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
  server-side, before the result is ever serialized. (Amended 2026-08-10,
  role-based scoping completion): for message search specifically, this
  same "before any other criterion" posture applies to the role check
  itself (REQ-5e through REQ-5j) — the caller's admin/non-admin status
  and, for a non-admin, the group-visibility predicate, are both
  resolved from the caller's current, re-derived role/membership state
  before `q`/`senderId`/`conversationId`/date filters are applied, never
  as a post-filter.**
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
  boundary. **(Amended 2026-08-10, bug fix): this line's premise — that
  `chat_participants` membership alone implies tenant boundary — was
  already corrected during implementation for the *has-an-active-tenant*
  case (see PLAN.md's "AppSec correction": an explicit `tenant_id`
  predicate is required in addition to the participant join). The
  amendment below corrects the remaining *no-active-tenant* case, which
  this line never actually addressed one way or the other. (Amended
  2026-08-10, role-based scoping completion): the full, current version
  of this correction is the role-based ruleset in "Amended (2026-08-10,
  role-based scoping) — REQ-5 completion" below, which supersedes the
  earlier no-active-tenant-only draft (REQ-5c/REQ-5d) in full — a
  `MEMBER_ADMIN`'s "no restriction" grant is still bounded to their own
  active tenant's `tenant_id`, never cross-tenant, and a `STAFF_ADMIN`'s
  "no restriction" grant is the sole case in this SPEC where the
  `tenant_id` predicate is intentionally not applied at all (by design,
  not by omission).**
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
      **(Amended 2026-08-10, role-based scoping completion): superseded
      for message-content search only by the role-based criteria below
      — a `STAFF_ADMIN`/tenant-active `MEMBER_ADMIN` caller now *does*
      get results from a conversation they hold no participant row on,
      per REQ-5e/REQ-5g; this original bullet's guarantee remains fully
      in force for entity search (REQ-18) and for a non-admin caller's
      message search, unchanged.**
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
- [ ] **(Amended 2026-08-10, bug fix — superseded)** A `STAFF`/
      `STAFF_ADMIN` caller with **no active tenant selected**, searching
      message content, gets results from a `PEER_DIRECT`/`PEER_GROUP`
      conversation they are a current direct participant of (a
      staff-to-staff or staff-support 1:1/group conversation with no
      tenant anchor at all), instead of an unconditional empty result.
      **Now generalized by REQ-5e/REQ-5f/REQ-5h below (see "Amended
      (2026-08-10, role-based scoping) — REQ-5 completion"): this
      behavior is a specific instance of REQ-5f/REQ-5h's non-admin
      participancy rule and REQ-5e's staff-admin unrestricted rule, both
      of which apply with or without an active tenant.**
- [ ] **(Amended 2026-08-10, bug fix — superseded)** The same
      no-active-tenant staff caller's search still returns **zero**
      results from any tenant-scoped conversation they are not an
      active-tenant member of, even if they hold some other, unrelated
      participant row on a conversation belonging to a different tenant
      — confirming the corrected REQ-5 narrows scope to direct
      participancy, it does not widen it into a cross-tenant scan.
      **Now generalized by REQ-5j below: a non-admin's scope is never
      cross-tenant regardless of active-tenant state; a `MEMBER_ADMIN`'s
      unrestricted grant (REQ-5g) is likewise never cross-tenant; only
      `STAFF_ADMIN` (REQ-5e) is intentionally cross-tenant, by explicit
      product-owner design.**
- [ ] **(Amended 2026-08-10, role-based scoping completion)** A
      `GlobalRole.STAFF_ADMIN` caller's message search returns matches
      from any `PEER_DIRECT`/`PEER_GROUP` conversation platform-wide,
      including one they hold no `chat_participants` row on and
      regardless of tenant or group visibility (REQ-5e).
- [ ] **(Amended 2026-08-10, role-based scoping completion)** A
      `GlobalRole.STAFF` (non-admin) caller with no active tenant gets
      results only from: 1:1 conversations they participate in; `PUBLIC`
      groups; `REQUEST_TO_JOIN` groups; and `PRIVATE` groups they are a
      member of — never a `PRIVATE` group they haven't joined (REQ-5f).
- [ ] **(Amended 2026-08-10, role-based scoping completion)** A tenant
      member holding `MembershipRole.MEMBER_ADMIN` in their active
      tenant gets unrestricted message-search results within that
      tenant (no participancy/visibility filter), but zero results from
      any conversation belonging to a different tenant, even one they
      hold a stale participant row on (REQ-5g/REQ-5j).
- [ ] **(Amended 2026-08-10, role-based scoping completion)** A tenant
      member holding `MembershipRole.MEMBER` (non-admin) in their active
      tenant gets results, within that tenant only, from: 1:1
      conversations they participate in; `PUBLIC` groups; `REQUEST_TO_JOIN`
      groups; and `PRIVATE` groups they are a member of — never a
      `PRIVATE` group in-tenant they haven't joined, and never any
      conversation in a different tenant (REQ-5h/REQ-5j).

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
  — see REQ-18. (Amended 2026-08-10, bug fix): the REQ-5 no-active-tenant
  correction below is not an oversight bypass — it does not let a staff
  caller see any conversation they are not a direct, current participant
  of; it only removes the previously-unconditional "no active tenant ⇒
  empty result" behavior for conversations the caller already,
  legitimately, directly participates in. (Amended 2026-08-10,
  role-based scoping completion): this bullet is now superseded, for
  message-content search only, by the role-based ruleset below — a
  `STAFF_ADMIN`/active-tenant `MEMBER_ADMIN` caller's unrestricted-search
  grant (REQ-5e/REQ-5g) is an explicit, bounded, product-confirmed role
  privilege, not the unbounded "look-in on conversations they aren't a
  participant of, regardless of role" bypass this bullet originally, and
  still, rules out for every *non-admin* caller and for entity search in
  full (REQ-18, unaffected).**
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
  existing tenant boundary. **(Amended 2026-08-10, role-based scoping
  completion): unaffected — entity search's REQ-18 carries no admin
  exception; see that section's framing note.**
- **(Amended 2026-08-10) A person or group result the caller can find
  by name but cannot actually reach** — explicitly rejected by REQ-20's
  resolution (Tier 3 item B): unlike some directory-search products,
  this endpoint never returns a "dead-end" match deferred to a
  click-time failure; eligibility is enforced at match time, not at
  open time.
- **(Amended 2026-08-10, bug fix — superseded, see role-based scoping
  section) A no-active-tenant staff caller searching across tenant-scoped
  conversations they have no active-tenant relationship with** — remains
  out of scope for a *non-admin* staff caller, per REQ-5f/REQ-5j; a
  `GlobalRole.STAFF_ADMIN` caller is the one explicit, product-confirmed
  exception to this line (REQ-5e), by design, not an accidental widening.
- **(Amended 2026-08-10, role-based scoping completion) Cross-tenant
  message search for a `MembershipRole.MEMBER_ADMIN` caller** — a
  tenant admin's unrestricted-search grant (REQ-5g) is bounded to their
  own active tenant; it never extends to another tenant's conversations,
  including one they hold a stale/unrelated participant row on (REQ-5j).
- **(Amended 2026-08-10, role-based scoping completion) An admin-role
  exception for entity search (REQ-16 through REQ-26)** — the
  admin/non-admin distinction introduced by this amendment applies only
  to message-content search; entity search's REQ-17/REQ-18 are
  unaffected and carry no admin exception of any kind. If a future
  product decision extends the same admin exception to entity search,
  that requires its own explicit confirmation, not an inferred extension
  of this amendment.

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

**Amendment (2026-08-10, role-based scoping completion): resolved by
the product owner, in full, across a rapid sequence of messages (see the
superseding section below for the full transcript-derived ruleset).**
This item replaces the earlier, incomplete REQ-5c/REQ-5d draft (which
only covered the staff-no-active-tenant case) with the complete
four-case admin/non-admin ruleset the product owner actually intended.
Not inferred — each of the four cases (staff admin, staff non-admin,
tenant admin, tenant non-admin) was either stated directly or is the
product owner's own explicit generalization ("é admin? busca em
qualquer lugar sem restrição. não é admin, pode buscar 1:1, grupos
públicos ou grupos que ele faz parte."), which is treated as
authoritative product intent, not an AI-inferred extrapolation.

**This document, in full, is now ready for read-back and sign-off** —
every requirement (REQ-1 through REQ-26, plus REQ-5e through REQ-5j
below), acceptance criterion, and "Out of scope" line is final, with no
remaining open Tier 3 item. Ready to be approved alongside the two
frontend amendments (`knowly-app/specify/features/chat-message-search/SPEC.md`'s
"Amended (2026-08-10)" and `knowly-app/specify/features/chat-unified-ui/SPEC.md`'s
"Amended (5)") before PLAN.md work starts for any of the three.

## Amended (2026-08-10, role-based scoping) — REQ-5 completion

> **This section fully replaces "Amended (2026-08-10, bug fix) — REQ-5
> no-active-tenant scoping correction" (the REQ-5c/REQ-5d draft below
> the horizontal rule at the end of this document is kept only as a
> superseded historical record — do not implement REQ-5c/REQ-5d as
> written; implement REQ-5e through REQ-5j below instead).** The earlier
> draft was a partial fix, addressing only "what happens to a staff
> caller with no active tenant." The product owner's follow-up messages
> (2026-08-10, delivered as a rapid sequence, reproduced and folded in
> here in full) made clear the real rule is role-based and applies
> regardless of active-tenant state:
>
> 1. "Antes de seguir, staff pode fazer buscas em grupos públicos, com
>    requisição para entrar e privados que ele faz parte" — staff can
>    search in public groups, request-to-join groups, and private groups
>    they participate in.
> 2. "Staff admin pode buscar em qualquer lugar sem restrição" — a staff
>    admin can search anywhere, no restriction (cross-tenant, no
>    participancy/visibility limit).
> 3. "member admin pode buscar em qualquer lugar do seu tenant sem
>    restrição" — a tenant member holding the admin role can search
>    anywhere within their own active tenant, no restriction, but never
>    across other tenants.
> 4. "é admin? busca em qualquer lugar sem restrição. não é admin, pode
>    buscar 1:1, grupos públicos ou grupos que ele faz parte." — the
>    product owner's own generalization: admin (staff-admin or
>    tenant-admin, each within their own bounded scope) ⇒ unrestricted
>    search within that scope; non-admin ⇒ restricted to 1:1
>    conversations participated in, `PUBLIC` groups, `REQUEST_TO_JOIN`
>    groups, and `PRIVATE` groups the caller is a member of.
>
> **Existing role vocabulary reused, nothing invented.** "Admin" maps to
> two already-existing enums in this codebase, never a new role concept:
> `GlobalRole.STAFF_ADMIN` (`br.com.conectabyte.knowly.tenancy.GlobalRole`)
> for staff, and `MembershipRole.MEMBER_ADMIN`
> (`br.com.conectabyte.knowly.tenancy.MembershipRole`) for a tenant
> member's role within their active tenant. `ChatGroupVisibility`'s
> three existing values (`PUBLIC`, `REQUEST_TO_JOIN`, `PRIVATE` — see
> `knowly-app/src/app/core/chat.model.ts` and the backend equivalent
> enum) are reused unchanged, identical to REQ-19's existing group
> discoverability set.

**Corrected REQ-5 (EARS/GEARS) — final, replaces REQ-5c/REQ-5d in
full.**

- **REQ-5e [State-Driven]** While the caller is a staff user holding
  `GlobalRole.STAFF_ADMIN`, when a message search request is submitted,
  the system shall return matches from any `PEER_DIRECT`/`PEER_GROUP`
  conversation platform-wide, with no `chat_participants` join
  restriction, no `tenant_id` predicate, and no `ChatGroupVisibility`
  restriction — i.e. fully unrestricted within this capability's
  existing `PEER_DIRECT`/`PEER_GROUP` scope (REQ-1). This is the sole
  case in this SPEC where search is intentionally cross-tenant.
- **REQ-5f [State-Driven]** While the caller is a staff user holding
  `GlobalRole.STAFF` and not `STAFF_ADMIN`, when a message search
  request is submitted, the system shall scope results to: (a) 1:1
  (`PEER_DIRECT`) conversations for which the caller currently holds a
  non-removed, non-soft-deleted `chat_participants` row; (b) `PEER_GROUP`
  conversations with `ChatGroupVisibility.PUBLIC`; (c) `PEER_GROUP`
  conversations with `ChatGroupVisibility.REQUEST_TO_JOIN`; and (d)
  `PEER_GROUP` conversations with `ChatGroupVisibility.PRIVATE` for
  which the caller currently holds a non-removed, non-soft-deleted
  `chat_participants` row. This applies identically whether or not the
  caller has an active tenant selected — active-tenant state is not a
  factor in this rule at all, since staff scope was never tenant-anchored
  to begin with.
- **REQ-5g [State-Driven]** While the caller is a tenant member holding
  `MembershipRole.MEMBER_ADMIN` in their currently active tenant, when a
  message search request is submitted, the system shall return matches
  from any `PEER_DIRECT`/`PEER_GROUP` conversation whose `tenant_id`
  equals the caller's active tenant, with no `chat_participants` join
  restriction and no `ChatGroupVisibility` restriction within that
  tenant — but shall never include a conversation belonging to any other
  tenant, regardless of any participant row the caller may separately
  hold there.
- **REQ-5h [State-Driven]** While the caller is a tenant member holding
  `MembershipRole.MEMBER` (not `MEMBER_ADMIN`) in their currently active
  tenant, when a message search request is submitted, the system shall
  scope results, within that active tenant only, to: (a) 1:1
  (`PEER_DIRECT`) conversations for which the caller currently holds a
  non-removed, non-soft-deleted `chat_participants` row; (b) `PEER_GROUP`
  conversations with `ChatGroupVisibility.PUBLIC`; (c) `PEER_GROUP`
  conversations with `ChatGroupVisibility.REQUEST_TO_JOIN`; and (d)
  `PEER_GROUP` conversations with `ChatGroupVisibility.PRIVATE` for
  which the caller currently holds a non-removed, non-soft-deleted
  `chat_participants` row — identical shape to REQ-5f, scoped
  additionally to the active tenant's `tenant_id`.
- **REQ-5i [Ubiquitous]** For every non-admin case (REQ-5f, REQ-5h), a
  `PRIVATE` group the caller is not currently a non-removed,
  non-soft-deleted participant of is never matched, regardless of query
  content — mirroring REQ-19's existing group-discoverability rule for
  `PRIVATE` groups exactly (visible-by-search is never broader than
  visible-by-browse for `PRIVATE` groups).
- **REQ-5j [Unwanted Behavior]** If a caller's admin-role grant is
  tenant-scoped (`MembershipRole.MEMBER_ADMIN`, REQ-5g) and a candidate
  conversation belongs to a tenant other than the caller's currently
  active tenant, then the system shall exclude that conversation from
  the results even if the caller holds a non-removed `chat_participants`
  row on it (e.g. a stale membership from a tenant they've since left,
  or a role held in a different tenant they are not currently active
  in) — a tenant-admin's unrestricted grant never becomes a cross-tenant
  scan. This preserves, for `MEMBER_ADMIN`, the exact same anti-scan
  guarantee the original REQ-5/REQ-5d language established, narrowed
  here to apply specifically to the admin-unrestricted case rather than
  the participancy case REQ-2 already covers for non-admins.

**Precedence and interaction with REQ-2/REQ-3/REQ-19.** REQ-5e through
REQ-5j are the complete access-control rule for message-content search;
REQ-2's "current participant" rule is the *non-admin* baseline these
requirements refine (REQ-5f/REQ-5h), not a separate, additional
restriction layered on top of the admin cases (REQ-5e/REQ-5g bypass
REQ-2's participancy join entirely, by design). REQ-3's non-revealing
`conversationId`-filter behavior is unaffected in shape — it now
evaluates "is this conversation in-scope" against whichever of REQ-5e
through REQ-5h applies to the caller's actual role/tenant state, rather
than against REQ-2 alone. The `PUBLIC`/`REQUEST_TO_JOIN`/`PRIVATE`
visibility categories used here are identical to, and must stay
consistent with, REQ-19's existing group-discoverability rule for
unified entity search — this is the same underlying `ChatGroupVisibility`
concept applied to a second capability (content search, not just
name-matching), not a new, parallel visibility taxonomy.

**What this does not change.** Entity search (REQ-16 through REQ-26) is
explicitly untouched by this section — see that section's own framing
note and the corresponding "Out of scope" bullet. Support-content search
remains fully out of scope regardless of caller role (REQ-1's
`PEER_DIRECT`/`PEER_GROUP`-only boundary is unaffected by anything in
this section). Locale resolution (REQ-13–REQ-15), pagination/ordering
(REQ-10), and filter behavior (REQ-7–REQ-9, REQ-11, REQ-12) are all
unaffected — this section only changes *which conversations* are
in-scope before those filters apply, per the Non-functional
requirements' "before any other criterion" posture.

**Acceptance criteria for this section** are listed above in
"Acceptance criteria" (the "Amended 2026-08-10, role-based scoping
completion" bullets) and are not repeated here.

**Status.** Final — confirmed by the product owner 2026-08-10 across the
message sequence quoted above, not an open Tier 3 question. Supersedes
"Amended (2026-08-10, bug fix) — REQ-5 no-active-tenant scoping
correction" below in full. Ready for PLAN.md/TASKS.md work by
`software-architect` against `ChatMessageSearchService`/
`ChatMessageSearchRepository` — the native-query predicate structure
needs an admin/non-admin branch (REQ-5e/REQ-5g bypass the
`chat_participants`/`ChatGroupVisibility` predicates entirely; REQ-5f/
REQ-5h apply them; REQ-5g/REQ-5j still apply the `tenant_id` predicate
for `MEMBER_ADMIN`, REQ-5e intentionally omits it for `STAFF_ADMIN`),
replacing the no-active-tenant-only branch the earlier draft described.

---

## Superseded — Amended (2026-08-10, bug fix) — REQ-5 no-active-tenant scoping correction

> **Historical record only. Superseded in full by "Amended (2026-08-10,
> role-based scoping) — REQ-5 completion" above — do not implement
> REQ-5c/REQ-5d below.** Kept for traceability of how the role-based
> ruleset was arrived at (this was the first, partial pass at the same
> underlying gap) rather than deleted outright, consistent with this
> project's incident history around silently editing out prior decisions
> (`DECISIONS.md`) — the correction here is an explicit, visible
> supersession, not a silent rewrite.

> **Bug-fix amendment, not a new open design question.** This corrects a
> functional gap in the already-implemented REQ-5/AppSec fail-closed
> behavior (`ChatMessageSearchService.search()`,
> `knowly-api/src/main/java/br/com/conectabyte/knowly/chat/ChatMessageSearchService.java`,
> ~lines 68-72), found during a routine review of shipped behavior, not
> requested as new scope. **The product owner (repo owner,
> vcruz@meudroz.com) has explicitly confirmed the corrected behavior on
> 2026-08-10**: "staff chat deve ter os mesmos poderes que o chat dos
> tenants" — staff must be able to search the content of their own
> conversations exactly like a tenant-scoped user can, with no
> functional gap. This is a final decision; it does not require further
> confirmation before PLAN work proceeds.

**What was wrong.** The shipped implementation of REQ-5 (see PLAN.md's
"AppSec correction" section) makes `ChatMessageSearchService.search()`
fail closed to an **unconditional empty result** whenever
`TenantContext.getActiveTenantId()` is empty — regardless of caller.
This was the right call for stopping a cross-tenant scan (its intended
purpose), but it was over-broad: it also silently blocks a `STAFF`/
`STAFF_ADMIN` caller from searching their own `PEER_DIRECT`/`PEER_GROUP`
conversations that exist entirely outside any tenant (staff-to-staff or
staff-support chat, scoped by direct `chat_participants` membership, not
by tenant membership) — a conversation kind that has always been
reachable and readable by that same caller through every other chat
read path (`listConversations`, message history, etc.), just not
searchable. That is a functional gap, not a security property: REQ-5's
actual intent (per this document's "Tier 3 — resolved" item 2 and
PLAN.md's own AppSec rationale) was to prevent a caller with no active
tenant from being handed an unfiltered, tenant-agnostic scan across
*every* tenant's conversations — not to block them from searching
conversations they already, legitimately, directly participate in.

**Corrected REQ-5 (EARS/GEARS) — superseded, see above.**

- **REQ-5c [State-Driven] (superseded by REQ-5f/REQ-5h above)** While the
  caller has no active tenant selected
  (`TenantContext.getActiveTenantId()` is empty), when a message search
  request is submitted, the system shall scope results to
  `PEER_DIRECT`/`PEER_GROUP` conversations for which the caller
  currently holds a non-removed, non-soft-deleted `chat_participants`
  row (the same REQ-2 join), evaluated **without** any `tenant_id`
  predicate for this state specifically — rather than returning an
  unconditional empty result. This applies identically to every caller,
  staff or otherwise; it is not a staff-only carve-out, it is what
  "no active tenant" now means for every caller.
- **REQ-5d [Unwanted Behavior] (superseded by REQ-5j above)** If, while
  the caller has no active tenant selected, a candidate conversation is
  tenant-scoped (i.e. its `tenant_id` is not `NULL`) and the caller has
  no active-tenant membership relationship to that tenant, then the
  system shall exclude that conversation's messages from the results,
  exactly as before this amendment — REQ-5c's removal of the `tenant_id`
  predicate applies only to the participancy check itself; it does not,
  and must never, cause a tenant-scoped conversation to become visible
  to a caller with no active membership in that tenant.

**Status.** Superseded 2026-08-10 by "Amended (2026-08-10, role-based
scoping) — REQ-5 completion" above, which is the authoritative,
implementable version of this correction.
