# SPEC — bootstrap-profile-completion (frontend)

> **Amendment (2026-08-02, product owner decision — RG removal):** per
> `identity-profile-model-v2/SPEC.md`'s own 2026-08-02 RG-removal
> amendment (LGPD data-minimization), `rg`/`rgOrgaoEmissor` are removed
> entirely from this SPEC's mandatory field set, request body, and
> screen — no field, no input, no display. This SPEC was implemented and
> committed **today**, before this decision (commit `90777be`), with
> REQ-3 explicitly listing RG and RG issuing body as mandatory; that
> requirement (and every acceptance criterion/DTO reference built on it)
> is struck through and marked **~~(superseded 2026-08-02 — RG removed)~~**
> rather than deleted, per this repo's "collected, then removed"
> traceability discipline — this is now a PLAN/TASKS-level follow-up
> (out of scope for this pass) to actually remove the already-shipped RG
> inputs from the implemented screen.

> **Amendment (2026-08-02, product owner decision, direct instruction —
> birth_date removal):** in the same live conversation as the RG
> decision above, the product owner directly instructed: "tira data de
> nascimento também" (take out birth date too), per
> `identity-profile-model-v2/SPEC.md`'s own companion amendment (same
> date, same reasoning). `birthDate` is removed entirely from this
> SPEC's mandatory field set, request body, and screen — no field, no
> input, no display. This SPEC was implemented and committed **today**,
> before this decision (commit `90777be`), with REQ-3 explicitly listing
> `birthDate` as mandatory alongside `fullName`/`cpf`/RG; that
> requirement (and every acceptance criterion/DTO reference built on it)
> is struck through and marked **~~(superseded 2026-08-02 — birth_date
> removed)~~**, same traceability discipline as RG — this is likewise a
> PLAN/TASKS-level follow-up (out of scope for this pass) to actually
> remove the already-shipped `birthDate` input from the implemented
> screen.

> **Amendment (2026-08-02, product owner decision):** this SPEC's
> non-functional requirements originally carried over `user-profile-v2`'s
> "no CPF/RG format/checksum validation, no masked input" line verbatim.
> That line is reversed for masked *input display* the same way it was
> reversed in `user-profile-v2/SPEC.md`'s own companion amendment — see
> new REQ-15 below. Client-side format/checksum *validation* remains out
> of scope here; the backend (`identity-profile-model-v2/SPEC.md`'s
> REQ-4a) normalizes and, for `cpf` only, checksum-validates every field
> this screen submits, regardless of what this screen sends. **(RG struck
> from this line 2026-08-02, same day, second amendment — RG removed
> entirely, see amendment above.)** `birthDate` was never a masked
> field, so it is unaffected by this particular amendment — its removal
> is covered entirely by the birth_date-removal amendment above.

> The what and the why. No technical implementation details.

## Context and motivation

The backend feature `mandatory-complete-profile`
(`knowly-api/specify/features/mandatory-complete-profile/SPEC.md`,
shipped, marked done in `PROJECT_STATUS.md`) put the system's bootstrap
`STAFF_ADMIN` account — the very first account, created by
`staff-bootstrap-user`'s migration with nothing but an email — into a
**pending-profile-completion** state on creation. While pending, the
backend's `ProfileCompletionFilter` blocks every request except
authentication, `GET /api/users/me/profile`, and the dedicated
self-completion endpoint `POST /api/users/me/profile/complete`
(`UserProfileController.completeOwnProfile`,
`UserProfileService.completeOwnProfile`) — this last endpoint is the one
and only place this account can supply its missing profile fields
without anyone else's approval (no other `STAFF_ADMIN` exists yet to
approve an edit request; see that SPEC's judgment call 3).

That backend endpoint has been live since `mandatory-complete-profile`
shipped, but `knowly-app` never built a screen for it — there is no
reference to `profile/complete` anywhere in `knowly-app/src`. Today, when
the bootstrap account hits the 409 `PROFILE_COMPLETION_REQUIRED` signal,
`auth.interceptor.ts` redirects it to the existing generic `/profile`
page (`OwnProfilePageComponent`). That page is built for
`user-profile-v2`'s entirely different model (self-edit **always** goes
through a pending approval request; avatar upload is the only direct
self-edit) — neither of its two actions works for a pending bootstrap
account: `POST /api/users/me/profile/edit-requests` and
`POST /api/users/me/profile/avatar` are both **outside**
`ProfileCompletionFilter`'s allowlist while pending, so both 409 again,
and `OwnProfilePageComponent`'s own copy ("a profile change request is
already pending approval") is actively misleading — no such request
exists; the account is simply stuck.

This feature is the frontend half `mandatory-complete-profile`'s own
"Out of scope" section named as needed and not yet written: a dedicated
screen that calls `POST /api/users/me/profile/complete`, plus wiring the
login response's already-existing `pendingProfileCompletion` flag
(`VerifyCodeResponseDto`, currently unread anywhere in `knowly-app/src`)
so the app can recognize this state proactively instead of only
reactively via a 409.

**This SPEC does not reopen any backend rule.** The mandatory field set,
the "no approval needed, self-only, one-time" nature of this endpoint,
and the full-block allowlist are all carried over verbatim from
`mandatory-complete-profile/SPEC.md` (as amended 2026-08-02 to remove RG
and, separately, `birth_date` — see amendments at top of this SPEC) —
only screen layout, form structure, and client-side flow are this
SPEC's own judgment calls.

**API contract (read-only reference, not re-litigated here):**

| Endpoint | Purpose |
|---|---|
| `GET /api/users/me/profile` | Caller's own profile (allowed while pending) |
| `POST /api/users/me/profile/complete` | One-time, no-approval self-completion (allowed while pending; 409 `PROFILE_ALREADY_COMPLETE` if called again after success) |
| Login/`verify` response | `VerifyCodeResponseDto.pendingProfileCompletion: boolean` |

Request body (`MandatoryProfileFieldsDto`, all fields required except
`address.numero`/`address.complemento`):

```
fullName: string
~~birthDate: string (date)~~
cpf: string
~~rg: string~~
~~rgOrgaoEmissor: string~~
address: { cep, logradouro, numero?, complemento?, bairro, cidade, estado, pais }
contacts: [{ type: 'PHONE'|'WHATSAPP'|'EMAIL'|'OTHER', value, label?, isPrimary }, ...]  // at least 1
```

**(`rg`/`rgOrgaoEmissor` struck 2026-08-02 — RG removed entirely; see
amendment at top of this SPEC. `birthDate` struck 2026-08-02 —
birth_date removed entirely; see second amendment at top of this SPEC.
The already-implemented screen currently still sends all three fields —
removing them from the actual submitted payload/form is a PLAN/TASKS-
level follow-up, out of scope for this SPEC amendment pass.)**

Response on success: `UserProfileDto` — the same shape
`ProfileService`'s existing `UserProfile` interface already models
(`userId`, `email`, `fields` nested `ProfileFields`, `avatarUrl`).

**Not allowed while pending (both 409 `PROFILE_COMPLETION_REQUIRED`,
confirmed by reading `ProfileCompletionFilter`'s allowlist):**
`POST /api/users/me/profile/edit-requests`,
`POST /api/users/me/profile/avatar`, and every other endpoint in the
system. This screen must not offer avatar upload or any other action —
there is nowhere else for the pending account to go.

## User stories

- As the bootstrap `STAFF_ADMIN`, on my very first login, I want a clear
  screen asking me to fill in my full profile (name, CPF, address, at
  least one contact) — not a generic profile page that offers actions I
  can't actually use yet — so I understand what's required and can get
  past it in one shot. **(RG struck from this story 2026-08-02 — RG
  removed entirely. `birth date` struck 2026-08-02 — birth_date removed
  entirely.)**
- As the bootstrap `STAFF_ADMIN`, I want to be sent to this screen
  automatically the moment I log in while my profile is still
  incomplete, not just after happening to trigger a 409 on some
  unrelated action.
- As the bootstrap `STAFF_ADMIN`, once I successfully submit my full
  profile, I want to land in the normal app immediately, with no leftover
  banner or stuck state.
- As the bootstrap `STAFF_ADMIN`, if I submit an incomplete or invalid
  form, I want clear, field-level feedback — not a bare network-error
  screen — so I can fix it and resubmit without confusion.
- **As the bootstrap `STAFF_ADMIN`, I want my CPF, CEP, and phone
  number to display with the usual punctuation as I type**, matching the
  same masking behavior `user-profile-v2` gives every other user, rather
  than this one-time screen feeling less polished than the ordinary
  profile form. **(RG struck from this story 2026-08-02 — RG removed
  entirely.)**

## Requirements (EARS/GEARS)

### Recognizing the pending state

- **REQ-1 [Event-Driven]** When the login/verify-code flow succeeds and
  the response's `pendingProfileCompletion` is `true`, the system shall
  navigate directly to this feature's dedicated completion screen,
  without first rendering `/welcome`, the onboarding tour, or any other
  post-login destination.
- **REQ-2 [Unwanted Behavior]** If any HTTP response anywhere in the app
  returns 409 with `error.code === 'PROFILE_COMPLETION_REQUIRED'`, then
  the system shall navigate to this feature's dedicated completion
  screen (not the generic `/profile` page). **Resolved:** implemented by
  changing `auth.interceptor.ts`'s existing `PROFILE_COMPLETION_REQUIRED`
  redirect target from `/profile` to this feature's new route — the
  interceptor already isolates this exact response code to one branch
  (added when the login-loop bug was fixed earlier in this same
  conversation), so this is a one-line target change, not a new
  mechanism. PLAN.md/TASKS.md implement it as such; no design choice
  remains open here.

### The completion screen itself

- **REQ-3 [Ubiquitous]** ~~The completion screen shall present a form
  collecting exactly the mandatory field set: full name, birth date,
  CPF, RG, RG issuing body (`rgOrgaoEmissor`), a structured address
  (`cep`/`logradouro`/`numero`/`complemento`/`bairro`/`cidade`/`estado`/
  `pais`, with `numero`/`complemento` optional and every other address
  field required), and a contacts list requiring at least one entry
  (`type`/`value`/`label`/`isPrimary`, one of `PHONE`/`WHATSAPP`/`EMAIL`/
  `OTHER`).~~ **(superseded 2026-08-02 — RG removed)** ~~The completion
  screen shall present a form collecting exactly the mandatory field
  set: full name, birth date, CPF, a structured address
  (`cep`/`logradouro`/`numero`/`complemento`/`bairro`/`cidade`/`estado`/
  `pais`, with `numero`/`complemento` optional and every other address
  field required), and a contacts list requiring at least one entry
  (`type`/`value`/`label`/`isPrimary`, one of `PHONE`/`WHATSAPP`/`EMAIL`/
  `OTHER`).~~ **(superseded 2026-08-02, same day, second amendment —
  birth_date removed)** The completion screen shall present a form
  collecting exactly the mandatory field set: full name, CPF, a
  structured address (`cep`/`logradouro`/`numero`/`complemento`/
  `bairro`/`cidade`/`estado`/`pais`, with `numero`/`complemento` optional
  and every other address field required), and a contacts list requiring
  at least one entry (`type`/`value`/`label`/`isPrimary`, one of
  `PHONE`/`WHATSAPP`/`EMAIL`/`OTHER`).
- **REQ-4 [Ubiquitous]** The completion screen shall present its
  requirement as mandatory, one-time, and self-completed — its copy
  shall not use `/profile`'s "pending approval"/"edit request" language,
  since no approval step exists for this flow.
- **REQ-5 [Unwanted Behavior]** If the caller attempts to submit the
  form with any required field empty, then the system shall block
  submission client-side and show field-level validation messages,
  without calling the backend.
- **REQ-6 [Event-Driven]** When the caller submits a fully-filled form,
  the system shall call `POST /api/users/me/profile/complete` with the
  full field set.
- **REQ-7 [Event-Driven]** When that call succeeds, the system shall
  navigate away from the completion screen to the app's normal
  post-login landing destination (the same destination the session
  would have reached had `pendingProfileCompletion` been `false`), with
  no further gate or confirmation step.
- **REQ-8 [Unwanted Behavior]** If that call is rejected with 400
  (server-side field validation failure, including a checksum-invalid
  `cpf` per `identity-profile-model-v2/SPEC.md`'s REQ-4a), then the
  system shall show a clear, non-technical message identifying which
  field(s) failed, without discarding the caller's already-entered
  values.
- **REQ-9 [Unwanted Behavior]** If that call is rejected with 409
  `PROFILE_ALREADY_COMPLETE` (the account was already completed by a
  prior successful submission, e.g. a stale screen reloaded after
  success), then the system shall treat it the same as REQ-7 — navigate
  to the normal post-login destination — rather than showing an error,
  since the account is in the state the caller wanted to reach anyway.
- **REQ-10 [Unwanted Behavior]** If that call fails with a network/5xx
  error, then the system shall show the existing app-wide network-error
  state and leave the caller's entered values in the form so they are
  not lost.
- **REQ-11 [Ubiquitous]** The completion screen shall not render an
  avatar-upload control, an edit-request submission path, or any
  navigation to other parts of the app (nav menu, other routes) — every
  one of those calls 409s while pending (see the API contract table
  above), so offering them would reproduce the exact stuck state this
  feature exists to fix.
- **REQ-12 [Ubiquitous]** The completion screen shall not render `email`
  as an editable field — it may be shown read-only for the caller's own
  orientation, consistent with `user-profile-v2`'s existing
  read-only-`email` convention, but is never part of the submitted
  payload.

### Contacts sub-form (reuses the existing shape, minimum-one constraint added)

- **REQ-13 [Ubiquitous]** The contacts section shall let the caller add,
  edit, and remove entries, select a `type` from the four supported
  values, and mark at most one entry per `type` as primary — the same
  behavior already specified for `user-profile-v2`'s contacts editor
  (REQ-6/REQ-7 there), reused here rather than re-implemented.
- **REQ-14 [Unwanted Behavior]** If the caller attempts to submit with
  zero contacts, then the system shall block submission client-side
  with a clear message, matching this SPEC's completeness definition
  (at least one contact is mandatory here, unlike `user-profile-v2`'s
  own-profile edit form where contacts may already be empty for an
  established account).

### Masked input — display-only (added 2026-08-02, product owner decision)

- **REQ-15 [Ubiquitous]** The `cpf`, `cep` (within the structured
  address), and any contact entry whose `type` is `PHONE`/`WHATSAPP`
  shall apply the same mask-as-you-type display formatting specified in
  `user-profile-v2/SPEC.md`'s REQ-21 — reusing that shared sub-form
  piece (per the non-functional requirements below and
  `user-profile-v2`'s judgment call 6) rather than a separate
  implementation. As with `user-profile-v2`'s REQ-22/23, the value
  submitted to `POST /api/users/me/profile/complete` remains
  unmasked/plain regardless of display, and this masking does not
  constitute client-side format/checksum validation — the form does not
  block submission or show a validation error based on the mask or a
  checksum failing; that check is server-side only (REQ-8 above).
  **(`rg` struck from this requirement 2026-08-02 — RG removed
  entirely, matching `user-profile-v2/SPEC.md`'s own REQ-21 narrowing on
  the same date. `birthDate` was never a masked field, so it is
  unaffected here — its removal is covered entirely by the
  birth_date-removal amendment at the top of this SPEC.)**

## Non-functional requirements

- Design: follows "Ink & Signal," reuses existing shared components
  (the address/contacts sub-form pieces already built for
  `user-profile-v2`'s `ProfileFieldsFormComponent`, including its
  masking behavior per REQ-15) wherever the field shapes match — no new
  component library, no new dependency.
- Accessibility: the full form (address block, contacts list
  add/remove/edit, validation messages) is fully keyboard-operable with
  clear focus states, matching the bar already set for
  `user-profile-v2` (including its masking-related accessibility
  requirement).
- Security: `cpf` values are never logged to the browser console
  and never appear in any client-side error message beyond naming the
  field. (`rg` struck 2026-08-02 — RG removed entirely.)
- **No CPF/CEP/phone format or checksum *validation* on the
  frontend** — masking (REQ-15) is display-only and does not imply or
  require validation; the frontend never blocks submission based on
  checksum correctness. **Amended 2026-08-02:** the earlier "no masked
  input" wording is reversed (see REQ-15) — only client-side
  format/checksum *validation* and CEP-to-address autofill/lookup remain
  out of scope, matching `user-profile-v2`'s identical, already-amended
  scope discipline. **(`RG` struck from this line 2026-08-02, same day,
  second amendment — RG removed entirely.)**
- This screen is reachable only by a session whose `pendingProfileCompletion`
  is (or was) `true` — it performs no authorization check of its own
  beyond calling the backend endpoint, which independently re-enforces
  who may call it; this SPEC is never the actual authorization boundary.

## Acceptance criteria

- [ ] On a successful login where `pendingProfileCompletion` is `true`,
      the session is navigated directly to the completion screen before
      anything else renders.
- [ ] A 409 `PROFILE_COMPLETION_REQUIRED` anywhere in the app navigates
      to the completion screen, not the generic `/profile` page.
- [ ] ~~The completion screen renders the full mandatory field set
      (name, birth date, CPF, RG, RG issuing body, structured address,
      contacts with a minimum of one entry) with no avatar-upload
      control and no navigation to other parts of the app.~~
      **(superseded 2026-08-02 — RG removed)** ~~The completion screen
      renders the full mandatory field set (name, birth date, CPF,
      structured address, contacts with a minimum of one entry) with no
      avatar-upload control and no navigation to other parts of the
      app.~~ **(superseded 2026-08-02, same day, second amendment —
      birth_date removed)** The completion screen renders the full
      mandatory field set (name, CPF, structured address, contacts with
      a minimum of one entry) with no avatar-upload control and no
      navigation to other parts of the app.
- [ ] Submitting with any required field (including zero contacts)
      empty is blocked client-side with field-level messages, no
      backend call made.
- [ ] A fully-filled submission calls `POST /api/users/me/profile/complete`
      and, on success, navigates to the normal post-login destination
      with no further gate.
- [ ] A 409 `PROFILE_ALREADY_COMPLETE` response is treated as success
      (navigates onward), not shown as an error.
- [ ] A 400 response shows which field(s) failed without losing entered
      values (including a checksum-invalid `cpf` rejection); a
      network/5xx failure shows the existing network-error state, also
      without losing entered values.
- [ ] `email` is shown read-only if at all, never editable, never
      submitted.
- [ ] Typing into the `cpf`, `cep`, and phone-type contact fields shows
      standard mask punctuation live. The submitted payload is
      unmasked/plain in every case. **(The former "`rg` shows a
      best-effort digit grouping" clause is struck 2026-08-02 — RG
      removed entirely.)**
- [ ] Masking never blocks submission and never triggers a client-side
      validation error on its own.
- [ ] No `rg`/`rgOrgaoEmissor` field, input, or display exists anywhere
      on this screen, and neither is sent in the `POST
      /api/users/me/profile/complete` request body. (Added 2026-08-02 —
      this is a PLAN/TASKS-level follow-up against the
      already-implemented screen, not yet satisfied as of this
      amendment.)
- [ ] No `birthDate` field, input, or display exists anywhere on this
      screen, and it is not sent in the `POST
      /api/users/me/profile/complete` request body. (Added 2026-08-02 —
      this, too, is a PLAN/TASKS-level follow-up against the
      already-implemented screen, not yet satisfied as of this
      amendment.)
- [ ] `npm run format:check && npm test && npm run build && npm run lint`
      all pass.

## Out of scope

- Any change to the backend's completeness definition, field
  validation, encryption/blind-index handling, or the two-mechanism
  design (`mandatory-complete-profile` owns all of that, untouched
  here) — this includes the normalization/CPF-checksum mechanics added
  by that SPEC's own 2026-08-02 amendment, and the RG-removal and
  birth_date-removal decisions themselves; this screen only submits
  values and displays whatever 400 message the backend returns, it does
  not re-implement or duplicate that logic.
- Any change to `staff-user-provisioning`'s or `TenantService.addMember`'s
  own creation-time forms — those already require a complete profile at
  creation time server-side (REQ-7/REQ-8 of the backend SPEC); this
  screen is the bootstrap-account-only, post-creation exception, not a
  general "complete your profile" flow for anyone else.
- `user-profile-v2`'s own screens (`/profile`, the edit-request inbox,
  the other-user detail-panel section) — untouched; this is a new,
  separate screen for a narrower, mutually-exclusive state (a pending
  bootstrap account cannot reach any of those screens' actions anyway,
  per the allowlist).
- Avatar upload from this screen — explicitly excluded (REQ-11); the
  bootstrap account can upload an avatar later, once past this screen
  and no longer pending, via the existing `/profile` page.
- Any grace period, "skip for now," or partial-completion save/draft
  mechanism — the backend's `applyMandatoryProfile` is all-or-nothing
  (`@Valid` on the full DTO); no partial-submission UI is implied or
  built here.
- Retrofitting `staff-bootstrap-user`'s migration or any pre-existing
  incomplete account created before this feature shipped — same
  exclusion `mandatory-complete-profile`'s own SPEC already carries.
- Client-side CPF/CEP/phone format/checksum *validation* — unchanged
  exclusion; only masked *display* (REQ-15) was ever reopened by the
  2026-08-02 amendment. (`RG` struck 2026-08-02, same day, second
  amendment — RG removed entirely.)
- **`rg`/`rgOrgaoEmissor` as fields anywhere on this screen** (added
  2026-08-02, product owner decision — LGPD data-minimization; see
  amendment at top of this SPEC). Actually removing the already-
  implemented RG inputs from the shipped screen/DTO usage is a
  PLAN/TASKS-level follow-up, not performed by this SPEC amendment pass.
- **`birthDate` as a field anywhere on this screen** (added 2026-08-02,
  product owner decision, direct instruction — LGPD data-minimization;
  see second amendment at top of this SPEC). Actually removing the
  already-implemented `birthDate` input from the shipped screen/DTO
  usage is likewise a PLAN/TASKS-level follow-up, not performed by this
  SPEC amendment pass.

## Judgment calls (Tier 2 — flag before PLAN.md work starts if any should be reconsidered)

1. **A new, dedicated route/component, not a variant/mode of
   `OwnProfilePageComponent`.** The two screens' actions are almost
   entirely disjoint (this screen: one endpoint, no avatar, no
   approval; `/profile`: avatar upload + always-pending-request editing)
   and their allowed-while-pending vs. allowed-only-once-complete
   surfaces don't overlap — forcing them into one component with a
   pending-account boolean mode would recreate exactly the confusion
   this feature exists to remove. The address/contacts sub-form pieces
   are still reused as shared building blocks (per the non-functional
   requirements above), only the page/route/submit-flow is new.
2. **This screen is reachable via both REQ-1 (proactive, from the login
   response) and REQ-2 (reactive, from any 409)** rather than relying on
   only one — REQ-1 covers the common case (first login) with the best
   UX (no wasted round-trip); REQ-2 is a safety net for any code path
   that doesn't go through the normal login flow (e.g. a page reload
   with an existing session) hitting the gate later than login time.
3. **REQ-9's "treat 409 `PROFILE_ALREADY_COMPLETE` as success"** was
   chosen over showing an error, since by definition the account is
   already in the state the user wants (complete) — the only way to
   reach this case is a stale form resubmission (e.g. double-click,
   back-button-then-resubmit) after a previous successful call, not a
   real failure.
4. **REQ-15's masking reuses `user-profile-v2`'s shared sub-form
   masking implementation rather than a second one** — same rationale
   as judgment call 1's general "reuse shared sub-form pieces" policy;
   whatever mask directive/pipe PLAN.md for `user-profile-v2` settles on
   is the one this screen imports, not a duplicate.
5. **RG removal (2026-08-02) is stated as a requirement here but not yet
   implemented against the already-shipped screen** — this SPEC
   amendment pass only updates the SPEC document per the user's explicit
   instruction to defer PLAN/TASKS work; the actual removal of RG
   inputs/DTO fields from the committed `90777be` implementation is a
   follow-up PLAN/TASKS update, tracked via the struck-through
   acceptance criterion above.
6. **birth_date removal (2026-08-02, direct instruction, same day as the
   RG removal) is treated exactly the same way as judgment call 5** —
   stated as a requirement here but not yet implemented against the
   already-shipped screen; the actual removal of the `birthDate`
   input/DTO field from the committed `90777be` implementation is a
   follow-up PLAN/TASKS update, tracked via its own struck-through
   acceptance criterion above.
</content>
