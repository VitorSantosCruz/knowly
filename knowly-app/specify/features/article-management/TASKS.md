# TASKS — Article management (UI)

> Atomic, sequential, verifiable tasks derived from PLAN.md.
> Each "Implement" task ends with `npm run format` and a small
> Conventional Commit before moving on.

## 0. Backend prerequisite (done alongside this feature)

- [x] 1. Backend: `GET /api/tenants/permissions` added to `knowly`'s
      `tenancy` feature (`ownEffectivePermissions` + controller
      endpoint), verified by its own test there.

## 1. Services (REQ-1, REQ-2, REQ-5, REQ-6, REQ-7, REQ-9)

- [x] 2. Test: `ArticleService.list`/`getDetail`/`update`/`remove` call
      the right method/URL/body (Red).
- [x] 3. Implement those four methods (Green).
- [x] 4. Test: `ArticleService.upload` posts a `FormData` with `title`
      and `file` (Red).
- [x] 5. Implement `ArticleService.upload` (Green).
- [x] 6. Test: `PermissionsService.fetch()` populates `permissions()`
      from `GET /api/tenants/permissions`; `has()` reflects it (Red).
- [x] 7. Implement `PermissionsService` (Green).

## 2. Articles page — list and detail (REQ-1, REQ-5, REQ-8)

- [x] 8. Test: `ArticlesPageComponent` renders the article list with
      title/status on load (Red).
- [x] 9. Implement `ArticlesPageComponent` + route `/articles` (Green).
- [x] 10. Test: selecting an article shows its text (or failure reason)
       and a link to the original file (Red).
- [x] 11. Implement article selection + detail rendering (Green).
- [x] 12. Test: a 403 on list shows `NoAccessStateComponent` (Red).
- [x] 13. Implement the error/permission-denied state (Green).

## 3. Upload and polling (REQ-2, REQ-3, REQ-4)

- [x] 14. Test: uploading a supported file adds it to the list
       immediately as "processing" (Red, mocked `ArticleService.upload`).
- [x] 15. Implement the upload form + immediate list update (Green).
- [x] 16. Test: an unsupported/oversized upload (400) shows an error and
       adds nothing to the list (Red).
- [x] 17. Implement the upload-error state (Green).
- [x] 18. Test: while any article is "processing", the list refetches on
       an interval until none remain; the interval is cleared on
       destroy (Red, using Vitest fake timers — Angular's `fakeAsync`
       needs zone.js, which this zoneless app doesn't load).
- [x] 19. Implement the polling behavior (Green).

## 4. Edit and delete (REQ-6, REQ-7, REQ-9)

- [x] 20. Test: editing an article's title/text persists and the shown
       detail updates (Red).
- [x] 21. Implement the edit form (Green).
- [x] 22. Test: deleting an article removes it from the list (Red).
- [x] 23. Implement the delete action (Green).
- [x] 24. Test: the upload form / edit controls / delete button are
       absent (not rendered, not just disabled) when
       `PermissionsService` lacks the corresponding `ARTICLE_*`
       permission (Red).
- [x] 25. Implement the permission-gated conditional rendering (Green).
- [x] 25a. (Emergent) Add a 403 catch to upload/select/edit/delete too,
       not just the initial list load — a defense-in-depth backstop
       behind REQ-9 for a permission revoked mid-session before
       `PermissionsService` refreshes (same gap class found and fixed
       in `members`/`conversations`).

## 5. Final verification

- [x] 26. Run `npm run format:check && npm test && npm run build` and
       confirm everything is green.
- [x] 27. Update `PLAN.md`'s "Emergent decisions" if anything changed
       (see 25a above).
- [x] 28. Update `SPEC.md`'s acceptance-criteria checkboxes.

## 6. Non-flickering poll (REQ-10)

- [ ] 29. **Red** — Extend `articles-page.component.spec.ts`: a poll
       tick whose response is identical (`id`+`title`+`status`, same
       order) to the current `articles()` does not set `loading` back to
       `true` (the full-page `[data-testid="loading-state"]` branch
       never reappears) and does not replace the `article-list` DOM node
       (capture the `<ul data-testid="article-list">` element reference
       before the tick, assert `===` after); a poll tick with one changed
       row still updates that row's status badge text.
- [ ] 30. **Green** — Split `loadArticles(tenantId)` into
       `loadArticles(tenantId, { isInitialLoad })`; only the initial call
       (from the constructor `effect`) sets `loading`. Poll-triggered
       calls (from `schedulePollIfNeeded`'s `setTimeout`) pass
       `{ isInitialLoad: false }` and never touch `loading`. Add a
       shallow-compare helper (`id`+`title`+`status`, same length/order)
       used only in the poll path to skip `this.articles.set(...)` when
       the fetched list is identical to the current value; the initial
       load and post-upload/edit/delete local updates keep calling
       `.set()`/`.update()` unconditionally as today.
- [ ] 31. Run `npm test -- articles-page.component` and confirm green;
       commit
       (`fix(articles): stop background polling from flickering the article list`).

## 7. Shared `ConfirmDialogComponent` (REQ-11–13, native `<dialog>`)

- [ ] 32. **Red** — Write `shared/confirm-dialog.component.spec.ts`: the
       underlying `<dialog>` calls `showModal()` when `open` becomes
       `true` and `close()` when it becomes `false` (assert via the
       element's `open` property); clicking the confirm button emits
       `(confirm)`; clicking the cancel button emits `(cancel)`; the
       native `dialog` `cancel` event (what the browser fires on
       `Escape`) also emits `(cancel)`; the `message` input renders as
       text.
- [ ] 33. **Green** — Create `shared/confirm-dialog.component.ts`: a
       standalone component wrapping a native `<dialog>` (`viewChild` +
       `ElementRef`), with an `effect()` calling `showModal()`/`close()`
       in reaction to the `open` input; `message: input<string>()`;
       `(confirm)`/`(cancel)` outputs — confirm/cancel buttons call
       `.emit()` directly, and the dialog's native `cancel` DOM event
       (`(cancel)="onDialogCancel()"` on the `<dialog>` element) also
       routes to the `(cancel)` output via a small handler method to
       avoid name-colliding with Angular's own `cancel` output binding
       syntax.
- [ ] 34. Run `npm test -- confirm-dialog.component` and confirm green;
       commit (`feat(shared): add native <dialog>-based ConfirmDialogComponent`).

## 8. Wire delete confirmation into `ArticlesPageComponent` (REQ-11–13)

- [ ] 35. **Red** — Extend `articles-page.component.spec.ts`: clicking
       "Delete" renders `<app-confirm-dialog>` with the article's title
       in its message and does **not** call `DELETE
       /api/tenants/7/articles/{id}` yet; clicking confirm fires the
       delete request and removes the row on success (update the
       existing delete test to go through the dialog); clicking cancel
       (and separately, dispatching a synthetic `cancel` event on the
       dialog) closes the prompt, issues no HTTP request, and leaves the
       row in place.
- [ ] 36. **Green** — Add `pendingDelete = signal<ArticleSummary | null>(null)`.
       `onDelete(articleId)` looks up the article in `articles()` and
       sets `pendingDelete` instead of calling `articleService.remove`
       directly. Extract the existing removal logic into a private
       `performDelete(tenantId, articleId)`. Template: import
       `ConfirmDialogComponent`, render `<app-confirm-dialog>` bound to
       `[open]="pendingDelete() !== null"` and a composed message
       interpolating `pendingDelete()!.title` (new `articles.confirmDelete`
       i18n key with a `{{title}}` param); `(confirm)` calls
       `performDelete(...)` then clears `pendingDelete`; `(cancel)` only
       clears `pendingDelete`. Add `articles.confirmDelete` and generic
       `common.confirm`/`common.cancel` keys to `public/i18n/en.json` and
       `public/i18n/pt-BR.json`.
- [ ] 37. Run `npm test -- articles-page.component` and confirm green;
       commit (`feat(articles): require confirmation before deleting an article`).

## 9. Upload button enabled state (REQ-14/15)

- [ ] 38. **Red** — Extend `articles-page.component.spec.ts`: the upload
       submit button has `disabled` set `true` with only a title, only a
       file, or neither, and `false` once both are present; clicking it
       while disabled issues no `POST /api/tenants/7/articles` request.
- [ ] 39. **Green** — Promote `selectedFile` from a private field to
       `protected readonly selectedFile = signal<File | null>(null)`
       (`onFileSelected`/reset paths call `.set(...)` instead of
       assigning); add `protected readonly canUpload = computed(() =>
       this.uploadTitle().trim().length > 0 && this.selectedFile() !== null)`;
       bind `[disabled]="!canUpload()"` on the upload submit button and
       append `disabled:opacity-50 disabled:cursor-not-allowed` to
       `uploadButtonClass`. Keep `onUpload`'s existing guard as
       defense-in-depth.
- [ ] 40. Run `npm test -- articles-page.component` and confirm green;
       commit
       (`feat(articles): disable Upload button until title and file are provided`).

## 10. Two-state layout (REQ-16/17)

- [ ] 41. **Red** — Extend `articles-page.component.spec.ts`: with
       `selectedDetail()` null, the `<aside>` carries the full-width
       class and no content `<section>` node is in the DOM; selecting an
       article flips `<aside>` to the narrow-width class and mounts the
       `<section>` alongside it.
- [ ] 42. **Green** — Change `<aside>`'s width classes to
       `[class.w-full]="selectedDetail() === null"`/
       `[class.w-80]="selectedDetail() !== null"` (mutually exclusive,
       `shrink-0` stays unconditional, static `w-80` removed from the
       element's static `class` attribute). Move the existing
       `@if (selectedDetail(); as detail)` up to gate the `<section>`
       element itself instead of wrapping only its inner `<div>`.
- [ ] 43. Run `npm test -- articles-page.component` and confirm green;
       commit
       (`feat(articles): full-width layout until an article is selected`).

## 11. Full regression + doc sync

- [ ] 44. Run `npm run format`, then
       `npm run format:check && npm test && npm run build && npm run lint`
       for the whole `knowly-app` project and confirm everything is
       green. Cross-check against SPEC.md's REQ-10–17 acceptance-criteria
       checkboxes and tick them off.
- [ ] 45. Update `PLAN.md` with any decision that changed during
       implementation (e.g. the exact `ConfirmDialogComponent` API if it
       diverged).
- [ ] 46. Update root `PROJECT_STATUS.md` to record the REQ-10–17 UX
       fixes as implemented.
- [ ] 47. Final commit for any doc-only changes from steps 45/46
       (`docs(articles): record REQ-10-17 UX fixes completion`).
