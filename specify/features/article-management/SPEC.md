# SPEC — Article management

## Context and motivation

This is the first real piece of tenant business data built on top of the
`tenancy` feature's permission model: tenant users upload documents (PDF,
image, audio) that become "articles" — the knowledge base the future
AI-assisted search feature will read from. Every article is normalized to
plain text on upload (so the AI/search feature that consumes it later
never has to care whether the source was a PDF, a scanned image, or a
recording), while the original file stays attached for reference.

This feature covers the article entity, its CRUD, file storage, and the
async text-normalization pipeline. It deliberately does **not** cover
search/AI-assisted answering or usage metrics — those are later features
that read what this one produces (see "Out of scope").

## User stories

- As a tenant user with the right permission, I want to upload a PDF,
  image, or audio file and have it become a searchable article without
  manually transcribing anything.
- As a tenant user, I want to see the list of articles in my tenant and
  open one to read or edit its text.
- As a tenant admin, I want article view/create/edit/delete to be
  separately grantable permissions, not a single all-or-nothing "articles"
  toggle.
- As anyone, I want to know when my upload failed to process (corrupt
  file, unsupported format) instead of it silently vanishing or hanging
  forever in "processing".

## Requirements (EARS/GEARS)

### Article entity and permissions

- **REQ-1 [Ubiquitous]** The system shall represent an article as a
  tenant-scoped entity with: a title, normalized plain-text body, a
  processing status, and a reference to its original uploaded file.
- **REQ-2 [Ubiquitous]** The system shall define four independent
  permissions for articles — view, create, edit, delete — each grantable
  on its own (directly or via access group), per the tenancy feature's
  deny-by-default model. Having one does not imply another (e.g. a user
  who can create articles cannot necessarily delete the one they just
  created).
- **REQ-3 [Ubiquitous]** Every article read (view, list) and write
  (create, edit, delete) shall be logged via the existing `@AuditLog`
  mechanism, consistent with the tenancy feature's audit conventions.
- **REQ-4 [Ubiquitous]** Articles shall be isolated per tenant via the
  same Hibernate-filter mechanism already established for tenant-scoped
  entities — an article never appears in another tenant's queries.

### Upload and file storage

- **REQ-5 [Event-Driven]** When a user with the create permission uploads
  a file, the system shall accept PDF, common image formats (PNG, JPEG),
  and common audio formats (MP3, WAV, M4A), store the original file in
  object storage, create an article record in a "processing" status, and
  return immediately without waiting for text extraction to finish.
- **REQ-6 [Unwanted Behavior]** If the uploaded file's type is not one of
  REQ-5's supported formats, then the system shall reject the upload
  before storing anything, with a clear unsupported-format error.
- **REQ-7 [Unwanted Behavior]** If the uploaded file exceeds the
  configured maximum size, then the system shall reject the upload before
  storing anything.

### Text normalization pipeline

- **REQ-8 [Event-Driven]** When an article finishes uploading, the system
  shall asynchronously extract plain text from the original file: direct
  text extraction for PDFs, OCR for images, and speech-to-text
  transcription for audio.
- **REQ-9 [Event-Driven]** When text extraction succeeds, the system
  shall store the extracted text on the article and mark it "ready".
- **REQ-10 [Unwanted Behavior]** If text extraction fails (corrupt file,
  unrecognizable content, extraction provider error), then the system
  shall mark the article "failed" with a reason, rather than leaving it
  "processing" indefinitely or silently discarding it.
- **REQ-11 [State-Driven]** While an article is "processing" or "failed",
  the system shall still let it appear in the article list (so uploads
  are never invisible) but shall not treat its (absent) text as available
  to read/edit until it reaches "ready".

### Listing, viewing, editing, deleting

- **REQ-12 [Ubiquitous]** The system shall let a user with the view
  permission list all of their active tenant's articles and open one to
  read its title, normalized text, status, and a reference/link to the
  original file.
- **REQ-13 [Ubiquitous]** The system shall let a user with the edit
  permission change an article's title and normalized text directly
  (e.g. to fix an OCR/transcription mistake), independent of who created
  it or who has the create permission.
- **REQ-14 [Ubiquitous]** The system shall let a user with the delete
  permission remove an article. Per the tenancy feature's own convention
  for removable records, this is out of scope to decide here whether it's
  a hard or soft delete — see "Out of scope".

## Non-functional requirements

- Reliability: the upload endpoint itself must stay fast regardless of
  file size or audio length — REQ-5 explicitly returns before extraction
  starts. Extraction runs as a background job (RabbitMQ), consistent with
  the project's existing async patterns (e.g. the login-code email flow).
- Observability: every stage (upload accepted, extraction started,
  extraction succeeded/failed) is logged with the article id and tenant
  id as first-class fields, per the constitution's structured-logging
  convention.
- Security: object storage access is never exposed directly to the
  client — the backend proxies/signs any access to the original file.

## Acceptance criteria

- [x] Uploading a supported PDF/image/audio file creates an article in
      "processing" status and returns immediately.
      (`ArticleControllerIntegrationTest`)
- [x] An unsupported file type or oversized file is rejected before
      anything is stored. (`ArticleControllerIntegrationTest` covers
      unsupported type; `ArticleUploadSizeLimitIntegrationTest` covers
      oversized, with `knowly.article.max-file-size` overridden low via
      `@TestPropertySource` rather than uploading a genuinely large
      file.)
- [x] A processing PDF eventually reaches "ready" with correct extracted
      text. (`ArticleExtractionListenerTest`, against a real
      PDFBox-generated PDF and real Tika extraction)
- [x] A processing image eventually reaches "ready" with OCR'd text.
      (`ArticleExtractionListenerTest`, against a real PNG generated
      in-test with `Graphics2D`/`ImageIO` and real Tesseract OCR — see
      TASKS.md task 20 for how `tesseract` was made available in this
      sandbox without root.)
- [x] A processing audio file eventually reaches "ready" with transcribed
      text. (`ArticleExtractionListenerTest`, `TranscriptionModel`
      mocked at the Spring AI client boundary — no real Whisper API
      call is exercised)
- [x] A corrupt/unrecognizable file reaches "failed" with a reason,
      never stuck in "processing". (`ArticleExtractionListenerTest`)
- [x] A user with only the view permission can list and open articles but
      gets denied on create/edit/delete. (`ArticleControllerIntegrationTest`
      proves view-without-create is denied; edit/delete-independence is
      covered by the edit/delete-specific tests below rather than a single
      combined test.)
- [x] A user with edit but not create can still fix an existing article's
      text. (`ArticleControllerIntegrationTest`)
- [x] Articles from one tenant never appear in another tenant's list.
      (`ArticleControllerIntegrationTest`, `ArticleRepositoryTest`)
- [x] Every list/view/create/edit/delete action produces an audit event.
      (`ArticleControllerIntegrationTest` verifies this for `list`; the
      other four actions carry the identical `@AuditLog` mechanism
      already verified generically in the `tenancy` feature's
      `AuditLogAspectTest`.)

## Out of scope

- AI-assisted search/answering over articles (a later feature that reads
  the normalized text this one produces).
- Usage metrics (per-article usage counts, conversations, messages —
  the `onboarding-dashboard` frontend feature's metrics endpoints, a
  separate future backend feature that queries data this one creates).
- Hard-vs-soft delete semantics for REQ-14 (decided in PLAN.md, not here
  — this SPEC only requires that a delete permission exists and is
  independently grantable).
- Article versioning/revision history beyond what Envers already gives
  entity-state auditing for.
- Any UI (a separate `knowly-app` feature, built after this backend
  contract exists).
