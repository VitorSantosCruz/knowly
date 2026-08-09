# SPEC — Conversations (chat UI)

> **Superseded in part by `chat-unified-ui/SPEC.md` (2026-08-09,
> implemented):** REQ-1's "dedicated `/conversations` route" is now
> folded under the shared navigation shell at `/chat?section=articles`
> (nested resource id: `/chat/articles/:conversationId`) as the "Base
> de artigos" section — content/behavior below are otherwise unchanged.
> The old `/conversations` route still resolves (redirected into the
> new path) rather than being removed.

## Context and motivation

The `knowly` backend's `conversations` feature already implements a full
RAG chat over the tenant's articles (create/list/view conversations,
send a message and get a streamed, article-grounded response), gated by
the `CONVERSATION_USE` permission. Nothing in the frontend uses it yet —
this feature is the chat screen itself, the primary way a user actually
interacts with the assistant.

## User stories

- As a tenant user with chat access, I want to ask a question and see
  the assistant's answer appear as it's generated, instead of staring at
  a blank screen until the whole response is ready.
- As a returning user, I want to see my past conversations and reopen
  one to continue it, without losing context.
- As a user without chat access, I want a clear "you don't have access"
  message instead of a broken or confusing screen.

## Requirements (EARS/GEARS)

- **REQ-1 [Ubiquitous]** The system shall show, at the `/conversations`
  route, a list of the caller's own past conversations (most recent
  first) alongside the currently open conversation's messages.
- **REQ-2 [Event-Driven]** When the user starts a new conversation, the
  system shall create it via the backend and make it the active
  conversation, ready to receive the first message.
- **REQ-3 [Event-Driven]** When the user selects a past conversation from
  the list, the system shall load and display its full message history.
- **REQ-4 [Event-Driven]** When the user sends a message, the system
  shall display it immediately in the transcript, then stream the
  assistant's response into the transcript token-by-token as it arrives,
  rather than waiting for the full response.
- **REQ-5 [State-Driven]** While a response is streaming, the system
  shall disable the message input to prevent sending a second message
  before the first completes.
- **REQ-6 [Unwanted Behavior]** If sending a message or loading the
  conversation list/detail fails with a 403, then the system shall show
  a clear "you don't have access to this" state instead of a broken
  screen.
- **REQ-7 [Unwanted Behavior]** If the stream ends with an error event
  (backend model/provider failure), then the system shall show an
  inline error on that message rather than leaving the transcript
  looking like it's still loading forever.
- **REQ-8 [Ubiquitous]** The system shall visually distinguish the
  user's messages from the assistant's in the transcript.

## Non-functional requirements

- Design: follows the established design-system standard (slate/indigo
  palette, 8pt spacing grid, consistent card/button/input states),
  consistent with `dashboard` and `members`.
- Accessibility: message input and conversation list are keyboard
  operable; streamed content updates are perceivable (not purely
  color-based).

## Acceptance criteria

- [x] The conversations screen lists the caller's own past conversations,
      most recent first.
- [x] Starting a new conversation creates it and makes it active.
- [x] Selecting a past conversation loads and shows its messages.
- [x] Sending a message shows it immediately, then streams the
      assistant's reply token-by-token.
- [x] The message input is disabled while a response is streaming.
- [x] A 403 from any conversations action shows a clear permission-denied
      message, not a raw error (list, new-conversation, select, and
      send-message each handle it independently).
- [x] A stream-ending error event shows an inline error on that message.
- [x] User and assistant messages are visually distinguishable.

## Out of scope

- Editing or deleting a conversation or an individual message (matches
  the backend's own out-of-scope).
- Renaming/titling a conversation (the backend's `title` field exists
  but nothing sets it yet — conversations are shown by date/first
  message preview instead).
- Citations UI (which article backed which part of an answer) — the
  backend tracks citations for the dashboard's usage metric only, not
  for per-message display here.
- Markdown/rich-text rendering of assistant responses — plain text for
  this first version.
