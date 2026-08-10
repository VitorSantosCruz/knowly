# TASKS — chat-unified-ui (frontend)

> Atomic, sequential, verifiable tasks derived from PLAN.md (reconciled
> 2026-08-08 against `chat-group-membership-management`'s backend PLAN.md,
> AppSec-approved without reservations). Each "Implement" task ends with
> `npm run format` and a small Conventional Commit before moving on.
> Section 9 (route wiring) intentionally comes *after* every section it
> depends on, so the old `/chat`/`/support`/`/conversations` routes stay
> live and green until the new shell is ready to replace them in one cut.

## 1. Shared prerequisites (models)

- [ ] 1. Extend `core/chat.model.ts`'s `ConversationDetail` with
      `visibility: 'PRIVATE'|'REQUEST_TO_JOIN'|'PUBLIC'`,
      `archivedAt: string | null`, `adminUserIds: number[]` — additive
      fields matching the backend's extended `ChatConversationDetailDto`
      verbatim. No test needed (pure type change); verify no existing
      `ConversationDetail` literal in `chat.service.spec.ts`/
      `support.service.spec.ts` fixtures breaks under `strict` (run
      `npm run build` after this task specifically).
- [ ] 2. Add `ChatGroupVisibility` type alias, `ChatDiscoverableGroupDto`
      (`{ id, title, tenantId, visibility, participantCount }`),
      `ChatJoinRequestDto` (`{ id, conversationId, requesterUserId,
      requesterNickname, status: 'PENDING'|'APPROVED'|'REJECTED',
      decidedAt: string | null }`), `ChatAddParticipantsResultDto`
      (`{ conversation: ConversationDetail, rejected: { userId, reason:
      'ALREADY_PARTICIPANT'|'INELIGIBLE' }[] }`) to `core/chat.model.ts`,
      matching PLAN.md's "Consumed API contracts" verbatim.
- [ ] 3. Confirm `PageResponseDto<T>` (already defined for
      `tenant-pagination-search`) is imported/reused as-is for
      `ChatDiscoverableGroupDto` pagination — no new envelope type. If
      it lives in a tenant-specific file, extract it to a shared
      location first (small housekeeping, no behavior change) rather
      than duplicating the interface.
- [ ] 4. Add `CreateConversationRequest`'s `visibility` field (required
      when `kind: 'GROUP'`, absent/ignored for `'DIRECT'`), matching
      REQ-13/18's create-group payload.

## 2. `ChatDirectoryService` — discoverable groups (REQ-8's Groups candidate set)

- [ ] 5. Test: `ChatDirectoryService.fetchDiscoverableGroups()` calls
      `GET /api/chat/discoverable-groups?page=0&size=200` with no
      `tenantId` param, unwraps `response.content` into
      `discoverableGroups()` (Red).
- [ ] 6. Implement `fetchDiscoverableGroups` (Green).
- [ ] 7. Test: a fixture response containing only non-`PRIVATE`,
      not-yet-joined groups round-trips into `discoverableGroups()`
      unmodified — asserting the service applies **no** client-side
      visibility/membership filtering (documents the "backend invariant,
      not re-derived" decision from PLAN.md) (Red — this is really an
      absence-of-behavior assertion, write it as "output equals input
      array reference-wise/deep-equal, no rows dropped").
- [ ] 8. Confirm task 7 passes with the task-6 implementation as-is
      (Green — no additional code should be needed; if it isn't green,
      that itself means step 6 added filtering logic that must be
      removed).

## 3. `ChatGroupService` — group governance actions

- [ ] 9. Test: `ChatGroupService.join(id)` calls
      `POST /api/chat/conversations/{id}/join` with an empty body and,
      on `200`, patches `ChatService`'s `_details` map with the returned
      `ConversationDetail` (Red).
- [ ] 10. Implement `join`, injecting `ChatService` to reuse its
      `_details` map (Green).
- [ ] 11. Test: `join`'s three distinct failure branches — `400`
      (`CHAT_INELIGIBLE_PARTICIPANT`), `403`
      (`CHAT_PARTICIPANT_ALREADY_MEMBER`), `409` (wrong visibility mode
      or deleted) — each surface a distinct, non-generic error, and none
      mutate `ChatService`'s state (Red).
- [ ] 12. Implement that three-way error branch (Green).
- [ ] 13. Test: `ChatGroupService.requestToJoin(id)` calls
      `POST /api/chat/conversations/{id}/join-requests` and returns the
      created `ChatJoinRequestDto` (`status: 'PENDING'`) to the caller,
      without opening the conversation view (REQ-21) (Red).
- [ ] 14. Implement `requestToJoin` (Green).
- [ ] 15. Test: `requestToJoin`'s `400`/`403`/`409` failures each
      surface an inline error and leave no partial state behind (Red).
- [ ] 16. Implement that error handling (Green).
- [ ] 17. Test: `ChatGroupService.fetchPendingJoinRequests(id)` calls
      `GET /api/chat/conversations/{id}/join-requests?status=PENDING`
      and populates `pendingJoinRequests()` keyed by conversation id
      (Red).
- [ ] 18. Implement `fetchPendingJoinRequests` (Green).
- [ ] 19. Test: `ChatGroupService.approveJoinRequest(id, requestId)`
      calls `POST .../join-requests/{requestId}/approve` and, on `200`,
      removes that request from `pendingJoinRequests()` and calls
      `ChatService.openConversation(id)` again to refresh the
      participant list (since the response carries no updated
      membership) (Red).
- [ ] 20. Implement `approveJoinRequest`'s success path (Green).
- [ ] 21. Test: `approveJoinRequest`'s new REQ-30a `400`
      (`CHAT_INELIGIBLE_PARTICIPANT`) case does **not** remove the
      request from `pendingJoinRequests()` and surfaces a distinct
      "no longer approvable" message, never the generic REQ-25/27
      failure message (Red).
- [ ] 22. Implement that distinct 400 branch (Green).
- [ ] 23. Test: `approveJoinRequest`'s `403`/`409`
      (`CHAT_JOIN_REQUEST_ALREADY_DECIDED`) both leave
      `pendingJoinRequests()` untouched and surface the generic
      REQ-25/27 inline error (Red).
- [ ] 24. Implement that branch (Green).
- [ ] 25. Test: `ChatGroupService.rejectJoinRequest(id, requestId)`
      calls `POST .../join-requests/{requestId}/reject`, consumes the
      returned `ChatJoinRequestDto` (`status: 'REJECTED'`), and removes
      it from `pendingJoinRequests()` on success; a `403`/`409` leaves
      it untouched with an inline error (Red).
- [ ] 26. Implement `rejectJoinRequest` (Green).
- [ ] 27. Test: `ChatGroupService.promote(id, userId)` calls
      `POST /api/chat/conversations/{id}/admins/{userId}` and, on
      `200`, patches `ChatService`'s `_details` map with the returned
      `ConversationDetail` (now including `userId` in `adminUserIds`)
      (Red).
- [ ] 28. Implement `promote` (Green).
- [ ] 29. Test: `promote`'s `400` (`CHAT_PARTICIPANT_ALREADY_ADMIN`,
      no-op), `403` (caller not admin), `404` (target not a participant)
      each surface an inline error without mutating state (Red).
- [ ] 30. Implement that error handling (Green).
- [ ] 31. Test: `ChatGroupService.removeParticipant(id, userId)` calls
      `DELETE /api/chat/conversations/{id}/participants/{userId}` and,
      on `200`, patches `ChatService`'s `_details` map directly from the
      returned `ConversationDetail` body — asserting the service does
      **not** manually recompute the participant list client-side (Red).
- [ ] 32. Implement `removeParticipant` (Green).
- [ ] 33. Test: `removeParticipant`'s `403` (not admin), `404`, `409`
      (would empty the group) each leave the cached detail untouched
      with an inline error (Red).
- [ ] 34. Implement that error handling (Green).
- [ ] 35. Test: `ChatGroupService.leave(id)` calls
      `POST /api/chat/conversations/{id}/leave` with **no body
      parsing** (asserting the service handles a `204` correctly, not
      expecting JSON) and, on success, drops the conversation from
      `ChatService`'s `_conversations` signal (Red).
- [ ] 36. Implement `leave` (Green).
- [ ] 37. Test: `leave`'s `403` (`CHAT_ACCESS_DENIED`, not a genuine
      participant) leaves `_conversations` untouched with an inline
      error (Red).
- [ ] 38. Implement that error handling (Green).
- [ ] 39. Test: `ChatGroupService.changeVisibility(id, visibility)`
      calls `PUT /api/chat/conversations/{id}/visibility` (asserting
      `PUT`, not `PATCH`) with `{ visibility }` and, on `200`, patches
      `ChatService`'s `_details` map with the returned detail (Red).
- [ ] 40. Implement `changeVisibility` (Green).
- [ ] 41. Test: `changeVisibility`'s `400`
      (`CHAT_VISIBILITY_UNCHANGED`, no-op), `403` (not admin), `409`
      (archived/deleted) each leave cached state untouched with an
      inline error (Red).
- [ ] 42. Implement that error handling (Green).
- [ ] 43. Test: `ChatGroupService.deleteGroup(id)` calls
      `DELETE /api/chat/conversations/{id}` and, on `204`, drops the
      conversation from `ChatService`'s `_conversations` signal and its
      `_details` map (Red).
- [ ] 44. Implement `deleteGroup` (Green).
- [ ] 45. Test: `deleteGroup`'s `403` (`CHAT_ACCESS_DENIED`), `404`,
      `409` (`CHAT_CONVERSATION_ALREADY_DELETED`) each leave state
      untouched with an inline error (Red).
- [ ] 46. Implement that error handling (Green).
- [ ] 47. Test: `ChatGroupService.addParticipants(id, userIds)` calls
      `POST /api/chat/conversations/{id}/participants` with `{
      userIds }` and, on `200` with an **all-accepted** response
      (`rejected: []`), patches `ChatService`'s `_details` map from
      `result.conversation` (Red).
- [ ] 48. Implement `addParticipants`'s all-accepted path (Green).
- [ ] 49. Test: a `200` response with a **non-empty `rejected[]`**
      (partial success) still patches `_details` from
      `result.conversation` *and* returns `result.rejected` to the
      caller for inline per-id display — asserting this is **not**
      treated as this method's error path (no `permissionActionError`-
      style state is set) (Red).
- [ ] 50. Implement that partial-success surfacing (Green).
- [ ] 51. Test: a `400` (every submitted id rejected — nothing added at
      all), `403` (`CHAT_ACCESS_DENIED`, not admin), `404`, `409`
      (wrong kind/archived/deleted) each leave `_details` untouched with
      an inline error (Red).
- [ ] 52. Implement that error handling (Green).

## 4. `chat-directory.component.ts` — People + Groups list, search (REQ-3, REQ-4, REQ-8, REQ-9, REQ-10, REQ-11)

- [ ] 53. Test: on init, `ChatDirectoryComponent` calls
      `ChatService.fetchConversations()`,
      `ChatService.fetchEligibleParticipants('direct')`, and
      `ChatDirectoryService.fetchDiscoverableGroups()`, and renders the
      combined, unfiltered list (own DIRECT conversations + eligible
      non-messaged people + own GROUP conversations + discoverable
      groups) when `searchQuery` is empty (Red).
- [ ] 54. Implement that combined `computed()` list + initial fetches
      (Green).
- [ ] 55. Test: clicking a person row not yet messaged calls
      `ChatService.createConversation({ kind: 'DIRECT', ... })` and
      navigates to the resulting conversation; clicking a person with an
      existing DIRECT conversation navigates straight to it without a
      second create call (REQ-3) (Red).
- [ ] 56. Implement that click-to-open/create dispatch (Green).
- [ ] 57. Test: clicking a group the viewer already participates in
      navigates to its conversation view (REQ-5), unchanged from
      today's `conversation-list-item.component.ts` behavior (Red).
- [ ] 58. Implement/confirm that branch, reusing
      `conversation-list-item.component.ts` unchanged (Green).
- [ ] 59. Test: clicking a `PUBLIC` discoverable group (not yet a
      participant) calls `ChatGroupService.join(id)` and, on success,
      navigates to the group's conversation view with no intermediate
      confirmation (REQ-20) (Red).
- [ ] 60. Implement that click dispatch (Green).
- [ ] 61. Test: clicking a `REQUEST_TO_JOIN` discoverable group calls
      `ChatGroupService.requestToJoin(id)` and, on success, shows that
      request as pending inline (e.g. a disabled "pedido enviado" state
      on that row) instead of navigating anywhere (REQ-21) (Red).
- [ ] 62. Implement that click dispatch + pending-state rendering
      (Green).
- [ ] 63. Test: a failed join or join-request click (REQ-25) shows an
      inline error on that specific row and leaves the row's clickable
      state exactly as before the attempt (Red).
- [ ] 64. Implement that error handling (Green).
- [ ] 65. Test: typing into the search field filters person/group rows
      to only those whose display name contains the typed text,
      case-insensitively, live per keystroke (REQ-8) (Red).
- [ ] 66. Implement the `searchQuery` signal + filtering `computed()`
      (Green).
- [ ] 67. Test: a search with zero matches renders a "no results for
      '<query>'" message, distinct from the pre-existing "no
      conversations yet" empty state (REQ-10) (Red).
- [ ] 68. Implement that distinct empty state (Green).
- [ ] 69. Test: clearing the search field restores the full,
      unfiltered list (REQ-11) (Red).
- [ ] 70. Implement that restore behavior (Green — likely free once 66
      is correct; write the test anyway as its own regression anchor).
- [ ] 71. Test: a fixture including a `PRIVATE`-visibility candidate
      that would textually match the search query is still never
      rendered — documenting that this is impossible by construction
      (the fetched discoverable-groups list never contains one), not a
      client-side filter this component performs (Red — assert the
      fixture data itself excludes it, matching task 7/8's service-level
      assertion; this is the component-level half of that same
      invariant).
- [ ] 72. No implementation needed for task 71 (confirm Green as-is);
      if it fails, that means a `PRIVATE` row leaked into the fixture
      or the component added filtering logic that shouldn't exist —
      fix at the root cause, not by adding a client-side filter.
- [ ] 73. Each of `create-group-dialog.component.ts`'s trigger, every
      directory row, and the search field itself keyboard-navigable
      with an explicit `aria-label` — write as one accessibility test
      per control (Red), implement (Green).

## 5. `create-group-dialog.component.ts` (REQ-12, REQ-13, REQ-18)

- [ ] 74. Test: `CreateGroupDialogComponent` disables submit until both
      a non-empty name and one of the three visibility options
      (`PRIVATE`/`REQUEST_TO_JOIN`/`PUBLIC`) are selected (REQ-18) (Red).
- [ ] 75. Implement that validation gating, template-driven signal-bound
      state (no `ReactiveFormsModule`, per PLAN.md) (Green).
- [ ] 76. Test: submitting calls
      `ChatService.createConversation({ kind: 'GROUP', tenantId, title,
      visibility, participantUserIds: [] })` and, on `201`, navigates to
      the new group's conversation view immediately (REQ-13) (Red).
- [ ] 77. Implement that submit wiring (Green).
- [ ] 78. Test: a failed creation (400/403) shows an inline error and
      keeps the dialog open with the entered name/visibility intact
      (Red).
- [ ] 79. Implement that error handling (Green).
- [ ] 80. Implement `<dialog>`-based open/close wiring (native dialog
      element, per the `deletion-confirmation-token` precedent) — no
      separate Red, covered by tasks 74-79's rendering assertions.

## 6. `group-visibility-badge.component.ts` (REQ-26)

- [ ] 81. Test: renders a distinct label/style for each of
      `PRIVATE`/`REQUEST_TO_JOIN`/`PUBLIC`, mirroring
      `ticket-status-badge.component.ts`'s shape (Red).
- [ ] 82. Implement the three-state badge (Green).
- [ ] 83. Test: `ChatDirectoryComponent`'s group rows and
      `CreateGroupDialogComponent`'s visibility selector both render
      this badge/label consistently (not two divergent copies of the
      same enum-to-label mapping) (Red).
- [ ] 84. Wire the shared badge into both call sites (Green).

## 7. `group-admin-panel.component.ts` (REQ-14, REQ-15, REQ-22, REQ-23, REQ-24, REQ-28, REQ-29, REQ-30, REQ-31, REQ-32)

- [ ] 85. Test: `GroupAdminPanelComponent` renders none of its actions
      (pending requests, promote, visibility-change, remove-participant,
      delete-group) when `currentUserId` is absent from the input
      `ConversationDetail.adminUserIds` — asserting removal from the DOM
      entirely, not `display:none` (Red).
- [ ] 86. Implement that admin-gating `computed()` and conditional
      rendering (Green).
- [ ] 87. Test: when the viewer is an admin, it calls
      `ChatGroupService.fetchPendingJoinRequests(id)` on init and
      renders each pending request with approve/reject actions (REQ-22)
      (Red).
- [ ] 88. Implement that rendering + fetch (Green).
- [ ] 89. Test: clicking "aprovar" calls
      `ChatGroupService.approveJoinRequest` and removes that row from
      the pending list on success only; the REQ-30a 400 case shows an
      inline "não pode mais ser aprovado" message on that row and keeps
      it in the list (Red).
- [ ] 90. Implement that approve wiring (Green).
- [ ] 91. Test: clicking "rejeitar" calls
      `ChatGroupService.rejectJoinRequest` and removes that row from the
      pending list on success only, leaving it in place on failure with
      an inline error (Red).
- [ ] 92. Implement that reject wiring (Green).
- [ ] 93. Test: a "promover a admin" action next to each non-admin
      participant calls `ChatGroupService.promote` and, on success, that
      participant's row updates to reflect admin status without a full
      reload (REQ-30) (Red).
- [ ] 94. Implement that promote wiring (Green).
- [ ] 95. Test: a "remover" action next to each other participant
      (REQ-14) calls `ChatGroupService.removeParticipant` after a
      confirm step and, on success, removes them from the displayed
      participant list; on failure, the participant stays listed with an
      inline error (REQ-15) (Red).
- [ ] 96. Implement that remove wiring (Green).
- [ ] 97. Test: a visibility-change control (REQ-28) calls
      `ChatGroupService.changeVisibility` on confirm and, on success,
      updates the displayed `group-visibility-badge.component.ts`
      instance in place (REQ-29) (Red).
- [ ] 98. Implement that visibility-change wiring (Green).
- [ ] 99. Test: an "excluir grupo" action (REQ-31) calls
      `ChatGroupService.deleteGroup` after a confirm step and, on
      success, navigates the acting admin away from the group's view
      (REQ-32); on failure, the view stays exactly as it was with an
      inline error (Red).
- [ ] 100. Implement that delete wiring (Green).
- [ ] 101. Confirmation steps for remove-participant, delete-group, and
       leave-group (task 103) all reuse the existing native `<dialog>`
       `ConfirmDialogComponent` (`deletion-confirmation-token` precedent)
       rather than three ad-hoc `window.confirm()` calls or three new
       dialog components — no separate Red, covered by tasks 95-100's
       assertions on the confirm flow.

## 8. `conversation-detail.component.ts` extensions (REQ-16, REQ-17)

- [ ] 102. Test: a "sair do grupo" action renders for any genuine
       participant (`participantUserIds.includes(currentUserId)`), and
       is absent for a `viewerRelation === 'LOOKING_IN'` viewer — never
       shown to an admin present only via tenant-level look-in (REQ-16)
       (Red).
- [ ] 103. Implement that gating + the action itself (Green).
- [ ] 104. Test: confirming "sair do grupo" calls
       `ChatGroupService.leave(id)` and, on success, removes the group
       from the viewer's own list (via `ChatService`'s signal) and
       navigates them away from the group's view; on failure, the view
       is unchanged with an inline error (REQ-17, REQ-27) (Red).
- [ ] 105. Implement that leave wiring (Green).
- [ ] 106. Wire `group-admin-panel.component.ts` into
       `conversation-detail.component.ts` as a conditional child
       (rendered for `kind === 'PEER_GROUP'` only, never for `DIRECT`/
       `SUPPORT`) — no separate Red, covered by section 7's own
       component-level tests plus one integration assertion that it
       never renders for a `DIRECT` conversation.

## 9. `ChatShellComponent` + sidebar + route migration (REQ-1, REQ-2, REQ-6, REQ-7)

- [ ] 107. Test: `ChatShellComponent` reads `section` from
       `ActivatedRoute.queryParamMap`, defaulting to `'people'` when
       absent, and renders `ChatDirectoryComponent` for `'people'`/
       `'groups'` (Red).
- [ ] 108. Implement that default + People/Groups dispatch (Green — note:
       People and Groups both render `ChatDirectoryComponent`, the
       `section` value only changes the sidebar's active-tab styling and
       which half of the combined list is visually emphasized/scrolled
       to, per REQ-2's "four distinct, always-visible sections" — both
       are always in the DOM together, per SPEC framing).
- [ ] 109. Test: `section: 'support'` renders `SupportPageComponent`
       unchanged (REQ-6) (Red).
- [ ] 110. Implement that dispatch branch (Green).
- [ ] 111. Test: `section: 'articles'` with an active tenant renders
       `ConversationsPageComponent` unchanged (REQ-7); with **no**
       active tenant, renders the existing "no active tenant" empty
       state instead — this is the one behavior change from today's
       route-guard-based gating and needs this explicit regression test
       (Red).
- [ ] 112. Implement that tenant-presence check + dispatch (Green).
- [ ] 113. Test: `ChatSidebarComponent` renders the 4 section
       tabs (People/Groups/Support/Base de artigos) and the search
       field, each keyboard-navigable with an `aria-label`; clicking a
       tab updates the `section` query param without a full navigation/
       reload (Red).
- [ ] 114. Implement `ChatSidebarComponent` (Green).
- [ ] 115. Register `/chat` (no guard), `/chat/:conversationId` (no
       guard), `/chat/support/:channelId` (no guard),
       `/chat/articles/:conversationId` (no guard) in `app.routes.ts`,
       all routing to `ChatShellComponent`, alongside the still-live old
       routes (do not remove `/support`/`/conversations` yet — see next
       task).
- [ ] 116. Test: `ChatShellComponent` reads `:conversationId` (peer/
       group), `:channelId` under `/chat/support/`, or `:conversationId`
       under `/chat/articles/` and forwards the right id to the right
       child component/service (Red).
- [ ] 117. Implement that id-forwarding dispatch (Green).
- [ ] 118. Replace `/support`, `/support/:channelId`, `/conversations`
       in `app.routes.ts` with `redirectTo` entries into
       `/chat?section=support`, `/chat/support/:channelId`, and
       `/chat?section=articles` respectively.
- [ ] 119. Test: a `Router` navigation to `/support` and to
       `/conversations` each resolve to the expected `/chat` URL with
       the expected `section` query param (route-migration regression,
       per PLAN.md) (Red).
- [ ] 120. Confirm the redirects satisfy task 119 (Green).
- [ ] 121. Update `nav-menu.component.ts`: collapse the two existing
       `'/chat'`/`'/conversations'` entries into one ("Conversas",
       `routerLink: '/chat'`), per REQ-1.
- [ ] 122. Test: `nav-menu.component.ts` renders exactly one "Conversas"
       nav entry, not two, and no entry still points at the old
       `/conversations`/`/support` paths directly (Red).
- [ ] 123. Confirm task 121's change satisfies task 122 (Green).
- [ ] 124. Update `welcome-page.component.ts`'s CTA from
       `routerLink="/conversations"` to
       `[routerLink]="['/chat']" [queryParams]="{ section: 'articles' }"`.
- [ ] 125. Retire `ChatPageComponent`, `new-conversation-dialog
       .component.ts`, and `conversation-list.component.ts` (delete
       files + their specs), confirming nothing else in the codebase
       still imports them (`grep` check before deleting).
- [ ] 126. Run the full existing chat/support/conversations spec suites
       (`chat.service.spec.ts`, `support.service.spec.ts`,
       `conversation.service.spec.ts`, `message-thread.component.spec.ts`,
       `conversation-list-item.component.spec.ts`,
       `chat-header.component.spec.ts`, `participant-picker.component
       .spec.ts`, and every `support`/`conversations` feature spec) and
       confirm all still pass unmodified — none of their underlying
       services/contracts changed in this feature.

## 10. i18n and design

- [ ] 127. Add unified-nav i18n keys to `public/i18n/en.json`/
       `pt-BR.json`: sidebar section labels (Pessoas/Grupos/Support/Base
       de artigos), search placeholder and "no results for '<query>'"
       copy, "Criar grupo" dialog (name/visibility labels, three
       visibility option labels/descriptions), visibility badge labels,
       join/request-to-join copy, admin-panel actions (aprovar/rejeitar/
       promover/mudar visibilidade/excluir grupo), "sair do grupo".
- [ ] 128. Apply the established Tailwind design tokens to every new
       component (sidebar, directory rows, badges, dialogs, admin
       panel), reusing existing list/card/badge/dialog patterns from
       `internal-team-chat`/`user-management`/`deletion-confirmation-
       token` rather than introducing new visual language.
- [ ] 129. Confirm responsive collapse/expand behavior at this app's
       existing mobile/tablet/desktop breakpoints for the new sidebar +
       main-panel shell, matching `internal-team-chat`'s existing
       `/chat` screen's pattern (manual check + a viewport-width test if
       an existing precedent for that exists in this codebase; otherwise
       manual verification only, documented in the commit message).

## 12. Amendment (3): unified column 1 + full-directory column 3 (REQ-1/REQ-2/REQ-2c/REQ-2d, REQ-33–REQ-37)

> Supersedes section 4's shipped-2-column tasks where noted. See
> `PLAN.md`'s "Amendment (3) reconciliation" section for the full
> rationale behind each decision below, including the two feasibility
> calls (column-3 sort, hard-delete). Tasks marked **BLOCKED** must not
> be started until the named backend prerequisite lands — do not
> reorder them earlier just because they'd otherwise be next in
> sequence.

### 12a. `ChatDirectoryRowsService` — unified list + discovery rows

- [x] 134. Test: `ChatDirectoryRowsService.conversationRows()` returns
       `[supportRow, ...rest]` with Support always first regardless of
       the underlying people/group/article ordering, and never affected
       by `talkedQuery`/any search state (Red).
- [x] 135. Implement `conversationRows` as a `computed()` merging
       `talkedPeople()`, `groupRows()` (members only — discoverable,
       non-member groups move to `discoveryRows`, see below), and
       `articleRows()`, sorted by each kind's existing id-descending
       proxy, with `supportRow` unconditionally prepended (Green).
- [x] 136. Test: `discoveryRows()` (renamed from `notTalkedPeople`)
       returns not-yet-messaged people **and** discoverable, non-member
       groups combined, with zero overlap against `conversationRows()`
       (Red).
- [x] 137. Implement `discoveryRows` — extend the renamed computed to
       include `ChatDirectoryService.discoverableGroups()` rows
       alongside not-yet-messaged people (Green).
- [x] 138. Update `groupRows()`'s doc comment and callers: it now backs
       `conversationRows` (member groups only) — `personGroupRows()`'s
       combined people+groups shape is retired in favor of the two
       narrower computeds feeding `conversationRows`/`discoveryRows`
       directly (small internal refactor, covered by tasks 134-137's
       assertions; no new user-facing behavior).
- [x] 139. Test: `discoveryRows()` sorts alphabetically by
       `displayName` (the documented interim fallback, see PLAN.md's
       column-3 feasibility decision) — asserting this is explicitly the
       fallback branch, not a placeholder for an already-computed real
       recency value (Red).
- [x] 140. Implement that alphabetical sort, with a doc comment stating
       explicitly this is REQ-2d's interim fallback pending the backend
       amendment (Green).
- [ ] 141. **BLOCKED — backend prerequisite: a new
       `GET /api/chat/interaction-recency`-style endpoint (or
       equivalent), specified via its own backend SPEC/PLAN amendment
       (see PLAN.md's "Cross-surface recency sort" decision).** Once
       that contract exists: test that `discoveryRows()` sorts
       descending by the fetched per-entity last-interaction timestamp,
       falling back to alphabetical only for entities with no computed
       timestamp (REQ-2d, final ranking) — do not start this task until
       the backend feature is approved and its PLAN.md is reconciled
       into this feature's own PLAN.md first, mirroring
       `chat-group-membership-management`'s existing reconciliation
       precedent.
- [ ] 142. **BLOCKED — same prerequisite as task 141.** Implement the
       real cross-surface sort once the endpoint exists, replacing the
       task-140 fallback (not deleting its alphabetical tiebreak, which
       REQ-2d keeps as the tiebreak among zero-interaction entities even
       in the final version).

### 12b. `chat-full-directory.component.ts` — column 3

- [x] 143. Extract `chat-directory.component.ts`'s existing
       `filterByQuery` free function into a shared
       `chat-directory-search.util.ts` (small refactor, no behavior
       change) so both column components import one implementation.
- [x] 144. Test: `ChatFullDirectoryComponent` renders
       `rowsService.discoveryRows()`, filtered by its own independent
       `searchQuery` signal (never affecting or affected by column 1's
       search), with distinct `data-testid`/`aria-label`s from column 1
       (Red).
- [x] 145. Implement `ChatFullDirectoryComponent` (Green) — reuse
       `AvatarComponent`/`GroupVisibilityBadgeComponent` and the same
       click-to-open-or-create/join/request-to-join handlers already on
       `ChatDirectoryRowsService`, no new interaction logic.
- [x] 146. Test: a search with zero matches in column 3 shows its own
       "no results for '<query>'" message, distinct from column 1's
       (REQ-10, per-column) (Red).
- [x] 147. Implement that empty state (Green).
- [x] 148. Test: clicking a not-yet-messaged person or a discoverable
       group in column 3 behaves identically to the same click in
       column 1 today (create-and-open for a person, join/request-to-
       join for a group) — REQ-3's "applies identically regardless of
       whether the row is in column 1 or column 3" (Red).
- [x] 149. Confirm/implement that reuse (Green — should be free, since
       both components call the same `ChatDirectoryRowsService` methods;
       write the test anyway as its own regression anchor).
- [x] 150. Accessibility: column 3's search field and every row
       keyboard-navigable with its own distinct `aria-label` from column
       1's equivalents (Red), implement (Green).

### 12c. `chat-directory.component.ts` rewrite — unified column 1

- [x] 151. Test: `ChatDirectoryComponent` renders one `<ul>` over
       `rowsService.conversationRows()` with Support always the first
       row in the DOM, regardless of any other row's data (Red).
- [x] 152. Implement that rewrite, deleting the 3-section (talked/
       not-talked/groups) template entirely (Green) — reuses the
       existing avatar/badge/active-row/error-row per-item rendering
       already built for the shipped 2-column version.
- [x] 153. Test: one `unifiedQuery` search field filters every row
       except the pinned Support row, which stays visible under any
       non-matching query (REQ-2/REQ-9, Support exemption confirmed)
       (Red).
- [x] 154. Implement that filtering, structurally excluding Support from
       the filtered computed rather than special-casing it in the
       template (Green).
- [x] 155. Test: a search with zero matches shows the distinct "no
       results for '<query>'" message; clearing the field restores the
       full list (REQ-10/REQ-11, now over the unified list) (Red).
- [x] 156. Implement (Green — likely free once 154 is correct).
- [x] 157. Update `chat-directory.component.spec.ts`'s existing talked/
       not-talked/groups-section assertions to match the unified list —
       delete assertions for section headers/titles that no longer
       exist (`chat.contacts.talkedTitle`/`notTalkedTitle`), replacing
       i18n keys accordingly (see task 168).
- [x] 158. `ChatShellComponent` wiring: render `ChatFullDirectoryComponent`
       as the third pane alongside the existing directory/conversation
       panes, extending its existing 2-pane dispatch (no separate Red;
       covered by task 159's collapse test and a basic "3 panes render
       simultaneously above the collapse breakpoint" smoke assertion
       added to `chat-shell.component.spec.ts`).

### 12d. Three-way collapse (REQ-2c, final)

- [x] 159. Test: below the layout's column breakpoint, `ChatShellComponent`
       shows exactly one of the three panes (conversations list, thread,
       full directory) at a time, and a back/forward affordance moves
       between them, extending the existing 2-pane collapse test (Red).
- [x] 160. Implement that 3-way collapse, generalizing the existing
       2-pane collapse state to track which of 3 panes (not 2) is
       currently active (Green).

### 12e. Clearing a 1:1 conversation (REQ-33, REQ-37) — BLOCKED

- [ ] 161. **BLOCKED — backend prerequisite: a new endpoint that
       hard-deletes a `PEER_DIRECT` conversation + its messages, scoped
       to a genuine participant (see PLAN.md's hard-delete feasibility
       decision — likely an extension of `DELETE
       /api/chat/conversations/{id}` to accept `PEER_DIRECT`, or a new
       endpoint, decided by that backend PLAN, not here).** Do not start:
       `ChatGroupService`/a new `ChatConversationLifecycleService`
       method calling that endpoint, a "limpar conversa" action on
       column-1 person rows, row-removal-on-success (person then appears
       in `discoveryRows()`), and the REQ-37 inline-error-on-failure
       path, all wait on that backend contract landing and being
       reconciled into this feature's PLAN.md first.

### 12f. Clearing a group (REQ-34) — no new work

- [x] 162. Confirm (no new test needed): REQ-34's "no clear action for
       groups, distinct from leaving" is already fully satisfied by
       section 8's existing "sair do grupo" tasks (102-105) — add one
       assertion to `conversation-detail.component.spec.ts` (if not
       already implied by its existing action-list assertions) that no
       "limpar"/"clear" control renders anywhere in a group's view,
       alongside its existing "sair do grupo" assertion, so this
       non-requirement has an explicit regression anchor rather than
       relying on absence-by-never-having-built-it.

### 12g. Support has no clear action (REQ-35) — explicit non-task

- [x] 163. Test: no "limpar"/"clear" control of any kind renders for the
       Support row in column 1, under any state (Red — this is an
       explicit regression test for an intentional absence, per
       `chat-directory.component.spec.ts`'s existing convention for
       "never" assertions, e.g. task 71/72's `PRIVATE`-group case).
- [x] 164. No implementation task follows — REQ-35 is a deliberate
       absence of a feature (SPEC.md, final round, item 1). If task 163
       ever fails, that means a clear action was accidentally added to
       the Support row and must be removed, not that this task list is
       missing a "build it" step.

### 12h. Clearing a "Base de artigos" conversation (REQ-36, REQ-37) — BLOCKED

- [ ] 165. **BLOCKED — backend prerequisite: a new endpoint that
       hard-deletes one specific RAG conversation + its messages, scoped
       to its own owning participant (see PLAN.md's hard-delete
       feasibility decision — e.g. `DELETE
       /api/tenants/{tenantId}/conversations/{conversationId}`, decided
       by that backend PLAN, not here).** Do not start: a
       `ConversationService` (frontend) method calling that endpoint, a
       "limpar" action on column-1 article rows, row-removal-on-success
       (leaving the viewer's other RAG conversations untouched), and the
       REQ-37 inline-error-on-failure path, all wait on that backend
       contract landing and being reconciled into this feature's PLAN.md
       first.

### 12i. i18n, a11y, verification

- [x] 166. Update i18n keys: retire
       `chat.contacts.talkedTitle`/`notTalkedTitle`/`talkedSearchLabel`/
       `notTalkedSearchLabel` (superseded by one unified search label);
       add `chat.directory.unifiedSearchLabel`,
       `chat.fullDirectory.searchLabel`,
       `chat.fullDirectory.noResults`/`emptyState`, and (once 12e/12h
       unblock) "limpar conversa"/"limpar" confirmation copy for 1:1 and
       RAG rows.
- [x] 167. Accessibility: column 1's and column 3's search fields each
       have their own distinct `aria-label`, per SPEC.md's NFR
       (Amended (3), final) — one combined test asserting the two labels
       are never equal.
- [x] 168. Run
       `npm run format:check && npm test && npm run build && npm run lint`
       for everything in section 12 that is not BLOCKED, and commit
       incrementally per this repo's atomic-commit convention — do not
       batch the whole amendment into one commit.
- [x] 169. Update `PLAN.md`'s "Amendment (3) reconciliation" section (or
       add a new "Emergent decisions, Amendment (3)" section) with
       anything discovered while executing 12a-12i, following this
       PLAN's own established precedent for documenting deviations.
- [x] 170. Update `SPEC.md`'s Amended-(3) acceptance-criteria checkboxes
       to reflect what's now verified — leave REQ-33/REQ-36/REQ-2d's
       "real ranking" checkboxes unchecked with a note pointing at the
       relevant BLOCKED task until their backend prerequisites land.

## 13. Amendment (4): naming, renaming, icon (REQ-38–REQ-41, REQ-13 final round)

> Backend prerequisite is done and committed (see PLAN.md's "Amendment
> (4) reconciliation" section for the final contract). Nothing here is
> BLOCKED. V32's NOT NULL backfill was verified clean — no backend fix
> needed before starting this section.

### 13a. Shared model + icon-picker component

- [x] 171. Add `IconKey` union type (24 literal values, matching the
       backend enum verbatim) and an `ICON_KEYS: IconKey[]` constant to
       `core/chat.model.ts`. Add `icon: IconKey | null` to
       `ConversationSummary` (`conversation.service.ts`) and
       `ConversationDetail` (`chat.model.ts`). No test needed (pure type
       change); run `npm run build` after this task to confirm no
       existing literal fixture breaks under `strict`.
- [x] 172. Test: `icon-picker.component.ts` renders all 24 icon buttons,
       each with a distinct, human-readable `aria-label` (not the raw
       enum key), keyboard-reachable; clicking one emits `iconSelected`
       with that key; the currently-`[selected]` key (if any) is visually
       distinguished from the rest (Red).
- [x] 173. Implement `icon-picker.component.ts` (`shared/chat/`) as a
       standalone, signal-`input`/`output` component (Green).
- [x] 174. Test: passing `[selected]="null"` renders no icon as selected;
       passing an unset/omitted `[selected]` behaves identically (Red).
- [x] 175. Implement/confirm that default-unselected state (Green).

### 13b. `ConversationService`/`ChatGroupService`/`ChatService` signature changes

- [x] 176. Test: `ConversationService.create(tenantId, title, icon?)`
       `POST`s `{ title, icon }` (icon omitted/`undefined` when not
       passed) to `/api/tenants/{tenantId}/conversations` (Red).
- [x] 177. Implement that signature change; update
       `chat-shell.component.ts`'s `onOpenArticles()` call site (see
       13c) so this compiles (Green).
- [x] 178. Test: `ConversationService.rename(tenantId, conversationId,
       title, icon?)` `PUT`s `{ title, icon }` to
       `/api/tenants/{tenantId}/conversations/{conversationId}` and
       returns the updated `ConversationSummary` (Red).
- [x] 179. Implement `rename` (Green).
- [x] 180. Test: `rename`'s `400` (blank title/invalid icon) and `404`
       (not the caller's own conversation — deliberate, not `403`, per
       the reconciled contract) each surface distinctly to the caller,
       with no local state to roll back (this service holds no signal
       state of its own) (Red).
- [x] 181. Implement that error surfacing (Green).
- [x] 182. Test: `ChatGroupService.rename(id, title, icon?)` `PUT`s `{
       title, icon }` to `/api/chat/conversations/{id}` and, on `200`,
       patches `ChatService`'s `_details` map with the returned
       `ConversationDetail` (Red).
- [x] 183. Implement `rename` (Green).
- [x] 184. Test: `rename`'s `400` (blank title/invalid icon), `403` (not
       group admin — note: `403`, not `404`, unlike the RAG case), `404`
       (unknown/wrong-kind/deleted) each leave `_details` untouched with
       an inline error (Red).
- [x] 185. Implement that error handling (Green).
- [x] 186. Test: `ChatService.createConversation` forwards an optional
       `icon` field verbatim in its existing `POST
       /api/chat/conversations` body when provided (Red).
- [x] 187. Implement that additive field (Green).

### 13c. RAG creation dialog (REQ-7 Amended (4), REQ-38)

- [x] 188. Test: `CreateConversationDialogComponent` disables submit
       until a non-blank name is entered (icon optional), mirroring
       `create-group-dialog.component.ts`'s existing pattern (REQ-38)
       (Red).
- [x] 189. Implement that validation gating (template-driven signal-bound
       state, no `ReactiveFormsModule`) (Green).
- [x] 190. Test: submitting calls `ConversationService.create(tenantId,
       title, icon)` and, on success, opens the new conversation as
       active in the conversation column, identically to today's
       create-and-open behavior otherwise (Red).
- [x] 191. Implement that submit wiring (Green).
- [x] 192. Test: a failed creation (`400`/`403`) shows an inline error
       and keeps the dialog open with the entered name/icon intact
       (REQ-41) (Red).
- [x] 193. Implement that error handling (Green).
- [x] 194. Test: `chat-shell.component.ts`'s `onOpenArticles()` now opens
       `CreateConversationDialogComponent` instead of calling
       `conversationService.create(tenantId)` with no name (REQ-7,
       Amended (4)) (Red).
- [x] 195. Implement that dispatch change; delete the now-dead direct
       `conversationService.create(tenantId)` no-args call path (Green).

### 13d. RAG rename affordance (REQ-39)

- [x] 196. Test: a pencil-icon rename affordance renders in the RAG
       conversation's own header (column 2) only when the viewer is that
       conversation's owning participant — reuses the existing
       "owns/participant" computed REQ-36's clear-affordance gating
       already established (Red).
- [x] 197. Implement that gating + affordance (Green).
- [x] 198. Test: activating it opens an inline edit form (name input +
       `icon-picker.component.ts`, prefilled with the current
       title/icon) in place of the header, with save/cancel (Red).
- [x] 199. Implement that inline form (Green).
- [x] 200. Test: saving calls `ConversationService.rename(...)` and, on
       success, updates the row's displayed title/icon in column 1
       without a full page reload (REQ-39) — asserted via whichever
       shared signal (`ChatDirectoryRowsService`/`ChatService`) column 1
       reads from (Red).
- [x] 201. Implement that success-path patch (Green).
- [x] 202. Test: a failed rename (`400`/`404`) shows an inline error and
       leaves the row's displayed name/icon unchanged (REQ-41) (Red).
- [x] 203. Implement that error handling (Green).

### 13e. Group creation dialog gains icon picker (REQ-13, final round)

- [x] 204. Test: `create-group-dialog.component.ts` renders
       `icon-picker.component.ts`, optional (submit stays enabled with no
       icon chosen — only name + visibility remain required, unchanged
       from before this amendment) (Red).
- [x] 205. Implement that addition (Green).
- [x] 206. Test: submitting with an icon chosen includes `icon` in the
       `POST /api/chat/conversations` body (via
       `ChatService.createConversation`'s new field); submitting with
       none chosen omits it (Red).
- [x] 207. Implement that wiring (Green).

### 13f. Group rename affordance (REQ-40)

- [x] 208. Test: a pencil-icon rename affordance renders in a group's own
       header (column 2) only when the viewer is that group's admin
       (`adminUserIds.includes(currentUserId)`, the exact computed
       `group-admin-panel.component.ts` already derives) (Red).
- [x] 209. Implement that gating + affordance (Green).
- [x] 210. Test: activating it opens the same inline edit form shape as
       13d (name + `icon-picker.component.ts`, prefilled), with
       save/cancel (Red).
- [x] 211. Implement that inline form, reusing the same presentational
       sub-component as 13d rather than a second copy (Green).
- [x] 212. Test: saving calls `ChatGroupService.rename(...)` and, on
       success, updates the group's displayed title/icon everywhere it
       appears — its own header, column 1's row, search results (column
       3) — without a full page reload (REQ-40) (Red).
- [x] 213. Implement that success-path patch, via `ChatGroupService
       .rename`'s existing `_details`-map patch (Green — should be
       largely free once `_details` is patched, since column 1/3 already
       read from that shared state; write the test anyway as its own
       regression anchor).
- [x] 214. Test: a failed rename (`400`/`403`/`404`) shows an inline
       error and leaves the group's displayed name/icon unchanged
       everywhere (REQ-41) (Red).
- [x] 215. Implement that error handling (Green).

### 13g. Row rendering of custom title/icon (extends existing rendering)

- [x] 216. Test: `chat-directory.component.ts`'s article rows render
       each conversation's own `title` (already the case since
       `title` was always displayed) and, when `icon` is set, that
       `IconKey`'s Lucide icon instead of the generic fallback icon
       (Red).
- [x] 217. Implement that icon rendering, reusing the `IconKey` → Lucide
       component lookup table added in 13a (Green).
- [x] 218. Test: `chat-directory.component.ts`'s group rows render a
       group's own `icon` when set, falling back to the existing default
       otherwise (same lookup table) (Red).
- [x] 219. Implement that rendering (Green).
- [x] 220. Test: a RAG/group row with no `icon` set (including every
       pre-Amendment-(4) row, per the V32 backfill — `icon` stays `null`
       for those) renders the existing default/fallback icon, not a
       broken/blank one (Red).
- [x] 221. Confirm/implement that fallback (Green).

### 13h. i18n and verification

- [x] 222. Add i18n keys: RAG creation dialog (name label, icon-picker
       label, submit-disabled hint), rename affordance labels
       (`aria-label` for the pencil button, save/cancel), icon-picker's
       24 per-icon `aria-label`s (human-readable names, not raw enum
       keys), inline rename-error copy.
- [x] 223. Run
       `npm run format:check && npm test && npm run build && npm run lint`
       for section 13 and commit incrementally per this repo's
       atomic-commit convention — do not batch the whole amendment into
       one commit.
- [x] 224. Update `PLAN.md`'s "Amendment (4) reconciliation" section (or
       add a new "Emergent decisions, Amendment (4)" section) with
       anything discovered while executing 13a–13g, following this
       PLAN's own established precedent for documenting deviations.
- [x] 225. Update `SPEC.md`'s Amended-(4) acceptance-criteria checkboxes
       to reflect what's now verified.

## 14. Amendment (2026-08-10): persistent search bar — shell/layout only (REQ-1/REQ-2c/REQ-42–REQ-47, REQ-8/REQ-9 removal)

> See `PLAN.md`'s "Amendment (2026-08-10) — persistent search bar"
> section for the full rationale. **This section covers only the
> shell/layout half owned by this feature** — the new header region's
> mounting point, removing the two retired per-column search inputs
> (while preserving column 1/3's browsing/partition logic untouched),
> deleting the now-dead `chat-directory-search.util.ts`, and layout
> regression coverage. `chat-unified-search.component.ts`'s own internal
> behavior (query semantics, debounce, grouping, recent places, overlay
> dismissal logic) is owned by
> `chat-message-search/TASKS.md` — not duplicated or re-tested here;
> this section only asserts the component is mounted and rendered, not
> what it does internally.

### 14a. `ChatShellComponent` header region

- [x] 226. Test: `ChatShellComponent` renders a new `<header>` region
       (`chat-search-bar-region`) above the existing 3-column container,
       containing exactly one `<app-chat-unified-search>` element — use a
       stub/fake component for `ChatUnifiedSearchComponent` in this spec
       (the real component's internals belong to
       `chat-message-search/TASKS.md`) (Red).
- [x] 227. Implement the new header region + mounting
       `ChatUnifiedSearchComponent` with no inputs/outputs, above the
       existing 3-column flex/grid container (Green).
- [x] 228. Test: the header region and its `<app-chat-unified-search>`
       child render identically regardless of which `section`/narrow-
       viewport pane is currently active — parametrize over every
       existing section-dispatch case already covered by
       `chat-shell.component.spec.ts` (people/groups/support/articles,
       and the 3-way narrow-viewport collapse from Amendment (3)),
       asserting the header survives every one of them (Red — this is
       REQ-42's core "never disappears" claim).
- [x] 229. Confirm task 228 passes given task 227's implementation
       (Green — the header sits outside the pane-dispatch conditional by
       construction; if it doesn't pass, the header was accidentally
       nested inside a conditionally-rendered pane and must move up).
- [x] 230. Test: the header region and its child are reachable in DOM/tab
       order before the 3-column container (matching visual top-to-bottom
       order) (Red).
- [x] 231. Implement/confirm that DOM ordering (Green).

### 14b. Remove column 1's own search input, preserve browsing/partition logic

- [x] 232. Test: `chat-directory.component.ts` renders
       `rowsService.conversationRows()`'s full, unfiltered row set
       directly — including a fixture row that would previously have
       been excluded by a stale `unifiedQuery` value — with no `<input>`
       matching the old search `data-testid`/`aria-label` anywhere in the
       rendered DOM (Red).
- [x] 233. Implement: delete the `unifiedQuery` writable signal, its
       `<input>` template markup, its `aria-label`, and the `computed()`
       that filtered `rowsService.conversationRows()`; change the
       template to render `rowsService.conversationRows()` directly.
       `ChatDirectoryRowsService`'s own computed chain
       (`talkedPeople`/`groupRows`/`articleRows`/`supportRow` merge,
       pinned-Support-first ordering) is untouched by this task (Green).
- [x] 234. Test: Support's pinned-first ordering and every existing
       REQ-2/REQ-2d row-click/active-state/create-or-open/join/
       request-to-join behavior from the Amendment (3) suite still passes
       unmodified after the search-field removal (Red — extends the
       existing suite rather than adding new behavior).
- [x] 235. Confirm task 234 passes as-is (Green — no new implementation
       expected; if it fails, the removal in task 233 touched something
       beyond the search field/filtering computed and must be scoped
       back down).

### 14c. Remove column 3's own directory search input, preserve its logic

- [x] 236. Test: `chat-full-directory.component.ts` renders
       `rowsService.discoveryRows()`'s full, unfiltered set directly —
       including a fixture row that would previously have been excluded
       by a stale `searchQuery` value — with no search `<input>` anywhere
       in the rendered DOM (Red).
- [x] 237. Implement: delete the `searchQuery` signal, its `<input>`, its
       `aria-label`, and the `filterByQuery`-based `computed()`; render
       `rowsService.discoveryRows()` directly. `discoveryRows()`'s own
       sort order (or its documented interim alphabetical fallback, per
       the "Cross-surface recency sort" decision, unchanged by this
       amendment) is untouched (Green).
- [x] 238. Test: REQ-2d's sort order (or its documented interim
       alphabetical fallback), and every existing click-to-open-or-create/
       join/request-to-join behavior in column 3, still pass unmodified
       after the search-field removal (Red).
- [x] 239. Confirm task 238 passes as-is (Green — same "removal-only,
       no logic change" expectation as task 235).

### 14d. Delete `chat-directory-search.util.ts`

- [x] 240. Grep the codebase for any remaining import of
       `chat-directory-search.util` (beyond the two call sites removed in
       14b/14c) — confirm none remain before deleting.
- [x] 241. Delete `chat-directory-search.util.ts` and its spec (if one
       exists) now that both call sites (`chat-directory.component.ts`,
       `chat-full-directory.component.ts`) no longer import it (Green —
       no separate Red; task 240's grep is the pre-condition check).

### 14e. Layout regression — REQ-42's "never disappear" requirement

- [x] 242. Test: at each of this app's existing mobile/tablet/desktop
       breakpoints (reusing the same breakpoint set already exercised by
       Amendment (3)'s 3-way collapse test, task 159/160), the search bar
       header region remains present and visible in the DOM regardless of
       which single pane is currently shown at the narrow-viewport
       collapse (Red).
- [x] 243. Confirm/implement whatever CSS is needed so the header region
       is never included in the pane-collapse logic — it sits outside the
       collapsing 3-column container by construction (per 14a), so this
       should already be satisfied; only add CSS if the test in 242
       reveals a gap (Green).
- [x] 244. Test: the overlay dropdown's absolute positioning does not
       reflow or resize the 3-column container's own layout — assert the
       columns' container element's computed layout classes are
       unchanged whether the (stubbed) `<app-chat-unified-search>` is
       reporting an open or closed state, matching PLAN.md's
       "does not push column content down" decision (Red — uses a
       stub/fake for the inner overlay-open state, since the overlay's
       own open/close mechanics are owned by `chat-message-search`'s own
       component and tests).
- [x] 245. Implement/confirm the header region's own container uses
       fixed-height, non-flow-affecting CSS (e.g. the header itself does
       not grow/shrink based on the dropdown's open state) so task 244
       passes (Green).

### 14f. Verification and documentation

- [x] 246. Run
       `npm run format:check && npm test && npm run build && npm run lint`
       for section 14 (this section's tasks only — `chat-unified-search
       .component.ts`'s own tests are verified as part of
       `chat-message-search/TASKS.md`'s own verification task) and commit
       incrementally per this repo's atomic-commit convention — do not
       batch the whole amendment into one commit.
- [x] 247. Update `PLAN.md`'s "Amendment (2026-08-10)" section (or add a
       new "Emergent decisions, Amendment (2026-08-10)" section) with
       anything discovered while executing 14a-14e, following this PLAN's
       own established precedent for documenting deviations.
- [x] 248. Update `SPEC.md`'s Amended-(5) acceptance-criteria checkboxes
       for REQ-1/REQ-2c/REQ-42–REQ-47/REQ-8/REQ-9-removal to reflect
       what's now verified by this section's tests — leave any criterion
       that depends on `chat-unified-search.component.ts`'s own internal
       behavior (owned by the companion feature) unchecked with a note
       pointing at `chat-message-search/TASKS.md` instead of duplicating
       that feature's own verification here.
- [x] 249. Update `../../../../PROJECT_STATUS.md` with an entry
       documenting the persistent-search-bar shell/layout landing (new
       header region on `ChatShellComponent`, retirement of column 1/3's
       own search inputs and `chat-directory-search.util.ts`, cross-
       reference to `chat-message-search`'s companion entry for the
       search behavior itself) — a concurrent agent may also be updating
       this file for the companion feature; write a reasonable entry
       without worrying about exact wording, conflicts get merged at
       execution time.

## 11. Final verification

- [ ] 130. Run
       `npm run format:check && npm test && npm run build && npm run lint`
       and confirm everything is green — `npm run lint` is mandatory,
       per this subproject's `CLAUDE.md`. Covers sections 1–9, 12, and 13
       (all now unblocked or fully implemented at this point) — 12e/12h/
       141/142 remain BLOCKED and stay excluded from this pass.
- [ ] 131. Update `PLAN.md`'s "Reconciliation status" section (or add an
       "Emergent decisions" section) with anything that changed during
       implementation, following `internal-team-chat/PLAN.md`'s
       precedent for documenting deviations discovered while executing
       tasks.
- [ ] 132. Update `SPEC.md`'s acceptance-criteria checkboxes to reflect
       what's now verified by tests.
- [ ] 133. Update `internal-team-chat/SPEC.md` and `conversations/SPEC.md`
       in place, per this SPEC's own header instruction, to point their
       affected lines (REQ-1/2/3's "Nova conversa" flow,
       "unrelated, unchanged" RAG-chat boundary; REQ-1's "dedicated
       `/conversations` route") at `chat-unified-ui/SPEC.md` as the
       superseding document — the approval trail `constitution.md`'s
       "Approved applies to changing an existing SPEC's scope" rule
       requires.
