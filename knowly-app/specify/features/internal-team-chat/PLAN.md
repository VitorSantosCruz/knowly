# PLAN — internal-team-chat (frontend)

> The how. Translates SPEC.md into concrete technical decisions.
> References SPEC.md. **Reconciled against
> `knowly-api/specify/features/internal-team-chat/PLAN.md` (2026-07-31)**
> — the API contract section below is the final, agreed shape; it is no
> longer provisional. See the root `DECISIONS.md` for the real-time
> delivery decision this reconciliation confirmed.

## Architectural decisions

- **Two top-level routes, not one**: `/chat` (peer 1:1/group conversations
  list + conversation view) and `/support` (member's own channel, or the
  staff inbox / member support-browsing view depending on session) — why:
  REQ-1..9 (peer) and REQ-10..18 (support) are different data models with
  different eligibility/permission rules; forcing them into one route
  with internal tabs would blur the guard story (peer chat has no guard,
  support-inbox/support-browse screens are permission-gated) and doesn't
  match any existing precedent (`/dashboard` splits by *active-tenant
  presence*, not by feature).
- **`/chat` carries `tenantSelectionGuard`, `/support` does not.** Why:
  peer conversations (REQ-1/REQ-2/REQ-3) are explicitly available to
  "any staff or tenant member... regardless of role" and `STAFF_ADMIN`
  oversight (REQ-7) spans *every* tenant, which only works correctly for
  a staff session with no single active tenant selected — gating `/chat`
  behind `tenantSelectionGuard` would break the `STAFF_ADMIN` "all tenants"
  case. `/support`'s member-facing behavior (REQ-10/11) is scoped to the
  viewer's own single support channel, which is resolved by the backend
  from the authenticated user, not from an active-tenant selection —
  same reasoning as `/profile` and `/profile-edit-requests` in
  `app.routes.ts`, which also carry no guard for the identical reason
  ("universal to any authenticated session regardless of tenant
  context"). The staff-inbox and member-support-browse sub-views inside
  `/support` are gated by a **permission check inside the page component**
  (mirroring `staffGuard`'s "check the actual permission via
  `GET /api/staff/permissions`" fix), not a route guard — because the
  same `/support` route must render three different things (my own
  channel / staff inbox / nothing) depending on session shape, the same
  "one screen, two [here: three] contexts" pattern as
  `DashboardWrapperPageComponent`/`UserManagementPageComponent`, not a
  guard redirect to a different URL.
- **`/chat/:conversationId` and `/support/:channelId` as child routes**,
  not query params — why: conversation/channel identity is a resource
  identity, not transient view state; matches this app's existing
  pattern of resource ids in the path (`/tenants/:id` equivalents seen
  elsewhere) rather than `?id=`.
- **New signals-based `chat.service.ts`** (peer conversations + messages)
  and **`support.service.ts`** (support channels/tickets), following the
  `PermissionsService`/`ActiveTenantService` shape exactly: private
  signal(s) + public `.asReadonly()` + `fetch()`/action methods owning
  the HTTP call — no new state pattern. Kept as two services, not one,
  because their data shapes, eligibility rules, and permission gates are
  independent (REQ-1..9 vs. REQ-10..18); a single service would mix two
  unrelated domains the way `PermissionsService`/`GlobalPermissionsService`
  are already kept separate despite structural similarity.
- **A shared, reusable `message-thread.component.ts`** (message list +
  progressive/paginated loading UI + optional composer) used by both the
  peer conversation view and the support channel view — why: REQ-19/20/21
  (progressive loading, local loading indicator, retry-on-failure) are
  identical requirements across both shapes; the only per-shape
  difference is whether a composer is shown and what "load more" calls.
  Parametrized via `@Input()`s (`messages`, `hasMore`, `loading`,
  `loadError`, `showComposer`, and output events `loadMore`/`send`) so it
  stays a pure presentational component with no service of its own
  (matches `metric-tile.component.ts`'s "additive presentational mode"
  precedent for a shared, parametrized display component rather than a
  fork per consumer).
- **Cursor-based pagination state (`beforeMessageId`/`afterMessageId`,
  opaque cursor), not page/size offset pagination**, for message
  history. Why: REQ-19's requirement is "never duplicate already-loaded
  messages" while new messages may be arriving concurrently (see the
  polling decision below) — offset-based pagination silently shifts
  under concurrent inserts (a new message pushes the "next page"
  boundary), which is exactly the failure mode REQ-19/21 exist to avoid;
  a cursor anchored to a message `id` is stable regardless of what's
  appended at the newest end. **Reconciled with the backend PLAN**: the
  cursor is `id`-only (not the compound `(created_at, id)` this PLAN's
  first draft assumed the backend would need) — `chat_messages.id` is a
  `BIGSERIAL`, strictly increasing in insertion order per conversation,
  so it's already a total order and needs no timestamp tie-breaker; the
  backend PLAN was corrected to match. This is a genuinely new
  pagination shape for this codebase (`tenant-pagination-search`'s
  `PageResponseDto` is page/size, built for a stable list, not an
  append/prepend feed) — see the `DECISIONS.md` entry below.
- **Look-in oversight rendering is a presentational flag on the
  conversation list item and header, never a separate route/component
  tree.** A group conversation the viewer reaches via REQ-7/REQ-8's
  override carries a `viewerRelation: 'PARTICIPANT' | 'LOOKING_IN'` field
  (from the list/detail API) that the same conversation-list-item and
  chat-header components branch on — a distinct badge
  ("Looking in as admin/support"), a distinct header banner, and the
  composer conditionally omitted (per REQ's "no composer" + the SPEC's
  out-of-scope note). Why one component with a branch, not two: the
  *data* (messages, participants, header info) rendered is identical
  either way — only a small, localized part of the chrome changes, so a
  parallel component tree would duplicate the message-thread wiring for
  no reason.
- **New real-time approach: short-interval polling, not WebSocket/SSE
  for peer/support message delivery — a Tier 2 judgment call, no exact
  precedent exists.** The only existing "live update" pattern in this
  codebase is `ConversationService.sendMessage`'s **SSE response stream
  for the AI assistant's own reply to that one request** (`fetch()` +
  manual `ReadableStream` pump) — that is answering a single in-flight
  request, not a channel subscription for messages sent by *other*
  users, which is what peer/support chat actually needs. Introducing a
  bidirectional WebSocket (or a server-push SSE endpoint per open
  conversation) is real new backend infra (a stateful connection per
  open chat) with no existing precedent to reuse, and the SPEC
  explicitly marks "real-time transport" out of scope as a PLAN-level
  decision. **Decision: poll `GET .../messages?after=<lastSeenId>` every
  5s while a conversation/channel view is open (paused when the tab is
  hidden via the Page Visibility API, to avoid needless load), reusing
  the exact same cursor/cache-append logic REQ-19's "load older" already
  needs** (new messages get appended at the newest end the same way
  older ones get prepended). Why 5s and not e.g. 2s or 15s: no NFR pins
  this down; 5s is short enough to feel "live" for a text-chat use case
  at this app's current scale, and long enough not to meaningfully add
  load next to the existing per-open-view request volume. **Reconciled
  with the backend PLAN**: the backend PLAN independently deferred
  real-time delivery to "whatever GET the frontend already polls," which
  is exactly this decision — the two PLANs were never actually in
  conflict, just written from opposite ends of the same mechanism; the
  backend PLAN added explicit `after=<cursor>` support to the messages
  endpoint on reconciliation specifically to serve this. This is the
  first "receive updates from other users" requirement in this
  codebase — see the `DECISIONS.md` entry below (now written, not just
  drafted); a future feature with a materially different freshness/scale
  requirement should revisit this, not copy the 5s constant blindly, and
  should evaluate the backend PLAN's flagged future direction (SSE-per-
  user backed by RabbitMQ fan-out) rather than reinventing a third
  approach.
- **Participant/group-creation picker is a new, reusable
  `participant-picker.component.ts`**, driven entirely by a backend
  eligibility endpoint (see contracts below) rather than any client-side
  staff/member/tenant filtering logic — why: REQ-2/REQ-3 are explicit
  that eligibility ("staff↔member 1:1", per-tenant capacity for groups)
  must reflect the backend's rule, never be reimplemented in the UI; the
  component's only job is to render whatever candidate list the backend
  returns for the current picker context (1:1 target, member-only-group
  anchored to tenant `T`, or staff-only-group) and let the caller select
  from it — no eligibility branching lives in this component or its
  service.
- **Look-in participant list exclusion is asserted by the frontend
  test suite against the API contract, not re-derived client-side.**
  REQ-7/8's "the admin never appears in the group's own participant
  list" is a backend invariant (the look-in override never inserts a
  membership row) — the frontend's job is only to render whatever
  participant list the backend returns, plus render the separate
  `viewerRelation` banner; there is no client-side filtering-out-the-
  admin logic to write or maintain, since the admin was never in that
  list to begin with. Documented explicitly so a future reader doesn't
  assume a defensive client-side filter is missing.
- **Support inbox and member-support-browse reuse the same
  `support.service.ts` and the same `message-thread.component.ts`**,
  differing only in which endpoint the surrounding page calls
  (`/api/tenants/{tenantId}/support/tickets/unclaimed` for staff,
  `/api/tenants/{tenantId}/support/members/{memberUserId}/channel` for
  browsing another member) — same one-shape-per-concern reasoning as the
  peer side.

## Components and routes

```
core/
  chat.service.ts                (+ .spec.ts)
  support.service.ts             (+ .spec.ts)
  chat-permission.ts              // re-exports the confirmed backend string literals:
                                   // GlobalPermission.STAFF_SUPPORT_HANDLE (inbox/claim/
                                   // transfer/close gating) and Permission.SUPPORT_CHANNEL_VIEW
                                   // (tenant-scoped browse-other-members gating), per
                                   // knowly-api's internal-team-chat PLAN.md

features/chat/
  chat-page.component.ts                    // route: /chat — list + outlet for detail
  conversation-list.component.ts             // REQ-1/7/8: own conversations + look-ins
  conversation-list-item.component.ts        // branches on viewerRelation
  conversation-detail.component.ts           // route: /chat/:conversationId
  chat-header.component.ts                   // participant names, look-in banner
  new-conversation-dialog.component.ts       // REQ-2/3 entry point (1:1 vs group toggle)
  participant-picker.component.ts            // reusable candidate multi/single-select

features/support/
  support-page.component.ts                  // route: /support — dispatches by role
  member-support-channel.component.ts        // REQ-10/11: own channel
  staff-support-inbox.component.ts            // REQ-12/13: unclaimed queue + claim
  staff-support-channel.component.ts          // REQ-13..16: claimed 1:1 view, transfer/close
  member-support-browse.component.ts          // REQ-17: browse other members' histories
  ticket-status-badge.component.ts            // OPEN/CLOSED/ASSIGNED chip, reused everywhere

shared/chat/
  message-thread.component.ts                 // REQ-19/20/21, shared by both features
  message-composer.component.ts                // send + inline retry (REQ-6)
```

Routes added to `app.routes.ts`:

| Path | Component | Guard |
|---|---|---|
| `/chat` | `ChatPageComponent` | none (see rationale above) |
| `/chat/:conversationId` | `ChatPageComponent` (child outlet: `ConversationDetailComponent`) | none |
| `/support` | `SupportPageComponent` | none |
| `/support/:channelId` | `SupportPageComponent` (child outlet) | none |

`SupportPageComponent` fetches `GET /api/staff/permissions` once
(mirroring `staffGuard`'s fixed pattern, not a route guard — see above)
to decide, in order: does the viewer hold the support-handling
permission → render `StaffSupportInboxComponent`/
`StaffSupportChannelComponent`; else does the viewer hold the
support-view permission → offer `MemberSupportBrowseComponent` alongside
their own channel; else → `MemberSupportChannelComponent` only. A staff
session with the support-handling permission still sees their own member
channel only if they *also* hold a real tenant membership somewhere — out
of scope here to invent; if the backend SPEC's model gives staff no
personal support channel at all, `member-support-channel.component.ts`
simply never renders for a pure-staff session (404/empty-state handled
the same inline way every other "not applicable to this session" case
already is, e.g. `ProfileEditRequestsInboxPageComponent`'s empty list).

## Consumed API contracts

> **Final, reconciled against `knowly-api/specify/features/internal-team-chat/PLAN.md`
> (2026-07-31)** — this is the backend's actual contract, adopted here
> verbatim rather than this PLAN's earlier provisional design (which used
> flat `/api/support/...` paths, split `direct`/`group` POST endpoints,
> and a `limit`/`hasMore` envelope shape; none of that survived
> reconciliation). Note this is a plain array/object response shape, not
> `tenant-pagination-search`'s `PageResponseDto` envelope — that envelope
> is for page/size-stable lists; this is the cursor-feed shape per the
> pagination decision above.

### Peer chat

| Method | Path | Request | Response | Status |
|---|---|---|---|---|
| GET | `/api/chat/conversations` | — | `ConversationSummary[]` (own + look-ins, `viewerRelation` per item) | 200 |
| POST | `/api/chat/conversations` | `{ kind: 'DIRECT'\|'GROUP', tenantId?: number\|null, title?: string, participantUserIds: number[] }` | `ConversationSummary` | 201, 400 (ineligible participant), 403 |
| GET | `/api/chat/conversations/{id}` | — | `ConversationDetail` (header info, `viewerRelation`, participants) | 200, 403/404 (not participant, not eligible for look-in) |
| GET | `/api/chat/conversations/{id}/messages?before={cursor}&size=30` | — | `MessagePage` (older page, `messages` oldest→newest, `nextCursor` = next older cursor or `null`) | 200, 403, 404 |
| GET | `/api/chat/conversations/{id}/messages?after={cursor}&size=30` | — | `MessagePage` (poll: everything newer than `cursor`, `nextCursor` = newest id seen or `null` if empty) | 200, 403, 404 |
| POST | `/api/chat/conversations/{id}/messages` | `{ content }` | `Message` | 201, 403 (look-in viewer, not a real participant), 404 |
| GET | `/api/chat/eligible-participants?scope=direct` | — | `CandidateUser[]` | 200 |
| GET | `/api/chat/eligible-participants?scope=group&tenantId={id}` | — | `CandidateUser[]` (member-only-group candidates) | 200 |
| GET | `/api/chat/eligible-participants?scope=group-staff-only` | — | `CandidateUser[]` (staff-only-group candidates) | 200 |

`ConversationSummary`: `{ id, kind: 'DIRECT'|'GROUP', tenantId: number|null, title: string|null, participantNicknames: string[], viewerRelation: 'PARTICIPANT'|'LOOKING_IN', lastMessagePreview, lastMessageAt }`.
`CandidateUser`: `{ userId, nickname }` — matches the backend's `CandidateUserDto` exactly.
`Message`: `{ id, conversationId, senderUserId, senderNickname, content, createdAt }`.
`MessagePage`: `{ messages: Message[], nextCursor: string|null }` — opaque cursor is `base64(messageId)`; the client never decodes it, only round-trips whatever the server returned. Cursor is **id-only**, not a timestamp compound — see the pagination decision above.

### Support channel

Tenant-nested, per the backend PLAN — the active tenant id (from
`ActiveTenantService`) is required for every one of these calls, which
this PLAN's earlier flat `/api/support/...` draft missed (support
tickets are tenant-anchored data, not a global-staff concept, except for
the inbox/claim/transfer/close actions themselves which a `STAFF_SUPPORT_HANDLE`
holder can act on across tenants — see below).

| Method | Path | Request | Response | Status |
|---|---|---|---|---|
| POST | `/api/tenants/{tenantId}/support/tickets` | `{}` | `TicketSummary` | 201, 409 (already has an open ticket) |
| GET | `/api/tenants/{tenantId}/support/tickets/unclaimed` | — | `TicketSummary[]` | 200, 403 (no `STAFF_SUPPORT_HANDLE`) |
| POST | `/api/tenants/{tenantId}/support/tickets/{ticketId}/claim` | `{}` | `TicketSummary` | 200, 403, 409 (already claimed) |
| POST | `/api/tenants/{tenantId}/support/tickets/{ticketId}/transfer` | `{ toStaffUserId }` | `TicketSummary` | 200, 403, 400 (target lacks permission) |
| POST | `/api/tenants/{tenantId}/support/tickets/{ticketId}/close` | `{}` | `TicketSummary` | 200, 403, 409 (already closed) |
| GET | `/api/tenants/{tenantId}/support/members/{memberUserId}/channel` | — | `SupportChannelSummary` (own, or another member's if `SUPPORT_CHANNEL_VIEW` held) | 200, 403, 404 |
| GET | `/api/tenants/{tenantId}/support/members/{memberUserId}/channel/messages?before={cursor}&size=30` | — | `MessagePage` | 200, 403, 404 |
| GET | `/api/tenants/{tenantId}/support/members/{memberUserId}/channel/messages?after={cursor}&size=30` | — | `MessagePage` (poll) | 200, 403, 404 |
| POST | `/api/tenants/{tenantId}/support/members/{memberUserId}/channel/messages` | `{ content }` | `Message` | 201, 403 (not the assigned staff / view-only), 404, 409 (closed ticket) |

For the staff inbox view, which is not tenant-scoped in the UI (a
`STAFF_SUPPORT_HANDLE` holder sees unclaimed tickets across every tenant,
mirroring `STAFF_ADMIN`'s cross-tenant look-in reasoning elsewhere in this
PLAN): `staff-support-inbox.component.ts` calls the
`tickets/unclaimed` endpoint once per tenant the viewer has resolved
access to, rather than the backend exposing a separate flat aggregate
endpoint — the backend PLAN does not define a cross-tenant inbox
aggregation endpoint, and inventing one is out of scope for this
reconciliation; if the unclaimed-queue UX needs a true cross-tenant
single call, that's a follow-up backend PLAN change, not something this
frontend PLAN decides unilaterally.

`SupportChannelSummary`: `{ userId, memberNickname, openTicket: TicketSummary|null, closedTickets: TicketSummary[] }`.
`TicketSummary`: `{ id, status: 'OPEN'|'ASSIGNED'|'CLOSED', assignedStaffUserId: number|null, assignedStaffNickname: string|null, createdAt, closedAt: string|null }` — note `ASSIGNED` is a real, distinct status per the backend PLAN's `SupportTicketStatus` enum (`OPEN`/`ASSIGNED`/`CLOSED`), not just `OPEN`/`CLOSED` as this PLAN's earlier draft assumed; `ticket-status-badge.component.ts` renders three states, not two.

## State and data

- `ChatService` (signals): `_conversations` (list, `Signal<ConversationSummary[]>`),
  per-open-conversation message cache keyed by conversation id
  (`Map<number, { messages: Message[]; hasMore: boolean; oldestCursor: string|null }>`
  held in a signal, updated immutably on load-more/poll/send), `fetchConversations()`,
  `openConversation(id)`, `loadOlderMessages(id)`, `sendMessage(id, content)`
  (optimistic-append with a `pending`/`failed` per-message flag for REQ-6's inline
  retry — matches REQ-6's "append then mark failed, let retry" rather than only
  appending on server confirmation, since REQ-5 says append "when the current user
  sends," not "when the server confirms").
- `SupportService` (signals): mirrors the same shape for `myChannel`, `inboxTickets`,
  and the same per-channel message-cache map, plus `claim(ticketId)`/
  `transfer(ticketId, toUserId)`/`close(ticketId)` actions that patch the relevant
  `TicketSummary` signal in place on success.
- Polling lives in the two detail components (`ConversationDetailComponent`,
  `StaffSupportChannelComponent`/`MemberSupportChannelComponent`), not inside the
  services — an `interval(5000)` piped through `takeUntilDestroyed()` and gated by
  `document.visibilityState === 'visible'`, calling the service's existing
  "poll for after cursor" method; this keeps the service itself a plain
  fetch-on-demand API surface (consistent with `ActiveTenantService`/
  `PermissionsService` never owning their own timers) and the polling lifecycle
  scoped exactly to "a detail view is mounted," which is also naturally when
  `takeUntilDestroyed()` tears it down.
- No reactive forms needed for the composer (single textarea + submit is simpler
  as a template-driven `[(ngModel)]`/signal-bound value, matching this app's
  existing composer-less-than-form-heavy screens); the new-conversation dialog's
  participant picker uses a plain signal-backed multi-select list (checkbox per
  candidate), not a `FormArray` — no existing precedent for `ReactiveFormsModule`
  on a multi-select-from-a-fetched-list shape in this codebase, and a signal-backed
  `Set<number>` of selected ids is simpler for this exact interaction.

## Dependencies

None. No new npm package — polling uses RxJS `interval`/`takeUntilDestroyed`
(already a dependency), icons come from `@lucide/angular` (already in use),
styling is Tailwind only, per the existing "Ink & Signal" tokens (chat bubbles,
badges, and the "looking in" banner reuse existing `ink-*`/`signal-*` utility
classes and existing badge/chip patterns — e.g. `ticket-status-badge.component.ts`
follows the same visual shape as any existing status-chip component in
`user-management`/`dashboard`, not a new visual language).

## Testing strategy (Vitest)

- `chat.service.spec.ts` / `support.service.spec.ts`: HTTP mocking via
  `HttpTestingController`, covering fetch/list/send/claim/transfer/close success
  and error paths, and the cursor-append/prepend logic (load-older prepends without
  duplicating, poll-after appends without duplicating — the exact REQ-19/21
  invariant), matching `active-tenant.service.spec.ts`'s existing style.
- `participant-picker.component.spec.ts`: renders whatever candidate list the
  service returns with no client-side filtering — a fake candidate list that
  intentionally includes non-obvious cases (a staff user, a plain member) is
  rendered as-is, asserting the component never excludes/includes based on any
  local staff/member check.
- `conversation-list-item.component.spec.ts` / `chat-header.component.spec.ts`:
  the `viewerRelation === 'LOOKING_IN'` branch renders the oversight banner/badge
  and omits the composer trigger; `'PARTICIPANT'` renders normally — this is the
  component-level test backing the SPEC's "never mistaken for membership"
  acceptance criterion and its `aria-label`/copy requirement.
- `message-thread.component.spec.ts`: initial page render, "load more" triggers
  the older-page fetch, a failed older-page fetch shows retry without discarding
  already-loaded messages (REQ-21), loading indicator is local not full-screen
  (REQ-20).
- `staff-support-channel.component.spec.ts`: composer hidden for a ticket assigned
  to someone else (REQ-14), composer/full-history correctly swaps after transfer
  (REQ-15), closed ticket shows badge + no composer + no reopen action anywhere
  (REQ-16).
- `chat-page.component.spec.ts` / `support-page.component.spec.ts`: routing-level
  smoke tests confirming no route in this feature ever renders a
  permission-denied state for plain messaging (first acceptance criterion) and
  that `/support`'s three-way dispatch picks the right sub-view per permission
  response.
- Accessibility: every new interactive control (`send`, `claim`, `transfer`,
  `close`, `load more`) gets an explicit `aria-label` assertion in its component
  spec, matching this app's existing accessibility-test convention (see any
  existing `*.spec.ts` asserting `aria-label` in `user-management`/`dashboard`).

## Reconciliation status

Resolved (2026-07-31) against `knowly-api/specify/features/internal-team-chat/PLAN.md`
by the software architect: exact endpoint paths/nesting, the cursor
shape (id-only, not `(created_at, id)`), the `Permission`/`GlobalPermission`
string literals (`SUPPORT_CHANNEL_VIEW`, `STAFF_SUPPORT_HANDLE`), and the
real-time delivery approach (client polling via the existing paginated
GET, now with backend-added `after` support) are all final per the
"Consumed API contracts" section above. The two `DECISIONS.md` entries
originally drafted here have been written to the root `DECISIONS.md`
(see `internal-team-chat` entries there) rather than kept as drafts in
this file.

## Emergent decisions (implementation)

All 119 TASKS.md items are implemented, tested, and committed
(2026-07-31). Decisions made while executing tasks 100-113 that weren't
already spelled out above:

- **`SupportTicketDto` carries neither `tenantId` nor `memberUserId`** —
  confirmed against `knowly-api`'s actual DTO (only
  `id, supportChannelId, status, assignedStaffUserId, openedAt,
  closedAt`), not a PLAN.md oversight caught earlier. `SupportPageComponent`
  bridges this by calling `ChatService.openConversation(supportChannelId)`
  and reading `{tenantId, participantUserIds[0]}` off the resulting
  `ConversationDetail` — valid because a support channel's only formal
  `ChatParticipant` is the member (staff act on it without ever being
  added as participants; see `SupportTicketService.getOrCreateChannel`).
  This reuses the existing peer-chat detail endpoint as the bridge rather
  than adding a new backend field/endpoint, which was out of scope for
  this (frontend-only) task range.
- **`/support/:channelId` is a second flat route to `SupportPageComponent`
  itself** (reading `:channelId` via `ActivatedRoute.paramMap`), not a
  nested `<router-outlet>` to a separate child component as this PLAN's
  routing table names suggested — there was no distinct child view to
  route to once the three-way dispatch and the ticket-id resolution both
  live in `SupportPageComponent`, so a second sibling route was simpler
  and has identical behavior.
- **`StaffSupportInboxComponent.claim()` (already committed) navigates to
  plain `/support`, not `/support/:channelId`** — it relies on
  `SupportService.activeTicket()` (a signal, already set by `claim()`
  itself) rather than a route param. `SupportPageComponent`'s channel-id
  resolution therefore prefers an explicit `:channelId` route param
  (future deep-linking) and falls back to
  `SupportService.activeTicket()?.supportChannelId` when absent, so both
  paths resolve to the same `{tenantId, memberUserId}` bridge above.
- **REQ-17's member-browse has no dedicated "pick a member" list
  component** — TASKS.md's task 99-102 scope only covers
  `MemberSupportBrowseComponent` rendering a chosen member's channel
  read-only, not a picker UI, and no such component appears in the
  "Components and routes" list above. `SupportPageComponent` therefore
  offers a plain numeric member-id input (`browse-member-id-input`)
  ahead of `MemberSupportBrowseComponent`, consistent with the SPEC's
  acceptance criterion ("can open and read another member's support
  history") without inventing a new component out of scope.
