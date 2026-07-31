# SPEC — internal-team-chat (frontend)

> The what and the why. No technical implementation details.

## Context and motivation

`knowly-app` currently has no UI for staff/members to talk to each
other — the only chat screen is the existing knowledge-base
conversation view. This SPEC adds the screens for the two new chat
shapes the backend SPEC
(`knowly-api/specify/features/internal-team-chat/SPEC.md`) introduces:
open peer-to-peer 1:1/group chat, and each member's single persistent
support channel (ticket-aware, backed by the channel's full history so
staff can recognize recurring issues). The pre-existing knowledge-base
chat screen is unaffected.

## User stories

- As any staff or tenant member, I want to see a list of my ongoing
  peer conversations and open a new 1:1 or group chat, so I can reach
  colleagues without leaving the product.
- As any staff or tenant member, I want to send and read messages in a
  1:1 or group chat, identifying every participant by their profile
  nickname, and have older messages load as I scroll back rather than
  waiting on a single giant fetch.
- As a staff user who also holds a membership in a tenant, I want to be
  offered as a candidate for that tenant's member-only group (and DM
  its members), but not be offered at all for a tenant I hold no
  membership in.
- As a `STAFF_ADMIN`, I want to look into any group conversation in my
  conversations area, even one I never joined myself, so I can exercise
  oversight — clearly presented as me looking in as support/admin, not
  as if I'd joined the group.
- As a `MEMBER_ADMIN`, I want to look into every member-only group
  belonging to a tenant I administer, even one I never joined myself,
  without that visibility leaking into tenants I don't administer, and
  without it ever appearing (to me or to the group's real participants)
  as if I've become a member.
- As a tenant member, I want a single "Support" entry point that shows
  my full history with staff, lets me see whether I currently have an
  open ticket, and lets me start a new one when my last one is closed.
- As a staff user holding the support permission, I want an inbox of
  unclaimed support channels, the ability to claim one, and — once
  claimed — to see that member's entire prior support history (not
  just the new ticket) so I can recognize a recurring or already-known
  issue before replying.
- As a tenant member holding the support-view permission, I want to
  browse other members' support histories, not just my own.

## Requirements (EARS/GEARS)

### Peer-to-peer chat

- **REQ-1 [Ubiquitous]** The system shall provide a peer conversations
  list screen, reachable by any authenticated user regardless of role,
  showing every 1:1 and group peer conversation the current user
  participates in.
- **REQ-2 [Ubiquitous]** The system shall let a user start a new 1:1
  peer conversation by selecting another user from a picker, and shall
  reflect the backend's eligibility rules (rejecting/hiding a
  staff↔member 1:1 target unless the staff user holds an active
  membership in that member's tenant) rather than re-implementing that
  rule independently in the UI.
- **REQ-3 [Ubiquitous]** The system shall let a user start a new
  peer-to-peer group conversation by selecting multiple participants,
  where eligibility is evaluated **per participant, by tenant capacity,
  matching the backend's rule exactly (not a fixed staff-vs-member
  boolean split)**:
  - When the group being created is anchored to a tenant `T`
    ("member-only group"), the participant picker shall offer every
    member of `T` **and** every staff user who additionally holds an
    active membership in `T`, and shall not offer a staff user with no
    membership in `T`.
  - When the group being created has no tenant anchor ("staff-only
    group"), the participant picker shall offer only staff users (in
    their staff capacity) and shall not offer a plain tenant member.
  - The same staff user may therefore appear as an eligible candidate
    for tenant `T`'s member-only group and simultaneously be absent
    from tenant `U`'s member-only group's candidate list, reflecting
    that eligibility is per-tenant, not a global user attribute.
  - Being added as a group participant this way is the **only** UI path
    that makes a user an actual member of a group. It is entirely
    independent of REQ-7/REQ-8's look-in override below — being able to
    look into a group never appears as an option in this picker, and
    looking into a group never adds anyone to it.
- **REQ-4 [Ubiquitous]** The system shall render every participant in
  a peer conversation (list view, chat header, per-message sender
  label) using that participant's profile nickname, never a raw email
  or internal identifier.
- **REQ-5 [Event-Driven]** When the current user sends a message in an
  open peer conversation, the system shall append it to that
  conversation's message list and clear the message composer.
- **REQ-6 [Unwanted Behavior]** If sending a peer message fails (e.g.
  network/server error), then the system shall show an inline error on
  that message and let the user retry, without silently dropping it.
- **REQ-7 [Ubiquitous]** The peer conversations list screen (REQ-1)
  shall, for a `STAFF_ADMIN` viewer, also include every group
  conversation across every tenant that the `STAFF_ADMIN` is looking
  into, whether or not that `STAFF_ADMIN` is a genuine participant, and
  shall let them open any of those groups to read its history. **This
  look-in is presented in the UI as a distinct, external oversight
  presence — never as ordinary membership**: the group's own
  participant/member list (as shown to its real participants, and
  wherever the `STAFF_ADMIN` themself views that list) never includes
  the `STAFF_ADMIN` as a result of this look-in, and the screen shall
  frame the `STAFF_ADMIN`'s own presence as something like "you're
  viewing this as support/admin oversight" rather than any "you joined"
  framing. This does not apply to 1:1 conversations the `STAFF_ADMIN`
  isn't a participant of — those never appear.
- **REQ-8 [Ubiquitous]** The peer conversations list screen (REQ-1)
  shall, for a `MEMBER_ADMIN` viewer, also include every member-only
  group belonging to any tenant where that `MEMBER_ADMIN` currently
  holds the `MEMBER_ADMIN` role, whether or not they are a genuine
  participant of that specific group, presented and framed identically
  to REQ-7's oversight presence (never as membership, never adding the
  `MEMBER_ADMIN` to that group's participant/member list). It shall
  never include a staff-only group, or a member-only group of a tenant
  they don't currently administer, or any 1:1 conversation they aren't
  a participant of.
- **REQ-9 [Unwanted Behavior]** If a `MEMBER_ADMIN` attempts to open a
  member-only group belonging to a tenant they do not currently
  administer (e.g. via a stale/guessed link), then the system shall
  reject the request the same way it would for any other non-eligible,
  non-participant user.

### Support channel

- **REQ-10 [Ubiquitous]** The system shall provide a member-facing
  "Support" screen showing the current member's single persistent
  Support Channel: the full ticket history (open and closed) and the
  currently active ticket's conversation, if any.
- **REQ-11 [State-Driven]** While the member's Support Channel has no
  open ticket, the system shall show a clearly visible action to start
  a new support ticket; while a ticket is already open, the system
  shall show that ticket's conversation instead of the start action.
- **REQ-12 [Ubiquitous]** The system shall provide a staff-facing
  Support inbox (visible only to staff holding the support-handling
  permission) listing unclaimed support channels/tickets, with an
  action to claim one.
- **REQ-13 [Event-Driven]** When a staff user claims a ticket, the
  system shall move that ticket into the staff user's own active
  support view, enable message sending in it for that staff user, and
  load the member's full Support Channel history — every prior ticket,
  open or closed, not only the newly-claimed ticket's own messages — so
  the staff user can see recurring/already-known issues before
  replying.
- **REQ-14 [State-Driven]** While a ticket is assigned to a staff user
  other than the current viewer, the system shall render that support
  channel's history as read-only for the current viewer (no composer
  shown), for any staff user or tenant member who can only view, not
  participate, per the backend's view/participate split.
- **REQ-15 [Ubiquitous]** The system shall let the staff user currently
  assigned to a ticket transfer it to another eligible staff user via
  an explicit action, after which the composer disappears for the
  original assignee and appears for the new one (with the same full
  channel history available to the new assignee).
- **REQ-16 [Event-Driven]** When a staff user closes a ticket, the
  system shall visually mark that ticket as closed (e.g. a status
  badge) and remove its message composer permanently — with no "reopen"
  action ever shown for a closed ticket.
- **REQ-17 [Ubiquitous]** The system shall provide a tenant-member-facing
  screen (gated on the support-view permission) for browsing other
  members' Support Channel histories within the same tenant, read-only.
- **REQ-18 [Ubiquitous]** The system shall render every participant in
  a Support Channel (member and any staff who has posted) using their
  profile nickname.

### Message history loading (applies to both shapes)

- **REQ-19 [Ubiquitous]** The system shall load message history for a
  peer conversation or a Support Channel progressively — an initial
  bounded page on open, followed by additional bounded pages fetched on
  demand (e.g. scroll-up / "load more") — never fetching or rendering a
  conversation's or channel's entire history in one request, matching
  the backend's paginated retrieval contract.
- **REQ-20 [State-Driven]** While an older-messages page is being
  fetched, the system shall show a loading indicator local to the
  history area rather than blocking or re-rendering the whole screen.
- **REQ-21 [Unwanted Behavior]** If loading an older-messages page
  fails, then the system shall show an inline retry action in the
  history area rather than silently giving up or discarding already-
  loaded messages.

## Non-functional requirements

- Accessibility: chat message lists, composer, claim/close/transfer
  actions, and "load more" controls must be keyboard-navigable and
  screen-reader-labeled (e.g. `aria-label`s identifying sender, action
  buttons, and loading state), matching this app's existing
  accessibility bar (WCAG AA) set for other screens. A group entered via
  the `STAFF_ADMIN`/`MEMBER_ADMIN` look-in override (REQ-7/REQ-8) must
  be visually and semantically distinguished from a group the viewer
  actually participates in — not just a small badge, but framing (copy,
  `aria-label`) that reads as "someone from support/admin is looking
  in," never as "a new member joined" — so neither the admin nor the
  group's real participants could reasonably mistake it for membership.
- Performance: conversation/message lists must use the progressive
  loading described in REQ-19 rather than fetching a member's/staff's
  entire history at once — this is a functional requirement here (see
  REQ-19/20/21), not left as an unstated implementation detail; a real
  client has already experienced this problem with unbounded history
  loads.
- Responsiveness: peer chat, support screens, and their pickers/inboxes
  must be usable at the breakpoints already supported elsewhere in
  `knowly-app` (mobile, tablet, desktop).

## Acceptance criteria

- [ ] Every role can see and open the peer conversations list with no
      permission-denied state ever shown for the ability to message.
- [ ] Starting a 1:1 with a staff/member target the current user isn't
      eligible to DM (per backend rules) is prevented or clearly
      rejected in the UI, not silently allowed.
- [ ] A staff user who **also holds a membership** in tenant `T` shows
      up as an eligible candidate in tenant `T`'s member-only group
      picker.
- [ ] A staff user with **no membership** in tenant `T` does not appear
      as a candidate in tenant `T`'s member-only group picker.
- [ ] A plain tenant member never appears as a candidate for a
      staff-only group's picker.
- [ ] The same staff user appears in tenant `T`'s member-only group
      picker and is absent from tenant `U`'s member-only group picker
      in the same test scenario, confirming per-tenant (not global)
      eligibility.
- [ ] A `STAFF_ADMIN` viewer can list and open a group (staff-only or
      member-only, any tenant) they are **not** a participant of.
- [ ] A `MEMBER_ADMIN` viewer can list and open a member-only group
      belonging to a tenant where they currently hold `MEMBER_ADMIN`,
      even when they are **not** a participant of that group.
- [ ] A `MEMBER_ADMIN` viewer cannot list or open a member-only group
      belonging to a tenant where they do **not** currently hold
      `MEMBER_ADMIN`.
- [ ] A `MEMBER_ADMIN` viewer never sees a staff-only group in their
      conversations list, and a direct attempt to open one is rejected.
- [ ] Neither a `STAFF_ADMIN` nor a `MEMBER_ADMIN` can list or open a
      1:1 conversation they are not a participant of — verified
      explicitly for both roles, confirming the oversight override
      never extends to 1:1s.
- [ ] **After a `STAFF_ADMIN` or an in-scope `MEMBER_ADMIN` opens a
      group via the look-in override, that group's displayed
      participant/member list is unchanged** — the admin does not
      appear in it (as rendered both to the admin themself and to the
      group's genuine participants), and the screen presents the
      admin's presence with distinct "looking in" copy/labeling, never
      "joined" framing.
- [ ] All participant names shown anywhere in this feature are profile
      nicknames.
- [ ] A member with no open ticket sees a "start support ticket"
      action; a member with an open ticket sees that ticket's thread
      instead.
- [ ] A staff user with the support permission sees an inbox of
      unclaimed tickets and can claim one, after which they can send
      messages in it.
- [ ] After claiming a ticket, the staff user can see the member's
      full prior support history (other tickets), not only the new
      ticket's messages.
- [ ] A staff user who is not the assigned staff sees the channel
      read-only (no composer) once a ticket is assigned to someone
      else.
- [ ] Transferring a ticket moves composer access and full-history
      visibility from the old to the new assignee.
- [ ] A closed ticket shows a closed badge, has no composer, and has no
      reopen action anywhere in the UI.
- [ ] A tenant member with the support-view permission can open and
      read another member's support history.
- [ ] Opening a conversation/channel with more messages than fit in one
      page shows only the first page initially, with a working
      "load more"/scroll-triggered action that fetches older messages
      without re-fetching or duplicating already-loaded ones.
- [ ] A failed older-messages fetch shows a retry action and does not
      remove already-loaded messages from view.

## Out of scope

- The pre-existing knowledge-base article chat screen — unrelated,
  unchanged.
- Message editing/deletion UI, read receipts, typing indicators, file/
  image attachment UI in peer or support chat.
- Push/email/browser notifications for new messages.
- Real-time transport (websocket vs. polling) — a PLAN-level technical
  decision, not specified here.
- Full-text search over history (pagination/lazy-load only).
- Any change to the existing knowledge-base conversation screen's
  permission-gating UI.
- A group's own tenant anchor (member-only group vs. staff-only group)
  being changed after creation — group "kind" is fixed at creation
  time in this SPEC; converting one kind to another is not covered.
- **Message-sending UI, and any UI implying membership, for an admin
  viewing a group solely via the REQ-7/REQ-8 look-in override while not
  otherwise a genuine participant** — mirrors the backend SPEC's
  equivalent exclusion; the override is read-only oversight, never
  presented as, and never functioning as, joining the group. No
  composer is shown, and the admin never appears in the group's
  participant/member list, unless they are also a genuine participant
  through the ordinary eligibility path (REQ-3).
