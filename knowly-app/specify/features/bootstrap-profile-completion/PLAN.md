# PLAN — bootstrap-profile-completion

> The how. Translates the SPEC into concrete technical decisions.
> References SPEC.md.

## Architectural decisions

- **New, dedicated route `/complete-profile`, no guard** — carries the
  same "no guard, backend re-enforces" pattern already used for
  `/profile` and `/profile-edit-requests` in `app.routes.ts`. *Why*:
  the SPEC's own non-functional requirement says this screen "performs
  no authorization check of its own... never the actual authorization
  boundary" — `POST /api/users/me/profile/complete` already 401s an
  unauthenticated caller (existing `authInterceptor` 401 branch sends
  them to `/login` before this screen could do anything) and 409
  `PROFILE_ALREADY_COMPLETE`s a non-pending caller (handled as REQ-9
  success, see below), so a guard here would duplicate enforcement the
  backend already owns, not add real protection. See "AppSec review"
  below for the full walk-through of this reasoning.
- **New standalone `CompleteProfilePageComponent`** in
  `src/app/features/complete-profile/complete-profile-page.component.ts`,
  not a mode/variant of `OwnProfilePageComponent` — this mirrors the
  SPEC's own judgment call 1 verbatim (disjoint action sets, disjoint
  allowed-while-pending surfaces). It composes `ProfileFieldsFormComponent`
  directly (no `AvatarUploadComponent`, no edit-request/pending-banner
  UI, no nav) — REQ-11 is satisfied by omission, not by a flag that
  hides those pieces.
- **`ProfileFieldsFormComponent` gains one new optional input,
  `requireAllFields` (default `false`)** — when `true`: (a) every
  mandatory input (`fullName`, `rg`, `rgOrgaoEmissor`, `cpf`,
  `birthDate`, and every address field except `numero`/`complemento`)
  renders with the native `required` attribute, and (b) submission is
  blocked client-side with a message if `contacts().length === 0`
  (REQ-14), mirroring the existing `contactLimitMessage` pattern with a
  new `contactsRequiredMessage` signal. *Why extend the shared
  component instead of forking it*: REQ-3/13's field/contacts shapes
  are identical to `user-profile-v2`'s existing form — only the
  completeness requirement differs (this SPEC's form requires
  everything; `/profile`'s edit-request form still legitimately allows
  partial edits). Defaulting to `false` means every existing call site
  (`OwnProfilePageComponent`, `ProfileSectionComponent`) is behaviorally
  unchanged; only `CompleteProfilePageComponent` passes `true`. This is
  a Tier 2 call — flagged here rather than silently forking the
  component into two near-duplicates.
- **`ProfileService` gains `completeOwnProfile(dto: MandatoryProfileFields): Observable<UserProfile>`**
  calling `POST /api/users/me/profile/complete`, following the exact
  shape of every other method in that service (thin HTTP wrapper, no
  extra state). A new `MandatoryProfileFields` interface is added
  there too — structurally identical to `ProfileFields` (same
  `fullName`/`birthDate`/`cpf`/`rg`/`rgOrgaoEmissor`/`address`) but
  with `contacts: Omit<Contact, 'id'>[]` (never an `id` — every
  contact submitted here is new by construction) instead of reusing
  `ProfileFields.contacts: Contact[]` as-is.
- **`AuthService.verifyCode`/`verifyPassword` change return type** from
  `Observable<void>` to `Observable<{ pendingProfileCompletion: boolean }>`,
  reading the field straight through from
  `VerifyCodeResponseDto`/the password-verify response (same backend
  `resolveSessionOutcome` computation per `mandatory-complete-profile`'s
  PLAN, so both verify endpoints carry the field identically) — no new
  HTTP call, no new session-state signal; the `tap(() =>
  this.loggedIn.set(true))` side effect is preserved unchanged. *Why
  both verify methods, not just the code flow SPEC REQ-1 names
  explicitly*: the backend computes `pendingProfileCompletion` once, in
  `resolveSessionOutcome`, shared by both the code and password verify
  paths (per that feature's own PLAN) — reading it from only one of the
  two would leave the password-login path silently unhandled for the
  exact account this feature exists for. This is a Tier 2 call (SPEC's
  own wording only names "the login/verify-code flow"); flagged here
  rather than assumed.
- **`LoginPageComponent`'s two success handlers** (`onSubmitCode`,
  `onSubmitPassword`) change their `next` callback from an
  unconditional `router.navigateByUrl('/welcome')` to: navigate to
  `/complete-profile` when `pendingProfileCompletion` is `true`,
  otherwise keep the existing `/welcome` navigation unchanged — this is
  the entire REQ-1 implementation, no new service/state needed since
  the flag arrives synchronously in the same response the `next`
  callback already receives.
- **`auth.interceptor.ts`'s existing `PROFILE_COMPLETION_REQUIRED`
  branch changes its `router.navigateByUrl('/profile')` call to
  `router.navigateByUrl('/complete-profile')`** — the one-line change
  the SPEC's resolved open item already specifies. No other line in
  the interceptor changes; the 401 branch, the `catchError`/
  `throwError` structure, and every other error code's handling are
  untouched.
- **On successful `POST /api/users/me/profile/complete` (REQ-7) and on
  409 `PROFILE_ALREADY_COMPLETE` (REQ-9), `CompleteProfilePageComponent`
  navigates to `/welcome`** — the same literal destination
  `LoginPageComponent`'s `next` handlers already use for a non-pending
  session, satisfying REQ-7's "the same destination the session would
  have reached had `pendingProfileCompletion` been `false`" without
  introducing a second "normal post-login destination" concept to keep
  in sync with `LoginPageComponent`'s own literal.
- **Form/validation approach**: plain template-driven validation via
  `ProfileFieldsFormComponent`'s new `requireAllFields` mode (native
  `required` + the zero-contacts check) — no `ReactiveFormsModule`,
  no new form library, matching this component's existing signal-based,
  non-Reactive-Forms pattern exactly. REQ-5's "block submission,
  show field-level messages, no backend call" is satisfied by the
  browser's native HTML5 constraint validation on `(submit)` (the
  existing `onSubmit(event)` already calls `event.preventDefault()`
  unconditionally — a `required` field left empty stops the
  `submit` event via the browser's own reporting *before* Angular's
  `(submit)` handler fires, when the button is a real `type="submit"`
  inside the `<form>`, which it already is) plus the new zero-contacts
  message for the one case native HTML can't express (REQ-14).
- **DTO mapping at the page-component boundary**: `CompleteProfilePageComponent`
  receives `ProfileFieldsFormSubmission` (`{ fields, contactChanges }`)
  from `ProfileFieldsFormComponent`'s `(submitted)` output — same event
  shape every other consumer of that component already receives — and
  maps only `fields` (ignoring `contactChanges`, which is meaningless
  here: every contact is new, there is no prior state to diff against)
  into `MandatoryProfileFields` by stripping `id` from each contact
  before calling `completeOwnProfile`.
- **Error handling (REQ-8/9/10)**, all in
  `CompleteProfilePageComponent`'s `catchError`, mirroring
  `OwnProfilePageComponent`'s existing shape (`error` signal +
  `ErrorStateComponent` for network/5xx, a dedicated signal for the
  4xx case):
  - 400 → a `fieldErrors` signal set from `err.error` (backend's
    `MethodArgumentNotValidException` → 400 body already carries
    per-field messages elsewhere in this codebase's convention;
    rendered as a list naming the failed field(s), never the raw
    `cpf`/`rg` *values* — only field *names* are ever shown or logged,
    per the SPEC's security NFR). The submitted `fields`/`contacts`
    are left exactly as typed (`formFields` signal is only ever
    updated from the form's own local state, never reset on error).
  - 409 `PROFILE_ALREADY_COMPLETE` → treated identically to success:
    navigate to `/welcome`, no error rendered (REQ-9).
  - Anything else (network/5xx) → `error.set('network')`, rendering
    the existing shared `ErrorStateComponent`, with the form's entered
    values untouched underneath (same "don't reset on failure"
    guarantee as the 400 case).

## AppSec review (self-check per this repo's process — PLAN before TASKS.md)

Question asked: does the new route/redirect logic introduce any way to
reach the completion screen, or call `/complete`, without a valid
pending session, or leak the endpoint to non-bootstrap accounts?

- **Reaching the screen without authentication**: not possible via any
  new mechanism. `/complete-profile` carries no guard, but rendering
  the page alone makes no network call with side effects — the page's
  only outbound call on submit is `POST /api/users/me/profile/complete`,
  which the existing `authInterceptor`'s 401 branch already redirects
  to `/login` for any unauthenticated caller, unchanged by this PLAN.
- **Reaching `/complete` as a non-bootstrap, already-complete account**:
  possible (the route has no guard, and any authenticated user could
  navigate there directly), but produces no privilege escalation or
  data leak: `ProfileCompletionFilter`'s allowlist keeps this endpoint
  reachable unconditionally (per `mandatory-complete-profile`'s PLAN,
  by design — "safe to have in the allowlist unconditionally"), and
  `UserProfileService.completeOwnProfile` rejects with 409
  `PROFILE_ALREADY_COMPLETE` for anyone already complete, writing
  nothing. REQ-9 makes the frontend treat that as success and navigate
  onward — the caller ends up exactly where they'd have landed via
  `/welcome` directly. No field of another account is ever touched:
  the endpoint has no `{id}` path variable (backend PLAN, "applies
  only to the authenticated caller's own record").
- **CSRF**: no new exemption is added; `/api/users/me/profile/complete`
  already falls under the existing `"/api/users/**"` CSRF-ignore entry
  per the backend PLAN, and this frontend PLAN makes no
  `SecurityConfig` change (there's nothing to make — that file is
  backend-owned).
- **Leaking the endpoint's existence to non-bootstrap accounts**: the
  route is unauthenticated-reachable in URL terms but requires a
  logged-in session to get past the 401 branch, and the form/copy
  reveal nothing an authenticated user couldn't already infer from
  `mandatory-complete-profile`'s shipped, documented backend behavior.
  Not a new information disclosure.

**Conclusion: no issues found.** The no-guard decision is safe because
enforcement genuinely lives server-side (both authentication via 401
and the completion invariant via 409 `PROFILE_ALREADY_COMPLETE`), which
is exactly what the SPEC's own non-functional requirement already
states and this PLAN does not weaken.

## Components and routes

| Route | Component | Guard |
|---|---|---|
| `/complete-profile` | `CompleteProfilePageComponent` (new) | none (see AppSec review) |

- `CompleteProfilePageComponent` (new) — composes `ProfileFieldsFormComponent`
  with `[requireAllFields]="true"` and no `AvatarUploadComponent`; no
  nav, no other route links, no pending-approval copy (REQ-4/11).
- `ProfileFieldsFormComponent` (existing, `src/app/shared/`) — gains
  `requireAllFields` input (default `false`); no other call site
  changes behavior.
- `LoginPageComponent` (existing) — `onSubmitCode`/`onSubmitPassword`
  `next` callbacks branch on the verify response's
  `pendingProfileCompletion`.
- `auth.interceptor.ts` (existing) — one-line redirect target change.

## Consumed API contracts

Cross-referencing `knowly-api/specify/features/mandatory-complete-profile/PLAN.md`
(shipped) — not re-derived here.

| Method | Path | Request | Response | Status |
|---|---|---|---|---|
| POST | `/api/users/me/profile/complete` | `MandatoryProfileFieldsDto` (`fullName`, `birthDate`, `cpf`, `rg`, `rgOrgaoEmissor`, `address`, `contacts[]`) | `UserProfileDto` (200); field-error body (400); `{"code":"PROFILE_ALREADY_COMPLETE"}` (409) | 200, 400, 409 |
| POST | `/api/auth/login-code/verify` (existing) | unchanged | **+ `pendingProfileCompletion: boolean`** | 200, 401 (unchanged codes) |
| POST | `/api/auth/login-password/verify` (existing) | unchanged | **+ `pendingProfileCompletion: boolean`** | 200, 401 (unchanged codes) |

## State and data

- No new shared/service-level signal is introduced.
  `pendingProfileCompletion` is read once, synchronously, from the
  verify response inside `LoginPageComponent`'s existing `subscribe`
  callback — it is transient routing input, not state anything else
  in the app needs to observe later (consistent with the "session
  edge cases" guidance: a staff session's pending-completion state is
  a server-side fact re-checked via the 409 safety net, REQ-2, not
  something the frontend caches client-side).
- `CompleteProfilePageComponent` owns local signals only:
  `formFields` (seeded empty, same `EMPTY_FIELDS` shape
  `OwnProfilePageComponent` already defines — duplicated locally per
  that component's own existing pattern, not extracted into a shared
  constant, since this PLAN doesn't touch that file), `submitting`,
  `error` (`'network' | null`), `fieldErrors` (`string[] | null`).
- No Reactive Forms, no new store/signal service — see "Form/validation
  approach" above.

## Dependencies

None. No new `package.json` entry — reuses `ProfileFieldsFormComponent`,
`ErrorStateComponent`, `TranslocoPipe`, and existing HTTP/service
patterns exactly as already present.

## Amendment — REQ-15 masked input, inherited (2026-08-02)

- **Masking is inherited automatically, not reimplemented**: REQ-15's
  CPF/RG/CEP/phone mask-as-you-type formatting comes for free once
  `CompleteProfilePageComponent` composes `ProfileFieldsFormComponent`
  (already this PLAN's decision, unchanged) — the masking lives inside
  that shared component's template (`InputMaskDirective` on the `cpf`,
  `cep`, and phone-type contact-value inputs, per
  `user-profile-v2/PLAN.md`'s amendment), not in
  `CompleteProfilePageComponent` itself. This screen's `[requireAllFields]="true"`
  input and REQ-15's masking are independent, orthogonal props on the
  same shared component — neither this PLAN's `requireAllFields` mode
  nor `user-profile-v2`'s masking amendment touch or depend on the
  other's implementation.
- **Ordering**: `user-profile-v2`'s masking amendment (its
  `InputMaskDirective` + the wiring into `ProfileFieldsFormComponent`'s
  template) must land **first** — this feature's own TASKS.md adds only
  a confirmation task (no new masking code), so there is nothing to
  implement here until that directive exists on the shared component.
  If `bootstrap-profile-completion`'s implementation reaches that point
  before `user-profile-v2`'s amendment ships, this feature blocks on it
  the same way its original PLAN already blocks on
  `identity-profile-model-v2`'s backend contract.

## Testing strategy (Vitest)

- `profile-fields-form.component.spec.ts` (existing file, extended):
  new cases for `requireAllFields=true` — mandatory inputs render
  `required`, zero-contact submission is blocked with
  `contactsRequiredMessage` shown and `submitted` never emitted;
  existing `requireAllFields=false` (default) cases must stay green
  unchanged (regression guard for `OwnProfilePageComponent`/
  `ProfileSectionComponent`).
- `complete-profile-page.component.spec.ts` (new): renders the full
  field set, no avatar control, no nav (REQ-3/11/12 — assert
  `email` is read-only text, never an input); successful submit calls
  `completeOwnProfile` with the mapped DTO and navigates to `/welcome`
  (REQ-6/7); 409 `PROFILE_ALREADY_COMPLETE` also navigates to
  `/welcome` with no error rendered (REQ-9); 400 renders `fieldErrors`
  without resetting `formFields` (REQ-8); network/5xx renders
  `ErrorStateComponent` without resetting `formFields` (REQ-10).
- `auth.service.spec.ts` (existing, extended): `verifyCode`/
  `verifyPassword` resolve with `{ pendingProfileCompletion }` read
  straight from the mocked HTTP response, `isLoggedIn` still flips to
  `true` in both outcomes.
- `login-page.component.spec.ts` (existing, extended): a verify
  response with `pendingProfileCompletion: true` navigates to
  `/complete-profile` instead of `/welcome`, for both the code and
  password tabs; `pendingProfileCompletion: false` (or field absent)
  preserves the existing `/welcome` navigation.
- `auth.interceptor.spec.ts` (existing, extended): a 409
  `PROFILE_COMPLETION_REQUIRED` response navigates to
  `/complete-profile`, not `/profile`; the 401 branch and all other
  error codes stay covered exactly as today.
