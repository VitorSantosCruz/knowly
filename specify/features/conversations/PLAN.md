# PLAN — Conversations (RAG chat over articles)

> The how. Translates the SPEC into concrete technical decisions.
> References SPEC.md.

## Architectural decisions

### Packages

- New package `br.com.conectabyte.knowly.conversation`: `Conversation`,
  `Message`, `MessageRole` enum, repositories, `ConversationService`,
  `ConversationController`, DTOs, and the RAG plumbing
  (`ArticleEmbeddingListener`, `ArticleEmbeddingRabbitConfig`).
- Extend `br.com.conectabyte.knowly.article.Article` with
  `embeddingStatus` (`EmbeddingStatus`: `PENDING`, `READY`, `FAILED`) and
  `embeddingFailureReason` — mirrors the existing `status`/
  `failureReason` pair on the same entity rather than a separate table,
  since it's 1:1 with the article and follows the same lifecycle shape.
- New `Permission.CONVERSATION_USE` constant (REQ-5), added to the
  existing enum for the same reason `ARTICLE_*` was: `PermissionService`/
  `PermissionAspect` already operate generically over `Permission`.

### Embedding pipeline (REQ-1, REQ-2, REQ-3)

- Reuses the article-management precedent exactly: after
  `ArticleExtractionListener` marks an article `READY`, it publishes a
  new `ArticleReadyForEmbeddingEvent` to a new queue
  `article.ready-for-embedding` (+ DLQ, retry, publisher confirms —
  `ArticleEmbeddingRabbitConfig` mirrors `ArticleRabbitConfig`). Kept as a
  second event on the same queue family rather than piggy-backing on
  `ArticleUploadedEvent`, since embedding only makes sense once text
  exists — a distinct trigger for a distinct pipeline stage.
- `ArticleEmbeddingListener` (not `@Transactional`, same reasoning as
  `ArticleExtractionListener`: it's a background process with no tenant
  in context, and every repository call is already scoped by an explicit
  article id): loads the article, chunks its text with Spring AI's
  `TokenTextSplitter`, wraps each chunk in a `Document` with metadata
  `{tenant_id, article_id}`, and calls `VectorStore#add`. On success,
  sets `embeddingStatus = READY`. On failure, sets `FAILED` +
  `embeddingFailureReason` and does **not** requeue — same terminal
  choice as extraction failures (REQ-3): a bad chunk/provider error won't
  succeed on blind retry, and a stuck `PENDING` forever is worse than a
  visible terminal failure.
- Article deletion (`ArticleService#delete`, already soft-delete):
  additionally calls `VectorStore#delete` with a filter expression on
  `article_id` synchronously in the same service call, before returning
  — REQ-2 requires embeddings gone as soon as the article is, not
  eventually-consistent via a queue.

### Conversation/message domain (REQ-4, REQ-6, REQ-7)

- `Conversation`: tenant (`@ManyToOne`, `@Filter` per the established
  `tenantFilter`), owner (`@ManyToOne User`), optional `title` (nullable
  — no auto-titling in this feature, see Out of scope), standard
  auditing columns.
- `Message`: conversation (`@ManyToOne`), `role` (`MessageRole`: `USER`,
  `ASSISTANT`), `content` (text), `createdAt`. No `updatedAt`/edit
  support — messages are append-only (Out of scope: editing/deleting a
  message).
- Ownership isolation (REQ-6): the tenant `@Filter` already keeps a
  conversation out of other tenants' queries, but user-ownership is
  **not** modeled as a Hibernate filter (unlike tenant scoping, "current
  user" isn't a single global context value applied uniformly the same
  way across every entity in the codebase yet) — instead every
  `ConversationRepository` query method takes the owner's user id as an
  explicit parameter, and `ConversationService` always passes the
  authenticated caller's id, never a caller-supplied one. A request for
  another user's conversation id simply returns "not found" (no
  existence leak), consistent with the tenancy feature's precedent of
  never confirming another actor's records exist.
- `@AuditLog` on every service method (REQ-7), logging conversation/
  message **ids** and the actor only — never message `content`, per the
  constitution's PII/content-in-logs rule, which explicitly calls out
  "conversation/chat history" as in scope for that rule.

### Chat / RAG endpoint (REQ-8 through REQ-12)

- `POST /api/tenants/{tenantId}/conversations` creates an empty
  conversation owned by the caller (requires `CONVERSATION_USE`,
  REQ-12 — permission checked before any model/vector-store call).
- `POST /api/tenants/{tenantId}/conversations/{id}/messages` (`{ content
  }`) streams the assistant's answer via `SseEmitter` (REQ-8):
  1. Persists the user's message immediately.
  2. Retrieves the top-k relevant chunks via
     `VectorStore#similaritySearch`, with a `FilterExpression` on
     `tenant_id` — enforced at the query level, not filtered after the
     fact, per the SPEC's security NFR (a leaked chunk from another
     tenant is a real breach, not a display bug).
  3. Builds a prompt: system instructions + retrieved chunks as context +
     conversation history + the new message. If no chunk clears a minimum
     similarity threshold, the system instructions explicitly tell the
     model to say it found no matching articles (REQ-10) rather than
     silently omitting the context section (which would let the model
     answer from general knowledge unprompted).
  4. Calls `ChatModel#stream(prompt)` (Spring AI's reactive streaming
     API, returns `Flux<ChatResponse>`) and forwards each emitted content
     delta to the `SseEmitter` as it arrives.
  5. On `Flux` completion: persists the concatenated full assistant
     response as a `Message`, then completes the emitter.
  6. On `Flux` error (REQ-11): sends a single SSE `error` event with a
     generic message (no raw provider exception text leaked to the
     client) and completes the emitter with that error — never left
     hanging.
- Streaming (not synchronous) was the explicit choice for this feature,
  even though it's more complex to test, because the UX goal is visible
  token-by-token progress on what can be a multi-second model call.

## Data schema

`V9__add_embedding_status_to_articles.sql`:

```sql
ALTER TABLE articles
  ADD COLUMN embedding_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  ADD COLUMN embedding_failure_reason VARCHAR(500);
```

`V10__create_conversations_and_messages.sql`:

```sql
CREATE TABLE conversations (
  id BIGSERIAL PRIMARY KEY,
  tenant_id BIGINT NOT NULL REFERENCES tenants(id),
  owner_user_id BIGINT NOT NULL REFERENCES users(id),
  title VARCHAR(255),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by VARCHAR(255) NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by VARCHAR(255) NOT NULL
);
CREATE INDEX ix_conversations_tenant ON conversations (tenant_id);
CREATE INDEX ix_conversations_owner ON conversations (owner_user_id);

CREATE TABLE messages (
  id BIGSERIAL PRIMARY KEY,
  conversation_id BIGINT NOT NULL REFERENCES conversations(id),
  role VARCHAR(20) NOT NULL,
  content TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_messages_conversation ON messages (conversation_id);
```

`V11__create_conversations_envers_audit_table.sql`: `conversations_aud`,
same pattern as every other `_aud` table. `messages` is intentionally
**not** Envers-audited — it's already append-only with no update/delete
path, so a history-of-history table would only duplicate `messages`
itself with zero additional information.

The `vector_store` table itself is created by
`spring-ai-starter-vector-store-pgvector`'s own schema initializer
(`spring.ai.vectorstore.pgvector.initialize-schema=true`), not a
project-authored Flyway migration — consistent with treating it as
Spring AI's managed table, not project schema.

## API contracts

All under `/api/tenants/{tenantId}/conversations`, behind
`@RequiresPermission(CONVERSATION_USE)` + `@AuditLog`:

- `POST /api/tenants/{tenantId}/conversations` → `201 { id, title: null
  }`.
- `GET /api/tenants/{tenantId}/conversations` → the caller's own
  conversations, most recent first.
- `GET /api/tenants/{tenantId}/conversations/{id}` → detail + ordered
  messages; `404` if not the caller's own (no existence leak).
- `POST /api/tenants/{tenantId}/conversations/{id}/messages` (`{ content
  }`) → `text/event-stream`, one `message` event per content delta, a
  final `done` event, or an `error` event on failure (REQ-11); `404` if
  not the caller's own conversation.

## Dependencies

- No new dependencies: `spring-ai-starter-model-openai`,
  `spring-ai-starter-vector-store-pgvector`, and Spring MVC's built-in
  `SseEmitter` already cover everything this feature needs.

## Package/file structure

- `br.com.conectabyte.knowly.conversation`: `Conversation`, `Message`,
  `MessageRole`, `ConversationRepository`, `MessageRepository`,
  `ConversationService`, `ConversationController`, DTOs
  (`ConversationSummaryDto`, `ConversationDetailDto`, `SendMessageRequestDto`),
  `ArticleEmbeddingListener`, `ArticleEmbeddingRabbitConfig`,
  `ArticleReadyForEmbeddingEvent`.
- `br.com.conectabyte.knowly.article`: add `EmbeddingStatus` enum,
  extend `Article` with the two new columns, publish
  `ArticleReadyForEmbeddingEvent` from `ArticleExtractionListener`, and
  add the `VectorStore#delete` call to `ArticleService#delete`.
- `br.com.conectabyte.knowly.tenancy.Permission`: add
  `CONVERSATION_USE`.

## Testing strategy

- `ArticleEmbeddingListenerTest`: a ready article with real short text
  produces chunks and a `VectorStore#add` call (using an in-memory/test
  `VectorStore`, not real OpenAI embeddings — the `EmbeddingModel` is
  mocked at the Spring AI client boundary, same precedent as
  `OpenAiAudioTranscriptionModel` in `article-management`); a provider
  failure marks the article `FAILED` with a reason and does not retry.
- `ArticleServiceTest`/`ArticleControllerIntegrationTest` addition:
  deleting an article calls `VectorStore#delete` with the right filter.
- `ConversationRepositoryTest`: tenant-filter isolation (reusing the
  `TenantIsolationIntegrationTest` pattern) and owner-scoping (user A
  never sees user B's conversation even same tenant).
- `ConversationControllerIntegrationTest` (Testcontainers, mocked
  `ChatModel`/`EmbeddingModel`): create conversation requires
  `CONVERSATION_USE` (403 otherwise, REQ-12); sending a message persists
  the user message, streams mocked deltas, persists the full assistant
  response after stream completion (REQ-9); a `Flux` error from the
  mocked `ChatModel` ends the stream with an `error` SSE event (REQ-11);
  another user's conversation id returns 404, not 403 (no existence
  leak); cross-tenant isolation on list/detail.
- No test exercises a real OpenAI call in CI — every `ChatModel`/
  `EmbeddingModel` interaction in tests is mocked, consistent with the
  constitution's dummy-API-key note for `application-test.yaml`.
