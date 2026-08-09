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

### Naming, renaming, and icon (Amended 2026-08-09 — REQ-13 through REQ-16)

- **Shared `IconKey` enum, new package.** `Conversation.icon` and (per
  the companion `chat-group-naming-and-icon` PLAN)
  `ChatConversation.icon` must accept the exact same fixed Lucide key
  set (frontend SPEC's "same fixed Lucide icon picker" requirement) but
  the two entities live in unrelated bounded contexts with no shared
  base class. A single `IconKey` enum is added in a new small
  cross-cutting package `br.com.conectabyte.knowly.icon`, mirroring the
  existing `audit`/`softdelete` precedent for standalone cross-cutting
  packages rather than duplicating the list per entity (duplication
  risks the two icon pickers silently drifting apart) or bolting it
  onto `tenancy` (already a de facto shared-kernel package, but for
  tenancy concepts specifically, not icons). Full reasoning recorded in
  `DECISIONS.md` ("shared `IconKey` enum in a new small cross-cutting
  package," 2026-08-09) since this is a genuinely novel cross-package
  decision with no exact prior precedent.
  - `IconKey` starts with a fixed set of values mirroring the icon
    names already available in `knowly-app`'s `@lucide/angular`
    dependency (e.g. `MESSAGE_CIRCLE`, `BOOK_OPEN`, `SPARKLES`,
    `USERS`, `HASH`, `FOLDER`, `STAR`, `BOT` — final catalog to be
    confirmed against the frontend's actual icon-picker list at
    implementation time, since the frontend is the source of truth for
    "this codebase's existing Lucide icon library"; the backend enum
    must be a superset-or-exact-match of whatever finite list the
    frontend's icon picker component renders, never a backend-invented
    list the frontend has to reverse-engineer). Documented as a
    `TASKS.md` coordination task rather than guessed here.
- **`Conversation.title` becomes required (REQ-13).** The column
  already exists (nullable in the original migration); no schema change
  needed for `title` itself, only a new `NOT NULL` constraint via
  migration plus request-level `@NotBlank` validation. `Conversation.icon`
  is a new nullable column (`@Enumerated(EnumType.STRING)`, typed
  `IconKey`) — nullable because REQ-15 requires unset icon to keep a
  default/fallback presentation, which is a **frontend display**
  decision, not a backend-computed default value; the backend never
  writes a synthetic default into the column.
- **`POST .../conversations` gains a request body.** New
  `CreateConversationRequestDto(@NotBlank String title, IconKey icon)`
  (icon optional/nullable, validated by Bean Validation's own enum
  deserialization — an unrecognized string fails JSON binding before
  reaching the controller, surfaced through the existing
  `MethodArgumentNotValidException`/`HttpMessageNotReadableException`
  paths already wired app-wide, no new custom `@Constraint` needed for
  the "valid enum key" part specifically). `ConversationService#create`
  now takes `title`/`icon` and sets them on the new `Conversation`.
- **New rename endpoint (REQ-14).** `PUT
  /api/tenants/{tenantId}/conversations/{conversationId}` — chosen over
  `PATCH` to match this codebase's established convention for a
  full-replace-of-named-fields action (see `chat-group-membership-management`
  PLAN's `PUT .../visibility` precedent, "matching this codebase's
  existing `PUT`-for-full-replace-of-a-single-field convention
  elsewhere"); this endpoint replaces `title` and (optionally) `icon`
  together as one request body (`RenameConversationRequestDto(@NotBlank
  String title, IconKey icon)`), not a partial-field `PATCH`. Requires
  `CONVERSATION_USE` (same permission as create/send) plus ownership:
  reuses `ConversationService#requireOwnConversation` (already used by
  `get`) — a non-owner or another tenant's conversation id gets the
  same 404 `ConversationNotFoundException` as `get` today (no existence
  leak, consistent with REQ-6's established owner-scoping precedent),
  **not** a 403 — REQ-16's "422/403 as appropriate" language is
  satisfied by re-using the existing not-found-not-forbidden pattern
  for the ownership case specifically (a 403 here would leak that a
  conversation id exists and belongs to someone else, which this
  codebase has already decided against once for `get`).
- **[AppSec-added, 2026-08-09] `title` needs an explicit upper bound.**
  `CreateConversationRequestDto`/`RenameConversationRequestDto` as
  drafted only carry `@NotBlank String title` — no `@Size`. The
  `title` column is `VARCHAR(255)`; without a matching `@Size(max =
  255)` on both DTOs, a caller can submit an arbitrarily long string
  that fails at the DB layer with an unhandled data-truncation error
  (a generic 500, not this endpoint's documented 400 contract) instead
  of being rejected cleanly by Bean Validation before ever reaching
  the repository. Add `@Size(max = 255)` to `title` on both DTOs (goes
  through the same existing `MethodArgumentNotValidException`/400 path
  already relied on for `@NotBlank`, no new validation mechanism).
  Non-blocking for the rest of this PLAN — this codebase has the same
  gap on other free-text title fields (e.g. `UpdateArticleRequestDto`)
  predating this feature, so it's flagged here as a fix scoped to the
  two DTOs this PLAN is actively introducing, not a mandate to sweep
  every existing endpoint in the same PR.
- **Validation-failure status code: 400, not 422 (Tier 2 deviation from
  the SPEC's literal wording).** REQ-16 says "422/403 as appropriate,"
  but this codebase has exactly one `MethodArgumentNotValidException`
  handler app-wide (`CreationValidationAuditAdvice`) and it always
  returns `400 BAD_REQUEST` — introducing a one-off 422 here would mean
  either a second, inconsistent validation-error status code in the
  same API, or duplicating/forking the existing global handler for one
  endpoint. Blank `title` and an invalid `icon` key both go through
  standard Bean Validation and get the existing uniform 400 treatment;
  only the ownership case (REQ-16's "does not own the target
  conversation") is a distinct, non-Bean-Validation rejection, and that
  one is 404 per the point above, not 403. This keeps the whole app's
  validation-error contract uniform rather than introducing REQ-16's
  422 as a special case nothing else in the codebase uses.

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

`V32__add_title_required_and_icon_to_conversations.sql` (Amended
2026-08-09 — REQ-13 through REQ-16; exact version number to be confirmed
against whatever the latest migration is at implementation time, since
`chat-group-naming-and-icon`'s own migration may land first or after
this one):

```sql
-- Backfill existing NULL titles before the NOT NULL constraint can be
-- added (pre-amendment rows never had a title set).
UPDATE conversations SET title = 'Conversa sem título' WHERE title IS NULL;
ALTER TABLE conversations
  ALTER COLUMN title SET NOT NULL,
  ADD COLUMN icon VARCHAR(50);

ALTER TABLE conversations_aud ADD COLUMN icon VARCHAR(50);
```

- `icon` stays nullable at the schema level even though `IconKey` is a
  fixed enum — see the "keeps its existing default/fallback
  presentation" reasoning above; enforcing NOT NULL here would force a
  backend-chosen default icon rather than leaving that to the frontend.
- The backfill string is a placeholder for pre-existing rows only (this
  feature's own creation flow always supplies a real title going
  forward); exact backfill copy to confirm with `po-product-owner`/
  `frontend-engineer` at implementation time if any pre-amendment
  conversations exist in a real environment (in a fresh/test database
  this is a no-op).

The `vector_store` table itself is created by
`spring-ai-starter-vector-store-pgvector`'s own schema initializer
(`spring.ai.vectorstore.pgvector.initialize-schema=true`), not a
project-authored Flyway migration — consistent with treating it as
Spring AI's managed table, not project schema.

## API contracts

All under `/api/tenants/{tenantId}/conversations`, behind
`@RequiresPermission(CONVERSATION_USE)` + `@AuditLog`:

| Method | Path | Request | Response | Status |
|---|---|---|---|---|
| `POST` | `/api/tenants/{tenantId}/conversations` | `{ title: string (required, non-blank), icon?: IconKey }` | `ConversationSummaryDto { id, title, icon }` | `201`; `400` blank title/invalid icon; `403` missing `CONVERSATION_USE` |
| `GET` | `/api/tenants/{tenantId}/conversations` | — | `ConversationSummaryDto[]`, most recent first | `200` |
| `GET` | `/api/tenants/{tenantId}/conversations/{id}` | — | `ConversationDetailDto` (detail + ordered messages) | `200`; `404` if not the caller's own (no existence leak) |
| `PUT` | `/api/tenants/{tenantId}/conversations/{id}` | `{ title: string (required, non-blank), icon?: IconKey }` | `ConversationSummaryDto { id, title, icon }` | `200`; `400` blank title/invalid icon; `404` if not the caller's own (REQ-16's ownership case — see PLAN note above on why 404 not 403) |
| `POST` | `/api/tenants/{tenantId}/conversations/{id}/messages` | `{ content: string }` | `text/event-stream`, one `message` event per delta + final `done`, or `error` (REQ-11) | `200` (stream); `404` if not the caller's own conversation |

(Amended 2026-08-09) `ConversationSummaryDto` gains `icon` alongside its
existing `id`/`title` fields.

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
- `br.com.conectabyte.knowly.icon` (Amended 2026-08-09, new package,
  shared with `chat-group-naming-and-icon`): `IconKey` enum.
- `br.com.conectabyte.knowly.conversation` (Amended 2026-08-09): add
  `CreateConversationRequestDto`, `RenameConversationRequestDto`; extend
  `Conversation` with `icon`; extend `ConversationSummaryDto` with
  `icon`; extend `ConversationController`/`ConversationService` with the
  rename endpoint.

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

- (Amended 2026-08-09) `ConversationControllerIntegrationTest` additions:
  `POST` without `title`/with a blank `title` returns `400`; `POST` with
  an invalid `icon` string returns `400`; `POST` with valid `title`/
  `icon` persists both and echoes them in the `201` body; `PUT`
  (rename) by the owning user updates `title`/`icon` and leaves messages
  untouched; `PUT` by a non-owner or on another tenant's conversation id
  returns `404` (not `403`); `PUT` with a blank `title` or invalid
  `icon` returns `400` with no partial update applied (assert the
  conversation's prior `title`/`icon` unchanged after the failed
  request).

## Emergent decisions

- `PgVectorStore#afterPropertiesSet` calls `embeddingModel.dimensions()`
  at context startup whenever `spring.ai.vectorstore.pgvector.dimensions`
  isn't set explicitly — which itself calls the real embeddings endpoint
  to infer the vector size. With the test profile's dummy OpenAI key,
  this broke every Spring context in the suite (not just
  conversation-related tests) with a 401 at startup. Fixed by pinning
  `spring.ai.vectorstore.pgvector.dimensions: 1536` in
  `application-test.yaml` — the actual value doesn't matter for tests
  that never call a real embedding model, it just needs to be present so
  `PgVectorStore` skips the real call.
- `ArticleEmbeddingListenerTest` (plain Mockito unit test, mocked
  `ArticleRepository`/`VectorStore`) directly covers the success path
  (chunks produced, `VectorStore#add` called with `tenant_id`/
  `article_id` metadata, `embeddingStatus` set to `READY`), the
  terminal-failure path (`FAILED` + reason, no rethrow), and the
  missing-article path (skipped, no `VectorStore`/repository call) —
  closing the gap left by relying only on `ArticleControllerIntegrationTest`'s
  indirect, failure-only coverage against the dummy OpenAI key.
- `MessageStreamingService#sendMessage` gained a package-private overload
  taking the `SseEmitter` explicitly (the public one just calls it with
  `new SseEmitter(0L)`), purely as a test seam: `MessageStreamingServiceTest`
  passes a `RecordingSseEmitter` (a small test-only `SseEmitter` subclass
  overriding `send`/`complete`/`completeWithError`) to assert the *actual*
  SSE event names and payloads sent — one `message` event per delta, a
  final `done` event, or a single `error` event on failure — rather than
  only the persisted end-state. The `ConversationControllerIntegrationTest`
  additions still separately confirm the same flow end-to-end through real
  Spring MVC request handling (permission gating, real repository
  persistence, mocked `ChatModel`/`VectorStore` beans).
