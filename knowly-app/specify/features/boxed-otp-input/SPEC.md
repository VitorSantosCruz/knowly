# SPEC — Boxed one-time-code input

## Context and motivation

The login screen's "Code" tab (see `login/SPEC.md` REQ-7) currently uses a
single plain-text input for the 6-digit numeric login code (backend
contract: `knowly-api/specify/features/authentication/SPEC.md` REQ-3).
This feature replaces it with the common "one box per digit" segmented
pattern, matching user expectation for numeric one-time codes and making
the fixed 6-digit length visually obvious before submission. Frontend-only:
no backend contract change (still one 6-digit string sent to
`login-code/verify`).

## User stories

- As a user, I want to enter my 6-digit login code into individual boxes,
  one digit each, so it's visually clear how many digits are expected and
  how many I've entered.
- As a user, I want to paste a code copied from my email straight into the
  first box and have it fill all six boxes automatically, instead of
  having to type each digit by hand.

## Requirements (EARS/GEARS)

### Rendering

- **REQ-1 [Ubiquitous]** The system shall render the Code tab's code entry
  as six individual single-character numeric boxes instead of a single
  text input, wrapped in a `role="group"` labeled as the one-time code
  field.

### Input behavior

- **REQ-2 [Event-Driven]** When the user types a numeric digit into a box,
  the system shall accept only that single digit, display it, and move
  focus to the next box (if any remain).
- **REQ-3 [Unwanted Behavior]** If the user types a non-numeric character
  into a box, then the system shall reject the keystroke and leave the
  box's content unchanged.
- **REQ-4 [Event-Driven]** When the user presses Backspace on an empty
  box (not the first), the system shall move focus to the previous box
  and clear its content.
- **REQ-5 [Event-Driven]** When the user pastes text anywhere into the
  group, the system shall extract the first 6 numeric characters found in
  the pasted text, distribute them one per box starting at the first box,
  and move focus to the box after the last filled digit (or keep focus on
  the last box if all 6 are filled).
- **REQ-6 [Event-Driven]** When the user presses the Left or Right arrow
  key while a box is focused, the system shall move focus to the
  previous/next box respectively, if one exists.

### Submission

- **REQ-7 [Event-Driven]** When the user submits the Code tab's form (via
  the existing submit button) with all 6 boxes filled, the system shall
  compose the six digits into a single string and call
  `AuthService.verifyCode` exactly as today (superseding login/SPEC.md
  REQ-9's trigger for the Code tab only; button press is still required —
  confirmed no auto-submit-on-fill).
- **REQ-8 [Unwanted Behavior]** If the user submits the form with fewer
  than 6 boxes filled, then the system shall prevent submission and keep
  focus on the first empty box (native `required` validation equivalent
  per box).

### Error handling (unchanged from login/SPEC.md, restated for this
component's shape)

- **REQ-9 [Unwanted Behavior]** If the backend responds with
  `INVALID_CREDENTIALS` or `ACCOUNT_LOCKED`, then the system shall show
  the existing tooltip (per login/SPEC.md REQ-10/REQ-11) without clearing
  any of the six boxes.

## Non-functional requirements

- Accessibility: each box has an `aria-label` identifying its position
  (e.g. "Digit 1 of 6"); the group has a group-level accessible name;
  tab order flows through the six boxes in order; Backspace/arrow-key
  navigation is keyboard-operable without a mouse.
- Responsiveness: six boxes remain usable and legible on a 360px-wide
  viewport (per login/SPEC.md's existing responsiveness bar).
- Security: unchanged from login/SPEC.md — code is never logged.

## Acceptance criteria

- [x] The Code tab shows six single-digit boxes instead of one text
      field.
- [x] Typing a digit fills the current box and advances focus to the
      next.
- [x] Typing a non-digit is rejected; the box stays as it was.
- [x] Backspace on an empty box (not the first) moves focus back and
      clears the previous box.
- [x] Pasting a 6-digit code anywhere in the group fills all six boxes
      and moves focus appropriately.
- [x] Left/Right arrow keys move focus between boxes.
- [x] Submitting with all 6 boxes filled sends the composed 6-digit
      string to `login-code/verify`, identically to today's behavior.
- [x] Submitting with any box empty is prevented; focus goes to the
      first empty box.
- [x] `INVALID_CREDENTIALS`/`ACCOUNT_LOCKED` tooltips render exactly as
      before, without clearing any box.

## Out of scope

- Any change to the "Password" tab's single text-field input.
- Any backend contract change — the 6-digit length, numeric-only
  constraint, and `login-code/verify` payload shape are unchanged.
- Auto-submit once all 6 boxes are filled — button press is still
  required, confirmed with the user 2026-07-28.
- Extraction into a generic, reusable `shared/` OTP component for future
  non-login flows — login-page-specific markup confirmed with the user
  2026-07-28, per this codebase's YAGNI precedent (no other numeric-OTP
  flow exists today).
- Any resend-code / cooldown-timer UI — not part of this backlog item.
