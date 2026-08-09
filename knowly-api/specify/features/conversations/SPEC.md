# SPEC — Conversations (RAG chat over articles)

> **Amended 2026-08-09 — explicit product-owner reversal of an "Out of
> scope" line, not a silent reinterpretation.** The original "Out of
> scope" section below included "Conversation renaming/archiving/search."
> The product owner has now stated directly that they never made that
> exclusion decision — their exact words: *"pode dar nome antes e depois
> eu não falei que está fora de escopo, deve ter sido um agente aí, tanto
> grupo quanto conversa com a base podem ser nomeados e renomeados."*
> (Naming is allowed at creation and later; the owner never said renaming
> was out of scope — that line was written by a prior agent, not a real
> product decision. Both groups and RAG conversations get full naming +
> renaming.) Per `DECISIONS.md`'s Tier 3 rules, this is exactly the kind
> of "changing the scope of an existing, already-approved SPEC" call that
> must never be made unilaterally by an AI — this amendment records the
> owner's own explicit reversal, with the reversal itself cited here
> rather than the line simply being deleted. **"Archiving" and "search"
> remain out of scope** — only "renaming" is reversed; see the updated
> "Out of scope" section at the bottom.
>
> This amendment adds: `title` becomes a **required** field at creation
> (previously an optional, never-populated column — `POST
> /conversations` accepted no body and never set it); a new **rename**
> capability; and a new **icon** field (fixed-key, from this app's
> existing Lucide icon library), settable at creation and via rename.
> REQ-13 through REQ-16 below are new. Everything else in this document
> is unchanged.
>
> **Companion investigation finding (2026-08-09):** groups
> (`ChatConversation`, `chat` package) already have a `title` column,
> already required at creation by the frontend's "Criar grupo" dialog
> (`create-group-dialog.component.ts`'s `submitDisabled()` — creation is
> blocked until a non-empty name is entered), but **no rename endpoint or
> capability exists for groups today** — `ChatController` has no
> `PUT`/`PATCH` route touching `title`, and `chat-group-membership-management`'s
> SPEC does not define one. Group renaming is therefore **new scope**,
> not something already satisfied — see the note in `chat-unified-ui`'s
> SPEC for the frontend-facing requirement; this document's REQ-14 below
> is written generically enough to cover both the RAG `Conversation`
> entity and, if a matching backend change is made to `ChatConversation`,
> the group case (both entities already carry an equivalent `title`
> column). Whether the group-rename endpoint is added as an amendment to
> `chat-group-membership-management` or here is a PLAN-level call, not
> resolved by this SPEC.

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
- **(Amended 2026-08-09):** As a tenant user, I want to name a "Base de
  artigos" conversation when I create it and pick an icon for it, and
  rename it (or change its icon) later, so my list of conversations is
  distinguishable at a glance instead of all looking identical.

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

### Naming, renaming, and icon (Amended 2026-08-09)

- **REQ-13 [Ubiquitous]** The system shall require a non-blank `title` on
  every new conversation — `POST .../conversations` shall accept `title`
  as a required request field (previously accepted no body/an optional,
  never-populated column) and reject a request with a missing or
  blank/whitespace-only `title`.
- **REQ-14 [Event-Driven]** When a user with `CONVERSATION_USE` who owns a
  conversation submits a rename request with a new non-blank `title` (and
  optionally a new `icon`, see REQ-15), the system shall update that
  conversation's `title`/`icon` and persist the change, without altering
  its messages or any other field.
- **REQ-15 [Ubiquitous]** The system shall support an `icon` field on a
  conversation, accepted at creation (REQ-13) and via rename (REQ-14) as
  an optional key drawn from a fixed, server-validated set of supported
  icon keys corresponding to this codebase's existing Lucide icon library
  (`@lucide/angular`, per `knowly-app/CLAUDE.md`) — not free text, not an
  emoji, not an uploaded image. A conversation created without an `icon`
  keeps its existing default/fallback presentation (PLAN to define the
  exact default and the maintained key catalog).
- **REQ-16 [Unwanted Behavior]** If a rename or create request's `title`
  is missing/blank, or its `icon` (when provided) is not one of the fixed
  supported keys, or the acting user does not own the target conversation,
  then the system shall reject the request (422/403 as appropriate)
  without applying any partial change.

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
- [x] **(Amended 2026-08-09)** Creating a conversation without a `title`
      (or with a blank one) is rejected; creating one with a `title` and
      optional `icon` persists both.
- [x] **(Amended 2026-08-09)** The owning user can rename an existing
      conversation (`title` and/or `icon`); a non-owner rename attempt is
      rejected.
- [x] **(Amended 2026-08-09)** An `icon` value outside the fixed supported
      set is rejected on both create and rename, with no partial update
      applied.

## Out of scope

- Editing or deleting individual messages after they're sent.
- Sharing a conversation with other tenant members (see the "private to
  the user" decision above) — a future feature if collaboration is
  requested.
- Citations/source-linking in the UI pointing back to the specific
  article a piece of the answer came from (the answer is grounded via
  context, but no structured citation metadata is returned yet).
- Conversation archiving/search. **("Renaming" removed from this line
  2026-08-09 — explicit product-owner reversal, see the amendment note
  at the top of this document; renaming is now in scope via REQ-14.)**
- Non-text input to the chat (only typed messages; no voice input here —
  that's a different surface from the audio-upload-becomes-article path
  already covered by `article-management`).
- **(Amended 2026-08-09)** A free-text or emoji/uploaded-image icon —
  only a fixed, server-validated key from the existing Lucide set is
  supported (REQ-15).
- **(Amended 2026-08-09)** Group (`ChatConversation`) renaming/icon is
  **not** defined by this document — this SPEC only covers the RAG
  `Conversation` entity. If group renaming/icon needs its own backend
  endpoint, that is an amendment to `chat-group-membership-management`
  (or a new small SPEC), not silently folded in here, even though the
  product owner's reversal covers both kinds conceptually. **Forward
  pointer, added 2026-08-09 (same day, later round):** the frontend SPEC
  (`knowly-app/specify/features/chat-unified-ui/SPEC.md`, Amended (4),
  final round) now confirms the product owner wants groups to get the
  same fixed Lucide icon picker as RAG conversations, at both creation
  and rename — so **both** a group-rename endpoint and a group-icon
  field (at creation and rename) are needed on `ChatConversation` before
  that frontend work can proceed past SPEC stage, not rename alone as
  first noted above. This still does not belong in this document (RAG
  `Conversation` only) — whoever picks up
  `chat-group-membership-management`'s next amendment (or a new small
  backend SPEC) needs to cover: (a) a rename endpoint accepting `title`
  and `icon` on `ChatConversation`, scoped to a viewer the backend
  reports as that group's admin, and (b) an optional `icon` field
  accepted at group creation (`POST` group-create), same fixed
  Lucide-key catalog as REQ-15 above. No backend PLAN/SPEC work should
  start on this until that amendment exists — mirroring exactly how this
  document's own REQ-13 through REQ-16 were the prerequisite gate for
  the RAG side. **Resolved 2026-08-09 (architect):** this landed as a
  new small SPEC/PLAN/TASKS folder,
  `knowly-api/specify/features/chat-group-naming-and-icon/`, rather than
  an amendment to `chat-group-membership-management` (already a large,
  self-contained SPEC/PLAN — this addition is narrow enough, and
  conceptually distinct enough from membership/admin/visibility
  management, to warrant its own folder, matching this repo's existing
  precedent of many narrow single-purpose feature folders rather than
  perpetually growing one large one). See that folder's own SPEC.md for
  the EARS-formatted requirements (transcribing, not re-deciding, the
  approval already recorded here and in `chat-unified-ui`'s SPEC).
</content>
