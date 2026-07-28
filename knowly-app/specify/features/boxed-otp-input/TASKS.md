# TASKS — boxed-otp-input

> Atomic, sequential, verifiable tasks derived from PLAN.md.
> Each task should be small enough for a single implementation iteration.
> TDAD: test first (Red), then minimal code (Green).

## Test migration (must land before/alongside the markup change so the
suite never sits red for unrelated reasons)

- [x] 1. Add the `fillOtpBoxes(fixture, code)` test helper to
      `login-page.component.spec.ts` (queries
      `input[data-otp-index]` in DOM order, sets `.value` + dispatches
      `input` per box). Leave it unused for now (it will be Red/unused
      until task 3 lands the markup) — commit this alone only if a
      lint/unused-var check doesn't fail; otherwise fold into task 3.
- [x] 2. Rewrite the four existing `input[name="code"]`-based scenarios
      (valid code login, invalid-credentials tooltip, account-locked
      tooltip, aria-describedby association) to use `fillOtpBoxes` and
      assert against `input[data-otp-index]`/the group's `role="group"`
      instead of `input[name="code"]`. This is Red until task 3's
      markup exists.

## REQ-1 — six boxes replace the single input

- [x] 3. Implement the six-box markup (`role="group"`, six
      `<input data-otp-index>`/`id="otp-digit-{i}"`), the `digits`
      signal, and the `code` computed, replacing the old single
      `<input id="code" name="code">` and its `code` signal. Confirms
      task 1/2's rewritten tests go Green, and that "renders six boxes,
      zero `input[name=\"code\"]`" (a new assertion) is Green.

## REQ-2/REQ-3 — digit entry and rejection

- [x] 4. Write the test for REQ-2 (typing a digit into box 0 advances
      focus to box 1) — Red.
- [x] 5. Implement `onDigitInput` (commits the digit into `digits`,
      focuses `otp-digit-{i+1}` if it exists) — Green.
- [x] 6. Write the test for REQ-3 (a non-digit `keydown` leaves the
      box's value unchanged and does not advance focus) — Red.
- [x] 7. Implement `onDigitKeydown`'s non-digit `preventDefault` branch
      — Green.

## REQ-4 — Backspace navigation

- [x] 8. Write the test for REQ-4 (Backspace on an empty, non-first box
      moves focus to and clears the previous box) — Red.
- [x] 9. Implement `onDigitKeydown`'s Backspace branch — Green.

## REQ-5 — paste distribution

- [x] 10. Write the test for REQ-5 (pasting `"12-3456 extra"` anywhere
       in the group fills boxes 0-5 with `123456` and leaves focus on
       the last box) — Red.
- [x] 11. Implement the group-level `(paste)` handler (`onPaste`) —
       Green.
- [x] 12. Write a second REQ-5 test for the partial-paste case (pasting
       a 3-digit string fills boxes 0-2 and focuses box 3) — Red, then
       confirm `onPaste`'s "focus box after last filled digit" branch
       already makes it Green (no separate implementation task expected
       — if it doesn't, extend `onPaste`).

## REQ-6 — arrow-key navigation

- [x] 13. Write the test for REQ-6 (ArrowLeft/ArrowRight on a middle box
       moves focus to the adjacent box) — Red.
- [x] 14. Implement `onDigitKeydown`'s ArrowLeft/ArrowRight branch —
       Green.

## REQ-7/REQ-8 — submission behavior

- [x] 15. Confirm (no new test expected beyond task 2's rewritten "logs
       the user in when the code is correct") that `onSubmitCode`
       needs no changes beyond reading the new computed `code()` —
       verify by running the suite.
- [x] 16. Write the test for REQ-8 (submitting via `button.click()`
       with one box left empty does not call
       `authService.verifyCode`, relying on native `required`
       constraint validation) — Red.
- [x] 17. Confirm this passes with the existing per-box `required`
       attribute from task 3 — no new code expected; if Red, add
       `required` to each box's template (should already be present)
       — Green.

## REQ-9 — error tooltip preserved

- [x] 18. Confirm (via task 2's rewritten invalid-credentials/
       account-locked tests) that the tooltip renders and no box is
       cleared — no new code expected, verify by running the suite.

## i18n

- [x] 19. Add `login.codeGroupLabel` and `login.codeDigitLabel` (with
       `{{position}}`/`{{total}}` interpolation as needed) to both
       `en` and `pt-BR` Transloco JSON files; wire the group's
       `aria-label` and each box's `aria-label` to them. Remove the
       now-unused `login.codeLabel` key only if grep confirms nothing
       else references it.

## Wrap-up

- [x] 20. Run `npm run format:check && npm test && npm run build` and
       confirm everything is green.
- [x] 21. Update `PLAN.md`'s decisions if anything changed during
       implementation (e.g. the jsdom constraint-validation workaround
       turning out differently than expected).
- [x] 22. Commit.
