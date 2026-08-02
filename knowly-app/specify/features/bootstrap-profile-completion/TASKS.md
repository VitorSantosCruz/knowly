# TASKS — bootstrap-profile-completion

> Atomic, sequential, verifiable tasks derived from PLAN.md.
> Each task should be small enough for a single implementation iteration.

## `ProfileFieldsFormComponent`: `requireAllFields` mode (REQ-3/5/14, shared building block)

- [x] 1. Write failing tests in `profile-fields-form.component.spec.ts`
      for `requireAllFields=true`: mandatory inputs (`fullName`, `rg`,
      `rgOrgaoEmissor`, `cpf`, `birthDate`, every address field except
      `numero`/`complemento`) render `required`; submitting with zero
      contacts shows `contactsRequiredMessage` and does not emit
      `submitted` (Red).
- [x] 2. Implement the minimum `requireAllFields` input + template
      `[required]` bindings + zero-contacts guard in `onSubmit` for
      task 1's tests to pass (Green).
- [x] 3. Write a regression test confirming `requireAllFields=false`
      (default, unspecified) still allows zero contacts and no
      `required` attributes — protects `OwnProfilePageComponent`/
      `ProfileSectionComponent`'s existing behavior (Red if it doesn't
      already pass, otherwise confirm Green with no code change).

## `ProfileService`: completion endpoint (REQ-6)

- [x] 4. Write a failing test in `profile.service.spec.ts` for a new
      `completeOwnProfile(dto: MandatoryProfileFields)` method,
      asserting a `POST /api/users/me/profile/complete` call with the
      given body and returning the mocked `UserProfile` (Red).
- [x] 5. Implement `MandatoryProfileFields` interface and
      `completeOwnProfile` in `profile.service.ts` for task 4's test to
      pass (Green).

## `AuthService`: surface `pendingProfileCompletion` (REQ-1, precondition)

- [x] 6. Write failing tests in `auth.service.spec.ts`: `verifyCode`
      and `verifyPassword` resolve with
      `{ pendingProfileCompletion: true }` / `{ pendingProfileCompletion: false }`
      read from the mocked HTTP response body, and `isLoggedIn()` still
      becomes `true` in both cases (Red).
- [x] 7. Change `verifyCode`/`verifyPassword`'s return type and
      implementation in `auth.service.ts` to map through the response's
      `pendingProfileCompletion` field, keeping the existing
      `tap(() => this.loggedIn.set(true))` side effect, for task 6's
      tests to pass (Green).

## `LoginPageComponent`: proactive redirect (REQ-1)

- [x] 8. Write failing tests in `login-page.component.spec.ts`: a
      successful code-tab verify with `pendingProfileCompletion: true`
      navigates to `/complete-profile` (not `/welcome`); the same with
      `false` still navigates to `/welcome`; repeat both cases for the
      password tab (Red).
- [x] 9. Update `onSubmitCode`/`onSubmitPassword`'s `next` callbacks in
      `login-page.component.ts` to branch on the response's
      `pendingProfileCompletion` for task 8's tests to pass (Green).

## `auth.interceptor.ts`: reactive redirect target (REQ-2, SPEC's pre-resolved open item)

- [x] 10. Write/update the failing test in `auth.interceptor.spec.ts`
       asserting a 409 `PROFILE_COMPLETION_REQUIRED` response navigates
       to `/complete-profile`, not `/profile` (Red).
- [x] 11. Change the one `router.navigateByUrl(...)` call target in
       `auth.interceptor.ts`'s `PROFILE_COMPLETION_REQUIRED` branch for
       task 10's test to pass (Green).

## `CompleteProfilePageComponent`: the screen itself (REQ-3/4/11/12)

- [x] 12. Write failing tests in a new
       `complete-profile-page.component.spec.ts`: the full mandatory
       field set renders via `ProfileFieldsFormComponent` with
       `requireAllFields=true`; no avatar-upload control is rendered;
       no nav/other-route links are rendered; `email` renders as
       read-only text, never an input, and is never included in any
       submitted payload (Red).
- [x] 13. Implement the minimal `CompleteProfilePageComponent`
       (fetches own profile for the read-only email display, composes
       `ProfileFieldsFormComponent`) for task 12's tests to pass
       (Green).

## `CompleteProfilePageComponent`: submit + success (REQ-6/7)

- [x] 14. Write a failing test: submitting a fully-filled form calls
       `ProfileService.completeOwnProfile` with the fields mapped from
       `ProfileFieldsFormSubmission` (contacts stripped of `id`,
       `contactChanges` ignored), and on success navigates to
       `/welcome` (Red).
- [x] 15. Implement `onSubmit`'s DTO mapping and success navigation in
       `complete-profile-page.component.ts` for task 14's test to pass
       (Green).

## `CompleteProfilePageComponent`: error handling (REQ-8/9/10)

- [x] 16. Write a failing test: a 400 response renders field-error
       messages (naming only the failed field(s), never `cpf`/`rg`
       values) while leaving the form's already-entered values intact
       (Red).
- [x] 17. Implement the 400-handling branch (`fieldErrors` signal,
       rendered list, no reset of form state) for task 16's test to
       pass (Green).
- [x] 17a. Write a failing test spying on `console.log`/`console.error`/
       `console.warn`/`console.debug` around the 400-handling branch:
       submitting a 400 response whose body includes `cpf`/`rg` values
       (raw `HttpErrorResponse`, its `.error` body, and any nested field
       value) never reaches any `console.*` call — only the derived
       field-name error messages may be logged, if anything is (Red);
       adjust the 400-handling branch so no console call is passed the
       raw error object/body/PII for the test to pass (Green). Enforces
       SPEC.md's cpf/rg-never-logged non-functional requirement
       (~line 197-199) for this specific error path.
- [x] 18. Write a failing test: a 409 `PROFILE_ALREADY_COMPLETE`
       response navigates to `/welcome` with no error rendered (Red).
- [x] 19. Implement the 409-`PROFILE_ALREADY_COMPLETE` branch for task
       18's test to pass (Green).
- [x] 20. Write a failing test: a network/5xx response renders the
       existing `ErrorStateComponent` while leaving the form's
       already-entered values intact (Red).
- [x] 21. Implement the network/5xx branch (`error.set('network')`, no
       reset of form state) for task 20's test to pass (Green).

## Routing (REQ-1/2 wiring)

- [x] 22. Add the `/complete-profile` route (no guard, per PLAN.md's
       AppSec review) to `app.routes.ts`, wired to
       `CompleteProfilePageComponent`.

## Closing verification

- [x] 23. Run `npm run format:check && npm test && npm run build && npm run lint`
       and confirm everything is green.
- [x] 24. Re-read SPEC.md's acceptance criteria one by one against the
       finished implementation (the constitution's "Analyze" closing
       gate); update `PLAN.md` if any decision changed during
       implementation.

## Amendment (2026-08-02) — REQ-15 masked input (inherited, confirmation only)

- [x] 25. Write a test in `complete-profile-page.component.spec.ts`
       confirming that, since `CompleteProfilePageComponent` composes
       `ProfileFieldsFormComponent` unmodified, the rendered
       `cpf`/`cep`/phone-type-contact-value inputs display
       mask-as-you-type formatting exactly as
       `user-profile-v2`'s own `profile-fields-form.component.spec.ts`
       asserts (same directive, same behavior, no duplicate
       implementation) — and that `completeOwnProfile` still receives
       the unmasked/plain values on submit (Red only if
       `user-profile-v2`'s masking amendment (TASKS.md tasks 33–41)
       hasn't landed yet, otherwise Green with no new code, per
       PLAN.md's amendment ordering note).
