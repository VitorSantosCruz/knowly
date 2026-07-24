# SPEC — Article management (UI)

## Context and motivation

The `knowly` backend's `article-management` feature already implements
the full article lifecycle (upload PDF/image/audio, async text
extraction/OCR/transcription, view/edit/delete), gated by four
independent permissions. Nothing in the frontend uses it yet — this is
the screen where a tenant user actually builds the knowledge base the
`conversations` chat reads from.

## User stories

- As a user with the create permission, I want to upload a document and
  see it appear right away, with a clear "processing" status until it's
  ready to be used by the chat.
- As a user with the view permission, I want to see all of the tenant's
  articles and open one to read its extracted text.
- As a user with the edit permission, I want to fix a wrong OCR/
  transcription result directly in the article's text.
- As a user with the delete permission, I want to remove an article I no
  longer need.
- As a user without one of these permissions, I want the corresponding
  action to simply not be available to me, rather than failing
  confusingly after I try it.

## Requirements (EARS/GEARS)

- **REQ-1 [Ubiquitous]** The system shall show, at the `/articles`
  route, the list of the active tenant's articles with title and status.
- **REQ-2 [Event-Driven]** When a user with the create permission
  uploads a supported file (PDF, PNG, JPEG, MP3, WAV, M4A) with a title,
  the system shall submit it and show it in the list immediately with a
  "processing" status.
- **REQ-3 [State-Driven]** While any article in the list is
  "processing", the system shall periodically refresh the list until
  none remain in that state, so status changes are visible without a
  manual reload.
- **REQ-4 [Unwanted Behavior]** If an upload is rejected (unsupported
  type or file too large), then the system shall show a clear error
  without adding anything to the list.
- **REQ-5 [Event-Driven]** When a user selects an article, the system
  shall show its extracted text (or its failure reason, if "failed"),
  its status, and a link to the original file.
- **REQ-6 [Event-Driven]** When a user with the edit permission changes
  an article's title or text and saves, the system shall persist the
  change and reflect it immediately.
- **REQ-7 [Event-Driven]** When a user with the delete permission
  removes an article, the system shall remove it from the list.
- **REQ-8 [Unwanted Behavior]** If any article action is denied (403),
  then the system shall show a clear "you don't have access to this"
  state rather than a broken screen.
- **REQ-9 [Optional Feature]** Where the caller lacks a given permission
  (create/edit/delete), the system shall hide the corresponding action
  instead of showing it disabled or letting it fail.

## Non-functional requirements

- Design: follows the established design-system standard, consistent
  with `dashboard`/`members`/`conversations`.
- Accessibility: upload form and article list/detail are keyboard
  operable.

## Acceptance criteria

- [x] The articles screen lists the active tenant's articles with title
      and status.
- [x] Uploading a supported file adds it to the list immediately as
      "processing".
- [x] The list refreshes automatically while any article is
      "processing", until all have moved past it.
- [x] An unsupported/oversized upload shows an error, nothing added.
- [x] Selecting an article shows its text (or failure reason) and a link
      to the original file.
- [x] Editing an article's title/text persists and reflects immediately.
- [x] Deleting an article removes it from the list.
- [x] A 403 from any action shows a clear permission-denied message
      (list, upload, select, edit, and delete each handle it
      independently, as a defense-in-depth backstop behind REQ-9's
      hide-not-fail approach — e.g. a permission revoked mid-session
      before `PermissionsService` refreshes).
- [x] Create/edit/delete actions are hidden (not just disabled) for a
      user who lacks the corresponding permission.

## Out of scope

- Re-processing/retrying a "failed" article (matches the backend's own
  out-of-scope — a failed upload must be deleted and re-uploaded).
- Full-text search across articles.
- Bulk upload/delete.
- Rich-text editing of an article's text (plain textarea for this first
  version).
