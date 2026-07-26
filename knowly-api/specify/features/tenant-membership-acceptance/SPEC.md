# SPEC — tenant-membership-acceptance

## Context and motivation

Today, `TenantService.addMember` creates an immediately-active
`TenantMembership` the instant a `MEMBER_ADMIN` (or staff) adds anyone to
a tenant — no confirmation step, no notification of any kind (`MailService`
isn't even wired into `TenantService`). This has two concrete problems:

1. **Staff-escalation blind spot.** A `MEMBER_ADMIN` can add an existing
   `STAFF`/`STAFF_ADMIN` user as a tenant `MEMBER`/`MEMBER_ADMIN` without
   that staff user's knowledge or consent. Today's code doesn't
   *currently* reduce a staff bypass's effective access (staff bypasses
   are keyed off `GlobalRole`/`GlobalPermission`, never off
   `TenantMembership` rows), but nothing stops a *future* change from
   accidentally doing so, and — more importantly — the product decision
   is that a staff member must consciously *accept* gaining tenant-local
   scope inside a specific tenant's data before that membership does
   anything, exactly the same way any other invitee would.
2. **No consent step for anyone.** More generally, *any* invited user
   (staff or not) becomes an active tenant member with zero acknowledgment
   — there's no in-app record that an invitation happened, nor a signal
   back to whoever invited them (or the tenant's admin(s)) that it was
   accepted.

This feature introduces a minimal pending/accept flow for tenant
membership, entirely in-app (no email — email is used elsewhere in this
system, e.g. OTP delivery, but is explicitly not part of this accept
step), plus the narrowest possible new notification/request-accept
mechanism needed to support it.

This SPEC covers backlog items 9 and 10 from `PROJECT_STATUS.md` together,
since both need the same pending-membership/accept mechanism.

## User stories

- As a `MEMBER_ADMIN` adding a new member to my tenant, I want that
  person to explicitly accept before they become an active member, so
  that membership is never silently forced on anyone.
- As a `STAFF`/`STAFF_ADMIN` user added to a tenant as a member, I want
  my prior effective access in that tenant to stay exactly as it was
  until I explicitly accept, so that a tenant admin can't use membership
  assignment to blind me to what's happening in their tenant without my
  knowledge.
- As the person who invited a member (or the tenant's `MEMBER_ADMIN`(s)),
  I want to be notified in-app once the invitee accepts, so that I know
  the membership is actually active.
- As an admin/staff managing a tenant, I want to be able to explicitly
  deactivate a stale `MEMBER` row left over from before a user became
  staff, so that it doesn't keep silently limiting their access forever.
- As any user, I want a simple place to see and act on my own pending
  in-app requests, so I don't need email to know I've been invited
  somewhere.

## Requirements (EARS/GEARS)

- **REQ-1 [Event-Driven]** When a `MEMBER_ADMIN` (of their own tenant) or
  a staff actor (per existing `requireAdminOfTenantOrStaff` rules) calls
  the member-add action for a user who already has a `User` account in
  the system, the system shall create the corresponding
  `TenantMembership` in a **pending** state rather than an immediately
  active one.
- **REQ-1a [Unwanted Behavior]** If the member-add action targets an
  email with no existing `User` account, then the system shall not
  create a pending membership — since there is no account to deliver an
  in-app notification to, the invitee cannot be asked for consent, so
  the resulting membership is created **active immediately**, matching
  today's behavior. This is the existing new-user invite path (the
  `User` row is created at invite time via the passwordless/login-code
  flow); it is unaffected by this feature.
- **REQ-2 [State-Driven]** While a `TenantMembership` is pending, the
  system shall grant no tenant-scoped authorization derived from that
  membership — a pending row must not satisfy any check that today
  requires an active membership (e.g. `PermissionAspect`'s
  permission-check gate).
- **REQ-3 [Event-Driven]** When a pending `TenantMembership` is created
  for a user whose `GlobalRole` is `STAFF` or `STAFF_ADMIN`, the system
  shall leave that user's pre-existing effective access within that
  tenant — as determined by their existing staff bypass
  (`GlobalRole`/`GlobalPermission`-based, independent of any
  `TenantMembership` row) and by any other already-active membership row
  they may separately hold in that tenant — completely unaffected; the
  new pending row must not itself reduce, replace, or shadow that access
  in any way until accepted.
- **REQ-4 [Event-Driven]** When a `TenantMembership` is created in a
  pending state, the system shall create an in-app notification
  addressed to the invited user, referencing that pending membership,
  informing them a membership invitation awaits their acceptance.
- **REQ-5 [Event-Driven]** When the invited user accepts their pending
  membership invitation, the system shall transition that
  `TenantMembership` to active, at which point it grants tenant-scoped
  authorization exactly as an active membership does today.
- **REQ-6 [Event-Driven]** When a pending membership invitation is
  accepted, the system shall create an in-app notification for the
  tenant's active `MEMBER_ADMIN`(s) and for the user who performed the
  original member-add action, informing them the invitation was accepted
  (a single notification is sufficient where the same person occupies
  both roles).
- **REQ-7 [Event-Driven]** When the invited user declines their pending
  membership invitation, the system shall mark that `TenantMembership` as
  not active and not pending (declined), and it shall never become
  active without a brand-new invitation.
- **REQ-8 [Ubiquitous]** The system shall provide an endpoint for an
  authenticated user to list their own unresolved in-app
  notifications/requests.
- **REQ-9 [Event-Driven]** When a user accepts or declines a notification
  that references a pending membership, the system shall mark that
  notification resolved (no longer returned by REQ-8's listing).
- **REQ-10 [Unwanted Behavior]** If a user attempts to accept or decline
  a notification not addressed to them, then the system shall reject the
  action as a permission failure.
- **REQ-11 [Unwanted Behavior]** If a user attempts to accept or decline
  a notification whose referenced `TenantMembership` is no longer
  pending (already accepted, already declined, or removed), then the
  system shall reject the action rather than silently succeeding or
  double-processing it.
- **REQ-12 [Optional Feature]** Where an actor is a `MEMBER_ADMIN` of the
  tenant (or staff, per existing rules), the system shall let them
  explicitly deactivate an existing active `TenantMembership` — including
  a plain `MEMBER` row belonging to a user who has since become
  `STAFF`/`STAFF_ADMIN` — reusing the existing soft-deactivation
  mechanism (`TenantService.removeMember`), which already satisfies this
  requirement without modification.
- **REQ-13 [Event-Driven]** When a member-add call targets a user who
  already has a non-active (previously declined or previously removed)
  `TenantMembership` row for that tenant, the system shall re-create it
  in a pending state and require fresh acceptance, exactly as for a
  brand-new invitation — a prior acceptance never carries forward to a
  later, separate invitation.
- **REQ-14 [Ubiquitous]** Every notification shall carry at minimum: a
  recipient (`User`), a type identifying its context (e.g. "membership
  invitation pending acceptance" vs. "membership invitation accepted"), a
  reference to the `TenantMembership` it concerns, a resolved/unresolved
  state, and whatever else is needed to render and act on it (accept/
  decline for the actionable type, acknowledgment only for the
  informational type).

## Non-functional requirements

- Security: acceptance/decline is gated purely on "is this notification's
  recipient the caller" (REQ-10) — no new `Permission`/`GlobalPermission`
  is needed for a user to act on their own notifications.
- Security: tenant isolation is unaffected — a pending/active
  `TenantMembership` still belongs to exactly one tenant and is still
  subject to the existing `TenantFilter`; the new `Notification` entity is
  keyed to a specific user and a specific membership, never queried
  across tenants without that scoping.
- Observability: every state transition this feature introduces (pending
  membership created, accepted, declined, deactivated) must emit an audit
  event (actor, action, outcome), per the constitution's audit
  requirements — the same `@AuditLog` convention already used for
  `tenant.member.add`/`tenant.member.remove`.
- Consistency: this reuses one `Notification`-style mechanism for both
  the invitation-pending and invitation-accepted cases (REQ-4/REQ-6),
  rather than two separate mechanisms.

## Acceptance criteria

- [ ] Calling the member-add action never results in an immediately
      active `TenantMembership` for a user who already has an account —
      it is always pending first, for every kind of invited existing
      user (staff or not).
- [ ] Calling the member-add action for an email with no existing `User`
      account still results in an immediately active `TenantMembership`
      (no pending state, no notification) — unchanged from today.
- [ ] A pending `TenantMembership` grants zero tenant-scoped
      authorization — any `@RequiresPermission`-gated action fails for a
      user whose only membership in that tenant is pending.
- [ ] A `STAFF`/`STAFF_ADMIN` user's staff-bypass access to a tenant is
      identical before and immediately after a pending membership is
      created for them in that tenant — no regression in their existing
      access while pending.
- [ ] The invited user sees an unresolved notification referencing the
      new pending membership via the "list my notifications" endpoint.
- [ ] Accepting that notification activates the membership and grants
      normal tenant-scoped authorization from that point on.
- [ ] Accepting also produces a resolved-notification state for the
      invited user and a new notification for the tenant's `MEMBER_ADMIN`(s)
      and the original inviter.
- [ ] Declining leaves the membership permanently non-active — a fresh
      member-add call is required to invite that user again.
- [ ] A user cannot accept/decline another user's notification (403/
      permission denied).
- [ ] Accepting or declining an already-resolved notification/membership
      fails cleanly instead of double-processing.
- [ ] An admin/staff can explicitly deactivate an existing active
      `MEMBER` row for a user who has since become staff (already covered
      by the existing `removeMember` action — verify it still works
      unmodified for this scenario).
- [ ] Every new state transition (pending created, accepted, declined)
      is audit-logged with actor/action/outcome.

## Out of scope

- **Email as part of this accept step** — explicitly in-app only, per
  the confirmed requirement. (Email continues to be used elsewhere in
  the system, e.g. OTP delivery — unrelated and unchanged.)
- **`TenantService.createTenant`'s founding `MEMBER_ADMIN` membership** —
  that bootstrap path is separate from `addMember` and is not touched by
  this feature; the first admin of a newly created tenant remains
  immediately active, not pending. If this is ever wanted, that's a
  separate, explicit decision — not implied by this SPEC.
- **Push/websocket delivery of notifications** — a simple pollable
  "list my unresolved notifications" endpoint is sufficient; no
  real-time delivery mechanism is introduced.
- **Notification preferences/settings** (mute, digest, per-type opt-out,
  etc.) — not requested and not needed for these two use cases.
- **Expiry/TTL on a pending invitation** — a pending invitation stays
  pending indefinitely until accepted or declined; no automatic
  expiration is introduced.
- **Surfacing pending-membership status in the existing tenant
  member-listing/detail endpoints** (`listMembers`/`getMemberDetail`) —
  those are unmodified by this SPEC; if an admin needs to *see* an
  outstanding invitation from the member-management screen (as opposed
  to the invitee's own notification list), that's a follow-up SPEC,
  likely paired with a frontend change.
- **Reuse of this notification mechanism for item 13's profile-edit
  approval flow** — this feature builds the narrowest `Notification`
  model needed for the two membership-acceptance use cases only. It's
  plausible the same entity could later be extended with a new `type`
  value for profile-edit approval, but that extension, including any
  additional fields it might need, is a decision for item 13's own SPEC,
  not assumed or designed here.
- **Any change to `Permission`/`GlobalPermission` enums** — accept/decline
  authorization is based solely on notification-recipient identity
  (REQ-10), not a new permission.

## Decisions / judgment calls

1. **"Tenant owner" (REQ-6) = every active `MEMBER_ADMIN` of that
   tenant.** The codebase has no dedicated "owner" concept distinct from
   the `MEMBER_ADMIN` role — `createTenant` just creates the first member
   as `MEMBER_ADMIN` like any other. Interpreted "tenant owner" in the
   confirmed requirement as "the tenant's `MEMBER_ADMIN`(s)," plural,
   since nothing in the schema privileges a single "first" admin over any
   later-promoted one.
2. **Re-invitation after decline/removal always resets to pending (REQ-13)**
   — chosen for consistency and to avoid a stale prior acceptance
   silently reactivating without the invitee's fresh consent, matching
   this feature's whole premise.
3. **Declining does not itself notify the admin/inviter** — the confirmed
   requirements only specify notification-on-accept (REQ-6); silence on
   decline is treated as "not required," not as an oversight to silently
   fill in.
4. **REQ-12 needs no new code** — `TenantService.removeMember` already
   performs exactly the soft-deactivation the confirmed requirement 5
   describes (sets `active = false`, audit-logged, `MEMBER_ADMIN`-or-staff
   gated). This SPEC includes it as an acceptance criterion to verify,
   not as new work.
5. **No Tier 3 conflict found.** These two backlog items already carry
   detailed, dated (2026-07-26), explicitly-confirmed requirements from
   the product owner in `PROJECT_STATUS.md`, and nothing here reverses an
   existing SPEC's "Out of scope" line.
