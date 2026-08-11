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

## Amended (2026-08-10, role-based scoping) — REQ-5e–REQ-5j implementation

> Supersedes every "fail closed, no bypass for anyone, including staff
> with no active tenant" statement above (Architectural decisions'
> "Deliberately no staff-no-active-tenant bypass" note, and the
> REQ-5/staff-no-bypass regression test) **for message-content search
> only** — those statements described REQ-5's *original* wording and
> are now superseded by SPEC.md's "Amended (2026-08-10, role-based
> scoping) — REQ-5 completion" section (REQ-5e through REQ-5j). The
> unified entity-search section below (REQ-16–REQ-26) is explicitly
> unaffected per the SPEC's own framing note and needs no changes here.
> **AppSec must review this section before TASKS.md is generated** —
> see "AppSec review required" at the end of this amendment.

- **Role determination reuses `TenantContext.isStaffAdmin()`/`isStaff()`
  plus the exact `isActiveMemberAdminOf(actor, tenant)` pattern already
  established in `ChatConversationService`** (active
  `TenantMembership` lookup via `tenantMembershipRepository
  .findByUserAndTenant(actor, tenant).filter(TenantMembership::isActive)
  .filter(m -> m.getRole() == MembershipRole.MEMBER_ADMIN)`), rather than
  inventing a new role-resolution helper or a `@PreAuthorize` expression.
  This mirrors the same precedent `ChatConversationService#canReadSupportChannel`
  and `#isActiveMemberAdminOf` already use for an identical "does this
  caller hold the admin role in their currently-active tenant"
  question — the exact reference shape `DECISIONS.md`'s "Staff bypass
  authorization, never isolation" entry describes, now applied to a new
  endpoint instead of a new mechanism.
  `ChatMessageSearchService.search()` computes one `ChatMessageSearchScope`
  (new small private enum/record: `PLATFORM_UNRESTRICTED` /
  `TENANT_UNRESTRICTED` / `PARTICIPANT_AND_DISCOVERABLE`) with this
  precedence, evaluated in order so no caller can satisfy two branches
  ambiguously:
  1. `tenantContext.isStaffAdmin()` → `PLATFORM_UNRESTRICTED` (REQ-5e),
     regardless of any active tenant in session.
  2. Else, if `tenantContext.getActiveTenantId()` is present **and**
     `isActiveMemberAdminOf(actor, thatTenant)` → `TENANT_UNRESTRICTED`,
     bound to that tenant id (REQ-5g).
  3. Else, if an active tenant is present (ordinary `MEMBER`) →
     `PARTICIPANT_AND_DISCOVERABLE`, bound to that tenant id (REQ-5h/5j).
     **AppSec-required invariant**: immediately before binding
     `activeTenantId` for this branch, assert
     `Preconditions`-style (`if (activeTenantId == null) throw new
     IllegalStateException(...)`, matching the existing "throw
     `IllegalStateException` for an invariant that must never happen"
     pattern already used elsewhere in this codebase, e.g.
     `BlindIndexService`/`TaxIdEncryptionConverter`/`MailService`) that
     `activeTenantId` is non-null in this branch specifically. This
     exists solely so a future refactor that reorders or splits the
     precedence chain fails loudly here instead of silently falling
     through to the `PARTICIPANT_AND_DISCOVERABLE` fragment's nullable-
     aware `(:activeTenantId IS NULL OR cc.tenant_id =
     :activeTenantId)` guard with a null id — which would widen branch
     3 (an ordinary tenant `MEMBER`) to platform-wide scope, the exact
     failure mode branch 4's legitimate null-tenant case is allowed to
     hit. Branch 4 keeps no such assertion (null is its correct,
     intentional state).
  4. Else, if `tenantContext.isStaff()` (no active tenant) →
     `PARTICIPANT_AND_DISCOVERABLE`, unbound to any tenant — this is the
     staff-chat-parity fix (REQ-5f): staff's own participant/discoverable
     conversations are almost always `tenant_id IS NULL` (staff-to-staff
     chat), so the predicate below simply never applies a tenant filter
     in this branch rather than needing a separate "tenant IS NULL"
     special case.
  5. Else (no active tenant, not staff) → fail closed exactly as today:
     empty result, no query executed. This preserves REQ-2's original
     baseline for the one caller shape the amendment doesn't touch.

- **Repository shape: four native-query variants (×2 for pt/en) replace
  today's two, sharing predicate fragments as SQL string constants
  rather than duplicating the full statement four times.** Current
  `ChatMessageSearchRepository` already composes `SELECT_AND_JOIN` +
  `SCOPE_PREDICATE` + a locale-specific `@@` clause + `ORDER_AND_LIMIT`;
  this amendment splits `SCOPE_PREDICATE` into an always-present base
  (`kind IN (...)`, `archived_at`/`deleted_at IS NULL`,
  `m.deleted_at IS NULL`, and the existing optional `senderId`/
  `conversationId`/`dateFrom`/`dateTo`/`cursor` filters — all unchanged)
  plus one of three interchangeable **scope fragments**, selected in
  Java by `ChatMessageSearchScope` before the query method is chosen:
  - `PLATFORM_UNRESTRICTED`: no tenant predicate, no participant/
    discoverability predicate at all — `searchUnrestrictedPt`/`En`.
  - `TENANT_UNRESTRICTED`: `cc.tenant_id = :activeTenantId` only, no
    participant/discoverability predicate — `searchTenantUnrestrictedPt`/
    `En`. This is the existing tenant predicate from the AppSec
    correction above, reused verbatim; it never leaks cross-tenant for
    the same reason it doesn't today.
  - `PARTICIPANT_AND_DISCOVERABLE`: today's `chat_participants EXISTS`
    clause, **OR**ed with a new
    `cc.id = ANY(:additionalVisibleConversationIds)` clause, both still
    inside the same `(:activeTenantId IS NULL OR cc.tenant_id =
    :activeTenantId)` guard (nullable-aware, so the staff/no-tenant case
    in branch 4 above naturally applies no tenant restriction) —
    `searchScopedPt`/`En`. `additionalVisibleConversationIds` is
    resolved in Java, not SQL (see next bullet), so this fragment never
    needs to re-express `ChatEligibilityService`'s membership-tier/plan
    logic in raw SQL.
  This keeps the "one `WHERE` clause, no post-filter step in Java"
  posture the original AppSec correction established — the OR'd id list
  is a bind parameter, not a second query pass over already-fetched rows.

- **`additionalVisibleConversationIds` reuses existing discoverability
  building blocks unchanged, confirmed present**: `ChatConversationRepository
  .findDiscoverable(Pageable)` (visibility `IN (PUBLIC, REQUEST_TO_JOIN)`,
  already backing `ChatConversationService#listDiscoverableGroups` for
  REQ-19/28-style group discoverability) plus `ChatEligibilityService
  .isEligible(actor, tenantAnchorOf(conversation))` (already the
  "who can reach a group they haven't joined" gate) are the exact two
  building blocks needed — no duplication required. One gap: both are
  currently `Pageable`-shaped for UI listing, and search needs the full
  id set (bounded by workspace group count, not message count, so this
  is cheap). **New, small repository addition**: a sibling
  `findDiscoverableIds(Long tenantId)` / `findDiscoverableIdsPlatformWide()`
  query on `ChatConversationRepository` returning `List<Long>` only (same
  `visibility IN (...)` predicate as `findDiscoverable`, no pagination),
  reused by `ChatMessageSearchService` to compute, per request: fetch
  candidate ids → filter with `ChatEligibilityService.isEligible` →
  filter out ids the caller is already a participant of (already covered
  by the OR'd participant-EXISTS clause, so no need to de-duplicate in
  Java) → pass the remainder as the bind parameter. This is additive to
  `ChatConversationRepository`, not a new mechanism, and PRIVATE groups
  the caller hasn't joined are never included, since `findDiscoverable`'s
  `visibility IN (PUBLIC, REQUEST_TO_JOIN)` predicate already excludes
  them (REQ-5i/REQ-3's amended note). **AppSec-required cap**:
  `findDiscoverableIds`/`findDiscoverableIdsPlatformWide` carry an
  explicit `LIMIT 100` in the native query itself (not just a Java-side
  truncation after fetch), reusing the same `100` ceiling already
  established as this codebase's page-size cap convention
  (`ChatCursor.MAX_PAGE_SIZE`, `TenantService`/`StaffService`'s
  `MAX_PAGE_SIZE`) rather than inventing a new number — so the "bounded
  by workspace group count, cheap in practice" assumption above is an
  enforced ceiling, not an unenforced belief, and a tenant/platform with
  an unusually large discoverable-group count can't turn this into an
  unbounded-query DoS vector. A caller with more than 100 eligible
  discoverable groups simply gets the first 100 (by the same default
  ordering `findDiscoverable` already uses) folded into the OR'd id
  list; the already-joined participant path is unaffected by this cap
  since it's covered by the separate `chat_participants EXISTS` clause.

- **Why not fold eligibility into the native SQL directly**: `ChatEligibilityService
  .isEligible` branches on tenant membership state, plan/tier rules, and
  staff-capability checks that are Java-side business logic today, not
  columns queryable in one predicate; re-deriving that as raw SQL would
  duplicate logic that must stay in one place per this same file's
  earlier "no duplicated scoping logic per kind" principle (SPEC
  "Relationship to existing SPECs"). The bind-parameter-id-list approach
  keeps `ChatEligibilityService` as the single source of truth while
  still executing the actual message filter as one SQL statement.

- **`ChatMessageSearchService`'s Javadoc claim that it "deliberately
  never reads `tenantContext.isStaff()`/`isStaffAdmin()`" is now
  incorrect and must be rewritten**, not left standing next to
  contradicting code — this is exactly the kind of stale-comment risk
  DECISIONS.md warns about; the TASKS.md breakdown must include
  updating that Javadoc alongside the code change, in the same commit.

- **No new DECISIONS.md entry required.** This is not a novel
  architectural pattern — it is the existing "staff bypass
  authorization, never isolation" / `MEMBER_ADMIN`-unrestricted-within-
  tenant precedent (already used by `ChatConversationService` for group
  administration) applied to a new endpoint that previously opted out
  of it for a stated, now-superseded reason. Flagging this explicitly
  per this PLAN's own discipline rule rather than silently assuming it.

- **AppSec review required before TASKS.md.** This changes an
  authorization-critical, previously AppSec-hardened query (the same
  `ChatMessageSearchRepository` that already received one AppSec
  correction for a cross-tenant leak) to add a materially broader grant
  (platform-wide search for `STAFF_ADMIN`, tenant-wide for
  `MEMBER_ADMIN`) and a new OR-based visibility clause. Per this repo's
  standing rule, appsec-review must sign off on this section of PLAN.md
  before TASKS.md is generated — do not proceed to task breakdown until
  that review lands. Two areas most likely to need scrutiny: (a) the
  nullable-aware `(:activeTenantId IS NULL OR ...)` guard must be
  verified to never evaluate `NULL OR true` in a way that widens scope
  for a non-staff, non-tenant caller (branch 5's fail-closed case must
  never reach the query at all, exactly as today); (b) the
  `additionalVisibleConversationIds` id list must be verified to be
  computed fresh per request (never cached) and never accept a
  client-supplied id, consistent with REQ-2/REQ-17's "re-derived at
  request time" posture.

## Amended (2026-08-10, context-boundary correction) — REQ-5e–REQ-5j implementation (final)

> **This section fully replaces "Amended (2026-08-10, role-based
> scoping) — REQ-5e–REQ-5j implementation" above (the v1 draft, kept as
> a superseded historical record — do not implement it).** It
> implements SPEC.md's "Amended (2026-08-10, context-boundary
> correction) — REQ-5 completion (final)" section, which supersedes the
> v1 SPEC draft this v1 PLAN section was written against. This is the
> **second** correction to this same query path in one session — the
> diff from v1 is narrow and enumerated below so AppSec's re-review can
> be targeted rather than a full re-read.

**What changes vs. what stays the same.**

- **Deleted entirely**: the `PLATFORM_UNRESTRICTED` scope fragment and
  its two repository methods (`searchUnrestrictedPt`/`En`), and the
  `SELECT_AND_JOIN + BASE_PREDICATE + <locale clause> + ORDER_AND_LIMIT`
  query built with no tenant predicate at all. Not kept as unused dead
  code — removed, because there is no longer any caller shape that
  searches across tenant boundaries or across the staff/tenant boundary.
- **New, structurally mirrors `TENANT_UNRESTRICTED`**: a
  `STAFF_SCOPE_UNRESTRICTED` fragment/pair of repository methods
  (`searchStaffScopeUnrestrictedPt`/`En`) for `STAFF_ADMIN` in staff
  scope (REQ-5e, corrected) — `AND cc.tenant_id IS NULL` in place of
  `TENANT_UNRESTRICTED`'s `AND cc.tenant_id = :activeTenantId`, no
  participant/discoverability predicate, exactly the same shape
  otherwise (same `BASE_PREDICATE`, same locale clauses, same
  `ORDER_AND_LIMIT`). No bind parameter needed for this fragment (unlike
  `TENANT_UNRESTRICTED`'s `:activeTenantId`) since `IS NULL` is a
  constant condition, not caller-supplied.
- **Unchanged**: `TENANT_UNRESTRICTED` (`searchTenantUnrestrictedPt`/
  `En`, REQ-5g/`MEMBER_ADMIN`) — already correctly modeled in v1,
  confirmed by re-reading `ChatMessageSearchRepository.java` lines
  127–163; no edit needed to these two methods. `PARTICIPANT_AND_
  DISCOVERABLE` (`searchScopedPt`/`En`, REQ-5f/REQ-5h, non-admins) and
  its nullable-aware `((:activeTenantId IS NULL AND cc.tenant_id IS
  NULL) OR cc.tenant_id = :activeTenantId)` guard are also unchanged —
  this guard already correctly implements REQ-5j's context-boundary
  invariant for the non-admin case (it was the admin short-circuit in
  `ChatMessageSearchService.search()`, not this SQL, that let
  `STAFF_ADMIN` bypass context resolution).

- **`ChatMessageSearchService.search()` precedence is restructured to
  resolve context first, then branch on admin-vs-non-admin within it** —
  this is the actual root-cause fix, because the confirmed bug was
  `STAFF_ADMIN`'s branch (old branch 1, `tenantContext.isStaffAdmin()`)
  short-circuiting ahead of any context check at all. New precedence,
  replacing lines 99–215 of the current
  `ChatMessageSearchService.java`:
  1. Resolve `activeTenantId = tenantContext.getActiveTenantId()` first,
     unconditionally, before any role check.
  2. If `activeTenantId` is present: this is tenant-X context.
     - If `isActiveMemberAdminOf(actor, activeTenantId.get())` →
       `TENANT_UNRESTRICTED`, bound to that tenant (REQ-5g). Unchanged
       from v1's branch 2.
     - Else → `PARTICIPANT_AND_DISCOVERABLE`, bound to that tenant
       (REQ-5h/5i). Unchanged from v1's branch 3, including the
       AppSec-required `IllegalStateException` non-null invariant
       (still needed — see below).
  3. Else (`activeTenantId` empty): this is staff-scope context. A
     caller who reaches this branch with neither `isStaff()` nor
     `isStaffAdmin()` true falls through to the existing fail-closed
     branch — that caller shape (no active tenant, not staff) is
     untouched by this correction.
     - If `tenantContext.isStaffAdmin()` → **`STAFF_SCOPE_UNRESTRICTED`**
       (new; REQ-5e corrected) — the direct fix: this branch is now only
       reachable when there is *no* active tenant, so `STAFF_ADMIN`
       can no longer short-circuit into an active tenant's content.
     - Else if `tenantContext.isStaff()` → `PARTICIPANT_AND_DISCOVERABLE`,
       unbound to any tenant (REQ-5f). Unchanged from v1's branch 4,
       reusing `additionalVisibleConversationIdsPlatformWide` — see
       naming note below.
     - Else → fail closed, unchanged from v1's branch 5.
  This is a *reordering plus one substitution* (staff-admin fragment
  swapped, tenant-first precedence), not a rewrite of the non-admin
  branches, the cursor/pagination logic, the logging, or the DTO
  mapping — all of that (lines 216–310 of the current file) is
  unaffected.

- **`additionalVisibleConversationIdsPlatformWide`/
  `findDiscoverableIdsPlatformWide` naming is now misleading and should
  be renamed to `...StaffScope`/`findDiscoverableIdsStaffScope`** (no
  behavior change — these already only ever get invoked from the
  staff-no-active-tenant branch, never actually platform-wide across
  tenants) to stop the name implying a scope that no longer exists
  anywhere in this feature. Purely cosmetic, but worth doing in the same
  commit per this codebase's "don't leave a stale name next to
  corrected behavior" precedent (mirrors the existing Javadoc-rewrite
  rule below).

- **AppSec-required non-null invariant (v1's branch-3 guard, now
  branch-2's tenant-admin/tenant-member sub-branches): still needed,
  restated, not moot.** The reason it existed — a future refactor
  reordering the precedence chain and silently falling through into the
  nullable-aware `PARTICIPANT_AND_DISCOVERABLE` fragment with a null id
  — is if anything *more* relevant under the corrected model, since
  context resolution is now a single explicit fork (tenant-present vs.
  absent) that all four role outcomes flow through; an accidental swap
  of that fork's branches would now silently misroute an ordinary tenant
  `MEMBER` into staff-scope-shaped handling instead of just widening one
  branch. Keep the `IllegalStateException` assertion exactly as
  written in v1, moved (not altered) into the new tenant-present branch.
  No corresponding assertion is needed on the staff-scope side: `STAFF_
  SCOPE_UNRESTRICTED`'s `tenant_id IS NULL` condition is a SQL constant,
  not a caller-supplied bind parameter, so there is no null-vs-non-null
  ambiguity to guard against there.

- **LIMIT 100 `findDiscoverableIds`/`findDiscoverableIds...Scope` cap:
  confirmed unaffected, explicitly.** This cap and its reuse of
  `ChatConversationRepository.findDiscoverable`-style querying plus
  `ChatEligibilityService.isEligible` back the four-category non-admin
  case (REQ-5f/REQ-5h/REQ-5i) exclusively — that logic path is untouched
  by this correction, which only changes the admin-branch fragments and
  the precedence order deciding which fragment runs. No change needed
  to `ChatConversationRepository` or `ChatEligibilityService` usage.

- **`ChatMessageSearchService`'s class/method Javadoc already documents
  the v1 (wrong) precedence** (see current file lines 25–41) and must be
  rewritten to the corrected five-branch precedence above, in the same
  commit as the code change — same "no stale comment next to corrected
  code" rule v1's PLAN already flagged for the *original* pre-2026-08-10
  Javadoc; it now applies a second time to v1's own amendment Javadoc.
  Likewise `ChatMessageSearchRepository`'s class Javadoc (current file
  lines 29–60, describing `searchUnrestrictedPt`/`En` and the old
  three-fragment model) needs the `PLATFORM_UNRESTRICTED` bullet deleted
  and a `STAFF_SCOPE_UNRESTRICTED` bullet added in its place, mirroring
  the `TENANT_UNRESTRICTED` bullet's wording exactly.

- **No new DECISIONS.md entry required**, for the same reason v1 stated
  none was needed: this remains an application of the existing "admin
  role removes participancy restriction within one bounded scope"
  precedent, now applied correctly (bounded to the caller's *current*
  context) instead of incorrectly (unbounded). The correction itself —
  "resolve context before role" — is a bug fix to this feature's own
  precedence logic, not a new cross-cutting architectural pattern
  reusable elsewhere.

- **AppSec re-review required before TASKS.md/implementation resumes —
  second pass on this same code path.** Scope the re-review to exactly
  four things, to keep it fast: (1) confirm `STAFF_SCOPE_UNRESTRICTED`'s
  `cc.tenant_id IS NULL` condition can never be satisfied by a row that
  also matches a non-null tenant (trivial by construction, but worth one
  look given the previous bug was exactly this class of guard failing);
  (2) confirm the reordered precedence in `ChatMessageSearchService
  .search()` truly resolves `activeTenantId` before any role check, with
  no remaining code path that reads `isStaffAdmin()`/`isStaff()` first;
  (3) confirm the `IllegalStateException` invariant moved cleanly into
  the tenant-present branch without being dropped or weakened; (4)
  confirm no other branch was inadvertently touched — `TENANT_
  UNRESTRICTED` and `PARTICIPANT_AND_DISCOVERABLE` (both SQL and their
  callers) should diff as unchanged. Do not re-review the discoverable-
  ids/eligibility/pagination machinery — that was already reviewed
  under v1 and is confirmed unaffected above.

## Amended (2026-08-10) — unified entity search (REQ-16 through REQ-26)

> Companion to SPEC.md's "Amended (2026-08-10)" section. Everything
> above this heading (REQ-1 through REQ-15's design) is unchanged and
> shipped — this section only adds the new entity-search capability.
> Nothing here modifies `ChatMessageSearchService`/`ChatMessageSearch
> Repository`/`GET /api/chat/messages/search` in any way.

### Architectural decisions

- **One new, separate endpoint, `GET /api/chat/search`, not four
  parallel endpoints and not a parameter on `GET /api/chat/messages/
  search`.** SPEC's non-functional requirements already pin the
  "separate from message search" half of this call (see the SPEC's own
  rationale, not re-litigated here). The remaining call this PLAN makes
  is single-endpoint-with-four-sections vs. four independent endpoints:
  a single endpoint wins because (a) every result kind shares the exact
  same "recent places on blank query" trigger (REQ-25/26) and the exact
  same request shape (`q`, nothing else required) — four endpoints
  would mean four copies of blank-query handling instead of one; (b)
  `chat-unified-ui`'s own frontend SPEC already models this as one
  dropdown backed by one request-in-flight per keystroke (debounced),
  not four independently-racing requests; (c) `PageResponseDto`-style
  per-kind pagination (see "see more" below) composes cleanly as
  same-response sub-lists with independent `hasMore` flags, so there is
  no structural reason (unlike message-search's cursor, which had no
  natural per-kind meaning) to split the response. Per-kind partial
  failure (frontend SPEC REQ-30) is still possible with one endpoint: a
  transient failure inside one section's query is caught per-section in
  the service (see "Partial-failure handling" below) rather than
  failing the whole request, so this doesn't reintroduce the
  all-or-nothing risk the SPEC's rationale for message-search's own
  endpoint split was guarding against.
- **Route: `GET /api/chat/search`, distinct from `GET /api/chat/
  messages/search`.** No collision — `search` vs. `messages/search`
  are different path segments under `/api/chat`, both already live on
  `ChatController`; confirmed by listing `ChatController`'s existing
  `@GetMapping`s (no existing `/api/chat/search` mapping).
- **New `ChatEntitySearchService`, not folded into `ChatMessageSearch
  Service` or `ChatConversationService`.** Same reasoning the shipped
  PLAN already gave for keeping message search out of
  `ChatConversationService` (materially different authorization shape,
  see that section above) — entity search's shape is different again
  (four independent sub-queries against four different tables/services,
  no single `chat_participants` join can express it), so it gets its
  own service rather than further bloating either existing one.
- **No new repository query for people/Support/RAG matching — each
  reuses an existing service method's own query, called directly, not
  re-implemented:**
  - **People**: `ChatEntitySearchService` calls `UserRepository` for a
    name-prefix/substring match (new, small JPQL query — see below —
    `UserRepository` is a plain `@Filter`-free entity with no tenant
    column of its own, so this is safe to express as ordinary JPQL),
    then filters the candidate set through `ChatEligibilityService
    .eligibleAnchorsFor(candidate)` intersected with the caller's own
    `directScopeAnchorsForActor`-equivalent anchors — **this requires a
    small, additive change to `ChatEligibilityService`**: its existing
    `listCandidates(actor, "direct", tenantId)` method already computes
    exactly this anchor-intersection per candidate but does it over
    *every* non-deleted user in the system with no name filter, which
    is correct for a "browse everyone I could message" list but wasteful
    for a search-as-you-type endpoint hitting the same full-table scan
    on every keystroke. **New method: `ChatEligibilityService
    .searchEligibleDirectCandidates(User actor, String nameQuery, int
    limit)`** — same anchor-intersection logic as `listCandidates`'s
    `"direct"` branch (reused, not duplicated, via a shared private
    helper both methods now call), but pushes the name filter into the
    `UserProfileRepository` query itself (`WHERE LOWER(full_name) LIKE
    LOWER(:pattern) AND u.deletedAt IS NULL`, JPQL, no `@Filter`
    concerns since `UserProfile` carries no tenant column) so the
    eligibility check only runs over already-name-matched rows, not the
    whole user table. **The explicit `deletedAt IS NULL` guard is
    required here, not optional** — `listCandidates`'s existing
    "direct" path only ever sees non-deleted users to begin with,
    because it starts from `userRepository.findAllByDeletedAtIsNull()`;
    this new query starts from `UserProfile` directly instead (to push
    the name filter down), so it must restate the same guard explicitly
    or it silently re-opens the already-fixed 2026-08-04 "a soft-deleted
    user must never surface as a chat candidate" bug for this one new
    code path. This is a **within-precedent extension of an existing,
    already-approved method** (same rule REQ-20 already established,
    only now with a query-side prefilter for cost reasons, plus the
    restated soft-delete guard above), not a new access rule — no
    DECISIONS.md entry needed, this is Tier 1.
  - **Groups**: `ChatEntitySearchService` calls a new
    `ChatConversationService.searchDiscoverableGroups(User actor, String
    nameQuery, int limit)`, which is `listDiscoverableGroups`'s exact
    body (`findDiscoverable` + the same `isEligible`/`!isParticipant`
    filters) plus (a) a name predicate pushed into a new
    `ChatConversationRepository.findDiscoverableByTitle(String
    pattern, Long activeTenantId, Pageable)` query (JPQL, `title ILIKE
    :pattern`), and (b) participant groups the caller is *already in*
    whose title
    matches, which `listDiscoverableGroups` deliberately excludes today
    (its `!isParticipant` filter) but REQ-19 requires for search
    ("groups the caller currently participates in, **plus**
    non-participant discoverable groups") — added as a second query
    (`chatParticipantRepository.findByUserId` filtered by title) whose
    results are unioned with the discoverable-set query, de-duplicated
    by conversation id.
    **AppSec correction: `findByUserId` is NOT tenant-scoped and must
    not be treated as `@Filter`-trusted for this branch.**
    `ChatParticipant.java` carries `@Filter(SoftDeleteFilter)` only — no
    `@Filter(TenantFilter)`, no tenant column on the entity at all — so
    `findByUserId(actor.getId())` returns every conversation the caller
    participates in *across every tenant they belong to*, exactly the
    same class of gap the original message-search PLAN was corrected on
    (`chat_participants` alone never implies a tenant boundary). This is
    the identical situation `ChatConversationService#listConversations`
    already had to solve, and it already solves it correctly: after
    `findByUserId(actor.getId()).stream().map(ChatParticipant
    ::getConversation)`, it applies `isVisibleUnderActiveTenant(actor,
    conversation)` — a private method comparing `conversation.getTenant()`
    against `TenantContext.getActiveTenantId()` in Java — **before**
    returning anything. The new participant-groups branch reuses this
    exact check, not a re-derived one: `isVisibleUnderActiveTenant` is
    promoted from `private` to package-private (or a small shared
    `ChatTenantVisibility` helper extracted if a cleaner seam is
    preferred at implementation time) so `ChatConversationService
    .searchDiscoverableGroups` can call the identical logic
    `listConversations` already uses, rather than duplicating a
    second, potentially-drifting copy of the same comparison. Applied
    to every row from `findByUserId` **before** the title filter and
    **before** unioning with the discoverable-groups query result, same
    "tenant check first, structurally inseparable from the rest of the
    predicate" discipline this PLAN already established for the shipped
    native message-search query — only expressed in Java here since
    `findByUserId` is a fixed-shape existing method, not a query this
    PLAN is free to add a bind parameter to.
  - **AppSec correction (post-review): both new JPQL queries
    (`findDiscoverableByTitle` for groups and the RAG title query below)
    need their own explicit `tenantId`/`activeTenantId` bind parameter in
    the query text itself — relying on Hibernate's `@Filter` alone is not
    sufficient for either, even though both are ordinary JPQL (not
    native SQL like the shipped message-search query).** The reasoning
    "JPQL is safe to trust to `@Filter`" holds for an ordinary tenant-
    scoped caller, but not for the specific state `TenantFilterAspect`
    itself special-cases: it is a global `@Around` advice applied to
    *every* `@Transactional` service method, and it disables
    `TenantFilter` session-wide whenever `bypassForOversight ||
    (tenantContext.isStaff() && activeTenantId.isEmpty())` — a
    `STAFF`/`STAFF_ADMIN` caller with no active tenant selected is an
    easily-reachable state, not a hypothetical edge case, and it applies
    regardless of whether `ChatEntitySearchService`/`ChatConversation
    Service`/`ConversationService` themselves ever call
    `isStaff()`/`isStaffAdmin()` — the filter can be disabled by the
    aspect out from under a query that never looked at staff status at
    all. Absence of an `isStaff()` call in the new service code (this
    PLAN's original self-audit) is therefore not evidence the query is
    safe; the filter's *disabled* state is a property of the current
    Hibernate session, not of the calling code. **Fix, mirroring the
    shipped native message-search query's own already-approved pattern
    exactly, just for JPQL instead of native SQL:**
    - `findDiscoverableByTitle(String pattern, Long activeTenantId,
      Pageable pageable)` gets `tenant_id = :activeTenantId` written
      directly into its JPQL `WHERE` clause, in the same predicate as
      `title ILIKE :pattern` and `visibility IN ('PUBLIC',
      'REQUEST_TO_JOIN')` — not left to the session-level filter alone.
    - `ChatConversationService.searchDiscoverableGroups` resolves
      `TenantContext.getActiveTenantId()` itself, **before** calling the
      repository, and fails closed (empty result for the discoverable-
      groups branch, no query executed) when absent — same "no query
      run at all," not a sentinel value, discipline
      `ChatMessageSearchService` already uses. This governs every
      caller identically, including staff with no active tenant — no
      staff-only anchor exception here, since `listDiscoverableGroups`'s
      own existing rule (which this reuses) has no concept of a
      staff-only/null-tenant discoverable group to begin with.
    - The RAG query (see below) gets the identical treatment: an
      explicit `tenantId = :tenantId` predicate in its own JPQL, plus
      `ConversationService.searchOwn` resolving and fail-closing on
      `TenantContext.getActiveTenantId()` the same way, before calling
      the repository.
    - Both new repository methods' Javadoc explicitly documents *why*
      the explicit predicate is required despite being JPQL (the
      `TenantFilterAspect` staff-no-active-tenant case above) — mirroring
      `ChatMessageSearchRepository`'s own precedent Javadoc for the
      native-SQL case, so a future contributor doesn't assume "it's
      JPQL, `@Filter` handles it" is a safe generalization anywhere a
      caller can plausibly be staff.
  - **Support**: `ChatEntitySearchService` matches the fixed "Suporte"/
    "Support" label (locale-aware: matches the same string in whichever
    of the caller's `Accept-Language`-resolved locale's translations —
    reuses this PLAN's own `ChatSearchLocale`/`ChatMessageSearchLocale
    Resolver`, see below) against `q` via simple case-insensitive
    substring match in Java (no query at all — REQ-21 says the *label*
    is fixed and always-available; the actual visibility is entirely
    determined by an existing call, not a new query): if the label
    matches, calls the existing `SupportTicketService`/`ChatConversation
    Service.getConversation`-reachable "does this caller have a Support
    channel" check. Concretely: a new **read-only, side-effect-free**
    `SupportTicketService.findOwnOrClaimableChannel(User actor, Long
    activeTenantId)` that composes two already-existing lookups
    (`SupportTicketRepository`'s member-channel lookup used by
    `requireChannelId`'s member path, and staff's unclaimed-inbox/
    claimed-ticket visibility already used by `listUnclaimed`/`claim`)
    into one Optional-returning method, rather than duplicating either
    lookup's query. Requires `TenantContext.getActiveTenantId()` the
    same fail-closed way message search does (see below) — a caller
    with no active tenant gets no Support result, since Support is
    always tenant-anchored.
  - **RAG conversations**: `ChatEntitySearchService` calls a new
    `ConversationService.searchOwn(User owner, Long tenantId, String
    titleQuery, int limit)`, mirroring `ConversationService.list`'s
    existing `requireActiveTenant` + owner-scoping exactly, plus a new
    **explicit-`@Query` JPQL method** (not a Spring Data derived query,
    per the AppSec correction above) — `ConversationRepository
    .searchByOwnerAndTitle(@Param("ownerId") Long ownerId, @Param
    ("tenantId") Long tenantId, @Param("pattern") String
    titlePattern, Pageable pageable)`, `@Query("SELECT c FROM
    Conversation c WHERE c.owner.id = :ownerId AND c.tenant.id =
    :tenantId AND LOWER(c.title) LIKE LOWER(:pattern) ORDER BY
    c.createdAt DESC")`. The explicit `c.tenant.id = :tenantId`
    predicate is written into the query text itself, not left to
    `Conversation`'s own `@Filter(TenantFilter)` alone — same reasoning
    as `findDiscoverableByTitle` above: `@Filter` is session-scoped and
    `TenantFilterAspect` disables it for a staff caller with no active
    tenant, a state this query must not silently widen into "every
    tenant's RAG conversations this owner has ever created" for. Owner-
    scoping (`c.owner.id = :ownerId`) stays as an additional, independent
    predicate in the same clause, consistent with REQ-22's ownership
    rule — the tenant predicate narrows *which* tenant's conversations
    are visible, the owner predicate narrows *whose*; neither substitutes
    for the other.
- **"Recent places" (REQ-25/26) — SPEC's REQ-26 premise does not hold as
  literally stated for the RAG result kind, and this PLAN corrects it
  rather than silently reinterpreting the SPEC.** REQ-26 says "recent
  places" is served entirely from `ChatConversationService#listConversations`'s
  existing data — but `listConversations` only ever returns `chat`-
  package conversations (`PEER_DIRECT`/`PEER_GROUP`/`SUPPORT`, all
  backed by `ChatConversation`); it has no path to RAG conversations at
  all, because those live in the structurally separate `conversation`
  package/table (`Conversation`, owned by `ConversationService`, no
  relationship to `ChatConversation` whatsoever — confirmed by reading
  both services/entities directly). REQ-25 explicitly requires "recent
  places" to cover "any kind: 1:1, group, Support, RAG" — so taken
  literally, REQ-26 as written cannot deliver REQ-25's own scope for the
  RAG kind; `listConversations` alone structurally cannot include it.
  **Resolution (Tier 2, not Tier 3 — this doesn't touch scope or add a
  new query, it corrects which *existing* queries satisfy an already-
  approved requirement):** "recent places" merges the output of **two**
  already-existing, zero-new-query capabilities — `ChatConversationService
  .listConversations(actor)` (chat-kinds) and `ConversationService.list
  (owner, activeTenantId)` (RAG-kind, already `@Filter`-tenant-scoped,
  already ordered `findByOwnerIdOrderByCreatedAtDesc`) — rather than one.
  Both are already-shipped, already-tested read paths; nothing new is
  queried, and REQ-26's own stated rationale ("no new backend query, no
  new persisted recency signal, reuse the existing id-descending proxy")
  is honored exactly, just across two existing sources instead of one.
  The two lists are interleaved using each source's own existing
  ordering (`listConversations`'s natural order — itself already the
  accepted id-descending-via-participant-row proxy per REQ-26's own
  rationale — for chat kinds; `createdAt desc` for RAG, already ordered
  by the repository query) via a simple k-way merge on each item's own
  `createdAt`/`lastMessageAt` where present, falling back to id order
  where a chat conversation has no messages yet (mirrors `Chat
  ConversationSummaryDto.from`'s existing null-lastMessageAt handling)
  — capped at the same small fixed count as every other result group
  (see below). **This does not require a DECISIONS.md entry**: it's a
  design correction inside an already-approved requirement's own stated
  intent (REQ-26's rationale is about avoiding new schema/queries, which
  this still avoids), not a scope change — but it is flagged here
  explicitly, in writing, exactly as Tier 2 requires, and should be
  read back to the product owner/PO agent alongside this PLAN amendment
  before TASKS.md, since it changes REQ-26's literal implementation
  detail (though not its intent) and REQ-25/26 were both marked "final."
- **Access-control posture: every one of the five sub-queries
  (people/groups/Support/RAG/recent) resolves `TenantContext
  .getActiveTenantId()` itself, independently, and fails closed (empty
  result for that section, not for the whole response) when absent —
  no shared "resolve once, trust for all five" helper that could let one
  section's tenant resolution silently leak into another's.** This
  mirrors the shipped message-search service's own fail-closed pattern
  exactly (see "AppSec correction" above) and is deliberately
  per-section rather than once-per-request: Support and RAG are
  strictly tenant-anchored (no result without an active tenant), while
  people/groups already have their own anchor-resolution logic
  (`ChatEligibilityService`'s anchor sets, which can include the
  staff-only `null` anchor) that must not be short-circuited by an
  earlier all-or-nothing tenant check. **No `STAFF_ADMIN`/`MEMBER_ADMIN`
  bypass is read anywhere in `ChatEntitySearchService`** — no method in
  this service ever calls `tenantContext.isStaff()`/`isStaffAdmin()` to
  branch into a wider result set; this is the same explicit absence the
  shipped PLAN already calls out for message search (REQ-5/REQ-18), and
  is the single most important line-item for the AppSec re-review this
  amendment is gated on.
- **Per-group cap and "see more": fixed cap of 5 per result kind in the
  initial response, `hasMore: boolean` per section (not an exact
  overflow count, to avoid a cheap extra `COUNT(*)` query per section on
  every keystroke), expand-one-group via the same endpoint with two
  additional optional params, `type` (`people`|`groups`|`rag`, Support
  has no "more" — it is at most one result) and `offset`.** Reuses the
  same endpoint rather than a distinct expand endpoint: `type`+`offset`
  present means "return only that section, offset-paginated, skip the
  other three sections' queries entirely" — cheaper than a dedicated
  endpoint duplicating the section's query, and consistent with this
  endpoint already being "one request shape, blank-query-dependent
  behavior" per the "recent places" design above. `offset`-based (not
  cursor) pagination for the expand case only, since these are small,
  already-capped, non-real-time lists (SPEC's own "Out of scope: real-
  time/live-updating" line) where `OFFSET` cost is negligible at this
  corpus size — unlike message search's cursor requirement (REQ-10 is
  explicit "cursor rather than unbounded/offset"), REQ-16-26 impose no
  such requirement on entity search, so this PLAN doesn't invent one.
- **Locale resolution reuses `ChatMessageSearchLocaleResolver`/
  `ChatSearchLocale` unchanged, only for the Support label match** — no
  new locale resolver. People/group/RAG name matching is plain
  case-insensitive substring/prefix match (SPEC's own "Out of scope:
  fuzzy/typo-tolerant matching" — exact-substring semantics, no
  language-specific stemming needed for a proper-noun/title match the
  way free-text message content needed `tsvector`).
- **Exception handling**: no new exception types needed beyond what the
  service methods it composes already throw (`ChatAccessDeniedException`
  is never thrown here — REQ-23's "omit, don't reveal" rule means an
  inaccessible match is filtered out inside each section's own query/
  filter, not surfaced and then caught). Malformed `type`/`offset` on
  the "see more" expand path reuses `ChatInvalidCursorException`'s
  sibling pattern: a new `ChatInvalidSearchExpandParamException`
  (`400`, `CHAT_SEARCH_INVALID_EXPAND_PARAM`), added to the same
  `ChatExceptionHandler`.
- **Partial-failure handling**: each of the five sections is computed
  inside its own `try`/`catch (RuntimeException)` block in
  `ChatEntitySearchService`, logged at `WARN` (actor id, section name,
  no query text — same logging discipline as message search), and
  degrades to an empty section with `hasMore: false` rather than
  failing the whole request — this is what makes the single-endpoint
  design (see above) still satisfy the frontend SPEC's REQ-30 "partial
  failure never blanks the entire dropdown" expectation despite being
  one HTTP call, not four.

### API contracts

| Method | Path | Request | Response | Status |
|---|---|---|---|---|
| `GET` | `/api/chat/search` | Query params: `q` (optional — blank/missing triggers "recent places", REQ-25), `type` (optional, `people`\|`groups`\|`rag`, only meaningful with `offset`, "see more" expand), `offset` (optional, int, only meaningful with `type`). `Accept-Language` header (optional, Support-label locale only). | `ChatEntitySearchResultDto` (blank `q`) or `ChatEntitySearchResponseDto` (non-blank `q`) — see DTOs below; the "see more" expand form (`type`+`offset` present) returns `ChatEntitySearchSectionDto` for just that one section. | `200` |

**Error cases** (same `ChatErrorResponseDto` shape, `ChatExceptionHandler`):

| Condition | Exception | Status | Code |
|---|---|---|---|
| `type` supplied without `offset` or vice versa, or `type` not one of `people`/`groups`/`rag` | `ChatInvalidSearchExpandParamException` (new) | `400` | `CHAT_SEARCH_INVALID_EXPAND_PARAM` |

No `403`/`404` for any inaccessible match of any kind — REQ-23's
"omit, never reveal" rule, same posture as REQ-3's already-shipped
precedent for message search.

**New DTOs** (`br.com.conectabyte.knowly.chat.dto`):

```java
public record ChatPersonSearchResultDto(
        Long userId, String nickname, String avatarUrl) {}
// Deliberately identical shape to CandidateUserDto (REQ-24's own
// wording: "mirroring CandidateUserDto's existing shape") -- kept as a
// distinct type rather than reusing CandidateUserDto directly, since
// the two DTOs' meaning differs (a "could I start a conversation"
// candidate vs. a "found via search" result) even though today's field
// set happens to match; a future field added to one for its own reason
// (e.g. CandidateUserDto growing an "already have a conversation"
// flag) should not silently leak onto the other's response shape.

public record ChatGroupSearchResultDto(
        Long id, String title, boolean isParticipant,
        br.com.conectabyte.knowly.chat.ChatGroupVisibility visibility) {}
// isParticipant lets the frontend distinguish "open directly" (REQ-19's
// "opens exactly as it already does from column 3 today") from
// "join/request-to-join" without a second round-trip.

public record ChatSupportSearchResultDto(Long channelId) {}
// No richer shape needed -- REQ-21 defers entirely to Support's own
// existing detail endpoints once opened; this result kind only needs
// to say "yes, you have a reachable Support channel, here's its id."

public record ChatRagConversationSearchResultDto(Long id, String title) {}

public record ChatEntitySearchSectionDto<T>(
        List<T> results, boolean hasMore) {}

public record ChatEntitySearchResponseDto(
        ChatEntitySearchSectionDto<ChatPersonSearchResultDto> people,
        ChatEntitySearchSectionDto<ChatGroupSearchResultDto> groups,
        ChatSupportSearchResultDto support, // null if no Support result
        ChatEntitySearchSectionDto<ChatRagConversationSearchResultDto> rag) {}

public record ChatRecentPlaceDto(
        Long conversationId, String kind, // "PEER_DIRECT"|"PEER_GROUP"|"SUPPORT"|"RAG"
        String title, java.time.Instant orderingTimestamp) {}

public record ChatEntitySearchResultDto(List<ChatRecentPlaceDto> recentPlaces) {}
// Returned only for the blank-query ("recent places") case; kept as a
// distinct top-level type from ChatEntitySearchResponseDto rather than
// a fifth optional field on it, since the two are mutually exclusive
// response shapes for the same endpoint (present q vs. blank q), not a
// gradually-filled-in single shape.
```

### Dependencies

None. Every new query is either a Spring Data derived-query method or a
plain JPQL `@Query`, all already `@Filter`-trusted patterns this
codebase uses throughout; no native SQL, no new library, no new
`pom.xml` entry.

### Package/file structure

New files, same `br.com.conectabyte.knowly.chat`/`.dto`/`.exception`
packages as the shipped message-search feature, plus one new method
each on two existing services outside `chat` (`ChatEligibilityService`
already lives in `chat`; `ConversationService`/`ConversationRepository`
live in the sibling `conversation` package and gain one new method/query
each, additive only):

- `ChatEntitySearchService.java` — orchestrates the five sections,
  per-section try/catch (see above), locale resolution for the Support
  label only, builds the response DTOs.
- `ChatEntitySearchController` method — **added to the existing
  `ChatController`**, not a new controller (same reasoning the shipped
  PLAN already gives for keeping message search on `ChatController`).
- `dto/ChatPersonSearchResultDto.java`, `dto/ChatGroupSearchResultDto.java`,
  `dto/ChatSupportSearchResultDto.java`,
  `dto/ChatRagConversationSearchResultDto.java`,
  `dto/ChatEntitySearchSectionDto.java`,
  `dto/ChatEntitySearchResponseDto.java`, `dto/ChatRecentPlaceDto.java`,
  `dto/ChatEntitySearchResultDto.java` — new DTOs (see API contracts).
- `exception/ChatInvalidSearchExpandParamException.java` — new.
- `ChatEligibilityService.java` — new
  `searchEligibleDirectCandidates(User actor, String nameQuery, int
  limit)` method (additive; existing `listCandidates` refactored to
  share its `"direct"`-branch anchor-intersection logic via a new
  private helper, behavior-preserving).
- `ChatConversationService.java` — new `searchDiscoverableGroups(User
  actor, String nameQuery, int limit)` method (additive, composes
  `listDiscoverableGroups`'s existing filters with a new title
  predicate plus the participant-groups union described above); resolves
  `TenantContext.getActiveTenantId()` itself and fails closed (empty
  discoverable-groups branch, no query run) when absent, per the AppSec
  correction above. `isVisibleUnderActiveTenant` promoted from `private`
  to package-private so this method and `listConversations` share one
  implementation.
- `ChatConversationRepository.java` — new `findDiscoverableByTitle(String
  pattern, Long activeTenantId, Pageable pageable)` **explicit-`@Query`
  JPQL** method (additive), `tenant_id = :activeTenantId` written into
  the query text itself (AppSec correction above) — not a Spring Data
  derived query and not left to `@Filter` alone. Class-level Javadoc
  documents the `TenantFilterAspect` staff-no-active-tenant gotcha
  explicitly.
- `ChatParticipantRepository.java` — reuses existing `findByUserId`, no
  change; the caller (`ChatConversationService.searchDiscoverableGroups`)
  applies the shared `isVisibleUnderActiveTenant` tenant check to its
  results in Java, **before** the title filter and **before** unioning
  with the discoverable-groups query result (AppSec correction above) —
  title-filtering itself still happens in the service, not the
  repository, since that list is typically small per user.
- `SupportTicketService.java` — new `findOwnOrClaimableChannel(User
  actor, Long activeTenantId)` method (additive, read-only, composes
  two existing lookups).
- `conversation/ConversationService.java` — new `searchOwn(User owner,
  Long tenantId, String titleQuery, int limit)` method (additive);
  resolves and fails closed on `TenantContext.getActiveTenantId()`
  itself, same pattern as `searchDiscoverableGroups` above.
- `conversation/ConversationRepository.java` — new
  `searchByOwnerAndTitle(Long ownerId, Long tenantId, String pattern,
  Pageable pageable)` **explicit-`@Query` JPQL** method (additive,
  replaces the originally-proposed derived-query method per the AppSec
  correction above), `c.tenant.id = :tenantId` written into the query
  text itself alongside `c.owner.id = :ownerId` and the title predicate
  — not left to `@Filter` alone. Class-level Javadoc documents the same
  `TenantFilterAspect` gotcha as `ChatConversationRepository`'s new
  method above.
- `UserProfileRepository.java` — new name-prefilter JPQL query for
  people search (`WHERE LOWER(full_name) LIKE LOWER(:pattern) AND
  deletedAt IS NULL`), explicit `deletedAt IS NULL` guard restated per
  the non-blocking correction above.
- `ChatExceptionHandler.java` — one new `@ExceptionHandler` method.

### Testing strategy

Same AppSec-mandated regression class as the shipped message-search
feature (see above), applied once per new result kind, plus the
cross-cutting "recent places" merge and partial-failure cases:

**Unit** (`ChatEntitySearchServiceTest`, Mockito):

- Each of the five sections independently mocked-and-verified to call
  its underlying service/repository method with the caller's actual
  identity/tenant, never a client-supplied value.
- Partial-failure: one section's mocked dependency throws, assert the
  other four sections still populate and the thrown section degrades to
  `hasMore: false` / empty, not a 500.
- `type`+`offset` validation: missing one of the pair, or an
  out-of-enum `type`, throws `ChatInvalidSearchExpandParamException`
  before any repository call.

**Integration** (`ChatEntitySearchControllerIntegrationTest`,
Testcontainers Postgres):

- **REQ-19 (groups)**: a query matches (a) a group the caller already
  participates in, (b) a `PUBLIC` group not yet joined, (c) a
  `REQUEST_TO_JOIN` group not yet joined — all three present in results
  with correct `isParticipant`; a matching `PRIVATE` group the caller
  isn't in is absent. Cross-tenant companion: a same-titled `PUBLIC`
  group in a different tenant than the caller's active tenant is absent.
- **AppSec-required regression — participant-groups union cross-tenant
  isolation (Gap 1 fix)**: a caller who is a current participant of
  same-titled groups in two different tenants (Tenant 1 active in
  session, Tenant 2 not) searching for that title gets back only the
  Tenant 1 group, never the Tenant 2 one — this is the exact gap flagged
  in AppSec re-review; the parameterized REQ-19 case above (different
  visibility states) is not sufficient to catch this specifically since
  it doesn't exercise the participant-groups union branch across two
  tenants for the *same* caller.
- **AppSec-required regression — JPQL exposure under
  `TenantFilterAspect`'s staff-no-active-tenant bypass (Gap 2 fix)**: a
  `STAFF`/`STAFF_ADMIN` caller with **no active tenant selected**
  searching by a group title that matches `PUBLIC`/`REQUEST_TO_JOIN`
  groups in two different tenants gets zero group results (not both
  tenants' matches) — asserts `findDiscoverableByTitle`'s explicit
  `tenant_id = :activeTenantId` predicate (not the session-level
  `@Filter`, which `TenantFilterAspect` disables in exactly this state)
  is what's actually doing the scoping. Companion, identical shape, for
  RAG: a `STAFF`/`STAFF_ADMIN` caller with no active tenant and their own
  title-matching RAG conversations in two different tenants gets zero
  RAG results via this endpoint, not a merged cross-tenant list —
  confirms `ConversationRepository.searchByOwnerAndTitle`'s explicit
  `tenant.id = :tenantId` predicate is doing the same job. Both cases
  must be run with `tenantContext.isStaff()` true and no active tenant
  in session specifically (not just "no active tenant" generically),
  since that's the precise state `TenantFilterAspect` special-cases and
  the state the original PLAN's self-audit incorrectly assumed was safe.
- **REQ-20 (people)**: a name-matching user who shares no tenant/staff
  anchor with the caller is absent from results (dedicated fixture, same
  shape as the shipped `listCandidates` test already uses).
- **REQ-21 (Support)**: a member with an open channel gets it back for
  a "Suporte"/"Support" query in both `en` and `pt-BR` `Accept-Language`;
  a caller with no channel and no support permission gets no Support
  result; a `STAFF_ADMIN` with no support permission and no claimed
  ticket also gets no Support result (confirms REQ-18's "only Support's
  own existing role-based visibility governs this, no blanket staff
  grant").
- **REQ-22 (RAG)**: a title-matching RAG conversation owned by another
  user in the same tenant is absent; the caller's own is present;
  cross-tenant companion identical in shape to message search's own.
- **REQ-18/AppSec (no oversight bypass, all four kinds)**: a
  `STAFF_ADMIN`/`MEMBER_ADMIN` caller with no participant row, no
  membership, and no Support permission gets zero people/group/RAG
  results beyond what their own real anchors would allow — explicit
  regression test mirroring the shipped message-search REQ-5 test,
  covering every new result kind in the same parameterized shape.
- **No-active-tenant fail-closed (all sections)**: a caller with no
  active tenant in session gets empty groups/Support/RAG sections (and
  a people section scoped to only their staff-only anchor, if any, per
  `ChatEligibilityService`'s existing anchor rules) — not a 500, not an
  unfiltered scan.
- **REQ-25/26 (recent places)**: a caller with a mix of chat and RAG
  conversations gets a merged, correctly-ordered list; a chat
  conversation they've since left/been removed from/that's archived or
  soft-deleted is absent (reuses the shipped feature's own fixture
  helpers); a RAG conversation belonging to another user is absent even
  if it would otherwise sort into the caller's recent-places window.
- **REQ-23 (non-revealing omission)**: for each kind, an inaccessible
  match returns the same shape as "no match at all" (empty section /
  absent Support), asserted indistinguishable, mirroring REQ-3's already-
  shipped precedent.
- Pre-existing regression companions: reuses the same Testcontainers
  Postgres setup and fixture builders as
  `ChatMessageSearchControllerIntegrationTest` and
  `chat-group-membership-management`'s helpers.

### AppSec re-review (2026-08-10) — two blocking gaps found and fixed

First-pass AppSec review of this amendment found two blocking
cross-tenant scoping gaps, both now fixed in place above (not left as
open items — this section records what was found and fixed, for the
re-review):

- **Gap 1 (fixed)**: the participant-groups union branch (Groups,
  above) originally cited `chatParticipantRepository.findByUserId` as
  "already an existing, `@Filter`-trusted repository method" — wrong,
  `ChatParticipant` carries no `@Filter(TenantFilter)`/tenant column at
  all, so a same-titled group in a second tenant the caller also
  participates in would have matched. Fixed by reusing
  `ChatConversationService`'s own existing `isVisibleUnderActiveTenant`
  check (the same one `listConversations` already applies), promoted to
  package-private, applied to every `findByUserId` row before the title
  filter and before unioning with the discoverable-groups results. See
  the "Groups" bullet's "AppSec correction" and the new dedicated
  regression test above.
- **Gap 2 (fixed)**: the two new JPQL queries (`findDiscoverableByTitle`
  for groups, the RAG title query) originally relied on Hibernate's
  `@Filter` alone for tenant scoping — insufficient because
  `TenantFilterAspect` is a global `@Around` advice that disables
  `TenantFilter` session-wide for any `STAFF`/`STAFF_ADMIN` caller with
  no active tenant selected, regardless of whether the calling code ever
  reads `isStaff()` itself. Fixed by giving both queries their own
  explicit `tenant_id`/`tenant.id = :tenantId` bind parameter in the
  query text (same discipline the shipped native message-search query
  already uses, just applied to JPQL here), with the calling service
  resolving `TenantContext.getActiveTenantId()` and failing closed
  (empty result, no query run) when absent — see the "Groups"/"RAG
  conversations" bullets' "AppSec correction" and the two new dedicated
  regression tests above.
- **Non-blocking, also fixed**: the people-search name-prefilter JPQL
  query didn't restate `deletedAt IS NULL` the way `listCandidates`'s
  existing path implicitly gets it from `findAllByDeletedAtIsNull()` —
  re-introduction risk for the 2026-08-04 "soft-deleted user must never
  surface as a chat candidate" fix. Guard added explicitly to the new
  query.
- **Confirmed clean by AppSec, unchanged**: people-search eligibility
  re-derivation, Support scoping, the RAG recent-places merge design
  (the REQ-26 correction above), parameterized binding throughout, and
  the frontend rendering/CSRF posture this PLAN assumes.
- **Both remaining gates are now closed (2026-08-10):**
  - The REQ-26 correction (recent places merges two existing sources
    instead of one) was confirmed against SPEC intent by the orchestrator
    on the user's behalf — a Tier 2 technical correction, not a product-
    scope change (it still avoids any new backend query/persisted
    recency signal, REQ-26's own stated rationale), so it did not require
    looping back to the user a second time.
  - The re-review of Gap 1, Gap 2, and the non-blocking guard was
    performed by a fresh AppSec pass (2026-08-10) and returned a clean
    **PASS** — all three fixes verified structurally sound (the
    participant-groups tenant check is inseparable from the query
    pipeline, both JPQL queries carry explicit tenant bind parameters
    immune to `TenantFilterAspect`'s staff-no-active-tenant bypass, and
    the `deletedAt IS NULL` guard was confirmed present), with no
    regression in the previously-passing pieces. **TASKS.md generation
    for all three amended documents (this one, and both frontend PLANs)
    was cleared to proceed on this basis**, and has since happened.

## Amended (2026-08-11, membership-precedence generalization) — REQ-5 completion (final v3)

> Implements SPEC.md's "Amended (2026-08-11, membership-precedence
> generalization) — REQ-5 completion (final v3)" (REQ-5r–REQ-5v), the
> locked, authoritative source of truth. **No prior v3 draft exists
> anywhere in this PLAN.md** — the "final v2"/"staff-admin-context-
> agnostic" model (SPEC.md's now-superseded "Amended (2026-08-11,
> staff-admin/staff-membership correction) — REQ-5 completion (final
> v2)") was rejected before any PLAN section for it was ever written, so
> there is nothing to mark superseded here beyond noting that rejection
> explicitly. The section immediately above ("context-boundary
> correction — final") remains the last **implemented** state (commit
> `8e1c225`) and is confirmed, by re-reading
> `ChatMessageSearchService.java` (current file, 328 lines) and
> `ChatMessageSearchRepository.java`, to still be exactly what's in the
> code today: `activeTenantId` resolved first; tenant-present branches on
> `isActiveMemberAdminOf` (`TENANT_UNRESTRICTED` vs.
> `PARTICIPANT_AND_DISCOVERABLE`); staff-scope branches on
> `isStaffAdmin()` (`STAFF_SCOPE_UNRESTRICTED`) then `isStaff()`
> (`PARTICIPANT_AND_DISCOVERABLE`, unbound); else fail-closed. This is
> the delta from that confirmed-current state to final v3, scoped as
> narrowly as possible.

**What changes vs. what stays the same.**

- **New first step inside the tenant-present branch (`activeTenantId.isPresent()`),
  before the existing `isActiveMemberAdminOf` check**: look up whether
  the actor holds *any* active `TenantMembership` in `activeTenantId`
  at all (not just `MEMBER_ADMIN`) — reuse
  `tenantMembershipRepository.findByUserAndActiveTrue(actor)` (already
  injected, already the exact call `isActiveMemberAdminOf` makes) rather
  than adding a second repository method; filter for
  `m.getTenant().getId().equals(tenantId) && m.isActive()` to get
  `Optional<TenantMembership>`. **Why:** REQ-5s requires membership
  presence, not just `MEMBER_ADMIN`, to gate the entire branch — a
  `MEMBER` row must also short-circuit past any staff check, which the
  current code already does implicitly (its `else` branch *is* the
  `PARTICIPANT_AND_DISCOVERABLE` fragment) but only by accident of there
  being no staff-fallback branch to skip; v3 makes that skip explicit
  because a staff-fallback branch is being added beside it.
- **If membership is present** → branch exactly as today, unchanged:
  `MEMBER_ADMIN` → `TENANT_UNRESTRICTED`; anything else (`MEMBER`) →
  `PARTICIPANT_AND_DISCOVERABLE`. **No new SQL fragment, no changed bind
  parameters, no changed method signatures for either query path.**
  `isActiveMemberAdminOf` can be reused as-is by evaluating it on the
  already-fetched `Optional<TenantMembership>` (`.map(m -> m.getRole()
  == MembershipRole.MEMBER_ADMIN).orElse(false)`) instead of
  re-querying — same result, one query instead of two.
- **If membership is absent for `activeTenantId`** → **new `else`
  sub-branch**, added as a sibling to the existing membership branch,
  not replacing it:
  - `tenantContext.isStaffAdmin()` → reuse `TENANT_UNRESTRICTED`
    (`searchTenantUnrestrictedPt`/`En`) bound to `activeTenantId` —
    **the same fragment `MEMBER_ADMIN` gets**, per REQ-5s(a)/REQ-5t: its
    SQL (`AND cc.tenant_id = :activeTenantId`) has no caller-role
    predicate, so it is correct to reuse verbatim rather than clone.
  - else if `tenantContext.isStaff()` (non-admin) holds
    `GlobalPermission.TENANT_ACT_AS_ANY` → same `TENANT_UNRESTRICTED`
    fragment, same binding. **Permission check**: call
    `globalPermissionService.hasPermission(actor,
    GlobalPermission.TENANT_ACT_AS_ANY)` directly — **not**
    `TenantService`'s private `requireStaff` helper, which throws
    `PermissionDeniedException` on failure; this path must fail closed
    (empty result), not throw, per this service's existing convention
    (the no-active-tenant/not-staff branch already returns an empty
    `ChatMessageSearchPageDto` rather than throwing) and per REQ-5s(d).
    Requires injecting `GlobalPermissionService` into
    `ChatMessageSearchService`'s constructor (new dependency on an
    existing bean — no new class, no `pom.xml` change).
  - else (no membership, not `STAFF_ADMIN`, `STAFF` without
    `TENANT_ACT_AS_ANY`) → fail closed: `logSearch(...)` +
    `new ChatMessageSearchPageDto(List.of(), null)`, mirroring the
    existing no-active-tenant/not-staff fail-closed branch exactly
    (REQ-5s(e), documented as unreachable in ordinary operation but
    handled defensively).
- **Staff-scope branch (`activeTenantId` empty) is unchanged, confirmed
  explicitly**: `isStaffAdmin()` → `STAFF_SCOPE_UNRESTRICTED`;
  `isStaff()` → `PARTICIPANT_AND_DISCOVERABLE` unbound; else fail
  closed. No membership concept applies here (REQ-5v) — nothing in this
  amendment touches this `else if`/`else` chain at all.
- **The `IllegalStateException` non-null invariant on `tenantId` stays
  exactly as-is, unmoved.** It guards `Optional#isPresent()` producing a
  non-null value at the top of the tenant-present branch, before any
  role/membership logic runs; the new membership lookup and its two
  sibling role checks are all downstream of that guard and all consume
  the same already-validated `tenantId`, so none of them introduce a new
  null-vs-non-null ambiguity for it to catch. No adjustment needed.
- **No new DECISIONS.md entry required.** This is a refinement of the
  same "resolve context before role" precedent already established by
  the context-boundary correction above, now additionally requiring
  "resolve membership before role, within a resolved tenant context" —
  a narrowing of an existing precedent's application, not a new
  cross-cutting pattern. `TENANT_ACT_AS_ANY`'s use as a staff fallback
  when no membership exists also has direct precedent in
  `TenantService.getTenantForStaffAccess`-style methods (grep
  `TENANT_ACT_AS_ANY` in `TenantService.java`, lines 226/273) — reused,
  not invented.

**AppSec re-review scope — fifth pass on this code path, precisely
bounded:**

- **New, needs review**: (1) the membership lookup's filter predicate
  (`tenant match + isActive()`) correctly identifies *any* membership
  role, not just `MEMBER_ADMIN`, and cannot be satisfied by a stale/
  inactive row; (2) the new `STAFF_ADMIN`/`TENANT_ACT_AS_ANY` fallback
  sub-branch is reachable *only* when the membership lookup returned
  empty — confirm no code path evaluates it before the membership check
  runs; (3) the `TENANT_ACT_AS_ANY` permission check uses
  `globalPermissionService.hasPermission` directly (fails closed to
  empty results) and not `requireStaff` (which throws) — a throwing path
  here would be a behavior change SPEC.md does not ask for; (4) the new
  `GlobalPermissionService` constructor dependency is read-only and
  introduces no new write/side-effect surface.
- **Provably unchanged, do not re-review**: the staff-scope
  (no-active-tenant) branch in its entirety; the `TENANT_UNRESTRICTED`
  and `PARTICIPANT_AND_DISCOVERABLE` SQL fragments themselves (only
  their existing bind sites gain a second, third caller — same
  fragment, same query text); the four-category discoverability/
  eligibility machinery (`additionalVisibleConversationIds*`,
  `ChatEligibilityService`); the `IllegalStateException` invariant
  (unmoved, unmodified); cursor/pagination/logging/DTO mapping.

**TASKS.md generation is blocked until this AppSec pass returns a clean
PASS** — same gate this feature's prior three corrections all went
through.
