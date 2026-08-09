# PLAN — chat-unified-ui (frontend)

> The how. Translates SPEC.md into concrete technical decisions.
> References SPEC.md (APPROVED FOR PLAN, 2026-08-08).
>
> **Reconciled against `knowly-api/specify/features/chat-group-membership-management/PLAN.md`
> (2026-08-08, closed as source of truth, including that PLAN's own "Frontend contract
> reconciliation" section)** — the API contract section below is the final, agreed shape;
> it is no longer provisional. Every earlier "provisional/to confirm" note has been
> resolved per that backend PLAN's diff table: discoverable-groups' path/pagination
> envelope, the promote-to-admin path, the visibility HTTP method, approve/reject's
> response shape, remove-participant's status code/body, leave's status code, the
> previously-missing add-participants endpoint, and the join status-code mapping all now
> match the backend PLAN verbatim, not this PLAN's original guesses.

## Architectural decisions

- **One route, `/chat`, replaces the three separate top-level routes
  (`/chat`, `/support`, `/conversations`) as the single nav entry
  ("Conversas") — but the four sections (People/Groups/Support/Base de
  artigos) are shell-internal state (a `section` query param), not four
  child routes.** Why: REQ-1/REQ-2 require one screen with one sidebar,
  and the four sections have **materially different guard needs**
  (People/Groups/Support carry no guard today — `STAFF_ADMIN` oversight
  and staff-only Support access must work with no active tenant
  selected; "Base de artigos" is tenant-scoped and today sits behind
  `tenantSelectionGuard`). A single Angular route can only carry one
  `canActivate` array, so gating per-section by route config is not
  possible without either (a) forcing every section behind
  `tenantSelectionGuard` (breaks the staff cross-tenant People/Groups/
  Support case, a regression of the exact bug class `member-admin-tenant-
  bypass`/`staffGuard` already fixed), or (b) four child routes each
  with their own guard, which reintroduces the very route-hopping REQ-1
  exists to remove and duplicates `SupportPageComponent`'s existing
  in-component dispatch pattern for no reason. **Decision: `/chat` itself
  carries no route guard** (same reasoning as today's `/chat`/`/support`
  entries), and the shell component checks "is there an active tenant"
  itself only for the "Base de artigos" section, rendering
  `NoTenantSelectedStateComponent` (or equivalent existing empty-state,
  reusing whatever `ConversationsPageComponent` already shows when no
  tenant is active) inline instead of blocking navigation — this is the
  same "one screen, N contexts, permission dispatch inside the
  component" pattern `SupportPageComponent`/`DashboardWrapperPageComponent`
  already establish, applied to a fourth context (RAG) instead of three.
- **`section` is a query param (`/chat?section=people|groups|support|articles`),
  not a path segment, and defaults to `people` when absent.** Why: the
  section is a *view mode* within one screen, not a distinct resource —
  matches this app's existing precedent of query params for transient
  view state (vs. path segments reserved for resource identity, per
  `internal-team-chat` PLAN's own routing rationale). A query param also
  makes "deep-link into Support" or "deep-link into Base de artigos"
  trivial to preserve from old bookmarks (see the redirect decision
  below) without inventing four new route configs.
- **Resource identity (an open conversation, group, support channel, or
  RAG conversation) stays in the path, as today, just nested one level
  under `/chat`** — preserves the existing "ids are path segments, not
  query params" rule (`internal-team-chat` PLAN):
  - `/chat/:conversationId` — peer 1:1 or group conversation (existing
    `ChatConversationSummaryDto`/`ConversationDetail` id space; a group
    is already just a `kind: 'PEER_GROUP'` conversation, so no separate
    id space is needed here — REQ-3/REQ-5 reuse the exact existing
    detail/message endpoints, unchanged).
  - `/chat/support/:channelId` — support channel (existing
    `SupportPageComponent`'s `:channelId`, now nested).
  - `/chat/articles/:conversationId` — RAG conversation (existing
    `ConversationsPageComponent`'s conversation id, now nested).
  A bare id under `/chat/:conversationId` alone would collide with the
  three different id spaces `internal-team-chat` (peer chat), the
  support feature (channel), and `conversations` (RAG) each mint
  independently (all `BIGSERIAL`, all starting at 1) — the `support/`
  and `articles/` segments disambiguate which service resolves the id,
  same reasoning as any existing disjoint-resource nesting in this app
  (e.g. `/tenants/access-groups` vs `/staff/access-groups`).
- **Old `/support` and `/conversations` top-level routes become
  `redirectTo` entries into the new nested paths (`/chat?section=support`
  and `/chat?section=articles`), not removed outright — `/chat` itself
  needs no redirect since its path is unchanged.** Checked first: the
  only in-app reference to the old top-level paths is
  `nav-menu.component.ts` (two separate nav entries, being collapsed
  into REQ-1's single entry — updated directly, not left to redirect)
  and `welcome-page.component.ts`'s `routerLink="/conversations"` (a
  first-run CTA — updated directly to point at
  `/chat?section=articles`). No e2e suite exists yet referencing these
  paths (verified — only unit specs assert `router.navigate(['/support'])`-
  style calls inside components being changed anyway). The `redirectTo`
  entries are kept anyway as a low-cost safety net for anything
  external (a bookmarked URL, a saved link in a support ticket) rather
  than a hard 404 — consistent with never breaking a resolvable URL
  without a documented reason.
- **`ChatShellComponent`, not a rename of `ChatPageComponent`, owns the
  sidebar + section dispatch; the three existing page components
  (`ConversationDetailComponent`-hosting logic, `SupportPageComponent`,
  `ConversationsPageComponent`) become section content rendered inside
  the shell's main panel, each keeping its own component/service
  entirely unchanged internally** — why: REQ-2's explicit framing ("one
  screen means a shared navigation surface only... nothing about how
  they work internally is being merged") means the safest architecture
  is composition, not a merge/rewrite of three already-shipped,
  already-tested feature areas. `ChatPageComponent` is retired in favor
  of `ChatShellComponent` (new name) because its current responsibility
  (list + outlet for one section) is being replaced by "sidebar +
  4-section dispatch," a different enough shape that reusing the old
  name/file would blur what changed for a future reader — matches how
  `internal-team-chat`'s own "Emergent decisions" section already
  documents deviating from a PLAN's original component name when the
  actual shape earned a different one.
- **People and Groups get one new shared list, `chat-directory.component.ts`**
  (not two near-duplicate list components) driven by a new
  `ChatDirectoryService` (see State and data) that composes the
  already-existing `ChatService.eligibleParticipants` (People,
  unchanged eligibility source, REQ-4) with a new "discoverable groups"
  fetch (Groups, REQ-8's candidate set) — why one component: REQ-8
  filters both concurrently with the identical name-match predicate:
  a single component with a `kind: 'person' | 'group'` per-row
  discriminator avoids writing the same filter/empty-state/no-results
  logic twice, mirroring `message-thread.component.ts`'s existing
  "one parametrized presentational component for two call sites"
  precedent in this exact feature area.
- **Client-side search (REQ-8/REQ-9/REQ-10/REQ-11) lives entirely inside
  `chat-directory.component.ts`, filtering the already-fetched
  `ChatService.conversations()` + `ChatService.eligibleParticipants()`
  + a new `ChatDirectoryService.discoverableGroups()` signals — no new
  endpoint, no debounce needed.** Why no debounce: this is an in-memory
  `Array.filter` over an already-loaded, per-NFR "reasonably bounded"
  list (not a network call per keystroke), so there is no request to
  throttle — a `computed()` signal keyed off a local `searchQuery`
  writable signal re-derives synchronously on every keystroke, same
  cost class as any other computed-signal filter already in this
  codebase (e.g. permission-gated nav item lists). Support and "Base de
  artigos" sections are structurally excluded from the filter (REQ-9)
  by never being part of the filtered list in the first place — they're
  separate sidebar sections rendered unconditionally, not rows that
  could accidentally match a query.
- **Group visibility/discovery and the join/admin actions are consumed
  through a new `chat-group.service.ts`, kept separate from
  `chat.service.ts` and `chat-directory.component.ts`'s own service**
  — why: `ChatService` already owns conversation list/detail/message
  state for both DIRECT and GROUP kinds (REQ-3/REQ-5 reuse it
  unchanged); the *new* surface this SPEC adds (visibility, join
  requests, admin promotion, participant removal, group deletion) is a
  materially different concern (group governance, not messaging) with
  its own error/loading shape (REQ-25/REQ-27's "leave state exactly as
  it was on failure," a non-optimistic pattern, unlike `ChatService
  .sendMessage`'s existing optimistic-append). Mixing the two into one
  service would blur "messaging state" and "group governance state" the
  same way `PermissionsService`/`GlobalPermissionsService` are kept
  separate on this exact principle. **Reconciled against the backend
  PLAN**: `ChatGroupService` reads/writes `ChatConversationDetailDto`
  (frontend-side `ConversationDetail`, extended with `visibility:
  'PRIVATE'|'REQUEST_TO_JOIN'|'PUBLIC'`, `archivedAt: string|null`,
  `adminUserIds: number[]` — additive fields on the same DTO
  `ChatService` already fetches, per the backend PLAN's "extended, not
  replaced" note) rather than a separate `GroupCapabilities`/summary
  shape this PLAN originally invented; `isAdmin` is now derived
  client-side as `adminUserIds.includes(currentUserId)`, not a
  dedicated capabilities endpoint (see "Consumed API contracts" below —
  there is no `GET .../capabilities` endpoint in the backend PLAN, that
  was this PLAN's own invention and is removed).
- **Every group governance action (REQ-15/17/23/24/29/30/32) is
  non-optimistic: call the endpoint, only patch local signal state on
  success, show an inline error and leave state untouched on failure.**
  Why: REQ-27 states this explicitly ("rather than optimistically
  applying the change before the backend confirms it") — this is a
  SPEC requirement, not a judgment call, and it deliberately diverges
  from `ChatService.sendMessage`'s existing optimistic pattern (correct,
  since sending a message and removing a participant/deleting a group
  have very different blast radii on failure).
- **"Criar grupo" is a new `create-group-dialog.component.ts`** (native
  `<dialog>`, per this codebase's established first-modal precedent —
  `deletion-confirmation-token`'s `ConfirmDialogComponent`, no new
  overlay library), replacing `new-conversation-dialog.component.ts`'s
  group-creation half (REQ-3's "no separate Nova conversa step" retires
  that dialog's 1:1 picker entirely; REQ-13's dedicated group-creation
  flow — name + visibility, no participant picker at creation time —
  is different enough from the old combined 1:1/group dialog that
  reusing it would keep dead 1:1-picker code alongside the new People
  list's click-to-open behavior). `participant-picker.component.ts` is
  **not removed** — the SPEC's backend companion still needs an
  "add participant to an existing group" flow (REQ-14's sibling, adding
  people *after* creation, called out explicitly in REQ-13: "other
  participants are added afterward"), and that add-participant flow
  reuses this existing picker unchanged, now driven by
  `ChatGroupService` instead of being invoked from the retired dialog.
- **Group visibility badge (REQ-26) is a new, small
  `group-visibility-badge.component.ts`**, mirroring
  `ticket-status-badge.component.ts`'s existing shape (one enum in,
  one styled chip out) — reused in the Groups section list, search
  results, and the group's own header, not three separate inline badges.

## Components and routes

```
core/
  chat.service.ts                 // unchanged internals; new consumers only
  support.service.ts              // unchanged
  conversation.service.ts         // unchanged (RAG, from `conversations`)
  chat-group.service.ts           // NEW — governance actions (join/leave/
                                   //   promote/visibility/delete/approve/reject)
  chat-directory.service.ts       // NEW — discoverable-groups fetch backing
                                   //   the Groups half of REQ-8's search
  chat.model.ts                   // + ChatGroupVisibility, ChatJoinRequestDto,
                                   //   ChatDiscoverableGroupDto, ChatAddParticipantsResultDto
                                   //   types (see State and data); ConversationDetail gains
                                   //   visibility/archivedAt/adminUserIds (additive, matches
                                   //   the backend's extended ChatConversationDetailDto)

features/chat/
  chat-shell.component.ts                     // NEW — route: /chat, sidebar +
                                               //   section dispatch + search field
  chat-sidebar.component.ts                   // NEW — 4 section tabs + search input
  chat-directory.component.ts                 // NEW — People+Groups filtered list
                                               //   (REQ-8..11), replaces the old
                                               //   conversation-list.component.ts's
                                               //   "own conversations only" scope
  conversation-list-item.component.ts         // unchanged, reused inside chat-directory
  group-visibility-badge.component.ts         // NEW
  create-group-dialog.component.ts            // NEW — REQ-12/13/18
  group-admin-panel.component.ts              // NEW — REQ-22/28: pending join
                                               //   requests + visibility change +
                                               //   promote-to-admin, rendered inside
                                               //   the group's own view, gated on
                                               //   ChatGroupService's capability flag
  conversation-detail.component.ts            // unchanged (message-thread reuse),
                                               //   gains group-admin-panel.component.ts
                                               //   and remove/leave/delete actions
                                               //   as conditional children (REQ-14..17,
                                               //   REQ-31/32), all gated the same way
  participant-picker.component.ts             // unchanged — reused for post-creation
                                               //   "add participant" only

features/support/                             // unchanged internals; rendered as a
                                               //   chat-shell section, not its own route
  support-page.component.ts

features/conversations/                       // unchanged internals; rendered as a
                                               //   chat-shell section, not its own route
  conversations-page.component.ts

shared/chat/
  message-thread.component.ts                 // unchanged, reused as-is
  message-composer.component.ts               // unchanged
```

`ChatPageComponent`, `new-conversation-dialog.component.ts`, and
`conversation-list.component.ts` are retired — their remaining
responsibilities are absorbed by `ChatShellComponent`/
`chat-directory.component.ts`/`create-group-dialog.component.ts` above;
`participant-picker.component.ts` survives as noted.

Routes added/changed in `app.routes.ts`:

| Path | Component | Guard |
|---|---|---|
| `/chat` | `ChatShellComponent` | none (see rationale above) |
| `/chat/:conversationId` | `ChatShellComponent` (reads `:conversationId`, same pattern `SupportPageComponent` already uses for `:channelId` rather than a nested outlet) | none |
| `/chat/support/:channelId` | `ChatShellComponent` | none |
| `/chat/articles/:conversationId` | `ChatShellComponent` | none |
| `/support` | — | `redirectTo: '/chat', queryParams: { section: 'support' }` |
| `/support/:channelId` | — | `redirectTo: '/chat/support/:channelId'` |
| `/conversations` | — | `redirectTo: '/chat', queryParams: { section: 'articles' }` |

`nav-menu.component.ts`'s two existing entries (`routerLink: '/chat'`,
`routerLink: '/conversations'`) collapse into one ("Conversas",
`routerLink: '/chat'`), per REQ-1. `welcome-page.component.ts`'s
`routerLink="/conversations"` CTA updates to
`[routerLink]="['/chat']" [queryParams]="{ section: 'articles' }"`.

`ChatShellComponent`'s internal dispatch (mirrors `SupportPageComponent`'s
existing "check the actual permission/session shape inside the
component" pattern, not a route guard):
1. Reads `section` from `ActivatedRoute.queryParamMap`, defaulting to
   `'people'`.
2. For `section === 'articles'`: checks `ActiveTenantService.activeTenant()`
   — if absent, renders the existing "no active tenant" empty state
   (reused from wherever `ConversationsPageComponent`/`dashboard`
   already shows it) instead of `ConversationsPageComponent`, since that
   component's own guard-free operation today implicitly assumed
   `tenantSelectionGuard` had already run at the route level; now that
   the guard is gone from this shared shell, the shell must perform that
   same check itself, once, before mounting the RAG section. This is a
   genuinely new obligation introduced by dropping the per-route guard
   — flagged explicitly as the one non-trivial migration risk in this
   PLAN (see Testing strategy).
3. For every other section: no tenant/permission gate at the shell
   level — `SupportPageComponent` already does its own three-way
   permission dispatch internally, and People/Groups have no gate
   beyond the existing eligible-participants/discoverable-groups
   backend scoping.

## Consumed API contracts

### Unchanged, reused as-is (from `internal-team-chat`/`conversations`
PLANs — not re-derived here)

Peer chat (`GET/POST /api/chat/conversations`, `.../messages`,
`/api/chat/eligible-participants`), support
(`/api/tenants/{tenantId}/support/...`), and RAG conversations
(`/api/tenants/{tenantId}/conversations/...`) — exact shapes as already
documented in `internal-team-chat/PLAN.md` and `conversations/PLAN.md`.
This PLAN adds no changes to any of them.

### New — group discovery (REQ-8's Groups candidate set)

> **Final, reconciled against `chat-group-membership-management`'s
> backend PLAN.md (2026-08-08).** Corrects this PLAN's earlier
> provisional path/envelope/DTO guess per that PLAN's "Frontend contract
> reconciliation" table.

| Method | Path | Request | Response | Status |
|---|---|---|---|---|
| GET | `/api/chat/discoverable-groups?page={n}&size={n}` | — | `PageResponseDto<ChatDiscoverableGroupDto>` | 200 |

`ChatDiscoverableGroupDto`: `{ id, title, tenantId, visibility:
'PRIVATE'|'REQUEST_TO_JOIN'|'PUBLIC', participantCount }` — matches the
backend DTO exactly. Two corrections from this PLAN's original
(provisional) assumption:
- **No `tenantId` query param** — eligibility/tenant-anchoring is
  derived server-side from the caller, never client-selected, so
  `ChatDirectoryService.fetchDiscoverableGroups()` takes no tenant
  argument.
- **No `viewerJoinState` field, and no `PRIVATE` rows ever appear** —
  the endpoint is structurally incapable of returning a group the
  viewer already belongs to or one they're not eligible to see, per
  REQ-19/REQ-28's server-side exclusion (the backend PLAN's explicit
  design note: "already-joined groups are excluded server-side... not
  flagged"). `ChatDirectoryService`/`chat-directory.component.ts` must
  **not** contain any client-side `viewerJoinState === 'MEMBER'` or
  visibility filtering — that was this PLAN's own now-removed
  provisional field, not a real backend concept; re-deriving it client-
  side would be exactly the "backend invariant re-derived in the UI"
  anti-pattern this SPEC's Groups section explicitly avoids elsewhere
  (see the look-in-exclusion precedent from `internal-team-chat`).
- The `PageResponseDto<T>` envelope (`{ content: T[], page, size,
  totalElements, totalPages }`, same shape `tenant-pagination-search`
  already established) is unwrapped by `ChatDirectoryService` — the
  component only ever sees `.content`. Given the SPEC's own NFR ("an
  already-fetched, already reasonably-bounded candidate list... if a
  tenant's ... discoverable-group count ever grows large enough...
  that's the trigger for server-side search"), `ChatDirectoryService`
  fetches a single large page (`size=200`, matching this app's existing
  "fetch-once, filter client-side" NFR posture) rather than wiring up
  incremental pagination UI now — REQ-8's search still runs entirely
  client-side over that fetched page; true server-side paginated
  discovery is deferred to the same future trigger the SPEC's NFR
  already names, not decided here.

### New — group governance (REQ-13/14/15/16/17/20/21/22/23/24/28/29/30/31/32)

> **Final, reconciled against `chat-group-membership-management`'s
> backend PLAN.md (2026-08-08)** — paths, verbs, request/response
> shapes, and status codes below are copied verbatim from that PLAN's
> "API contracts" section (and its own "Frontend contract
> reconciliation" diff against this PLAN's earlier draft), not
> guessed.

| Method | Path | Request | Response | Status |
|---|---|---|---|---|
| POST | `/api/chat/conversations` | `{ kind: 'GROUP', tenantId, title, visibility: 'PRIVATE'\|'REQUEST_TO_JOIN'\|'PUBLIC', participantUserIds: [] }` | `ChatConversationDetailDto` (unchanged from this PLAN's original assumption — group creation itself is not part of the backend PLAN's diff table, still served by the existing `internal-team-chat` create endpoint, now carrying `visibility` in the request per REQ-13/18) | 201 |
| POST | `/api/chat/conversations/{id}/participants` | `{ userIds: number[] }` | `ChatAddParticipantsResultDto { conversation: ChatConversationDetailDto, rejected: { userId, reason: 'ALREADY_PARTICIPANT'\|'INELIGIBLE' }[] }` | 200 (even with partial rejections), 400 (`CHAT_INELIGIBLE_PARTICIPANT`/`CHAT_PARTICIPANT_ALREADY_MEMBER`, only when **every** submitted id was rejected), 403 (`CHAT_ACCESS_DENIED`, not admin), 404, 409 (wrong kind/archived/deleted) |
| DELETE | `/api/chat/conversations/{id}/participants/{userId}` | — | `ChatConversationDetailDto` | 200 (not 204 — body carries the updated participant list), 403 (not admin), 404, 409 (would empty the group) |
| POST | `/api/chat/conversations/{id}/leave` | — (no body) | *(no body)* | 204 (not 200), 403 (`CHAT_ACCESS_DENIED`, not a genuine participant), 404 |
| POST | `/api/chat/conversations/{id}/admins/{userId}` | — (no body) | `ChatConversationDetailDto` | 200, 400 (`CHAT_PARTICIPANT_ALREADY_ADMIN`, no-op), 403 (caller not admin), 404 (target not a participant) |
| PUT | `/api/chat/conversations/{id}/visibility` | `{ visibility: 'PRIVATE'\|'REQUEST_TO_JOIN'\|'PUBLIC' }` | `ChatConversationDetailDto` | 200, 400 (`CHAT_VISIBILITY_UNCHANGED`, no-op), 403 (not admin), 409 (archived/deleted) |
| POST | `/api/chat/conversations/{id}/join-requests` | `{}` | `ChatJoinRequestDto` | 201, 400 (`CHAT_INELIGIBLE_PARTICIPANT`), 403 (`CHAT_PARTICIPANT_ALREADY_MEMBER`), 409 (`CHAT_JOIN_REQUEST_DUPLICATE`, or wrong-mode/archived/deleted) |
| GET | `/api/chat/conversations/{id}/join-requests?status=PENDING` | — | `ChatJoinRequestDto[]` (admin-only) | 200, 403 (not admin), 404 |
| POST | `/api/chat/conversations/{id}/join-requests/{requestId}/approve` | `{}` | `ChatJoinRequestDto` | 200, 400 (`CHAT_INELIGIBLE_PARTICIPANT` — REQ-30a, requester no longer eligible, request left `PENDING`), 403 (not admin), 409 (`CHAT_JOIN_REQUEST_ALREADY_DECIDED`) |
| POST | `/api/chat/conversations/{id}/join-requests/{requestId}/reject` | `{}` | `ChatJoinRequestDto` (status `REJECTED`) | 200, 403 (not admin), 409 (already decided) |
| POST | `/api/chat/conversations/{id}/join` | `{}` | `ChatConversationDetailDto` | 200, 400 (`CHAT_INELIGIBLE_PARTICIPANT`), 403 (`CHAT_PARTICIPANT_ALREADY_MEMBER`), 409 (not `PUBLIC`, or deleted) |
| DELETE | `/api/chat/conversations/{id}` | — | *(no body)* | 204, 403 (`CHAT_ACCESS_DENIED`), 404, 409 (`CHAT_CONVERSATION_ALREADY_DELETED`) |

Corrections from this PLAN's original (provisional) draft, each now
applied above rather than left as a diff to remember at TASKS.md time:

- **No `GET .../capabilities` endpoint exists** — it was this PLAN's
  own invention. `isAdmin` is derived client-side from
  `ChatConversationDetailDto.adminUserIds.includes(currentUserId)`,
  `isParticipant` from the existing `participantUserIds` check
  (`deriveViewerRelation`'s existing logic) — both already present on
  every detail fetch `ChatService.openConversation()` already performs,
  so no extra round-trip is needed to gate REQ-14/16/22/28/31's UI.
- **Promote path is `.../admins/{userId}`**, not
  `.../participants/{userId}/promote`.
- **Visibility change is `PUT`**, not `PATCH` (full-replace-of-a-single-
  field convention, matching the backend's stated reasoning).
- **Approve/reject return `ChatJoinRequestDto`**, not a conversation
  shape — approving does **not** echo the updated participant list;
  `ChatGroupService.approveJoinRequest()` must separately re-fetch (or
  optimistically append to) the conversation's participant list if the
  UI needs it reflected immediately, since the response no longer
  carries it. Approve also gets a new 400 `CHAT_INELIGIBLE_PARTICIPANT`
  outcome (REQ-30a) that the frontend must render distinctly from the
  existing 403/409 cases (see error-handling note below) — the request
  stays `PENDING` server-side, so `ChatGroupService` must **not** remove
  it from `_pendingJoinRequests` on this specific 400, unlike every
  other action's uniform "success removes it from the pending list"
  rule.
- **Remove-participant returns `200` with a `ChatConversationDetailDto`
  body**, not `204` with an empty body — `ChatGroupService
  .removeParticipant()` patches local state directly from the returned
  detail rather than manually computing the post-removal participant
  list.
- **Leave returns `204`**, not `200` with a body — no behavior change
  to `ChatGroupService.leave()`'s "drop the conversation from
  `ChatService`'s own list" logic, since that never depended on a
  response body.
- **Add-participants is `POST /api/chat/conversations/{id}/participants`
  with a batch `userIds: number[]` body**, returning the partial-success
  `ChatAddParticipantsResultDto` shape above — this endpoint was
  entirely absent from this PLAN's original contract table (a genuine
  gap, not a mismatch). `participant-picker.component.ts`'s reuse for
  "add participant to an existing group" now submits the full selected
  set in one batch call, not one call per selected participant, and
  `ChatGroupService.addParticipants()` surfaces `result.rejected` inline
  next to whichever candidates were rejected (distinguishing
  `ALREADY_PARTICIPANT` from `INELIGIBLE` in the message) rather than
  treating the call as all-or-nothing.
- **Join (`PUBLIC`) status codes are reassigned**: `400` = ineligible
  (`CHAT_INELIGIBLE_PARTICIPANT`), `403` = already a participant
  (`CHAT_PARTICIPANT_ALREADY_MEMBER`), `409` = wrong visibility mode or
  deleted — three distinct cases, not the original two-case
  403-covers-two-conditions guess. `ChatGroupService.join()`'s error
  handler branches on all three explicitly.

`ChatJoinRequestDto` (frontend-side alias: `JoinRequest`): `{ id,
conversationId, requesterUserId, requesterNickname, status, decidedAt
}` — the earlier provisional `JoinRequest` interface's `requestedAt`
field is renamed/replaced; the backend never sends a submission
timestamp, only `status` (`PENDING`/`APPROVED`/`REJECTED`) and
`decidedAt` (`null` while pending).

## State and data

- **`ChatDirectoryService`** (signals, new): `_discoverableGroups:
  Signal<ChatDiscoverableGroupDto[]>`, `fetchDiscoverableGroups()` (no
  tenant argument — see the reconciled contract above; calls
  `GET /api/chat/discoverable-groups?page=0&size=200` and stores
  `response.content`). Kept separate from `ChatService` because it's a
  distinct backend concept (discovery, not "my conversations") even
  though both back the same UI list — same reasoning
  `PermissionsService`/`GlobalPermissionsService` already establish for
  "structurally similar, conceptually distinct" state.
- **`chat-directory.component.ts`** owns the search state locally:
  `searchQuery = signal('')`, a `computed()` combining
  `ChatService.conversations()` (kind `PEER_DIRECT`/`PEER_GROUP`, own
  conversations), `ChatService.eligibleParticipants()` (People not yet
  messaged), and `ChatDirectoryService.discoverableGroups()`
  (non-participant groups, already excludes `PRIVATE` and
  already-joined groups server-side — no client-side re-filtering, per
  the reconciled contract) into one filtered, deduplicated
  `{ kind: 'person'|'group', ...}[]` list — filter predicate is
  `displayName.toLowerCase().includes(searchQuery().toLowerCase())`,
  applied only to person/group rows, never to Support/"Base de
  artigos" (REQ-9, structurally — those aren't part of this computed
  list at all).
- **`ChatGroupService`** (signals, new): `_pendingJoinRequests:
  Signal<Map<number, ChatJoinRequestDto[]>>` (admin-only, populated by
  `GET .../join-requests?status=PENDING`), plus one action method per
  governance endpoint above — `join(id)`, `requestToJoin(id)`,
  `addParticipants(id, userIds)`, `approveJoinRequest(id, requestId)`,
  `rejectJoinRequest(id, requestId)`, `promote(id, userId)`,
  `removeParticipant(id, userId)`, `leave(id)`, `changeVisibility(id,
  visibility)`, `deleteGroup(id)`. Every action that returns a
  `ChatConversationDetailDto` (`addParticipants` via its
  `result.conversation`, `removeParticipant`, `promote`,
  `changeVisibility`, `join`) patches `ChatService`'s own `_details` map
  in place with that response on success — `ChatGroupService` never
  keeps a second, parallel copy of conversation detail state, it only
  writes into `ChatService`'s existing map (a small cross-service call,
  same pattern `SupportService` already uses when it patches a
  `TicketSummary` it doesn't itself own the source-of-truth fetch for).
  `deleteGroup`/`leave` additionally drop the conversation from
  `ChatService`'s `_conversations` signal (no body to read for `leave`,
  per its `204`). `approveJoinRequest`'s success does **not** carry an
  updated participant list (the reconciled contract's `ChatJoinRequestDto`
  response has none) — it removes the request from
  `_pendingJoinRequests` and separately calls
  `ChatService.openConversation(id)` again to refresh the participant
  list, rather than trying to synthesize the new membership client-side.
  `approveJoinRequest`'s **new REQ-30a 400 case is handled distinctly**:
  it does *not* remove the request from `_pendingJoinRequests` (the
  request is still `PENDING` server-side) and surfaces a specific
  "este pedido não pode mais ser aprovado" inline message, never the
  generic REQ-25/REQ-27 failure message used for 403/409/network cases.
  `addParticipants`'s success still surfaces `result.rejected` inline
  (per-id `ALREADY_PARTICIPANT`/`INELIGIBLE` reasons) even though the
  call is a 200 — a partial rejection is not treated as this method's
  "error path" for REQ-25/REQ-27 purposes, since the batch call itself
  succeeded. On any other error, no signal mutation happens (REQ-25/
  REQ-27's "leave state exactly as it was"), and the error is returned
  for the calling component to render inline.
- `isAdmin`/`isParticipant` are **not** service-held signals — they're
  a `computed()` in whichever component needs them
  (`group-admin-panel.component.ts`, `conversation-detail.component.ts`),
  derived from `ChatService.details().get(id)` (`adminUserIds.includes
  (currentUserId)` / `participantUserIds.includes(currentUserId)`) plus
  `AuthService`'s current user id — no separate capability fetch, per
  the reconciled contract removing the invented `GET .../capabilities`
  endpoint.
- No reactive forms for `create-group-dialog.component.ts` (name input +
  a 3-option visibility radio group is simpler as template-driven
  signal-bound state, consistent with this feature area's existing
  "no `ReactiveFormsModule` for simple single-screen forms" precedent
  from `internal-team-chat`'s own composer/picker).

## Dependencies

None. No new npm package — the modal reuses the existing native
`<dialog>` precedent (`deletion-confirmation-token`), icons/styling
reuse `@lucide/angular` and existing Tailwind utility classes, and
search/filtering is a plain `computed()` signal, no new library.

## Testing strategy (Vitest)

- `chat-shell.component.spec.ts`: section dispatch via `queryParamMap`
  (defaults to `people`; each of the four `section` values renders the
  right child); the "Base de artigos" section renders the no-tenant
  empty state when `ActiveTenantService.activeTenant()` is `null` and
  `ConversationsPageComponent` otherwise — this is the one behavior
  change from today's route-guard-based gating and needs its own
  explicit regression test (see rationale above).
- `chat-directory.component.spec.ts`: REQ-8 filters People+Groups by
  name, case-insensitively, live per keystroke; REQ-9 confirms Support/
  "Base de artigos" are never part of the filtered/candidate list at
  all (asserted by construction — they're not rendered by this
  component); REQ-10's distinct "no results for '<query>'" message
  vs. the pre-existing "no conversations yet" empty state; REQ-11
  clearing search restores the full list; a fixture including a
  `PRIVATE` discoverable-group candidate asserts the component never
  needs to filter it out client-side (it's simply never in the fetched
  list — same "don't re-derive a backend invariant" test shape
  `internal-team-chat`'s look-in exclusion test already established).
- `create-group-dialog.component.spec.ts`: submit requires both a
  non-empty name and one of the three visibility options selected
  (REQ-18); successful submit calls `POST /api/chat/conversations` with
  `visibility` in the body and navigates to the new group.
- `chat-group.service.spec.ts`: `HttpTestingController`-based, one test
  per action method asserting the exact reconciled method/path/status
  handling above — including `addParticipants`'s partial-`rejected[]`
  success case (200, not an error path), `approveJoinRequest`'s 400
  `CHAT_INELIGIBLE_PARTICIPANT` case (request stays in
  `_pendingJoinRequests`, distinct message, not the generic failure
  path), `removeParticipant`'s `200`-with-body patch, and `leave`'s
  `204`-no-body drop-from-list — directly backs REQ-25/REQ-27/REQ-30a's
  acceptance criteria and guards against silently reverting to this
  PLAN's earlier (wrong) status-code assumptions.
- `group-admin-panel.component.spec.ts`: renders approve/reject/
  promote/visibility-change/delete actions only when the
  `adminUserIds.includes(currentUserId)` computed is `true`; a
  non-admin fixture (current user id absent from a fixture
  `ChatConversationDetailDto.adminUserIds`) asserts none of those
  controls exist in the DOM (not just hidden) — same accessibility-tree
  removal requirement REQ-19/NFR already require for search-filtered
  rows, applied here to admin-only actions.
- `conversation-detail.component.spec.ts` (extended): "sair do grupo"
  shown for a genuine participant, not shown for a `LOOKING_IN` viewer
  (REQ-16's explicit "never shown to an admin present only via
  tenant-level look-in" — reuses the existing `viewerRelation`
  derivation, now double-checked against the `participantUserIds`-
  derived `isParticipant` computed for this specific action).
- Route-migration regression: a test asserting `/support` and
  `/conversations` navigations redirect to `/chat` with the expected
  `section` query param, so this refactor can't silently 404 an old
  bookmark.
- Existing suites potentially touching this area (`internal-team-chat`'s
  405-test count per `PROJECT_STATUS.md`) that must still pass unmodified
  in their own service/model specs (`chat.service.spec.ts`,
  `support.service.spec.ts`, `conversation.service.spec.ts`,
  `message-thread.component.spec.ts`) since none of their underlying
  services/contracts change here — only retired: `chat-page.component
  .spec.ts`, `new-conversation-dialog.component.spec.ts`,
  `conversation-list.component.spec.ts` (superseded by the new shell/
  directory/dialog specs above, not left dangling against deleted
  components).
- Accessibility: `chat-sidebar.component.spec.ts` asserts each of the 4
  section tabs and the search field are keyboard-reachable with
  `aria-label`s, matching this feature area's existing convention.

## Reconciliation status

Resolved (2026-08-08) against
`knowly-api/specify/features/chat-group-membership-management/PLAN.md`
(closed as source of truth) by the software architect, using that
PLAN's own "Frontend contract reconciliation" diff table as the
authoritative correction list: discoverable-groups' path
(`/api/chat/discoverable-groups`, not `/api/chat/groups/discoverable`)
and pagination envelope (`PageResponseDto`, no `tenantId` param, no
`viewerJoinState`), the promote-to-admin path (`.../admins/{userId}`),
visibility's HTTP method (`PUT`, not `PATCH`), approve/reject's response
shape (`ChatJoinRequestDto`, not a conversation object) plus the new
REQ-30a 400 case, remove-participant's status/body (`200` + detail, not
`204` + empty), leave's status (`204`, not `200`), the previously
entirely-missing add-participants endpoint and its partial-success
`rejected[]` shape, and the join status-code-to-condition mapping
(`400`/`403`/`409` for three distinct conditions, not two) are all now
final per the "Consumed API contracts" section above — no more "to
confirm" language remains anywhere in this document. This PLAN is ready
for TASKS.md generation in full, including the group-governance
portion, once the mandatory AppSec review of both PLANs (this one and
the backend's) referenced in this feature's coordination flow has run.

## Amendment (3) reconciliation (2026-08-09)

> Covers SPEC.md's third amendment (2026-08-09, "Approved for PLAN — zero
> open blockers"): REQ-1/REQ-2/REQ-2c/REQ-2d (unified single-list column 1
> + a third full-directory column, replacing the shipped 2-column
> "Already talked to"/"Haven't talked yet"/Groups partition), and
> REQ-33–REQ-37 (clear-conversation semantics). Written against the
> already-shipped 2-column code (`chat-directory.component.ts`,
> `chat-sidebar.component.ts`, `chat-directory-rows.service.ts`, all read
> in full as part of this reconciliation) rather than against this PLAN's
> stale "2-column" architectural-decisions/components sections above,
> which this section supersedes for column layout and list structure —
> those sections' non-layout content (API contracts, group-governance
> service split, non-optimistic error handling, dialog/badge components)
> is unaffected and still authoritative.

### New third-column component

- **New `chat-full-directory.component.ts`** (`features/chat/`), same
  width as column 1, rendered by `ChatShellComponent` as the third
  persistent pane. Why a new component rather than extending
  `ChatDirectoryComponent`: column 1 and column 3 render **disjoint** row
  sets with different empty-state copy, different a11y labels, and (per
  REQ-2d) a materially different sort — forcing one component to render
  both would mean a `column: 1 | 3` discriminator prop threaded through
  every template branch, for no code reuse beyond the row-button markup
  already isolated in `AvatarComponent`/`GroupVisibilityBadgeComponent`.
  Both components consume the same `ChatDirectoryRowsService` (below),
  so there is no duplicated data-fetching or click-handling logic, only
  duplicated presentation, which is the correct axis to share on here
  (mirrors this PLAN's own existing "one service, multiple thin
  presentational consumers" pattern already used for
  `ChatGroupService`/`group-admin-panel.component.ts` +
  `conversation-detail.component.ts`).
- `ChatFullDirectoryComponent` has its own `searchQuery` signal and its
  own `data-testid`/`aria-label` set (REQ-2d's "own independent search
  field... never column 1's"), filtering `ChatDirectoryRowsService
  .discoveryRows()` (see below) by `displayName`, same
  `filterByQuery` predicate `ChatDirectoryComponent` already uses
  (extracted to a shared `chat-directory-search.util.ts` so both
  components import one implementation instead of two copies — a small,
  in-scope refactor of the free function `chat-directory.component.ts`
  already defines at file scope today).

### Unified single list (column 1)

- **`ChatDirectoryRowsService` gains one new computed, `conversationRows:
  DirectoryRow[]`**, replacing `talkedPeople`/`groupRows`/`supportRow`/
  `articleRows` as four separately-rendered sections with one ordering:
  `[supportRow, ...everythingElseSortedByRecencyProxy]`. `talkedPeople`,
  `groupRows`, `articleRows` are **kept** as computeds (not deleted) since
  they're still the natural place to compute each kind's own recency
  proxy before merging — `conversationRows` becomes a thin `computed()`
  that concatenates and sorts them, Support unconditionally first. Why
  keep the per-kind computeds instead of building one flat pipeline: it
  keeps each kind's own known gap/proxy documented at the computed that
  owns it (id-descending for people/groups, per the existing doc
  comments; article rows get the same id-descending proxy, not currently
  sorted at all today — a small extension, not a new concept) rather than
  smearing three different fallback rules into one function body.
- **`notTalkedPeople` is renamed to `discoveryRows`** (still a computed
  on `ChatDirectoryRowsService`) and **extended to include discoverable
  groups** (previously part of `personGroupRows`/`groupRows`'s combined
  list) — column 3's full disjoint-complement set per REQ-2d: eligible
  people with no 1:1 conversation, plus every discoverable group the
  viewer isn't a participant of. This is the same underlying data
  `ChatDirectoryService.discoverableGroups()`/
  `ChatService.eligibleParticipants()` already expose — no new fetch,
  only a new grouping of already-fetched signals.
- **`ChatDirectoryComponent` (column 1) is rewritten** to render one
  `<ul>` over `rowsService.conversationRows()`, one search field
  (`unifiedQuery` signal) filtering everything **except** the pinned
  Support row (REQ-2's "exempt from the unified search filter,
  regardless of whether the typed text matches"), matching REQ-9's
  continued Support exemption. The existing 3-section markup (talked/
  not-talked/groups) is deleted, not kept behind a flag — SPEC.md's
  amendment fully supersedes that partition, per its own "Which REQ
  numbers this amendment supersedes" list; there is no dual-mode
  requirement to preserve.
- **`ChatSidebarComponent` is unaffected by this amendment** — its 3
  action buttons + tenant-gating logic (documented in its own file
  comment) are orthogonal to the list-partitioning change; no rework
  needed there.

### Cross-surface recency sort for column 3 — feasibility decision

**Decision: this needs new backend support and is a hard prerequisite
for the *real* REQ-2d sort; it does not belong in this feature's own
PLAN, and TASKS.md schedules an interim fallback so column 3 itself
isn't blocked on it.**

Verified in `knowly-api` (read `ChatConversationService`,
`ChatController`, `ChatMessageRepository`, `ConversationRepository`,
`chat-directory-rows.service.ts`'s own already-documented gap): no
endpoint, DTO field, or repository query anywhere in the backend
computes "most recent interaction between viewer and entity X, across
every group either has posted in and every 1:1 ever exchanged, surviving
a hard delete of the 1:1 record." The closest existing data —
`ChatConversationSummaryDto`/`ConversationDetail` — carries no
`lastMessageAt` at all (a gap this PLAN's own 2026-08-09 emergent
decisions already flagged and worked around with an id-descending proxy
for column 1's own, much narrower, "my own conversations" sort).
REQ-2d's signal is categorically different and harder: it is
**per-participant, cross-conversation, and required to survive hard
deletion of the very row that would normally carry the timestamp**
(REQ-33's premise is that the conversation record is gone).

A client-side workaround was considered and rejected, per REQ-2d's own
implementation-risk note: computing it client-side would require
fetching full message history for every shared group per participant
(does not scale — this NFR section's own "already-fetched, reasonably-
bounded candidate list" posture assumes list-level data, not per-
message history fan-out) and **cannot work at all** for the
since-hard-deleted-1:1 case, since by definition no client-reachable
record of that conversation survives its deletion — only a backend
system that either (a) never physically purges the row (soft-delete
with a `deletedAt` the frontend never sees, which the group-deletion
precedent above already uses) or (b) records a separate durable
"last-interaction" fact at deletion time, can answer this query at all.

**Decision, concretely:**
1. This frontend PLAN does **not** invent that backend contract. Per
   `constitution.md`'s Tier 3 rule and this SPEC's own header ("This
   frontend SPEC does not yet cover a 1:1 conversation hard-delete
   endpoint... nor the equivalent for a RAG conversation... both are new,
   not-yet-specified backend dependencies"), the natural extension is a
   **new backend SPEC/PLAN amendment** (most likely to
   `chat-group-membership-management` or a new small
   `chat-conversation-lifecycle` backend feature, sibling to the
   hard-delete endpoints REQ-33/REQ-36 also need — see below, since both
   gaps point at the same missing capability: durable last-interaction
   data that survives a 1:1's hard delete) exposing something like `GET
   /api/chat/interaction-recency?entityIds=...` returning `{ entityId,
   lastInteractionAt }[]`, computed server-side from message history
   (including soft-deleted/purged 1:1s, however that backend amendment
   chooses to retain the fact of the interaction).
1a. **[AppSec-added authorization requirement, 2026-08-09]** Whoever
   specifies that future `GET /api/chat/interaction-recency` endpoint
   must scope it so it can never be used as a cross-entity discovery
   oracle: it must compute/return `lastInteractionAt` **only** for
   `entityIds` that share a genuine interaction with the calling
   viewer (a 1:1, current or since-deleted, or a group conversation
   either has posted in) — never a blind "give me the last-interaction
   timestamp for any arbitrary user/group id" lookup. An `entityId` the
   viewer has no interaction history with must come back exactly the
   same as an `entityId` that simply doesn't exist (both silently
   absent from the response, not a distinguishable "found but zero
   interactions" vs. "not found" signal) — this endpoint must not
   become a side channel for probing which users/groups exist in the
   system, or whether two other parties have ever interacted, that the
   viewer isn't already entitled to see via `eligibleParticipants()`/
   `discoverableGroups()`. It must also apply the same tenant-filter
   discipline as every other tenant-owned query (no manual/parallel
   scoping mechanism) so it can never surface an interaction that
   happened in a tenant the current session has no access to.
2. **Until that backend work exists, column 3 ships with an interim,
   documented fallback sort**: alphabetical by display name (the same
   tiebreak REQ-2d already specifies for "no computed timestamp" rows) —
   i.e. every column-3 row is treated as "no known interaction" for now.
   This is not a silent shortcut: `ChatDirectoryRowsService
   .discoveryRows()`'s doc comment must say explicitly that this is
   REQ-2d's fallback branch, pending the backend amendment above, so a
   future reader doesn't mistake alphabetical-only for the finished
   ranking. This satisfies the acceptance criterion's *structural* half
   (column 3 renders the right disjoint row set, sorted, searchable) and
   ships a usable feature now, without a false claim of building the
   real cross-surface signal client-side (which was rejected above as
   infeasible). The real recency sort becomes a fast-follow task, gated
   on the backend amendment, tracked as its own blocked task in TASKS.md
   rather than silently left undone.

### 1:1/RAG hard-delete — feasibility decision (REQ-33/34/36/37)

**Decision: confirmed hard prerequisite, backend PLAN/SPEC amendment
required before any REQ-33/REQ-36 frontend task starts. TASKS.md marks
that dependency explicitly and schedules no "clear conversation" UI work
for 1:1/RAG ahead of it.**

Verified in `knowly-api`:
- `ChatController`/`ChatConversationService.deleteConversation` **does**
  expose `DELETE /api/chat/conversations/{id}` — but it is hard-scoped
  to `ChatConversationKind.PEER_GROUP` only
  (`if (conversation.getKind() != ChatConversationKind.PEER_GROUP) throw
  ChatGroupStateConflictException(Detail.NOT_PEER_GROUP)`) and is a
  **soft** delete (`setDeletedAt`, cascading soft-delete to participants/
  messages) gated on the group-admin/tenant-oversight authorization
  model — this is REQ-31/32's existing "excluir grupo" endpoint, already
  fully covered by section 7/8's already-planned tasks. It is not usable
  for a `PEER_DIRECT` conversation and would need to reject on kind even
  if called.
- No endpoint anywhere deletes a `PEER_DIRECT` chat conversation.
- `ConversationController`
  (`/api/tenants/{tenantId}/conversations`, the RAG feature) exposes only
  `POST` (create), `GET` (list/get), and `POST .../messages` (SSE
  send) — no `DELETE` of any kind.

This confirms SPEC.md's own "Out of scope" call-out verbatim: **REQ-33
and REQ-36 both require a new backend endpoint that does not exist
today**, and REQ-34 needs no new work at all (it's the existing leave-
group flow, already covered by section 8's tasks — `sair do grupo` is
categorically not a delete). Concretely:
- REQ-33 needs something like `DELETE /api/chat/conversations/{id}`
  **extended** to also accept `PEER_DIRECT` (or a new, narrower endpoint
  if reusing the group one is judged unsafe given its very different
  authorization model — "only a genuine participant," not "group admin
  or tenant oversight") — this is the backend PLAN's decision to make,
  not this frontend PLAN's.
- REQ-36 needs an analogous `DELETE` on `ConversationController`
  (`DELETE /api/tenants/{tenantId}/conversations/{conversationId}`),
  scoped to the conversation's own owning participant.
- Both should ideally be specified together with REQ-2d's
  interaction-recency need above, since a genuine hard delete plus "the
  interaction still counts for column 3 ranking" implies the backend
  needs to retain *some* durable trace of a deleted 1:1/RAG conversation
  even after the frontend-visible record is gone — the same backend
  amendment can plausibly cover both gaps in one PLAN.
- REQ-35 (Support: no clear action, ever) needs **no backend work** —
  it's an explicit non-feature. The frontend task here is only "assert
  no clear/limpar control renders for the Support row," never a call to
  an endpoint.
- **[AppSec-added authorization requirements, 2026-08-09]** Whoever
  writes the backend PLAN/SPEC amendment for the REQ-33/REQ-36
  hard-delete endpoints must satisfy all of the following before either
  ships, given this is new, irreversible, destructive functionality:
  1. **Participant check must be a fresh, server-side DB lookup keyed
     off the authenticated actor and the path's `conversationId`** —
     `actor.id ∈ conversation.participantUserIds` (or the RAG
     equivalent: `actor.id == conversation.ownerId`), re-derived from
     the database on every call, never trusted from a client-supplied
     flag or a stale/cached participant list. This is the same
     discipline `ChatEligibilityService`'s own doc comment already
     states ("never trusts a client-supplied 'I'm eligible' flag") —
     the new endpoint must follow it, not reinvent a weaker check.
  2. **A `conversationId` belonging to someone else's 1:1/RAG
     conversation, or to a group/Support conversation, must be
     rejected identically** (same status code/body) **regardless of
     whether the id exists at all** — a non-participant must not be
     able to distinguish "that conversation doesn't exist" from "that
     conversation exists but isn't yours" from "that conversation
     exists but is a group/Support, not a 1:1/RAG" purely from the
     response, both to prevent conversation-id enumeration and to keep
     the reasonable-time-only rejection here (this isn't a timing-
     sensitive secret comparison, but the response *shape* must not
     leak existence).
  3. **This is a genuine IDOR surface by construction** (a
     `conversationId` is presumably a sequential/guessable numeric id,
     same shape as every other `/api/chat/conversations/{id}` route) —
     the authorization check above is not optional hardening, it is
     the entire security boundary for an endpoint whose entire purpose
     is permanent, irreversible data destruction. No caller-supplied
     "I am a participant" assertion of any kind may substitute for the
     DB-derived check.
  4. **Kind must be validated server-side**, mirroring the existing
     `deleteConversation`'s own `kind != PEER_GROUP` guard: the new
     1:1 endpoint must reject a `PEER_GROUP`/`SUPPORT` conversation id
     even if the caller happens to be a genuine participant of one
     (e.g. a group member), since REQ-34/REQ-35 deliberately withhold
     this action from those kinds — "participant of *a* conversation"
     is not sufficient, it must be "participant of *this specific*
     1:1/RAG conversation."
  5. **Hard delete means hard delete** — per REQ-33/REQ-36's own
     wording ("permanently deletes... full message history"), this is
     a deliberate exception to this repo's usual soft-delete-by-default
     posture (`deleteConversation`'s existing group-delete endpoint
     soft-deletes via `setDeletedAt`). If the backend PLAN wants to
     retain a durable trace for REQ-2d's interaction-recency signal
     (see above), that must be a separate, narrower retained fact (e.g.
     a last-interaction timestamp/audit row), never the full message
     content/history — reviving message content post-"permanent
     deletion" would contradict the product owner's own stated
     semantics and expectations set by REQ-33/REQ-36's UI copy.

This PLAN does not schedule REQ-33/REQ-36's frontend tasks (API service
methods, UI clear actions, row-removal-on-success wiring) — TASKS.md
marks them explicitly **BLOCKED — backend prerequisite**, per this
repo's existing convention for cross-repo dependencies (see
`chat-group-membership-management`'s own frontend-PLAN coordination
precedent, now on the other side of that same relationship).

### Reconciling in-progress uncommitted files against this amendment

- **`knowly-app/src/app/core/chat-directory-rows.service.ts`
  (uncommitted, new)**: mostly still correct and reusable — its
  `personGroupRows`/`ensureLoaded`/tenant-resolution-race fix/click
  handlers are unaffected by this amendment and stay as-is. **Needs
  rework**: (a) its own doc comment is stale (describes the now-
  superseded "2-column, partition replaces 3rd column" outcome — update
  to describe the unified-list + real-3rd-column outcome instead); (b)
  add `conversationRows` (Support-pinned, unified, sorted) and rename/
  extend `notTalkedPeople` → `discoveryRows` (adding discoverable groups)
  as described above; (c) `talkedPeople`/`groupRows` stay as internal
  building blocks for `conversationRows`, no longer rendered directly by
  a component.
- **`knowly-app/src/app/features/chat/chat-directory.component.ts`
  (uncommitted, modified)**: its 3-section (talked/not-talked/groups)
  template is **superseded outright** — needs a rewrite to the one-list-
  plus-pinned-Support shape described above. Its avatar/badge/active-row/
  error-row rendering per list item, and its `filterByQuery` helper, are
  reusable as-is inside the new single `<ul>`.
- **`knowly-app/src/app/features/chat/chat-sidebar.component.ts`
  (uncommitted, modified)**: **no changes needed** — its 3 action
  buttons + tenant-gating are untouched by this amendment (confirmed
  above).
- **`knowly-app/src/app/features/chat/chat-contacts-panel.component.ts`
  and its spec (uncommitted, new, per this task's own prompt) were
  searched for and do not exist in the working tree** — `git status`/
  `find` both come back empty for that path at the time of this
  reconciliation, despite being named in the task brief as already
  written. Nothing to reconcile for these two files; if they surface
  later (e.g. an uncommitted stash, or a sibling worktree), they should
  be re-evaluated against this section before being wired into
  `ChatShellComponent`, since their described purpose (a 3rd "já
  falou"/"ainda não falou" column) is the exact idea SPEC.md's Amended
  (2) already retired same-day, not this amendment's actual column 3.
- **`chat-directory.component.spec.ts`/`chat-sidebar.component.spec.ts`
  (uncommitted, modified)**: the directory spec's talked/not-talked/
  groups-section assertions need rewriting alongside the component;
  the sidebar spec needs no changes (component is unchanged).

## Emergent decisions (implementation, 2026-08-09)

Deviations discovered while executing TASKS.md, following
`internal-team-chat/PLAN.md`'s own precedent for documenting these
rather than silently drifting from the plan:

- **`create-group-dialog.component.ts` uses a `signal` + `(input)`
  binding for the group-name field, not `[(ngModel)]`/`FormsModule`.**
  During TDAD a `[(ngModel)]`-bound plain field did not reliably
  propagate into a `computed()` signal's dependency in this app's
  zoneless setup — a simulated keystroke updated the DOM but
  `submitDisabled()` kept evaluating against a stale value. Switching
  the field to a `signal` written via `(input)` (the same pattern
  `conversations-page.component.ts`'s message input already uses)
  fixed it outright and is now the safer default for any new form
  field in this codebase's zoneless components — `ngModel` on a plain
  (non-signal) property should be treated as a known trap here, not
  reached for by default the way `new-conversation-dialog.component.ts`
  (now retired) and `support-page.component.ts` still do.
- **`ConversationListItemComponent` is retired, not reused.** The PLAN
  called for `chat-directory.component.ts` to reuse it unchanged for
  group rows; in practice, `ChatDirectoryComponent`'s combined
  People+Groups row model (one discriminated-union `DirectoryRow[]`
  list driving click-to-open-or-create for a person, open/join/
  request-to-join for a group, and per-row inline error state) didn't
  map cleanly onto that component's narrower "one existing conversation,
  one row, `routerLink` only" shape without either duplicating its
  template inline anyway or forcing awkward inputs onto it for cases
  (join/request-to-join) it was never designed for. Rather than ship it
  unused, it was deleted alongside the other retired components.
- **`GroupAdminPanelComponent`'s remove-participant/delete-group
  confirmations are a lightweight inline two-click "are you sure?"
  step, not a reuse of `ConfirmDialogComponent`.** That component's
  actual shape (discovered while wiring this up) requires a
  backend-issued typed confirmation *word* via a `fetchToken: () =>
  Observable<string>` input — a flow built for
  `deletion-confirmation-token`'s higher-risk deletions, with no
  equivalent token endpoint anywhere in this feature's (or the backend
  companion's) API contract. Inventing one would have been unscoped
  backend work; the inline confirm keeps the "explicit second step
  before an irreversible action" property without it.
- **`chat-sidebar.component.ts` has no search input**, despite the
  components table's literal "4 section tabs + search input"
  description — the search field and its `searchQuery` state live
  entirely in `chat-directory.component.ts` (as PLAN.md's own "State
  and data" section already specifies), so adding a second copy in the
  sidebar would only mean prop-drilling a query string shell → sidebar
  → directory for state that already lives one level down. The sidebar
  is tabs only.
- **`ChatShellComponent` resolves which section to render from two
  sources** — `ActivatedRoute.data.chatSection` (`'peer'`/`'support'`/
  `'articles'`, set per id-carrying route in `app.routes.ts`) for the
  3 routes that carry a resource id, and the `section` query param
  (defaulting to `'people'`) only for the bare `/chat` path — rather
  than a single unified signal. This wasn't spelled out at the task
  level in TASKS.md (tasks 107/116 described the two halves
  separately) but is the mechanism that makes both halves of the
  redirect table (`/support/:channelId` → a real nested route;
  `/support` → `/chat?section=support`) work without four separate
  child routes.
- **`/support` and `/conversations`'s `redirectTo` are functions, not
  bare strings**, so the new `section` query param is added alongside
  whatever other query params a bookmarked URL might already carry
  (Angular's string `redirectTo` would silently drop them). `Route`'s
  function-based `redirectTo` (Angular 22) is used for exactly this;
  `/support/:channelId` stays a plain string redirect since it only
  needs to carry the path param through, not merge query params.

**Backend now implemented (2026-08-09).**
`knowly-api/specify/features/chat-group-membership-management/` shipped
all 138 TASKS.md items against the exact contract this reconciliation
table already reflects — no further corrections needed here; this
frontend feature can proceed against the live endpoints directly (see
`PROJECT_STATUS.md`'s `chat-group-membership-management` entry).

## Emergent decisions (implementation, 2026-08-09, "3-column then 2-column" amendment)

Following this SPEC's own navigation-surface amendment (tab strip →
3 columns → 2 columns, all same day), plus a set of tester-reported UX
fixes on the shipped cut:

- **`ChatShellComponent` went from a 4-tab dispatcher to a 3-column
  layout, then to the final 2-column layout, in one continuous session**
  — `ChatContactsPanelComponent` (the 3rd "já falou"/"ainda não falou"
  column) was built, tested green, then deleted the same day once the
  product owner found it redundant with the directory column's own
  People rows. Its partitioning *logic* survived — moved into
  `ChatDirectoryRowsService.talkedPeople()`/`notTalkedPeople()`, now
  consumed directly by `ChatDirectoryComponent` — only the separate
  column/component wrapper was discarded. `ChatDirectoryRowsService`
  itself was kept as its own file (not re-inlined into the component)
  since it still centralizes the click-to-open-or-create/join/
  request-to-join logic in one place, which remains useful with a single
  consumer.
- **`ChatSidebarComponent` gates 2 of its 3 actions on
  `ActiveTenantService.activeTenantId() !== null`** (a new
  `hasActiveTenant` input, computed by `ChatShellComponent`) —
  "Abrir chamado de suporte" and "Falar com a base de artigos" are both
  meaningless without a tenant (one opens a member ticket inside a
  tenant, the other calls a tenant-scoped RAG endpoint); a staff viewer
  doing pure cross-tenant oversight with no active tenant must not see
  them as available actions. "Criar grupo" stays unconditional. This was
  a genuine gap in the original amendment's reasoning (which reasoned
  about the *Support section itself* needing to work without a tenant
  for staff oversight, correctly, but didn't separately consider the
  *action buttons'* own tenant-scoping) — found by a tester, not
  originally specified.
- **The directory list's Support row uses a distinct i18n key
  (`chat.directory.supportRowLabel`, "Suporte"/"Support") from the
  sidebar's "Abrir chamado de suporte" action** — the first cut reused
  the action's own label for the row, which a tester correctly read as
  an accidental duplicate rather than two intentionally different
  entries (one starts a new ticket, the other reopens whatever Support
  experience already applies).
- **`shared/avatar.component.ts` (`AvatarComponent`) is a new, small,
  reusable image/generic-icon-fallback component**, extracted from
  `avatar-menu.component.ts`'s existing own-avatar pattern rather than
  reimplemented inline in `chat-directory.component.ts` — anticipating
  reuse in a future conversation header, per the request that prompted
  it. **Known data gap, not fixed here**: `PersonRow.avatarUrl` is
  always `null` today because `CandidateUserDto`
  (`GET /api/chat/eligible-participants`) only carries
  `{ userId, nickname }` on the wire — no `avatarUrl` field exists to
  read. `AvatarComponent` safely renders its generic fallback icon in
  this case (never a broken image), and the plumbing (`PersonRow`'s own
  `avatarUrl` field, `ChatDirectoryComponent`'s template) is already
  wired end-to-end — the only remaining gap is a backend DTO change
  (`CandidateUserDto` growing `avatarUrl`), tracked as a follow-up rather
  than done here, since this feature's scope is explicitly "no new
  endpoint/contract change."
- **`talkedPeople()`/`groupRows()` sort by id descending, not a real
  `lastMessageAt`** — same already-documented gap as `chat.model.ts`'s
  own comment on `ConversationSummary` carrying no activity timestamp.
  This is the closest available proxy for "most recently active" without
  a backend change, and — the specific bug this fixes — it depends only
  on conversation/group id, never on which row is currently open/
  selected, so merely opening a conversation cannot reorder the list (a
  tester reported exactly that flicker against an earlier build where,
  it turned out, no such reordering-on-click logic actually existed —
  the fix here is really a regression test locking in "selection never
  drives sort" as an explicit contract, not a behavior change).
- **Currently-open-row highlighting (`ChatDirectoryComponent`) derives
  the active id purely from `Router.url`** (via
  `Router.events`/`NavigationEnd`, `toSignal`), matching against
  `/chat/:id` (peer/group), `/chat/articles/:id`, and a
  `section=support` substring check for the Support row — no new
  service state, since the URL is already the single source of truth
  for "what's open" in this shell.
- **"Criar grupo" gained a `LucidePlus` icon** alongside its existing
  filled (`bg-signal-600`) style — the color alone wasn't enough for a
  tester to read it as a button rather than a permanently-"selected"
  list row (list rows have no selection styling of their own today).

## Amendment (4) reconciliation (2026-08-09): naming, renaming, icon (REQ-38–REQ-41, REQ-13)

> Covers SPEC.md's fourth amendment. The backend prerequisite is now
> **done and committed**: `POST /api/tenants/{tenantId}/conversations`
> (RAG, `title` required, `icon` optional), `PUT
> /api/tenants/{tenantId}/conversations/{id}` (RAG rename), and `PUT
> /api/chat/conversations/{id}` (group rename, title + optional icon) all
> exist, backed by a shared `br.com.conectabyte.knowly.icon.IconKey` enum
> (24 values, each verified to map to a real `@lucide/angular` export —
> see the list below). This section is written against the real,
> committed contract (`conversations/PLAN.md` §"API contracts",
> `chat-group-naming-and-icon/PLAN.md`), not a provisional guess — no
> "to confirm" language remains here. Nothing in this section touches
> column layout, collapse, search, or governance — those stay exactly as
> Amendment (3) left them.

### Consumed API contracts (final)

| Method | Path | Request | Response | Status |
|---|---|---|---|---|
| `POST` | `/api/tenants/{tenantId}/conversations` | `{ title: string (required, non-blank), icon?: IconKey }` | `ConversationSummaryDto { id, title, icon }` | `201`; `400` blank title/invalid icon; `403` missing `CONVERSATION_USE` |
| `PUT` | `/api/tenants/{tenantId}/conversations/{id}` | `{ title: string (required, non-blank), icon?: IconKey }` | `ConversationSummaryDto { id, title, icon }` | `200`; `400` blank title/invalid icon; `404` if not the caller's own conversation (deliberate — a 403 here would leak existence, per that PLAN's own "404 not 403" note; same reasoning REQ-16 already documents for the ownership case) |
| `PUT` | `/api/chat/conversations/{id}` | `{ title: string (required, non-blank), icon?: IconKey }` | `ChatConversationDetailDto` (`icon` field added, additive) | `200`; `400` blank title/invalid icon; `403` caller is not a current group admin (or is a `PEER_DIRECT` participant — the existing `requireGroupAdmin` check, reused unchanged); `404` unknown/wrong-kind/deleted conversation id |
| `POST` | `/api/chat/conversations` | (existing, +) `icon?: IconKey` alongside `kind`/`tenantId`/`title`/`participantUserIds`/`visibility` | `ChatConversationSummaryDto` (`icon` added) | `201`; `400` invalid icon key |

`IconKey` (24 values, both DTOs share the exact same enum — no separate
frontend/backend catalogs to keep in sync beyond string-literal parity):
`MESSAGE_CIRCLE`, `MESSAGES_SQUARE`, `BOOK_OPEN`, `NOTEBOOK`, `SPARKLES`,
`BOT`, `USERS`, `HASH`, `FOLDER`, `STAR`, `HEART`, `FLAG`, `TARGET`,
`ROCKET`, `LIGHTBULB`, `GLOBE`, `COMPASS`, `GRADUATION_CAP`,
`BRIEFCASE`, `ARCHIVE`, `TAG`, `BOOKMARK`, `LAYERS`, `CODE`.

Note the RAG endpoints' ownership-failure status code is **404, not
403** (deliberately, per the backend PLAN's own existing rationale) while
the group endpoint's equivalent failure is **403** (reusing
`requireGroupAdmin`'s existing, already-403 behavior, unchanged by this
amendment) — REQ-41's "inline error, dialog stays open" handling must
branch on status code per surface, not assume both dialogs get the same
code for "you don't own/administer this."

### Architectural decisions

- **New `IconKey` union type + a fixed display-name/Lucide-component
  lookup table live in `core/chat.model.ts`** (not duplicated in
  `conversation.service.ts` and `chat-group.service.ts` separately) —
  why: both RAG and group dialogs need the exact same 24-entry catalog,
  same as the backend sharing one enum between two DTOs; a single
  frontend source of truth (`ICON_KEYS: IconKey[]` + a
  `Record<IconKey, Type<LucideIconComponent>>` map for rendering) avoids
  the two dialogs drifting out of sync the way two independent frontend
  enums could.
- **One new shared, standalone `icon-picker.component.ts`
  (`shared/chat/`)**, not two near-duplicate pickers inside
  `create-group-dialog.component.ts` and the new RAG creation dialog —
  why: REQ-38's RAG picker and REQ-13(final round)/REQ-40's group picker
  are explicitly "same mechanism, same fixed icon set" per the product
  owner's own confirmation, not two independently-evolving UIs. A
  presentational component taking `[selected]: IconKey | null` and
  emitting `(iconSelected): IconKey` (a signal-based `input`/`output`
  pair, no form control wrapper needed since it's a single-value picker,
  not part of a larger reactive form) is reused by three call sites: the
  new RAG creation dialog, the new RAG rename affordance, and
  `create-group-dialog.component.ts` (creation) plus the new group
  rename affordance. Rendered as a grid of 24 `@lucide/angular` icon
  buttons, each `aria-label`ed with a human-readable name (not the raw
  enum key), consistent with this feature area's existing a11y
  convention (REQ requires "keyboard-reachable with `aria-label`s").
  This is a genuinely new, reusable UI pattern with no existing
  precedent in this codebase (badge/dialog precedents exist, a fixed-set
  icon-grid picker does not) — flagged here per PLAN.md discipline #1,
  not silently introduced; it needs no new dependency (`@lucide/angular`
  is already installed) so it is not a Tier 3 item.
- **The RAG creation dialog is a new `create-conversation-dialog.component.ts`**
  (`features/chat/`), replacing `onOpenArticles()`'s current silent
  `conversationService.create(tenantId)` call in
  `chat-shell.component.ts` — mirrors `create-group-dialog.component.ts`'s
  existing shape exactly (native `<dialog>`, template-driven signal-bound
  state, submit disabled until name is non-blank, per REQ-38/REQ-13's
  "mirroring 'Criar grupo''s existing disabled-until-named pattern").
  Composes `icon-picker.component.ts` for the optional icon. On submit,
  calls the now-title/icon-accepting `ConversationService.create`
  (signature change below) and opens the result as the active
  conversation, exactly as today's create-and-open behavior otherwise
  (REQ-38's closing clause).
- **Rename is a small inline affordance on each row, not a context
  menu.** Investigated this codebase's existing row-level-action
  precedent first, per the task brief: groups today expose "sair do
  grupo" as an action *inside the group's own opened view*
  (`conversation-detail.component.ts`, gated on `isParticipant`), never
  as a control on the directory row itself — there is no existing
  row-level context-menu/kebab pattern anywhere in `chat-directory
  .component.ts` or `chat-full-directory.component.ts` to follow.
  **Decision: rename lives inside the conversation's own opened view
  (column 2), as a small pencil-icon button next to the conversation's
  title in its header**, for both RAG and group conversations — this
  extends the *existing* "actions live inside the opened view, not on
  the directory row" convention `conversation-detail.component.ts`
  already establishes for "sair do grupo"/admin actions, rather than
  inventing a new context-menu interaction pattern column 1's rows have
  never had. Clicking it opens a small inline form (name input +
  `icon-picker.component.ts`, prefilled with the current title/icon) in
  place of the header, with save/cancel — not a new `<dialog>`, since
  this is an edit-in-place of already-visible content, not a modal
  workflow like creation. On success, the row's displayed title/icon in
  column 1 must update without a full reload (REQ-39/REQ-40's explicit
  requirement) — satisfied by patching the same shared signal state
  `ChatService`/`ChatDirectoryRowsService` already read from (see State
  and data below), the same "single source of truth, multiple thin
  consumers" pattern this PLAN already uses for group governance.
- **Rename ownership gating reuses each surface's existing capability
  computed, not a new one**: the RAG rename affordance renders only when
  the viewer is the conversation's own owning participant (mirrors
  REQ-36's existing "only the conversation's own participant may clear
  it" gating, same computed reused); the group rename affordance renders
  only when `adminUserIds.includes(currentUserId)` (the exact same
  `isAdmin` computed `group-admin-panel.component.ts` already derives,
  reused rather than duplicated — REQ-40's "same authorization model as
  REQ-28/REQ-31").
- **`ConversationService.create`/new `.rename` and
  `ChatGroupService.rename`/`ChatService.createConversation`'s `icon`
  field are all non-optimistic**, matching this PLAN's already-
  established REQ-25/REQ-27 pattern for group governance and REQ-41's
  own explicit wording ("never optimistically applying the change before
  the backend confirms it") — call the endpoint, patch local signal
  state only on success, inline error + dialog/edit-form stays open on
  failure.
- **[AppSec-added, 2026-08-09] RAG rename's `404` must render the same
  generic inline-error copy as every other rename/creation failure
  (e.g. "Não foi possível renomear. Tente novamente."), never a
  existence-specific string like "conversa não encontrada" or anything
  that would let the caller distinguish "this conversation id doesn't
  exist" from "it exists but isn't yours anymore" — the backend
  deliberately returns `404` instead of `403` for this exact
  existence-hiding reason (see "Consumed API contracts" above), and a
  frontend that renders a more specific message on `404` than on `400`/
  network failure would quietly reopen the leak the backend closed. The
  13d/13f inline edit-form's error branch must use one shared,
  status-code-agnostic error string per surface (RAG vs group), not a
  `switch` on status code that special-cases `404` text — group rename's
  `403` and `404` (wrong-kind/deleted/unknown id) may also share the
  same generic copy for the same reason. This is a MUST for task 200's
  and 214's Green implementation, not just the Red test's status-code
  assertion — the existing task wording ("surfaces distinctly to the
  caller") only covers *that an* error shows, not *what it says*; add an
  explicit assertion in 180/200/214's tests that the rendered message
  text is identical across the failure-status variants tested, not just
  that "an error shows."
- **[AppSec-added, 2026-08-09] Icon picker must only ever emit one of
  the 24 `IconKey` literal values** — `icon-picker.component.ts`'s
  `iconSelected` output type is the `IconKey` union itself (not
  `string`), and the component has no free-text/manual-entry path; every
  icon button's click handler emits a value drawn from the fixed
  `ICON_KEYS` constant it iterates to render the grid, never a value
  constructed from user input. This keeps the invalid-icon-key case
  frontend-unreachable by construction (defense in depth on top of the
  backend's own `400` validation) — confirm task 173's Green
  implementation has no `string`-typed intermediate that widens the type
  before emission.
- **[AppSec-added, 2026-08-09] Rename-affordance visibility must be
  computed from data already available client-side, not shown
  optimistically and gated only by the backend's `403`/`404`.** 13d/13f
  already specify reusing the existing ownership/admin computeds
  (REQ-36's owning-participant computed, `group-admin-panel.component.ts`'s
  `isAdmin` computed) — this is confirmed correct and is the load-bearing
  mechanism keeping the pencil icon off Support conversations and off
  RAG/group conversations the viewer doesn't control. Tasks 196/208's Red
  tests must assert non-rendering for a viewer who lacks the right
  (not just rendering for one who has it) to actually anchor this as a
  regression test, not just a happy-path one.

### Service method signature changes

- **`ConversationService`** (`core/conversation.service.ts`):
  - `ConversationSummary` gains `icon: IconKey | null` (additive).
  - `create(tenantId: number)` → `create(tenantId: number, title: string, icon?: IconKey)`,
    now `POST`ing `{ title, icon }` instead of `{}` — every existing call
    site (`chat-shell.component.ts`'s `onOpenArticles()`) must be updated
    to route through the new creation dialog instead of calling this
    directly with no name.
  - New method: `rename(tenantId: number, conversationId: number, title: string, icon?: IconKey): Observable<ConversationSummary>`,
    `PUT`ting `/api/tenants/${tenantId}/conversations/${conversationId}`
    with `{ title, icon }`.
- **`ChatGroupService`** (`core/chat-group.service.ts`): new method
  `rename(id: number, title: string, icon?: IconKey): Observable<ConversationDetail>`,
  `PUT`ting `/api/chat/conversations/${id}` with `{ title, icon }`, and
  on `200` patching `ChatService`'s `_details` map with the returned
  detail — same "patch the shared map on success only" pattern every
  other `ChatGroupService` action already follows. `ConversationDetail`
  (`core/chat.model.ts`) gains `icon: IconKey | null` (additive, matches
  the backend's extended `ChatConversationDetailDto`).
- **`ChatService.createConversation`**'s `CreateConversationRequest`
  (`core/chat.model.ts`) gains an optional `icon?: IconKey` field,
  forwarded verbatim in the existing `POST /api/chat/conversations` call
  — no new method, since group creation already goes through this one
  request shape (REQ-13, final round).

### V32 migration — NOT NULL backfill check (verified, no gap)

Read `knowly-api/src/main/resources/db/migration/V32__add_title_required_and_icon_to_conversations.sql`
directly: it runs `UPDATE conversations SET title = 'Conversa sem
título' WHERE title IS NULL` **before** `ALTER TABLE conversations ALTER
COLUMN title SET NOT NULL`. Every pre-existing `NULL`-title row (the
"Nova conversa" rows already present in dev, confirmed by this task's
own brief) is backfilled to a real, non-blank string before the
constraint is added — the migration cannot fail against existing data,
and no existing RAG conversation row violates the new `NOT NULL`. **This
is not a gap.** The one frontend-visible consequence worth noting
explicitly (not a bug, a UX fact): any RAG conversation created before
this feature shipped will render in column 1 with the literal fallback
title "Conversa sem título" and no icon (`icon` is nullable, untouched
by the backfill) — REQ-2's existing "falling back to a default icon
otherwise" clause already covers the no-icon case; no extra frontend
handling is needed for the backfilled title itself, since it's just an
ordinary non-blank string from the row's own perspective.

## Emergent decisions (implementation, 2026-08-09, TASKS.md section 12
completion pass)

> Covers finishing the remaining, previously-untouched unblocked tasks
> in section 12 (12d's collapse work, 12f/12g's regression anchors,
> 12i's a11y/i18n/verification) — 12a/12b/12c had already landed in
> earlier commits (`a72dc8f`, `ba48810`) by the time this pass started;
> that work is unchanged here, only checkbox-reconciled.

- **12d's 3-way collapse and its `chat-shell.component.spec.ts`/
  `chat-shell.component.ts` changes were already present, uncommitted,
  in the working tree** at the start of this pass — a
  `mobileFullDirectoryOpen` signal, reset on every route/query-param
  change, drives the third (`'fullDirectory'`) branch of `mobileView()`
  since column 3 has no route of its own to derive state from (unlike
  the conversation/directory halves, which stay purely route-derived).
  This pass added one more regression test on top
  (`chat-shell.component.spec.ts`, "column 1's and column 3's search
  fields each have their own, never-equal aria-label") to close out
  task 167's "one combined test asserting the two labels are never
  equal" wording literally, rather than relying on the two components'
  already-existing, separate "has *an* aria-label" assertions to imply
  it.
- **12f's regression anchor** was the one task in section 12 with no
  existing test at all: added
  `conversation-detail.component.spec.ts`'s "renders no 'limpar'/'clear'
  control anywhere in a group's view" case, alongside the existing
  "leave group" tests it's meant to sit next to, per REQ-34.
- **12g was already fully implemented** (`chat-directory.component
  .spec.ts`'s "does not render any clear/limpar control for the Support
  row" test) by the time this pass started — no new work needed, only
  checkbox reconciliation.
- **i18n retirement (task 166)** required no removal work: a grep for
  `talkedTitle`/`notTalkedTitle`/`talkedSearchLabel`/
  `notTalkedSearchLabel` across `public/i18n/*.json` and `src/` came
  back empty — the earlier 12c commit had already fully replaced them
  with `chat.directory.unifiedSearchLabel`/`chat.fullDirectory.*` keys,
  never leaving the superseded keys behind as dead entries.
- **12e and 12h remain BLOCKED**, unchanged from this PLAN's existing
  hard-delete feasibility decision — no code for either was started in
  this pass, per TASKS.md's explicit instruction not to reorder blocked
  tasks earlier.
- Full verification (`npm run format:check && npm test && npm run
  build && npm run lint`) passed clean after this pass's changes: 887
  tests across 104 spec files, zero lint errors, a clean production
  build. One pre-existing formatting drift in
  `chat-shell.component.ts` (from the already-uncommitted 12d changes)
  was fixed via `prettier --write` as part of this pass rather than
  left for a later cleanup commit, per this repo's "resolve warnings
  before commit" convention.

## Emergent decisions, Amendment (4) (implementation, 2026-08-09,
TASKS.md section 13 completion pass)

> Covers 13a–13h end to end. All backend endpoints consumed exactly as
> committed (see "Consumed API contracts" above) — no backend surprises
> found during implementation.

- **`ConversationSummary` (RAG, `core/conversation.service.ts`) and
  `ConversationSummary` (group, `core/chat.model.ts`) both needed the
  additive `icon` field** — not just `ConversationDetail`. Column 1's
  group rows read a group's icon from the already-fetched
  `ChatService.conversations()` list (`GET /api/chat/conversations`,
  `ChatConversationSummaryDto`), not from a per-row detail fetch (no
  such fetch happens until a group is actually opened) — the PLAN's
  original "carried on `ConversationDetail`" framing undersold this;
  the summary DTO carries it too, and the frontend needed a matching
  field to avoid a group row waiting on a detail fetch that may never
  happen before it's rendered.
- **Two small, deliberately duplicated 24-entry lookups exist**:
  `core/chat-icon-registry.ts` (`IconKey` → `{ component, selector,
  labelKey }`, used for `icon-picker.component.ts`'s `aria-label`s) and
  `shared/chat/chat-icon.component.ts` (a `@switch` over the same 24
  keys, statically importing every Lucide icon component so nothing
  needs `NgComponentOutlet`/dynamic-component wiring, consistent with
  this codebase's existing attribute-selector `@lucide/angular`
  convention). Not unified into one file because the registry's
  `Type<unknown>` entries were never actually consumed by anything
  once `ChatIconComponent`'s own static `@switch` was written — kept
  both since the registry's `labelKey`s are genuinely still needed for
  `icon-picker.component.ts`'s per-button `aria-label`s and merging
  would have made `chat-icon.component.ts` import a component type
  in a way `@switch` doesn't need. A follow-up could fold the registry
  into `chat-icon.component.ts` as a `labelKey`-only map; left as-is
  since both are small, single-purpose, and already tested.
- **RAG rename's "owning participant" gating (REQ-39) is satisfied by
  construction, not a new client-side computed.** `conversations
  -page.component.ts`'s conversation list already only ever contains
  the caller's own RAG conversations (`GET
  /api/tenants/{tenantId}/conversations` is caller-scoped, per
  `conversations`' backend contract) — REQ-36's clearing feature (which
  PLAN.md originally said this gating "reuses") was never actually
  implemented in this codebase (no `clearConversation`/REQ-36 code
  exists yet), so there was no existing ownership computed to reuse.
  The rename pencil is shown whenever a RAG conversation is open at
  all, which is equivalent to "the viewer owns it" given the list is
  already owner-scoped server-side — documented here rather than
  silently diverging from the PLAN's stated reuse.
- **Group rename (REQ-40) lives in `chat-header.component.ts`
  directly** (injecting `ChatGroupService`, gated by a new
  `canRenameGroup` computed mirroring `group-admin-panel.component
  .ts`'s `isAdmin`), not in `conversation-detail.component.ts` or a new
  component — `ChatHeaderComponent` is the one place already shared by
  every `PEER_GROUP`/`PEER_DIRECT`/`SUPPORT` detail view, and it already
  owns the icon+title rendering the rename form replaces in place.
- **`RenameFormComponent` (`shared/chat/rename-form.component.ts`)
  seeds its editable `name`/`icon` signals from `initialTitle()`/
  `initialIcon()` via a one-time-guarded `effect()`, not a field
  initializer or constructor-body read** — Angular's `NG8118` static
  check forbids reading a signal `input()` synchronously in a field
  initializer/constructor body (the value isn't guaranteed set until
  the first change-detection pass), and a plain always-reactive
  `effect()` would clobber an in-progress edit if the parent's
  `initialTitle()`/`initialIcon()` binding happened to re-evaluate
  (e.g. a parent re-render) while the form was open — the `seeded`
  guard makes this a genuine "read-once" prefill.
- **`onNewConversation()` (`conversations-page.component.ts`'s
  pre-existing in-page "+ Nova conversa" button, a secondary creation
  path inside an already-open RAG view, not the sidebar's "Falar com a
  base de artigos" action REQ-38 actually covers) needed a compile-safe
  title now that `ConversationService.create` requires one** — it now
  passes the existing `conversations.new` i18n string as a default
  title rather than gaining its own naming dialog, since REQ-38's scope
  is specifically the sidebar action (tasks 194/195); flagged here as a
  deliberate, minimal fix to an out-of-scope call site rather than a
  silent behavior change.
- **`AvatarComponent` gained an `icon: IconKey | null` input** (highest
  precedence, checked before both the existing image and `kind`
  fallback) rather than a parallel new component — every existing call
  site (person rows, group headers) is unaffected since the input
  defaults to `null`, and this reuses `AvatarComponent`'s existing
  round-avatar-with-fallback layout for RAG/group icons rather than
  duplicating it.
- **`icon-picker.component.ts` gained a `groupLabel` input** (an
  `aria-label` on its own `role="group"` wrapper) after `ng lint`
  caught `@angular-eslint/template/label-has-associated-control`
  errors on the two `<label>` wrappers originally used around it (in
  `create-group-dialog.component.ts`/`create-conversation-dialog
  .component.ts`) — a `<label>` needs an associated single form
  control, which a 24-button grid isn't; replaced with a `<div>` +
  visible `<span>` text + the picker's own `role="group"`
  `aria-label`, satisfying both the lint rule and screen-reader
  labeling.
- Full verification (`npm run format:check && npm test && npm run
  build && npm run lint`) passed clean: 925 tests across 107 spec
  files, zero lint errors, a clean production build.
