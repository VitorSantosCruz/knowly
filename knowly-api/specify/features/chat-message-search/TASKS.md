# TASKS — chat-message-search (backend)

> Atomic, sequential, verifiable tasks derived from PLAN.md.
> Each task should be small enough for a single implementation iteration.
> TDAD: Red (failing test) then Green (minimal code) per task pair.

**Context already done, not re-listed below:** `ChatConversation`/
`ChatParticipant`/`ChatMessage`/`ChatController`/`ChatExceptionHandler`/
`ChatCursor`/`ChatErrorResponseDto`/`DeletionConfirmationLocaleResolver`/
`TenantContext`/`TenantFilterAspect` all already exist and ship
unchanged from prior features — this feature only adds new files
alongside them per PLAN.md, never reimplements them.

**AppSec-approved, no open findings** — the PLAN.md in this folder is
the *post-correction* version (the `cc.tenant_id = :activeTenantId`
fail-closed predicate and the deliberate absence of a staff-no-active-
tenant bypass are both already baked into it). This file can be
executed end to end with no further PLAN-level gate expected
mid-implementation, aside from the explicit AppSec-required regression
tests it calls out below.

## Migration and schema

- [ ] 1. Write a migration/schema test (mirrors prior features'
      schema-assertion convention) asserting `chat_messages
      .content_tsv_pt`/`content_tsv_en` exist as generated `tsvector`
      columns and `idx_chat_messages_content_tsv_pt`/
      `idx_chat_messages_content_tsv_en` GIN indexes exist, per PLAN's
      schema block (Red — migration doesn't exist yet).
- [ ] 2. Write `V34__add_chat_message_search.sql` exactly per PLAN's
      schema block (Green for task 1).
- [ ] 3. Write a test confirming the migration's `ALTER TABLE`
      backfills `content_tsv_pt`/`content_tsv_en` for pre-existing
      `chat_messages` rows with no separate backfill step (seed a row
      before the migration runs in the Testcontainers fixture, or
      assert against an existing fixture row post-migration) (Red only
      if the generated-column backfill doesn't happen automatically;
      otherwise Green, confirming PLAN's "no backfill step needed"
      claim).
- [ ] 4. Fix the migration if task 3 exposed a gap (Green), or confirm
      none needed.

## Locale enum and resolver (REQ-13–15)

- [ ] 5. Write a unit test asserting `ChatSearchLocale` has exactly
      `PT`, `EN` (Red).
- [ ] 6. Implement `ChatSearchLocale.java` (Green for task 5).
- [ ] 7. Write `ChatMessageSearchLocaleResolverTest` — table-driven
      over `Accept-Language` values (`pt`, `pt-BR`, `pt-PT`, `en`,
      `en-US`, missing, empty, malformed/garbage, `fr` i.e. neither pt
      nor en), matching whatever case matrix
      `DeletionConfirmationLocaleResolverTest` already covers (Red).
- [ ] 8. Implement `ChatMessageSearchLocaleResolver.java`,
      byte-for-byte identical resolution logic to
      `DeletionConfirmationLocaleResolver` but returning
      `ChatSearchLocale`, plain `@Component`, no bean registration
      (Green for task 7).

## New exception types

- [ ] 9. Write a test asserting `ChatBlankSearchQueryException` and
      `ChatInvalidSearchDateRangeException` exist as `RuntimeException`
      subclasses matching the existing exception classes' shape (Red).
- [ ] 10. Implement both exception classes in
      `br.com.conectabyte.knowly.chat.exception` (Green for task 9).

## Response DTOs

- [ ] 11. Write a test asserting `ChatMessageSearchResultDto` and
      `ChatMessageSearchPageDto` exist with exactly the fields from
      PLAN's API contracts section (Red).
- [ ] 12. Implement both DTOs as records in
      `br.com.conectabyte.knowly.chat.dto` (Green for task 11).

## `ChatMessageSearchRepository`

- [ ] 13. Write a `ChatMessageSearchRepositoryTest` (Testcontainers):
      given two conversations in two different tenants with
      matching-content messages, and a caller who is a current
      participant in both, `searchPt`/`searchEn` bound with
      `activeTenantId` for Tenant 1 returns only Tenant 1's matches
      (Red — repository/query doesn't exist yet).
- [ ] 14. Implement `ChatMessageSearchRepository.java` with
      `searchPt(...)`/`searchEn(...)` native `@Query` methods per
      PLAN's structural predicate (participant EXISTS, tenant id,
      `kind IN (...)`, archived/deleted-state, message
      `deleted_at IS NULL`, optional `senderId`/`conversationId`/date
      filters, cursor `id <` predicate), including the class-level
      Javadoc warning that `@Filter` does not apply to native SQL here
      (Green for task 13).
- [ ] 15. Write a `ChatMessageSearchRepositoryTest`: a former
      participant (left/removed) of a conversation no longer matches
      via `searchPt`/`searchEn`, while a current participant of a
      different conversation with matching content still does (Red).
- [ ] 16. Fix the EXISTS predicate if task 15 exposed a gap (Green), or
      confirm none needed.
- [ ] 17. Write a `ChatMessageSearchRepositoryTest`: a `SUPPORT`
      conversation the caller has a role in is excluded even though the
      content matches (REQ-1) (Red).
- [ ] 18. Fix the `kind IN (...)` predicate if task 17 exposed a gap
      (Green), or confirm none needed.
- [ ] 19. Write a `ChatMessageSearchRepositoryTest`: an archived
      conversation and a soft-deleted conversation the caller is still
      a participant-row-holder of are both excluded (REQ-4) (Red).
- [ ] 20. Fix the archived/deleted predicate if task 19 exposed a gap
      (Green), or confirm none needed.
- [ ] 21. Write a `ChatMessageSearchRepositoryTest`: `senderId`,
      `conversationId`, `dateFrom`/`dateTo` each narrow results
      correctly individually and in a 2-3 case combined-filter matrix,
      with `null` passed through for unset filters (Red).
- [ ] 22. Fix the optional-filter predicates if task 21 exposed a gap
      (Green), or confirm none needed.
- [ ] 23. Write a `ChatMessageSearchRepositoryTest`: given a
      `chat_conversations` row with `tenant_id IS NULL` (staff-only
      peer conversation) containing matching content, a tenant-scoped
      caller's search never surfaces it regardless of participant
      status (Red).
- [ ] 24. Fix the tenant predicate if task 23 exposed a gap (Green), or
      confirm none needed.
- [ ] 25. Write a `ChatMessageSearchRepositoryTest`: searching a
      Portuguese word matches a distinct conjugated/plural form of the
      same word in `content` (e.g. `"reunião"` matches a message
      containing `"reuniões"`) via `searchPt`, and the equivalent
      English case via `searchEn` (REQ-13/14) (Red).
- [ ] 26. Fix the `to_tsvector`/`websearch_to_tsquery` locale wiring if
      task 25 exposed a gap (Green), or confirm none needed.
- [ ] 27. Write a `ChatMessageSearchRepositoryTest`: given >page-size
      matching messages across conversations, paging via the `id <
      :cursor` predicate returns no overlap/no gaps across two
      consecutive pages, ordered most-recent-first (REQ-10) (Red).
- [ ] 28. Fix the cursor predicate/ordering if task 27 exposed a gap
      (Green), or confirm none needed.

## `ChatMessageSearchService`

- [ ] 29. Write a `ChatMessageSearchServiceTest` (Mockito, no
      Testcontainers): blank/missing/whitespace-only `q` throws
      `ChatBlankSearchQueryException` before the repository is ever
      called (verify zero repository interaction) (REQ-11) (Red).
- [ ] 30. Write a `ChatMessageSearchServiceTest`: `dateFrom` after
      `dateTo` throws `ChatInvalidSearchDateRangeException` before the
      repository is ever called (REQ-12) (Red, same test class).
- [ ] 31. Implement `ChatMessageSearchService.java`'s input validation
      (REQ-11/REQ-12 checks only, no query logic yet) satisfying tasks
      29-30 (Green).
- [ ] 32. Write a `ChatMessageSearchServiceTest`: given a resolved
      locale and a set of optional filters, the correct repository
      method (`searchPt` vs `searchEn`) is invoked with the correct
      bind parameters, `null` passed through for unset optional filters
      (Red).
- [ ] 33. Implement the locale-dispatch + parameter-binding logic in
      `ChatMessageSearchService`, delegating to
      `ChatMessageSearchLocaleResolver` and the repository, mapping
      rows to `ChatMessageSearchResultDto`, building the
      cursor-paginated `ChatMessageSearchPageDto` (Green for task 32).
- [ ] 34. Write a `ChatMessageSearchServiceTest`: with no active tenant
      in `TenantContext` (including when `tenantContext.isStaff()` is
      `true`), the service returns an empty `ChatMessageSearchPageDto`
      with **zero repository interaction** — no query executed at all
      (AppSec-required fail-closed behavior, deliberately no staff
      bypass) (Red).
- [ ] 35. Implement the `TenantContext.getActiveTenantId()` resolve-and-
      short-circuit step in `ChatMessageSearchService`, called before
      any repository invocation, satisfying task 34 (Green).
- [ ] 36. Write a `ChatMessageSearchServiceTest`: with an active tenant
      present, the resolved `activeTenantId` is passed through to the
      repository call as the bound `activeTenantId` parameter (Red).
- [ ] 37. Wire the resolved `activeTenantId` into the repository call
      satisfying task 36 (Green for task 35's implementation, if not
      already covered).
- [ ] 38. Write a `ChatMessageSearchServiceTest`: the service's log
      statement includes actor id, `hasQuery`, filter-presence booleans,
      and result count, and never includes the raw `q` string (assert
      via a captured log appender, not just code review) (Red).
- [ ] 39. Add the structured `INFO` logging call to
      `ChatMessageSearchService`, with the Javadoc reminder not to add
      `q` to it later (Green for task 38).

## Controller and DTO wiring

- [ ] 40. Write a `ChatControllerIntegrationTest` (Testcontainers, CSRF
      token via `obtainCsrfCookie()`): `GET
      /api/chat/messages/search?q=...` happy path returns `200` with a
      `ChatMessageSearchPageDto`-shaped body (Red).
- [ ] 41. Implement `ChatController.searchMessages`, wiring
      `@RequestParam`s (`q`, `senderId`, `conversationId`, `dateFrom`,
      `dateTo`, `cursor`, `size`) and the `Accept-Language` header to
      `ChatMessageSearchService`, no `@AuditLog` (Green for task 40).
- [ ] 42. Write a `ChatControllerIntegrationTest`: `q` blank/missing
      returns `400` with `CHAT_SEARCH_QUERY_BLANK`; `dateFrom` after
      `dateTo` returns `400` with `CHAT_SEARCH_INVALID_DATE_RANGE`;
      malformed `cursor` returns `400` with the existing
      `CHAT_INVALID_CURSOR` code (Red).
- [ ] 43. Implement the two new `@ExceptionHandler` methods in
      `ChatExceptionHandler` for `ChatBlankSearchQueryException`/
      `ChatInvalidSearchDateRangeException`, same
      `ChatErrorResponseDto` shape as every existing handler (Green for
      task 42; the `ChatInvalidCursorException` case should already be
      Green via the existing handler).
- [ ] 44. Write a `ChatControllerIntegrationTest`: a `conversationId`
      filter pointing at (a) a real conversation the caller isn't a
      participant of, (b) a nonexistent id, (c) a `SUPPORT`
      conversation, (d) an archived/soft-deleted former conversation of
      the caller's — all four return `200` with an empty/unaffected
      page, indistinguishable from each other and from "no matches"
      (REQ-3) (Red).
- [ ] 45. Fix `ChatController`/`ChatMessageSearchService` if task 44
      exposed a gap (a stray 403/404 leaking through), or confirm none
      needed (Green).

## Integration tests — core isolation and AppSec-required regressions

- [ ] 46. Write the **core isolation test**
      (`ChatMessageSearchControllerIntegrationTest`, Testcontainers,
      parameterized across all four removal modes — left / removed /
      archived / soft-deleted — as one parameterized test, not four):
      user A and user B share a group conversation with
      matching-content messages; after the removal mode is applied to
      user A, user A's search for that content returns zero results
      from that conversation, while another conversation user A is
      still a current participant of still surfaces correctly (Red).
- [ ] 47. Fix any gap task 46 exposes across service/repository (Green),
      or confirm none needed.
- [ ] 48. **AppSec-required cross-tenant regression test**: a user who
      is a current participant of conversations in two different
      tenants (Tenant 1 and Tenant 2), with matching-content messages
      in both, searching with Tenant 1 active in session returns only
      Tenant 1's matches, never Tenant 2's (Red).
- [ ] 49. **AppSec-required no-active-tenant fail-closed test**
      (companion to task 48, same test class): the same user with no
      active tenant in session gets zero results, not an unfiltered
      cross-tenant scan — including when `tenantContext.isStaff()` is
      `true`, confirming the deliberate absence of
      `TenantFilterAspect`'s staff-no-active-tenant bypass for this
      endpoint specifically (Red).
- [ ] 50. Fix any gap tasks 48-49 expose (Green), or confirm none
      needed — this is the exact AppSec-flagged gap and must not be
      left red.
- [ ] 51. Write a `ChatMessageSearchControllerIntegrationTest`
      (REQ-5/staff-no-bypass): a `STAFF_ADMIN` and a `MEMBER_ADMIN`
      user, each with zero `chat_participants` rows on a conversation
      containing matching content, get zero results from that
      conversation via this endpoint — explicit regression test against
      ever wiring in `BypassTenantFilterForOversight` or any oversight
      check here (Red).
- [ ] 52. Fix any gap task 51 exposes (Green), or confirm none needed.
- [ ] 53. Write a `ChatMessageSearchControllerIntegrationTest` (REQ-1):
      a message inside a `SUPPORT` conversation the caller has a role
      in (ticket owner or assigned staff) never appears in results,
      confirming the `kind` filter does real, independent work beyond
      the participant check (Red).
- [ ] 54. Fix any gap task 53 exposes (Green), or confirm none needed.
- [ ] 55. Write a `ChatMessageSearchControllerIntegrationTest`
      (REQ-13/14, end-to-end locale confirmation): a Portuguese-resolved
      caller (via `Accept-Language`) matches a conjugated/plural
      Portuguese word form a literal substring search would miss, and
      the equivalent for English; and supplying an arbitrary/forged
      locale-shaped query parameter has no effect, confirming REQ-14's
      "never from a client-supplied parameter" end-to-end (Red).
- [ ] 56. Fix any gap task 55 exposes (Green), or confirm none needed.
- [ ] 57. Write a `ChatMessageSearchControllerIntegrationTest` (REQ-10):
      fetch page 1, follow `nextCursor` to page 2, assert no
      overlap/no gaps against a fixture of >`size` matching messages
      spread across conversations, chronological most-recent-first
      ordering (Red).
- [ ] 58. Fix any gap task 57 exposes (Green), or confirm none needed.
- [ ] 59. Write a `ChatMessageSearchControllerIntegrationTest`
      (REQ-7/8/9): `senderId`, `conversationId`, `dateFrom`/`dateTo`
      each narrow results correctly individually and in a 2-3 case
      combined-filter matrix, at the controller/HTTP layer (Red).
- [ ] 60. Fix any gap task 59 exposes (Green), or confirm none needed.

## Wrap-up

- [ ] 61. Run `./mvnw spotless:apply` then `./mvnw verify` and confirm
      the whole suite (existing chat/tenancy/staff-RBAC tests included)
      is green.
- [ ] 62. Update `PROJECT_STATUS.md` to reflect this feature's
      completion, noting the new `chat_messages` generated `tsvector`
      columns/GIN indexes and the native-query tenant-scoping gotcha
      this feature's repository documents, so a future conversation
      doesn't have to rediscover it from the diff.

---

# Amendment (2026-08-10) — unified entity search (REQ-16–REQ-26)

> Continues numbering from task 62 above. Everything above this heading
> is already shipped and untouched. Derived from PLAN.md's "Amended
> (2026-08-10)" section plus its "AppSec re-review (2026-08-10)" fix
> section — the final, approved design, including both AppSec-mandated
> corrections (Gap 1: `isVisibleUnderActiveTenant` reuse for the
> participant-groups union; Gap 2: explicit tenant predicates on
> `findDiscoverableByTitle`/`searchByOwnerAndTitle`) and the non-blocking
> `deletedAt IS NULL` restatement.

## `ChatEligibilityService.searchEligibleDirectCandidates` (people)

- [x] 63. Write a `ChatEligibilityServiceTest`: `searchEligibleDirectCandidates(actor, nameQuery, limit)`
      returns only name-matching candidates that also satisfy the same
      anchor-intersection rule as `listCandidates`'s `"direct"` branch,
      via a shared private helper (assert both methods produce the same
      eligibility verdict for the same actor/candidate pair) (Red —
      method doesn't exist yet).
- [x] 64. Implement `searchEligibleDirectCandidates`, extracting the
      `"direct"`-branch anchor-intersection logic from `listCandidates`
      into a shared private helper both methods call, and pushing the
      name filter into a new `UserProfileRepository` query (Green for
      task 63).
- [x] 65. Write a `ChatEligibilityServiceTest`/`UserProfileRepositoryTest`:
      the new name-prefilter JPQL query includes an explicit
      `deletedAt IS NULL` guard — a soft-deleted user matching the name
      query never appears, mirroring the 2026-08-04 fix for
      `listCandidates` (Red).
- [x] 66. Add the `deletedAt IS NULL` predicate to the new
      `UserProfileRepository` query if task 65 exposes a gap (Green), or
      confirm none needed.

## `isVisibleUnderActiveTenant` promotion (shared groups tenant check)

- [x] 67. Write a `ChatConversationServiceTest`: `isVisibleUnderActiveTenant`
      is reachable (package-private or extracted helper) from a test in
      the same package and returns identical results to
      `listConversations`'s existing use of it for the same
      actor/conversation pair (Red — method is currently `private`).
- [x] 68. Promote `isVisibleUnderActiveTenant` from `private` to
      package-private (or extract a small shared `ChatTenantVisibility`
      helper, per PLAN's stated preference at implementation time),
      updating `listConversations` to keep using the same implementation
      (Green for task 67; must not change `listConversations`'s existing
      passing tests).

## `ChatConversationRepository.findDiscoverableByTitle`

- [x] 69. Write a `ChatConversationRepositoryTest` (Testcontainers):
      `findDiscoverableByTitle(pattern, activeTenantId, pageable)`
      matches `PUBLIC`/`REQUEST_TO_JOIN` groups by `title ILIKE :pattern`
      within the given tenant only — a same-titled group in a different
      tenant is excluded even when passed no other filter (Red — method
      doesn't exist yet).
- [x] 70. Implement `findDiscoverableByTitle` as an explicit-`@Query`
      JPQL method with `tenant_id = :activeTenantId` written directly
      into the query text (not left to `@Filter` alone), plus
      class-level Javadoc documenting the `TenantFilterAspect`
      staff-no-active-tenant gotcha, mirroring
      `ChatMessageSearchRepository`'s precedent (Green for task 69).

## `ConversationRepository.searchByOwnerAndTitle`

- [x] 71. Write a `ConversationRepositoryTest` (Testcontainers):
      `searchByOwnerAndTitle(ownerId, tenantId, pattern, pageable)`
      matches only the given owner's title-matching `Conversation` rows
      within the given tenant — a same-titled `Conversation` owned by
      the same user in a different tenant is excluded (Red — method
      doesn't exist yet).
- [x] 72. Implement `searchByOwnerAndTitle` as an explicit-`@Query` JPQL
      method (`SELECT c FROM Conversation c WHERE c.owner.id = :ownerId
      AND c.tenant.id = :tenantId AND LOWER(c.title) LIKE
      LOWER(:pattern) ORDER BY c.createdAt DESC`), with class-level
      Javadoc documenting the same `TenantFilterAspect` gotcha (Green
      for task 71).

## `ChatConversationService.searchDiscoverableGroups`

- [x] 73. Write a `ChatConversationServiceTest`: with no active tenant
      in `TenantContext`, `searchDiscoverableGroups(actor, nameQuery,
      limit)` returns an empty discoverable-groups result with **zero
      repository interaction** — no query executed at all (fail-closed,
      no staff bypass) (Red).
- [x] 74. Implement `searchDiscoverableGroups`'s
      `TenantContext.getActiveTenantId()` resolve-and-short-circuit step,
      called before any repository invocation (Green for task 73).
- [x] 75. Write a `ChatConversationServiceTest`: with an active tenant
      present, `searchDiscoverableGroups` composes
      `listDiscoverableGroups`'s existing `isEligible`/`!isParticipant`
      filters with the new title predicate via
      `findDiscoverableByTitle`, correctly bound with `activeTenantId`
      (Red).
- [x] 76. Implement the `findDiscoverableByTitle`-backed discoverable-set
      branch of `searchDiscoverableGroups` (Green for task 75).
- [x] 77. Write a `ChatConversationServiceTest`: a title-matching group
      the caller already participates in (via
      `chatParticipantRepository.findByUserId`) is included in results
      alongside non-participant discoverable matches, de-duplicated by
      conversation id, with `isParticipant` correctly set on each (REQ-19)
      (Red).
- [x] 78. Implement the participant-groups union branch, applying the
      title filter and unioning with the discoverable-set results,
      de-duplicated by conversation id (Green for task 77).
- [x] 79. **AppSec-required regression (Gap 1)**: write a
      `ChatConversationServiceTest`: a caller who is a current
      participant of same-titled groups in two different tenants (Tenant
      1 active in session, Tenant 2 not) searching for that title gets
      back only the Tenant 1 group from the participant-groups union
      branch, never the Tenant 2 one — asserts `findByUserId`'s
      unfiltered cross-tenant rows are narrowed by
      `isVisibleUnderActiveTenant` **before** the title filter and
      **before** unioning with the discoverable-set query (Red — this is
      the exact gap AppSec's first-pass review caught).
- [x] 80. Apply `isVisibleUnderActiveTenant` to every `findByUserId` row
      before the title filter and before unioning, if task 79 exposes a
      gap (Green) — must not be left red; this is the AppSec-mandated
      fix, not an optional hardening.

## `ChatEntitySearchService` — Support section

- [x] 81. Write a `SupportTicketServiceTest`:
      `findOwnOrClaimableChannel(actor, activeTenantId)` returns the
      member's own open channel when one exists, composing the existing
      member-channel lookup used by `requireChannelId` (Red — method
      doesn't exist yet).
- [x] 82. Implement `findOwnOrClaimableChannel`'s member-channel branch
      (Green for task 81).
- [x] 83. Write a `SupportTicketServiceTest`: for a staff caller,
      `findOwnOrClaimableChannel` composes the existing unclaimed-inbox/
      claimed-ticket visibility already used by
      `listUnclaimed`/`claim`, returning no result for a staff caller
      with no support permission and no claimed ticket (REQ-18) (Red).
- [x] 84. Implement the staff-visibility branch of
      `findOwnOrClaimableChannel` (Green for task 83).
- [x] 85. Write a `ChatEntitySearchServiceTest`: the Support section
      matches the fixed "Suporte"/"Support" label case-insensitively
      against `q`, locale-aware via the reused
      `ChatMessageSearchLocaleResolver`/`ChatSearchLocale`, for both
      `en` and `pt-BR` `Accept-Language` (Red).
- [x] 86. Implement the Support-label match + `findOwnOrClaimableChannel`
      call in `ChatEntitySearchService`, requiring
      `TenantContext.getActiveTenantId()` fail-closed the same way
      message search does (no active tenant means no Support result)
      (Green for task 85).

## `ConversationService.searchOwn` (RAG)

- [x] 87. Write a `ConversationServiceTest`: with no active tenant in
      `TenantContext`, `searchOwn(owner, tenantId, titleQuery, limit)`
      returns an empty result with **zero repository interaction**
      (fail-closed, no staff bypass) (Red).
- [x] 88. Implement `searchOwn`'s `TenantContext.getActiveTenantId()`
      resolve-and-short-circuit step (Green for task 87).
- [x] 89. Write a `ConversationServiceTest`: `searchOwn` returns only the
      given owner's title-matching conversations within the resolved
      tenant, via `searchByOwnerAndTitle`, correctly bound with both
      `ownerId` and `tenantId` (Red).
- [x] 90. Implement the `searchByOwnerAndTitle`-backed query call in
      `searchOwn` (Green for task 89).
- [x] 91. **AppSec-required regression (Gap 2, RAG half)**: write a
      `ConversationServiceTest`/`ConversationRepositoryTest`: a
      `STAFF`/`STAFF_ADMIN` caller with **no active tenant selected**
      and title-matching RAG conversations they own in two different
      tenants gets zero results via `searchOwn`, not a merged
      cross-tenant list — must be run with `tenantContext.isStaff()`
      true and no active tenant in session specifically, confirming
      `searchByOwnerAndTitle`'s explicit `tenant.id = :tenantId`
      predicate (not the session-level `@Filter`, which
      `TenantFilterAspect` disables in exactly this state) is doing the
      scoping (Red — this is the exact gap AppSec's first-pass review
      caught).
- [x] 92. Fix `searchOwn`/`searchByOwnerAndTitle` if task 91 exposes a
      gap (Green) — must not be left red; this is the AppSec-mandated
      fix, not an optional hardening.

## `ChatEntitySearchService` — groups/RAG staff-no-active-tenant regressions (Gap 2, groups half)

- [x] 93. **AppSec-required regression (Gap 2, groups half)**: write a
      `ChatConversationServiceTest`: a `STAFF`/`STAFF_ADMIN` caller with
      **no active tenant selected**, searching by a group title that
      matches `PUBLIC`/`REQUEST_TO_JOIN` groups in two different
      tenants, gets zero group results via `searchDiscoverableGroups` —
      must be run with `tenantContext.isStaff()` true and no active
      tenant in session specifically, confirming
      `findDiscoverableByTitle`'s explicit `tenant_id = :activeTenantId`
      predicate is doing the scoping (Red — already covered structurally
      by task 73's general fail-closed test, but this task asserts the
      specific staff-no-active-tenant state AppSec flagged, not just
      "any caller with no active tenant").
- [x] 94. Confirm task 73/74's fail-closed implementation already
      satisfies task 93 (Green), or fix if a staff-specific bypass path
      is found — must not be left red.

## `ChatEntitySearchService` — people/groups/Support/RAG orchestration and DTOs

- [x] 95. Write a test asserting the new DTOs
      (`ChatPersonSearchResultDto`, `ChatGroupSearchResultDto`,
      `ChatSupportSearchResultDto`, `ChatRagConversationSearchResultDto`,
      `ChatEntitySearchSectionDto`, `ChatEntitySearchResponseDto`,
      `ChatRecentPlaceDto`, `ChatEntitySearchResultDto`) exist with
      exactly the fields from PLAN's "Amended" API contracts section
      (Red).
- [x] 96. Implement all eight DTOs as records in
      `br.com.conectabyte.knowly.chat.dto` (Green for task 95).
- [x] 97. Write a test asserting `ChatInvalidSearchExpandParamException`
      exists as a `RuntimeException` subclass matching the existing
      exception classes' shape (Red).
- [x] 98. Implement `ChatInvalidSearchExpandParamException.java` (Green
      for task 97).
- [x] 99. Write a `ChatEntitySearchServiceTest`: each of the four
      matched-`q` sections (people/groups/Support/RAG) is computed by
      calling its underlying service/repository method with the
      caller's actual identity/tenant, never a client-supplied value
      (mocked, verified per section) (Red).
- [x] 100. Implement `ChatEntitySearchService`'s non-blank-`q` orchestration,
      calling `ChatEligibilityService.searchEligibleDirectCandidates`,
      `ChatConversationService.searchDiscoverableGroups`,
      `SupportTicketService.findOwnOrClaimableChannel`,
      `ConversationService.searchOwn`, each resolving
      `TenantContext.getActiveTenantId()` independently per section
      (Green for task 99).
- [x] 101. Write a `ChatEntitySearchServiceTest`: one section's mocked
      dependency throws a `RuntimeException`; assert the other three
      sections still populate and the thrown section degrades to
      `hasMore: false`/empty (Support: absent), not a 500, logged at
      `WARN` with no query text (Red).
- [x] 102. Implement the per-section `try`/`catch (RuntimeException)`
      wrapping + `WARN` logging in `ChatEntitySearchService` (Green for
      task 101).
- [x] 103. Write a `ChatEntitySearchServiceTest`: `type`+`offset`
      validation — missing one of the pair, or an out-of-enum `type`,
      throws `ChatInvalidSearchExpandParamException` before any
      repository call (Red).
- [x] 104. Implement the `type`/`offset` expand-param validation in
      `ChatEntitySearchService`, short-circuiting to a single-section
      query when both are present and valid (skipping the other three
      sections' queries entirely) (Green for task 103).
- [x] 105. Write a `ChatEntitySearchServiceTest`: with `q` blank/missing,
      the service returns a `ChatEntitySearchResultDto` merging
      `ChatConversationService.listConversations(actor)` (chat kinds)
      and `ConversationService.list(owner, activeTenantId)` (RAG kind)
      via a k-way merge on `createdAt`/`lastMessageAt`, capped at the
      fixed per-section count, falling back to id order for
      no-messages-yet chat conversations (REQ-25/26) (Red).
- [x] 106. Implement the blank-`q` "recent places" merge in
      `ChatEntitySearchService` (Green for task 105).

## `ChatController` — `GET /api/chat/search` and error handling

- [x] 107. Write a `ChatControllerIntegrationTest` (Testcontainers, CSRF
      token via `obtainCsrfCookie()`): `GET /api/chat/search?q=...`
      happy path returns `200` with a `ChatEntitySearchResponseDto`-shaped
      body; `GET /api/chat/search` with no `q` returns `200` with a
      `ChatEntitySearchResultDto`-shaped body (Red — endpoint doesn't
      exist yet).
- [x] 108. Implement the new `searchEntities` method on `ChatController`,
      wiring `q`/`type`/`offset`/`Accept-Language` to
      `ChatEntitySearchService` (Green for task 107).
- [x] 109. Write a `ChatControllerIntegrationTest`: `type` supplied
      without `offset` (and vice versa), or an out-of-enum `type`,
      returns `400` with `CHAT_SEARCH_INVALID_EXPAND_PARAM` (Red).
- [x] 110. Implement the new `@ExceptionHandler` method in
      `ChatExceptionHandler` for `ChatInvalidSearchExpandParamException`,
      same `ChatErrorResponseDto` shape as every existing handler (Green
      for task 109).
- [x] 111. Write a `ChatControllerIntegrationTest`: valid `type`+`offset`
      returns only that section's results, offset-paginated, and does
      not invoke the other three sections' underlying calls (assert via
      a spied/mocked service layer or a fixture where the other three
      sections would otherwise be non-empty) (Red).
- [x] 112. Fix `ChatController`/`ChatEntitySearchService` if task 111
      exposes a gap (Green), or confirm none needed.

## Integration tests — AppSec-required regressions and REQ coverage (full stack)

- [x] 113. Write a `ChatEntitySearchControllerIntegrationTest` (REQ-19,
      groups): a query matches (a) a group the caller already
      participates in, (b) a `PUBLIC` group not yet joined, (c) a
      `REQUEST_TO_JOIN` group not yet joined — all three present with
      correct `isParticipant`; a matching `PRIVATE` group the caller
      isn't in is absent; a same-titled `PUBLIC` group in a different
      tenant than the caller's active tenant is absent (Red).
- [x] 114. Fix any gap task 113 exposes (Green), or confirm none needed.
- [x] 115. **AppSec-required regression (Gap 1, full stack)**: write a
      `ChatEntitySearchControllerIntegrationTest`: a caller who is a
      current participant of same-titled groups in two different
      tenants (Tenant 1 active in session, Tenant 2 not) searching for
      that title via `GET /api/chat/search` gets back only the Tenant 1
      group, never the Tenant 2 one (Red — end-to-end confirmation of
      task 79/80's service-level fix, must not be left red).
- [x] 116. Fix any gap task 115 exposes (Green), or confirm none needed.
- [x] 117. **AppSec-required regression (Gap 2, full stack, groups)**:
      write a `ChatEntitySearchControllerIntegrationTest`: a
      `STAFF`/`STAFF_ADMIN` caller with no active tenant selected
      searching by a group title matching `PUBLIC`/`REQUEST_TO_JOIN`
      groups in two different tenants gets zero group results via `GET
      /api/chat/search`, asserting exactly zero results, not a merged
      cross-tenant set (Red — end-to-end confirmation of task 93/94's
      fix, must not be left red).
- [x] 118. **AppSec-required regression (Gap 2, full stack, RAG)**: write
      a `ChatEntitySearchControllerIntegrationTest`, same test class as
      task 117: a `STAFF`/`STAFF_ADMIN` caller with no active tenant
      selected and title-matching RAG conversations in two different
      tenants gets zero RAG results via `GET /api/chat/search`,
      asserting exactly zero results, not a merged cross-tenant set (Red
      — end-to-end confirmation of task 91/92's fix, must not be left
      red).
- [x] 119. Fix any gap tasks 117-118 expose (Green), or confirm none
      needed.
- [x] 120. Write a `ChatEntitySearchControllerIntegrationTest` (REQ-20,
      people): a name-matching user who shares no tenant/staff anchor
      with the caller is absent from results, same fixture shape as the
      existing `listCandidates` test (Red).
- [x] 121. Fix any gap task 120 exposes (Green), or confirm none needed.
- [x] 122. Write a `ChatEntitySearchControllerIntegrationTest` (REQ-21,
      Support): a member with an open channel gets it back for a
      "Suporte"/"Support" query in both `en` and `pt-BR`
      `Accept-Language`; a caller with no channel and no support
      permission gets no Support result; a `STAFF_ADMIN` with no support
      permission and no claimed ticket also gets no Support result
      (REQ-18) (Red).
- [x] 123. Fix any gap task 122 exposes (Green), or confirm none needed.
- [x] 124. Write a `ChatEntitySearchControllerIntegrationTest` (REQ-22,
      RAG): a title-matching RAG conversation owned by another user in
      the same tenant is absent; the caller's own is present;
      cross-tenant companion identical in shape to message search's own
      (Red).
- [x] 125. Fix any gap task 124 exposes (Green), or confirm none needed.
- [x] 126. Write a `ChatEntitySearchControllerIntegrationTest` (REQ-18/
      AppSec, no oversight bypass, all four kinds): a `STAFF_ADMIN`/
      `MEMBER_ADMIN` caller with no participant row, no membership, and
      no Support permission gets zero people/group/RAG results beyond
      what their own real anchors would allow — parameterized across all
      four kinds in one test class, mirroring the shipped message-search
      REQ-5 test (Red).
- [x] 127. Fix any gap task 126 exposes (Green), or confirm none needed.
- [x] 128. Write a `ChatEntitySearchControllerIntegrationTest`
      (no-active-tenant fail-closed, all sections): a caller with no
      active tenant in session gets empty groups/Support/RAG sections
      (and a people section scoped to only their staff-only anchor, if
      any) — not a `500`, not an unfiltered scan (Red).
- [x] 129. Fix any gap task 128 exposes (Green), or confirm none needed.
- [x] 130. Write a `ChatEntitySearchControllerIntegrationTest` (REQ-25/26,
      recent places): a caller with a mix of chat and RAG conversations
      gets a merged, correctly-ordered list; a chat conversation
      they've since left/been removed from/that's archived or
      soft-deleted is absent (reuses the shipped feature's fixture
      helpers); a RAG conversation belonging to another user is absent
      even if it would otherwise sort into the caller's recent-places
      window (Red).
- [x] 131. Fix any gap task 130 exposes (Green), or confirm none needed.
- [x] 132. Write a `ChatEntitySearchControllerIntegrationTest` (REQ-23,
      non-revealing omission): for each of the four kinds, an
      inaccessible match returns the same shape as "no match at all"
      (empty section / absent Support), asserted indistinguishable,
      mirroring REQ-3's already-shipped precedent (Red).
- [x] 133. Fix any gap task 132 exposes (Green), or confirm none needed.

## Wrap-up (amendment)

- [x] 134. Run `./mvnw spotless:apply` then `./mvnw verify` and confirm
      the whole suite (existing chat/tenancy/staff-RBAC tests plus every
      task above) is green.
## Amendment (2026-08-10, role-based scoping) — REQ-5e–REQ-5j

> Continues numbering from task 135 above. Derived from PLAN.md's "Amended
> (2026-08-10, role-based scoping) — REQ-5e–REQ-5j implementation"
> section (AppSec-approved, GO-WITH-CHANGES, both required changes
> applied: the branch-3 `IllegalStateException` invariant and the
> `LIMIT 100` cap on `findDiscoverableIds`/`findDiscoverableIdsPlatformWide`).

- [x] 136. Add `ChatConversationRepository.findDiscoverableIds(tenantId)`/
      `findDiscoverableIdsPlatformWide()` (native `@Query`, explicit
      `LIMIT 100`), with tests confirming `PUBLIC`/`REQUEST_TO_JOIN`-only
      matching, tenant scoping, and that `PRIVATE` groups are never
      returned.
- [x] 137. Restructure `ChatMessageSearchRepository` into `BASE_PREDICATE`
      plus three scope fragments (`searchUnrestrictedPt`/`En`,
      `searchTenantUnrestrictedPt`/`En`, `searchScopedPt`/`En` with the
      new `additionalVisibleConversationIds` bind parameter), updating
      the existing repository tests to the renamed `searchScoped*`
      methods.
- [x] 138. Implement `ChatMessageSearchService.search()`'s role-based
      branching (REQ-5e-REQ-5j precedence order: `STAFF_ADMIN` ->
      tenant-active `MEMBER_ADMIN` -> tenant `MEMBER` (with the
      AppSec-required `IllegalStateException` invariant on a null
      `activeTenantId`) -> staff-no-tenant -> fail closed), computing
      `additionalVisibleConversationIds` via `ChatEligibilityService`,
      and rewriting the class Javadoc that claimed the service "never
      reads `isStaff()`/`isStaffAdmin()`" (now false).
- [x] 139. Update `ChatMessageSearchServiceTest` for the new constructor
      dependencies (`TenantMembershipRepository`, `ChatConversationRepository`,
      `ChatEligibilityService`) and all five branches, including REQ-5j's
      stale-membership-in-a-different-tenant case.
- [x] 140. Update/add `ChatMessageSearchControllerIntegrationTest` cases:
      REQ-5e (`STAFF_ADMIN` unrestricted, with/without active tenant),
      REQ-5f (staff-no-tenant now gets scoped results, not empty),
      REQ-5g (active-tenant `MEMBER_ADMIN` unrestricted-in-tenant),
      REQ-5j (a `MEMBER_ADMIN`'s stale membership/participant row in a
      different tenant never crosses tenant boundaries), and REQ-5i (a
      non-admin's search never matches an unjoined `PRIVATE` group but
      does match a discoverable `PUBLIC` group).
- [x] 141. Run `./mvnw spotless:apply` then the scoped chat-message-search
      test suite and confirm green.
- [ ] 142. Update `PROJECT_STATUS.md` to reflect this amendment's
      completion.

## Wrap-up (original)

- [x] 135. Update `PROJECT_STATUS.md` to reflect this amendment's
      completion, noting the new `GET /api/chat/search` endpoint, the two
      AppSec-fixed cross-tenant scoping gaps (participant-groups union,
      JPQL staff-no-active-tenant exposure) and where their regression
      tests live, and the REQ-26 "recent places merges two sources"
      design correction, so a future conversation doesn't have to
      rediscover any of it from the diff.
