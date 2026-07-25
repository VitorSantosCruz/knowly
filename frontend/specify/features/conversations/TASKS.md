# TASKS — Conversations (chat UI)

> Atomic, sequential, verifiable tasks derived from PLAN.md.
> Each "Implement" task ends with `npm run format` and a small
> Conventional Commit before moving on.

## 0. Housekeeping

- [x] 1. Add `CONVERSATION_USE`/`DASHBOARD_VIEW` to `core/permission.ts`.

## 1. Conversation service (REQ-1, REQ-2, REQ-3)

- [x] 2. Test: `ConversationService.list`/`create`/`getDetail` call the
      right method/URL/body (Red).
- [x] 3. Implement `ConversationService.list`/`create`/`getDetail`
      (Green).
- [x] 4. Test: `ConversationService.sendMessage` parses a fixture SSE
      stream (mocked `fetch`) into `message`/`done`/`error` events
      (Red).
- [x] 5. Implement `ConversationService.sendMessage` (Green).
- [x] 5a. (Emergent) Test + implement: a non-`ok` `fetch` response
      (403 or other) is detected *before* attempting to parse the body
      as SSE — a 403 emits `{type: 'permission-denied'}`, anything else
      emits a generic `{type: 'error'}` — since a JSON error body isn't
      valid SSE and would otherwise leave the caller's "sending" state
      stuck with no `done`/`error` event ever arriving.

## 2. Conversations page — list and detail (REQ-1, REQ-2, REQ-3, REQ-6, REQ-8)

- [x] 6. Test: `ConversationsPageComponent` renders the conversation list
      on load (Red).
- [x] 7. Implement `ConversationsPageComponent` + route `/conversations`
      (Green).
- [x] 8. Test: starting a new conversation creates it and makes it
      active (Red).
- [x] 9. Implement the new-conversation action (Green).
- [x] 10. Test: selecting a past conversation loads and shows its
       messages, visually distinguishing user/assistant (Red).
- [x] 11. Implement conversation selection + transcript rendering
       (Green).
- [x] 12. Test: a 403 on list shows `NoAccessStateComponent` (Red).
- [x] 13. Implement the error/permission-denied state (Green) — applied
       independently to list, new-conversation, select, and send-message
       (each catches its own 403 rather than only the initial load).

## 3. Sending messages and streaming (REQ-4, REQ-5, REQ-7)

- [x] 14. Test: sending a message shows it immediately, then appends
       streamed deltas into the assistant's message as they arrive
       (Red, mocked `ConversationService.sendMessage`).
- [x] 15. Implement the send action + streaming append (Green).
- [x] 16. Test: the input is disabled while streaming and re-enabled on
       completion (Red).
- [x] 17. Implement the disabled-while-sending state (Green).
- [x] 18. Test: a stream `error` event shows an inline error on that
       message instead of hanging (Red).
- [x] 19. Implement the inline stream-error state (Green).

## 4. Final verification

- [x] 20. Run `npm run format:check && npm test && npm run build` and
       confirm everything is green.
- [x] 21. Update `PLAN.md`'s "Emergent decisions" if anything changed
       (see 5a above).
- [x] 22. Update `SPEC.md`'s acceptance-criteria checkboxes.
