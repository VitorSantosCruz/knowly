# PLAN — Conversations (chat UI)

> The how. Translates the SPEC into concrete technical decisions.
> References SPEC.md.

## Architectural decisions

- New route `/conversations`, `ConversationsPageComponent` — split-pane
  layout: a conversation list sidebar + the active conversation's
  transcript, mirroring the `members` feature's list+detail pattern
  (one page component owning both, rather than a separate route per
  pane) but rendered side-by-side instead of list-then-panel, since chat
  benefits from both being visible at once.
- `ConversationService` (`core/conversation.service.ts`): `list`,
  `create`, `getDetail` wrap the three JSON endpoints exactly like
  `MemberService`. `sendMessage` is different — it must stream a
  `text/event-stream` response from a `POST` with a JSON body and the
  session cookie, which the native `EventSource` API cannot do
  (`EventSource` is GET-only, no request body). Uses the Fetch API
  directly (`fetch(..., {method: 'POST', credentials: 'include'})`) and
  reads the response body via `ReadableStream`/`TextDecoder`, parsing
  `event:`/`data:` lines itself into an `Observable<ChatStreamEvent>`
  (`{type: 'message', data: string} | {type: 'done'} | {type: 'error',
  data: string}`) — a small hand-rolled parser rather than a library,
  since the wire format is simple (three event types, one per line
  pair) and pulling in an SSE client library for this alone isn't
  justified.
- `ConversationsPageComponent` owns the transcript as a plain array of
  `{role, content}` signals; on send, appends the user message
  immediately (REQ-4), appends a placeholder assistant message, and
  updates that placeholder's `content` as `message` events arrive,
  disabling the input (REQ-5) until `done`/`error`.
- Reuses `NoAccessStateComponent`/`ErrorStateComponent` for REQ-6/REQ-7,
  same convention as `dashboard`/`members`.
- `ActiveTenantService` (already exists) supplies the tenant id the
  conversations endpoints need in their path — unlike the metrics
  endpoints, `conversations` endpoints keep `{tenantId}` in the path
  (see the backend's own PLAN.md), so this frontend feature needs it
  too, same as `members`.
- `Permission` union type gains `CONVERSATION_USE` and `DASHBOARD_VIEW`
  (the backend added both; the frontend's admin permission-toggle list
  in `members` was missing them) — a small housekeeping fix bundled
  into this feature since it touches the same file.

## State and data

- `conversations: Signal<ConversationSummary[]>`
- `activeConversationId: Signal<number | null>`
- `messages: Signal<Message[]>` (the active conversation's transcript,
  including the in-progress streamed assistant message)
- `sending: Signal<boolean>` — disables the input while a response
  streams (REQ-5)
- `error: Signal<'network' | 'permission-denied' | null>`

## Consumed API contracts

All already implemented in `knowly` (`specify/features/conversations/`
there):

- `POST /api/tenants/{tenantId}/conversations` → `201 { id, title }`
- `GET /api/tenants/{tenantId}/conversations` → `200
  Array<{ id, title }>`
- `GET /api/tenants/{tenantId}/conversations/{id}` → `200
  { id, title, messages: Array<{ id, role, content }> }`
- `POST /api/tenants/{tenantId}/conversations/{id}/messages`
  (`{ content: string }`) → `text/event-stream`: `event: message` /
  `data: <delta>` repeated, then `event: done`, or `event: error` /
  `data: <message>` on failure.
- `403 { code: 'PERMISSION_DENIED' }` on any of the above without
  `CONVERSATION_USE`.

## Package/file structure

- `core/conversation.service.ts` (+ `.spec.ts`)
- `features/conversations/conversations-page.component.ts` (+ `.spec.ts`)
- `core/permission.ts`: add `CONVERSATION_USE`, `DASHBOARD_VIEW`.
- `app.routes.ts`: register `/conversations`.

## Testing strategy

- `ConversationService`: HTTP method/URL/body assertions via
  `HttpTestingController` for `list`/`create`/`getDetail`, same pattern
  as `MemberService`; `sendMessage`'s SSE parsing tested against a
  hand-built `ReadableStream` fixture emitting the three event types
  (message/done/error), asserting the emitted `Observable` events —
  `fetch` itself is mocked at the boundary since Vitest's jsdom
  environment doesn't drive a real network stream.
- `ConversationsPageComponent`: renders the conversation list on load;
  selecting one loads its messages; sending a message shows it
  immediately then streams the mocked assistant reply into the
  transcript; input disabled while streaming, re-enabled on
  done/error; a stream error event shows an inline error rather than
  hanging; a 403 on load shows `NoAccessStateComponent`.

## Emergent decisions

- `ChatStreamEvent` gained a fourth variant, `{type: 'permission-denied'}`,
  distinct from `{type: 'error', data: string}`. Reasoning: a 403 response
  to the streaming endpoint is a JSON error body, not an SSE stream —
  `ConversationService.pump` now checks `response.ok` before attempting
  to read/parse the body at all, since parsing a JSON body as SSE lines
  would silently produce no events and leave the caller's `sending` state
  stuck forever (no `done` or `error` event would ever arrive). A plain
  `error` variant instead handles any other non-403 failure with a
  generic message.
- Every conversations action (list, new-conversation, select, send)
  catches its own 403 independently rather than only the initial list
  load — matches the `members` feature's precedent after the same gap
  was found and fixed there.
