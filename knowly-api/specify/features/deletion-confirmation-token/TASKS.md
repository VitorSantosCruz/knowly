# TASKS — deletion-confirmation-token

> Atomic, sequential, verifiable tasks derived from PLAN.md.
> Each "Implement" task ends with `./mvnw spotless:apply && ./mvnw verify`
> and a small Conventional Commit before moving on.

## 0. Foundations

- [x] 1. Curate and add `deletion-confirmation-en.txt` and
      `deletion-confirmation-pt-br.txt` (≥512 entries each, 4-8 lowercase
      letters, no diacritics/punctuation, profanity-filtered, plural/
      near-duplicate-reduced) to `src/main/resources/wordlists/`.
- [x] 2. Add `AuthProperties.DeletionConfirmationToken(Duration ttl)` +
      `knowly.auth.deletion-confirmation-token.ttl: 5m` to
      `application.yaml`.

## 1. Wordlist loading (REQ-2, acceptance criterion 2)

- [x] 3. Test: `DeletionConfirmationWordlistTest` — both lists load, each
      has ≥512 entries, no duplicate/blank lines, every entry matches
      `[a-z]{4,8}` (Red).
- [x] 4. Implement `DeletionLocale` enum + `DeletionConfirmationWordlist`
      (`@Component`, `@PostConstruct`, fails fast if either list is
      short/malformed) (Green).

## 2. Locale resolution (REQ-31)

- [x] 5. Test: `DeletionConfirmationLocaleResolverTest` — `pt-BR`/`pt`/
      case-insensitive variants resolve to `PT_BR`; missing, empty,
      unparseable, or unrelated (`en`, `es`, `fr`) headers resolve to
      `EN` (Red).
- [x] 6. Implement `DeletionConfirmationLocaleResolver` (Green).

## 3. Generic token service (REQ-1, REQ-4, REQ-6, REQ-7, REQ-8, REQ-9,
   REQ-10, REQ-11, REQ-12, REQ-32)

- [x] 7. Test: `DeletionConfirmationTokenServiceTest` — generate returns
      a two-distinct-word, hyphen-joined, locale-appropriate word;
      validateAndConsume round-trips (match → true, then the same word
      is rejected on reuse); wrong word rejected without consuming a
      *different* live token; wrong resourceId rejected; wrong user
      rejected; expired (TTL forced via property override) rejected;
      re-generating for the same (resourceType, resourceId, user)
      invalidates the prior token (REQ-12); a wrong guess still consumes
      the token immediately (REQ-32); dummy-hash comparison happens when
      no token exists (timing safety, mirrors `LoginCodeServiceTest`)
      (Red).
- [x] 8. Implement `DeletionConfirmationTokenService` (Green).
- [x] 9. Implement `DeletionConfirmationInvalidException` +
      `DeletionConfirmationExceptionHandler` (400, generic
      `DELETION_CONFIRMATION_INVALID` body, no distinguishing detail —
      REQ-7).
- [x] 10. Implement `DeletionConfirmationTokenDto`,
      `DeleteConfirmationRequestDto`.

## 4. Article endpoints (REQ-13, REQ-14, REQ-15)

- [x] 11. Test: generation endpoint requires `ARTICLE_DELETE` (403
      without it, 200 + word with it) (Red).
- [x] 12. Implement `POST .../articles/{articleId}/deletion-confirmation-token`
      (Green).
- [x] 13. Test: `DELETE .../articles/{articleId}` rejects with 400 when
      `word` is missing, wrong, or belongs to a different article/user;
      succeeds and deletes when the word matches; the word cannot be
      reused (Red).
- [x] 14. Implement token validation in `ArticleService#delete` +
      `word` field on the controller's delete request (Green).

## 5. Tenant member/permission/access-group endpoints (REQ-16..REQ-24)

- [x] 15. Test: 3 new generation endpoints
      (`.../members/{membershipId}/deletion-confirmation-token`,
      `.../permissions/{permission}/deletion-confirmation-token`,
      `.../access-groups/{accessGroupId}/deletion-confirmation-token`)
      each require the same check as their `DELETE` sibling (Red).
- [x] 16. Implement the 3 generation endpoints + `TenantService`
      methods, reusing `requireAdminOfTenantOrStaff` (Green).
- [x] 17. Test: `removeMember`/`revokePermission`/`unassignAccessGroup`
      each reject (400) a missing/wrong/foreign-scoped/foreign-user word
      and succeed with the right one, single-use (Red).
- [x] 18. Implement token validation in the 3 `TenantService` methods +
      `word` field on the 3 controller `DELETE` handlers (Green).

## 6. Staff permission/access-group endpoints (REQ-25..REQ-30)

- [x] 19. Test: 2 new generation endpoints
      (`/api/staff/users/{userId}/permissions/{permission}/deletion-confirmation-token`,
      `/api/staff/users/{userId}/access-groups/{accessGroupId}/deletion-confirmation-token`)
      each require `STAFF_PERMISSION_MANAGE` (Red).
- [x] 20. Implement the 2 generation endpoints + `StaffService` methods
      (Green).
- [x] 21. Test: `revokePermission`/`unassignAccessGroup` each reject
      (400) a missing/wrong/foreign-scoped word and succeed with the
      right one, single-use (Red).
- [x] 22. Implement token validation in the 2 `StaffService` methods +
      `word` field on the 2 controller `DELETE` handlers (Green).

## 7. Locale wiring end-to-end (REQ-31, acceptance criterion 3)

- [x] 23. Test: `Accept-Language: pt-BR` on a generation request yields
      a word drawn from the pt-BR list; a missing/garbage header yields
      an EN-list word — exercised against the article generation
      endpoint as the representative case (Red).
- [x] 24. Implement/confirm `@RequestHeader(value = "Accept-Language",
      required = false)` plumbing on all 6 generation endpoints down to
      `DeletionConfirmationTokenService#generate` (Green).

## 8. Audit logging (Security NFR)

- [x] 25. Test: a generation call and both a successful and a failed
      validation call each produce an `AuditEvent`
      (`deletion_confirmation_token.generate` /
      `deletion_confirmation_token.validate`, SUCCESS/FAILURE outcome),
      with no plaintext word anywhere in the event (Red).
- [x] 26. Confirm/adjust `DeletionConfirmationTokenService`'s direct
      `AuditEventWriter` usage to satisfy this (Green — likely already
      covered by task 8).

## 9. Final verification

- [x] 27. Run the full `./mvnw spotless:apply && ./mvnw verify` and
      confirm the entire suite is green.
- [x] 28. Update `PLAN.md` if any decision changed during
      implementation.
- [x] 29. Update `SPEC.md`'s acceptance-criteria checkboxes to reflect
      what's now verified by tests.
