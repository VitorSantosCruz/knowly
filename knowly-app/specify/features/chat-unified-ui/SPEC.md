# SPEC — chat-unified-ui (frontend)

> The what and the why. No technical implementation details.
>
> **Status: APPROVED FOR PLAN.** All Tier 3 items are resolved — see
> "Tier 3 — resolved" below, including the group-admin authorization
> model closed by the companion backend SPEC
> (`chat-group-membership-management`). Ready to hand to
> `software-architect` for PLAN.md.
>
> **This document amends two already-shipped, approved SPECs:**
> - `knowly-app/specify/features/internal-team-chat/SPEC.md` (119/119
>   tasks done, 2026-07-31) — specifically its REQ-1/REQ-2/REQ-3 (separate
>   "Nova conversa" picker flow) and its "Out of scope" line "The
>   pre-existing knowledge-base article chat screen — unrelated,
>   unchanged" (now superseded — see Tier 3 resolution #1 below).
> - `knowly-app/specify/features/conversations/SPEC.md` — specifically its
>   REQ-1 ("at the `/conversations` route", a dedicated top-level screen,
>   now folded under the shared navigation shell — content/behavior
>   unchanged).
>
> Once PLAN.md work starts, both `internal-team-chat/SPEC.md` and
> `conversations/SPEC.md` should have their affected lines edited in
> place to point at this document, per `constitution.md`'s "Approved
> applies to changing an existing SPEC's scope" rule — this SPEC is that
> approval trail.
>
> **Backend dependency, specified separately, not defined here:**
> `chat-group-membership-management` (backend) now covers: adding a
> participant to an existing group, removing a participant, leaving a
> group, joining a Public group, requesting to join a
> Com-solicitação-de-entrada group, approving/rejecting a join request,
> changing a group's visibility type, and deleting a group — all gated
> by a new per-conversation **group admin** role (creator becomes admin
> automatically, can promote other participants, auto-promotion if a
> group is left with no admin). This frontend SPEC does not redefine
> that authorization model — it only consumes those endpoints and
> reflects whatever admin/non-admin capability the backend reports for
> the current viewer.

## Context and motivation

The product owner reviewed the current chat screen (sidebar "Conversas"
with a separate "Nova conversa" button and an empty "Nenhuma conversa
ainda." list) and identified four UX problems with the flow that shipped
under `internal-team-chat`:

1. There is no way to search for a person or a group to talk to.
2. Starting a conversation requires leaving the main list into a
   separate "new conversation" flow, instead of just clicking who you
   want to talk to.
3. There is no dedicated "create group" action (name it, enter it, add
   people afterward) — group creation today is folded into the same
   picker as 1:1 (`internal-team-chat` REQ-3), which doesn't match how
   the owner wants group creation to feel.
4. "Chat" (peer/team chat), "Support", and "Conversas" (the RAG chat
   over the knowledge base) are separate screens/routes today; the owner
   wants one navigation surface where the user picks between talking to
   a person, a group, their Support channel, or the article-grounded
   assistant.

**Important framing, confirmed by the product owner (see Tier 3
resolution #1 below): "one screen" means a shared navigation
surface/shell only.** The four kinds of conversation (1:1, group,
Support, RAG) remain logically separate sections, each keeping its own
existing content, behavior, and permission model exactly as already
specified by `internal-team-chat`/`conversations`. This SPEC never
merges their behavior — it only removes the need to jump between
separate top-level screens/routes to reach any of them.

**Follow-up scope addition, confirmed by the product owner (2026-08-08):**
groups now have a **visibility type**, chosen at creation, which changes
both the "Criar grupo" flow (REQ-13) and group search behavior
(REQ-8/REQ-9) — see the "Group visibility and discovery" section below.

**Deferred, at the product owner's instruction (2026-08-08): full-text
search over message content** (searching by a snippet of what was
actually said, with sender/conversation/date filters) is **not** part of
this SPEC. This feature's search (REQ-8) is limited to matching a
person's or group's **display name**, not message contents. See
"Out of scope / Future work" for the reasoning.

## Tier 3 — resolved (product owner, 2026-08-08)

These were stop-and-ask items per `DECISIONS.md`'s decision-making
authority section, all now answered directly by the product owner (or,
for #4, closed by the companion backend SPEC's own approved decision).
No Tier 3 item remains open in this document.

1. **Reversing `internal-team-chat`'s "unrelated, unchanged" boundary
   against the knowledge-base chat screen — confirmed, with an explicit
   scope clarification.** The merge is approved, but only at the
   navigation/surface level: RAG chat, 1:1 chat, group chat, and Support
   remain logically separate sections with their own existing
   content/behavior — nothing about how they work internally is being
   merged. REQ-2 below reflects this exactly (four distinct sections,
   each unchanged in behavior).
2. **Support is included in the unified screen — confirmed.** It becomes
   a fourth sidebar section, keeping its existing claim/transfer/close
   permission model and screens exactly as specified in
   `internal-team-chat`'s SPEC (REQ-10 through REQ-18) — only its entry
   point moves under the shared navigation shell.
3. **"Add participant to an existing group" is specified as a separate
   backend SPEC amendment to `internal-team-chat`.** Now folded into the
   broader `chat-group-membership-management` backend SPEC (see #4) —
   this frontend SPEC does not redefine or duplicate that authorization
   logic; REQ-14/REQ-15 describe the frontend's consumption of whatever
   endpoint that backend SPEC produces.
4. **Who is authorized to approve/reject a join request, add/remove a
   participant, change a group's visibility, or delete a group is now
   defined by the approved backend SPEC
   `chat-group-membership-management`: a per-conversation "group admin"
   role.** The creator of a group becomes its admin automatically; an
   admin can promote other participants to admin; if a group is left
   with no admin (e.g. the only admin leaves), the backend
   auto-promotes someone to keep every group in a valid state. This
   frontend SPEC does not re-specify that model — every requirement
   below that depends on "who may do X" (REQ-14, REQ-18/REQ-23/REQ-24,
   and any visibility-change/group-delete action) is written to depend
   on a backend-reported admin/capability flag for the current viewer,
   never on a frontend-side role check.

## User stories

- As any user, I want one place to go for "talk to someone," whether
  that's a specific coworker, a group, Support, or the article-grounded
  assistant, instead of remembering which of several screens has what I
  need.
- As any user, I want to search by name to find the person or group I
  want to talk to, instead of scanning an unsorted list.
- As any user, I want to just click a person's name to start or resume
  talking to them, without going through a separate "new conversation"
  step first.
- As any user, I want a dedicated "create group" action where I name the
  group, choose who can find/join it, land inside it immediately (as its
  admin), and add more people once it exists.
- As a user creating a group, I want to control whether it's private
  (invite-only), discoverable-but-gated (join requests need approval),
  or fully open (anyone eligible can join instantly).
- As a user browsing groups, I want to find and request to join (or
  directly join, if public) a group I'm not yet part of, without needing
  someone to invite me first.
- As a group admin, I want to approve/reject join requests, remove a
  participant, promote another participant to admin, or change the
  group's visibility, from inside the group's own view.
- As a group participant (admin or not), I want to leave a group myself.

## Requirements (EARS/GEARS)

### Unified navigation surface

> **Amended 2026-08-09** after the first shipped cut of this screen (tab
> strip that swaps the entire main panel per section) was tried by the
> product owner and reported as unintuitive — reaching a person/group AND
> checking prior history both took too many clicks, and the tab strip hid
> most of the screen's own value at any given moment. Direction given:
> follow the layout pattern already proven intuitive by established
> messaging apps (WhatsApp Web, Telegram Web, Slack) — a persistent list
> column with search and direct new-conversation/new-group actions, never
> hidden behind a tab, alongside a persistent conversation column — rather
> than reinvent one. REQ-1/REQ-2/REQ-3/REQ-5/REQ-6/REQ-7 below replace the
> tab-strip design; REQ-4/REQ-8 through REQ-32 (search, group
> creation/visibility/admin) are unaffected in behavior, only in where
   they're anchored on screen.
>
> **Amended (2) 2026-08-09, same day** — a first cut of the amendment
> above shipped as **3** persistent columns (directory, conversation, and
> a new "contacts" column with a "já falou"/"ainda não falou" partition
> of the same person/group data). The product owner liked the
> already-talked-to/haven't-talked-yet partitioning idea but found a
> separate 3rd column redundant with the directory column's own People
> rows. **Final direction: 2 columns, not 3** — the partitioning idea
> moves *into* the directory column's People section (replacing its flat
> list), instead of living in its own column. REQ-1/REQ-2/REQ-2a/REQ-2b/
> REQ-2c below reflect this final, 2-column shape; there is no REQ-2b
> "contacts column" anymore — see REQ-2 (amended) for where that
> partitioning now lives. Groups/Support/"Base de artigos" are unaffected
> by this second amendment, only People's own presentation.
>
> **Follow-up UX fixes, same day, reported by a tester on the shipped
> 2-column cut (not requiring further re-architecture):** (a) the 3 quick
> actions (REQ-2) — "Abrir chamado de suporte" and "Falar com a base de
> artigos" are tenant-scoped actions and must not be offered to a staff
> viewer with no active tenant selected (mirrors the existing "no active
> tenant" gating `conversations`' own SPEC already applies to the RAG
> section); "Criar grupo" stays unconditional since a staff-only group is
> valid independent of any tenant. (b) The directory list's always-present
> Support row and the "Abrir chamado de suporte" action must use visibly
> distinct labels (a duplicate-looking "Abrir chamado de suporte" appeared
> twice on screen — a labeling bug, not an intentional second entry
> point). (c) "Criar grupo" needed a clearer button affordance (an icon,
> not just color, since its original border-less style read as a
> permanently-"selected" list row to at least one tester). (d) Each row
> that opens the currently-open conversation/group/Support/RAG view shows
> a visual "active" state, and each person row shows their avatar
> (`UserProfile.avatarUrl`, falling back to a generic icon — see
> `PLAN.md`'s note on `CandidateUserDto` not yet carrying `avatarUrl`,
> a tracked backend follow-up, not implemented here). (e) The "already
> talked to" partition sorts most-recently-active first, proxied by
> conversation id (descending) until the backend exposes a real
   `lastMessageAt`/activity timestamp (tracked follow-up, see PLAN.md) —
   and, critically, this sort never reacts to which row is merely
   selected/open, only to genuine conversation data, so opening a
   conversation cannot itself reorder the list.

- **REQ-1 [Ubiquitous]** The system shall provide a single top-level
  navigation entry ("Conversas") that replaces the previously separate
  `/chat`, `/support`, and `/conversations` entries, opening one screen
  laid out as **two** persistent columns — a directory column and a
  conversation column (REQ-2a) — instead of a sidebar that swaps the
  main panel's entire content per section.
- **REQ-2 [Ubiquitous]** The directory column (leftmost) shall always
  show, simultaneously and without a tab/section switch: (a) three
  direct action buttons — "Abrir chamado de suporte", "Falar com a base
  de artigos", "Criar grupo" — gated per the tenant-scoping note below,
  and (b) a unified list combining People, Groups, the existing Support
  channel/ticket entry point, and existing "Base de artigos"
  conversations. This list is never itself hidden behind a section tab;
  it is the directory column's permanent content.
  - **People rows** — every user the current viewer is eligible to
    message 1:1, per `internal-team-chat`'s existing eligibility rules
    (REQ-2), each with their existing/most-recent 1:1 conversation if
    one exists. **Amended (2) 2026-08-09**: rather than one flat list,
    People rows are partitioned into two groups, each with its own
    independent search field: **"Already talked to"** (an existing 1:1
    conversation exists) and **"Haven't talked yet"** (eligible, no
    conversation yet) — sorted most-recently-active first within each
    partition, same "established messaging app" framing as before, this
    is purely a presentation split of the same eligibility data, not a
    new backend concept. Clicking a row in either partition behaves
    identically (REQ-3). Each row also shows the person's avatar
    (falling back to a generic icon when unavailable — see PLAN.md's
    note on the current `avatarUrl` data gap).
  - **Group rows** — every group conversation the viewer is a
    participant of, plus (see "Group visibility and discovery" below)
    discoverable groups the viewer isn't yet part of, plus
    `STAFF_ADMIN`/`MEMBER_ADMIN` look-ins per `internal-team-chat`'s
    existing REQ-1/REQ-7/REQ-8, unchanged — kept as a single list with
    its own single search field (REQ-8), not partitioned like People.
  - **Support row(s)** — the viewer's own existing Support channel/ticket
    (member), or the staff unclaimed-inbox/claimed-ticket entries (staff
    with the support permission), each opening the existing Support
    experience (`internal-team-chat` REQ-10–REQ-18, unchanged) in the
    conversation column. The "Abrir chamado de suporte" action (REQ-2)
    is the only way to start a brand-new one; existing ones are rows
    like any other conversation, not hidden behind a separate action.
    This row and the "Abrir chamado de suporte" action must be visibly
    distinct entries (not the same label twice — a duplication bug found
    and fixed the same day).
  - **"Base de artigos" rows** — every existing RAG conversation the
    viewer has (`conversations`' existing REQ-1 through REQ-8; a viewer
    may have more than one), each opening in the conversation column
    exactly as today. The "Falar com a base de artigos" action (REQ-2)
    always starts a new one — mirrors REQ-2 of `conversations`' own
    SPEC ("When the user starts a new conversation... create it... make
    it the active conversation") — existing ones are reached via their
    row, never via that action.
  - **Tenant-scoping of the 2 conversation-starting actions (bug fix,
    2026-08-09)**: "Abrir chamado de suporte" and "Falar com a base de
    artigos" both only mean something with an active tenant selected
    (opening a ticket is a member action inside a tenant; the RAG
    endpoint itself is tenant-scoped) — a staff viewer with no active
    tenant (pure cross-tenant oversight) shall not see these two as
    available actions. "Criar grupo" is unconditional (a staff-only
    group is valid independent of any tenant). This does not hide the
    Support row itself, which staff can still reach with no active
    tenant, per `internal-team-chat`'s existing oversight model.
  - **Currently-open row indication (2026-08-09)**: whichever row
    (person, group, Support, or "Base de artigos") corresponds to what's
    currently shown in the conversation column (REQ-2a) shall be
    visually indicated as active/selected.
- **REQ-2a [Ubiquitous]** The conversation column (right) shall show
  whichever conversation, group, Support channel, or "Base de artigos"
  conversation is currently open, using the existing unchanged
  components/behavior for each kind (`message-thread`/
  `conversation-detail`, `SupportPageComponent`,
  `ConversationsPageComponent` respectively) — this SPEC changes only
  which column renders them, not their own behavior.
- **REQ-2c [State-Driven]** While the viewport is narrower than the
  layout's two-column breakpoint, the system shall collapse to showing
  one column at a time (directory or conversation, whichever the viewer
  last activated) — mirroring the existing collapsible-sidebar
  convention already used by the app shell.
- **REQ-3 [Event-Driven]** When the user clicks a person's row (either
  partition) in the directory column, the system shall open that
  person's existing 1:1 conversation if one exists, or create-and-open a
  new one if it doesn't — with no separate "Nova conversa" step, dialog,
  or route in between.
- **REQ-4 [Unwanted Behavior]** If the user clicks a person they are not
  eligible to message 1:1 (per the backend's existing eligibility rule),
  then the system shall not offer that person as a row in the first
  place — mirrors `internal-team-chat` REQ-2's existing eligibility
  filtering, just applied to a list instead of a picker.
- **REQ-5 [Event-Driven]** When the user clicks a group they already
  participate in, the system shall open that group's conversation view
  in the conversation column, identically to `internal-team-chat`'s
  existing REQ-1/REQ-7/REQ-8 behavior (including the distinct "looking
  in" framing for an admin's look-in access — note: this is the
  existing tenant-level `STAFF_ADMIN`/`MEMBER_ADMIN` oversight, a
  different concept from the new per-group "group admin" role
  introduced below).
- **REQ-6 [Event-Driven]** When the user activates "Abrir chamado de
  suporte" or clicks an existing Support row, the system shall show the
  existing Support experience appropriate to the viewer's role — own
  channel (member), unclaimed inbox/claimed ticket (staff with the
  support permission), or read-only browse (support-view permission) —
  exactly as `internal-team-chat`'s existing REQ-10 through REQ-18
  already specify, unchanged — in the conversation column.
- **REQ-7 [Event-Driven]** When the user activates "Falar com a base de
  artigos" or clicks an existing "Base de artigos" row, the system shall
  show the existing RAG conversation view (`conversations`' existing
  REQ-1 through REQ-8) in the conversation column, unchanged in its own
  behavior.

### Search (by name only — see "Out of scope / Future work" for message
content search)

- **REQ-8 [Event-Driven]** When the user types into a search field, the
  system shall filter that field's own section to only the entries whose
  display name (person's profile nickname, or group name) contains the
  typed text, case-insensitively, updating as the user types. **Amended
  (2) 2026-08-09**: since People is now two partitions
  ("Already talked to"/"Haven't talked yet", see REQ-2), each partition
  has its own independent search field — typing in one never affects the
  other. Groups keeps a single search field over its own candidate set:
  every group the viewer already participates in, plus every
  **Discoverable** or **Public** group (see below) the viewer does not
  yet participate in — never a **Private** group the viewer isn't
  already in (REQ-19). No search here ever looks inside message content
  — see "Out of scope / Future work."
- **REQ-9 [Ubiquitous]** The system shall not filter the Support or
  "Base de artigos" sections by this search — search narrows People/
  Groups only; both other sections remain always reachable.
- **REQ-10 [Unwanted Behavior]** If a search yields no matching person or
  group, then the system shall show a "no results for '<query>'" message
  distinct from the existing "no conversations yet" empty state, so a
  user can tell "I have zero conversations" apart from "my search typo
  matched nothing."
- **REQ-11 [Event-Driven]** When the user clears the search field, the
  system shall restore the full, unfiltered People/Groups sections.

### Group creation

- **REQ-12 [Ubiquitous]** The sidebar's Groups section shall show a
  "Criar grupo" action, visually distinct from clicking an existing
  group.
- **REQ-13 [Event-Driven]** When the user activates "Criar grupo" and
  submits a group name **and a visibility type** (REQ-18), the system
  shall create a new group conversation containing only the creator as
  a participant, automatically the group's admin (per the backend's
  `chat-group-membership-management` model), and immediately open it as
  the active conversation — other participants are added afterward, not
  chosen as part of this creation step.

### Group visibility and discovery

- **REQ-18 [Ubiquitous]** The "Criar grupo" action (REQ-12/REQ-13) shall
  require the creator to choose exactly one visibility type before the
  group is created:
  - **Privado** — the group is never returned by search (REQ-19) to a
    non-participant; the only way to become a participant is being
    added by the group's admin (REQ-13/REQ-14 of the existing
    add-participant flow, specified in the backend companion SPEC).
  - **Com solicitação de entrada** — the group appears in search
    results (REQ-8) to any eligible non-participant; a non-participant
    may submit a join request (REQ-21), which must be approved by a
    group admin before they become a participant (REQ-22/REQ-23).
  - **Público** — the group appears in search results (REQ-8) to any
    eligible non-participant; a non-participant may join immediately
    (REQ-20), with no approval step.
- **REQ-19 [Unwanted Behavior]** If a non-participant's search matches a
  **Private** group by name, then the system shall not include that
  group in the search results shown to them — a Private group is
  invisible to search for anyone not already a participant, full stop.
- **REQ-20 [Event-Driven]** When a non-participant clicks a **Público**
  group in their search results, the system shall add them as a
  participant immediately (calling the backend's join endpoint) and open
  the group's conversation view, with no intermediate confirmation step
  beyond the click itself.
- **REQ-21 [Event-Driven]** When a non-participant clicks a **Com
  solicitação de entrada** group in their search results, the system
  shall let them submit a join request (calling the backend's
  request-to-join endpoint) and shall show that request as pending
  rather than opening the group's conversation view, since they are not
  yet a participant.
- **REQ-22 [Optional Feature]** Where the backend reports that the
  current viewer holds the **group admin** role for a group they
  participate in (per `chat-group-membership-management`'s admin model),
  the system shall show that group's pending join requests with
  approve/reject actions; a non-admin participant shall not see these
  actions.
- **REQ-23 [Event-Driven]** When a group admin approves a pending join
  request via REQ-22, the system shall call the backend's approval
  endpoint and, on success, add the requester as a participant and
  remove that request from the pending list.
- **REQ-24 [Event-Driven]** When a group admin rejects a pending join
  request via REQ-22, the system shall call the backend's rejection
  endpoint and, on success, remove that request from the pending list
  without adding the requester as a participant.
- **REQ-25 [Unwanted Behavior]** If joining a Public group (REQ-20),
  submitting a join request (REQ-21), or approving/rejecting one
  (REQ-23/REQ-24) fails (backend rejection or network/server error),
  then the system shall show an inline error and leave the viewer's/
  group's state exactly as it was before the attempt.
- **REQ-26 [Ubiquitous]** The system shall visually indicate a group's
  visibility type (e.g. a badge: Privado / Solicitação / Público)
  wherever a group is listed — in the Groups section, in search results,
  and in the group's own header.
- **REQ-28 [Optional Feature]** Where the backend reports that the
  current viewer holds the group admin role, the system shall offer
  actions to change that group's visibility type and to promote another
  participant to admin, from inside the group's own view; a non-admin
  participant shall not see these actions.
- **REQ-29 [Event-Driven]** When a group admin changes the group's
  visibility type via REQ-28, the system shall call the backend's
  update-visibility endpoint and, on success, update the badge (REQ-26)
  and the group's discoverability in search accordingly.
- **REQ-30 [Event-Driven]** When a group admin promotes another
  participant to admin via REQ-28, the system shall call the backend's
  promote endpoint and, on success, reflect that participant as an
  admin (e.g. they now also see REQ-22/REQ-28's admin-only actions on
  their own next load/refresh of that group).

### Group membership management (frontend consumption only — backend
authorization specified separately)

> The requirements below describe what the frontend does once the
> corresponding backend endpoints exist, specified by
> `chat-group-membership-management` (backend): add participant, remove
> participant, leave group, join a Public group, request-to-join a
> Com-solicitação group, approve/reject a join request, change
> visibility, promote to admin, and delete a group — all gated by that
> SPEC's group-admin model. This SPEC does not define who is allowed to
> do any of these things — that belongs entirely to the backend SPEC.
> The frontend's job is to call the endpoint and reflect its
> response/errors.

- **REQ-14 [Optional Feature]** Where the backend reports that the
  current viewer holds the group admin role for a group they are
  viewing, the system shall offer a "remover" action next to each other
  participant.
- **REQ-15 [Event-Driven]** When a group admin confirms removing a
  participant via REQ-14's action, the system shall call the
  corresponding backend endpoint and, on success, remove that
  participant from the group's displayed participant list without a
  full page reload.
- **REQ-16 [Ubiquitous]** The system shall offer a "sair do grupo" action
  to any user viewing a group they are a genuine participant of (not
  shown to an admin viewing solely via the tenant-level look-in
  override, which is not membership).
- **REQ-17 [Event-Driven]** When the user confirms leaving a group via
  REQ-16's action, the system shall call the corresponding backend
  endpoint and, on success, remove that group from the current user's
  own Groups section and navigate them away from that group's view.
  (Whether this is even allowed for the group's sole admin, and what
  happens to admin status when they do, is entirely the backend's
  auto-promotion rule, per Tier 3 resolution #4 — the frontend does not
  special-case this.)
- **REQ-31 [Optional Feature]** Where the backend reports that the
  current viewer holds the group admin role, the system shall offer a
  "excluir grupo" action from inside the group's own view.
- **REQ-32 [Event-Driven]** When a group admin confirms deleting a group
  via REQ-31's action, the system shall call the backend's delete
  endpoint and, on success, remove that group from every participant's
  Groups section (on their next load) and navigate the acting admin away
  from that group's view.
- **REQ-27 [Unwanted Behavior]** If a remove-participant, leave-group,
  change-visibility, promote-to-admin, or delete-group call fails (e.g.
  the backend rejects it as unauthorized, or a network/server error),
  then the system shall show an inline error and leave the group's
  displayed state unchanged, rather than optimistically applying the
  change before the backend confirms it.

## Non-functional requirements

- Accessibility: the sidebar's search field, section headers/tabs
  (People/Groups/Support/Base de artigos), "Criar grupo" action
  (including its visibility-type choice), group visibility badges, join/
  request-to-join actions, approve/reject actions, promote-to-admin
  action, and any "remover"/"sair do grupo"/"excluir grupo" actions must
  all be keyboard-navigable and screen-reader-labeled, matching
  `internal-team-chat`'s existing accessibility bar (WCAG AA).
  Search-filtered-out entries (including Private groups, per REQ-19)
  must be removed from the accessibility tree, not merely visually
  hidden.
- Performance: REQ-8's search filters an already-fetched, already
  reasonably-bounded candidate list (the same eligible-participants/
  discoverable-groups data the backend already scopes down), and only
  ever matches display names, never message content (see "Out of scope
  / Future work"). **Tier 2 note (not Tier 3, but flagged for the
  record):** if a tenant's member or discoverable-group count ever grows
  large enough that fetching the full candidate list up front becomes
  impractical, that's the trigger to introduce a server-side search
  query param, mirroring `tenant-pagination-search`'s existing pattern —
  not something this SPEC pre-builds speculatively.
- Responsiveness: the unified screen must be usable at the breakpoints
  already supported elsewhere in `knowly-app` (mobile, tablet, desktop) —
  on narrow viewports, the sidebar and main panel follow the same
  collapse/expand pattern `internal-team-chat`'s existing `/chat` screen
  already uses.

## Acceptance criteria

- [x] A single "Conversas" navigation entry replaces the previously
      separate chat/support/conversations entries.
- [x] The sidebar shows People, Groups, Support, and "Base de artigos" as
      four distinct sections in one screen, each behaving exactly as its
      own existing SPEC already describes.
- [x] Clicking a person opens or creates their 1:1 conversation directly,
      with no intermediate "new conversation" screen/dialog.
- [x] A person the viewer isn't eligible to message never appears in the
      People section.
- [x] Clicking a group the viewer participates in opens it, preserving
      the existing look-in framing for `STAFF_ADMIN`/`MEMBER_ADMIN`
      oversight access.
- [x] Selecting Support shows the existing role-appropriate Support
      experience, unchanged in behavior/permissions.
- [x] Selecting "Base de artigos" shows the existing RAG conversation
      list/detail, unchanged in behavior, inside the same screen.
- [x] Typing in the search field filters People and Groups by display
      name only; Support and "Base de artigos" are never filtered; no
      match is ever found against message content.
- [x] A search with no matches shows a distinct "no results" message,
      not the generic empty-conversations message.
- [x] Clearing the search field restores the full list.
- [x] "Criar grupo" requires choosing a visibility type (Privado / Com
      solicitação de entrada / Público) before creating the group, which
      contains only the creator (as admin) and opens immediately.
- [x] A Private group never appears in another non-participant's search
      results, under any query.
- [x] A Com-solicitação-de-entrada group appears in a non-participant's
      search results; clicking it submits a join request and shows it
      as pending, without opening the group's conversation view.
- [x] A Público group appears in a non-participant's search results;
      clicking it joins immediately and opens the group's conversation
      view, with no approval step.
- [x] Only a viewer the backend reports as the group's admin sees
      pending-join-request approve/reject actions, visibility-change,
      promote-to-admin, and delete-group actions; a non-admin
      participant sees none of them.
- [x] Approving a join request adds the requester as a participant;
      rejecting does not.
- [x] Every group listing (Groups section, search results, group header)
      shows its visibility type.
- [x] A group admin removing a participant removes them from the
      displayed list on success only.
- [x] Any genuine group participant sees a "sair do grupo" action
      (never shown to an admin present only via tenant-level look-in);
      confirming it removes the group from their own list and navigates
      them away on success only.
- [x] A group admin changing visibility updates the displayed badge and
      the group's future discoverability in search on success only.
- [x] A group admin promoting another participant makes admin-only
      actions available to that participant on their next load.
- [x] A group admin deleting a group removes it from every participant's
      list and navigates the acting admin away, on success only.
- [x] A failed join, join-request, approve/reject, remove-participant,
      leave-group, change-visibility, promote, or delete-group call
      shows an inline error and leaves the group's displayed state
      unchanged.

## Out of scope / Future work

- **Full-text search over message content** (searching by a snippet of
  what was actually said, with sender/conversation/date filters) —
  **deferred at the product owner's explicit instruction (2026-08-08),
  not part of this SPEC.** This SPEC's search (REQ-8) matches person/
  group display names only. Reason for deferral: message-content search
  needs its own indexing strategy (e.g. full-text index or a dedicated
  search service) that neither `internal-team-chat` nor this SPEC's
  already-fetched-candidate-list approach supports — it is a materially
  bigger feature (data model, indexing, likely a new backend endpoint
  with its own pagination/relevance-ranking concerns), not a small
  extension of REQ-8's name-matching filter. Tracked here so it isn't
  lost; a future SPEC should own it explicitly rather than it being
  silently folded into this one's "search" requirements.
- Everything already out of scope in `internal-team-chat`'s SPEC
  (message editing/deletion, read receipts, typing indicators, file/
  image attachments, push/email/browser notifications, real-time
  transport choice) and in `conversations`' SPEC (conversation editing/
  deletion/renaming, citations UI, markdown rendering) — unchanged by
  this amendment.
- Any change to Support's, Groups', People's, or RAG chat's own internal
  behavior or permission model — this SPEC only changes the shared
  navigation surface they're reached from (see Tier 3 resolution #1).
- Changing a group's visibility type after creation beyond the
  admin-only REQ-28/REQ-29 action already specified above — no further
  visibility-transition rules (e.g. cooldowns, notifying participants)
  are covered.
- Canceling or seeing the status history of a rejected/withdrawn join
  request beyond "it's no longer pending" — no request history view is
  specified here.
- Defining *who* is authorized to remove a participant, approve a join
  request, change visibility, promote an admin, or delete a group — that
  rule belongs entirely to `chat-group-membership-management` (backend);
  this SPEC only reflects whatever admin/capability flag that backend
  contract returns for the current viewer.
- A group's tenant anchor (member-only vs. staff-only) changing after
  creation — still fixed at creation time, per `internal-team-chat`'s
  existing constraint; independent of and orthogonal to the new
  visibility-type choice.
- Server-side search — see the Performance non-functional note above;
  this SPEC filters an already-fetched list only.
- Changing `internal-team-chat`'s or `conversations`' underlying
  permission/eligibility rules — this SPEC only changes how those rules
  are surfaced in the UI.
