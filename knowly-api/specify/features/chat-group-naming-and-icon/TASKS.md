# TASKS — Group (`ChatConversation`) rename and icon

> Atomic, sequential, verifiable tasks derived from PLAN.md.
> Each "Implement" task ends with `./mvnw spotless:apply && ./mvnw verify`
> and a small Conventional Commit before moving on.
>
> **Coordinate task 1 with whoever implements `conversations`' own
> Amended-2026-08-09 tasks** — the `IconKey` enum is shared. Whichever
> feature lands first creates it; the second only reuses it (skip task 1
> if it already exists, note that in the commit message).

## 1. Shared `IconKey` foundation

- [x] 1. Create `br.com.conectabyte.knowly.icon.IconKey` (skip if
       `conversations`' own task already created it — confirm by
       checking for the package/class first). Values confirmed against
       `knowly-app`'s actual Lucide icon-picker catalog, not invented
       independently.

## 2. Group creation gets an optional icon (REQ-1, REQ-4)

- [x] 2. `V33__add_icon_to_chat_conversations.sql` (confirm actual
       next-available version number at implementation time): add
       `icon` column to `chat_conversations` and `chat_conversations_aud`.
- [x] 3. Test: `ChatConversation.icon` persists and round-trips as an
       `IconKey` (Red).
- [x] 4. Implement the `icon` field on `ChatConversation` (Green).
- [x] 5. Test: `POST /api/chat/conversations` (`kind=GROUP`) with a
       valid `icon` persists it and returns it in the response (Red).
- [x] 6. Test: `POST /api/chat/conversations` (`kind=GROUP`) with an
       invalid `icon` string returns `400`, no group created (Red).
- [x] 7. Test: `POST /api/chat/conversations` (`kind=DIRECT`) with an
       `icon` present is ignored (or rejected — pick and assert one
       behavior explicitly per PLAN.md's note) (Red).
- [x] 8. Implement `icon` on `CreateChatConversationRequestDto` +
       `ChatConversationService#createConversation` (Green, tasks 5-7).

## 3. Group rename endpoint (REQ-2, REQ-3)

- [x] 9. Test: a current group admin renames a group's `title`/`icon`
       via `PUT /api/chat/conversations/{id}`; response reflects both;
       a subsequent `GET` confirms persistence (Red).
- [x] 10. Test: a non-admin participant of the target group gets `403`
       on rename (Red).
- [x] 11. Test: an admin of a **different** group gets `403` when
       attempting to rename this one (per-conversation scoping matrix,
       AppSec precedent) (Red).
- [x] 12. Test: an unknown/wrong-kind/deleted conversation id returns
       `404` (Red).
- [x] 13. Test: a blank `title` or invalid `icon` on rename returns
       `400`; the group's prior `title`/`icon` unchanged afterward (Red).
- [x] 14. Implement `RenameChatConversationRequestDto` +
       `ChatConversationService#renameConversation` (reusing
       `requireGroupAdmin`) + `ChatController`'s `PUT
       /conversations/{id}` (Green, tasks 9-13).
- [x] 15. Confirm whether `icon` needs adding to
       `ChatConversationSummaryDto` (used by column 1's row list) in
       addition to `ChatConversationDetailDto`, per PLAN.md's open note
       — add to both if the summary DTO is what the list view actually
       renders from.
- [x] 16. Add `@AuditLog(action = "chat.group.rename", ...)` on the
       rename endpoint, following this codebase's existing
       `chat.group.*` action-naming convention.

## 4. Final verification

- [x] 17. Run `./mvnw spotless:apply && ./mvnw verify` (full suite) and
       confirm everything is green.
- [x] 18. Update `PLAN.md`'s architectural decisions if anything changed
       during implementation (especially the open "summary vs. detail
       DTO" and "ignore vs. reject icon on DIRECT" calls).
- [x] 19. Update `SPEC.md`'s acceptance-criteria checkboxes to reflect
       what's now verified by tests.
- [ ] 20. Once both this feature and `conversations`' Amended
       2026-08-09 tasks are done, confirm with `frontend-engineer`
       (owning `chat-unified-ui` REQ-38 through REQ-41) that the actual
       endpoint paths/DTOs match what that frontend PLAN assumed —
       correct the frontend PLAN's contract table if not, mirroring
       `chat-group-membership-management`'s own "Frontend contract
       reconciliation" precedent rather than silently changing the
       backend to match a pre-existing frontend guess.
