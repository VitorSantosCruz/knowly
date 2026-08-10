# SPEC — chat-message-search (backend)

> The what and the why. No technical implementation details.

## Context and motivation

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
  product owner explicitly declined to grant.
- Must respect `chat-group-membership-management`'s archive (REQ-43) and
  soft-delete (REQ-49) semantics: a conversation the caller is no longer
  a current participant of — because they left, were removed, or the
  conversation was archived/soft-deleted — must never surface in their
  search results, exactly like `chat-group-membership-management`'s
  REQ-46 already establishes for its own read paths.
- Reuses `chat_messages`/`chat_participants` unchanged in shape apart
  from the new indexed column(s) this SPEC adds (see Non-functional
  requirements) — no new entity beyond the search index itself.

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

## Requirements (EARS/GEARS)

### Search scope and access control

- **REQ-1 [Ubiquitous]** The system shall provide a message-content
  search capability limited to `PEER_DIRECT` and `PEER_GROUP`
  conversations; `SUPPORT` conversations are never included in search
  results or search indexing scope for this capability.
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
  never trusted from client input").
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

## Out of scope

- `SUPPORT` conversation content search of any kind — a possible future
  feature with its own access-model analysis, not defined here.
- Any `STAFF_ADMIN`/`MEMBER_ADMIN` oversight/look-in bypass for this
  endpoint — every caller, staff included, is scoped strictly to their
  own current participant conversations.
- Relevance-ranked (`ts_rank`) result ordering — v1 is chronological
  only; relevance ordering is a valid future increment, not specified
  here.
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
  `knowly-app/specify/features/chat-message-search/SPEC.md`.
- Real-time/live-updating search results as new messages arrive while a
  search is open — each search request reflects a point-in-time
  snapshot.
- Search analytics/metrics (e.g. "most searched terms") — not requested
  and not defined here.

## Tier 3 — status

**None outstanding.** All three items flagged by the prior
investigation (`PROJECT_STATUS.md` item 16) — Support-channel scope,
oversight-bypass scope, and locale/language handling — are resolved
above with the product owner's explicit decisions. This SPEC is ready
for `PLAN.md` once read back and approved.
