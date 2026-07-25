> **Reference example.** This SPEC/PLAN/TASKS exists only to demonstrate the
> format expected by this project's Spec-Driven Development process. It has
> not been implemented; use it as a model when writing the SPEC for a real
> feature.

# SPEC — Tags CRUD

## Context and motivation

`knowly` needs a simple resource to classify content by keywords ("tags").
It's the system's first domain entity and serves as the reference case for
the CRUD pattern other entities will follow.

## User stories

- As an authenticated user, I want to create a tag so I can classify
  content with it later.
- As an authenticated user, I want to list existing tags so I can pick one
  instead of duplicating one that already exists.
- As an authenticated user, I want to remove a tag that no longer makes
  sense.

## Requirements (EARS/GEARS)

- **REQ-1 [Event-Driven]** When the client sends `POST /api/tags` with a
  valid `name` (1–50 characters, not empty), the system shall create the
  tag and respond `201 Created` with the created resource.
- **REQ-2 [Unwanted Behavior]** If `name` is empty, exceeds 50 characters,
  or a tag with the same name already exists (case-insensitive), then the
  system shall respond `400 Bad Request` without creating the tag.
- **REQ-3 [Ubiquitous]** The system shall expose `GET /api/tags` returning
  the list of tags ordered by name (A–Z).
- **REQ-4 [Event-Driven]** When the client sends `DELETE /api/tags/{id}`
  for an existing id, the system shall remove the tag and respond `204 No
  Content`.
- **REQ-5 [Unwanted Behavior]** If `DELETE /api/tags/{id}` references a
  non-existent id, then the system shall respond `404 Not Found`.

## Non-functional requirements

- Security: all endpoints require an authenticated user (Spring Security);
  no public endpoint.
- Observability: validation and not-found errors must be logged at `WARN`
  level, never `ERROR` (they are not system failures).

## Acceptance criteria

- [ ] Creating a valid tag returns 201 and the body contains `id` and
      `name`.
- [ ] Creating a duplicate tag (same name, case-insensitive) returns 400.
- [ ] Listing tags returns an alphabetically ordered array.
- [ ] Removing an existing tag returns 204 and it disappears from the
      listing.
- [ ] Removing a non-existent tag returns 404.

## Out of scope

- Editing (`PUT`/`PATCH`) tags — future feature, not covered here.
- Associating tags with other entities (content, users) — future feature.
