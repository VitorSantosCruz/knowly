# SPEC — internal-team-chat (backend)

> The what and the why. No technical implementation details.

## Context and motivation

Today, `knowly`'s only chat surface is member-to-knowledge-base
(`conversations`, permission-gated for `MEMBER`s). Staff and tenant
members have no way to talk to each other or to one another as peers
inside the product — everything happens outside it (email, other
tools). This feature adds an **internal team chat** layer with two
distinct shapes:

- **(a) Open peer-to-peer chat** — 1:1 and group conversations among
  staff-with-staff or member-with-member, with no permission gate on
  the ability to talk (distinct from the knowledge-base chat, which
  stays permission-gated for `MEMBER`s).
- **(b) A single, fixed, per-member support channel** — every tenant
  member has exactly one persistent channel with "the staff team,"
  inside which bounded support tickets open and close over time. The
  channel — not the ticket — is the unit of history specifically so
  that whichever staff member picks up a *new* ticket for a member can
  see that member's prior tickets in the same channel: this is meant
  to let staff catch recurring/already-known issues, not just serve as
  a record-keeping nicety.

**(c) The pre-existing member↔knowledge-base article chat is out of
scope** — already shipped, unrelated to this feature, unchanged.

This SPEC covers only backend behavior (data ownership, business
rules, permission gating). The consuming screens are specified
separately in `knowly-app/specify/features/internal-team-chat/SPEC.md`
per this project's cross-folder SPEC placement rule.

## User stories

- As any staff or tenant member, I want to start a private 1:1 chat
  with another staff/member peer, without needing any special
  permission, so I can communicate directly inside the product.
- As any staff or tenant member, I want to create a group chat with
  other eligible peers, so my team can coordinate together.
- As a staff user who also holds a membership in a tenant, I want to
  DM a member of that tenant as a peer, or join that tenant's
  member-only group, the same way any other member of that tenant
  could — but not be able to do either for a tenant I hold no
  membership in.
- As a `STAFF_ADMIN`, I want to be able to look into any group
  conversation across any tenant, even one I'm not a participant of, so
  I can exercise oversight — without that look-in ever making me a
  member of the group.
- As a `MEMBER_ADMIN`, I want to look into every member-only group
  belonging to a tenant I administer, even one I didn't join myself,
  without that look-in either granting me membership or leaking into a
  tenant I don't administer.
- As a tenant member, I want one persistent place to reach "support"
  that keeps the full history of everything I've ever asked, even
  across multiple separate issues over time.
- As a staff user holding the support permission, I want to see
  unclaimed support channels, pick one up, and — critically — see that
  member's prior support history so I can recognize a recurring issue
  before replying.
- As a tenant member holding the relevant permission, I want to review
  the full support history for my organization's members, not just the
  threads I personally opened.
- As any chat participant, I want old messages to load progressively as
  I scroll back, not all at once, so long-running conversations stay
  fast to open.

## Requirements (EARS/GEARS)

### Peer-to-peer chat (shape a)

- **REQ-1 [Ubiquitous]** The system shall allow any authenticated user
  (`STAFF_ADMIN`, `STAFF`, `MEMBER_ADMIN`, `MEMBER`) to initiate a
  peer-to-peer 1:1 or group conversation with no permission check
  beyond being authenticated — messaging itself is never
  permission-gated.
- **REQ-2 [Ubiquitous]** The system shall treat a peer-to-peer 1:1
  conversation as private: visible and writable only to its two
  participants. No admin-override exists for 1:1 conversations (see
  REQ-5a/REQ-5b, which apply to groups only).
- **REQ-3 [Unwanted Behavior]** If a `STAFF`/`STAFF_ADMIN` user
  attempts to open a 1:1 peer conversation with a
  `MEMBER`/`MEMBER_ADMIN` user, and that staff user does not hold an
  active membership in the same tenant as the target member, then the
  system shall reject the request.
- **REQ-4 [Ubiquitous]** The system shall allow a staff user who holds
  an active membership in a given tenant to open a 1:1 peer
  conversation with a member of that same tenant, treating the staff
  user as an ordinary peer member for that conversation (not in their
  staff capacity).
- **REQ-5 [Ubiquitous]** The system shall determine peer-to-peer group
  eligibility **per participant, by the capacity they act in relative
  to that specific group**, using exactly the same eligibility rule as
  1:1 chat (REQ-3/REQ-4) rather than a separate policy:
  - A group anchored to a tenant `T` ("member-only group") may include
    any member of `T`, **and** any staff user who additionally holds an
    active membership in `T` (that staff user participates as a peer
    member of `T`, same exception as REQ-4). It may never include a
    staff user with no active membership in `T` (acting purely in their
    staff capacity).
  - A group with no tenant anchor ("staff-only group") may include only
    staff users acting in their staff capacity. It may never include a
    plain tenant member (a `MEMBER`/`MEMBER_ADMIN` with no staff
    capacity).
  - The same staff user can therefore be eligible for tenant `T`'s
    member-only group (because they hold a membership in `T`) while
    being ineligible for tenant `U`'s member-only group (no membership
    in `U`) — eligibility is evaluated per tenant, not as a fixed,
    global role flag on the user.
  - This eligibility rule governs group **participation** (being added
    as a member of the group, i.e. holding a durable participant
    record). It is distinct from, and the *only* path into, actual
    membership — it is never granted or implied by the admin-oversight
    visibility rules below (REQ-5a/REQ-5b).
- **REQ-5a [Ubiquitous]** The system shall allow a `STAFF_ADMIN` user to
  look into — see and read the message history of — **any**
  peer-to-peer group conversation, staff-only or member-only, belonging
  to any tenant, regardless of whether that `STAFF_ADMIN` is a
  participant, mirroring `STAFF_ADMIN`'s existing unconditional
  authorization bypass used elsewhere in this system (e.g.
  `PermissionAspect`). **This look-in creates no durable
  membership/participant record**: the system shall not add the
  `STAFF_ADMIN` to the group's participant list, shall not emit any
  "member joined"-shaped event, and shall not treat the `STAFF_ADMIN`
  as a group participant for any purpose other than this read-only
  oversight access itself (e.g. not for REQ-5's eligibility, not for
  REQ-7's send rights). This override applies to groups only, never to
  1:1 conversations (REQ-2).
- **REQ-5b [State-Driven]** While a `MEMBER_ADMIN` currently holds an
  active `MEMBER_ADMIN` role in tenant `T`, the system shall allow that
  user to look into — see and read the message history of — every
  member-only group belonging to tenant `T`, regardless of whether they
  are a participant of that specific group — scoped strictly to tenants
  they currently administer, not global like REQ-5a. **This look-in
  creates no durable membership/participant record**, identically to
  REQ-5a: no addition to the group's participant list, no "member
  joined" event, no participant status for any other purpose. This
  override applies to member-only groups only: it grants no visibility
  into a staff-only group, and no visibility into any group
  (member-only or otherwise) of a tenant the user does not administer,
  and never into a 1:1 conversation (REQ-2). **The only way a
  `MEMBER_ADMIN` (or any staff user) becomes an actual participant of a
  tenant's member-only group is by satisfying REQ-5's eligibility rule
  directly (holding a genuine membership in that tenant) — REQ-5a/REQ-5b
  oversight access and REQ-5 participation are two fully independent
  mechanisms; neither implies or grants the other.**
- **REQ-6 [Event-Driven]** When a peer conversation (1:1 or group) is
  created, the system shall persist every participant's identity and
  make each participant's current profile nickname (from the
  identity/profile model) available for display.
- **REQ-7 [Ubiquitous]** The system shall allow any current participant
  of a peer-to-peer group conversation to send messages to it; the
  system shall not require any participant to hold a separate
  permission to do so. This SPEC does not extend message-**sending**
  rights to an admin exercising the REQ-5a/REQ-5b look-in override
  while not otherwise a genuine participant — see "Out of scope."

### Support channel (shape b)

- **REQ-8 [Ubiquitous]** The system shall maintain exactly one
  persistent Support Channel per tenant member (`MEMBER`/
  `MEMBER_ADMIN`), created no later than the first time that member
  opens a support ticket.
- **REQ-9 [Event-Driven]** When a member needs support and has no
  currently-open ticket in their Support Channel, the system shall
  create a new Support Ticket **inside that member's existing Support
  Channel** — never a new channel.
- **REQ-10 [Unwanted Behavior]** If a member attempts to open a new
  support ticket while another ticket in their Support Channel is
  still open (not `CLOSED`), then the system shall reject the request
  and direct the member to the existing open ticket rather than
  creating a second, concurrent one.
- **REQ-11 [State-Driven]** While a Support Ticket is unclaimed
  (`OPEN`, not yet assigned), the system shall allow any staff user
  holding the support-handling permission to view the Support
  Channel's full message/ticket history and to claim (accept) the
  ticket.
- **REQ-12 [Event-Driven]** When a staff user accepts an unclaimed
  ticket, the system shall (a) assign that ticket to that staff user,
  (b) for the duration of that ticket, restrict message-**sending** in
  the channel to exactly two people — the member who owns the channel
  and the staff user currently assigned — and (c) make available to
  that assigned staff user the member's **entire Support Channel
  history**, including every prior ticket (open or closed), not only
  the messages belonging to the newly-claimed ticket, so a recurring or
  already-known issue is recognizable before the staff user replies.
- **REQ-13 [State-Driven]** While a ticket is assigned, every other
  staff user holding the support-handling permission shall retain
  read-only visibility of the channel's full history but shall not be
  able to send messages in it.
- **REQ-14 [Ubiquitous]** The system shall allow the staff user
  currently assigned to an open ticket to transfer it to a different
  staff user holding the support-handling permission; on transfer, the
  previous assignee loses send access and the new assignee gains it,
  including the same full-channel-history visibility described in
  REQ-12(c).
- **REQ-15 [Event-Driven]** When a staff user closes a ticket, the
  system shall mark it `CLOSED`, permanently stop it from accepting new
  messages, and retain it — unchanged — in the channel's history.
- **REQ-16 [Unwanted Behavior]** If any user attempts to reopen a
  `CLOSED` ticket or to send a message associated with a `CLOSED`
  ticket, then the system shall reject the action; a new support need
  from the same member shall only ever be satisfiable by REQ-9 (a new
  ticket in the same channel).
- **REQ-17 [Ubiquitous]** The system shall allow any tenant member
  (not only the channel's opener) who holds the
  tenant-scoped Support Channel view permission to view that member's
  Support Channel's full history (every ticket, open or closed),
  regardless of who opened it.
- **REQ-18 [Ubiquitous]** The system shall allow message-sending in a
  Support Channel only from: (a) the member who owns the channel,
  while the channel's current ticket is `OPEN` or `ASSIGNED`, and (b)
  the staff user currently assigned to that ticket. No one else may
  send messages into the channel, regardless of view access.
- **REQ-19 [Ubiquitous]** The system shall make each participant's
  current profile nickname (from the identity/profile model) available
  for display wherever a Support Channel or its tickets are shown.

### Message history retrieval (applies to both shapes)

A Support Channel accumulates every ticket a member has ever had, and a
long-running peer conversation can accumulate an unbounded number of
messages — retrieving either in one unpaginated response is a real,
already-observed performance problem (a real client has hit this), not
a hypothetical one.

- **REQ-20 [Ubiquitous]** The system shall retrieve message history —
  for peer conversations and for Support Channels alike — through a
  paginated/lazy-loading mechanism, never returning a conversation's or
  channel's entire message history in a single unbounded response. The
  exact pagination mechanism (e.g. cursor-based, offset-based, page
  size) is a PLAN-level technical decision, not specified here.
- **REQ-21 [Event-Driven]** When a client requests older messages for a
  peer conversation or a Support Channel it has access to, the system
  shall return the next bounded page of history (oldest-appropriate
  ordering for "load more" scrolling) rather than requiring the client
  to already know a full offset/cursor position server-side state
  doesn't otherwise expose.
- **REQ-22 [Unwanted Behavior]** If a client requests a page size above
  a server-enforced maximum, then the system shall cap it rather than
  honor an arbitrarily large request.

## Non-functional requirements

- Security: peer-to-peer conversations remain isolated to their
  participants only (no cross-tenant/cross-conversation leakage);
  Support Channel data stays scoped to its owning tenant via the
  existing Hibernate tenant filter, matching every other tenant-owned
  entity in this codebase; staff bypass authorization exactly as
  documented in `DECISIONS.md` ("Staff can act as any tenant without
  holding a membership") but never bypass isolation. Group-eligibility
  checks (REQ-5) must re-derive each participant's capacity
  (tenant-membership lookup) at group-creation/membership-change time,
  not trust a client-supplied "I'm eligible" flag. The REQ-5a/REQ-5b
  admin-oversight override must be re-derived from the caller's actual
  current role (`STAFF_ADMIN`) or current active `MEMBER_ADMIN`
  membership in the target tenant at access time — never cached or
  inferred from a stale session claim — and must never be checked
  against 1:1 conversations (REQ-2's isolation must hold even for these
  two admin roles). The look-in access itself must be implemented as a
  read path that bypasses the participant check without ever writing a
  participant/membership row — the data model must make "can currently
  read this group as an oversight admin" and "is a participant of this
  group" independently and separately queryable, so no code path can
  accidentally treat the former as proof of the latter.
- Permissions: this feature introduces two new permissions rather than
  reusing an unrelated existing one (no existing permission maps
  cleanly to "support channel access") — a tenant-scoped
  `SUPPORT_CHANNEL_VIEW` (gates REQ-17 for tenant members) and a
  global-scoped `STAFF_SUPPORT_HANDLE` (gates REQ-11/REQ-13/REQ-14 for
  staff), following the same `Permission`/`GlobalPermission` split
  already established by `staff-rbac-split`. This is a Tier 2 judgment
  call (no scope/security-tradeoff/new-dependency implication) — record
  it in the PLAN as this SPEC's decision, not a silent default. REQ-5a/
  REQ-5b are role-based bypasses (`STAFF_ADMIN`/active `MEMBER_ADMIN`),
  not gated by either new permission, mirroring how those two roles
  already bypass `PermissionAspect`/`requireAdminOfTenantOrStaff`
  elsewhere in this codebase.
- Observability: ticket lifecycle transitions (open, assign, transfer,
  close) should be auditable consistently with this codebase's existing
  `@AuditLog` pattern for other significant state changes. An admin
  looking into a group via the REQ-5a/REQ-5b override (as opposed to as
  a genuine participant) is a reasonable candidate for the same
  `@AuditLog` treatment, given this codebase's existing precedent of
  auditing admin-bypass actions (e.g. `MEMBER_ADMIN`'s
  `PermissionAspect` bypass) — a PLAN-level decision, not mandated
  further here.
- Performance/SLA: message history retrieval (REQ-20/21/22) must not
  degrade as a Support Channel's ticket count or a peer conversation's
  message count grows — this is the specific, already-observed problem
  this SPEC is guarding against, not a generic aspiration; the PLAN
  must pin down a concrete pagination mechanism and page-size cap
  before implementation, not defer it indefinitely.

## Acceptance criteria

- [ ] Any of the four roles can start a 1:1 or group peer conversation
      with no permission check.
- [ ] A staff↔member 1:1 is rejected unless the staff user holds an
      active membership in the member's tenant.
- [ ] A staff user with an active membership in a tenant can DM a
      member of that tenant as a peer.
- [ ] A staff user who **also holds an active membership** in tenant
      `T` can join/be added to tenant `T`'s member-only group.
- [ ] A staff user who does **not** hold a membership in tenant `T`
      (acting purely in staff capacity) is rejected when attempting to
      join/be added to tenant `T`'s member-only group.
- [ ] A plain tenant member (no staff role at all) is rejected when
      attempting to join/be added to a staff-only group, under any
      circumstance.
- [ ] The same staff user is accepted into tenant `T`'s member-only
      group (holds a membership there) and rejected from tenant `U`'s
      member-only group (holds no membership there) in the same test
      run — proving eligibility is evaluated per tenant, not as a
      global flag.
- [ ] A `STAFF_ADMIN` who is **not** a participant of a given group
      (staff-only or member-only, any tenant) can still list/open it
      and read its history.
- [ ] A `MEMBER_ADMIN` who is **not** a participant of a member-only
      group belonging to a tenant they currently administer can still
      list/open it and read its history.
- [ ] A `MEMBER_ADMIN` is rejected from listing/opening a member-only
      group belonging to a tenant they do **not** currently administer,
      even if they hold `MEMBER_ADMIN` elsewhere.
- [ ] A `MEMBER_ADMIN` is rejected from listing/opening a staff-only
      group under any circumstance.
- [ ] Neither a `STAFF_ADMIN` nor a `MEMBER_ADMIN` can open a 1:1
      conversation they are not a participant of — the admin overrides
      apply to groups only, verified explicitly for both admin roles.
- [ ] **After a `STAFF_ADMIN` looks into a group they are not a
      participant of, that group's participant/member list is
      unchanged** — the `STAFF_ADMIN` does not appear in it, no
      "member joined" event was recorded, and re-querying REQ-5's
      eligibility for that `STAFF_ADMIN` against that group still
      reflects their pre-look-in status.
- [ ] **After an in-scope `MEMBER_ADMIN` looks into a member-only group
      of a tenant they administer but are not a participant of, that
      group's participant/member list is unchanged** — same assertions
      as the `STAFF_ADMIN` criterion above.
- [ ] Every tenant member has exactly one Support Channel, lazily
      created on first ticket.
- [ ] Opening a new support need after a prior ticket closed creates a
      new ticket inside the same channel, not a new channel.
- [ ] A member cannot open a second concurrent ticket while one is
      already open.
- [ ] Any staff user with `STAFF_SUPPORT_HANDLE` can view an unclaimed
      ticket's channel history and claim it.
- [ ] After a staff user claims a ticket, only that staff user and the
      channel's member can send messages; other staff retain read-only
      access.
- [ ] After claiming (or being transferred) a ticket, the assigned
      staff user can retrieve the member's full channel history —
      every prior ticket, open or closed — not only the newly-claimed
      ticket's own messages.
- [ ] An assigned ticket can be transferred to a different staff user,
      moving send access and full-history visibility accordingly.
- [ ] Closing a ticket is terminal: no further messages, no reopening;
      history remains visible.
- [ ] Any tenant member holding `SUPPORT_CHANNEL_VIEW` can view any
      member's Support Channel history in their tenant, not only their
      own.
- [ ] Participants are displayed using their profile nickname
      everywhere peer/support conversations render identity.
- [ ] Retrieving message history for a peer conversation or a Support
      Channel never returns the entire history in a single response —
      older messages are fetched via an additional, bounded request
      (e.g. "load more"/scroll-triggered), verified against a
      conversation/channel seeded with a message count well beyond one
      page.
- [ ] A page-size request above the server-enforced cap is capped, not
      honored as-is.

## Out of scope

- The pre-existing member↔knowledge-base article chat (`conversations`
  feature) — unrelated, unchanged.
- Message editing/deletion, read receipts, typing indicators, file/
  image attachments in peer or support chat.
- Push notifications or email alerts for new messages (any future need
  for this is a separate feature).
- Real-time transport mechanism (websocket/SSE/polling) — a PLAN-level
  technical decision, not a business requirement.
- Cross-tenant peer-to-peer member groups (a member-only group is
  anchored to exactly one tenant; a member of tenant `T` cannot be in
  tenant `U`'s member-only group unless they separately hold a
  membership in `U` too, in which case they're eligible for `U`'s group
  the same way any of `U`'s own members are).
- Any change to the existing `conversations` (knowledge-base chat)
  permission model.
- Any search/filter over a Support Channel's or peer conversation's
  history (pagination only, no full-text search).
- **Message-sending by an admin exercising the REQ-5a/REQ-5b look-in
  override while not otherwise a genuine group participant.** Both
  requirements are explicitly a *read-only, non-membership* oversight
  override ("look into," never "join"); this SPEC does not extend send
  rights alongside it, and does not grant the override any effect on
  the group's actual participant/membership records. If a future need
  arises for an oversight admin to post into a group they aren't a
  member of, or for the override to itself grant membership, that is a
  distinct, separate product decision — not folded in silently here.
