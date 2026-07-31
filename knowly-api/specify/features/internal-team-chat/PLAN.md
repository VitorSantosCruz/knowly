# PLAN — internal-team-chat (backend)

> The how. Translates the SPEC into concrete technical decisions.
> References SPEC.md.

## Architectural decisions

- **One `ChatConversation` model covers both shapes** (peer 1:1/group and
  the per-member support channel) with a `kind` discriminator
  (`PEER_DIRECT`, `PEER_GROUP`, `SUPPORT`), rather than two entity
  hierarchies — both need participants + paginated messages + tenant
  scoping, and REQ-20/21/22 (pagination) must behave identically for
  either. Support-specific state (ticket lifecycle) lives in a separate
  `SupportTicket` entity referencing the channel, not fields bolted onto
  `ChatConversation` — a peer conversation has no ticket concept at all,
  so it stays out of that table entirely. New package
  `br.com.conectabyte.knowly.chat` (sibling of `conversation`, which
  stays untouched per SPEC's out-of-scope) — deliberately not reusing
  `conversation`/`Conversation`, since that entity/table is
  member↔knowledge-base and this SPEC forbids touching it.
- **Tenant scoping**: `ChatConversation` carries a nullable `tenant_id`
  with the existing `@Filter(TenantFilter.NAME)`. `PEER_DIRECT` staff↔
  staff and staff-only groups have `tenant_id = NULL` (not tenant-owned
  data, mirrors `GlobalAccessGroup`'s no-filter precedent for genuinely
  global entities) — a `NULL` value in a `tenant_id = :tenantId` filter
  condition never matches any concrete tenant id, so a `NULL`-tenant row
  is **structurally invisible** to the tenant-scoped Hibernate filter
  regardless of which tenant is active. Every member-only/support/
  tenant-anchored row keeps `tenant_id` set and goes through the filter
  exactly like `Conversation` does today.
  - **Correction (AppSec review, 2026-07-31): this PLAN's earlier draft
    proposed a manual `Session.disableFilter(TenantFilter.NAME)` call
    inside `ChatConversationService`'s read methods, described as
    "exactly like `GlobalPermissionService`'s unfiltered entities." That
    analogy is wrong and the mechanism as drafted is disallowed:
    `GlobalPermissionService`'s entities (`DirectGlobalPermissionGrant`/
    `GlobalAccessGroup*`) simply carry no `tenant_id` and no `@Filter` at
    all — they were never filtered, so there is nothing to disable.
    `TenantFilter` on tenant-owned entities (which `ChatConversation`
    is, for every non-`NULL`-tenant row) is exclusively managed by
    `TenantFilterAspect`, a single `@Around("@annotation(...Transactional)")`
    aspect that enables/disables the filter once per transaction based
    on `TenantContext.isStaff()`/`getActiveTenantId()`. A second, manual
    `disableFilter`/`enableFilter` call from inside a service method is
    exactly the "parallel/manual scoping mechanism" this codebase's
    tenancy posture forbids (see root `DECISIONS.md`/constitution: every
    tenant-owned entity goes through the one `@Filter` mechanism, never
    a bespoke one) — it can also race with or be silently undone by the
    aspect re-enabling the filter on the next `@Transactional` boundary,
    and gives no single place to reason about "is the filter on right
    now." **Corrected design:** REQ-5a/REQ-5b's cross-tenant oversight
    read must go through `TenantFilterAspect` itself, not around it.
    Concretely: add a narrow, explicit extension point — e.g. a
    `@BypassTenantFilterForOversight` marker the aspect checks for
    *in addition to* its existing `isStaff() && activeTenantId.isEmpty()`
    condition — that disables the filter for that one `@Transactional`
    method only when the annotation is present. The annotated method
    itself must still **re-derive and verify** the caller's `STAFF_ADMIN`/
    active-`MEMBER_ADMIN`-of-target-tenant status from `TenantContext`/
    `TenantMembershipRepository` before returning any row (the annotation
    only widens what the *query* can see; it never substitutes for the
    authorization check already specified below) — so a `STAFF_ADMIN`
    or in-scope `MEMBER_ADMIN` gets correct cross-tenant visibility for
    the oversight read *regardless of whether they currently have an
    active tenant selected*, while every other tenant-owned read in this
    service continues through the aspect's normal per-transaction
    enable/disable exactly as today. This keeps "fails closed by
    default" intact and keeps `@Filter` the single, auditable place
    tenant scoping happens for every entity, this one included.
- **Participants are a separate durable table** (`chat_participants`:
  `conversation_id`, `user_id`, `joined_at`), not a JSON list on the
  conversation — REQ-5's per-tenant eligibility and REQ-7's send-rights
  check are both "is this specific user currently a participant of this
  specific conversation" queries, which need an indexed join table, not
  a blob. This is also the table REQ-5a/REQ-5b's "look-in creates no
  participant row" requirement is asserted against directly (an
  integration test just asserts no row was inserted here).
- **Admin look-in (REQ-5a/REQ-5b) is a pure read-time authorization
  branch, never a write.** `ChatConversationService.getConversation`/
  `listMessages` resolve access with: (1) is the caller a row in
  `chat_participants` for this conversation → normal access; else (2) is
  the caller `STAFF_ADMIN` (unconditional, any conversation) → oversight
  read access; else (3) is the caller an active `MEMBER_ADMIN` of the
  conversation's `tenant_id`, and the conversation is `PEER_GROUP` with a
  non-null tenant (member-only) → oversight read access; else reject.
  Both bypass branches are **re-derived per request** from
  `TenantContext.isStaffAdmin()` / a fresh `TenantMembershipRepository`
  lookup — never cached — per the SPEC's non-functional requirement.
  Because this logic lives entirely in the read path and never touches
  `chat_participants`, "oversight access" and "is a participant" stay
  independently queryable by construction, satisfying the SPEC's
  explicit data-model requirement. `sendMessage` only ever checks path
  (1) — the two bypass branches never grant send rights, matching REQ-7's
  explicit scope limit.
- **Eligibility (REQ-5) is computed at conversation-creation/participant-
  add time only, from a fresh `TenantMembershipRepository` lookup** —
  never trusted from the request body. A `ChatEligibilityService`
  encapsulates "can user X be added as a peer participant of
  conversation-with-tenant-anchor T (nullable)": staff acting in staff
  capacity is eligible only for `tenant_id = NULL` groups; any user
  (staff or member) holding an active `TenantMembership` in tenant `T`
  is eligible for `T`'s member-only group; nothing else qualifies. This
  single service backs both 1:1 creation and group add/create, so REQ-3/
  REQ-4/REQ-5's "same rule for 1:1 and group" requirement is enforced by
  sharing code, not by parallel reimplementation.
- **Support channel is modeled as a lazily-created singleton per
  member**, not eagerly on membership creation — a `chat_conversations`
  row with `kind = SUPPORT`, `tenant_id` set, and exactly one participant
  row for the owning member, created inside `SupportTicketService.
  openTicket` the first time it's needed (`INSERT ... ON CONFLICT DO
  NOTHING`-style idempotent get-or-create under a unique constraint on
  `(tenant_id, owner_user_id) WHERE kind = 'SUPPORT'`, guarding concurrent
  double-creation) — satisfies REQ-8 without a migration-time backfill
  across every existing member.
- **`SupportTicket` lifecycle** (`OPEN`, `ASSIGNED`, `CLOSED`) is its own
  entity FK'd to the channel and to the assigned staff `User` (nullable
  until claimed). REQ-10 (one open ticket at a time) is enforced by a
  partial unique index (`UNIQUE (support_channel_id) WHERE status !=
  'CLOSED'`) plus an application-level check-then-throw for a clean error
  — belt-and-suspenders, since a race on ticket creation is plausible
  under concurrent requests from the same member (double-click). REQ-16
  (no reopen, no message on a closed ticket) is enforced in
  `SupportTicketService`/`sendMessage`, not by a DB trigger — consistent
  with how `TenantMembership`'s status transitions are guarded in
  `tenant-membership-acceptance`.
- **Send-rights while a ticket is open/assigned (REQ-12b/13/18) are
  computed from the ticket, not from `chat_participants`.** The staff
  assignee is deliberately **not** added as a `chat_participants` row —
  doing so would conflate "can send into this specific ticket window"
  with "is a durable member of this channel," which the SPEC never asks
  for and which would leak into REQ-5-style eligibility if reused
  elsewhere. `sendMessage` for a `SUPPORT` conversation checks: caller is
  the channel's owning member (channel `OPEN`/`ASSIGNED` — really: the
  ticket status) OR caller is `ticket.assignedStaff` AND
  `ticket.status == ASSIGNED`. Read access (REQ-11/13/17) for the
  channel's full history is checked separately: owning member always;
  any staff with `STAFF_SUPPORT_HANDLE` always (REQ-11/13, unclaimed or
  not); any tenant member with `SUPPORT_CHANNEL_VIEW` in that tenant
  (REQ-17). Transfer (REQ-14) is a plain `ticket.assignedStaff` update
  guarded by "caller is the current assignee, holds
  `STAFF_SUPPORT_HANDLE`, and the target holds it too" — re-checked at
  transfer time, not assumed from the original claim.
- **Permissions** (per SPEC's own resolved Tier-2 call, recorded here as
  this SPEC's decision, not invented independently): add
  `SUPPORT_CHANNEL_VIEW` to the existing tenant-scoped `Permission` enum
  (gates REQ-17, checked via the existing `@RequiresPermission`/
  `PermissionAspect`, `MEMBER_ADMIN` bypass included exactly like every
  other tenant permission) and `STAFF_SUPPORT_HANDLE` to the existing
  global-scoped `GlobalPermission` enum (gates REQ-11/13/14 via the
  existing `@RequiresGlobalPermission`/`GlobalPermissionAspect`,
  `STAFF_ADMIN` bypass included). REQ-5a/REQ-5b are **not** gated by
  either — they are role checks inline in the service (see above),
  matching how `MEMBER_ADMIN`'s existing `PermissionAspect` bypass is a
  role check, not a granted permission.
- **Pagination (REQ-20/21/22): cursor-based on `id` alone, not
  `(created_at, id)`** — reconciled with the frontend PLAN
  (`knowly-app`'s `internal-team-chat` PLAN.md) during cross-PLAN
  review: `chat_messages.id` is `BIGSERIAL`, strictly monotonically
  increasing in insertion order for a given conversation, so it is
  already a total order with no same-instant tie-breaking need —
  `created_at` adds nothing `id` doesn't already give for cursor
  purposes (it stays a plain display column, just no longer part of
  the cursor). This PLAN originally proposed a compound
  `(created_at, id)` cursor defensively; on review that was
  over-specified for this schema, and a plain id-cursor is simpler for
  both sides to implement and test. `GET .../messages?before=<cursor>
  &size=<n>` returns the page of messages with `id < decoded cursor`,
  ordered `id DESC` (newest-first, omit `before` for the first/most-
  recent page); `GET .../messages?after=<cursor>&size=<n>` returns
  messages with `id > decoded cursor`, ordered `id ASC` — this second
  mode exists specifically to serve the frontend's polling decision
  (see below): "give me what's new since the last message I have,"
  which is the same cursor mechanism, not a second one. The opaque
  cursor is `base64(String.valueOf(id))`, decoded server-side.
  Offset-based pagination was rejected for both modes: it silently
  skips/duplicates rows when new messages arrive between page fetches,
  which is exactly the scrolling/polling scenario REQ-21 describes.
  Default page size 30, server-enforced max 100 (REQ-22) — `size`
  above 100 is clamped, not rejected, matching the SPEC's "cap rather
  than honor" wording; `after` mode is not size-capped the same way
  since it's expected to return a small delta, but is still clamped to
  100 defensively. Same mechanism serves both peer conversations and
  support channels via one shared `ChatMessageRepository` query
  (`findByConversationIdAndCursor`), since REQ-20 explicitly requires
  identical treatment for both shapes.
- **Real-time delivery: none in this PLAN's implementation scope beyond
  what pagination already provides — polling via the existing paginated
  GET is the delivery mechanism, explicitly deferred rather than
  invented.** This codebase's only existing push precedent
  (`MessageStreamingService`'s `SseEmitter` in `conversation/`) is a
  single-request, single-response AI-completion stream tied to one HTTP
  call's lifecycle — it is not a registry of long-lived per-user
  connections and does not solve "deliver a message written by user A to
  a currently-open session of user B." Building that (a persistent
  `SseEmitter` registry keyed by user id, or WebSocket/STOMP) is a new
  infrastructure shape with connection-lifecycle, memory, and horizontal-
  scaling implications (multiple app instances behind a load balancer
  would need pub/sub fan-out, e.g. via the already-provisioned RabbitMQ,
  to deliver to a connection held by a *different* instance) — genuinely
  Tier 3 (new dependency/pattern, real infra tradeoff), not a PLAN-level
  detail to decide unilaterally. **Recommendation for a follow-up
  decision** (flagged for `DECISIONS.md`, not written here): if/when
  real-time push is wanted, prefer SSE-per-user (not WebSocket) backed by
  RabbitMQ fan-out for multi-instance delivery, since it reuses the
  already-provisioned broker and the existing SSE precedent rather than
  adding a new protocol; ship polling first and only build this once an
  actual latency complaint exists. The SPEC explicitly places the
  transport mechanism out of scope, so this PLAN does not implement any
  push mechanism at all — a client refreshes a conversation's tail via
  the same paginated GET (`before` omitted) on an interval or on focus,
  which is a frontend-side decision, not this PLAN's. **Reconciled with
  the frontend PLAN**: the frontend's concrete near-term decision
  (5-second client polling of the paginated GET, paused on a hidden
  tab, via the new `after`-cursor mode above) is exactly this "delivery
  via the existing paginated GET" — the two PLANs are not in conflict,
  they were written from opposite ends of the same mechanism. This
  alignment, plus the future SSE-via-RabbitMQ recommendation above, is
  recorded as a `DECISIONS.md` entry (see root `DECISIONS.md`,
  `internal-team-chat` entry on real-time delivery) so a future
  conversation doesn't have to re-derive it.
- **Audit**: `@AuditLog` on every ticket lifecycle transition
  (`support.ticket.open`, `support.ticket.claim`, `support.ticket.
  transfer`, `support.ticket.close`) and on the REQ-5a/REQ-5b look-in
  read path itself (`chat.group.oversight_view`, distinct from the
  normal `chat.conversation.view` a genuine participant triggers) —
  mirrors `member-admin-tenant-bypass`'s precedent of auditing admin-
  bypass reads, and gives the acceptance criteria's "participant list
  unchanged after look-in" assertions a corresponding audit trail to
  verify against, not just a DB assertion.

## Data schema

New migration `V20__create_chat_tables.sql`:

```sql
CREATE TABLE chat_conversations (
  id BIGSERIAL PRIMARY KEY,
  kind VARCHAR(20) NOT NULL,               -- PEER_DIRECT | PEER_GROUP | SUPPORT
  tenant_id BIGINT REFERENCES tenants (id), -- NULL = staff-only peer conversation
  title VARCHAR(255),                       -- group display name; unused for PEER_DIRECT/SUPPORT
  owner_user_id BIGINT REFERENCES users (id), -- SUPPORT only: the member the channel belongs to
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by VARCHAR(255) NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by VARCHAR(255) NOT NULL
);

CREATE UNIQUE INDEX ux_chat_conversations_support_channel
  ON chat_conversations (tenant_id, owner_user_id)
  WHERE kind = 'SUPPORT';

CREATE INDEX ix_chat_conversations_tenant ON chat_conversations (tenant_id);

CREATE TABLE chat_participants (
  id BIGSERIAL PRIMARY KEY,
  conversation_id BIGINT NOT NULL REFERENCES chat_conversations (id),
  user_id BIGINT NOT NULL REFERENCES users (id),
  joined_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (conversation_id, user_id)
);

CREATE INDEX ix_chat_participants_user ON chat_participants (user_id);

CREATE TABLE chat_messages (
  id BIGSERIAL PRIMARY KEY,
  conversation_id BIGINT NOT NULL REFERENCES chat_conversations (id),
  sender_user_id BIGINT NOT NULL REFERENCES users (id),
  content TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_chat_messages_conversation_cursor
  ON chat_messages (conversation_id, created_at DESC, id DESC);

CREATE TABLE support_tickets (
  id BIGSERIAL PRIMARY KEY,
  support_channel_id BIGINT NOT NULL REFERENCES chat_conversations (id),
  status VARCHAR(20) NOT NULL DEFAULT 'OPEN', -- OPEN | ASSIGNED | CLOSED
  assigned_staff_user_id BIGINT REFERENCES users (id),
  opened_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  closed_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by VARCHAR(255) NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by VARCHAR(255) NOT NULL
);

CREATE UNIQUE INDEX ux_support_tickets_one_open_per_channel
  ON support_tickets (support_channel_id)
  WHERE status != 'CLOSED';

CREATE INDEX ix_support_tickets_channel ON support_tickets (support_channel_id);
```

`chat_conversations`/`chat_participants`/`support_tickets` get `@Audited`
+ matching `_aud` tables in the same migration (same shape as `V4`/`V11`'s
existing `_aud` pattern: nullable columns, PK `(id, rev)`). `chat_messages`
is deliberately **not** Envers-audited — matches `messages`'
(knowledge-base chat) existing precedent of not auditing message content
itself, only conversation/participant/ticket state.

`Permission.java`: add `SUPPORT_CHANNEL_VIEW`.
`GlobalPermission.java`: add `STAFF_SUPPORT_HANDLE`.
No column/enum-width change needed (both are `VARCHAR`-backed with
existing headroom, per `staff-rbac-split`'s precedent).

## API contracts

New `ChatController`, `/api/chat/**` (peer conversations; not
tenant-path-scoped like `ConversationController`, since a staff-only
group has no tenant in its URL — tenant anchoring is a conversation
property, not a route parameter):

| Method | Path | Request | Response | Status |
|---|---|---|---|---|
| POST | `/api/chat/conversations` | `{kind: DIRECT\|GROUP, tenantId?: number, title?: string, participantUserIds: number[]}` | `ChatConversationSummaryDto` | 201, 400 (ineligible participant), 403 |
| GET | `/api/chat/conversations` | — | `ChatConversationSummaryDto[]` | 200 |
| GET | `/api/chat/conversations/{id}` | — | `ChatConversationDetailDto` | 200, 403, 404 |
| GET | `/api/chat/conversations/{id}/messages?before=&after=&size=` | — | `ChatMessagePageDto` | 200, 403, 404 |
| POST | `/api/chat/conversations/{id}/messages` | `{content: string}` | `ChatMessageDto` | 201, 403 (not a sender), 404, 409 (closed ticket, if SUPPORT) |
| GET | `/api/chat/eligible-participants?scope=direct\|group\|group-staff-only&tenantId=` | — | `CandidateUserDto[]` | 200 |

New `SupportChannelController`, `/api/tenants/{tenantId}/support` (tenant-
path-scoped like `ConversationController`, since REQ-17's view permission
and REQ-9's channel are inherently tenant-owned):

| Method | Path | Request | Response | Status |
|---|---|---|---|---|
| POST | `/api/tenants/{tenantId}/support/tickets` | `{}` (caller = owning member) | `SupportTicketDto` | 201, 409 (already-open ticket) |
| GET | `/api/tenants/{tenantId}/support/tickets/unclaimed` | — | `SupportTicketSummaryDto[]` | 200, 403 (`STAFF_SUPPORT_HANDLE`) |
| POST | `/api/tenants/{tenantId}/support/tickets/{ticketId}/claim` | `{}` | `SupportTicketDto` | 200, 403, 409 (already claimed) |
| POST | `/api/tenants/{tenantId}/support/tickets/{ticketId}/transfer` | `{toStaffUserId: number}` | `SupportTicketDto` | 200, 403, 400 (target lacks permission) |
| POST | `/api/tenants/{tenantId}/support/tickets/{ticketId}/close` | `{}` | `SupportTicketDto` | 200, 403, 409 (already closed) |
| GET | `/api/tenants/{tenantId}/support/members/{memberUserId}/channel` | — | `ChatConversationDetailDto` | 200, 403, 404 |
| GET | `/api/tenants/{tenantId}/support/members/{memberUserId}/channel/messages?before=&after=&size=` | — | `ChatMessagePageDto` | 200, 403, 404 |
| POST | `/api/tenants/{tenantId}/support/members/{memberUserId}/channel/messages` | `{content: string}` | `ChatMessageDto` | 201, 403, 409 |

`ChatMessagePageDto`: `{messages: ChatMessageDto[], nextCursor: string
\| null}` — in `before` mode, `nextCursor` is the cursor for the next
older page (null signals no older page); in `after`-mode (polling),
`nextCursor` is the newest id seen in this response, for the client to
use as its next `after` value (null/omitted when the response is empty,
in which case the client keeps its previous `after` value). `messages`
is always ordered oldest→newest within a single response regardless of
mode, since that's what both the initial-render and polling-append
consumers need; only the *cursor comparison direction* (`<`/`>` against
`id`) differs between `before`/`after`. `ChatMessageDto` includes
`senderUserId`, `senderNickname` (REQ-6/REQ-19, resolved from the
identity/profile model, mirroring how `ConversationSummaryDto`/existing
DTOs already resolve nicknames), `content`, `createdAt`. `CandidateUserDto`:
`{userId, nickname}` — backs the frontend's `participant-picker`
component; `scope=direct` returns any staff-or-member candidate eligible
for a 1:1 with the caller, `scope=group&tenantId=T` returns candidates
eligible for a member-only group anchored to tenant `T` (per
`ChatEligibilityService`), `scope=group-staff-only` returns staff
candidates eligible for a staff-only group; this endpoint is a thin
read-only wrapper around the existing `ChatEligibilityService`, added on
reconciliation with the frontend PLAN since the picker has no other way
to source its candidate list without reimplementing eligibility
client-side (which REQ-3/4/5 explicitly forbid). `403`s use the existing
`PermissionDeniedException`/global exception-handler convention
(`ConversationExceptionHandler`'s shape) rather than a new one.

## Dependencies

None new (backend `pom.xml` unchanged) — reuses Spring MVC, Hibernate
Envers, existing `@Filter`/`@RequiresPermission`/`@RequiresGlobalPermission`/
`@AuditLog` infrastructure, RabbitMQ only referenced as a *future*
recommendation for real-time push, not implemented now.

## Package/file structure

New package `br.com.conectabyte.knowly.chat`:

- `ChatConversation.java`, `ChatConversationKind.java` (enum)
- `ChatParticipant.java`
- `ChatMessage.java`
- `SupportTicket.java`, `SupportTicketStatus.java` (enum)
- `ChatConversationRepository.java`, `ChatParticipantRepository.java`,
  `ChatMessageRepository.java` (custom cursor query), `SupportTicketRepository.java`
- `ChatEligibilityService.java` — REQ-3/4/5 shared rule, plus a
  read-only `listCandidates(scope, tenantId)` method backing the
  `/api/chat/eligible-participants` endpoint added on reconciliation
- `ChatConversationService.java` — create/list/get/send, admin-look-in
  branch (REQ-5a/5b), participant management
- `SupportTicketService.java` — open/claim/transfer/close (REQ-9–16),
  channel get-or-create
- `ChatController.java`, `SupportChannelController.java`
- `dto/ChatConversationSummaryDto.java`, `dto/ChatConversationDetailDto.java`,
  `dto/ChatMessageDto.java`, `dto/ChatMessagePageDto.java`,
  `dto/SupportTicketDto.java`, `dto/SupportTicketSummaryDto.java`,
  `dto/CreateChatConversationRequestDto.java`, `dto/SendChatMessageRequestDto.java`,
  `dto/CandidateUserDto.java`
- `exception/ChatAccessDeniedException.java`, `exception/SupportTicketConflictException.java`,
  `exception/ChatExceptionHandler.java`

Modified:

- `br.com.conectabyte.knowly.tenancy.Permission` — add `SUPPORT_CHANNEL_VIEW`
- `br.com.conectabyte.knowly.tenancy.GlobalPermission` — add `STAFF_SUPPORT_HANDLE`
- `src/main/resources/db/migration/V20__create_chat_tables.sql` (new)

## AppSec review notes (2026-07-31; CSRF item resolved 2026-07-31)

- **CSRF exemption inheritance via `/api/tenants/**` — RESOLVED.** The
  user decided (2026-07-31) to fix this now rather than route the new
  `SupportChannelController` endpoints around it or leave it as known
  debt. `SecurityConfig`'s CSRF `ignoringRequestMatchers` no longer
  contains the `/api/tenants/**` wildcard; it now lists the single exact
  pre-authentication path `/api/tenants/active` (the tenant-selection
  endpoint that runs immediately after login, before a full session is
  established — same request sequence as the exempted `/api/auth/**`
  endpoints). Every other endpoint nested under `/api/tenants/**` —
  `TenantController`'s own member/permission/access-group mutations,
  `ConversationController`, `ArticleController`, and this PLAN's future
  `SupportChannelController` — now correctly requires the
  `X-XSRF-TOKEN` header like every other authenticated mutating
  endpoint. Integration tests that had been implicitly relying on the
  broad exemption (`TenantManagementIntegrationTest`,
  `StaffRbacIntegrationTest`, `MembershipAcceptanceIntegrationTest`,
  `ConversationControllerIntegrationTest`, `ArticleControllerIntegrationTest`,
  `ArticleUploadSizeLimitIntegrationTest`) were updated to obtain and
  send a real CSRF cookie/header, mirroring
  `AuthControllerIntegrationTest`'s `obtainCsrfCookie()` convention,
  rather than the production behavior being loosened to match what the
  tests happened to assume. See `DECISIONS.md` (2026-07-31,
  `internal-team-chat` AppSec follow-up) for the full rationale and the
  precedent this sets: `SupportChannelController` must not assume it
  inherits CSRF exemption from this prefix when it's implemented — it
  doesn't, and shouldn't.
- **Closed-ticket immutability (REQ-16) is application-level only, no DB
  constraint** — the PLAN itself notes this is consistent with how
  `TenantMembership` status transitions are guarded elsewhere, so it's
  not a deviation from existing precedent, but it is weaker than REQ-10's
  belt-and-suspenders (partial unique index + app check). Not blocking,
  but worth a defensive DB check (e.g. a partial index or trigger
  rejecting inserts referencing a `CLOSED` ticket) if this ever needs to
  survive a bug in `SupportTicketService`, not just a passing test suite.

## Testing strategy

- Unit tests: `ChatEligibilityService` (every REQ-3/4/5 branch —
  staff-no-membership rejected, staff-with-membership accepted per-tenant,
  plain member rejected from staff-only group, same staff user
  accepted/rejected across two different tenants in one test run per the
  acceptance criteria); `SupportTicketService` (REQ-10 double-open
  rejection, REQ-15/16 closed-ticket terminality, REQ-14 transfer moving
  send/history rights); cursor encode/decode round-trip and page-size
  clamping (REQ-22) in isolation.
- Integration tests (`@SpringBootTest`, Testcontainers, mirrors
  `ConversationControllerIntegrationTest`):
  - Full REQ-1–REQ-7 matrix from the acceptance criteria: 1:1 creation
    across all four role combinations, group creation eligibility
    per-tenant, `STAFF_ADMIN`/`MEMBER_ADMIN` look-in on groups they
    aren't participants of with an explicit post-look-in assertion that
    `chat_participants` is unchanged and eligibility re-query is
    unaffected, look-in never applying to 1:1 or to out-of-scope tenants/
    staff-only groups.
  - Full support-channel lifecycle (REQ-8–19): lazy channel creation,
    one-open-ticket enforcement, claim/transfer/close send-rights
    transitions, `STAFF_SUPPORT_HANDLE`/`SUPPORT_CHANNEL_VIEW` gating,
    full-history visibility across multiple closed tickets after a new
    claim.
  - Pagination (REQ-20–22): seed a conversation/channel past one page,
    assert first response is bounded, `nextCursor` walks backward
    correctly with no gap/duplicate even when a new message is inserted
    between two page fetches, oversized `size` is clamped not rejected.
  - Tenant isolation: a staff-only conversation is invisible under any
    active tenant filter context; a member-only group of tenant `A` is
    unreachable (403/404, not leaked) to a member of tenant `B`.
  - Audit: ticket-lifecycle and oversight-view `@AuditLog` entries are
    written (`AuditEventWriter`'s `REQUIRES_NEW` semantics already cover
    read-only-transaction safety, reused as-is).
