# TASKS — Conversations (RAG chat over articles)

> Atomic, sequential, verifiable tasks derived from PLAN.md.
> Each "Implement" task ends with `./mvnw spotless:apply && ./mvnw verify`
> and a small Conventional Commit before moving on.

## 0. Foundations

- [x] 1. Add `CONVERSATION_USE` to the `Permission` enum.
- [x] 2. `V9__add_embedding_status_to_articles.sql`.
- [x] 3. `V10__create_conversations_and_messages.sql`.
- [x] 4. `V11__create_conversations_envers_audit_table.sql`.
- [x] 5. Enable `spring.ai.vectorstore.pgvector.initialize-schema=true`
      in `application.yaml` (and the test config, against the existing
      pgvector Testcontainer). Test config additionally pins an explicit
      `dimensions` value so `PgVectorStore#afterPropertiesSet` doesn't
      call the real OpenAI embeddings endpoint just to infer vector
      size — an emergent finding, see PLAN.md.

## 1. Embedding pipeline (REQ-1, REQ-2, REQ-3)

- [x] 6. Test: when `ArticleExtractionListener` marks an article
      `READY`, it publishes an `ArticleReadyForEmbeddingEvent` (Red).
- [x] 7. Implement the publish call + `ArticleEmbeddingRabbitConfig`
      queue/DLQ/retry, mirroring `ArticleRabbitConfig` (Green).
- [x] 8. Test: `ArticleEmbeddingListener` chunks a ready article's text
      and calls `VectorStore#add` with chunks tagged
      `{tenant_id, article_id}`, then marks `embeddingStatus = READY`
      (Red, mocked `EmbeddingModel`/`VectorStore`).
- [x] 9. Implement `ArticleEmbeddingListener` (Green).
- [x] 10. Test: an embedding-provider failure marks the article
       `embeddingStatus = FAILED` with a reason, and does not requeue
       (Red).
- [x] 11. Implement the terminal-failure handling (Green).
- [x] 12. Test: deleting an article calls `VectorStore#delete` with a
       filter on `article_id` (Red).
- [x] 13. Implement the delete-embeddings call in
       `ArticleService#delete` (Green).

## 2. Conversation/message domain (REQ-4, REQ-6, REQ-7)

- [x] 14. Test: `Conversation`/`Message` persist and round-trip; the
       tenant filter isolates a conversation from other tenants' queries
       (Red, reusing `TenantIsolationIntegrationTest`'s pattern).
- [x] 15. Implement `Conversation`, `Message`, `MessageRole`, and their
       repositories (Green).
- [x] 16. Test: a repository query scoped to user A's id never returns
       user B's conversation, even in the same tenant (Red).
- [x] 17. Implement the owner-id-scoped repository methods (Green).

## 3. Conversation CRUD endpoints (REQ-5, REQ-12)

- [x] 18. Test: `POST .../conversations` requires `CONVERSATION_USE`; a
       caller without it gets `403` before any other work happens (Red).
- [x] 19. Implement `ConversationController#create` +
       `ConversationService#create` with `@RequiresPermission` (Green).
- [x] 20. Test: `GET .../conversations` lists only the caller's own
       conversations, most recent first (Red).
- [x] 21. Implement the list endpoint (Green).
- [x] 22. Test: `GET .../conversations/{id}` returns `404` (not `403`)
       for another user's conversation id (Red).
- [x] 23. Implement the detail endpoint with the not-found-not-forbidden
       behavior (Green).
- [x] 24. Test: conversation creation emits an `@AuditLog` event with
       the conversation id only, never message content (Red). (Directly
       asserted for `conversation.create`; the same `@AuditLog`
       mechanism is applied identically to list/view/message-send —
       structurally guaranteed to never log content since the aspect
       only records action/resourceType/resourceId, not method
       arguments — but each of those isn't independently asserted by a
       test.)
- [x] 25. Implement the `@AuditLog` annotations (Green).

## 4. Chat / RAG streaming endpoint (REQ-8, REQ-9, REQ-10, REQ-11)

- [x] 26. Test: sending a message persists the user's `Message`
       immediately, before the model call happens (Red, mocked
       `ChatModel`).
- [x] 27. Implement the immediate user-message persistence (Green).
- [x] 28. Test: the retrieval step calls
       `VectorStore#similaritySearch` with a `FilterExpression` scoped to
       the caller's tenant id (Red).
- [x] 29. Implement the scoped similarity search + prompt assembly
       (Green).
- [x] 30. Test: with no chunk above the relevance threshold, the prompt
       sent to the mocked `ChatModel` instructs it to say no matching
       articles were found (Red).
- [x] 31. Implement the no-context-found prompt branch (Green).
- [x] 32. Test: a mocked `ChatModel#stream` emitting several deltas
       produces the full concatenated response persisted as an assistant
       `Message` after completion, both at the service level (unit test
       with a captured `SseEmitter`) and end-to-end through the
       controller (mocked `ChatModel`/`VectorStore` beans) (Red).
- [x] 33. Implement the `SseEmitter` streaming + post-completion
       persistence (Green).
- [x] 34. Test: a mocked `ChatModel#stream` error ends the stream
       (assistant message never persisted) rather than hanging (Red).
- [x] 35. Implement the error-path handling (Green).

## 5. Final verification

- [x] 36. Run `./mvnw spotless:apply && ./mvnw verify` (full suite) and
       confirm everything is green (accounting for the documented
       full-suite Testcontainers/Redis flakiness unrelated to this
       feature, per prior features' notes).
- [x] 37. Update `PLAN.md`'s architectural decisions if anything changed
       during implementation.
- [x] 38. Update `SPEC.md`'s acceptance-criteria checkboxes to reflect
       what's now verified by tests.

## 6. Naming, renaming, and icon (Amended 2026-08-09 — REQ-13 through REQ-16)

> Coordinate task 39 with whoever implements `chat-group-naming-and-icon`
> — the `IconKey` enum is shared and should only be created once.

- [x] 39. Create the new `br.com.conectabyte.knowly.icon` package with
       the `IconKey` enum, confirming its value list against
       `knowly-app`'s actual `@lucide/angular` icon-picker catalog (not
       an independently-invented list) — if that frontend catalog
       doesn't exist yet as a fixed, enumerable list, coordinate with
       `frontend-engineer`/`software-architect` (frontend) before
       finalizing values, since the backend enum must match, not lead.
- [x] 40. `V32__add_title_required_and_icon_to_conversations.sql`
       (confirm actual next-available version number at implementation
       time): backfill null titles, `title` `NOT NULL`, add `icon`
       column to `conversations` and `conversations_aud`.
- [x] 41. Test: `Conversation.icon` persists and round-trips as an
       `IconKey` (Red).
- [x] 42. Implement the `icon` field on `Conversation` (Green).
- [x] 43. Test: `POST .../conversations` without a `title` (or blank)
       returns `400` and creates nothing (Red).
- [x] 44. Test: `POST .../conversations` with a `title` and an invalid
       `icon` string returns `400` and creates nothing (Red).
- [x] 45. Test: `POST .../conversations` with a valid `title`/`icon`
       persists both and echoes them in the `201` body (Red).
- [x] 46. Implement `CreateConversationRequestDto` + update
       `ConversationController#create`/`ConversationService#create` to
       accept and persist `title`/`icon` (Green, tasks 43-45).
- [x] 47. Test: `PUT .../conversations/{id}` by the owning user with a
       new `title`/`icon` updates both, leaves messages untouched (Red).
- [x] 48. Test: `PUT .../conversations/{id}` by a non-owner, or on
       another tenant's conversation id, returns `404` (Red).
- [x] 49. Test: `PUT .../conversations/{id}` with a blank `title` or
       invalid `icon` returns `400` with the conversation's prior
       `title`/`icon` unchanged afterward (no partial update) (Red).
- [x] 50. Implement `RenameConversationRequestDto` +
       `ConversationController`/`ConversationService`'s rename endpoint,
       reusing `requireOwnConversation` (Green, tasks 47-49).
- [x] 51. Update `ConversationSummaryDto`/`ConversationDetailDto` to
       include `icon`.
- [x] 52. Add `@AuditLog(action = "conversation.rename", ...)` on the
       rename endpoint, consistent with REQ-7's existing coverage.
- [x] 53. Update `SPEC.md`'s three new (Amended 2026-08-09) acceptance
       criteria checkboxes once verified.
- [x] 54. Run `./mvnw spotless:apply && ./mvnw verify` and confirm green.
