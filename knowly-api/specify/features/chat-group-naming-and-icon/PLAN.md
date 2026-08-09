# PLAN — Group (`ChatConversation`) rename and icon

> The how. Translates SPEC.md into concrete technical decisions.
> References SPEC.md and the sibling `conversations/PLAN.md` (Amended
> 2026-08-09 section) for the shared `IconKey` decision.

## Architectural decisions

### Shared `IconKey` enum (no new decision here — reuses `conversations`')

- `ChatConversation.icon` is typed against the same
  `br.com.conectabyte.knowly.icon.IconKey` enum introduced by
  `conversations/PLAN.md`'s Amended 2026-08-09 section, not a second
  enum. Full reasoning in `DECISIONS.md` ("shared `IconKey` enum in a
  new small cross-cutting package," 2026-08-09) — not repeated here.
  Whichever of the two features (`conversations` or this one) is
  implemented first creates the `br.com.conectabyte.knowly.icon`
  package and the enum; the second only adds its own `@Enumerated`
  column referencing it. **Coordinate implementation order** so the
  enum isn't created twice under two different names — check whether it
  already exists before adding it.

### `ChatConversation.icon` column and creation-time acceptance (REQ-1, REQ-4)

- New nullable column `icon VARCHAR(50)` on `chat_conversations` (and
  `chat_conversations_aud`), `@Enumerated(EnumType.STRING)` typed
  `IconKey` — same nullable-for-default-fallback reasoning as
  `Conversation.icon` (the "no icon set" default/fallback presentation
  is a frontend display decision, not a backend-synthesized value).
- `CreateChatConversationRequestDto` gains an optional `IconKey icon`
  field alongside its existing `kind`/`tenantId`/`title`/
  `participantUserIds`/`visibility`. Left null/unvalidated-for-presence
  for `kind=DIRECT` (1:1 conversations have no icon concept, mirroring
  how `visibility` is already null/ignored for `DIRECT` in the existing
  DTO). An invalid enum string fails standard Bean Validation/JSON
  binding the same way an invalid `visibility` string would today —
  reuses the existing global `400` handling, no new validation
  mechanism.
- `ChatConversationService#createConversation` sets `icon` on the new
  `ChatConversation` when `kind=GROUP` (ignored for `DIRECT`, same as
  `title`/`visibility` already are).

### New rename endpoint (REQ-2, REQ-3)

- `PUT /api/chat/conversations/{id}` — chosen over `PATCH` for the same
  reason `conversations/PLAN.md`'s rename endpoint and this codebase's
  existing `PUT .../visibility` both did: a full-replace-of-named-fields
  action, matching this codebase's established `PUT` convention rather
  than introducing a `PATCH` precedent nothing else here uses.
- Request: `RenameChatConversationRequestDto(@NotBlank @Size(max = 255)
  String title, IconKey icon)` (icon optional/nullable) — same shape as
  `conversations`' `RenameConversationRequestDto`, intentionally
  parallel DTOs in each package rather than one shared DTO (the two
  entities' rename semantics are conceptually parallel but structurally
  independent — a shared request DTO would be an unnecessary coupling
  between the `conversation` and `chat` packages beyond the `IconKey`
  value type itself, which is the only thing that actually needs to be
  shared per the SPEC's "same fixed set" requirement).
  **[AppSec-added, 2026-08-09]** `@Size(max = 255)` matches the
  sibling `conversations/PLAN.md` fix for the same class of gap
  (unbounded `title` hitting a `VARCHAR(255)` column as an unhandled
  500 instead of a clean 400) — apply the same bound to
  `CreateChatConversationRequestDto.title` too if it doesn't already
  have one (it predates this PLAN; confirm at implementation time
  rather than assuming).
- Authorization: reuses `ChatConversationService#requireGroupAdmin`
  exactly, the same method already gating `promoteToAdmin`,
  `addParticipants`, `removeParticipant`, `changeVisibility` — **no new
  authorization check is written**, per this codebase's own explicit
  precedent (`chat-group-membership-management`'s AppSec follow-up note:
  every group-admin-only action must reuse the same per-conversation
  admin check, not a hand-rolled variant). `requireGroupAdmin` already
  throws `ChatAccessDeniedException`/`ChatConversationNotFoundException`
  as appropriate for a non-admin/non-participant caller or an
  unknown/wrong-kind conversation id — this endpoint inherits those
  exact status codes (403/404) unchanged, no new exception type needed.
- Blank `title` or an invalid `icon` string: standard Bean Validation
  400, same reasoning as `conversations/PLAN.md`'s "400, not 422"
  decision — this codebase has exactly one validation-failure handler
  app-wide and it always returns 400; introducing 422 here would be the
  same one-off inconsistency `conversations/PLAN.md` already declined
  to introduce.
- `ChatConversationService#renameConversation(User actor, Long id,
  String title, IconKey icon)`: calls `requireGroupAdmin`, then updates
  `title`/`icon` on the `ChatConversation`, and returns the existing
  `ChatConversationDetailDto` shape (same response type
  `changeVisibility` already returns) — not a new summary/detail DTO,
  reusing the existing detail assembly (`participantIds`, `nicknames`,
  `adminUserIds`, `avatarUrls`) since the frontend's REQ-40 needs the
  updated name/icon reflected "everywhere it appears," and the existing
  detail DTO is already the shape column 1's row-update logic consumes
  elsewhere in this codebase's chat flows.
- **1:1 (`DIRECT`) conversations are explicitly out of scope for rename**
  — `requireGroupAdmin` itself only resolves admin status via
  `chat_participants.is_admin`, which per `ChatController`'s own
  existing comment ("REQ-2: 1:1 conversations never get an admin
  override, of any kind") never applies to `DIRECT` kind; attempting to
  rename a `DIRECT` conversation id through this endpoint therefore
  already fails via the existing admin check with no additional
  kind-check needed — but this PLAN calls it out explicitly as a
  Tier-1 "no code needed" reliance on existing behavior, not an
  oversight, in case an implementer is tempted to special-case it.

## Data schema

`V33__add_icon_to_chat_conversations.sql` (Amended — exact version
number to confirm at implementation time against whatever's latest,
coordinated with `conversations/PLAN.md`'s own `V32`):

```sql
ALTER TABLE chat_conversations ADD COLUMN icon VARCHAR(50);
ALTER TABLE chat_conversations_aud ADD COLUMN icon VARCHAR(50);
```

No backfill needed — `icon` stays nullable indefinitely (see reasoning
above), so existing groups simply read as "no icon set."

## API contracts

| Method | Path | Request | Response | Status |
|---|---|---|---|---|
| `POST` | `/api/chat/conversations` | (existing, +) `icon?: IconKey` alongside `kind`/`tenantId`/`title`/`participantUserIds`/`visibility` | `ChatConversationSummaryDto` (unchanged shape — icon added if the summary DTO doesn't already carry it, see task list) | `201`; `400` invalid icon key |
| `PUT` | `/api/chat/conversations/{id}` | `{ title: string (required, non-blank), icon?: IconKey }` | `ChatConversationDetailDto` | `200`; `400` blank title/invalid icon; `403` caller is not a current group admin (or is a 1:1 participant — `requireGroupAdmin`'s existing behavior); `404` unknown/wrong-kind/deleted conversation id |

`ChatConversationSummaryDto`/`ChatConversationDetailDto`: confirm at
implementation time whether `icon` needs adding to both or just the
detail DTO — check current field lists before assuming; if the summary
DTO is what column 1's row list (`GET /api/chat/conversations`)
actually renders from (per `chat-unified-ui`'s REQ-2/REQ-2d row
rendering), it needs `icon` too, not just the detail shape.

## Dependencies

- No new dependencies. Reuses `IconKey` (shared with `conversations`),
  existing `ChatConversationService`/`ChatController`/
  `ChatParticipantRepository` machinery, existing `@AuditLog`/Bean
  Validation/global exception-handling infrastructure.

## Package/file structure

- `br.com.conectabyte.knowly.icon`: `IconKey` enum (shared, created
  once — see coordination note above).
- `br.com.conectabyte.knowly.chat`: extend `ChatConversation` with
  `icon`; extend `CreateChatConversationRequestDto` with `icon`; add
  `RenameChatConversationRequestDto`; extend `ChatConversationService`
  with `renameConversation`; extend `ChatController` with `PUT
  /conversations/{id}`; extend `ChatConversationSummaryDto`/
  `ChatConversationDetailDto` with `icon` (confirm both vs. detail-only
  per the API contracts note above).

## Testing strategy

- `ChatConversationServiceTest`/`ChatControllerIntegrationTest`
  additions:
  - Creating a `GROUP` with a valid `icon` persists it; with an invalid
    `icon` string returns `400`, no group created.
  - Creating a `DIRECT` conversation with an `icon` present in the
    request either ignores it or is rejected — pick one behavior
    explicitly at implementation time (recommend: ignore, mirroring
    existing `visibility`-for-`DIRECT` handling, for consistency) and
    assert it, rather than leaving it an implicit accident of whatever
    the JSON binding happens to do.
  - A current group admin renames `title`/`icon`; response reflects
    both; a subsequent `GET` confirms persistence.
  - A non-admin participant of the **target** group gets `403` (not
    `404`) — reuses `requireGroupAdmin`'s existing tested behavior, but
    add this endpoint's own explicit case per the AppSec follow-up
    precedent in `chat-group-membership-management/PLAN.md` (don't
    assume coverage transfers automatically; write the test for this
    specific action).
  - An admin of a **different** group cannot rename this one (`403`) —
    same AppSec-driven per-action, per-conversation-scoping matrix
    precedent.
  - A blank `title` or invalid `icon` on rename returns `400`; the
    group's prior `title`/`icon` are unchanged afterward (no partial
    update).
  - `@AuditLog` event asserted for the rename action
    (`chat.group.rename` or similar, following this file's existing
    `chat.group.*` naming convention).

## Emergent decisions

- `IconKey` was created by this feature's sibling, `conversations`'
  Amended 2026-08-09 tasks, which landed first in the same session;
  `chat-group-naming-and-icon`'s task 1 was a no-op reuse (confirmed
  the package/enum already existed before adding `ChatConversation`'s
  own `@Enumerated` column against it), exactly as PLAN anticipated.
- Final curated `IconKey` catalog (24 values, all confirmed against
  real `@lucide/angular` component exports in
  `knowly-app/node_modules/@lucide/angular`): `MESSAGE_CIRCLE`,
  `MESSAGES_SQUARE`, `BOOK_OPEN`, `NOTEBOOK`, `SPARKLES`, `BOT`,
  `USERS`, `HASH`, `FOLDER`, `STAR`, `HEART`, `FLAG`, `TARGET`,
  `ROCKET`, `LIGHTBULB`, `GLOBE`, `COMPASS`, `GRADUATION_CAP`,
  `BRIEFCASE`, `ARCHIVE`, `TAG`, `BOOKMARK`, `LAYERS`, `CODE`.
- `renameConversation` deliberately does **not** call
  `requirePeerGroup` before `requireGroupAdmin` (unlike
  `promoteToAdmin`/`addParticipants`, which call both) -- confirmed at
  implementation time that `requireGroupAdmin`'s own
  `chat_participants.is_admin` lookup already rejects a `DIRECT`
  conversation id (a participant row exists but is never admin-flagged
  for that kind) with `403`, exactly as this PLAN's "no code needed"
  note predicted. Verified by
  `ChatGroupNamingAndIconControllerIntegrationTest`; an unknown/deleted
  conversation id still gets `404` via `loadConversation`.
- "Summary vs. detail DTO" resolved: `icon` was added to **both**
  `ChatConversationSummaryDto` and `ChatConversationDetailDto` --
  `ChatConversationSummaryDto` already backs the row list `GET
  /api/chat/conversations` renders from (chat-unified-ui REQ-2/REQ-2d),
  so it needed `icon` too, not just the detail shape.
- "Ignore vs. reject icon on `DIRECT`" resolved: **ignore**, mirroring
  `visibility`'s existing `DIRECT`-is-ignored precedent in
  `ChatConversationService#createConversation` -- an `icon` present on
  a `kind=DIRECT` create request is silently dropped, never rejected.
  Covered by `creatingADirectConversationWithAnIconIgnoresIt`.
