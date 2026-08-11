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
> **Status (2026-08-10, context-boundary correction — see the final
> section of this document): a live Playwright test caught a real design
> error in the just-shipped "role-based scoping" ruleset (REQ-5e–REQ-5j
> as originally drafted) — a `STAFF_ADMIN` viewing chat in staff scope
> (no active tenant) could find a tenant member's message via search.
> The product owner confirmed this is a genuine bug, not a
> misunderstanding, and corrected the model: current viewing context
> (staff scope, or one specific active tenant) always bounds what search
> can find, for every role, with no exception — an admin role only ever
> removes the participancy/visibility restriction *within* that same
> context, it never expands the context itself. "Amended (2026-08-10,
> role-based scoping) — REQ-5 completion" below is superseded in full by
> "Amended (2026-08-10, context-boundary correction) — REQ-5 completion
> (final)" at the end of this document — implement that section, not the
> superseded one. REQ-1 through REQ-4, REQ-6 through REQ-15, and REQ-16
> through REQ-26 (entity search) are unaffected and remain final — see
> that section's own confirmation that entity search was independently
> checked against the same bug class and found already correct.**

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
  completion, later superseded — see below): this "no oversight bypass"
  statement is refined, not reversed, by the role-based scoping ruleset
  in "Amended (2026-08-10, context-boundary correction) — REQ-5
  completion (final)" below — an *admin* role (`GlobalRole.STAFF_ADMIN`
  or `MembershipRole.MEMBER_ADMIN`) does get an
  unrestricted-*within-the-caller's-current-context* search grant, but
  that grant never expands which context (staff scope vs. a specific
  tenant) is searched, and is therefore not the kind of unbounded staff
  "look-in"/oversight bypass this line was written to rule out. See that
  section for the full ruleset and the reasoning for why this is not the
  same thing.**
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
   completion, later superseded): this item is refined by the role-based
   scoping ruleset below — see "Amended (2026-08-10, context-boundary
   correction) — REQ-5 completion (final)" for the current, correct
   version of why an explicit, bounded, context-respecting admin-role
   search grant is not the same thing as the unbounded oversight bypass
   this item rules out.**
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
- **(Amended 2026-08-10, role-based scoping completion — corrected
  2026-08-10, context-boundary correction, see below):** As a staff
  admin viewing chat in staff scope, I want to search across every
  staff-scope conversation without the public/request-to-join/private-
  membership restriction that applies to a non-admin, since an admin's
  oversight need within that scope is broader by design — **but I never
  want that to reach into any tenant's conversations while I'm not
  currently inside that tenant.** As a tenant member who holds the admin
  role in my active tenant, I want the same unrestricted search within
  my own active tenant only — never another tenant's conversations, and
  never staff-scope conversations. As a non-admin (staff or tenant
  member), I want my search to additionally reach public and
  request-to-join groups I haven't joined yet, not just groups I'm
  already a participant of, mirroring the same discoverability I already
  get browsing groups — still bounded to my current context.

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
  2026-08-10, role-based scoping completion; corrected 2026-08-10,
  context-boundary correction): this baseline rule is the non-admin rule
  and the admin exception both build on — see "Amended (2026-08-10,
  context-boundary correction) — REQ-5 completion (final)" below for the
  complete, current, final ruleset (REQ-5e through REQ-5j), which
  supersedes both REQ-5c/REQ-5d and the first (incorrect,
  context-crossing) REQ-5e–REQ-5j draft in full.**
- **REQ-3 [Unwanted Behavior]** If a caller supplies a `conversationId`
  filter for a conversation they are not currently a participant of (or
  that does not exist, or is `SUPPORT`, or is archived/soft-deleted),
  then the system shall exclude that conversation from the results
  without revealing whether it exists — behaving identically to "the
  caller has zero matching messages in that conversation," not
  distinguishing "no access" from "no matches" or "no such
  conversation." **(Amended 2026-08-10, context-boundary correction):
  "not currently a participant of" here is read as "not currently
  in-scope per the final REQ-5e through REQ-5j ruleset" — for a
  non-admin caller this includes a `PUBLIC`/`REQUEST_TO_JOIN` group they
  are eligible for but haven't joined, per REQ-5f/REQ-5h/REQ-5i, always
  within the caller's current context (REQ-5j); it still excludes a
  `PRIVATE` group they're not a member of, and it excludes any
  conversation belonging to a different context entirely (a different
  tenant, or staff scope while active in a tenant, or vice versa), for
  every role including admins.**
- **REQ-4 [Unwanted Behavior]** If a conversation the caller was
  previously a participant of has since been archived (per
  `chat-group-membership-management` REQ-43) or soft-deleted (REQ-49),
  or the caller has left/been removed from it, then the system shall
  exclude that conversation's messages from that caller's search
  results from that point forward, regardless of any earlier
  participation.
- **REQ-5 [Ubiquitous] (Amended 2026-08-10, role-based scoping
  completion; corrected 2026-08-10, context-boundary correction — see
  "Amended (2026-08-10, context-boundary correction) — REQ-5 completion
  (final)" below for the current, complete ruleset).** The system's
  access-control rule for this capability is role-based *within the
  caller's current viewing context*: a caller holding an admin role
  (`GlobalRole.STAFF_ADMIN` while in staff scope, or
  `MembershipRole.MEMBER_ADMIN` while active in their own tenant)
  searches, within that same context only, without the
  participancy/visibility restriction REQ-2 otherwise applies, per
  REQ-5e/REQ-5g below; every other caller remains subject to REQ-2 as
  refined by REQ-5f/REQ-5h/REQ-5i below. No role, admin or not, ever
  reaches outside the caller's current context (REQ-5j) — this is the
  master invariant the earlier, incorrect draft violated. **This
  supersedes the original unqualified "no bypass for anyone" wording,
  the earlier partial no-active-tenant-only correction (REQ-5c/REQ-5d),
  and the first role-based draft's cross-context premise (which
  incorrectly let a `STAFF_ADMIN` in staff scope match tenant content) —
  see the final superseding section for the complete ruleset.**

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
>
> **(Amended 2026-08-10, context-boundary correction — verified, no fix
> needed here): this section was independently re-checked against the
> same context-crossing bug class that hit message search (REQ-5e–REQ-5j)
> and found already correct, not merely "carries no admin exception."**
> `ChatEntitySearchService` never reads `isStaff()`/`isStaffAdmin()` at
> all (confirmed directly in source, not just via the historical
> `PROJECT_STATUS.md` note this was checked against), and every
> underlying primitive it delegates to —
> `ChatConversationService#listConversations`,
> `#searchDiscoverableGroups` (REQ-19), `ChatEligibilityService` (REQ-20),
> `SupportTicketService#findOwnOrClaimableChannel` (REQ-21), and RAG's
> own tenant-owned `conversations` query (REQ-22) — already resolves
> `TenantContext#getActiveTenantId()` itself and fails closed (empty
> branch, not a wider one) whenever it is absent, exactly the
> context-respecting shape REQ-5j now requires for message search too.
> Because entity search never had a role-based branch to begin with, it
> never had a *role* crossing context — and its existing "no active
> tenant → fail closed for tenant-anchored results" posture already
> matches the corrected context-first model. **No REQ-16–REQ-26 change is
> needed; this note exists so a future reader doesn't have to re-derive
> the same conclusion from scratch.**

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
  role-based scoping completion; corrected 2026-08-10, context-boundary
  correction): for message search specifically, this same "before any
  other criterion" posture applies to the role check **and the current-
  context check** themselves (REQ-5e through REQ-5j) — the caller's
  current context (staff scope vs. a specific active tenant),
  admin/non-admin status, and, for a non-admin, the group-visibility
  predicate, are all resolved from the caller's current, re-derived
  state before `q`/`senderId`/`conversationId`/date filters are applied,
  never as a post-filter.**
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
  2026-08-10, role-based scoping completion; corrected 2026-08-10,
  context-boundary correction): the full, current version of this
  correction is the ruleset in "Amended (2026-08-10, context-boundary
  correction) — REQ-5 completion (final)" below, which supersedes both
  the earlier no-active-tenant-only draft (REQ-5c/REQ-5d) and the first,
  incorrect role-based draft (which wrongly let `STAFF_ADMIN` cross into
  tenant content) in full — a `MEMBER_ADMIN`'s "no restriction" grant is
  bounded to their own active tenant's `tenant_id` and never reaches
  staff scope or another tenant; a `STAFF_ADMIN`'s "no restriction" grant
  is bounded to staff-scope conversations only (`tenant_id IS NULL`) and
  never reaches any tenant's `tenant_id`, including while the same
  `STAFF_ADMIN` is separately active in a tenant.**
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
      **(Amended 2026-08-10, role-based scoping completion; corrected
      2026-08-10, context-boundary correction): superseded for
      message-content search only by the role-based, context-bounded
      criteria below — a `STAFF_ADMIN`/active-tenant `MEMBER_ADMIN`
      caller now *does* get results from a conversation they hold no
      participant row on, **but only when that conversation belongs to
      the caller's own current context** (staff scope for `STAFF_ADMIN`,
      their own active tenant for `MEMBER_ADMIN`), per the final
      REQ-5e/REQ-5g below; this original bullet's guarantee remains fully
      in force for any conversation outside the caller's current context,
      for entity search (REQ-18), and for a non-admin caller's message
      search, unchanged.**
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
      **Now generalized by the final REQ-5e/REQ-5f/REQ-5h below (see
      "Amended (2026-08-10, context-boundary correction) — REQ-5
      completion (final)"): this behavior is a specific instance of
      REQ-5f/REQ-5h's non-admin participancy rule and REQ-5e's
      staff-admin unrestricted-within-staff-scope rule, both of which
      apply with or without an active tenant, and both of which are
      bounded to staff scope only.**
- [ ] **(Amended 2026-08-10, bug fix — superseded)** The same
      no-active-tenant staff caller's search still returns **zero**
      results from any tenant-scoped conversation they are not an
      active-tenant member of, even if they hold some other, unrelated
      participant row on a conversation belonging to a different tenant
      — confirming the corrected REQ-5 narrows scope to direct
      participancy, it does not widen it into a cross-tenant scan.
      **Now generalized by REQ-5j below: a non-admin's scope is never
      cross-context regardless of active-tenant state; a `MEMBER_ADMIN`'s
      unrestricted grant (REQ-5g) is likewise never cross-tenant; and — as
      of the context-boundary correction — `STAFF_ADMIN` (REQ-5e) is
      likewise never cross-context: its unrestricted grant reaches every
      staff-scope conversation, and only staff-scope conversations,
      never a tenant's. The earlier draft's "STAFF_ADMIN is the one
      intentional cross-tenant exception" premise is retracted — see the
      final section below for the confirmed bug and correction.**
- [ ] **(Amended 2026-08-10, role-based scoping completion — retracted
      2026-08-10, context-boundary correction: this exact bullet
      described the bug a live Playwright test caught.)** ~~A
      `GlobalRole.STAFF_ADMIN` caller's message search returns matches
      from any `PEER_DIRECT`/`PEER_GROUP` conversation platform-wide,
      including one they hold no `chat_participants` row on and
      regardless of tenant or group visibility.~~ **Replaced by:** A
      `GlobalRole.STAFF_ADMIN` caller viewing chat in **staff scope**
      gets unrestricted message-search results from every staff-scope
      `PEER_DIRECT`/`PEER_GROUP` conversation (no participancy/visibility
      filter), but **zero** results from any tenant's conversation, even
      one they hold a `chat_participants` row on, while in staff scope
      (REQ-5e/REQ-5j).
- [ ] **(Amended 2026-08-10, role-based scoping completion)** A
      `GlobalRole.STAFF` (non-admin) caller with no active tenant gets
      results only from: 1:1 conversations they participate in; `PUBLIC`
      groups; `REQUEST_TO_JOIN` groups; and `PRIVATE` groups they are a
      member of — never a `PRIVATE` group they haven't joined, and never
      any tenant's conversation (REQ-5f/REQ-5j).
- [ ] **(Amended 2026-08-10, role-based scoping completion)** A tenant
      member holding `MembershipRole.MEMBER_ADMIN` in their active
      tenant gets unrestricted message-search results within that
      tenant (no participancy/visibility filter), but zero results from
      any conversation belonging to a different tenant or to staff
      scope, even one they hold a stale participant row on (REQ-5g/
      REQ-5j).
- [ ] **(Amended 2026-08-10, role-based scoping completion)** A tenant
      member holding `MembershipRole.MEMBER` (non-admin) in their active
      tenant gets results, within that tenant only, from: 1:1
      conversations they participate in; `PUBLIC` groups; `REQUEST_TO_JOIN`
      groups; and `PRIVATE` groups they are a member of — never a
      `PRIVATE` group in-tenant they haven't joined, and never any
      conversation in a different tenant or in staff scope (REQ-5h/
      REQ-5j).
- [ ] **(Amended 2026-08-10, context-boundary correction — new)** A
      `STAFF_ADMIN` who is currently active in a tenant (i.e. has
      switched into that tenant, so is not in staff scope) gets **zero**
      staff-scope results from message search while active in that
      tenant — the master invariant (REQ-5j) runs both directions, not
      just tenant-content-leaking-into-staff-scope.
- [ ] **(Amended 2026-08-10, context-boundary correction — new,
      regression test for the exact reported bug)** A `STAFF_ADMIN`
      viewing chat in staff scope (no active tenant) who searches for a
      word known to appear only in a tenant member's message (e.g.
      "Member Three"'s message inside a tenant conversation) gets
      **zero** results for that query — the exact scenario the live
      Playwright test caught, now a permanent regression fixture.

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
  role-based scoping completion; corrected 2026-08-10, context-boundary
  correction): this bullet is now superseded, for message-content search
  only, by the final role-based, context-bounded ruleset below — a
  `STAFF_ADMIN`/active-tenant `MEMBER_ADMIN` caller's unrestricted-search
  grant (REQ-5e/REQ-5g) is an explicit, bounded, product-confirmed role
  privilege *strictly within the caller's own current context*, not the
  unbounded "look-in on conversations they aren't a participant of,
  regardless of role or context, anywhere" bypass this bullet originally,
  and still, rules out for every *non-admin* caller, for entity search in
  full (REQ-18, unaffected), and — critically, per the context-boundary
  correction — for any conversation outside the admin's own current
  context, no exception.**
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
  exception; see that section's framing note. (Amended 2026-08-10,
  context-boundary correction): re-verified, still unaffected — see
  entity search's own "verified, no fix needed here" note.**
- **(Amended 2026-08-10) A person or group result the caller can find
  by name but cannot actually reach** — explicitly rejected by REQ-20's
  resolution (Tier 3 item B): unlike some directory-search products,
  this endpoint never returns a "dead-end" match deferred to a
  click-time failure; eligibility is enforced at match time, not at
  open time.
- **(Amended 2026-08-10, bug fix — superseded, see context-boundary
  correction section) A no-active-tenant staff caller searching across
  tenant-scoped conversations they have no active-tenant relationship
  with** — remains out of scope for every staff caller, admin included:
  the earlier role-based draft's claim that `GlobalRole.STAFF_ADMIN` was
  "the one explicit, product-confirmed exception to this line" is
  **retracted** by the context-boundary correction below — a
  `STAFF_ADMIN` in staff scope never sees tenant-scoped conversations
  either; its unrestricted grant (REQ-5e) reaches only staff-scope
  conversations.
- **(Amended 2026-08-10, role-based scoping completion) Cross-tenant
  message search for a `MembershipRole.MEMBER_ADMIN` caller** — a
  tenant admin's unrestricted-search grant (REQ-5g) is bounded to their
  own active tenant; it never extends to another tenant's conversations
  or to staff scope, including one they hold a stale/unrelated
  participant row on (REQ-5j).
- **(Amended 2026-08-10, role-based scoping completion) An admin-role
  exception for entity search (REQ-16 through REQ-26)** — the
  admin/non-admin distinction introduced by this amendment applies only
  to message-content search; entity search's REQ-17/REQ-18 are
  unaffected and carry no admin exception of any kind. If a future
  product decision extends the same admin exception to entity search,
  that requires its own explicit confirmation, not an inferred extension
  of this amendment.
- **(Amended 2026-08-10, context-boundary correction — new) Any
  cross-context search result for any role, including any admin role**
  — a `STAFF_ADMIN`'s or `MEMBER_ADMIN`'s unrestricted-within-context
  grant (REQ-5e/REQ-5g) is now explicitly, permanently out of scope for
  reaching a *different* context (staff scope while active in a tenant,
  a different tenant, or tenant content while in staff scope) — this is
  the corrected, symmetric restatement of the master invariant (REQ-5j)
  the reported bug violated, and it applies uniformly to messages,
  people, groups, and RAG/knowledge-base conversations per the product
  owner's own generalization ("Isso vale para tudo, usuários, grupos,
  conversas com a base, etc").

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
now-superseded section below for the full transcript-derived ruleset,
which had a real bug — see the context-boundary correction that
follows it, which is the current authoritative version).**

**Amendment (2026-08-10, context-boundary correction): the role-based
scoping ruleset above was shipped, then caught by a live Playwright test
with a genuine bug — a `STAFF_ADMIN` in staff scope could find a tenant
member's message. The product owner confirmed this is a real design
error and corrected the model to be context-first, not role-first:
current viewing context (staff scope, or one active tenant) always
bounds results for every role; an admin role only removes the
participancy/visibility restriction *within* that same context. See the
final section of this document for the complete, corrected ruleset.**

**This document, in full, is now ready for read-back and sign-off** —
every requirement (REQ-1 through REQ-26, plus the final REQ-5e through
REQ-5j below), acceptance criterion, and "Out of scope" line is final,
with no remaining open Tier 3 item. Ready to be approved alongside the
two frontend amendments (`knowly-app/specify/features/chat-message-search/SPEC.md`'s
"Amended (2026-08-10)" and `knowly-app/specify/features/chat-unified-ui/SPEC.md`'s
"Amended (5)") before PLAN.md/TASKS.md work resumes for the corrected
REQ-5e–REQ-5j.

## Superseded — Amended (2026-08-10, role-based scoping) — REQ-5 completion (v1 — DO NOT IMPLEMENT, contains the reported bug)

> **Historical record only, kept per this project's "visible
> supersession, not silent deletion" convention (see `DECISIONS.md`'s
> incident record) — do not implement REQ-5e through REQ-5j as written
> in this section.** This version's premise — that `STAFF_ADMIN`'s
> "search anywhere, no restriction" grant meant literally *any*
> conversation platform-wide, cross-tenant, regardless of the caller's
> current viewing context — was confirmed by the product owner on
> 2026-08-10 as a genuine design error after a live Playwright test
> caught it in practice (a `STAFF_ADMIN` viewing chat in staff scope,
> with no active tenant selected, found a tenant member's message via
> search). It is fully superseded by "Amended (2026-08-10,
> context-boundary correction) — REQ-5 completion (final)" below, which
> is the authoritative, implementable ruleset. Every acceptance-criteria
> and out-of-scope bullet elsewhere in this document that depended on
> this section's premise has its own inline correction note pointing
> here and to the final section.

> The product owner's follow-up messages (2026-08-10, delivered as a
> rapid sequence, reproduced and folded in here in full) made clear the
> real rule is role-based and applies regardless of active-tenant state:
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
> **In hindsight, message 2 ("busca em qualquer lugar sem restrição" for
> staff admin) was read literally as "platform-wide, cross-tenant" —
> which is what shipped, and what the Playwright test caught as wrong.
> The product owner's 2026-08-10 correction clarifies "qualquer lugar"
> meant "anywhere within staff's own scope," not "anywhere on the
> platform including inside tenants" — see the final section below for
> the corrected reading.**

**(v1, superseded) REQ-5e through REQ-5j as originally drafted:**

- **REQ-5e (v1, superseded) [State-Driven]** While the caller is a staff
  user holding `GlobalRole.STAFF_ADMIN`, when a message search request
  is submitted, the system shall return matches from any
  `PEER_DIRECT`/`PEER_GROUP` conversation platform-wide, with no
  `chat_participants` join restriction, no `tenant_id` predicate, and no
  `ChatGroupVisibility` restriction. **Superseded: this is the exact bug
  — see REQ-5e (final) below, which bounds this grant to staff-scope
  conversations only.**
- **REQ-5f (v1, superseded) [State-Driven]** (unchanged in substance by
  the correction — see REQ-5f (final) below, restated with explicit
  staff-scope framing.)
- **REQ-5g (v1, superseded) [State-Driven]** (unchanged in substance by
  the correction — confirmed correct; see REQ-5g (final) below,
  restated unchanged.)
- **REQ-5h (v1, superseded) [State-Driven]** (unchanged in substance by
  the correction — see REQ-5h (final) below.)
- **REQ-5i (v1, superseded) [Ubiquitous]** (unchanged in substance by the
  correction — see REQ-5i (final) below.)
- **REQ-5j (v1, superseded) [Unwanted Behavior]** Only addressed the
  `MEMBER_ADMIN` cross-tenant case, silent on the `STAFF_ADMIN`
  cross-context case — this omission is exactly what let the bug ship.
  **Superseded: REQ-5j (final) below restates this as the master,
  symmetric invariant covering every role and every context, in both
  directions.**

**Status.** Superseded 2026-08-10 (context-boundary correction) by the
final section below, following a genuine bug confirmed by the product
owner via a live Playwright test.

---

## Superseded — Amended (2026-08-10, bug fix) — REQ-5 no-active-tenant scoping correction

> **Historical record only. Superseded in full by "Amended (2026-08-10,
> context-boundary correction) — REQ-5 completion (final)" at the end of
> this document — do not implement REQ-5c/REQ-5d below.** Kept for
> traceability of how the corrected ruleset was arrived at (this was the
> first, partial pass at the same underlying gap) rather than deleted
> outright, consistent with this project's incident history around
> silently editing out prior decisions (`DECISIONS.md`) — the correction
> here is an explicit, visible supersession, not a silent rewrite.

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

- **REQ-5c [State-Driven] (superseded)** While the caller has no active
  tenant selected (`TenantContext.getActiveTenantId()` is empty), when a
  message search request is submitted, the system shall scope results to
  `PEER_DIRECT`/`PEER_GROUP` conversations for which the caller
  currently holds a non-removed, non-soft-deleted `chat_participants`
  row (the same REQ-2 join), evaluated **without** any `tenant_id`
  predicate for this state specifically — rather than returning an
  unconditional empty result. This applies identically to every caller,
  staff or otherwise; it is not a staff-only carve-out, it is what
  "no active tenant" now means for every caller.
- **REQ-5d [Unwanted Behavior] (superseded)** If, while the caller has no
  active tenant selected, a candidate conversation is tenant-scoped (i.e.
  its `tenant_id` is not `NULL`) and the caller has no active-tenant
  membership relationship to that tenant, then the system shall exclude
  that conversation's messages from the results, exactly as before this
  amendment — REQ-5c's removal of the `tenant_id` predicate applies only
  to the participancy check itself; it does not, and must never, cause a
  tenant-scoped conversation to become visible to a caller with no
  active membership in that tenant.

**Status.** Superseded 2026-08-10 by "Amended (2026-08-10,
context-boundary correction) — REQ-5 completion (final)" at the end of
this document, which is the authoritative, implementable version of this
correction.

---

## Amended (2026-08-10, context-boundary correction) — REQ-5 completion (final)

> **This section fully replaces "Amended (2026-08-10, role-based
> scoping) — REQ-5 completion" (the v1 draft above, kept as a superseded
> historical record — do not implement it) and remains, as before, the
> section that supersedes the earlier "Amended (2026-08-10, bug fix) —
> REQ-5 no-active-tenant scoping correction" (REQ-5c/REQ-5d) in full.
> This is the third and current pass at the same underlying access-
> control rule, and — unlike the previous two — was prompted by a
> genuine, confirmed bug caught by a live Playwright test, not a
> proactive review or a forward product request.**

**What was wrong, confirmed by the product owner as a genuine design
error.** While logged in as a `STAFF_ADMIN` viewing chat in **staff
scope** (no active tenant selected — the left sidebar showed only
Support/staff colleagues/a private staff group, no tenant content), the
message search bar returned a message from "Member Three" — a tenant
member's message from inside a tenant. The v1 draft's REQ-5e
("`STAFF_ADMIN` → search unrestricted, platform-wide, across ALL
conversations, regardless of tenant or participancy") was the direct
cause: it read the product owner's "staff admin pode buscar em qualquer
lugar sem restrição" as license to cross into tenant content, which was
never the intent.

**The corrected, simpler mental model (product owner's exact words,
2026-08-10):** "estando na visão do staff mensagens dentro de tenant são
encontradas, para encontrar mensagens de um tenant o usuário precisa
estar dentro dele, independente do perfil do usuário. staff_admin na
visão de staff vê mensagens do staff e nada mais, se ele entra em um
tenant ele vê as mensagens daquele tenant e nada mais. Isso vale para
tudo, usuários, grupos, conversas com a base, etc."

- **Current context = active tenant (if one is selected) OR staff scope
  (if none is selected). Never both, never a union.**
- Within the current context, an admin role (`STAFF_ADMIN` in staff
  scope, or `MEMBER_ADMIN` in their active tenant) removes only the
  participancy/visibility restriction (sees every conversation in that
  context, not just ones they participate in) — it does **not** expand
  the context itself.
- Within the current context, a non-admin (`STAFF` in staff scope, or
  `MEMBER` in their active tenant) is restricted to: 1:1 they
  participate in, `PUBLIC` groups, `REQUEST_TO_JOIN` groups, and
  `PRIVATE` groups they're a member of — all within that same context.
- **No role, however privileged, ever sees content from a different
  context** (a different tenant, or staff scope while active in a
  tenant, or tenant content while in staff scope) via this search. This
  applies uniformly to messages (this section), and — confirmed
  separately, see the entity-search section's own note — was already
  true for people/group/RAG entity search, which needed no fix.

**Corrected REQ-5 (EARS/GEARS) — final, replaces the v1 draft and
REQ-5c/REQ-5d in full.**

- **REQ-5e [State-Driven] (final)** While the caller is a staff user
  holding `GlobalRole.STAFF_ADMIN` and is currently in **staff scope**
  (`TenantContext.getActiveTenantId()` is empty), when a message search
  request is submitted, the system shall return matches from any
  `PEER_DIRECT`/`PEER_GROUP` conversation whose `tenant_id` is `NULL`
  (i.e. a staff-scope conversation), with no `chat_participants` join
  restriction and no `ChatGroupVisibility` restriction — but shall
  **never** include a conversation whose `tenant_id` is non-`NULL`
  (i.e. belongs to any tenant), regardless of any participant row the
  caller may separately hold there. This is the corrected reading of the
  product owner's "search anywhere, no restriction" — "anywhere" means
  "anywhere within staff scope," not "anywhere on the platform."
- **REQ-5f [State-Driven] (final, restated — unchanged in substance from
  the superseded v1 draft)** While the caller is a staff user holding
  `GlobalRole.STAFF` and not `STAFF_ADMIN`, and is currently in staff
  scope, when a message search request is submitted, the system shall
  scope results, to staff-scope (`tenant_id IS NULL`) conversations
  only, to: (a) 1:1 (`PEER_DIRECT`) conversations for which the caller
  currently holds a non-removed, non-soft-deleted `chat_participants`
  row; (b) `PEER_GROUP` conversations with `ChatGroupVisibility.PUBLIC`;
  (c) `PEER_GROUP` conversations with
  `ChatGroupVisibility.REQUEST_TO_JOIN`; and (d) `PEER_GROUP`
  conversations with `ChatGroupVisibility.PRIVATE` for which the caller
  currently holds a non-removed, non-soft-deleted `chat_participants`
  row. This applies whenever the caller has no active tenant selected —
  staff scope was never tenant-anchored to begin with.
- **REQ-5g [State-Driven] (final, restated unchanged — confirmed correct
  in the v1 draft, not affected by the bug)** While the caller is a
  tenant member holding `MembershipRole.MEMBER_ADMIN` in their currently
  active tenant, when a message search request is submitted, the system
  shall return matches from any `PEER_DIRECT`/`PEER_GROUP` conversation
  whose `tenant_id` equals the caller's active tenant, with no
  `chat_participants` join restriction and no `ChatGroupVisibility`
  restriction within that tenant — but shall never include a
  conversation belonging to any other tenant, or to staff scope,
  regardless of any participant row the caller may separately hold
  there.
- **REQ-5h [State-Driven] (final, restated — unchanged in substance from
  the superseded v1 draft)** While the caller is a tenant member holding
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
- **REQ-5i [Ubiquitous] (final, restated unchanged)** For every non-admin
  case (REQ-5f, REQ-5h), a `PRIVATE` group the caller is not currently a
  non-removed, non-soft-deleted participant of is never matched,
  regardless of query content — mirroring REQ-19's existing
  group-discoverability rule for `PRIVATE` groups exactly (visible-by-
  search is never broader than visible-by-browse for `PRIVATE` groups).
- **REQ-5j [Unwanted Behavior] (final — the master invariant; this is the
  corrected, symmetric rule the reported bug violated)** If a candidate
  conversation's context (staff scope, i.e. `tenant_id IS NULL`; or a
  specific tenant, i.e. `tenant_id` equals that tenant) does not match
  the caller's **currently active viewing context** (staff scope if no
  active tenant is selected; that specific tenant if one is), then the
  system shall exclude that conversation from message-search results —
  with **no exception for any role**, admin or not. Concretely:
  - A `STAFF_ADMIN`/`STAFF` in staff scope never matches a conversation
    with a non-`NULL` `tenant_id`, regardless of participant row.
  - A `MEMBER_ADMIN`/`MEMBER` active in tenant X never matches a
    conversation whose `tenant_id` is `NULL` (staff scope) or belongs to
    a different tenant Y, regardless of participant row.
  - This holds even for a caller with a genuine, non-stale
    `chat_participants` row on the out-of-context conversation (e.g. a
    `STAFF_ADMIN` who also happens to be a direct participant in a
    tenant conversation) — REQ-5e/REQ-5g's admin bypass is scoped to
    *this request's current context only*, it is never a standing,
    context-independent grant.

**Precedence and interaction with REQ-2/REQ-3/REQ-19.** REQ-5e through
REQ-5j are the complete access-control rule for message-content search;
REQ-2's "current participant" rule is the *non-admin* baseline these
requirements refine (REQ-5f/REQ-5h), not a separate, additional
restriction layered on top of the admin cases (REQ-5e/REQ-5g bypass
REQ-2's participancy join entirely, by design, but never REQ-5j's
context predicate). REQ-3's non-revealing `conversationId`-filter
behavior is unaffected in shape — it now evaluates "is this conversation
in-scope" against whichever of REQ-5e through REQ-5h applies to the
caller's actual role/context state, rather than against REQ-2 alone. The
`PUBLIC`/`REQUEST_TO_JOIN`/`PRIVATE` visibility categories used here are
identical to, and must stay consistent with, REQ-19's existing
group-discoverability rule for unified entity search — this is the same
underlying `ChatGroupVisibility` concept applied to a second capability
(content search, not just name-matching), not a new, parallel visibility
taxonomy.

**What this does not change.** Entity search (REQ-16 through REQ-26) was
independently re-checked against this exact bug class and confirmed
already correct — see that section's own "verified, no fix needed here"
note; it is not touched by this correction. Support-content search
remains fully out of scope regardless of caller role or context (REQ-1's
`PEER_DIRECT`/`PEER_GROUP`-only boundary is unaffected by anything in
this section). Locale resolution (REQ-13–REQ-15), pagination/ordering
(REQ-10), and filter behavior (REQ-7–REQ-9, REQ-11, REQ-12) are all
unaffected — this section only changes *which conversations* are
in-scope before those filters apply, per the Non-functional
requirements' "before any other criterion" posture.

**Acceptance criteria for this section** are listed above in
"Acceptance criteria" (the "Amended 2026-08-10, context-boundary
correction" bullets, plus the corrected/retracted "role-based scoping
completion" bullets they annotate) and are not repeated here.

**Status.** Final — confirmed by the product owner 2026-08-10 following
a live Playwright test's bug report, superseding both the v1 role-based
draft above and the earlier REQ-5c/REQ-5d no-active-tenant draft in
full. Ready for PLAN.md/TASKS.md rework by `software-architect` against
`ChatMessageSearchService`/`ChatMessageSearchRepository` — the
already-planned admin/non-admin branch (per the v1 PLAN.md, now itself
needing a corresponding correction pass) must additionally gate
REQ-5e/REQ-5g's bypass on the caller's *current context* (`tenant_id IS
NULL` for `STAFF_ADMIN`, `tenant_id = activeTenantId` for
`MEMBER_ADMIN`), not omit the `tenant_id` predicate outright as the v1
draft's REQ-5e did. **An AppSec re-review of the corrected PLAN is
required before TASKS.md/implementation resumes**, given this is
exactly the class of access-control defect AppSec review exists to
catch, and the first pass missed it.
