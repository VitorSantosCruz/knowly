# TASKS — internal-team-chat (backend)

> Atomic, sequential, verifiable tasks derived from PLAN.md.
> Each task should be small enough for a single implementation iteration.

**Context already done, not re-listed below:** the CSRF exemption
narrowing (`SecurityConfig`'s `ignoringRequestMatchers` reduced from
`/api/tenants/**` to the exact `/api/tenants/active` path) was already
implemented and committed separately, ahead of this feature, per the
AppSec review note in `PLAN.md` and the `DECISIONS.md` 2026-07-31 entry.
`SupportChannelController`'s tasks below assume that exemption is
already narrow and simply send a CSRF token like every other mutating
`/api/tenants/**` endpoint — no SecurityConfig work appears in this file.

## Migration and entities

- [ ] 1. Write `V20MigrationTest` (or extend the existing Flyway
      migration-checksum/schema test convention) asserting
      `chat_conversations`, `chat_participants`, `chat_messages`,
      `support_tickets`, and their `_aud` counterparts exist with the
      columns/indexes/constraints from `PLAN.md`'s schema block (Red —
      migration doesn't exist yet).
- [ ] 2. Write `V20__create_chat_tables.sql` (new package
      `br.com.conectabyte.knowly.chat` gets no SQL of its own; migration
      lives in `src/main/resources/db/migration/`), including the
      partial unique indexes (`ux_chat_conversations_support_channel`,
      `ux_support_tickets_one_open_per_channel`) and the `_aud` tables
      matching `V4`/`V11`'s pattern (Green for task 1).
- [ ] 3. Write a unit test asserting `ChatConversationKind` has exactly
      `PEER_DIRECT`, `PEER_GROUP`, `SUPPORT` and `SupportTicketStatus`
      has exactly `OPEN`, `ASSIGNED`, `CLOSED` (Red).
- [ ] 4. Implement `ChatConversationKind.java`, `SupportTicketStatus.java`
      (Green for task 3).
- [ ] 5. Write a repository-level test (`@DataJpaTest` or Testcontainers,
      mirroring `Conversation`'s own entity test) persisting a
      `ChatConversation` with `tenant_id = NULL` and asserting it is
      still readable (staff-only conversation carries no tenant) (Red —
      entity doesn't exist).
- [ ] 6. Implement `ChatConversation.java` (with `@Filter(TenantFilter
      .NAME)`, `@Audited`), `ChatParticipant.java`, `ChatMessage.java`
      (not `@Audited`, per PLAN), `SupportTicket.java` (`@Audited`), and
      their repositories (`ChatConversationRepository`,
      `ChatParticipantRepository`, `ChatMessageRepository`,
      `SupportTicketRepository`) — no custom queries yet beyond
      Spring-Data-derived ones (Green for task 5).
- [ ] 7. Write a test asserting a `ChatConversation` row with
      `tenant_id = NULL` is invisible under any active-tenant filter
      context and visible with no active tenant (staff-only, structural
      invisibility per PLAN's `NULL`-never-matches-`:tenantId` reasoning)
      — mirrors `GlobalAccessGroup`'s existing no-filter precedent test
      shape (Red only if the `@Filter` condition is written wrong;
      otherwise already Green, confirming the schema decision rather
      than adding code).
- [ ] 8. Fix `@Filter` condition if task 7 failed; otherwise no
      production change (Green).

## Permissions

- [ ] 9. Write a test asserting `Permission.SUPPORT_CHANNEL_VIEW` and
      `GlobalPermission.STAFF_SUPPORT_HANDLE` exist (Red).
- [ ] 10. Add `SUPPORT_CHANNEL_VIEW` to `Permission.java` and
      `STAFF_SUPPORT_HANDLE` to `GlobalPermission.java`, no column-width
      change needed per PLAN (Green for task 9).

## `ChatEligibilityService` (REQ-3/4/5)

- [ ] 11. Write `ChatEligibilityServiceTest`: staff user with no active
      membership in tenant `T` → ineligible for `T`'s member-only group
      and for a 1:1/group with a member of `T` (Red).
- [ ] 12. Write `ChatEligibilityServiceTest`: staff user with an active
      membership in tenant `T` → eligible for `T`'s member-only group,
      treated as an ordinary peer (Red, same test class as 11).
- [ ] 13. Write `ChatEligibilityServiceTest`: plain member (no staff
      role) → ineligible for a staff-only (`tenant_id = NULL`) group
      (Red).
- [ ] 14. Write `ChatEligibilityServiceTest`: the same staff user is
      eligible for tenant `T`'s member-only group and ineligible for
      tenant `U`'s in the same test run, proving per-tenant evaluation,
      not a global flag (Red — this is the acceptance criterion's
      explicit dual-tenant assertion).
- [ ] 15. Implement `ChatEligibilityService.isEligible(User candidate,
      Long tenantIdAnchor)` (fresh `TenantMembershipRepository` lookup,
      never trusting request body input) satisfying tasks 11-14 (Green).
- [ ] 16. Write `ChatEligibilityServiceTest.listCandidates` tests for
      `scope=direct`, `scope=group&tenantId=T`, `scope=group-staff-only`
      — each returning the correct candidate set per the same rule (Red).
- [ ] 17. Implement `ChatEligibilityService.listCandidates(scope,
      tenantId)` reusing `isEligible` (Green for task 16).

## `ChatConversationService` — create/list/send (REQ-1/2/6/7)

- [ ] 18. Write `ChatConversationServiceTest`: any authenticated user
      (all four roles) can create a 1:1 with an eligible peer, no
      permission check involved (Red).
- [ ] 19. Write `ChatConversationServiceTest`: creating a 1:1/group with
      an ineligible participant (per `ChatEligibilityService`) is
      rejected (400-shaped exception) (Red).
- [ ] 20. Implement `ChatConversationService.createConversation(actor,
      kind, tenantId, title, participantUserIds)` — validates every
      participant via `ChatEligibilityService`, persists
      `ChatConversation` + one `ChatParticipant` row per participant,
      resolves nicknames for REQ-6 (Green for tasks 18-19).
- [ ] 21. Write `ChatConversationServiceTest`: a genuine participant can
      list/get their own 1:1 and group conversations; a non-participant,
      non-admin caller is rejected from `getConversation` (403/404)
      (Red).
- [ ] 22. Implement `ChatConversationService.listConversations(actor)` /
      `getConversation(actor, id)` — participant-membership check only,
      no admin branch yet (Green for task 21).
- [ ] 23. Write `ChatConversationServiceTest`: a genuine participant of a
      group can send a message; a non-participant is rejected from
      `sendMessage` (403) — explicitly for a group, per REQ-7 (Red).
- [ ] 24. Implement `ChatConversationService.sendMessage(actor,
      conversationId, content)` — checks `chat_participants` membership
      only (path (1) from PLAN, no admin bypass here) (Green for task
      23).
- [ ] 25. Write `ChatConversationServiceTest`: neither `STAFF_ADMIN` nor
      `MEMBER_ADMIN` can open/read a 1:1 conversation they aren't a
      participant of — admin overrides never apply to 1:1s (REQ-2)
      (Red only if the service already has a bypass leaking into 1:1;
      otherwise Green, confirming isolation before the bypass branch is
      added in the next section).
- [ ] 26. Confirm task 25 passes with no production change yet (Green).

## Admin look-in / `TenantFilterAspect` extension (REQ-5a/5b)

- [ ] 27. Write a `TenantFilterAspectTest` (or new
      `BypassTenantFilterForOversightTest`): a method annotated
      `@BypassTenantFilterForOversight` inside an active
      `@Transactional` boundary has the tenant filter disabled for its
      duration regardless of `TenantContext`'s active tenant, and the
      filter is re-enabled afterward exactly as the aspect already does
      for `isStaff() && activeTenantId.isEmpty()` (Red — annotation and
      aspect branch don't exist yet).
- [ ] 28. Add `@BypassTenantFilterForOversight` marker annotation and the
      additional condition check to `TenantFilterAspect`'s existing
      `@Around` advice (Green for task 27) — no second/manual
      `disableFilter` call anywhere else in the codebase, per PLAN's
      corrected design.
- [ ] 29. Write `ChatConversationServiceTest`: a `STAFF_ADMIN` who is
      **not** a participant of a group (staff-only or member-only, any
      tenant) can still `getConversation`/read message history via the
      look-in branch; after the read, `chat_participants` for that group
      is unchanged (no row added) and a fresh `ChatEligibilityService`
      re-query for that `STAFF_ADMIN` against that group is unaffected
      (Red).
- [ ] 30. Implement the `STAFF_ADMIN` branch (2) of
      `getConversation`/`listMessages`'s access resolution, annotated
      `@BypassTenantFilterForOversight`, re-deriving
      `TenantContext.isStaffAdmin()` fresh per call, never writing a
      participant row (Green for task 29).
- [ ] 31. Write `ChatConversationServiceTest`: an active `MEMBER_ADMIN`
      of tenant `T` who is **not** a participant of a `PEER_GROUP`
      member-only group anchored to `T` can still read it; same
      unchanged-participant-list and unaffected-eligibility assertions
      as task 29 (Red).
- [ ] 32. Implement branch (3) — active `MEMBER_ADMIN`-of-target-tenant
      look-in, restricted to `PEER_GROUP` with non-null `tenant_id`
      matching the admin's currently-active-administered tenant, fresh
      `TenantMembershipRepository` lookup, same
      `@BypassTenantFilterForOversight` method (Green for task 31).
- [ ] 33. Write `ChatConversationServiceTest`: a `MEMBER_ADMIN` is
      rejected from a member-only group of a tenant they do **not**
      currently administer, even if they hold `MEMBER_ADMIN` elsewhere;
      and rejected from any staff-only group under any circumstance
      (Red only if branch (3) is over-scoped; otherwise Green,
      confirming the scoping from task 32).
- [ ] 34. Confirm task 33 passes with no further code change (Green), or
      fix branch (3)'s scoping if it failed.
- [ ] 35. Write `ChatConversationServiceTest`: repeat task 25's 1:1
      isolation assertion for both admin roles again now that branches
      (2)/(3) exist, confirming the look-in never reaches `sendMessage`
      (REQ-7's explicit scope limit) — a `STAFF_ADMIN`/`MEMBER_ADMIN`
      look-in read succeeds but a subsequent `sendMessage` call from the
      same non-participant admin is rejected (Red only if look-in leaked
      into send path; otherwise Green).
- [ ] 36. Confirm task 35 passes (Green).
- [ ] 37. Write a test asserting `@AuditLog("chat.group.oversight_view")`
      is recorded for both admin branches' reads, distinct from the
      normal `chat.conversation.view` a genuine participant triggers
      (Red).
- [ ] 38. Add the two distinct `@AuditLog` annotations/action strings to
      the relevant service methods (Green for task 37).

## Support channel lifecycle (`SupportTicketService`, REQ-8–16)

- [ ] 39. Write `SupportTicketServiceTest`: `openTicket` for a member
      with no existing Support Channel lazily creates one (single
      participant row: the owning member) and one `OPEN` ticket inside
      it (Red).
- [ ] 40. Implement `SupportTicketService.openTicket(memberUser,
      tenantId)` — idempotent get-or-create channel (`INSERT ... ON
      CONFLICT DO NOTHING`-style under the unique constraint), then
      create the ticket (Green for task 39).
- [ ] 41. Write `SupportTicketServiceTest`: calling `openTicket` again
      while a ticket is still open (not `CLOSED`) is rejected (REQ-10)
      (Red).
- [ ] 42. Add the check-then-throw guard (belt to the DB partial index's
      suspenders) to `openTicket` (Green for task 41).
- [ ] 43. Write `SupportTicketServiceTest`: opening a new ticket after
      the prior one closed succeeds and lands in the **same** channel,
      not a new one (Red).
- [ ] 44. Confirm task 43 passes with no further change (Green) — the
      get-or-create logic from task 40 already guarantees this; task
      exists to lock in the assertion, not add code.
- [ ] 45. Write `SupportTicketServiceTest`: a staff user with
      `STAFF_SUPPORT_HANDLE` can `claim` an unclaimed (`OPEN`) ticket →
      ticket becomes `ASSIGNED`, assignee set; a staff user without the
      permission is rejected (403, via `@RequiresGlobalPermission`)
      (Red).
- [ ] 46. Implement `SupportTicketService.claim(staffUser, ticketId)`,
      gated by `@RequiresGlobalPermission(STAFF_SUPPORT_HANDLE)`, with a
      check-then-throw 409 if already claimed (belt-and-suspenders
      alongside the partial unique index) (Green for task 45).
- [ ] 47. Write `SupportTicketServiceTest`: claiming an already-`ASSIGNED`
      ticket is rejected with a conflict (Red).
- [ ] 48. Confirm task 47's conflict path from task 46 covers it (Green,
      or fix the check ordering if not).
- [ ] 49. Write `SupportTicketServiceTest`: `transfer` by the current
      assignee to a target holding `STAFF_SUPPORT_HANDLE` moves
      assignment; `transfer` attempted by a non-assignee, or to a target
      lacking the permission, is rejected (400/403) (Red).
- [ ] 50. Implement `SupportTicketService.transfer(staffUser, ticketId,
      toStaffUserId)` re-checking both sides' permission/assignee status
      at transfer time, not trusting the original claim (Green for task
      49).
- [ ] 51. Write `SupportTicketServiceTest`: `close` by the assignee marks
      the ticket `CLOSED`, retained unchanged in history; `close`
      attempted twice is rejected (409) (Red).
- [ ] 52. Implement `SupportTicketService.close(staffUser, ticketId)`
      (Green for task 51).
- [ ] 53. Write `SupportTicketServiceTest`/`ChatConversationServiceTest`:
      sending a message associated with a `CLOSED` ticket, or attempting
      to reopen one, is rejected (REQ-16) (Red).
- [ ] 54. Add the closed-ticket terminality guard in
      `sendMessage`/wherever reopen would be attempted — application-
      level only, no DB trigger, per PLAN's documented (AppSec-flagged,
      accepted-as-is) precedent (Green for task 53).

## Support channel send/read rights (REQ-11–13, 17–19)

- [ ] 55. Write `ChatConversationServiceTest`: for a `SUPPORT`
      conversation, only the owning member (while ticket `OPEN`/
      `ASSIGNED`) and the currently-`ASSIGNED` staff assignee can
      `sendMessage`; every other staff user (including one holding
      `STAFF_SUPPORT_HANDLE`) is rejected from sending (REQ-13/18) (Red).
- [ ] 56. Implement the `SUPPORT`-kind branch of `sendMessage` per PLAN's
      ticket-derived (not `chat_participants`-derived) rule — the
      assignee is deliberately never added as a `chat_participants` row
      (Green for task 55).
- [ ] 57. Write `ChatConversationServiceTest`: any staff holding
      `STAFF_SUPPORT_HANDLE` can read a channel's full history
      (unclaimed or claimed, REQ-11/13) regardless of assignment; a
      staff user without the permission is rejected from reading (Red).
- [ ] 58. Write `ChatConversationServiceTest`: any tenant member holding
      `SUPPORT_CHANNEL_VIEW` in that tenant can read another member's
      Support Channel history (REQ-17), gated via the existing
      `@RequiresPermission`/`PermissionAspect` (`MEMBER_ADMIN` bypass
      included) (Red).
- [ ] 59. Implement the `SUPPORT`-kind read-access branch (owning member
      always; `STAFF_SUPPORT_HANDLE` staff always;
      `SUPPORT_CHANNEL_VIEW` tenant members) satisfying tasks 57-58
      (Green).
- [ ] 60. Write a test: after claiming (or being transferred) a ticket,
      the assignee's read includes every prior ticket in the channel
      (open or closed), not only the newly-claimed ticket's own messages
      (REQ-12c/14) (Red only if history read is accidentally scoped to
      one ticket; otherwise Green, confirming the channel-not-ticket
      history model from task 59).
- [ ] 61. Confirm task 60 passes, or fix scoping if it filtered by
      ticket instead of channel (Green).
- [ ] 62. Write a test asserting `@AuditLog` entries for
      `support.ticket.open`/`claim`/`transfer`/`close` (Red).
- [ ] 63. Add the four `@AuditLog` annotations to `SupportTicketService`
      (Green for task 62).
- [ ] 64. Write a test asserting participant/sender nicknames (REQ-6/19)
      are resolved from the identity/profile model on every
      `ChatMessageDto`/`ChatConversationSummaryDto`/`SupportTicketDto`
      returned by the services above (Red only where a DTO mapper
      omitted it; otherwise Green).
- [ ] 65. Fix any DTO mapper found missing nickname resolution in task
      64 (Green), or confirm none needed fixing.

## Cursor pagination (REQ-20/21/22)

- [ ] 66. Write a unit test for cursor encode/decode round-trip
      (`base64(String.valueOf(id))` ↔ `id`) and for malformed-cursor
      rejection (Red).
- [ ] 67. Implement the cursor encode/decode helper (Green for task 66).
- [ ] 68. Write a unit test: page-size request above 100 is clamped to
      100, not rejected, for both `before` and `after` modes (REQ-22)
      (Red).
- [ ] 69. Implement server-side clamping (default 30, max 100) in the
      request-handling layer shared by both modes (Green for task 68).
- [ ] 70. Write `ChatMessageRepositoryTest` (Testcontainers): seed a
      conversation with messages past one page; `before` mode returns
      `id < cursor` ordered `id DESC`, omitting `before` returns the
      newest page first (Red).
- [ ] 71. Implement `ChatMessageRepository.findByConversationIdAndCursor`
      (`before` direction) used by both peer conversations and support
      channels via one shared query, per PLAN (Green for task 70).
- [ ] 72. Write `ChatMessageRepositoryTest`: `after` mode returns
      `id > cursor` ordered `id ASC`, used for polling deltas (Red).
- [ ] 73. Extend the shared repository query/service method to support
      `after` mode (Green for task 72).
- [ ] 74. Write an integration test: seed a conversation past one page,
      fetch page 1, insert a new message, fetch the next `before` page —
      no gap or duplicate row across the two fetches (REQ-21's
      "load more" correctness against concurrent inserts) (Red only if
      cursor semantics leak an off-by-one; otherwise Green).
- [ ] 75. Fix any off-by-one found in task 74 (Green), or confirm none
      needed.
- [ ] 76. Write a `ChatMessagePageDtoTest`/controller-level test:
      `nextCursor` in `before` mode is the next-older page's cursor (or
      `null` when no older page exists); in `after` mode it's the newest
      id seen in the response (or omitted when empty) — per PLAN's DTO
      contract (Red).
- [ ] 77. Implement `ChatMessagePageDto` mapping satisfying task 76
      (Green).

## Controllers and DTOs

- [ ] 78. Write `ChatControllerIntegrationTest` (Testcontainers, CSRF
      token obtained per `AuthControllerIntegrationTest`'s
      `obtainCsrfCookie()` convention): `POST /api/chat/conversations`
      happy path (201) and ineligible-participant path (400) (Red).
- [ ] 79. Implement `ChatController.createConversation` +
      `CreateChatConversationRequestDto` +
      `ChatConversationSummaryDto` mapping (Green for task 78).
- [ ] 80. Write `ChatControllerIntegrationTest`: `GET
      /api/chat/conversations` (list) and `GET
      /api/chat/conversations/{id}` (detail, 200/403/404) (Red).
- [ ] 81. Implement the two `GET` endpoints + `ChatConversationDetailDto`
      (Green for task 80).
- [ ] 82. Write `ChatControllerIntegrationTest`: `GET
      /api/chat/conversations/{id}/messages` with `before`/`after`/`size`
      query params, and `POST .../messages` happy path + 403 (not a
      sender) + 409 (closed ticket case deferred to
      `SupportChannelController` below) (Red).
- [ ] 83. Implement both message endpoints wiring into the cursor
      pagination and `sendMessage` service methods (Green for task 82).
- [ ] 84. Write `ChatControllerIntegrationTest`: `GET
      /api/chat/eligible-participants?scope=...` for all three `scope`
      values (Red).
- [ ] 85. Implement the endpoint as a thin wrapper over
      `ChatEligibilityService.listCandidates` + `CandidateUserDto`
      (Green for task 84).
- [ ] 86. Write `ChatExceptionHandlerTest`: `ChatAccessDeniedException`
      and `SupportTicketConflictException` map to 403/409 respectively,
      mirroring `ConversationExceptionHandler`'s shape (Red).
- [ ] 87. Implement `ChatAccessDeniedException`,
      `SupportTicketConflictException`, `ChatExceptionHandler` (Green
      for task 86).
- [ ] 88. Write `SupportChannelControllerIntegrationTest` (CSRF token
      obtained the same way — this controller is nested under
      `/api/tenants/**` and does **not** inherit any CSRF exemption per
      the already-narrowed `SecurityConfig`): `POST
      /api/tenants/{tenantId}/support/tickets` happy path (201) and
      already-open conflict (409) (Red).
- [ ] 89. Implement `SupportChannelController.openTicket` +
      `SupportTicketDto` mapping, delegating to
      `SupportTicketService.openTicket` (Green for task 88).
- [ ] 90. Write `SupportChannelControllerIntegrationTest`: `GET
      .../support/tickets/unclaimed` (200, 403 without
      `STAFF_SUPPORT_HANDLE`), `POST .../claim`, `POST .../transfer`,
      `POST .../close` — happy paths and their documented error statuses
      (Red).
- [ ] 91. Implement the four remaining ticket-lifecycle endpoints +
      `SupportTicketSummaryDto` (Green for task 90).
- [ ] 92. Write `SupportChannelControllerIntegrationTest`: `GET
      .../support/members/{memberUserId}/channel` and its `/messages`
      sub-resource (200/403/404), and `POST .../messages` (201/403/409)
      (Red).
- [ ] 93. Implement the three channel-scoped endpoints, reusing the same
      `ChatConversationDetailDto`/`ChatMessagePageDto`/`ChatMessageDto`
      shapes as the peer-chat endpoints (Green for task 92).

## Tenant isolation and cross-cutting regression

- [ ] 94. Write an integration test: a staff-only conversation
      (`tenant_id = NULL`) is unreachable/invisible under any active
      tenant filter context, from any endpoint (Red only if isolation
      regressed; otherwise Green, final confirmation of task 7's schema
      decision end-to-end through the controllers).
- [ ] 95. Write an integration test: a member-only group of tenant `A`
      returns 403/404 (not a leaked 200) to a member of tenant `B` with
      no membership in `A` (Red only if leaked; otherwise Green).
- [ ] 96. Run the full `./mvnw spotless:apply` then `./mvnw verify` and
      confirm the whole suite is green, including every existing
      integration test updated for the already-narrowed CSRF exemption
      (`TenantManagementIntegrationTest`,
      `StaffRbacIntegrationTest`, `MembershipAcceptanceIntegrationTest`,
      `ConversationControllerIntegrationTest`,
      `ArticleControllerIntegrationTest`,
      `ArticleUploadSizeLimitIntegrationTest`) — this feature's new
      controllers must not need any exemption of their own, per PLAN's
      AppSec resolution.
- [ ] 97. Update `PROJECT_STATUS.md` to reflect this feature's
      completion, and flag the deferred SSE-via-RabbitMQ real-time-push
      follow-up (already recorded in `DECISIONS.md`, 2026-07-31 entry)
      as a candidate for a future SPEC, not implemented here.
</content>
</invoke>
