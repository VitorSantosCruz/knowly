# SPEC — chat-unified-ui (frontend)

> The what and the why. No technical implementation details.
>
> **Status: APPROVED FOR PLAN — zero open Tier 3 blockers.** All Tier 3
> items from the original amendment, its "3-column then 2-column"
> follow-up, and "Amended (3)" are resolved (product owner, 2026-08-09
> — see "Tier 3 — resolved" sections below, including the final entry
> resolving REQ-35/REQ-36). **Every requirement in this document —
> REQ-1 (Amended (3), final), REQ-2 (Amended (3), final) including
> REQ-2d, REQ-33 through REQ-37 (all final), and REQ-9 (Amended (3),
> final) — is approved for PLAN.** This document, in full, is ready for
> `software-architect`/PLAN work.
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
> the current viewer. **It does not yet cover a 1:1 conversation
> hard-delete endpoint (REQ-33), nor the equivalent for a "Base de
> artigos" conversation (REQ-36) — both are new, not-yet-specified
> backend dependencies, see "Out of scope" below.**

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

**Further amendment (2026-08-09), see "Amended (3)" below:** the
product owner, after reviewing two screenshots of the 2-column cut,
asked for a further restructuring of column 1 (merging its "já
falou"/"ainda não falou"/"grupos" partitions into one unified,
unlabeled list) plus a brand-new column 3 (a full directory of every
user/group not already in column 1). This is a refinement of the same
navigation surface, not a new feature, and is folded into this same
document rather than a new SPEC. It originally raised 4 open questions;
all 4 are now fully resolved (2026-08-09, same day) — see the Tier 3
sections immediately below.

## Tier 3 — resolved (product owner, 2026-08-08)

These were stop-and-ask items per `DECISIONS.md`'s decision-making
authority section, all now answered directly by the product owner (or,
for #4, closed by the companion backend SPEC's own approved decision).
No Tier 3 item remains open from this round.

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

## Tier 3 — resolved (2026-08-09, Amended (3))

Answered directly by the product owner (2026-08-09, same day as the
open questions were raised). All four items are now fully closed,
including item 1's two narrow sub-cases (Support and "Base de
artigos"), answered in a follow-up round the same day — see "Tier 3 —
resolved (2026-08-09, Amended (3), final round)" immediately below for
those two.

1. **"Clear a conversation" (limpar) — resolved for 1:1 and group in
   this round; Support and RAG resolved in the follow-up round below.**
   - **1:1 (person):** confirmed as a **hard delete** — "apaga a
     conversa e o histórico." Clearing a 1:1 conversation permanently
     deletes both the conversation record and its full message
     history; it is not a per-viewer hide/archive. Reopening that
     person afterward (e.g. from column 3) starts a brand-new, empty
     conversation with no memory of the deleted one. **This needs a
     new backend endpoint that does not exist yet** in this SPEC or in
     `chat-group-membership-management` — see REQ-33 and "Out of
     scope" below.
   - **Group:** confirmed — clearing a group is **the same action as
     leaving it** (REQ-16/REQ-17, already specified and already
     implemented). There is no separate "clear group" concept, no new
     action, and no new backend call. A group leaves column 1 exactly
     the way it already does today: via "sair do grupo." See REQ-34.
   - **Support and "Base de artigos":** resolved in the follow-up round
     — see "Tier 3 — resolved (2026-08-09, Amended (3), final round)"
     below; REQ-35/REQ-36 are now written and final.
2. **Column 3's sort order — resolved, and it turns out richer than
   either of the two readings originally offered.** The product
   owner's answer describes a third reading, illustrated with a
   concrete example: column 3 sorts by **recency of the most recent
   interaction with that person/entity across every surface** the
   viewer shares with them — any 1:1 conversation (even one since
   hard-deleted per item 1 above, since the underlying interaction
   still happened) and any group conversation either party is/was a
   participant of — not just "has an active 1:1 conversation," and not
   limited to interactions that still have a visible column-1 entry.
   This is inherently **per-viewing-user and directional/asymmetric**
   (the owner's own example: the owner's last direct message to
   "cicrano" was earlier than a group message cicrano sent that the
   owner also saw, so cicrano ranks by the group's timestamp on the
   owner's own screen; from cicrano's own screen, the ranking is
   computed from cicrano's own vantage point and may differ; a third
   person, "beltrano," whose direct message to the owner came later
   than either, ranks above both on the owner's screen). See REQ-2d
   (Amended (3), final) below for the precise EARS phrasing, and the
   attached implementation-risk note — this may need new backend
   support that does not obviously exist today.
3. **Groups vs. people symmetry in column 1's "clear" rule — resolved
   by item 1's answer.** Groups do not get a "clear" action distinct
   from people at all; leaving (REQ-16/REQ-17) is the only removal
   action a group ever gets, unchanged from today. There is no group
   "clear" behavior left to define, because there is no such action
   for groups.
4. **Which REQ numbers this amendment supersedes — unchanged from the
   list already on record**, restated below now that the blocking
   status has changed:
   - REQ-2's "Already talked to"/"Haven't talked yet" partitioning
     language is superseded by REQ-2 (Amended (3), final) below.
   - REQ-2's groups-as-a-separate-list language is also superseded —
     groups fold into the unified list (REQ-2, Amended (3), final).
   - REQ-1's "two persistent columns"/REQ-2c's 2-column collapse are
     superseded by REQ-1 (Amended (3), final)/REQ-2d (Amended (3),
     final) below — three columns, not two.
   - REQ-9's Support exemption is **confirmed to continue** — see REQ-9
     (Amended (3), final) below; no longer a pending assumption.
   - REQ-3 through REQ-7 (click-to-open behavior), REQ-10/REQ-11
     (no-results/clear-search behavior), REQ-12 through REQ-32 (group
     creation/visibility/discovery/governance) are **not** touched by
     this amendment, as already stated — they continue to apply
     exactly as already specified, just operating over the new unified
     list's rows instead of the old partitioned ones.

## Tier 3 — resolved (2026-08-09, Amended (3), final round)

The last two sub-questions from item 1 above — Support's and "Base de
artigos"'s own "clear" semantics, which the product owner's first round
of answers explicitly did not cover — are now answered directly by the
product owner, same day. **This closes every remaining Tier 3 item in
this document; there are no more open blockers.**

1. **Support: cannot be cleared at all.** There is no clear/delete
   action for the Support conversation, full stop — it is a single,
   always-available conversation ("só tem uma") that simply never
   leaves the list, so "clear" does not apply to it as a concept. This
   is not a gap or an oversight — the product owner confirmed
   explicitly that no such action exists for Support. See REQ-35
   (final) below, written as an explicit unwanted-behavior requirement
   rather than a silent omission.
2. **"Base de artigos" (RAG): clearable, with the same hard-delete
   semantics as REQ-33's 1:1 clear.** Clearing a RAG conversation
   permanently deletes that specific conversation and its full message
   history; reopening "Base de artigos" afterward (via the "Falar com a
   base de artigos" action, REQ-7) starts a brand-new, empty
   conversation — no prior history is recoverable, exactly mirroring
   REQ-33's person case. Since a viewer may have more than one RAG
   conversation, "clear" here means deleting the one specific RAG
   conversation the action was invoked on, not all of them. This also
   needs a new backend endpoint that does not exist yet, same as
   REQ-33 — see REQ-36 (final) below and "Out of scope."

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
- **(Amended (3)):** As any user, I want my conversation list (column 1)
  to be one simple, unlabeled list of everyone/everything I've already
  talked to — no section headers getting in the way — with Support
  always pinned at the top since I only ever have one.
- **(Amended (3)):** As any user, I want a separate, always-available
  full directory of everyone and every group I haven't yet talked to,
  with its own search, ranked by who I've most recently interacted with
  anywhere (even in a shared group, or a 1:1 I later cleared), so I
  don't have to hunt for a "start new conversation" action to find
  someone new.
- **(Amended (3)):** As a user, I want to permanently clear a 1:1
  conversation and its history when I no longer want it around, with a
  clean slate if I talk to that person again later.
- **(Amended (3), final round):** As a user, I want to permanently clear
  a specific "Base de artigos" conversation and its history the same
  way I can clear a 1:1, with a clean slate if I start a new one
  afterward.

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
> they're anchored on screen.
>
> **Amended (2) 2026-08-09, same day** — a first cut of the amendment
> above shipped as **3** persistent columns (directory, conversation, and
> a new "contacts" column with a "já falou"/"ainda não falou" partition
> of the same person/group data). The product owner liked the
> already-talked-to/haven't-talked-yet partitioning idea but found a
> separate 3rd column redundant with the directory column's own People
> rows. **Final direction (at the time): 2 columns, not 3** — the
> partitioning idea moved *into* the directory column's People section
> (replacing its flat list), instead of living in its own column.
> **Superseded again by Amended (3) below**, which reintroduces a third
> column with a different purpose (a full directory, not a partition of
> the same data) — see that section for why this isn't the same idea
> coming back unchanged.
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
> `lastMessageAt`/activity timestamp (tracked follow-up, see PLAN.md) —
> and, critically, this sort never reacts to which row is merely
> selected/open, only to genuine conversation data, so opening a
> conversation cannot itself reorder the list.
>
> **Amended (3) 2026-08-09, same day, final — zero open questions
> remain.** After reviewing two screenshots of the shipped 2-column cut,
> the product owner asked for two further changes to column 1 and a
> brand-new column 3: (a) collapse column 1's "Already talked to"/"Haven't
> talked yet"/"Groups" into **one single unlabeled list** ("CONVERSAS"),
> one search field, mixing people and groups together — no section
> headers; (b) add a **third column, same width as column 1**, listing
> every user/group the viewer has *not* already got a column-1 entry for
> (a full directory), with its own independent search field, sorted by
> cross-surface last-interaction recency (see REQ-2d); (c) Support, being
> a single, singular conversation, is **pinned/locked at the top** of
> column 1's unified list rather than living in its own section; (d) RAG
> ("Base de artigos") conversations also live as ordinary rows in column
> 1's unified list, not a separate section; (e) a "clear conversation"
> action permanently deletes a 1:1 conversation and its history (REQ-33),
> making that person reappear in column 3; the same action does not
> exist for groups (REQ-34, same as leaving); Support has no clear
> action at all (REQ-35, final); "Base de artigos" clears the same way
> as a 1:1, per-conversation (REQ-36, final). This is **not** the same
> idea as the 3-column cut superseded by "Amended (2)": that earlier 3rd
> column was a partition of the *same* interacted/not-interacted data
> already shown in column 1 (hence "redundant"); this new column 3 is a
> *disjoint* set — a full directory of people/groups the viewer has
> **no** column-1 entry for at all, ranked by a materially richer,
> cross-surface recency signal — a different, non-redundant purpose.

- **REQ-1 [Ubiquitous]** The system shall provide a single top-level
  navigation entry ("Conversas") that replaces the previously separate
  `/chat`, `/support`, and `/conversations` entries, opening one screen
  laid out as **two** persistent columns — a directory column and a
  conversation column (REQ-2a) — instead of a sidebar that swaps the
  main panel's entire content per section.
  - **REQ-1 (Amended (3), final):** the screen shall instead be laid
    out as **three** persistent columns: a conversations column
    (REQ-2, final — the unified "CONVERSAS" list), a conversation/
    thread column (REQ-2a, unchanged), and a full-directory column
    (REQ-2d, final), the first and third the same width. **Approved
    for PLAN** — this 3-column version is now authoritative, replacing
    the 2-column version above.
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
    - **REQ-2 People rows (Amended (3), final):** the "Already talked
      to"/"Haven't talked yet" partition above is superseded — People
      rows with an existing 1:1 conversation move into **column 1's
      single unified "CONVERSAS" list** (see below); People rows with
      **no** existing conversation move entirely out of column 1 and
      into **column 3** (REQ-2d) instead of a "Haven't talked yet"
      partition of column 1. **Approved for PLAN.**
  - **Group rows** — every group conversation the viewer is a
    participant of, plus (see "Group visibility and discovery" below)
    discoverable groups the viewer isn't yet part of, plus
    `STAFF_ADMIN`/`MEMBER_ADMIN` look-ins per `internal-team-chat`'s
    existing REQ-1/REQ-7/REQ-8, unchanged — kept as a single list with
    its own single search field (REQ-8), not partitioned like People.
    - **REQ-2 Group rows (Amended (3), final):** groups the viewer
      already participates in move into column 1's unified "CONVERSAS"
      list (mixed with People, not a separate Groups list);
      discoverable groups the viewer is **not** yet a participant of
      move into column 3 alongside not-yet-messaged People. Groups get
      no "clear" action distinct from leaving (REQ-34) — fully
      resolved, no remaining ambiguity. **Approved for PLAN.**
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
    - **REQ-2 Support row (Amended (3), final, confirmed):** the
      Support row shall be pinned as the **first** row of column 1's
      unified list, always, regardless of sort order or any other
      row's recency — never demoted below any person/group row,
      mirroring the product owner's "fica travada em cima" instruction.
      **Confirmed by the product owner (2026-08-09):** the pinned
      Support row remains visible even while column 1's search field
      has an active, non-matching query — it is exempt from the
      unified search filter, same as REQ-9's existing Support
      exemption, regardless of whether the typed text matches
      "Suporte" or anything else. **Approved for PLAN.** Support has no
      "clear" action at all — see REQ-35 (final).
  - **"Base de artigos" rows** — every existing RAG conversation the
    viewer has (`conversations`' existing REQ-1 through REQ-8; a viewer
    may have more than one), each opening in the conversation column
    exactly as today. The "Falar com a base de artigos" action (REQ-2)
    always starts a new one — mirrors REQ-2 of `conversations`' own
    SPEC ("When the user starts a new conversation... create it... make
    it the active conversation") — existing ones are reached via their
    row, never via that action.
    - **REQ-2 "Base de artigos" rows (Amended (3), final):** existing
      RAG conversations move into column 1's unified list as ordinary
      rows (mixed with People/Groups/Support), no longer a visually
      separate section — and become subject to column 1's single
      unified search (superseding REQ-9's "never filtered" exemption
      for this row kind specifically; Support's exemption is
      unaffected, see above). **Approved for PLAN.** A RAG conversation
      can be cleared the same way a 1:1 can, per-conversation — see
      REQ-36 (final).
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
  which column renders them, not their own behavior. Unaffected by
  Amended (3).
- **REQ-2c [State-Driven]** While the viewport is narrower than the
  layout's column breakpoint, the system shall collapse to showing one
  column at a time (directory or conversation, whichever the viewer
  last activated) — mirroring the existing collapsible-sidebar
  convention already used by the app shell.
  - **REQ-2c (Amended (3), final):** with a third column, the system
    shall collapse to showing **one of the three columns at a time**
    (conversations list, thread, or full directory — whichever the
    viewer last activated), extending the same collapse convention
    from two panes to three; the viewer navigates between the three via
    the same back/forward affordance the existing collapsible sidebar
    already uses (e.g. a back action from the thread returns to
    whichever list column was last open). **Approved for PLAN** as a
    direct extension of the already-approved 2-column collapse
    behavior — no new UX decision beyond generalizing it to a third
    pane.
- **REQ-2d [Complex] (Amended (3), final)** The full-directory column
  (rightmost, same width as column 1) shall show, simultaneously and
  without a tab/section switch, every user the viewer is eligible to
  message 1:1 with **no existing 1:1 conversation** and every group the
  viewer is not yet a participant of that is discoverable to them
  (`PUBLIC`/`REQUEST_TO_JOIN`, per REQ-19, unchanged) — the disjoint
  complement of column 1's People/Group rows — **sorted as follows:**
  for each such person, compute the timestamp of the most recent
  interaction involving the current viewer and that person, across
  every surface either has been part of: the most recent message in
  any group conversation they currently share, and the most recent
  message that ever existed in a 1:1 conversation between them —
  including one since hard-deleted per REQ-33 (the interaction
  happened even though the record is gone; if the backend cannot
  recover that timestamp post-deletion, that person falls back to "no
  known interaction," per below — this exact feasibility question is
  the subject of the risk note below). Column 3 sorts descending by
  that per-person timestamp; a person or discoverable group with no
  computed timestamp (never interacted on any surface) sorts after
  every entity that has one, using the directory's existing default
  order (alphabetical by display name) as the tiebreak among them.
  This ranking is deliberately **per-viewing-user and asymmetric** —
  two people who interacted with each other may see each other ranked
  differently in their own column 3, since each is computed from that
  viewer's own vantage point (confirmed by the product owner's own
  example, see Tier 3 resolution #2 above). It has its own independent
  search field, filtering this column's rows only (never column 1's).
  **Approved for PLAN.**
  - **Implementation-risk note, not blocking approval but flagged
    prominently for PLAN:** this ranking needs a "most recent
    interaction with entity X, across any group message or a
    since-deleted 1:1" derived value per (viewer, other-entity) pair.
    `chat-directory-rows.service.ts`'s current data
    (`ChatService.conversations()`/`.details()`) exposes
    conversation-level data only — no per-participant "last time this
    specific other person was active in a group I'm in" signal, and no
    `lastMessageAt` on a conversation at all yet (`talkedPeople`'s own
    doc comment already flags the id-descending recency proxy as a
    stand-in for that exact, already-known gap). Computing REQ-2d's
    sort client-side would require fetching and correlating full
    message history per group per participant, which does not scale
    and cannot survive a hard delete (REQ-33, since the row is gone).
    This most likely needs new backend support (e.g. a "last
    interaction timestamp with entity X" derived endpoint/field) that
    does not obviously exist today. `PLAN.md` must resolve this
    feasibility question — including whether it belongs in this
    feature's PLAN at all or needs its own backend SPEC amendment —
    before committing to an implementation approach; this is a PLAN-
    level feasibility question, not a remaining SPEC ambiguity.
- **REQ-3 [Event-Driven]** When the user clicks a person's row (either
  partition) in the directory column, the system shall open that
  person's existing 1:1 conversation if one exists, or create-and-open a
  new one if it doesn't — with no separate "Nova conversa" step, dialog,
  or route in between. Applies identically regardless of whether the row
  is in column 1 or column 3 once Amended (3) lands.
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

### Clearing a conversation (Amended (3))

> **Fully resolved (2026-08-09) for all four conversation kinds — 1:1,
> group, Support, and "Base de artigos." REQ-33 through REQ-37 below are
> all final and approved for PLAN.**

- **REQ-33 [Event-Driven]** When the user confirms clearing a 1:1
  conversation from column 1, the system shall call a new backend
  endpoint (not yet specified — see "Out of scope" below) that
  permanently deletes that conversation and its full message history,
  and on success remove that person's row from column 1 (they then
  reappear in column 3, per REQ-2d's disjoint-complement rule, since
  they no longer have an existing 1:1 conversation). Only a genuine
  participant of that 1:1 conversation may clear it. Clicking that
  person again afterward (REQ-3) creates a brand-new, empty
  conversation — no prior history is recoverable.
- **REQ-34 [Ubiquitous]** The system shall not offer a "clear
  conversation" action for a group, distinct from "sair do grupo"
  (REQ-16/REQ-17) — clearing a group conversation is not a concept
  this SPEC defines; leaving is the only way a group ever leaves
  column 1.
- **REQ-35 [Unwanted Behavior] (final)** The system shall not offer a
  "clear conversation"/"limpar" action for the Support row, under any
  circumstance — Support is a single, always-available conversation per
  viewer ("só tem uma") with no clear or delete action of any kind; it
  simply never leaves column 1. This is a deliberate absence of a
  feature, confirmed by the product owner, not an unspecified gap.
- **REQ-36 [Event-Driven] (final)** When the user confirms clearing a
  specific "Base de artigos" conversation from column 1, the system
  shall call a new backend endpoint (not yet specified — see "Out of
  scope" below) that permanently deletes that specific RAG conversation
  and its full message history, and on success remove that
  conversation's row from column 1. Only the conversation's own
  participant (the viewer who owns it) may clear it. Reopening "Base de
  artigos" afterward (via the "Falar com a base de artigos" action,
  REQ-7) starts a brand-new, empty conversation — no prior history is
  recoverable. If the viewer has other, uncleared RAG conversations,
  those are entirely unaffected. Mirrors REQ-33's semantics exactly,
  scoped to the RAG conversation type.
- **REQ-37 [Unwanted Behavior]** If clearing a 1:1 conversation
  (REQ-33) or a "Base de artigos" conversation (REQ-36) fails (backend
  rejection, e.g. the caller isn't a participant, or a network/server
  error), then the system shall show an inline error and leave that
  conversation's row in column 1 exactly as it was before the attempt,
  never optimistically removing it before the backend confirms
  deletion.

### Search (by name only — see "Out of scope / Future work" for message
content search)

- **REQ-8 [Event-Driven]** When the user types into a search field, the
  system shall filter that field's own section to only the entries whose
  display name (person's profile nickname, or group name) contains the
  typed text, case-insensitively, updating as the user types. **Amended
  (2) 2026-08-09**: since People is now two partitions
  ("Already talked to"/"Haven't talked yet", see REQ-2), each partition
  has its own independent search field; typing in one never affects the
  other. Groups keeps a single search field over its own candidate set:
  every group the viewer already participates in, plus every
  **Discoverable** or **Public** group (see below) the viewer does not
  yet participate in — never a **Private** group the viewer isn't
  already in (REQ-19). No search here ever looks inside message content
  — see "Out of scope / Future work."
  - **REQ-8 (Amended (3), final):** superseded now that REQ-1/REQ-2
    (Amended (3)) are approved — column 1 gets **one** search field
    over its whole unified list (people-with-conversation, groups-
    with-conversation, RAG conversations; Support is exempt, see REQ-2
    above), and column 3 gets its **own, separate** search field over
    its own list (people-without-conversation, discoverable groups-
    without-conversation). The two search fields never affect each
    other's results. **Approved for PLAN.**
- **REQ-9 [Ubiquitous]** The system shall not filter the Support or
  "Base de artigos" sections by this search — search narrows People/
  Groups only; both other sections remain always reachable.
  - **REQ-9 (Amended (3), final):** superseded for "Base de artigos"
    (RAG rows are now ordinary, searchable rows in column 1's unified
    list, see REQ-2 above). **Confirmed by the product owner
    (2026-08-09):** Support's exemption continues unchanged — the
    pinned Support row stays visible regardless of any active,
    non-matching search query. **Approved for PLAN.**
- **REQ-10 [Unwanted Behavior]** If a search yields no matching person or
  group, then the system shall show a "no results for '<query>'" message
  distinct from the existing "no conversations yet" empty state, so a
  user can tell "I have zero conversations" apart from "my search typo
  matched nothing." Unaffected by Amended (3) beyond now applying per
  column (1 and 3 each get their own empty/no-results state).
- **REQ-11 [Event-Driven]** When the user clears the search field, the
  system shall restore the full, unfiltered People/Groups sections.
  Unaffected by Amended (3) beyond applying per column independently.

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
  action, and any "remover"/"sair do grupo"/"excluir grupo"/"limpar
  conversa" actions must all be keyboard-navigable and screen-reader-
  labeled, matching `internal-team-chat`'s existing accessibility bar
  (WCAG AA). Search-filtered-out entries (including Private groups, per
  REQ-19) must be removed from the accessibility tree, not merely
  visually hidden. **(Amended (3), final):** column 1's and column 3's
  search fields each need their own accessible label, distinct from
  each other.
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
  on narrow viewports, the screen follows the same collapse/expand
  pattern `internal-team-chat`'s existing `/chat` screen already uses.
  **(Amended (3), final):** with three columns, the narrow-viewport
  story shows one of the three at a time (REQ-2c, final), extending the
  same convention.

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

**Amended (3), approved for PLAN (2026-08-09):**

- [x] Column 1 shows one unified, unlabeled "CONVERSAS" list (people
      with an existing conversation + groups already joined + Support +
      RAG conversations), with Support always pinned first regardless of
      sort/search, and one search field over the whole list (Support
      exempt from that search). Verified by
      `chat-directory.component.spec.ts` (tasks 151-157).
- [x] Column 3, the same width as column 1, shows every person the
      viewer is eligible to message but hasn't yet, plus every
      discoverable group the viewer isn't a participant of — with zero
      overlap against column 1's rows — and has its own independent
      search field. Verified by `chat-full-directory.component.spec.ts`
      and `chat-directory-rows.service.spec.ts` (tasks 136-137, 144-150).
- [ ] Column 3 is sorted descending by, for each entity, the timestamp
      of the most recent interaction (any shared group's most recent
      message, or any 1:1 message ever exchanged with that person,
      including via a since-cleared/deleted 1:1) involving the current
      viewer and that entity; entities with no computed interaction
      timestamp sort after all entities that have one, alphabetically
      among themselves. **Not yet — ships today with the documented
      interim fallback (alphabetical-only, see
      `ChatDirectoryRowsService.discoveryRows()`'s doc comment); the real
      cross-surface ranking is BLOCKED on TASKS.md's tasks 141-142
      pending a new backend `interaction-recency`-style endpoint, per
      PLAN.md's "Cross-surface recency sort" feasibility decision.**
- [ ] Confirming "limpar" on a 1:1 conversation permanently deletes the
      conversation and its full message history; only a genuine
      participant may do this; the person then appears in column 3
      instead of column 1; re-clicking them afterward starts a brand-new,
      empty conversation. **BLOCKED — TASKS.md task 161 — needs a new
      backend hard-delete endpoint for `PEER_DIRECT` conversations that
      does not exist yet; not started.**
- [x] No "clear conversation" action is offered for a group — "sair do
      grupo" remains the only way a group leaves column 1. Verified by
      `conversation-detail.component.spec.ts` (task 162).
- [ ] A failed 1:1 clear attempt shows an inline error and leaves that
      row in column 1 unchanged. **BLOCKED — same prerequisite as the
      1:1 clear item above (TASKS.md task 161); not started.**
- [x] No "clear conversation"/"limpar" action is ever offered for the
      Support row, under any circumstance (REQ-35). Verified by
      `chat-directory.component.spec.ts` (task 163).
- [ ] Confirming "limpar" on a "Base de artigos" conversation permanently
      deletes that specific RAG conversation and its full message
      history; only its own participant may do this; the conversation's
      row is removed from column 1 on success; a viewer's other RAG
      conversations are unaffected; reopening "Base de artigos"
      afterward starts a brand-new, empty conversation (REQ-36).
      **BLOCKED — TASKS.md task 165 — needs a new backend hard-delete
      endpoint for RAG conversations that does not exist yet; not
      started.**
- [ ] A failed "Base de artigos" clear attempt shows an inline error and
      leaves that row in column 1 unchanged (REQ-37). **BLOCKED — same
      prerequisite as the "Base de artigos" clear item above (TASKS.md
      task 165); not started.**

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
- **(Amended (3)) 1:1 "clear conversation" backend endpoint** — the
  semantics are resolved as a hard delete (REQ-33), but **no backend
  endpoint for this exists yet** in this SPEC or in
  `chat-group-membership-management`. Implementing REQ-33 requires a
  new backend SPEC amendment (e.g. a `DELETE` on a 1:1 conversation,
  scoped to a genuine participant of it) before PLAN can build against
  it — this frontend SPEC does not invent that contract on its own.
- **(Amended (3), final round) "Base de artigos" "clear conversation"
  backend endpoint** — same gap as the 1:1 case above, and it applies
  here too: REQ-36's semantics (hard delete of one specific RAG
  conversation) are resolved, but **no backend endpoint for this exists
  yet** either, in this SPEC, in `conversations`' own SPEC, or in
  `chat-group-membership-management`. Implementing REQ-36 requires a new
  backend SPEC amendment (e.g. a `DELETE` on a RAG conversation, scoped
  to its own owning participant) before PLAN can build against it. This
  gap does **not** apply to Support (REQ-35) — Support has no clear
  action at all, by design, so there is no missing endpoint to track for
  it.
- **(Amended (3)) Column 3's sort order is now fully defined (REQ-2d,
  final)** as cross-surface last-interaction recency — the remaining
  open item is a PLAN-level feasibility question (see REQ-2d's
  implementation-risk note), not a SPEC ambiguity: whether the backend
  data needed to compute it exists today, and if not, whether that
  backend work belongs to this feature's PLAN or needs its own backend
  SPEC amendment.
</content>
