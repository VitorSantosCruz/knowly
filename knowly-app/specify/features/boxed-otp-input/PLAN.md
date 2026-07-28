# PLAN — boxed-otp-input

> The how. Translates SPEC.md into concrete technical decisions.
> References SPEC.md and `login/SPEC.md` (REQ-7/REQ-9/REQ-10/REQ-11,
> which this feature restates for the boxed shape but does not change).

## Architectural decisions

- **Login-page-specific markup, no new `shared/` component.** *Why:*
  confirmed with the user in SPEC.md's "Out of scope" — no second
  numeric-OTP flow exists today (YAGNI, matching this codebase's
  precedent in `primeng-removal/PLAN.md` of not building abstractions
  ahead of a second consumer). All new markup/handlers live directly in
  `login-page.component.ts`.

- **State: a single `digits = signal<string[]>(Array(6).fill(''))`,
  plus a `computed` `code = computed(() => this.digits().join(''))`
  replacing the existing `code` signal 1:1.** *Why:* mirrors this
  codebase's "signal + readonly derivation" shape used elsewhere
  (`PermissionsService` etc.) at component-local scale; keeps
  `onSubmitCode`'s call to `authService.verifyCode(email, code(), ...)`
  unchanged in shape — `code` stays a zero-argument accessor, only its
  origin changes from a plain signal to a computed one. **No change to
  the `AuthService.verifyCode` call site or its contract** — this is a
  purely frontend-internal, presentation-layer change per SPEC.md's
  "Context and motivation".

- **Six `<input>` elements, one per digit, each with a stable
  `id="otp-digit-{i}"` (0-indexed) and `data-otp-index="{i}"`, rendered
  via `@for (i of otpIndexes; track i)` over a static
  `protected readonly otpIndexes = [0, 1, 2, 3, 4, 5];` array.** *Why:*
  `id`-based `document.getElementById` focus management is the existing
  pattern in this same component (`onTabKeydown`'s
  `document.getElementById(\`tab-${...}\`)`) — reusing it instead of
  introducing `@ViewChildren`/`ElementRef` keeps one focus-management
  idiom in the file rather than two. `data-otp-index` gives tests (and
  the paste handler) an unambiguous, non-Angular-internal hook per box.

- **Digit-acceptance and rejection handled in `(keydown)`, not
  `(input)`.** *Why:* REQ-3 requires the keystroke itself to be
  rejected and the box's content left unchanged — filtering after the
  fact in `(input)` would let the browser briefly commit the character
  and require reverting it (a visible flicker, and a second source of
  truth to keep in sync with the `digits` signal). `onDigitKeydown`
  calls `event.preventDefault()` for any single printable character
  that isn't `0`-`9`, and separately handles `Backspace`/`ArrowLeft`/
  `ArrowRight` (REQ-4/REQ-6). Digit keys are *not* prevented; the
  resulting native `input` event is what `onDigitInput` uses to update
  the `digits` signal and advance focus (REQ-2) — this keeps the two
  handlers' responsibilities disjoint (keydown = filter/navigate, input
  = commit/advance) rather than duplicating digit-acceptance logic in
  both.

- **Focus advancement and Backspace-clear are imperative DOM calls
  (`document.getElementById(...)?.focus()`), not a derived signal.**
  *Why:* focus is inherently an imperative, one-shot DOM side effect,
  not app state — modeling "which box is focused" as a signal would add
  a second synchronization point with the DOM's own focus tracking for
  no behavioral benefit, and this component already treats tab focus
  the same way (`onTabKeydown`).

- **Paste handling is a single `(paste)` listener on the group
  container (the `role="group"` wrapping `<div>`), not six per-box
  listeners.** *Why:* REQ-5 says "when the user pastes text anywhere
  into the group" — binding once at the group level is both the
  literal reading of the requirement and avoids six duplicate handlers
  reaching the same conclusion. The handler calls
  `event.preventDefault()`, reads `event.clipboardData.getData('text')`,
  extracts the first 6 characters matching `/\d/` via
  `text.match(/\d/g)?.slice(0, 6) ?? []`, writes them into `digits`
  starting at index 0 (per REQ-5, "starting at the first box" —
  irrespective of which box was focused when the paste happened), and
  focuses the box after the last filled digit, or the last box if all
  six were filled.

- **Submission validation relies on native HTML `required` per box,
  not a manual "are all six filled" JS check before calling
  `verifyCode`.** *Why:* SPEC.md's REQ-8 explicitly frames this as
  "native `required` validation equivalent per box" — each of the six
  `<input required>` boxes participates in the enclosing `<form>`'s
  constraint validation exactly like the current single `<input
  required>` already does today; the browser blocks the `submit` event
  from firing at all and moves focus to the first invalid (empty) box
  automatically when the button is pressed. `onSubmitCode`'s body is
  therefore **unchanged** except for reading `this.code()` (now the
  computed value) instead of the old plain signal — no new manual
  length/fill check is introduced, avoiding a second, redundant
  validation path next to the one the browser already runs.

- **`inputmode="numeric"` and `autocomplete="one-time-code"` on each
  box.** *Why:* not in SPEC.md's requirements explicitly, but a
  same-tier, no-precedent-needed detail (Tier 1: standard HTML
  attributes, no behavior change to what's specified) that gets mobile
  numeric keyboards and, where supported, SMS-code autofill-into-first-
  box for free, consistent with SPEC.md's responsiveness bar without
  contradicting REQ-5's own paste-distribution behavior (autofill via
  `autocomplete="one-time-code"` fires a native `input` event on that
  box with the full code as its value in supporting browsers; this is
  handled by the same paste-extraction logic reused as a fallback in
  `onDigitInput` — see Testing strategy for what is and isn't covered).

- **ARIA structure**: the wrapping `<div role="group"
  [attr.aria-label]="'login.codeGroupLabel' | transloco">` replaces the
  existing `<label for="code">`; each box gets
  `[attr.aria-label]="'login.codeDigitLabel' | transloco: { position: i + 1 }"`
  (e.g. "Digit 1 of 6") and `[attr.aria-describedby]="errorCode() ?
  'credential-error' : null"` exactly as the old single input did, so
  the existing error-tooltip association (REQ-9, login/SPEC.md REQ-10/
  REQ-11) is preserved per box rather than lost. *Why:* REQ-1 and the
  non-functional accessibility requirements ask for exactly this
  shape; keeping `aria-describedby` on every box (not just the first)
  ensures a screen reader announces the error regardless of which box
  currently has focus.

- **New Transloco keys `login.codeGroupLabel` and
  `login.codeDigitLabel`** (the latter with an interpolated `position`)
  added to both `en` and `pt-BR` translation files, alongside the
  existing `login.codeLabel` (kept only if still referenced elsewhere —
  removed from this component's template since the boxed group replaces
  the single `<label>`). *Why:* this codebase's existing i18n
  convention (every user-facing string goes through Transloco); no new
  dependency, just two new keys in the two already-present locale files.

## Components and routes

- No new components, no new routes. `LoginPageComponent`
  (`knowly-app/src/app/features/login/login-page.component.ts`) is
  modified in place: the Code tab's `<label for="code">` +
  `<input id="code" name="code">` pair is replaced by the
  `role="group"` wrapper containing six `<input>` boxes, described
  above. The Password tab, email step, tab navigation, and turnstile
  captcha handling are untouched.

## Consumed API contracts

No change. Still `AuthService.verifyCode(email, code, captchaToken)` →
`POST /api/auth/login-code/verify` (per
`knowly-api/specify/features/authentication/SPEC.md` REQ-3), called with
the same three arguments in the same order; only the source of the
`code` string argument changes internally (computed signal instead of a
plain one bound to a single input's `input` event).

## State and data

- `digits = signal<string[]>(Array(6).fill(''))` — replaces
  `code = signal('')`.
- `code = computed(() => this.digits().join(''))` — read-only derived
  value, used exactly where the old `code` signal was used (template
  binding removed since there's no single input anymore; `onSubmitCode`
  reads `this.code()`).
- No new services. No reactive forms — this component doesn't use
  Angular forms today (plain template-driven `[value]`/`(input)`
  bindings), and the six boxes follow that same existing pattern rather
  than introducing `ReactiveFormsModule`/`FormArray` for the first time
  in this file.

## Dependencies

None. No new `package.json` entries — everything here is native HTML
(`inputmode`, `autocomplete`, clipboard `paste` event, `KeyboardEvent`)
and existing Angular/Transloco APIs already in use elsewhere in this
component.

## Testing strategy

- **Existing tests that break** (per the task description, lines
  ~175-177, 233-235, 258-260, 284 of `login-page.component.spec.ts`,
  all querying `input[name="code"]` and setting `.value` directly with
  one `input` dispatch): rewritten using a new local test helper,
  `fillOtpBoxes(fixture, '123456')`, that queries all
  `input[data-otp-index]` boxes in DOM order and, for each character,
  sets that box's `.value` and dispatches an `input` event on it (i.e.
  simulates real per-box typing rather than reusing the deleted
  single-input assignment) — this keeps every existing scenario (valid
  code login, invalid-credentials tooltip, account-locked tooltip,
  aria-describedby association) intact and passing without duplicating
  assertions across every box.
- **New tests for this feature's own requirements**, added to
  `login-page.component.spec.ts` under the existing `describe('credential
  step', ...)` block:
  - REQ-1: the Code tab renders six `input[data-otp-index]` boxes and
    zero `input[name="code"]` (replacing the old single-input existence
    check).
  - REQ-2: typing a digit into box 0 shows it there and moves focus to
    box 1 (assert `document.activeElement`).
  - REQ-3: dispatching a `keydown` with `key: 'a'` on a box leaves its
    `.value` empty and does not advance focus.
  - REQ-4: focusing box 2 (already filled), clearing it via Backspace
    behavior — specifically: an empty box 2, `Backspace` keydown, focus
    moves to box 1 and box 1's value is cleared.
  - REQ-5: dispatching a `paste` `ClipboardEvent` with
    `clipboardData` text `"12-3456 extra"` on the group `<div>` fills
    boxes 0-5 with `123456` and focuses... (all filled ⇒ stays on box 5).
  - REQ-6: `ArrowLeft`/`ArrowRight` keydown on a middle box moves focus
    to the adjacent box.
  - REQ-7: (already covered by the rewritten "logs the user in when the
    code is correct" test using `fillOtpBoxes`).
  - REQ-8: submitting the form via the button with box 3 left empty
    does not call `authService.verifyCode` (native constraint
    validation blocks the `submit` event — asserted by spying on
    `verifyCode` and confirming it's never called, since jsdom does
    enforce `required` constraint validation on `form.requestSubmit()`/
    button click, but *not* on a raw `dispatchEvent(new
    Event('submit'))` — see note below).
  - REQ-9: already covered by the two existing (rewritten)
    invalid-credentials/account-locked tests — no new test needed,
    just the `fillOtpBoxes` swap.

  **Known jsdom limitation, called out explicitly rather than silently
  worked around:** jsdom does not run full HTML5 constraint validation
  when a `submit` event is dispatched programmatically via
  `form.dispatchEvent(new Event('submit'))` (the pattern every existing
  test in this file uses) — it only does so via
  `HTMLFormElement.requestSubmit()` or a real button click. The REQ-8
  test therefore uses `button.click()` (already the pattern available
  since the submit button exists in the DOM) instead of the raw
  `dispatchEvent` pattern, specifically for that one test, so the
  browser-equivalent constraint-validation path is actually exercised
  and not just assumed.

## Deviations from this PLAN (implementation-detail corrections)

- `$any(event.target)` (the template-only Angular type-cast helper used
  elsewhere in this component's inline template bindings) is not valid
  inside a TypeScript method body — it's a template-expression-only
  construct. `onDigitInput` uses a plain
  `(event.target as HTMLInputElement).value` cast instead; behavior is
  identical, this is a syntax-only correction with no functional impact.
- The existing spec file actually had **six** call sites keying off
  `input[name="code"]`, not the ~four estimated in TASKS.md's task 1/2
  (the two tab-switch assertions checking `input[name="code"]` was
  falsy/truthy alongside the four scenario tests) — all six were
  updated to use `fillOtpBoxes`/`input[data-otp-index]` assertions
  consistently; no behavioral gap, just a slightly wider find-and-replace
  than originally scoped.
