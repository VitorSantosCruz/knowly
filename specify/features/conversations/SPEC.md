# SPEC — Conversations (RAG chat over articles)

## Context and motivation

This is the feature the `article-management` feature's knowledge base was
built for: letting a tenant user ask questions in natural language and get
answers grounded in their tenant's articles, instead of having to read
them manually. It also unblocks the `onboarding-status`/dashboard
"conversations" and "messages" metrics, which were deliberately left
showing no real data until this feature existed.

This feature covers: generating and storing embeddings for ready
articles, a conversation/message domain private to each user within their
active tenant, and a streaming chat endpoint that retrieves relevant
article context via vector similarity search and asks the configured chat
model to answer using it.

## User stories

- As a tenant user, I want to start a conversation and ask a question,
  and get an answer grounded in my tenant's articles rather than the
  model's general knowledge alone.
- As a tenant user, I want to see my own past conversations and continue
  one, without seeing conversations started by other members of the
  tenant.
- As a tenant user, I want the answer to stream in as it's generated,
  instead of waiting in silence for the full response.
- As a tenant admin, I want conversation/message usage to be a separately
  grantable permission, consistent with how article permissions work.

## Requirements (EARS/GEARS)

### Article embeddings

- **REQ-1 [Event-Driven]** When an article's text extraction finishes
  successfully ("ready"), the system shall chunk its extracted text and
  generate embeddings for each chunk, storing them in the vector store
  tagged with the article's id and tenant id.
- **REQ-2 [Event-Driven]** When an article is deleted, the system shall
  remove its embeddings from the vector store so deleted content can
  never surface in future answers.
- **REQ-3 [Unwanted Behavior]** If embedding generation fails for an
  article, then the system shall mark that article's embedding state as
  "failed" with a reason, leaving the article's own "ready" text status
  untouched (embedding failure doesn't undo a successful extraction), and
  shall not retry indefinitely.

### Conversation and message domain

- **REQ-4 [Ubiquitous]** The system shall represent a conversation as
  belonging to exactly one user within exactly one tenant, with a list of
  ordered messages, each tagged as from the user or the assistant.
- **REQ-5 [Ubiquitous]** The system shall define a `CONVERSATION_USE`
  permission, grantable directly or via access group, gating creating
  conversations and sending messages, per the tenancy feature's
  deny-by-default model.
- **REQ-6 [Ubiquitous]** Conversations and messages shall be isolated per
  tenant via the same Hibernate-filter mechanism already established for
  tenant-scoped entities, and additionally scoped to their owning user —
  a user shall never see or list another user's conversations, even
  within the same tenant.
- **REQ-7 [Ubiquitous]** Every conversation/message read and write shall
  be logged via the existing `@AuditLog` mechanism.

### Chat (retrieval-augmented generation)

- **REQ-8 [Event-Driven]** When a user with `CONVERSATION_USE` sends a
  message in a conversation (existing or new), the system shall retrieve
  the most relevant article chunks for that tenant via vector similarity
  search, include them as context in the prompt to the chat model, and
  stream the assistant's response back to the caller as it's generated
  (Server-Sent Events).
- **REQ-9 [Event-Driven]** When a message exchange completes, the system
  shall persist both the user's message and the assistant's full response
  on the conversation, in order.
- **REQ-10 [Unwanted Behavior]** If no relevant article context is found
  above a minimum relevance threshold, then the system shall still answer
  using the chat model but the assistant's response shall make clear it
  found no matching articles, rather than fabricating an answer as if it
  had.
- **REQ-11 [Unwanted Behavior]** If the chat model or embedding provider
  call fails, then the system shall end the stream with a clear error
  event rather than hanging or silently closing the connection.
- **REQ-12 [Unwanted Behavior]** If a user without `CONVERSATION_USE`
  attempts to create a conversation or send a message, then the system
  shall reject the request (403) before making any model or vector-store
  call.

## Non-functional requirements

- Security: RAG retrieval is always scoped to the caller's active tenant
  — a similarity search shall never return chunks from another tenant's
  articles, enforced at the query level (not just at the response-display
  level).
- Performance/SLA: the first token of a streamed response should begin
  arriving well before the full answer is ready (that's the point of
  streaming); embedding generation runs asynchronously and does not block
  the article-upload response.
- Observability: embedding failures and chat/model errors must emit a
  structured log, consistent with the constitution's "what must log"
  rule.

## Acceptance criteria

- [x] A "ready" article gets embeddings generated and stored, tagged by
      tenant and article id. (Verified with a mocked `VectorStore`/
      `EmbeddingModel` — no real OpenAI embedding call is exercised in
      CI, consistent with the constitution's dummy-API-key note.)
- [x] Deleting an article removes its embeddings.
- [x] A user with `CONVERSATION_USE` can create a conversation and send a
      message, receiving a streamed, article-grounded response. (Verified
      with a mocked `ChatModel`/`VectorStore`, including the actual SSE
      event names/payloads sent — one `message` event per delta plus a
      final `done` event — via a recording `SseEmitter` test double, not
      just the persisted end-state.)
- [x] The conversation and its messages are persisted in order after the
      stream completes.
- [x] A user only ever sees their own conversations, never another
      member's, even within the same tenant.
- [x] A similarity search never surfaces another tenant's article chunks.
- [x] A user without `CONVERSATION_USE` gets a 403 on conversation/message
      creation.
- [x] A model/embedding-provider failure ends the stream with a clear
      error event instead of hanging.

## Out of scope

- Editing or deleting individual messages after they're sent.
- Sharing a conversation with other tenant members (see the "private to
  the user" decision above) — a future feature if collaboration is
  requested.
- Citations/source-linking in the UI pointing back to the specific
  article a piece of the answer came from (the answer is grounded via
  context, but no structured citation metadata is returned yet).
- Conversation renaming/archiving/search.
- Non-text input to the chat (only typed messages; no voice input here —
  that's a different surface from the audio-upload-becomes-article path
  already covered by `article-management`).
