# TASKS — internal-team-chat (frontend)

> Atomic, sequential, verifiable tasks derived from PLAN.md.
> Each "Implement" task ends with `npm run format` and a small
> Conventional Commit before moving on.

## 1. Shared prerequisites

- [ ] 1. Add `chat-permission.ts` re-exporting `'STAFF_SUPPORT_HANDLE'`
      (as a `GlobalPermission` literal) and `'SUPPORT_CHANNEL_VIEW'` (as
      a `Permission` literal), per the backend PLAN's confirmed string
      literals — no test needed (pure constant re-export), but verify
      the literals against `knowly-api`'s `GlobalPermission`/`Permission`
      enums by grep before committing.
- [ ] 2. Add shared DTO types (`ConversationSummary`, `CandidateUser`,
      `Message`, `MessagePage`, `SupportChannelSummary`, `TicketSummary`)
      to `core/chat.model.ts`, matching the "Consumed API contracts"
      section verbatim.

## 2. `chat.service.ts` (REQ-1, REQ-2, REQ-3, REQ-5, REQ-6, REQ-19, REQ-21)

- [ ] 3. Test: `ChatService.fetchConversations()` calls
      `GET /api/chat/conversations` and populates `conversations()`
      (Red).
- [ ] 4. Implement `fetchConversations` (Green).
- [ ] 5. Test: `ChatService.createConversation(request)` calls
      `POST /api/chat/conversations` with the `DIRECT`/`GROUP` payload
      shape and appends the created `ConversationSummary` to
      `conversations()` on success (Red).
- [ ] 6. Implement `createConversation` (Green).
- [ ] 7. Test: `ChatService.openConversation(id)` calls
      `GET /api/chat/conversations/{id}` and
      `GET /api/chat/conversations/{id}/messages?size=30` (first page),
      seeding the per-conversation cache entry (`messages`, `hasMore`,
      `oldestCursor`) (Red).
- [ ] 8. Implement `openConversation` (Green).
- [ ] 9. Test: `ChatService.loadOlderMessages(id)` calls
      `GET .../messages?before={oldestCursor}&size=30`, **prepends**
      the returned page without duplicating any already-loaded message
      id, and updates `oldestCursor`/`hasMore` from the response (Red).
- [ ] 10. Implement `loadOlderMessages` (Green).
- [ ] 11. Test: a failed `loadOlderMessages` call leaves the existing
      cached messages untouched and exposes a per-conversation
      `loadError` flag the caller can read to show retry (REQ-21) (Red).
- [ ] 12. Implement that error-preserving behavior (Green).
- [ ] 13. Test: `ChatService.pollNewMessages(id)` calls
      `GET .../messages?after={newestCursor}&size=30` and **appends**
      the returned page without duplicating any already-loaded message
      id, no-op on an empty page (Red).
- [ ] 14. Implement `pollNewMessages` (Green).
- [ ] 15. Test: `ChatService.sendMessage(id, content)` optimistically
      appends a message with a `pending` flag, then on success replaces
      it with the server-confirmed `Message`, and on failure marks it
      `failed` instead of removing it (REQ-5, REQ-6) (Red).
- [ ] 16. Implement `sendMessage` (Green).
- [ ] 17. Test: retrying a `failed` message (same service method,
      re-invoked for that pending entry) clears the failed flag on
      success and re-marks it failed on repeated failure, without adding
      a duplicate message entry (Red).
- [ ] 18. Implement the retry path (Green).
- [ ] 19. Test: `ChatService.fetchEligibleParticipants(scope, tenantId?)`
      calls the three `GET /api/chat/eligible-participants` variants
      (`scope=direct`, `scope=group&tenantId=`, `scope=group-staff-only`)
      with no client-side filtering of the response (Red).
- [ ] 20. Implement `fetchEligibleParticipants` (Green).

## 3. `support.service.ts` (REQ-10..18)

- [ ] 21. Test: `SupportService.fetchMyChannel(tenantId, userId)` calls
      `GET /api/tenants/{tenantId}/support/members/{memberUserId}/channel`
      and populates `myChannel()` (Red).
- [ ] 22. Implement `fetchMyChannel` (Green).
- [ ] 23. Test: `SupportService.openTicket(tenantId)` calls
      `POST /api/tenants/{tenantId}/support/tickets` and refreshes
      `myChannel()`'s `openTicket` on success, surfaces the 409
      "already has an open ticket" case without retry-looping (Red).
- [ ] 24. Implement `openTicket` (Green).
- [ ] 25. Test: `SupportService.fetchInbox(tenantId)` calls
      `GET /api/tenants/{tenantId}/support/tickets/unclaimed` and
      populates `inboxTickets()`, merging results across multiple
      tenant ids into one signal without duplicate ticket ids (Red).
- [ ] 26. Implement `fetchInbox` (Green).
- [ ] 27. Test: `SupportService.claim(tenantId, ticketId)` calls
      `POST .../tickets/{ticketId}/claim` and patches that ticket's
      `TicketSummary` in place (removes it from `inboxTickets()`,
      updates status to `ASSIGNED`) on success (Red).
- [ ] 28. Implement `claim` (Green).
- [ ] 29. Test: `SupportService.transfer(tenantId, ticketId, toUserId)`
      calls `POST .../transfer` and patches the ticket's
      `assignedStaffUserId`/`assignedStaffNickname` in place on success
      (Red).
- [ ] 30. Implement `transfer` (Green).
- [ ] 31. Test: `SupportService.close(tenantId, ticketId)` calls
      `POST .../close` and patches the ticket's `status`/`closedAt` in
      place on success (Red).
- [ ] 32. Implement `close` (Green).
- [ ] 33. Test: `SupportService.openChannel(tenantId, memberUserId)`
      loads the channel's first message page the same
      prepend/append-without-duplicating way as `ChatService` (shared
      cursor logic, REQ-19/21) — cover load-older and poll-after (Red).
- [ ] 34. Implement `openChannel`'s message cache, reusing the same
      cursor-append/prepend approach as `chat.service.ts` (Green).
- [ ] 35. Test: `SupportService.sendMessage(tenantId, memberUserId,
      content)` calls `POST .../channel/messages`, optimistic-append +
      pending/failed flag identical to `ChatService.sendMessage` (Red).
- [ ] 36. Implement `sendMessage` (Green).

## 4. `message-thread.component.ts` (shared, REQ-19, REQ-20, REQ-21)

- [ ] 37. Test: renders the `messages` input oldest-to-newest with each
      message's `senderNickname` and content (Red).
- [ ] 38. Implement the base rendering (Green).
- [ ] 39. Test: a "load more" control emits `loadMore` only when
      `hasMore` is `true`, and is absent/disabled when `false` (Red).
- [ ] 40. Implement `loadMore` wiring (Green).
- [ ] 41. Test: while `loading` is `true`, a loading indicator renders
      local to the history area (not a full-screen state), and the
      already-rendered messages stay visible (REQ-20) (Red).
- [ ] 42. Implement the local loading indicator (Green).
- [ ] 43. Test: when `loadError` is truthy, an inline retry control
      renders (with `aria-label`) and emits `loadMore` again on click,
      without hiding already-loaded messages (REQ-21) (Red).
- [ ] 44. Implement the retry control (Green).
- [ ] 45. Test: `showComposer=true` renders `message-composer.component.ts`
      and wires its `send` output to the `send` output; `showComposer=false`
      renders no composer at all (Red).
- [ ] 46. Implement the conditional composer + `message-composer.component.ts`
      (single textarea + submit, signal-bound value, no `ReactiveFormsModule`)
      (Green).
- [ ] 47. Test: a message with a `failed` flag shows an inline error and
      a retry action (REQ-6), distinct from a `pending` (in-flight)
      message's rendering (Red).
- [ ] 48. Implement the `pending`/`failed` per-message rendering (Green).
- [ ] 49. Test: `send`, `load more`, and retry controls each expose an
      explicit `aria-label` (accessibility bar) (Red).
- [ ] 50. Implement the `aria-label`s (Green).

## 5. Peer chat: conversation list + item (REQ-1, REQ-4, REQ-7, REQ-8)

- [ ] 51. Test: `ConversationListComponent` renders `conversations()`
      from `ChatService`, calling `fetchConversations()` on init (Red).
- [ ] 52. Implement `ConversationListComponent` (Green).
- [ ] 53. Test: `ConversationListItemComponent` renders participant
      nicknames, last-message preview, and a normal (non-oversight)
      appearance when `viewerRelation === 'PARTICIPANT'` (Red).
- [ ] 54. Implement the `'PARTICIPANT'` rendering (Green).
- [ ] 55. Test: `ConversationListItemComponent` renders a distinct
      "looking in" badge with a support/admin-oversight `aria-label`
      (never "joined" copy) when `viewerRelation === 'LOOKING_IN'`
      (Red).
- [ ] 56. Implement the `'LOOKING_IN'` badge + copy (Green).

## 6. Peer chat: conversation detail + header (REQ-4, REQ-5, REQ-6, REQ-7, REQ-8, REQ-9, REQ-19, REQ-20, REQ-21)

- [ ] 57. Test: `ConversationDetailComponent` calls
      `ChatService.openConversation(id)` on route param change and
      passes the resulting messages/hasMore/loadError into
      `message-thread.component.ts` (Red).
- [ ] 58. Implement `ConversationDetailComponent` (Green).
- [ ] 59. Test: `ChatHeaderComponent` renders participant nicknames
      normally for `viewerRelation === 'PARTICIPANT'`, and a distinct
      oversight banner (with `aria-label`, non-"joined" copy) for
      `'LOOKING_IN'` (Red).
- [ ] 60. Implement `ChatHeaderComponent`'s two renderings (Green).
- [ ] 61. Test: `ConversationDetailComponent` omits the message composer
      entirely when `viewerRelation === 'LOOKING_IN'` (REQ-7/8's
      read-only override, out-of-scope composer) (Red).
- [ ] 62. Implement that composer omission via
      `message-thread.component.ts`'s `showComposer` input (Green).
- [ ] 63. Test: a 403/404 from `openConversation` (not a participant,
      not eligible for look-in — REQ-9) renders the existing
      not-found/no-access state, not a crash (Red).
- [ ] 64. Implement that error handling reusing the existing
      `NoAccessStateComponent`/`ErrorStateComponent` (Green).
- [ ] 65. Test: while `ConversationDetailComponent` is mounted and
      `document.visibilityState === 'visible'`, an `interval(5000)`
      (piped through `takeUntilDestroyed()`) calls
      `ChatService.pollNewMessages(id)`; no poll call fires while the
      tab is hidden (Red).
- [ ] 66. Implement the visibility-gated polling interval (Green).

## 7. Peer chat: new conversation + participant picker (REQ-2, REQ-3)

- [ ] 67. Test: `ParticipantPickerComponent` renders whatever candidate
      list is passed to it as-is (a fake list mixing a staff user and a
      plain member), with no local staff/member/tenant filtering
      applied (Red).
- [ ] 68. Implement `ParticipantPickerComponent` (single or multi-select
      checkbox list over a signal-backed `Set<number>` of selected ids)
      (Green).
- [ ] 69. Test: `NewConversationDialogComponent` in "1:1" mode calls
      `fetchEligibleParticipants('direct')` and, on selecting a
      candidate, calls `ChatService.createConversation` with
      `kind: 'DIRECT'` (Red).
- [ ] 70. Implement the 1:1 creation path (Green).
- [ ] 71. Test: `NewConversationDialogComponent` in "member-only group"
      mode (anchored to a chosen tenant `T`) calls
      `fetchEligibleParticipants('group', T)` and calls
      `createConversation` with `kind: 'GROUP', tenantId: T` and the
      selected participant ids (Red).
- [ ] 72. Implement the member-only-group creation path (Green).
- [ ] 73. Test: `NewConversationDialogComponent` in "staff-only group"
      mode calls `fetchEligibleParticipants('group-staff-only')` and
      calls `createConversation` with `kind: 'GROUP', tenantId: null`
      (Red).
- [ ] 74. Implement the staff-only-group creation path (Green).
- [ ] 75. Test: a 400 (ineligible participant) or 403 from
      `createConversation` shows an inline rejection message and does
      not close the dialog or navigate anywhere (acceptance criterion:
      "prevented or clearly rejected, not silently allowed") (Red).
- [ ] 76. Implement that error handling (Green).

## 8. `/chat` route wiring

- [ ] 77. Add `ChatPageComponent` (list + outlet) and register
      `/chat`, `/chat/:conversationId` in `app.routes.ts` with **no
      guard**, per the PLAN's rationale.
- [ ] 78. Test: `ChatPageComponent` never renders a permission-denied
      state for the base conversations list itself (first acceptance
      criterion) (Red).
- [ ] 79. Implement/confirm `ChatPageComponent` (Green).

## 9. Support: member's own channel (REQ-10, REQ-11)

- [ ] 80. Test: `MemberSupportChannelComponent` renders the ticket
      history (`closedTickets`) and, `openTicket === null`, shows a
      "start support ticket" action (REQ-11) (Red).
- [ ] 81. Implement that branch (Green).
- [ ] 82. Test: when `openTicket` is non-null, it renders that ticket's
      thread via `message-thread.component.ts` instead of the start
      action (Red).
- [ ] 83. Implement that branch (Green).
- [ ] 84. Test: clicking "start support ticket" calls
      `SupportService.openTicket` and, on success, swaps to the thread
      view; a 409 ("already has an open ticket") shows an inline message
      instead of a crash (Red).
- [ ] 85. Implement that wiring (Green).

## 10. Support: staff inbox + claimed channel (REQ-12, REQ-13, REQ-14, REQ-15, REQ-16)

- [ ] 86. Test: `StaffSupportInboxComponent` renders `inboxTickets()`
      from `SupportService.fetchInbox`, called once per resolved tenant
      id (per the PLAN's "one call per tenant" decision) (Red).
- [ ] 87. Implement `StaffSupportInboxComponent` (Green).
- [ ] 88. Test: clicking "claim" on a ticket calls
      `SupportService.claim` and, on success, navigates to
      `StaffSupportChannelComponent` for that ticket/member (Red).
- [ ] 89. Implement the claim action (Green).
- [ ] 90. Test: `StaffSupportChannelComponent` loads the member's full
      channel (all tickets, open and closed — REQ-13) via
      `SupportService.openChannel`, not just the newly-claimed ticket
      (Red).
- [ ] 91. Implement that full-history load (Green).
- [ ] 92. Test: the composer is hidden (`showComposer=false`) when the
      open ticket's `assignedStaffUserId` is not the current viewer
      (REQ-14) (Red).
- [ ] 93. Implement that gating (Green).
- [ ] 94. Test: clicking "transfer" calls `SupportService.transfer` and,
      on success, the composer disappears for the current viewer (now
      no longer assignee) (REQ-15) (Red).
- [ ] 95. Implement the transfer action (Green).
- [ ] 96. Test: clicking "close" calls `SupportService.close` and, on
      success, renders the closed badge, removes the composer
      permanently, and shows no "reopen" action anywhere (REQ-16)
      (Red).
- [ ] 97. Implement the close action (Green).
- [ ] 98. Implement `TicketStatusBadgeComponent` rendering the three
      `OPEN`/`ASSIGNED`/`CLOSED` states (no separate Red — covered by
      tasks 90-97's assertions on the badge's rendered text/class).

## 11. Support: member browse (REQ-17, REQ-18)

- [ ] 99. Test: `MemberSupportBrowseComponent` (rendered only when the
      viewer holds `SUPPORT_CHANNEL_VIEW`) calls
      `SupportService.fetchMyChannel`-equivalent for a chosen other
      member and renders it read-only (`showComposer=false`) (Red).
- [ ] 100. Implement `MemberSupportBrowseComponent` (Green).
- [ ] 101. Test: a 403 (viewer lacks `SUPPORT_CHANNEL_VIEW`) renders the
       existing `NoAccessStateComponent` rather than a partial view
       (Red).
- [ ] 102. Implement that error handling (Green).

## 12. `/support` route wiring + permission dispatch

- [ ] 103. Add `SupportPageComponent` and register `/support`,
       `/support/:channelId` in `app.routes.ts` with **no guard**, per
       the PLAN's rationale.
- [ ] 104. Test: `SupportPageComponent` fetches
       `GET /api/staff/permissions` once and renders
       `StaffSupportInboxComponent`/`StaffSupportChannelComponent` when
       the viewer holds `STAFF_SUPPORT_HANDLE` (Red).
- [ ] 105. Implement that first dispatch branch (Green).
- [ ] 106. Test: absent `STAFF_SUPPORT_HANDLE`, when the viewer holds
       `SUPPORT_CHANNEL_VIEW` it renders `MemberSupportBrowseComponent`
       alongside `MemberSupportChannelComponent` (own channel) (Red).
- [ ] 107. Implement that second dispatch branch (Green).
- [ ] 108. Test: absent both permissions, it renders
       `MemberSupportChannelComponent` only (Red).
- [ ] 109. Implement that fallback branch (Green).
- [ ] 110. Test: for a pure-staff session with no real tenant
       membership, `MemberSupportChannelComponent` renders its existing
       empty/not-applicable state rather than erroring (mirrors
       `ProfileEditRequestsInboxPageComponent`'s empty list) (Red).
- [ ] 111. Implement/confirm that empty-state handling (Green).
- [ ] 112. Test: polling for `StaffSupportChannelComponent`/
       `MemberSupportChannelComponent` (visibility-gated `interval(5000)`
       calling the channel's poll-after method) mirrors task 65/66's
       peer-chat polling test exactly (Red).
- [ ] 113. Implement that polling wiring (Green).

## 13. i18n and design

- [ ] 114. Add peer-chat i18n keys to `public/i18n/en.json` /
       `pt-BR.json`: conversation list, new-conversation dialog
       (1:1/member-group/staff-group modes), participant picker,
       chat header, "looking in" banner/badge copy, composer, load-more/
       retry labels.
- [ ] 115. Add support-channel i18n keys to both locale files: member's
       own channel (start-ticket action, thread), staff inbox
       (claim/transfer/close actions), ticket status badge
       (OPEN/ASSIGNED/CLOSED), member-browse screen.
- [ ] 116. Apply the established Tailwind "Ink & Signal" design tokens
       to every new component (chat bubbles, badges, banners), reusing
       existing status-chip/badge patterns from `user-management`/
       `dashboard` rather than introducing new visual language.

## 14. Final verification

- [ ] 117. Run `npm run format:check && npm test && npm run build` and
       confirm everything is green.
- [ ] 118. Update `PLAN.md`'s "Emergent decisions"/"Reconciliation
       status" if anything changed during implementation.
- [ ] 119. Update `SPEC.md`'s acceptance-criteria checkboxes to reflect
       what's now verified by tests.
</content>
