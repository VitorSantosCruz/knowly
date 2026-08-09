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
