# TASKS — Article management

> Atomic, sequential, verifiable tasks derived from PLAN.md.
> Each "Implement" task ends with `./mvnw spotless:apply && ./mvnw verify`
> and a small Conventional Commit before moving on.

## 0. Foundations

- [ ] 1. Add `software.amazon.awssdk:s3`, `org.apache.tika:tika-parser-ocr-package`
      to `pom.xml`; `org.testcontainers:minio` as a test dependency.
- [ ] 2. Add a `minio` service to `compose.yaml` (hardened per the
      project's established pattern) and register it as a `@Bean
      @ServiceConnection` (or equivalent manual `S3Client` bean pointed at
      it) in `TestcontainersConfiguration`.
- [ ] 3. Add the four `ARTICLE_*` constants to the `Permission` enum.
- [ ] 4. `V7__create_articles_table.sql` / `V8__create_articles_envers_audit_table.sql`.

## 1. Entity and isolation (REQ-1, REQ-4)

- [ ] 5. Test: `Article` persists, round-trips via `ArticleRepository`,
      and its `tenantFilter` isolates it from other tenants' articles
      (reusing `TenantIsolationIntegrationTest`'s pattern) (Red).
- [ ] 6. Implement `Article`, `ArticleStatus`, `ArticleRepository`
      (Green).

## 2. Object storage (Security NFR)

- [ ] 7. Test: `ArticleStorageService.upload(...)` stores bytes
      retrievable via `download(...)`, and `presignedUrl(...)` returns a
      URL that actually serves the content without additional
      credentials (Red, against the MinIO Testcontainer).
- [ ] 8. Implement `ArticleStorageService` (Green).

## 3. Upload endpoint (REQ-5, REQ-6, REQ-7)

- [ ] 9. Test: `POST .../articles` with a supported small PDF returns
      `202` with `status: "PROCESSING"`, and the original file is
      retrievable from storage (Red).
- [ ] 10. Implement `ArticleController#upload` + `ArticleService#create`
       (Green).
- [ ] 11. Test: an unsupported content type is rejected `400` before
       anything is stored (Red).
- [ ] 12. Implement the content-type allow-list check (Green).
- [ ] 13. Test: a file exceeding the configured max size is rejected
       `400` before anything is stored (Red).
- [ ] 14. Implement the size check (Green).
- [ ] 15. Test: upload requires `ARTICLE_CREATE`; a caller without it
       gets `403` (Red).
- [ ] 16. Implement the `@RequiresPermission` on upload (Green).

## 4. Extraction pipeline (REQ-8, REQ-9, REQ-10, REQ-11)

- [ ] 17. Implement `article.uploaded` queue + DLQ + retry + publisher
       confirms in a new `ArticleRabbitConfig`, mirroring
       `AuthRabbitConfig`'s existing pattern exactly (no test needed for
       the config itself — covered by tasks below exercising the real
       flow).
- [ ] 18. Test: uploading a real small PDF fixture eventually reaches
       `READY` with non-empty extracted text (Red, Awaitility — same
       style as `AuthRabbitConfigTest`).
- [ ] 19. Implement `PdfTextExtractor` (Tika `AutoDetectParser`) +
       `ArticleExtractionListener` wiring for PDF (Green).
- [ ] 20. Test: uploading a real small image fixture with OCR-able text
       eventually reaches `READY` with that text present (Red).
- [ ] 21. Implement the OCR path (Tika + `tika-parser-ocr-package`,
       `tesseract-ocr` on the runtime image) (Green).
- [ ] 22. Test: uploading an audio fixture, with
       `OpenAiAudioTranscriptionModel` mocked to return known text,
       eventually reaches `READY` with that text (Red).
- [ ] 23. Implement `AudioTranscriptionExtractor` (Green).
- [ ] 24. Test: uploading a corrupt/unrecognizable file reaches `FAILED`
       with a non-empty reason, is not stuck `PROCESSING`, and does not
       land in the DLQ (REQ-10's "not re-thrown for retry") (Red).
- [ ] 25. Implement the failure-handling path in
       `ArticleExtractionListener` (catch, mark `FAILED`, no rethrow)
       (Green).
- [ ] 26. Test: an article in `PROCESSING` or `FAILED` still appears in
       the list endpoint (REQ-11) (Red).
- [ ] 27. (Green — should already hold if the list query doesn't filter
       by status; write it anyway as a regression guard.)

## 5. Listing, viewing, editing, deleting (REQ-2, REQ-3, REQ-12, REQ-13, REQ-14)

- [ ] 28. Test: `GET .../articles` requires `ARTICLE_VIEW`; lists only
       the active tenant's articles (Red).
- [ ] 29. Implement the list endpoint (Green).
- [ ] 30. Test: `GET .../articles/{id}` requires `ARTICLE_VIEW` and
       returns a working pre-signed `originalFileUrl` (Red).
- [ ] 31. Implement the detail endpoint (Green).
- [ ] 32. Test: `PUT .../articles/{id}` requires `ARTICLE_EDIT`
       independent of `ARTICLE_CREATE` — a user with only edit can fix an
       existing article's text (REQ-13) (Red).
- [ ] 33. Implement the edit endpoint (Green).
- [ ] 34. Test: `DELETE .../articles/{id}` requires `ARTICLE_DELETE`;
       soft-deletes (row still queryable directly, absent from the list)
       (Red).
- [ ] 35. Implement the delete endpoint (soft delete) (Green).
- [ ] 36. Test: each of view/create/edit/delete produces its own
       `@AuditLog` action string, distinguishable in `AuditEvent` (REQ-3)
       (Red).
- [ ] 37. Implement `@AuditLog` on all five endpoints (Green — likely
       already covered if annotated during tasks 10/29/31/33/35; write
       the test first regardless, per TDAD).

## 6. Final verification

- [ ] 38. Run the full `./mvnw spotless:apply && ./mvnw verify` and
       confirm the entire suite (auth + tenancy + onboarding-status +
       article-management) is green.
- [ ] 39. Update `PLAN.md` if any decision changed during implementation.
- [ ] 40. Update `SPEC.md`'s acceptance-criteria checkboxes to reflect
       what's now verified by tests.
