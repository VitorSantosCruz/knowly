# TASKS — Conversations (RAG chat over articles)

> Atomic, sequential, verifiable tasks derived from PLAN.md.
> Each "Implement" task ends with `./mvnw spotless:apply && ./mvnw verify`
> and a small Conventional Commit before moving on.

## 0. Foundations

- [ ] 1. Add `CONVERSATION_USE` to the `Permission` enum.
- [ ] 2. `V9__add_embedding_status_to_articles.sql`.
- [ ] 3. `V10__create_conversations_and_messages.sql`.
- [ ] 4. `V11__create_conversations_envers_audit_table.sql`.
- [ ] 5. Enable `spring.ai.vectorstore.pgvector.initialize-schema=true`
      in `application.yaml` (and the test config, against the existing
      pgvector Testcontainer).

## 1. Embedding pipeline (REQ-1, REQ-2, REQ-3)

- [ ] 6. Test: when `ArticleExtractionListener` marks an article
      `READY`, it publishes an `ArticleReadyForEmbeddingEvent` (Red).
- [ ] 7. Implement the publish call + `ArticleEmbeddingRabbitConfig`
      queue/DLQ/retry, mirroring `ArticleRabbitConfig` (Green).
- [ ] 8. Test: `ArticleEmbeddingListener` chunks a ready article's text
      and calls `VectorStore#add` with chunks tagged
      `{tenant_id, article_id}`, then marks `embeddingStatus = READY`
      (Red, mocked `EmbeddingModel`/`VectorStore`).
- [ ] 9. Implement `ArticleEmbeddingListener` (Green).
- [ ] 10. Test: an embedding-provider failure marks the article
       `embeddingStatus = FAILED` with a reason, and does not requeue
       (Red).
- [ ] 11. Implement the terminal-failure handling (Green).
- [ ] 12. Test: deleting an article calls `VectorStore#delete` with a
       filter on `article_id` (Red).
- [ ] 13. Implement the delete-embeddings call in
       `ArticleService#delete` (Green).

## 2. Conversation/message domain (REQ-4, REQ-6, REQ-7)

- [ ] 14. Test: `Conversation`/`Message` persist and round-trip; the
       tenant filter isolates a conversation from other tenants' queries
       (Red, reusing `TenantIsolationIntegrationTest`'s pattern).
- [ ] 15. Implement `Conversation`, `Message`, `MessageRole`, and their
       repositories (Green).
- [ ] 16. Test: a repository query scoped to user A's id never returns
       user B's conversation, even in the same tenant (Red).
- [ ] 17. Implement the owner-id-scoped repository methods (Green).

## 3. Conversation CRUD endpoints (REQ-5, REQ-12)

- [ ] 18. Test: `POST .../conversations` requires `CONVERSATION_USE`; a
       caller without it gets `403` before any other work happens (Red).
- [ ] 19. Implement `ConversationController#create` +
       `ConversationService#create` with `@RequiresPermission` (Green).
- [ ] 20. Test: `GET .../conversations` lists only the caller's own
       conversations, most recent first (Red).
- [ ] 21. Implement the list endpoint (Green).
- [ ] 22. Test: `GET .../conversations/{id}` returns `404` (not `403`)
       for another user's conversation id (Red).
- [ ] 23. Implement the detail endpoint with the not-found-not-forbidden
       behavior (Green).
- [ ] 24. Test: every conversation action emits an `@AuditLog` event
       with conversation/message ids only, never message content (Red).
- [ ] 25. Implement the `@AuditLog` annotations (Green).

## 4. Chat / RAG streaming endpoint (REQ-8, REQ-9, REQ-10, REQ-11)

- [ ] 26. Test: sending a message persists the user's `Message`
       immediately, before the model call happens (Red, mocked
       `ChatModel`).
- [ ] 27. Implement the immediate user-message persistence (Green).
- [ ] 28. Test: the retrieval step calls
       `VectorStore#similaritySearch` with a `FilterExpression` scoped to
       the caller's tenant id (Red).
- [ ] 29. Implement the scoped similarity search + prompt assembly
       (Green).
- [ ] 30. Test: with no chunk above the relevance threshold, the prompt
       sent to the mocked `ChatModel` instructs it to say no matching
       articles were found (Red).
- [ ] 31. Implement the no-context-found prompt branch (Green).
- [ ] 32. Test: a mocked `ChatModel#stream` emitting several deltas
       produces one SSE `message` event per delta, then a `done` event,
       and the full concatenated response is persisted as an assistant
       `Message` after completion (Red).
- [ ] 33. Implement the `SseEmitter` streaming + post-completion
       persistence (Green).
- [ ] 34. Test: a mocked `ChatModel#stream` error ends the stream with
       an `error` SSE event and completes the emitter, rather than
       hanging (Red).
- [ ] 35. Implement the error-path handling (Green).

## 5. Final verification

- [ ] 36. Run `./mvnw spotless:apply && ./mvnw verify` (full suite) and
       confirm everything is green (accounting for the documented
       full-suite Testcontainers/Redis flakiness unrelated to this
       feature, per prior features' notes).
- [ ] 37. Update `PLAN.md`'s architectural decisions if anything changed
       during implementation.
- [ ] 38. Update `SPEC.md`'s acceptance-criteria checkboxes to reflect
       what's now verified by tests.
