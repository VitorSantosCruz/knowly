# TASKS — Article management

> Atomic, sequential, verifiable tasks derived from PLAN.md.
> Each "Implement" task ends with `./mvnw spotless:apply && ./mvnw verify`
> and a small Conventional Commit before moving on.

## 0. Foundations

- [x] 1. Add `software.amazon.awssdk:s3` (+ its BOM) to `pom.xml`;
      `org.testcontainers:minio` as a test dependency. (Tika's OCR/PDF
      modules already come transitively via
      `spring-ai-tika-document-reader` — confirmed via `dependency:tree`,
      no new Tika dependency needed.)
- [x] 2. Add a `minio` service to `compose.yaml` (hardened per the
      project's established pattern) and register it as a `@Bean
      @ServiceConnection` (or equivalent manual `S3Client` bean pointed at
      it) in `TestcontainersConfiguration`.
- [x] 3. Add the four `ARTICLE_*` constants to the `Permission` enum.
- [x] 4. `V7__create_articles_table.sql` / `V8__create_articles_envers_audit_table.sql`.

## 1. Entity and isolation (REQ-1, REQ-4)

- [x] 5. Test: `Article` persists, round-trips via `ArticleRepository`,
      and its `tenantFilter` isolates it from other tenants' articles
      (reusing `TenantIsolationIntegrationTest`'s pattern) (Red).
- [x] 6. Implement `Article`, `ArticleStatus`, `ArticleRepository`
      (Green).

## 2. Object storage (Security NFR)

- [x] 7. Test: `ArticleStorageService.upload(...)` stores bytes
      retrievable via `download(...)`, and `presignedUrl(...)` returns a
      URL that actually serves the content without additional
      credentials (Red, against the MinIO Testcontainer).
- [x] 8. Implement `ArticleStorageService` (Green).

## 3. Upload endpoint (REQ-5, REQ-6, REQ-7)

- [x] 9. Test: `POST .../articles` with a supported small PDF returns
      `202` with `status: "PROCESSING"`, and the original file is
      retrievable from storage (Red).
- [x] 10. Implement `ArticleController#upload` + `ArticleService#create`
       (Green).
- [x] 11. Test: an unsupported content type is rejected `400` before
       anything is stored (Red).
- [x] 12. Implement the content-type allow-list check (Green).
- [x] 13. Test: a file exceeding the configured max size is rejected
       `400` before anything is stored (Red).
- [x] 14. Implement the size check (Green).
- [x] 15. Test: upload requires `ARTICLE_CREATE`; a caller without it
       gets `403` (Red).
- [x] 16. Implement the `@RequiresPermission` on upload (Green).

## 4. Extraction pipeline (REQ-8, REQ-9, REQ-10, REQ-11)

- [x] 17. Implement `article.uploaded` queue + DLQ + retry + publisher
       confirms in a new `ArticleRabbitConfig`, mirroring
       `AuthRabbitConfig`'s existing pattern exactly (no test needed for
       the config itself — covered by tasks below exercising the real
       flow).
- [x] 18. Test: uploading a real small PDF fixture eventually reaches
       `READY` with non-empty extracted text (Red, Awaitility — same
       style as `AuthRabbitConfigTest`).
- [x] 19. Implement `PdfTextExtractor` (Tika `AutoDetectParser`) +
       `ArticleExtractionListener` wiring for PDF (Green).
- [x] 20. Test: uploading a real small image fixture with OCR-able text
       eventually reaches `READY` with that text present (Red), then
       implemented (Green) — `aRealImageEventuallyReachesReadyWithOcrdText`
       in `ArticleExtractionListenerTest`, against a real PNG generated
       in-test (`Graphics2D` draws text, `ImageIO` encodes it) and real
       Tesseract OCR (log line confirms: "Tesseract is installed and is
       being invoked"). This sandbox has no root/apt-get-install access,
       so `tesseract-ocr`/`libtesseract5`/`libleptonica6` were obtained
       via `apt-get download` (works without root — it only fetches, it
       doesn't install) and extracted into a scratch prefix with
       `dpkg -x <pkg>.deb <prefix>`; the test run then just needs
       `PATH` (for the `tesseract` binary Tika's OCR module shells out
       to), `LD_LIBRARY_PATH` (for `libtesseract.so`/`libleptonica.so`),
       and `TESSDATA_PREFIX` (the `eng`/`por` trained-data files) pointed
       at that prefix — no code change, no root, no persistent system
       change. The production Dockerfile's `apk add tesseract-ocr` (a
       real install on the runtime image) is unaffected and unrelated to
       this local workaround.
- [x] 21. Implement the OCR path (Tika's already-present
       `tika-parser-ocr-module`, `tesseract-ocr` binary added to the
       runtime image) (Green) — end-to-end verified by task 20.
- [x] 22. Test: uploading an audio fixture, with
       `OpenAiAudioTranscriptionModel` mocked to return known text,
       eventually reaches `READY` with that text (Red).
- [x] 23. Implement `AudioTranscriptionExtractor` (Green).
- [x] 24. Test: uploading a corrupt/unrecognizable file reaches `FAILED`
       with a non-empty reason, is not stuck `PROCESSING`, and does not
       land in the DLQ (REQ-10's "not re-thrown for retry") (Red).
- [x] 25. Implement the failure-handling path in
       `ArticleExtractionListener` (catch, mark `FAILED`, no rethrow)
       (Green).
- [x] 26. Test: an article in `PROCESSING` or `FAILED` still appears in
       the list endpoint (REQ-11) (Red).
- [x] 27. (Green — should already hold if the list query doesn't filter
       by status; write it anyway as a regression guard.)

## 5. Listing, viewing, editing, deleting (REQ-2, REQ-3, REQ-12, REQ-13, REQ-14)

- [x] 28. Test: `GET .../articles` requires `ARTICLE_VIEW`; lists only
       the active tenant's articles (Red).
- [x] 29. Implement the list endpoint (Green).
- [x] 30. Test: `GET .../articles/{id}` requires `ARTICLE_VIEW` and
       returns a working pre-signed `originalFileUrl` (Red).
- [x] 31. Implement the detail endpoint (Green).
- [x] 32. Test: `PUT .../articles/{id}` requires `ARTICLE_EDIT`
       independent of `ARTICLE_CREATE` — a user with only edit can fix an
       existing article's text (REQ-13) (Red).
- [x] 33. Implement the edit endpoint (Green).
- [x] 34. Test: `DELETE .../articles/{id}` requires `ARTICLE_DELETE`;
       soft-deletes (row still queryable directly, absent from the list)
       (Red).
- [x] 35. Implement the delete endpoint (soft delete) (Green).
- [x] 36. Test: each of view/create/edit/delete produces its own
       `@AuditLog` action string, distinguishable in `AuditEvent` (REQ-3)
       (Red).
- [x] 37. Implement `@AuditLog` on all five endpoints (Green — likely
       already covered if annotated during tasks 10/29/31/33/35; write
       the test first regardless, per TDAD).

## 6. Final verification

- [x] 38. Run the full `./mvnw spotless:apply && ./mvnw verify` and
       confirm the entire suite (auth + tenancy + onboarding-status +
       article-management) is green.
- [x] 39. Update `PLAN.md` if any decision changed during implementation.
- [x] 40. Update `SPEC.md`'s acceptance-criteria checkboxes to reflect
       what's now verified by tests.
