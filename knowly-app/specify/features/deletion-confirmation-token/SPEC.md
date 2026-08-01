# SPEC — Deletion confirmation token (UI)

## Context and motivation

The backend's `deletion-confirmation-token` feature (see
`knowly-api/specify/features/deletion-confirmation-token/SPEC.md`)
introduces a system-wide mechanism where any deletion requires a
server-generated, single-use, short-lived "security word" to be supplied
with the delete call, and wires this mechanism into **all six** existing
delete endpoints: article deletion, tenant member removal, tenant
permission revocation, tenant access-group unassignment, staff
permission revocation, and staff access-group unassignment — each with
its own sibling `POST .../deletion-confirmation-token` generation
endpoint.

This SPEC evolves the shared `ConfirmDialogComponent` so the
confirmation dialog itself fetches and displays the generated word when
it opens, and requires the user to retype it exactly before the Confirm
action is enabled — turning the existing "are you sure?" prompt into a
real proof-of-intent step backed by the new server-side precondition,
with no separate "generate word" step inserted before it. Because
`ConfirmDialogComponent` is shared, this word-confirmation behavior
automatically applies everywhere the component is used for a delete
action — but as of today, only `article-management`'s delete flow (see
that SPEC's REQ-11–13) actually routes through it. The other five
deletion actions in the product currently delete immediately on click,
with no confirmation dialog and no backend token check at all:

- Tenant member removal (`members-page.component.ts`'s
  `onRemoveMember`).
- Tenant permission revocation (`member-detail-panel.component.ts`'s
  `onTogglePermission`, revoke branch).
- Tenant access-group unassignment
  (`member-detail-panel.component.ts`'s `onUnassignAccessGroup`).
- Staff/global permission revocation
  (`staff-user-detail-panel.component.ts`'s `onTogglePermission`, revoke
  branch).
- Staff/global access-group unassignment
  (`staff-user-detail-panel.component.ts`'s `onUnassignAccessGroup`).

This SPEC brings all five of these into `ConfirmDialogComponent` and its
word-confirmation flow, so that every deletion in the product — not just
article deletion — has both a confirmation step and the server-enforced
proof-of-intent guarantee.

**Paste-blocking constraint (added after initial approval):** proof of
intent only holds if the user actually reads and manually types the
word rather than copy-pasting it from the display straight into the
input — a paste defeats the entire purpose of the retype step. This
SPEC therefore requires the retype input to reject paste/drop input by
any mechanism (keyboard shortcut, browser menu, drag-and-drop). This is
a single requirement on the shared `ConfirmDialogComponent` (REQ-22
below), not five separate per-flow requirements, since all six delete
dialogs (article plus the five added by this SPEC) render the same
component/input.

## User stories

- As a user about to delete an article, remove a tenant member, revoke a
  permission, or unassign an access group, I want the confirmation
  dialog to show me a word I have to type back before I can confirm, so
  a misclick can't delete something by accident.
- As a user who reopens or waits too long on the confirmation dialog, I
  want a clear error and a fresh word if my previous one expired or was
  already used, rather than a confusing failure.
- As a user, I want the word itself to be prominent and easy to read in
  the dialog, since I have to type it back exactly by hand.
- As a tenant member admin revoking a permission or unassigning an
  access group, or a staff admin doing the equivalent globally, I want
  the same confirm-and-retype safeguard article deletion has, since
  today those actions happen with a single click and no confirmation at
  all.
- As the system, I want the retype step to be an actual proof that the
  user read the word, not just a formality they can paste past, so a
  misclick still can't slip through disguised as a "confirmed" deletion.

## Requirements (EARS/GEARS)

- **REQ-1 [Event-Driven]** When the article deletion confirmation dialog
  (`ConfirmDialogComponent`, per `article-management`'s REQ-11) opens,
  the system shall request a new deletion confirmation token for that
  specific article from the backend and display the returned word
  prominently in the dialog.
- **REQ-2 [Ubiquitous]** The deletion confirmation dialog shall render a
  text input in which the user must retype the displayed word.
- **REQ-3 [State-Driven]** While the text the user has typed does not
  exactly match the displayed word, the system shall keep the dialog's
  Confirm action disabled.
- **REQ-4 [Event-Driven]** When the user's typed text exactly matches
  the displayed word, the system shall enable the dialog's Confirm
  action.
- **REQ-5 [Event-Driven]** When the user activates Confirm with a
  matching word, the system shall call `DELETE
  /api/tenants/{tenantId}/articles/{articleId}` supplying that word,
  per `article-management`'s REQ-12 (successful confirmation removes the
  article from the list).
- **REQ-6 [State-Driven]** While the deletion confirmation token request
  (REQ-1) is in flight, the system shall show a loading state in the
  dialog and keep the Confirm action disabled.
- **REQ-7 [Unwanted Behavior]** If requesting the deletion confirmation
  token (REQ-1) fails, then the system shall show a clear error in the
  dialog, offer a way to retry the request, and keep the Confirm action
  disabled until a word has successfully been fetched.
- **REQ-8 [Unwanted Behavior]** If the backend rejects the delete call
  (REQ-5) because the confirmation token is invalid, expired, or already
  used, then the system shall show a clear error in the dialog, discard
  the stale word and input, automatically request a fresh token (per
  REQ-1), and keep the article in the list (no deletion performed).
- **REQ-9 [Unwanted Behavior]** If the user dismisses or cancels the
  dialog (per `article-management`'s REQ-13) after a word has been
  fetched, then the system shall discard that word and the typed input
  without attempting to invalidate or otherwise act on the token, leaving
  the article unchanged.
- **REQ-10 [Ubiquitous]** The system shall never log or otherwise expose
  the deletion confirmation word anywhere other than the dialog's own
  display and input (no browser console logging), consistent with this
  project's rule against logging sensitive values client-side.
- **REQ-11 [Event-Driven]** When a user activates the remove action for
  a tenant member (`members-page.component.ts`'s "remove" button), the
  system shall open `ConfirmDialogComponent` rather than removing the
  member immediately, and that dialog instance shall follow REQ-1
  through REQ-10 (word fetch/display/retype/loading/error/cancel
  behavior), requesting its confirmation token scoped to that specific
  membership instance.
- **REQ-12 [Event-Driven]** When the user activates Confirm with a
  matching word in the member-removal dialog (per REQ-11), the system
  shall call `DELETE
  /api/tenants/{tenantId}/members/{membershipId}` supplying that word
  and, on success, remove the member from the list.
- **REQ-13 [Event-Driven]** When a user activates the revoke action for
  a directly-granted tenant permission
  (`member-detail-panel.component.ts`'s permission toggle, revoke
  branch), the system shall open `ConfirmDialogComponent` rather than
  revoking the permission immediately, and that dialog instance shall
  follow REQ-1 through REQ-10, requesting its confirmation token scoped
  to that specific (membership, permission) instance.
- **REQ-14 [Event-Driven]** When the user activates Confirm with a
  matching word in the tenant-permission-revocation dialog (per
  REQ-13), the system shall call `DELETE
  /api/tenants/{tenantId}/members/{membershipId}/permissions/{permission}`
  supplying that word and, on success, refresh the member detail so the
  permission no longer shows as granted.
- **REQ-15 [Event-Driven]** When a user activates the unassign action
  for a tenant access group (`member-detail-panel.component.ts`'s
  "unassign" button), the system shall open `ConfirmDialogComponent`
  rather than unassigning the group immediately, and that dialog
  instance shall follow REQ-1 through REQ-10, requesting its
  confirmation token scoped to that specific (membership, access group)
  instance.
- **REQ-16 [Event-Driven]** When the user activates Confirm with a
  matching word in the tenant-access-group-unassignment dialog (per
  REQ-15), the system shall call `DELETE
  /api/tenants/{tenantId}/members/{membershipId}/access-groups/{accessGroupId}`
  supplying that word and, on success, refresh the member detail so the
  group no longer appears assigned.
- **REQ-17 [Event-Driven]** When a user activates the revoke action for
  a directly-granted staff/global permission
  (`staff-user-detail-panel.component.ts`'s permission toggle, revoke
  branch), the system shall open `ConfirmDialogComponent` rather than
  revoking the permission immediately, and that dialog instance shall
  follow REQ-1 through REQ-10, requesting its confirmation token scoped
  to that specific (user, permission) instance.
- **REQ-18 [Event-Driven]** When the user activates Confirm with a
  matching word in the staff-permission-revocation dialog (per REQ-17),
  the system shall call `DELETE
  /api/users/{userId}/permissions/{permission}` supplying that word and,
  on success, refresh the staff user detail so the permission no longer
  shows as granted.
- **REQ-19 [Event-Driven]** When a user activates the unassign action
  for a staff/global access group
  (`staff-user-detail-panel.component.ts`'s "unassign" button), the
  system shall open `ConfirmDialogComponent` rather than unassigning the
  group immediately, and that dialog instance shall follow REQ-1 through
  REQ-10, requesting its confirmation token scoped to that specific
  (user, access group) instance.
- **REQ-20 [Event-Driven]** When the user activates Confirm with a
  matching word in the staff-access-group-unassignment dialog (per
  REQ-19), the system shall call `DELETE
  /api/users/{userId}/access-groups/{accessGroupId}` supplying that word
  and, on success, refresh the staff user detail so the group no longer
  appears assigned.
- **REQ-21 [Unwanted Behavior]** If the user dismisses or cancels any of
  the four newly-added confirmation dialogs (member removal, tenant
  permission revocation, tenant access-group unassignment, staff
  permission revocation, staff access-group unassignment) after a word
  has been fetched, then the system shall discard that word and the
  typed input without attempting to invalidate or otherwise act on the
  token, leaving the underlying resource unchanged — mirroring REQ-9.
- **REQ-22 [Unwanted Behavior]** If the user attempts to paste into, or
  drag-and-drop text onto, the retype input of any `ConfirmDialogComponent`
  instance (via keyboard shortcut, browser/OS context-menu "Paste", or
  drag-and-drop), then the system shall block that input entirely (the
  input's content shall remain unchanged by the attempted paste/drop)
  and shall not treat the blocked attempt as a match even if the pasted
  text would otherwise have matched the displayed word. This applies
  uniformly to every dialog instance covered by this SPEC — the article
  delete dialog (REQ-1–REQ-10) and all five newly-added dialogs
  (REQ-11–REQ-21) — since they all share the same retype input.

## Non-functional requirements

- Accessibility: the displayed word and retype input are both reachable
  and operable via keyboard and screen reader (the word is rendered as
  readable text, not an image; the input carries an accessible label
  referencing what it expects). Existing dialog accessibility
  (focus-trapped, `Escape`-dismissible) is unchanged. Applies to every
  dialog instance introduced by this SPEC, not just the article-delete
  one.
- Accessibility: blocking paste (REQ-22) must not block normal typing,
  autofill-free manual keyboard input, `Tab` focus navigation, or
  assistive-technology input methods that synthesize regular keystrokes
  — only the paste/drop mechanisms themselves are blocked.
- Performance: fetching the confirmation token on dialog open must not
  introduce a perceptible delay before the dialog itself becomes visible
  — the dialog opens immediately in a loading state (REQ-6) rather than
  waiting for the fetch to resolve before rendering. Applies uniformly
  across all six delete flows.
- Responsiveness: the dialog's new word-display and input elements follow
  the same layout conventions as the rest of `ConfirmDialogComponent`
  (already responsive).

## Acceptance criteria

- [x] Opening the article deletion confirmation dialog triggers a
      request for a new deletion confirmation token scoped to that
      article.
- [x] The dialog displays the returned word prominently once fetched.
- [x] The dialog shows a loading state, with Confirm disabled, while the
      token request is in flight.
- [x] The dialog provides a text input for retyping the word.
- [x] Confirm stays disabled until the typed text exactly matches the
      displayed word.
- [x] Confirm becomes enabled once the typed text exactly matches.
- [x] Confirming calls the article delete endpoint with the matched word
      and, on success, removes the article from the list.
- [x] A failed token-fetch request shows an error with a retry option and
      keeps Confirm disabled.
- [x] A delete call rejected for an invalid/expired/used token shows an
      error, discards the stale word/input, and automatically re-fetches
      a fresh token.
- [x] Cancelling/dismissing the dialog after a word was fetched discards
      it without performing any deletion, leaving the article list
      unchanged (per `article-management`'s REQ-13).
- [x] The confirmation word never appears in browser console logs.
- [x] Removing a tenant member (`members-page.component.ts`) opens
      `ConfirmDialogComponent` with the word-confirmation flow (fetch,
      display, retype-to-enable-Confirm, loading, fetch-error/retry,
      invalid-token-error/re-fetch, cancel-discards) instead of deleting
      immediately, and Confirm calls the member-removal delete endpoint
      with the matched word.
- [x] Revoking a directly-granted tenant permission
      (`member-detail-panel.component.ts`) opens
      `ConfirmDialogComponent` with the same word-confirmation flow
      instead of revoking immediately, and Confirm calls the
      permission-revocation delete endpoint with the matched word.
- [x] Unassigning a tenant access group
      (`member-detail-panel.component.ts`) opens
      `ConfirmDialogComponent` with the same word-confirmation flow
      instead of unassigning immediately, and Confirm calls the
      access-group-unassignment delete endpoint with the matched word.
- [x] Revoking a directly-granted staff/global permission
      (`staff-user-detail-panel.component.ts`) opens
      `ConfirmDialogComponent` with the same word-confirmation flow
      instead of revoking immediately, and Confirm calls the staff
      permission-revocation delete endpoint with the matched word.
- [x] Unassigning a staff/global access group
      (`staff-user-detail-panel.component.ts`) opens
      `ConfirmDialogComponent` with the same word-confirmation flow
      instead of unassigning immediately, and Confirm calls the staff
      access-group-unassignment delete endpoint with the matched word.
- [x] Cancelling/dismissing any of these four newly-added dialogs after
      a word was fetched discards it without performing any deletion,
      leaving the underlying resource unchanged.
- [x] Pressing a paste keyboard shortcut (e.g. Ctrl/Cmd+V) while the
      retype input is focused, in every one of the six delete dialogs,
      does not insert the pasted content into the field.
- [x] Using the browser/OS right-click context menu's "Paste" action on
      the retype input, in every one of the six delete dialogs, does not
      insert the pasted content into the field.
- [x] Dragging and dropping text onto the retype input, in every one of
      the six delete dialogs, does not insert the dropped content into
      the field.
- [x] Manual keystroke-by-keystroke typing into the retype input still
      works normally in every dialog after paste-blocking is added
      (paste-blocking does not regress ordinary typing, `Tab` focus
      navigation, or assistive-technology keystroke input).

## Out of scope

- A separate, standalone "generate word" step or screen before any
  confirmation dialog opens — the fetch happens automatically as part of
  the dialog opening (REQ-1 and its REQ-11/13/15/17/19 counterparts),
  not as a distinct user-triggered step.
- Copy-to-clipboard affordance for the displayed word.
- Any visual/UX redesign of `ConfirmDialogComponent` beyond adding the
  word display and retype input.
- Any deletion-shaped UI action not explicitly named in this SPEC (i.e.
  any delete flow introduced after this SPEC is written) — wiring a
  future new delete UI into this pattern is a separate SPEC when that
  flow is introduced, contingent on the backend SPEC wiring the
  matching endpoint first.
- Blocking paste anywhere else in the product outside this specific
  retype input (REQ-22) — no general anti-paste policy is introduced for
  any other form field.
- Detecting or warning about non-paste automation (e.g. scripted
  `dispatchEvent` calls bypassing the browser's native paste path) —
  REQ-22 blocks the standard user-facing paste/drop mechanisms only; it
  is a usability/intent safeguard, not a defense against a
  programmatically-controlled client.
</content>
