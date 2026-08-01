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
- As any user watching the list refresh automatically, I want the screen
  to stay visually stable instead of flickering/re-rendering while I'm
  reading or scrolling it.
- As a user with the delete permission, I want to be asked to confirm
  before an article is actually removed, so I don't lose it to a
  misclick.
- As a user filling in the upload form, I want the Upload button to
  visibly tell me it isn't ready yet until I've picked a file and given
  it a title.
- As a user with no article selected, I want the upload panel and list
  to use the full screen width, and only make room for the article
  content panel once I've actually selected something to read.

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
- **REQ-10 [State-Driven]** While the list refreshes automatically
  (REQ-3's polling), the system shall update only the article rows whose
  data actually changed, without clearing/re-rendering the full list,
  without showing a full-page/blocking loading indicator, and without
  moving the user's current scroll position — a background refresh must
  not be visually distinguishable from "nothing happened" unless
  something did.
- **REQ-11 [Event-Driven]** When a user with the delete permission
  triggers "Delete" on an article, the system shall show a confirmation
  prompt naming the article before removing anything.
- **REQ-12 [Event-Driven]** When the user confirms the deletion prompt,
  the system shall remove the article from the list (per REQ-7).
- **REQ-13 [Unwanted Behavior]** If the user dismisses or cancels the
  deletion confirmation prompt, then the system shall leave the article
  unchanged in the list and perform no deletion.
- **REQ-14 [State-Driven]** While the upload form is missing a title, a
  selected file, or both, the system shall render the Upload button in a
  visibly disabled state and shall not submit an upload if it is
  clicked.
- **REQ-15 [Event-Driven]** When both a title and a file have been
  provided in the upload form, the system shall enable the Upload
  button.
- **REQ-16 [State-Driven]** While no article is selected, the system
  shall render the upload panel and article list at the full width of
  the content area.
- **REQ-17 [Event-Driven]** When a user selects an article, the system
  shall shrink the upload panel and article list to a narrower column
  and show the selected article's content panel alongside it.

## Non-functional requirements

- Design: follows the established design-system standard, consistent
  with `dashboard`/`members`/`conversations`.
- Accessibility: upload form and article list/detail are keyboard
  operable. The deletion confirmation prompt is keyboard-operable and
  focus-trapped/dismissible with `Escape`, consistent with any other
  confirmation dialog already established in this codebase.

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
- [x] A background poll (REQ-3) that finds no data changes does not
      blank/re-render the list, does not show a full-page loading
      indicator, and does not move the scroll position.
- [x] A background poll that finds a status change updates only the
      affected row(s), without a full-list flicker.
- [x] Clicking "Delete" opens a confirmation prompt naming the article;
      the article is only removed after the user confirms.
- [x] Cancelling/dismissing the deletion confirmation leaves the article
      list unchanged.
- [x] The Upload button is visibly disabled while no title, no file, or
      neither has been provided, and does not submit if clicked in that
      state.
- [x] The Upload button becomes enabled once both a title and a file are
      present.
- [x] With no article selected, the upload panel + list occupy the full
      width of the content area.
- [x] Selecting an article shrinks the upload panel + list to a narrower
      column and reveals the article content panel alongside it.

## Out of scope

- Re-processing/retrying a "failed" article (matches the backend's own
  out-of-scope — a failed upload must be deleted and re-uploaded).
- Full-text search across articles.
- Bulk upload/delete.
- Rich-text editing of an article's text (plain textarea for this first
  version).
- Undo/restore after a confirmed deletion (REQ-11–13 only add a
  confirmation step before deletion, not a recovery mechanism after it).
- Configurable/animated column-width transition timing for the
  full-width ↔ narrow-column layout switch (REQ-16/17 only specify the
  two end states, not transition/animation behavior).
