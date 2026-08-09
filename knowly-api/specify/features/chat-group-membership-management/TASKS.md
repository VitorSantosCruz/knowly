# TASKS — chat-group-membership-management (backend)

> Atomic, sequential, verifiable tasks derived from PLAN.md.
> Each task should be small enough for a single implementation iteration.
> TDAD: Red (failing test) then Green (minimal code) per task pair.

**Context already done, not re-listed below:** `ChatConversation`/
`ChatParticipant`/`ChatMessage`/`ChatEligibilityService`/
`ChatOversightConversationLoader`/`BypassTenantFilterForOversight`/
`SoftDeleteFilter`/`SoftDeleteFilterAspect`/`AllowDeletedForOversight`/
`PageResponseDto` all already exist and ship unchanged from
`internal-team-chat` and `soft-delete-default-filter` — this feature
only extends them per PLAN.md, never reimplements them.

**AppSec-approved, no open findings** (approval covers both the
original PLAN and the REQ-30a eligibility-re-derivation correction, plus
the frontend-contract-change follow-up) — this file can be executed
end to end with no further PLAN-level gate expected mid-implementation.

## Migration and schema

- [x] 1. Write a migration/schema test (mirrors `internal-team-chat`'s
      `V20` schema-assertion convention) asserting
      `chat_participants.is_admin`, `chat_conversations.visibility`,
      `chat_conversations.archived_at`, `chat_conversations.deleted_at`,
      `chat_participants.deleted_at`, `chat_messages.deleted_at`, and
      the new `chat_join_requests`/`chat_join_requests_aud` tables (with
      `ux_chat_join_requests_pending`, `ix_chat_join_requests_conversation`,
      `ix_chat_conversations_discovery`) all exist with the
      types/defaults/constraints from `PLAN.md`'s schema block (Red —
      migration doesn't exist yet).
- [x] 2. Write `V31__chat_group_membership_management.sql` exactly per
      `PLAN.md`'s schema block, including the `_aud` column additions
      for `chat_conversations_aud`/`chat_participants_aud` and the new
      `chat_join_requests_aud` table (Green for task 1).
- [x] 3. Write a unit test asserting `Permission.CHAT_GROUP_DELETE`
      exists and `Permission.CHAT_GROUP_DELETE.viewDependency()` is
      empty (Red).
- [x] 4. Add `CHAT_GROUP_DELETE` to `Permission.java`, no
      `viewDependency()` entry, per PLAN's Tier 2 naming decision (Green
      for task 3).
- [x] 5. Write a unit test asserting `ChatGroupVisibility` has exactly
      `PRIVATE`, `REQUEST_TO_JOIN`, `PUBLIC` and `ChatJoinRequestStatus`
      has exactly `PENDING`, `APPROVED`, `REJECTED` (Red).
- [x] 6. Implement `ChatGroupVisibility.java`, `ChatJoinRequestStatus.java`
      (Green for task 5).

## Entity/filter extension (`is_admin`, `visibility`, `archived_at`, soft-delete)

- [x] 7. Write a repository-level test (Testcontainers): a newly
      persisted `ChatParticipant` defaults `isAdmin = false`; a newly
      persisted `ChatConversation` defaults `visibility = PRIVATE` and
      `archivedAt = null` (Red — fields don't exist yet).
- [x] 8. Add `isAdmin` to `ChatParticipant.java`; add `visibility`
      (`ChatGroupVisibility`), `archivedAt` (`Instant`) to
      `ChatConversation.java` (Green for task 7).
- [x] 9. Write a test (mirrors `soft-delete-default-filter`'s own entity
      tests): a soft-deleted `ChatConversation` (`deletedAt` set) is
      invisible to a standard derived-query/JPQL read inside a plain
      `@Transactional` method with no `@AllowDeletedForOversight`, and
      visible when that annotation is present on the calling method
      (Red — `ChatConversation` doesn't carry `SoftDeleteFilter` yet).
- [x] 10. Add `deletedAt` field + `@FilterDef`/`@Filter(SoftDeleteFilter
      .NAME)` to `ChatConversation.java`, alongside its existing
      `TenantFilter` pairing (mirrors `Conversation`/`AccessGroup`'s
      existing dual-filter shape) (Green for task 9).
- [x] 11. Repeat tasks 9-10 for `ChatParticipant.java` (Red then Green).
- [x] 12. Repeat tasks 9-10 for `ChatMessage.java`, noting it has no
      `_aud` table so only the base-table `deletedAt` column/filter pair
      is added (Red then Green).
- [x] 13. Write a test confirming every *existing* `internal-team-chat`
      query path (`ChatConversationRepository.findByIdRespectingFilter`,
      `ChatParticipantRepository.findByConversationId`,
      `ChatMessageRepository`'s cursor queries) now excludes soft-deleted
      rows by default with zero code change at the call site — proves
      the "structural, not opt-in" guarantee from PLAN's soft-delete
      section (Red only if `SoftDeleteFilterAspect`'s existing pointcut
      somehow doesn't reach these methods; otherwise Green, confirming
      the mechanism reuse).
- [x] 14. Fix any gap found in task 13 (Green), or confirm none needed.

## Group admin role (REQ-1/2/3/4/5/6/7/54)

- [x] 15. Write a `ChatConversationServiceTest`: creating a `PEER_GROUP`
      conversation makes the creator's `ChatParticipant` row
      `isAdmin = true`; every other initial participant's row is
      `isAdmin = false` (Red).
- [x] 16. Set `isAdmin = true` for the creator's row in
      `ChatConversationService.createConversation` (Green for task 15).
- [x] 17. Write a `ChatConversationServiceTest`: a private
      `requireGroupAdmin(actor, conversation)`-style check accepts a
      current admin, rejects a current non-admin participant, and
      rejects a non-participant entirely — scoped to the *specific*
      conversation passed in, not "is admin of anything" (Red).
- [x] 18. Implement the `requireGroupAdmin` helper in
      `ChatConversationService`, re-querying
      `ChatParticipantRepository.findByConversationIdAndUserId(...)
      .filter(ChatParticipant::isAdmin)` at request time — never cached,
      never derived from tenant/platform role (REQ-6) (Green for task
      17).
- [x] 19. Write a `ChatConversationServiceTest`: `promoteToAdmin` by a
      current admin, targeting a current non-admin participant,
      succeeds and grants `isAdmin = true`; a group may end up with more
      than one admin (REQ-2) (Red).
- [x] 20. Write `ChatConversationServiceTest` negatives for
      `promoteToAdmin`: caller not a current admin (REQ-3), target not a
      current participant (REQ-4), target already an admin (REQ-5) are
      each rejected with the documented error codes (Red, same test
      class as 19).
- [x] 21. Implement `ChatConversationService.promoteToAdmin(actor,
      conversationId, targetUserId)` satisfying tasks 19-20, using
      `requireGroupAdmin` (Green).
- [x] 22. Write a `ChatConversationServiceTest`: `POST .../admins/
      {userId}` writes `@AuditLog("chat.group.admin_promote")` (Red).
- [x] 23. Add the `@AuditLog` annotation to `promoteToAdmin` (Green for
      task 22).
- [x] 24. Write a `ChatParticipantRepositoryTest`: given 3+ participants
      with distinct `joinedAt` values, `findFirstByConversationId...`
      (the succession query) returns the earliest-`joinedAt` row; given
      two participants sharing the exact same `joinedAt`, it returns the
      one with the lower `user.id`, run twice against the same seed to
      confirm determinism (Red).
- [x] 25. Implement the custom `@Query` succession-selection method on
      `ChatParticipantRepository` (`ORDER BY joined_at ASC, user_id ASC
      LIMIT 1` over remaining participants) satisfying task 24 (Green).
- [x] 26. Write a `ChatConversationServiceTest`: when a group's sole
      admin leaves (REQ-18) while 2+ other participants remain, the
      longest-tenured remaining participant is automatically promoted
      (REQ-54) within the same transaction as the leave (Red).
- [x] 27. Write a `ChatConversationServiceTest`: the same scenario via
      `removeParticipant` (an admin removing the sole other admin) also
      triggers succession (Red, same test class as 26).
- [x] 28. Write a `ChatConversationServiceTest`: a group with 2+ current
      admins, one of whom leaves, does **not** trigger succession (at
      least one admin already remains) (Red, same test class).
- [x] 29. Implement `handleAdminDepartureIfNeeded(conversationId)` (count
      remaining participants → no-op if zero; count remaining admins →
      no-op if nonzero; else promote via task 25's query) and call it
      from the end of the removal path shared by `removeParticipant`/
      `leaveConversation` (Green for tasks 26-28).
- [x] 30. Write a test asserting `handleAdminDepartureIfNeeded`'s
      successful promotion writes a distinct
      `chat.group.admin_succession` audit event via `AuditEventWriter`
      directly (non-caller-initiated, mirrors the existing
      `chat.group.oversight_view` self-invocation pattern) (Red).
- [x] 31. Add the direct `AuditEventWriter` call to
      `handleAdminDepartureIfNeeded` (Green for task 30).

## Adding participants (REQ-8–12)

- [x] 32. Write a `ChatConversationServiceTest`: a current group admin
      adding one or more eligible, non-duplicate user ids succeeds,
      re-deriving `ChatEligibilityService.isEligible` per id (REQ-8),
      and the new participants are **not** admins by default (Red).
- [x] 33. Write `ChatConversationServiceTest` negatives for
      `addParticipants`: a non-admin caller is rejected outright
      (REQ-9); a request mixing valid, already-participant, and
      ineligible ids processes the valid ones and reports the rest as
      rejected with distinct reasons, without duplicating rows
      (REQ-10/11, partial-success semantics per PLAN); a request where
      **every** id is rejected returns 400-shaped failure with nothing
      added (Red, same test class).
- [x] 34. Write a `ChatConversationServiceTest`: `addParticipants`
      against a non-existent conversation (404), a non-`PEER_GROUP`
      conversation, or an archived/deleted group (409-shaped
      `ChatGroupStateConflictException`) is rejected (REQ-12) (Red).
- [x] 35. Implement `ChatConversationService.addParticipants(actor,
      conversationId, userIds)` returning the
      `ChatAddParticipantsResultDto {conversation, rejected[]}` shape,
      satisfying tasks 32-34 (Green).
- [x] 36. Write a test asserting `@AuditLog("chat.group.participant_add")`
      is recorded (Red).
- [x] 37. Add the `@AuditLog` annotation (Green for task 36).
- [x] 38. **403-matrix (AppSec follow-up note a):** write a
      `ChatConversationServiceTest` proving `addParticipants` rejects
      (1) an admin of a *different* group attempting to add to this one,
      and (2) a genuine non-admin *participant of this exact group*
      attempting to add — both distinct from the plain "non-participant"
      case already covered by task 33 (Red only if `requireGroupAdmin`'s
      scoping from task 18 is wrong; otherwise Green, locking in the
      matrix explicitly per PLAN's testing-strategy note).
- [x] 39. Fix `requireGroupAdmin`/`addParticipants` if task 38 exposed a
      scoping gap (Green), or confirm none needed.

## Removing participants (REQ-13–17)

- [x] 40. Write a `ChatConversationServiceTest`: a current group admin
      removing a current, non-last participant immediately revokes
      their read/write access, and clears their admin status if they
      had one (triggering REQ-54 per tasks 26-29 above) (Red).
- [x] 41. Write `ChatConversationServiceTest` negatives for
      `removeParticipant`: non-admin caller (REQ-14), target not a
      current participant (REQ-15), removal that would leave zero
      participants (REQ-16, rejected as a precondition check before the
      delete — never a race-prone post-check), non-existent/non-
      `PEER_GROUP`/archived/deleted conversation (REQ-17) (Red, same
      test class).
- [x] 42. Implement `ChatConversationService.removeParticipant(actor,
      conversationId, targetUserId)` satisfying tasks 40-41 (Green).
- [x] 43. Write a test asserting `@AuditLog("chat.group.participant_remove")`
      is recorded (Red).
- [x] 44. Add the `@AuditLog` annotation (Green for task 43).
- [x] 45. **403-matrix (AppSec follow-up note a):** write a
      `ChatConversationServiceTest` for `removeParticipant` proving
      rejection for (1) an admin of a different group, (2) a genuine
      non-admin participant of this exact group (Red only if scoping is
      wrong; otherwise Green).
- [x] 46. Fix any gap from task 45 (Green), or confirm none needed.

## Leaving a group (REQ-18–21) and empty-group archival (REQ-43/44/45/46/47)

- [x] 47. Write a `ChatConversationServiceTest`: any current participant
      (admin or not) can `leaveConversation`, regardless of their own
      admin status — never gated by `requireGroupAdmin` (REQ-18) (Red).
- [x] 48. Write `ChatConversationServiceTest` negatives: a non-participant
      caller is rejected (REQ-19); a non-existent/non-`PEER_GROUP`
      target is rejected (REQ-21) (Red, same test class).
- [x] 49. Implement `ChatConversationService.leaveConversation(actor,
      conversationId)` — delete the caller's own participant row,
      trigger `handleAdminDepartureIfNeeded` if participants remain
      (Green for tasks 47-48).
- [x] 50. Write a `ChatConversationServiceTest`: the **last** remaining
      participant leaving a `PRIVATE` or `REQUEST_TO_JOIN` group sets
      `archivedAt` (REQ-43) — no succession fires (zero participants
      remain, not just zero admins) (Red).
- [x] 51. Write a `ChatConversationServiceTest`: the last remaining
      participant leaving a `PUBLIC` group leaves `archivedAt = null`
      (REQ-47) — still discoverable, still directly joinable (Red, same
      test class).
- [x] 52. Implement the `archiveIfEmptied` helper (post-leave participant
      count check, branch on `visibility`) called from
      `leaveConversation` (Green for tasks 50-51).
- [x] 53. Write a test asserting archival writes
      `@AuditLog`/direct-`AuditEventWriter` `"chat.group.archive"`,
      non-caller-initiated like `chat.group.admin_succession` (Red).
- [x] 54. Add the direct `AuditEventWriter` call to `archiveIfEmptied`
      (Green for task 53).
- [x] 55. Write a `ChatConversationServiceTest`: an archived **tenant**
      group's history is readable by any user holding the `STAFF` role
      (not only `STAFF_ADMIN`) (REQ-44); an archived **staff** group's
      history is readable only by `STAFF_ADMIN` (REQ-45); a former
      participant with no other qualifying role is rejected from both
      after archival (REQ-46) — extends `internal-team-chat`'s existing
      REQ-5a/REQ-5b test fixtures (Red).
- [x] 56. Extend `ChatConversationService.requireReadableConversation`
      with the archived-group branch (checked only after the existing
      participant/oversight-look-in branches, using
      `ChatOversightConversationLoader`/`@BypassTenantFilterForOversight`
      unchanged, per PLAN's explicit reuse decision — no new bypass
      annotation) satisfying task 55 (Green).
- [x] 57. Write a `ChatConversationServiceTest`: `leaveConversation`
      against an already-archived/deleted group is a moot no-op per
      SPEC (archived groups have zero participants by construction, so
      there's no one left to leave) — assert the natural "not a current
      participant" rejection path (REQ-19) already covers this case,
      with no special-casing needed (Red only if a special case is
      missing; otherwise Green).
- [x] 58. Fix if task 57 exposed a gap (Green), or confirm none needed.

## Group visibility (REQ-22–26)

- [x] 59. Write a `ChatConversationServiceTest`: a current group admin
      can change visibility to any of the other two modes, taking effect
      immediately for discovery/join behavior (REQ-22/23) (Red).
- [x] 60. Write `ChatConversationServiceTest` negatives for
      `changeVisibility`: non-admin caller (REQ-24), requested mode
      identical to current (REQ-25, `CHAT_VISIBILITY_UNCHANGED`),
      archived or deleted target (REQ-26, `ChatGroupStateConflictException`)
      are each rejected (Red, same test class).
- [x] 61. Implement `ChatConversationService.changeVisibility(actor,
      conversationId, newVisibility)` satisfying tasks 59-60 (Green).
- [x] 62. Write a test asserting `@AuditLog("chat.group.visibility_change")`
      is recorded (Red).
- [x] 63. Add the `@AuditLog` annotation (Green for task 62).
- [x] 64. **403-matrix (AppSec follow-up note a):** write a
      `ChatConversationServiceTest` for `changeVisibility` proving
      rejection for (1) an admin of a different group, (2) a genuine
      non-admin participant of this exact group (Red only if scoping is
      wrong; otherwise Green).
- [x] 65. Fix any gap from task 64 (Green), or confirm none needed.

## Discovery of `REQUEST_TO_JOIN`/`PUBLIC` groups (REQ-27/28)

- [x] 66. Write a `ChatConversationRepositoryTest`: `findDiscoverable
      (Pageable)` returns only non-archived, non-deleted `PEER_GROUP`
      rows with `visibility IN (REQUEST_TO_JOIN, PUBLIC)` — a `PRIVATE`
      group, an archived group, and a `PEER_DIRECT`/`SUPPORT` row are
      all excluded (Red).
- [x] 67. Implement `ChatConversationRepository.findDiscoverable(Pageable)`
      (Green for task 66).
- [x] 68. Write a `ChatConversationServiceTest`: `listDiscoverableGroups`
      further filters the DB page by the caller's current
      `ChatEligibilityService` eligibility (REQ-27) and excludes groups
      the caller is already a participant of (REQ-28, exclude-not-mark
      per PLAN) (Red).
- [x] 69. Implement `ChatConversationService.listDiscoverableGroups(actor,
      Pageable)` returning `PageResponseDto<ChatDiscoverableGroupDto>`
      (Green for task 68).
- [x] 70. Write a `ChatDiscoverableGroupDtoTest`/service test confirming
      the DTO exposes `participantCount`, not the full
      `participantUserIds` list — deliberately narrower than
      `ChatConversationSummaryDto` (Red).
- [x] 71. Implement `ChatDiscoverableGroupDto` mapping (Green for task
      70).

## Requesting to join a `REQUEST_TO_JOIN` group (REQ-29–37, REQ-30a)

- [x] 72. Write a `ChatJoinRequestRepositoryTest`/entity test: a
      `ChatJoinRequest` persists with `status = PENDING` by default; the
      partial unique index rejects a second `PENDING` row for the same
      `(conversation_id, requester_user_id)` pair (REQ-34) (Red).
- [x] 73. Implement `ChatJoinRequest.java` + `ChatJoinRequestRepository`
      (Green for task 72).
- [x] 74. Write a `ChatConversationServiceTest`: an eligible,
      non-participant user submitting a join request to an active
      `REQUEST_TO_JOIN` group creates a `PENDING` row, re-deriving
      eligibility at submission time (REQ-29) (Red).
- [x] 75. Write `ChatConversationServiceTest` negatives for
      `submitJoinRequest`: already a participant (REQ-33, outright
      rejected, no record created), duplicate pending request (REQ-34,
      via the DB constraint from task 72 surfaced as a clean 409),
      ineligible user (REQ-35), target not currently `REQUEST_TO_JOIN`
      or archived/deleted (REQ-37) (Red, same test class).
- [x] 76. Implement `ChatConversationService.submitJoinRequest(actor,
      conversationId)` satisfying tasks 74-75 (Green).
- [x] 77. Write a test asserting `@AuditLog("chat.group.join_request_submit")`
      is recorded (Red).
- [x] 78. Add the `@AuditLog` annotation (Green for task 77).
- [x] 79. Write a `ChatConversationServiceTest`: a current group admin
      can `listJoinRequests` (filtered to `PENDING` by default); a
      non-admin caller is rejected (Red).
- [x] 80. Implement `ChatConversationService.listJoinRequests(actor,
      conversationId, status)` (Green for task 79).
- [x] 81. Write a `ChatConversationServiceTest`: a current group admin
      approving a **still-eligible** pending request adds the requester
      as a non-admin participant and marks the request `APPROVED`
      (REQ-30, control case) (Red).
- [x] 82. **REQ-30a (AppSec-mandated, non-negotiable):** write a
      `ChatConversationServiceTest` — submit a join request while the
      requester is eligible, then revoke their eligibility (deactivate
      their `TenantMembership` for a member-only group, or change their
      `GlobalRole` away from `STAFF`/`STAFF_ADMIN` for a staff-only
      group), then attempt approval: assert a 400-shaped
      `ChatIneligibleParticipantException`, **no** `ChatParticipant` row
      created, and the request row remains `PENDING` (not auto-
      `REJECTED`) afterward (Red).
- [x] 83. Implement `ChatConversationService.approveJoinRequest(actor,
      conversationId, requestId)`: load request → reject if not
      `PENDING` (REQ-36) → **re-derive `ChatEligibilityService
      .isEligible` fresh, a second time, independent of the
      submission-time check** → if ineligible, throw
      `ChatIneligibleParticipantException` leaving the request
      untouched (`PENDING`) → else create the `ChatParticipant` row
      (non-admin) and mark `APPROVED` — satisfying tasks 81-82 (Green).
      This is the exact gap AppSec's blocking finding identified; do not
      skip task 82's negative case.
- [x] 84. Write a `ChatConversationServiceTest`: a current group admin
      rejecting a pending request marks it `REJECTED`, no participant
      created (REQ-31); a non-admin caller cannot approve or reject
      (REQ-32); deciding an already-decided request is rejected (REQ-36)
      (Red).
- [x] 85. Implement `ChatConversationService.rejectJoinRequest(actor,
      conversationId, requestId)` satisfying task 84's reject/negative
      paths (approve's negatives already covered by task 83's
      `PENDING`-check reuse) (Green).
- [x] 86. Write a test asserting `@AuditLog` entries for
      `"chat.group.join_request_approve"` and
      `"chat.group.join_request_reject"` (Red).
- [x] 87. Add both `@AuditLog` annotations (Green for task 86).
- [x] 88. **403-matrix (AppSec follow-up note a):** write
      `ChatConversationServiceTest`s for `approveJoinRequest`/
      `rejectJoinRequest` proving rejection for (1) an admin of a
      different group, (2) a genuine non-admin participant of this
      exact group (Red only if scoping is wrong; otherwise Green).
- [x] 89. Fix any gap from task 88 (Green), or confirm none needed.

## Joining a `PUBLIC` group directly (REQ-38–42)

- [x] 90. Write a `ChatConversationServiceTest`: an eligible,
      non-participant user `joinPublicGroup`-ing an active `PUBLIC`
      group is immediately added as a non-admin participant, no
      approval step, re-deriving eligibility at join time (REQ-38)
      (Red).
- [x] 91. Write `ChatConversationServiceTest` negatives: already a
      participant (REQ-39, no-op rejection), ineligible user (REQ-40),
      target not currently `PUBLIC` or deleted (REQ-41), non-existent or
      non-`PEER_GROUP` target (REQ-42) (Red, same test class).
- [x] 92. Implement `ChatConversationService.joinPublicGroup(actor,
      conversationId)` satisfying tasks 90-91 (Green).
- [x] 93. Write a test asserting `@AuditLog("chat.group.direct_join")` is
      recorded (Red).
- [x] 94. Add the `@AuditLog` annotation (Green for task 93).

## Deleting a group (REQ-48–53)

- [x] 95. Write a `ChatConversationServiceTest`: a `STAFF_ADMIN` can
      `deleteConversation` any group (tenant or staff), with or without
      participants, unconditionally (REQ-48a) (Red).
- [x] 96. Write a `ChatConversationServiceTest`: a `MEMBER_ADMIN` can
      delete a tenant group belonging to a tenant they currently
      administer (REQ-48b), but is rejected from deleting a staff group
      or a tenant group of a tenant they don't administer (Red, same
      test class).
- [x] 97. Write a `ChatConversationServiceTest`: a user holding
      `CHAT_GROUP_DELETE` in a tenant can delete that tenant's group
      (REQ-48c), but holding it grants no authority over a staff group
      or another tenant's group (Red, same test class).
- [x] 98. Write a `ChatConversationServiceTest`: a current group admin of
      that specific group can delete it, tenant or staff, even with no
      tenant/platform role at all (REQ-48d) (Red, same test class).
- [x] 99. Write a `ChatConversationServiceTest`: a caller qualifying
      under none of the four paths is rejected (REQ-50) (Red, same test
      class).
- [x] 100. Implement `ChatConversationService.deleteConversation(actor,
      conversationId)` trying the four paths in PLAN's documented order
      (`STAFF_ADMIN` → active `MEMBER_ADMIN`-of-tenant →
      `CHAT_GROUP_DELETE`-holder-in-tenant → group-admin-of-this-group)
      satisfying tasks 95-99 (Green).
- [x] 101. Write a `ChatConversationServiceTest`: deleting a non-existent
      conversation returns not-found (REQ-51); deleting a non-
      `PEER_GROUP` conversation is rejected (REQ-52); deleting an
      already-soft-deleted conversation is rejected, not a silent no-op
      (REQ-53, `ChatGroupStateConflictException`/`ALREADY_DELETED`)
      (Red).
- [x] 102. Add the not-found/wrong-kind/already-deleted guards to
      `deleteConversation` (Green for task 101).
- [x] 103. Set `deletedAt = now()` on the `ChatConversation` row and
      (per REQ-49) on every associated `ChatParticipant` and
      `ChatMessage` row inside `deleteConversation`'s transaction (Green,
      extends task 100/102's implementation — no separate task pair
      since this is the core of what "soft-delete the group" means).
- [x] 104. Write an integration test: after deletion, the conversation is
      inaccessible through every normal path — `getConversation`,
      `listMessages`, `sendMessage`, `listConversations`,
      `listDiscoverableGroups`, `submitJoinRequest`/`joinPublicGroup`,
      `addParticipants`/`removeParticipant`/`promoteToAdmin`/
      `changeVisibility`, and the REQ-44/45 archived-group staff-
      visibility grants — for the deleting user, for other former
      participants, and for a `STAFF_ADMIN` alike (REQ-49) (Red).
- [x] 105. Fix any path found still reachable in task 104 (Green), or
      confirm `SoftDeleteFilter`'s default-on behavior (tasks 9-14)
      already covers all of them.
- [x] 106. Write a test confirming the row is still physically present
      in the database after deletion (a native/`@AllowDeletedForOversight`
      -style raw query bypassing the filter finds it) — soft delete, not
      a hard delete (Red only if a physical `DELETE` was used instead of
      `deletedAt`; otherwise Green, confirming task 103's approach).
- [x] 107. Fix if task 106 found a hard delete (Green), or confirm none
      needed.
- [x] 108. Write a test asserting `@AuditLog("chat.group.delete")` is
      recorded regardless of which of the four paths authorized it (Red).
- [x] 109. Add the `@AuditLog` annotation to `deleteConversation` (Green
      for task 108).

## `ChatConversationDetailDto` extension

- [x] 110. Write a `ChatConversationDetailDtoTest`: `from(...)` now
      includes `visibility`, `archivedAt` (nullable), and
      `adminUserIds` — additive, existing fields unchanged (Red).
- [x] 111. Extend `ChatConversationDetailDto`/its `from(...)` mapping
      (Green for task 110).

## Controllers, DTOs, and exception handling

- [x] 112. Write a `ChatControllerIntegrationTest` (Testcontainers, CSRF
      token via `obtainCsrfCookie()`): `POST /api/chat/conversations/{id}/participants`
      happy path (200, partial-success `rejected[]` populated for a
      mixed batch) (Red).
- [x] 113. Implement `ChatController.addParticipants` +
      `AddChatParticipantsRequestDto` + `ChatAddParticipantsResultDto` +
      `ChatParticipantRejectionDto` wiring to
      `ChatConversationService.addParticipants` (Green for task 112).
- [x] 114. Write a `ChatControllerIntegrationTest`: `DELETE
      /api/chat/conversations/{id}/participants/{userId}` (200 with
      updated detail, 403, 404, 409) (Red).
- [x] 115. Implement `ChatController.removeParticipant` (Green for task
      114).
- [x] 116. Write a `ChatControllerIntegrationTest`: `POST
      /api/chat/conversations/{id}/leave` (204, 403, 404) (Red).
- [x] 117. Implement `ChatController.leaveGroup` (Green for task 116).
- [x] 118. Write a `ChatControllerIntegrationTest`: `POST
      /api/chat/conversations/{id}/admins/{userId}` (200, 400, 403, 404)
      (Red).
- [x] 119. Implement `ChatController.promoteToAdmin` (Green for task
      118).
- [x] 120. Write a `ChatControllerIntegrationTest`: `PUT
      /api/chat/conversations/{id}/visibility` (200, 400, 403, 409)
      (Red).
- [x] 121. Implement `ChatController.changeVisibility` +
      `ChangeChatVisibilityRequestDto` (Green for task 120).
- [x] 122. Write a `ChatControllerIntegrationTest`: `GET
      /api/chat/discoverable-groups?page=&size=` returns a
      `PageResponseDto<ChatDiscoverableGroupDto>` envelope (Red).
- [x] 123. Implement `ChatController.listDiscoverableGroups` (Green for
      task 122).
- [x] 124. Write a `ChatControllerIntegrationTest`: `POST
      /api/chat/conversations/{id}/join-requests` (201, 400, 403, 409),
      `GET .../join-requests?status=PENDING` (200, 403), `POST
      .../join-requests/{requestId}/approve` (200, **400 for the REQ-30a
      case**, 403, 409), `POST .../join-requests/{requestId}/reject`
      (200, 403, 409) (Red).
- [x] 125. Implement `ChatController.submitJoinRequest`/
      `listJoinRequests`/`approveJoinRequest`/`rejectJoinRequest` +
      `ChatJoinRequestDto` wiring (Green for task 124).
- [x] 126. Write a `ChatControllerIntegrationTest`: `POST
      /api/chat/conversations/{id}/join` (200, 400, 403, 409) (Red).
- [x] 127. Implement `ChatController.joinPublicGroup` (Green for task
      126).
- [x] 128. Write a `ChatControllerIntegrationTest`: `DELETE
      /api/chat/conversations/{id}` (204, 403, 404, 409) across all four
      authorization paths at the controller/CSRF layer (not re-testing
      the service-level matrix, just confirming the wiring) (Red).
- [x] 129. Implement `ChatController.deleteConversation` (Green for task
      128).
- [x] 130. Write a `ChatExceptionHandlerTest`: `ChatGroupStateConflictException`
      (409, with `detail`), `ChatDuplicateParticipantException`,
      `ChatJoinRequestConflictException`, `ChatVisibilityUnchangedException`,
      `ChatAdminAlreadyGrantedException` each map to their documented
      status/error-code pair (Red).
- [x] 131. Implement the five new exception classes + their
      `ChatExceptionHandler` entries (Green for task 130).

## Cross-cutting regression, AppSec re-confirmation, and wrap-up

- [x] 132. Write an integration test: every one of this feature's new
      endpoints correctly requires the `X-XSRF-TOKEN` header like every
      other authenticated mutating endpoint under `/api/chat/**` — no
      new CSRF exemption was added anywhere in `SecurityConfig` for this
      feature, per PLAN (Red only if a route was accidentally exempted;
      otherwise Green).
- [x] 133. Fix `SecurityConfig` if task 132 found an accidental exemption
      (Green), or confirm none needed.
- [x] 134. Write an integration test re-confirming
      `internal-team-chat`'s existing REQ-5a/REQ-5b active-group
      look-in behavior is unaffected by this feature's archived-group
      branch addition (task 56) — an active, non-archived group's
      oversight access still follows the original rules exactly (Red
      only if a regression was introduced; otherwise Green).
- [x] 135. Fix any regression from task 134 (Green), or confirm none
      needed.
- [x] 136. Run `./mvnw spotless:apply` then `./mvnw verify` and confirm
      the whole suite (existing `internal-team-chat`/`soft-delete-
      default-filter` tests included) is green.
- [x] 137. Update `PROJECT_STATUS.md` to reflect this feature's
      completion, noting the reused/extended infrastructure (soft-delete
      filter now covers three more entities; `ChatConversationDetailDto`
      gained fields) so a future conversation doesn't have to
      rediscover it from the diff.
- [x] 138. Cross-check with the `knowly-app` `chat-unified-ui` feature
      owner (or leave an explicit note in that feature's PLAN.md if not
      done in the same session) that the "Frontend contract
      reconciliation" table in this feature's `PLAN.md` has been applied
      — this task is a coordination checkpoint, not backend code.
