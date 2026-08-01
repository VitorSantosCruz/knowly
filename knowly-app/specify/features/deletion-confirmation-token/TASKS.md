# TASKS — Deletion confirmation token (UI)

> Atomic, sequential, verifiable tasks derived from PLAN.md.
> Each "Implement" task ends with `npm run format` and a small
> Conventional Commit before moving on.

## 1. `localeInterceptor` (PLAN's "Accept-Language needs an explicit interceptor")

- [x] 1. **Red** — `core/locale.interceptor.spec.ts`: an outgoing request gains
      `Accept-Language` matching `TranslocoService.getActiveLang()`.
- [x] 2. **Green** — `core/locale.interceptor.ts`: `localeInterceptor`
      (`HttpInterceptorFn`), injects `TranslocoService`, clones every
      request with `Accept-Language: <getActiveLang()>`. Register in
      `app.config.ts`'s `withInterceptors([...])`.
- [x] 3. Run `npm test -- locale.interceptor` and confirm green; commit
      (`feat(core): add localeInterceptor sourcing Accept-Language from the active UI language`).

## 2. Extend `ConfirmDialogComponent` (REQ-1–10, REQ-22)

- [x] 4. **Red** — Extend `shared/confirm-dialog.component.spec.ts`:
      opening calls `fetchToken()` once and displays the resolved word;
      Confirm stays disabled until typed text matches exactly; Confirm
      emits the matched word; loading state disables Confirm while
      `fetchToken()` is pending; a `fetchToken()` error shows retry,
      retry re-invokes `fetchToken()`; bumping `retryToken` discards
      word/typed, shows the REQ-8 message, re-invokes `fetchToken()`;
      `dismissed` discards word/typed without calling anything; paste
      (`ClipboardEvent`), drop (`DragEvent`), and Ctrl+V-style paste all
      leave the input unchanged/Confirm disabled; manual `(input)`
      still works.
- [x] 5. **Green** — Add `fetchToken = input.required<() => Observable<string>>()`,
      `retryToken = input<number>(0)`; internal `word`/`typed`/`loading`/
      `fetchError`/`invalidWordNotice` signals; `confirmDisabled`
      computed; `confirm` output becomes `output<string>()`; effect
      reacting to `open`/`retryToken` transitions calling a private
      `requestToken()`; template adds word display, retype `<input>`
      with `(input)`/`(paste)`/`(drop)`/`(dragover)` bindings, loading/
      error/retry/invalid-word markup; new `common.confirmDialog.*` i18n
      keys.
- [x] 6. Run `npm test -- confirm-dialog.component` and confirm green;
      commit (`feat(shared): add token fetch/retype/paste-blocking flow to ConfirmDialogComponent`).

## 3. Service methods (generate-token + `word` param on delete calls)

- [x] 7. **Red/Green** — `ArticleService`: `generateDeletionToken(tenantId, articleId): Observable<string>`
      (new); `remove(tenantId, articleId, word)` (adds `word` as delete
      body). Update `article.service.spec.ts`.
- [x] 8. **Red/Green** — `MemberService`: `generateRemovalToken`,
      `generatePermissionRevocationToken`,
      `generateAccessGroupUnassignmentToken` (new); `remove`,
      `revokePermission`, `unassignAccessGroup` gain trailing `word`.
      Update `member.service.spec.ts`.
- [x] 9. **Red/Green** — `StaffUserService`: `generatePermissionRevocationToken`,
      `generateAccessGroupUnassignmentToken` (new); `revokePermission`,
      `unassignAccessGroup` gain trailing `word`. Update
      `staff-user.service.spec.ts`.
- [x] 10. Run `npm test -- article.service member.service staff-user.service`
      and confirm green; commit
      (`feat(core): add deletion-confirmation-token generation and word param to delete calls`).

## 4. Wire article deletion (REQ-1–10, already-dialog call site)

- [x] 11. **Red/Green** — Update `articles-page.component.spec.ts`/`.ts`:
      `pendingDelete` flow gains `deleteRetryToken` signal and a
      `fetchToken` closure calling `articleService.generateDeletionToken`;
      `confirmDelete(word)` calls `articleService.remove(..., word)`; a
      400 bumps `deleteRetryToken` and keeps the dialog/article in
      place; other errors close the dialog and show the page error
      state; cancel/dismiss discard with no HTTP call.
- [x] 12. Run `npm test -- articles-page.component` and confirm green;
      commit (`feat(articles): require the fetched confirmation word before deleting`).

## 5. Wire tenant member removal (REQ-11/12)

- [x] 13. **Red/Green** — `members-page.component.ts`/`.spec.ts`:
      `onRemoveMember` opens `ConfirmDialogComponent` via `pendingRemoval`
      instead of deleting immediately; `fetchToken` calls
      `MemberService.generateRemovalToken`; confirming with the matched
      word calls `MemberService.remove(..., word)`; a 400 bumps a
      `removalRetryToken` signal and re-fetches; cancel discards.
- [x] 14. Run `npm test -- members-page.component` and confirm green;
      commit (`feat(members): require confirmation-token flow before removing a member`).

## 6. Wire tenant permission revoke + access-group unassign (REQ-13–16)

- [x] 15. **Red/Green** — `member-detail-panel.component.ts`/`.spec.ts`:
      `onTogglePermission`'s revoke branch opens the dialog via
      `pendingPermissionRevoke`/`permissionRevokeRetryToken` (grant
      branch unchanged); `onUnassignAccessGroup` opens the dialog via
      `pendingGroupUnassign`/`groupUnassignRetryToken`; each `fetchToken`
      calls the matching new `MemberService` method; confirming calls
      the matching `DELETE` with the word and refreshes detail on
      success; a 400 bumps the matching retry signal; cancel discards.
- [x] 16. Run `npm test -- member-detail-panel.component` and confirm
      green; commit
      (`feat(members): require confirmation-token flow before revoking a permission or unassigning a group`).

## 7. Wire staff permission revoke + access-group unassign (REQ-17–20)

- [x] 17. **Red/Green** — `staff-user-detail-panel.component.ts`/`.spec.ts`:
      same shape as task 15/16, backed by `StaffUserService`'s new
      methods.
- [x] 18. Run `npm test -- staff-user-detail-panel.component` and
      confirm green; commit
      (`feat(user-management): require confirmation-token flow before revoking staff permission or unassigning group`).

## 8. i18n

- [x] 19. Add `common.confirmDialog.*` (loading/fetchError/retry/
      invalidWord/inputLabel/inputPlaceholder), `members.confirmRemove`,
      `members.confirmRevokePermission`, `members.confirmUnassignGroup`,
      `staffDirectory.confirmRevokePermission`,
      `staffDirectory.confirmUnassignGroup` to `public/i18n/en.json` and
      `public/i18n/pt-BR.json`.

## 9. Final verification

- [x] 20. Run `npm run format`, then
      `npm run format:check && npm test && npm run build && npm run lint`
      for the whole `knowly-app` project and confirm everything is
      green (note any pre-existing unrelated-file failures separately).
- [x] 21. Cross-check SPEC.md's acceptance criteria and tick them off.
- [x] 22. Final commit for any doc-only changes.
