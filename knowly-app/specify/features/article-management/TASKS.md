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
