# PLAN — Article management

> The how. Translates the SPEC into concrete technical decisions.
> References SPEC.md.

## Architectural decisions

### Packages

- New package `br.com.conectabyte.knowly.article`: `Article` entity,
  `ArticleStatus` enum, `ArticleRepository`, `ArticleService`,
  `ArticleController`, DTOs, exceptions, the extraction pipeline
  (`ArticleUploadedEvent`, `ArticleExtractionPublisher`,
  `ArticleExtractionListener`, `TextExtractor` + one implementation per
  file kind), and `ArticleStorageService` (object storage wrapper).
- New `Permission` enum constants in `br.com.conectabyte.knowly.tenancy`:
  `ARTICLE_VIEW`, `ARTICLE_CREATE`, `ARTICLE_EDIT`, `ARTICLE_DELETE`
  (REQ-2) — added to the existing enum, not a new one, since
  `PermissionService`/`PermissionAspect` already operate generically over
  `Permission` regardless of feature.

### Entity and status

- `Article`: id, tenant (`@ManyToOne`, `@Filter` per REQ-4, same
  `tenantFilter` already defined on `TenantMembership`/`AccessGroup`),
  title, `text` (nullable — absent until extraction succeeds, per
  REQ-11), `status` (`ArticleStatus`: `PROCESSING`, `READY`, `FAILED`),
  `failureReason` (nullable, populated only when `FAILED`),
  `originalFileKey` (the object-storage key), `originalFileName`,
  `originalContentType`. `@Audited` + JPA Auditing, same convention as
  every other entity.
- REQ-14's delete: **soft delete**, via the same `active` boolean pattern
  already established on `TenantMembership` (`ArticleRepository`
  queries always filter `active = true`; nothing is ever physically
  removed) — consistent with the tenancy feature's own precedent for
  removable tenant-scoped records, and it keeps the original file/Envers
  history queryable after "deletion", which a hard delete would lose.

### Object storage

- AWS SDK v2's S3 client (`software.amazon.awssdk:s3`) configured with a
  custom endpoint override, pointed at a **MinIO** container in
  `compose.yaml` (S3-compatible, self-hosted — avoids taking a real AWS
  dependency for local/self-hosted deployments, while keeping the actual
  client code identical to what a real S3 bucket would use later).
- `ArticleStorageService` wraps `PutObject`/`GetObject`/pre-signed
  `GetObject` URL generation — the controller never returns a raw
  storage URL or credential to the client; it returns a short-lived
  pre-signed URL for the original file (SPEC's "Security" NFR: object
  storage is never exposed directly).
- Object key convention: `tenants/{tenantId}/articles/{articleId}/original`
  (no need to keep the original filename in the key itself — it's stored
  separately on the entity for display/download purposes).

### Upload and async extraction

- `POST /api/tenants/{tenantId}/articles` accepts `multipart/form-data`
  (title + file). Validates content-type against an explicit allow-list
  and size against a configured max (REQ-6/7) **before** touching object
  storage — rejecting cheaply first. On success: uploads to object
  storage, saves the `Article` row as `PROCESSING`, publishes an
  `ArticleUploadedEvent` to RabbitMQ, and returns `202 Accepted` with the
  article (REQ-5 — chosen over `201` because the resource isn't fully
  "created" in its usable form yet, it's still processing).
- New RabbitMQ queue `article.uploaded` (+ DLQ, retry, publisher
  confirms — mirroring `AuthRabbitConfig`'s already-established pattern
  exactly, not reinventing it). `ArticleExtractionListener` consumes it,
  dispatches to the right `TextExtractor` by `originalContentType`, and
  on success/failure updates the article to `READY`/`FAILED` (REQ-9/10).
- `TextExtractor` interface, one implementation per kind:
  - `PdfTextExtractor` / image OCR: both via **Apache Tika**
    (`spring-ai-tika-document-reader` is already a dependency, pulling in
    Tika core) — Tika's `AutoDetectParser` handles PDF text extraction
    natively; image OCR additionally requires the `tika-parser-ocr-package`
    module plus a `tesseract-ocr` binary available on the runtime image
    (added to the app's `Dockerfile`).
  - `AudioTranscriptionExtractor`: Spring AI's
    `OpenAiAudioTranscriptionModel` (auto-configured by the already-present
    `spring-ai-starter-model-openai`, reusing the existing
    `OPENAI_API_KEY` — no new external credential).
- Extraction failures (Tika throwing, OCR producing nothing, transcription
  API error) are caught per-message and mapped to `FAILED` +
  `failureReason` — deliberately **not** re-thrown to trigger the queue's
  retry/DLQ machinery, since a corrupt file will never succeed on retry
  (REQ-10 wants a terminal, user-visible failure, not a DLQ entry nobody
  looks at).

## Data schema

`V7__create_articles_table.sql` + `V8__create_articles_envers_audit_table.sql`:

```sql
CREATE TABLE articles (
  id BIGSERIAL PRIMARY KEY,
  tenant_id BIGINT NOT NULL REFERENCES tenants(id),
  title VARCHAR(255) NOT NULL,
  text TEXT,
  status VARCHAR(20) NOT NULL,
  failure_reason VARCHAR(500),
  original_file_key VARCHAR(500) NOT NULL,
  original_file_name VARCHAR(255) NOT NULL,
  original_content_type VARCHAR(100) NOT NULL,
  active BOOLEAN NOT NULL DEFAULT true,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by VARCHAR(255) NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by VARCHAR(255) NOT NULL
);
CREATE INDEX ix_articles_tenant ON articles (tenant_id);
```

Envers mirror (`articles_aud`) follows the same pattern as every other
`_aud` table already in the project.

## API contracts

All under `/api/tenants/{tenantId}/articles`, each behind the matching
`@RequiresPermission`/`@AuditLog` pair:

- `POST /api/tenants/{tenantId}/articles` (multipart: `title`, `file`) →
  `202 { id, title, status: "PROCESSING" }` — requires `ARTICLE_CREATE`.
  `400 UNSUPPORTED_FILE_TYPE` / `400 FILE_TOO_LARGE` per REQ-6/7.
- `GET /api/tenants/{tenantId}/articles` → list, requires `ARTICLE_VIEW`.
- `GET /api/tenants/{tenantId}/articles/{articleId}` → detail + a
  pre-signed `originalFileUrl`, requires `ARTICLE_VIEW`.
- `PUT /api/tenants/{tenantId}/articles/{articleId}` (`{ title, text }`)
  → requires `ARTICLE_EDIT` (independent of `ARTICLE_CREATE`, REQ-13).
- `DELETE /api/tenants/{tenantId}/articles/{articleId}` → soft delete,
  requires `ARTICLE_DELETE`.

## Dependencies

- `software.amazon.awssdk:s3` (new) — object storage client.
- `org.apache.tika:tika-parser-ocr-package` (new) — OCR parser module;
  `spring-ai-tika-document-reader` already brings Tika core/PDF parsing.
- `org.testcontainers:minio` (new, test scope) — MinIO Testcontainers
  module for `TestcontainersConfiguration`.
- Runtime image: add `tesseract-ocr` (+ `tesseract-ocr-eng`/`-por`
  language packs) to the `Dockerfile`.
- `compose.yaml`: new `minio` service (S3-compatible, console disabled in
  prod-like config, healthcheck, resource limits — same hardening
  pattern as every other service there).

## Testing strategy

- `ArticleRepositoryTest`: soft-delete filtering, tenant-filter isolation
  (reusing the same pattern as `TenantIsolationIntegrationTest`).
- `ArticleControllerIntegrationTest` (Testcontainers incl. MinIO):
  upload → `202` + `PROCESSING`; unsupported type → `400`; oversized →
  `400`; permission checks per endpoint (view/create/edit/delete
  independence, REQ-2); soft-delete leaves the row queryable; audit event
  per action (REQ-3); cross-tenant isolation (REQ-4).
- `ArticleExtractionListenerTest`: a real small PDF fixture reaches
  `READY` with non-empty extracted text; a real small image fixture (with
  OCR-able text) reaches `READY`; a stubbed/mocked
  `OpenAiAudioTranscriptionModel` response reaches `READY` for an audio
  fixture (an actual Whisper API call isn't exercised in CI — mocked at
  the Spring AI client boundary, consistent with how `MailService` is
  mocked in `AuthControllerIntegrationTest`); a corrupt file reaches
  `FAILED` with a reason, not stuck `PROCESSING` and not in the DLQ.
