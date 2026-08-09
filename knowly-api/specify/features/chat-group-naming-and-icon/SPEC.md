# SPEC — Group (`ChatConversation`) rename and icon

> **This is an approval-trail SPEC, not a new product decision.** Every
> requirement below transcribes a decision the product owner has already
> made explicitly, recorded in two other, already-approved documents:
> - `knowly-api/specify/features/conversations/SPEC.md`'s amendment note
>   (2026-08-09), quoting the product owner directly: *"tanto grupo
>   quanto conversa com a base podem ser nomeados e renomeados"* (both
>   groups and RAG conversations can be named and renamed) — this is the
>   approval for group **renaming**.
> - `knowly-app/specify/features/chat-unified-ui/SPEC.md`'s "Tier 3 —
>   resolved (2026-08-09, Amended (4), final round)" — the product owner
>   confirmed directly that groups get the **same fixed Lucide icon
>   picker** as RAG conversations, at both creation and rename — this is
>   the approval for group **icon**.
>
> This document exists only because `conversations/SPEC.md` explicitly
> declined to fold group behavior into its own scope ("this SPEC only
> covers the RAG `Conversation` entity... a group-rename endpoint...
> is an amendment to `chat-group-membership-management` (or a new small
> SPEC)") and instructed whoever picks this up to cover exactly two
> things: (a) a rename endpoint accepting `title` and `icon` on
> `ChatConversation`, scoped to a viewer the backend reports as that
> group's admin, and (b) an optional `icon` field accepted at group
> creation, same fixed Lucide-key catalog. REQ-1 through REQ-4 below do
> exactly that, no more. **No new Tier 3 question is being decided by
> this document** — see PLAN.md for the technical "how."

## Context and motivation

Group creation (`POST /api/chat/conversations`, `kind=GROUP`) already
requires and stores a `title` (the frontend's "Criar grupo" dialog
blocks creation until a name is entered). It does not accept an `icon`
today, and there is no endpoint to rename a group's `title` or set/change
its `icon` after creation — confirmed by investigation: `ChatController`
has no `PUT`/`PATCH` route touching `ChatConversation.title`. This
mirrors, for groups, exactly what `conversations/SPEC.md`'s REQ-13
through REQ-16 already added for RAG conversations, and both features
exist to unblock the same frontend screen
(`knowly-app/specify/features/chat-unified-ui/SPEC.md`'s REQ-13/REQ-40).

## User stories

- As a group admin, I want to rename my group and change its icon after
  creation, the same way I can for a "Base de artigos" conversation, so
  the group stays identifiable/current as its purpose evolves.
- As a user creating a group, I want to optionally pick an icon for it
  at creation time, from the same fixed icon set used elsewhere in the
  app.

## Requirements (EARS/GEARS)

- **REQ-1 [Ubiquitous]** The system shall support an optional `icon`
  field on `ChatConversation`, accepted at group creation
  (`POST /api/chat/conversations`, `kind=GROUP`) as an optional key
  drawn from the same fixed, server-validated set of supported icon
  keys used by RAG conversations (`conversations`' REQ-15) — not free
  text, not an emoji, not an uploaded image. A group created without an
  `icon` keeps its existing default/fallback presentation, same as an
  RAG conversation without one.
- **REQ-2 [Event-Driven]** When a user the backend reports as a group's
  current admin (the same authorization model already used for
  `promoteToAdmin`/`addParticipants`/`changeVisibility` in
  `chat-group-membership-management`) submits a rename request with a
  new non-blank `title` and/or a new `icon`, the system shall update
  that group's `title`/`icon` and persist the change, without altering
  its participants, messages, visibility, or any other field.
- **REQ-3 [Unwanted Behavior]** If a rename request's `title` is
  missing/blank, or its `icon` (when provided) is not one of the fixed
  supported keys, or the acting user is not a current admin of the
  target group, then the system shall reject the request without
  applying any partial change.
- **REQ-4 [Unwanted Behavior]** If a group-creation request's `icon` is
  not one of the fixed supported keys, then the system shall reject the
  request (the existing required-`title`-on-creation behavior is
  unchanged — this REQ only concerns the new optional `icon`).

## Non-functional requirements

- Security: renaming is gated by the exact same group-admin
  authorization already enforced for every other group-admin-only
  mutation in `chat-group-membership-management` — no new authorization
  mechanism is introduced (see `PermissionAspect`/tenant-isolation
  precedents this codebase already treats as non-negotiable).
- Observability: the rename action is logged via the existing
  `@AuditLog` mechanism, consistent with every other group mutation.

## Acceptance criteria

- [x] A group can be created with an optional `icon` from the fixed set;
      an invalid `icon` key is rejected and no group is created.
- [x] A current group admin can rename a group's `title`/`icon`; the
      change is reflected on subsequent reads.
- [x] A non-admin participant (or a non-participant) attempting to
      rename a group is rejected; no partial change is applied.
- [x] A rename request with a blank `title` or an invalid `icon` key is
      rejected; no partial change is applied.

## Out of scope

- Any change to group creation's existing required-`title` behavior
  (unchanged, already shipped).
- Any change to group admin promotion/demotion, visibility, join
  requests, or membership — this document only adds naming/icon.
- 1:1 (`DIRECT`) conversation naming/icon — not a concept for direct
  conversations, not requested by the product owner, not covered here.
