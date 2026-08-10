# PLAN — chat-message-search (backend)

> The how. Translates the SPEC into concrete technical decisions.
> References SPEC.md. Reuses `chat_messages`/`chat_participants`/
> `ChatConversation` unchanged in shape apart from the new indexed
> columns this SPEC adds — no new entity beyond the search index
> itself, per the SPEC's "Relationship to existing SPECs" section.

## Architectural decisions

- **New endpoint `GET /api/chat/messages/search` on the existing
  `ChatController`, backed by a new `ChatMessageSearchService`, not a
  new `ChatMessageSearchController`.** Every other message-shaped
  read/write in this codebase already lives on `ChatController` +
  `ChatConversationService`; a search endpoint is a *read* over the
  same `chat_messages` table with an additional predicate, not a
  different resource family. It gets its own service class (rather
  than another method bolted onto the already-large
  `ChatConversationService`) because its authorization shape is
  materially different (REQ-2/REQ-5: join-through-`chat_participants`
  across *all* of the caller's conversations, never a single
  `conversationId` path-variable check like every other
  `ChatConversationService` method) — mixing that shape into the
  existing service would make the existing per-conversation methods
  harder to audit for the exact bypass-precedent mistake REQ-5 forbids.
- **Two generated STORED `tsvector` columns
  (`content_tsv_pt`/`content_tsv_en`) with two GIN indexes, added via
  migration, no entity-level projection needed beyond a native query.**
  Per the SPEC's non-functional requirements verbatim — Hibernate never
  needs to read or write these columns from Java (they're
  `GENERATED ALWAYS AS ... STORED`, populated by Postgres itself on
  insert), so `ChatMessage` gets **no new field** for them. This keeps
  the JPA entity mapping unchanged, avoiding any risk of Hibernate
  trying to manage a generated column's value.
- **The search query itself is a native, non-JPQL `@Query` on a new
  `ChatMessageSearchRepository` (not added to `ChatMessageRepository`),
  because it needs `websearch_to_tsquery()` and a `chat_participants`
  EXISTS-subquery `@@`-matched against a column JPQL/HQL cannot express
  portably.** Precedent: `ArticleRepository`/`ConversationRepository`/
  `MessageRepository`/`TenantRepository` all already mix native
  `@Query(nativeQuery = true)` methods into an otherwise-JPQL repository
  for exactly this reason (raw SQL functions, date-bucketing, etc. that
  Spring Data JPQL can't express) — this follows that same precedent. A
  separate repository interface (rather than adding to
  `ChatMessageRepository`) because this query returns a different
  projection shape (message + resolved sender fields for the DTO, see
  below) and has a fundamentally different composition strategy
  (dynamic optional-filter SQL) than `ChatMessageRepository`'s existing
  fixed-shape cursor queries — keeping them separate avoids one
  repository interface mixing two unrelated query-composition styles.
- **The `chat_participants` join-through-scoping predicate is applied
  as the *first* condition in the native query's `WHERE` clause,
  structurally inseparable from the rest of the predicate (same SQL
  statement, not a post-filter step in Java), per REQ-2/REQ-5's
  "before any other filter, never layered on afterward" requirement.**
  Concretely: `EXISTS (SELECT 1 FROM chat_participants cp WHERE
  cp.conversation_id = cc.id AND cp.user_id = :callerId AND
  cp.deleted_at IS NULL)` — this is evaluated for every candidate row
  regardless of which optional filters (`senderId`, `conversationId`,
  date range) the caller supplied, so there is no code path where the
  optional filters run without it. Three additional structural
  conditions travel with it in the same clause, per REQ-1/REQ-4 and the
  AppSec tenant-scoping correction below: `cc.tenant_id =
  :activeTenantId` (mandatory, resolved fail-closed before the query
  ever runs -- see "AppSec correction" below),
  `cc.kind IN ('PEER_DIRECT','PEER_GROUP')` (REQ-1, excludes SUPPORT),
  and `cc.archived_at IS NULL AND cc.deleted_at IS NULL` (REQ-4, a
  conversation's own soft-delete/archive state, independent of the
  caller's participant row's own `deleted_at`). `chat_messages.deleted_
  at IS NULL` is also required (messages are never edited/deleted today
  per SPEC, but the column and `@Filter` exist — matching it keeps this
  query consistent with every other message read path rather than
  assuming the invariant holds forever).
- **Locale resolution: a new, narrowly-scoped
  `ChatMessageSearchLocaleResolver` in the `chat` package, not a reuse
  of `DeletionConfirmationLocaleResolver` directly, but with byte-for-
  byte identical resolution logic.** `DeletionConfirmationLocaleResolver`
  lives in the `deletion` package and returns `DeletionLocale`
  (`EN`/`PT_BR`), a type with no meaning outside that feature's token-
  confirmation emails. Importing `deletion.DeletionLocale` into `chat`
  to select a `tsvector` column would create a cross-feature coupling
  where a future change to `DeletionLocale` (e.g. adding a third
  locale for email templates only) silently changes chat search
  behavior, or vice versa — the two features have no reason to share a
  release cycle. This is a **narrow, deliberate duplication** of ~15
  lines of `Locale.LanguageRange` parsing logic, not a new pattern: it
  preserves the SPEC's explicit instruction that this resolver "must
  NOT become a Spring-wide `LocaleResolver` bean" (still a plain
  `@Component`, no bean registration) while keeping each feature's
  locale type meaningful only within its own bounded context — the same
  reasoning this codebase already applies elsewhere (e.g. `ChatGroup
  Visibility` vs. other unrelated visibility enums are not unified just
  because they share values). Returns a new `ChatSearchLocale` enum
  (`PT`, `EN`) used only to pick which repository method/column to
  query.
- **AppSec correction (post-review): the native query must carry its own
  explicit `cc.tenant_id = :activeTenantId` predicate — REQ-2's
  `chat_participants` EXISTS clause alone is *not* tenant-scoped.**
  `TenantMembership` is unique on `(user_id, tenant_id)`, not one-per-
  user — a single user can be a member of, and a participant in
  conversations belonging to, more than one tenant. The original PLAN's
  premise ("`chat_participants` membership itself already implies the
  correct tenant boundary") was wrong: `cp.user_id = :callerId` matches
  that user's participant rows across *every* tenant they belong to,
  not just the currently-active one. Worse, this repository is a native
  `@Query(nativeQuery = true)` method (required for
  `websearch_to_tsquery`/`EXISTS`/dynamic optional filters — see above),
  and Hibernate's `@Filter`/`TenantFilter` mechanism — the ORM-layer
  enforcement this codebase otherwise relies on everywhere else — does
  **not** apply to native SQL, exactly the same documented gotcha
  `ChatConversationRepository#findByIdRespectingFilter`'s own Javadoc
  already calls out for primary-key lookups ("only HQL/JPQL queries and
  collection fetches respect [filters]"). So this query needed its own
  explicit tenant predicate in the same statement, not reliance on the
  session-level filter `TenantFilterAspect` enables for every other
  `@Transactional` service method. Fixed as follows:
  - `ChatMessageSearchService` resolves the active tenant id itself via
    `TenantContext.getActiveTenantId()` (the same source
    `TenantFilterAspect` reads) **before** invoking the repository, and
    fails closed if absent: an empty `Optional` short-circuits to an
    empty `ChatMessageSearchPageDto` with **no query executed at all**,
    rather than passing a sentinel through to SQL — a stronger
    guarantee than `TenantFilterAspect`'s own `NO_ACTIVE_TENANT_
    SENTINEL` pattern (which still runs a query, just one that matches
    nothing), and correct here because unlike a `-1` sentinel value,
    "run no query" cannot be defeated by a future edit that
    accidentally makes `tenant_id = -1` satisfiable (e.g. a bad
    migration, or a null-tenant peer conversation row — see below).
  - **Deliberately no staff-no-active-tenant bypass, unlike
    `TenantFilterAspect`'s own behavior for other endpoints.**
    `TenantFilterAspect` disables the tenant filter entirely when
    `tenantContext.isStaff() && activeTenantId.isEmpty()` (the
    oversight path). This endpoint does not read `isStaff()`/
    `isStaffAdmin()` at all — REQ-5 forbids *any* oversight bypass for
    search specifically, so "staff with no active tenant" is treated
    identically to any other caller with no active tenant: fail closed,
    zero results, not an unfiltered cross-tenant scan. This is the same
    reasoning REQ-5 already establishes for the `chat_participants`
    check; it now also governs the tenant predicate.
  - The resolved `activeTenantId` is bound as a plain query parameter
    (`:activeTenantId`) and appears in the **same** `WHERE`/`EXISTS`
    clause as the existing `chat_participants` EXISTS, `kind IN (...)`,
    and `archived_at`/`deleted_at IS NULL` conditions — one SQL
    statement, not a separate filter step, per the SPEC's own "before
    any other filter, never layered on afterward" posture, now applied
    to tenant scoping too, not just participant scoping.
  - `cc.tenant_id = :activeTenantId` additionally and correctly excludes
    any `chat_conversations` row with a `NULL` tenant_id (the
    staff-only peer-conversation case noted in `ChatConversation`'s own
    Javadoc — "a NULL value never matches the `tenant_id = :tenantId`
    filter condition") from every tenant-scoped caller's results, which
    is the desired behavior: a tenant-scoped user's search must never
    surface a staff-only conversation regardless of participant status,
    consistent with how the ORM-level filter already treats that same
    NULL case for every other tenant-scoped query in this codebase.
  - `ChatMessageSearchRepository`'s Javadoc explicitly documents this
    gotcha (mirroring `ChatConversationRepository#findByIdRespectingFilter`'s
    own precedent) so a future contributor adding a new filter to this
    query does not assume Hibernate's `@Filter` is doing tenant
    enforcement for them here the way it does for every JPQL-backed
    repository method elsewhere in this codebase.

- **`senderId`/`conversationId`/date-range filters are optional native-
  SQL predicates composed with `AND (:param IS NULL OR ...)`, not a
  Criteria/Specification API.** The existing native-query precedents in
  this codebase (`ArticleRepository`, `ConversationRepository`) use
  fixed hand-written SQL rather than `Specification`; introducing
  Criteria API here for four optional filters would be a heavier
  pattern than the codebase currently uses anywhere, for no material
  benefit over the standard "nullable bind parameter" idiom Postgres
  handles efficiently once each column has its own index.
- **Cursor pagination reuses `ChatCursor`'s existing id-only opaque-
  cursor shape (`base64(String.valueOf(id))`) and `DEFAULT_PAGE_SIZE`/
  `MAX_PAGE_SIZE`/`clampSize`, applied against `chat_messages.id`, not
  a new cursor type.** REQ-10 requires chronological ordering + cursor
  pagination, and `chat_messages.id` is already monotonic with
  `created_at` (`GenerationType.IDENTITY`, immutable messages, no
  reordering possible) — identical to how `ChatMessageRepository#find
  BeforeCursor`/`findAfterCursor` already page conversation history.
  Search adds `id < :cursor` (equivalently `>`, direction fixed to
  "descending / most-recent-first" for v1, see API contract) as one
  more `AND`-composed predicate in the same native query, not a
  separate pagination mechanism.
- **`q` parsing happens entirely inside Postgres
  (`websearch_to_tsquery('portuguese'|'english', :q)`), the service
  passes the raw string through** — REQ-6 explicitly wants
  `websearch_to_tsquery` semantics (quoted phrases, `-exclude`), which
  is exactly what that Postgres function implements; re-implementing
  any part of that parsing in Java would risk behavioral drift from
  what the function actually does. REQ-11 (blank/unusable `q` rejected)
  is checked in Java *before* the query runs (a `String.isBlank()`
  check is sufficient — `websearch_to_tsquery` on an all-stopword or
  punctuation-only string returns an empty tsquery that matches
  nothing, which would silently satisfy REQ-11's "reject" language only
  partially; explicit isBlank in Java plus documenting the "all-
  stopwords" edge case as accepted behavior, see Testing strategy).
- **Structured logging (actor id, `hasQuery`, filter-presence booleans,
  result count) at `INFO`, no `@AuditLog`.** SPEC's non-functional
  requirements explicitly say `@AuditLog` is not required (search is a
  read, not a state change) and explicitly forbid logging raw query
  text (user-authored, may contain sensitive content) — this follows
  the same shape as other high-volume, non-audited read logging
  already in the codebase (e.g. `listConversations`), just with an
  explicit reminder in the log statement's Javadoc not to add `q` to it
  later.

## Data schema

**Migration `V34__add_chat_message_search.sql`** (confirmed next-free
number via `ls knowly-api/src/main/resources/db/migration/` — highest
existing is `V33__add_icon_to_chat_conversations.sql`; the SPEC's
drafting-time guess of `V34` is still correct).

```sql
ALTER TABLE chat_messages
    ADD COLUMN content_tsv_pt tsvector
        GENERATED ALWAYS AS (to_tsvector('portuguese', content)) STORED,
    ADD COLUMN content_tsv_en tsvector
        GENERATED ALWAYS AS (to_tsvector('english', content)) STORED;

CREATE INDEX idx_chat_messages_content_tsv_pt
    ON chat_messages USING GIN (content_tsv_pt);

CREATE INDEX idx_chat_messages_content_tsv_en
    ON chat_messages USING GIN (content_tsv_en);
```

- No backfill step needed beyond the `ALTER TABLE`: `GENERATED ...
  STORED` columns are computed by Postgres for all existing rows as
  part of the `ALTER TABLE` itself (a one-time rewrite of
  `chat_messages`, acceptable at this corpus's documented size —
  "thousands–low tens of thousands of rows per tenant" per the SPEC's
  own sizing).
- No new table. No changes to `chat_participants` or
  `chat_conversations` schema — REQ-1/REQ-2/REQ-4's access rule is
  expressed entirely as query predicates against columns that already
  exist (`kind`, `archived_at`, `deleted_at` on `chat_conversations`;
  `deleted_at` on `chat_participants`).
- `ChatMessage.java` is **not modified** — the two new columns are
  Postgres-computed and never read/written through the entity; Hibernate
  has no mapping for them at all (kept entirely inside the native query
  in the new repository, referenced by raw column name).

## API contracts

| Method | Path | Request | Response | Status |
|---|---|---|---|---|
| `GET` | `/api/chat/messages/search` | Query params: `q` (required, non-blank), `senderId` (optional, `Long`), `conversationId` (optional, `Long`), `dateFrom`/`dateTo` (optional, ISO-8601 `Instant`), `cursor` (optional, opaque `ChatCursor` string), `size` (optional, int, clamped per `ChatCursor`). `Accept-Language` header (optional, resolves search locale per REQ-13–15). | `ChatMessageSearchPageDto` (see below) | `200` |

**Error cases** (all via `ChatExceptionHandler`, new handlers added to
the existing advice, same `ChatErrorResponseDto` shape):

| Condition | Exception | Status | Code |
|---|---|---|---|
| `q` blank/missing/whitespace-only | `ChatBlankSearchQueryException` (new) | `400` | `CHAT_SEARCH_QUERY_BLANK` |
| `dateFrom` after `dateTo` | `ChatInvalidSearchDateRangeException` (new) | `400` | `CHAT_SEARCH_INVALID_DATE_RANGE` |
| malformed `cursor` | `ChatInvalidCursorException` (existing, reused) | `400` | `CHAT_INVALID_CURSOR` |

No `404`/`403` for an inaccessible `conversationId` filter — per REQ-3,
that path returns a normal `200` with an empty/short page, not a
distinguishable error, so it is deliberately **not** listed as an error
case above; this is the one place this contract intentionally diverges
from `ChatConversationNotFoundException`/`ChatAccessDeniedException`'s
usual per-conversation-endpoint behavior, and that divergence is the
point of REQ-3.

**New DTOs** (`br.com.conectabyte.knowly.chat.dto`):

```java
public record ChatMessageSearchResultDto(
        Long id,
        Long conversationId,
        String conversationTitle,
        Long senderUserId,
        String senderNickname,
        String content,
        Instant createdAt) {}

public record ChatMessageSearchPageDto(
        List<ChatMessageSearchResultDto> results, String nextCursor) {}
```

`conversationId`/`conversationTitle` are included (unlike
`ChatMessageDto`, which is always scoped to one conversation via the
URL path) because search results span multiple conversations by
definition — the caller needs to know *which* conversation each hit
belongs to to act on it (REQ-6's user story: "remembers roughly what
they typed, but not who/which group").

## Dependencies

None. `websearch_to_tsquery`/`tsvector`/GIN indexing are native
PostgreSQL features already available via the existing `postgresql`
JDBC driver and Flyway setup — no new `pom.xml` dependency.

## Package/file structure

All new files in the existing `br.com.conectabyte.knowly.chat` package
(and `.dto`/`.exception` subpackages), following this feature area's
established layout — no new package:

- `ChatMessageSearchService.java` — orchestrates locale resolution,
  input validation (REQ-11/REQ-12), resolves `TenantContext.
  getActiveTenantId()` and fails closed (empty page, no query) when
  absent (see AppSec correction above), delegates the actual query to
  the repository, maps rows to `ChatMessageSearchResultDto`, builds the
  cursor-paginated response.
- `ChatMessageSearchRepository.java` — the native `@Query` search
  methods (one per resolved locale — `searchPt(...)`/`searchEn(...)` —
  rather than parameterizing the column name into SQL string
  concatenation, to keep the query text static/precompiled and avoid
  any temptation toward dynamic SQL assembly). Both methods take
  `activeTenantId` as a required bind parameter and carry a class-level
  Javadoc explicitly warning that, as native SQL, these methods are
  **not** covered by Hibernate's `TenantFilter`/`SoftDeleteFilter`
  `@Filter` mechanism — every scoping condition (tenant, participant,
  kind, archive/delete state) must be hand-written into the query text
  itself, mirroring `ChatConversationRepository#findByIdRespectingFilter`'s
  own precedent Javadoc for the same class of gap.
- `ChatMessageSearchLocaleResolver.java` — REQ-13/REQ-15 resolution
  logic, mirrors `DeletionConfirmationLocaleResolver` (see
  Architectural decisions).
- `ChatSearchLocale.java` — `enum { PT, EN }`, feature-local, not
  shared with `deletion.DeletionLocale`.
- `dto/ChatMessageSearchResultDto.java`, `dto/ChatMessageSearchPageDto.java`
  — new response DTOs (see API contracts).
- `dto/ChatMessageSearchRequestDto.java` — **not created**; the SPEC's
  filter set is simple enough (5 optional/required scalar params) to
  stay as `@RequestParam`s on the controller method directly, matching
  `listMessages`' existing `before`/`after`/`size` precedent, rather
  than introducing a request-binding DTO this feature area doesn't
  otherwise use for `GET` endpoints.
- `exception/ChatBlankSearchQueryException.java`,
  `exception/ChatInvalidSearchDateRangeException.java` — new,
  `RuntimeException` subclasses matching the existing exception
  classes' shape (see `ChatInvalidCursorException` for the Javadoc
  convention on *why* each is its own type).
- `ChatController.java` — one new method, `searchMessages`, added
  alongside the existing `/conversations/**` methods; no
  `@AuditLog` (see Architectural decisions).
- `ChatExceptionHandler.java` — two new `@ExceptionHandler` methods for
  the two new exception types, same file, same pattern as every
  existing handler in it.

## Testing strategy

**Unit** (`ChatMessageSearchServiceTest`, Mockito, no Testcontainers):

- REQ-11: blank/missing/whitespace-only `q` throws
  `ChatBlankSearchQueryException` before the repository is ever called
  (verify zero repository interaction).
- REQ-12: `dateFrom` after `dateTo` throws
  `ChatInvalidSearchDateRangeException` before the repository is ever
  called.
- REQ-13/REQ-15: `ChatMessageSearchLocaleResolverTest` — table-driven
  over `Accept-Language` values (`pt`, `pt-BR`, `pt-PT`, `en`, `en-US`,
  missing, empty, malformed/garbage, `fr` i.e. neither pt nor en) —
  identical case matrix to whatever
  `DeletionConfirmationLocaleResolverTest` already covers, confirming
  behavioral parity without sharing code.
- Cursor composition: given a resolved locale and a set of optional
  filters, the correct repository method is invoked with the correct
  bind parameters (`null` passed through for unset optional filters,
  not a sentinel).

**Integration** (`ChatMessageSearchControllerIntegrationTest`,
Testcontainers Postgres, real Flyway migrations — this SPEC's own
flagged main implementation risk gets the most coverage here):

- **The core isolation test** (SPEC's explicitly flagged risk): user A
  and user B share a group conversation with matching-content messages;
  user A leaves (or is removed from, or the conversation is archived,
  or soft-deleted) the conversation; user A's subsequent search for
  that content returns zero results from that conversation, while
  another conversation A is still a current participant of still
  surfaces correctly. Run as **one parameterized test across all four
  removal modes** (left / removed / archived / soft-deleted) rather
  than four independently-written tests, so the assertion shape can't
  drift between them.
- **AppSec-required cross-tenant regression test**: a user who is a
  current participant of conversations in two different tenants
  (Tenant 1 and Tenant 2), with matching-content messages in both,
  searching with Tenant 1 active in session returns only Tenant 1's
  matches, never Tenant 2's — this is the exact gap AppSec's review
  caught (fa68743-shaped, but leaking message content rather than
  participant identities) and must be covered explicitly, not merely
  implied by the archived/soft-delete parameterized test above. A
  companion case: the same user with **no active tenant** in session
  (fail-closed) gets zero results, not an unfiltered cross-tenant scan
  — including when `tenantContext.isStaff()` is true, confirming the
  deliberate absence of `TenantFilterAspect`'s own staff-no-active-
  tenant bypass for this endpoint specifically (see AppSec correction
  in Architectural decisions).
- REQ-5/staff-no-bypass: a `STAFF_ADMIN` and a `MEMBER_ADMIN` user, each
  with zero `chat_participants` rows on a conversation containing
  matching content, get zero results from that conversation via this
  endpoint — explicit regression test against ever wiring in
  `BypassTenantFilterForOversight` or any oversight check here, exactly
  the thing REQ-5 forbids.
- REQ-3: a `conversationId` filter pointing at (a) a real conversation
  the caller isn't a participant of, (b) a nonexistent id, (c) a
  `SUPPORT` conversation, (d) an archived/soft-deleted former
  conversation of the caller's — all four return the same shape
  (`200`, empty/unaffected results), asserted to be indistinguishable
  from each other and from "no matches."
- REQ-7/8/9: `senderId`, `conversationId`, `dateFrom`/`dateTo` each
  narrow results correctly individually and in combination (matrix of
  2-3 combined-filter cases, not just each filter alone).
- REQ-10: chronological ordering and cursor pagination — fetch page 1,
  follow `nextCursor` to page 2, assert no overlap/no gaps against a
  fixture of >`size` matching messages spread across conversations.
- REQ-13/14: an integration-level confirmation (not just the resolver's
  own unit test) that a Portuguese-resolved caller matches a
  conjugated/plural Portuguese word form a literal substring search
  would miss (e.g. searching `"reunião"` matches a message containing
  `"reuniões"`), and the equivalent for English; and that supplying an
  arbitrary/forged locale-shaped query parameter (if one were
  mistakenly accepted) has no effect — confirms REQ-14's "never from a
  client-supplied parameter" end-to-end, not just "the resolver ignores
  it in isolation."
- REQ-1: a message inside a `SUPPORT` conversation the caller has a
  role in (ticket owner or assigned staff) never appears in results,
  confirming REQ-1's scope exclusion isn't accidentally satisfied only
  by REQ-2's participant check (a caller could plausibly have some
  non-`chat_participants` relationship to a support ticket — this test
  confirms the `kind` filter is doing real, independent work).
- Pre-existing regression companions: reuse
  `chat-group-membership-management`'s archived/soft-deleted fixtures
  where practical (same helper builders) rather than re-deriving
  conversation/participant test fixtures from scratch.
