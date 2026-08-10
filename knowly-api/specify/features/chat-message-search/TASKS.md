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
