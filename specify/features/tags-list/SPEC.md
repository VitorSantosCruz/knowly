> **Reference example.** This SPEC/PLAN/TASKS exists only to demonstrate the
> format expected by this project's Spec-Driven Development process. It has
> not been implemented; use it as a model when writing the SPEC for a real
> feature. Depends on the API described in
> `knowly/specify/features/tags-crud/`.

# SPEC — Tags management screen

## Context and motivation

Interface for the user to view, create, and remove the tags exposed by the
`knowly` backend's `/api/tags` API. The app's first screen, and the
reference case for the list + simple form pattern.

## User stories

- As an authenticated user, I want to see the list of existing tags to
  understand what's already been registered.
- As an authenticated user, I want to create a new tag through the form
  without reloading the page.
- As an authenticated user, I want to remove a tag from the list with
  confirmation, to avoid accidental removal.

## Requirements (EARS/GEARS)

- **REQ-1 [Ubiquitous]** The system shall load and display the list of tags
  (via `GET /api/tags`) when entering the screen.
- **REQ-2 [Event-Driven]** When the user submits the new-tag form with a
  filled-in name, the system shall call `POST /api/tags` and, on success,
  add the tag to the list without reloading the page.
- **REQ-3 [Unwanted Behavior]** If the API responds `400` on creation
  (empty or duplicate name), then the system shall display the returned
  error message below the field, without clearing what the user typed.
- **REQ-4 [Event-Driven]** When the user confirms removal of a tag, the
  system shall call `DELETE /api/tags/{id}` and remove the tag from the
  displayed list on success.
- **REQ-5 [State-Driven]** While a request (list, create, or remove) is in
  progress, the system shall disable the corresponding action button to
  prevent double submission.

## Non-functional requirements

- Accessibility: form navigable by keyboard; validation errors associated
  with the field via `aria-describedby`.
- Responsiveness: list usable on mobile viewport (≥360px wide).

## Acceptance criteria

- [ ] On loading the screen, the list reflects what the API returns.
- [ ] Creating a valid tag updates the list without a reload.
- [ ] A 400 error on creation shows a message and keeps the typed value.
- [ ] Removing a tag disappears from the list after confirmation.
- [ ] Buttons are disabled while requests are in progress.

## Out of scope

- Editing tags — no endpoint exists for this yet (see backend SPEC).
- Pagination — a full list is acceptable at the current expected volume.
