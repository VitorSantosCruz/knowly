# PLAN — deletion-confirmation-token

> The how. Translates the SPEC into concrete technical decisions.
> References SPEC.md.

## Architectural decisions

- **New package `br.com.conectabyte.knowly.deletion`** holds the entire
  generic mechanism (service, config, wordlists, locale resolution) —
  it is cross-cutting infrastructure consumed by `article`, `tenancy`
  (twice) and the staff side of `tenancy`, not owned by any one of them,
  so it doesn't belong inside `article` or `tenancy`.
- **Storage: Redis (`StringRedisTemplate`), not a new table** — this
  mirrors `LoginCodeService`'s existing pattern exactly (`auth:login-code:`
  key prefix, `passwordEncoder.encode(...)` value, `opsForValue().set(key,
  hash, ttl)`), which is this codebase's actual "existing one-time-secret
  convention" the SPEC's non-functional section points at (confirmed by
  inspection: `OneTimePasswordService` instead persists on the `User`
  row, which doesn't fit here since a deletion token isn't tied to a
  single entity type). A DB table was considered and rejected: the token
  is single-use, 5-minute TTL, and needs no audit trail of its own
  (validation/generation are already independently audit-logged per
  REQ, satisfying the durability the SPEC actually cares about) — a table
  would need a cleanup job for expired rows that Redis's native `SET ...
  EX` gives for free. `knowly-api` currently runs as a single Spring
  Boot instance behind no load balancer (`compose.yaml`/`Dockerfile`
  show one app container, no horizontal scaling config) — even though
  Redis is already the shared/multi-instance-safe store the codebase
  uses for this exact class of ephemeral secret, so choosing it here
  costs nothing today and doesn't foreclose horizontal scaling later,
  unlike an in-memory Caffeine cache would.
- **Key shape**: `deletion-token:{resourceType}:{resourceId}:{userId}`
  (mirrors `LoginCodeService.key(email)`'s single-string-key approach).
  `resourceType` is a short fixed tag per wired endpoint (`article`,
  `tenant-member`, `tenant-permission`, `tenant-access-group`,
  `staff-permission`, `staff-access-group`); `resourceId` is the
  composite instance identity: a single id for member removal/article
  delete, `"{membershipId}:{permission}"` /
  `"{membershipId}:{accessGroupId}"` / `"{userId}:{permission}"` /
  `"{userId}:{accessGroupId}"` for the four compound-key endpoints — this
  gives REQ-8's "different resource instance is rejected" for free
  through simple key inequality, no separate equality-check logic needed.
- **One key per (resourceType, resourceId, userId) satisfies REQ-12 for
  free**: `SET` on an existing key overwrites the value and resets the
  TTL, so requesting a new token for the same instance/user
  atomically discards the old word — no separate invalidation step.
- **Generic service `DeletionConfirmationTokenService`**:
  - `String generate(String resourceType, String resourceId, User actor,
    String acceptLanguageHeader)` — resolves locale (see below), draws
    two distinct random words from the resolved wordlist via
    `SecureRandom`, joins with `-`, hashes with the existing `PasswordEncoder`
    bean, `SET`s the Redis key with the configured TTL, writes an audit
    event (`deletion_confirmation_token.generate`), and returns the
    plaintext word.
  - `boolean validateAndConsume(String resourceType, String resourceId,
    User actor, String suppliedWord)` — looks up the key; if absent,
    compares the supplied word against a constant dummy hash anyway
    (timing-safety, mirrors `LoginCodeService.verify`/
    `OneTimePasswordService.verifyAndRotate`'s dummy-hash pattern), then
    returns `false`. If present, deletes the key immediately regardless
    of outcome (single-use on first attempt, REQ-11/REQ-32) — a match
    returns `true`; a mismatch also deletes the key and returns `false`.
    This is a deliberate choice over "only consume on success": leaving
    a live token usable across repeated wrong guesses would let a caller
    who already holds delete permission brute-force the ~262k two-word
    combination space within the 5-minute TTL, defeating the token's
    purpose as proof of deliberate intent rather than UI theater (see
    DECISIONS.md). The cost is UX: a genuine typo requires generating a
    fresh word (REQ-12), same as an expired token — the frontend PLAN's
    REQ-8 refetch-on-rejection flow already covers this without a
    separate code path. Both outcomes write an audit event
    (`deletion_confirmation_token.validate`, `SUCCESS`/`FAILURE`). No
    exception is thrown for a bad word — the six call sites turn `false`
    into their own domain-appropriate 4xx (REQ-7's single generic
    message), keeping this service HTTP-agnostic.
  - This is intentionally a plain `@Service`, not annotated with
    `@AuditLog` — that aspect's `action`/`resourceType` are static
    per-annotation strings and can't carry this service's
    per-call-dynamic `resourceType`/`resourceId`, so both methods build
    and persist an `AuditEvent` directly via the existing
    `AuditEventWriter` (same component `AuditLogAspect` itself uses),
    keeping one audit-writing mechanism rather than a second one.
- **Wordlists are static in-repo resources**, not DB rows or a runtime
  fetch (SPEC's usability NFR requires this): `src/main/resources/
  wordlists/deletion-confirmation-en.txt` and
  `deletion-confirmation-pt-br.txt`, one lowercase word per line, no
  header/metadata. Loaded once at startup by
  `DeletionConfirmationWordlist` (`@Component`, `@PostConstruct`) into
  two immutable `List<String>`s via `ClassPathResource` — avoids re-reading
  the file on every generation call. A startup check asserts each list
  has ≥512 entries and fails fast (`IllegalStateException` at boot) if
  not, so a curation mistake is caught in CI/deploy rather than silently
  producing weak tokens in production.
- **Locale resolution is a small dedicated parser, not Spring's
  `LocaleResolver`/`@RequestHeader Locale`** — the SPEC's REQ-31 logic
  (`pt-BR`/`pt` primary tag → pt-BR, everything else including
  missing/unparseable → EN) is narrower than general `Locale` negotiation
  and Spring's default `AcceptHeaderLocaleResolver` would happily resolve
  to e.g. `pt-PT` or throw on a malformed header — neither matches
  REQ-31 exactly. `DeletionConfirmationLocaleResolver.resolve(String
  acceptLanguageHeaderValue)` parses via
  `Locale.LanguageRange.parse(...)` defensively (catching
  `IllegalArgumentException` for unparseable input) and checks only the
  highest-priority range's language tag against `"pt"`, returning an enum
  `DeletionLocale { EN, PT_BR }`. This is new, narrowly-scoped logic
  because the SPEC confirms (by inspection) nothing reusable exists yet
  — not a new general-purpose i18n mechanism, and it is not registered as
  a Spring `LocaleResolver` bean, so it has zero effect on any other part
  of the app.
- **Each of the 6 controllers gets one new sibling `POST
  .../deletion-confirmation-token` endpoint**, gated by the exact same
  authorization the corresponding `DELETE` already uses (see per-endpoint
  table below) — never a new/different permission, per REQ-15/18/21/24/
  27/30. Each existing `DELETE` handler gains a required `word` field
  (see API contracts). Authorization must run **before** token
  validation, not after — an unauthorized caller must get 403 regardless
  of whether they supply a word at all (consistent with every other
  guarded action in this codebase, and because a word check first would
  let an unauthorized caller use validation's generic error response to
  probe for a token's existence). So the order in each handler is:
  existing permission check (unchanged) → call
  `deletionConfirmationTokenService.validateAndConsume(...)` → on
  `false`, throw a new `DeletionConfirmationInvalidException` (400,
  generic message, no distinguishing detail — REQ-7) instead of
  proceeding → existing delete logic.
- **`word` is supplied as a required request body field on each `DELETE`
  call**, not a header or query param — `DELETE` requests without a body
  are unusual but Spring's `@RequestBody` on `@DeleteMapping` is already
  idiomatic in Spring MVC and keeps the word out of URLs/access logs
  (never logged in plaintext — NFR). A missing/blank `word` is rejected
  by Bean Validation (`@NotBlank`) before the handler runs, satisfying
  REQ-5 without extra code.
- **Config**: new `knowly.auth.deletion-confirmation-token.ttl: 5m`
  entry in `application.yaml`, new `DeletionConfirmationToken(Duration
  ttl)` record nested in the existing `AuthProperties`
  (`knowly.auth.*` already the home for every other TTL-bounded secret's
  config — `login-code`, `one-time-password`, `lockout`), satisfying
  REQ-4's "configurable, not hardcoded."

## Data schema

No new tables/entities/migrations. Token state lives entirely in Redis
(TTL-evicted, no durable row); audit trail uses the existing
`audit_events` table/`AuditEvent` entity unchanged (new `action` string
values only, no schema change).

## API contracts

New endpoints (all require the same auth/permission as the sibling
`DELETE`; all return the plaintext word once, in the response body):

| Method | Path | Request | Response | Status |
|---|---|---|---|---|
| POST | `/api/tenants/{tenantId}/articles/{articleId}/deletion-confirmation-token` | — | `{ "word": string }` | 200 / 403 |
| POST | `/api/tenants/{tenantId}/members/{membershipId}/deletion-confirmation-token` | — | `{ "word": string }` | 200 / 403 |
| POST | `/api/tenants/{tenantId}/members/{membershipId}/permissions/{permission}/deletion-confirmation-token` | — | `{ "word": string }` | 200 / 403 |
| POST | `/api/tenants/{tenantId}/members/{membershipId}/access-groups/{accessGroupId}/deletion-confirmation-token` | — | `{ "word": string }` | 200 / 403 |
| POST | `/api/staff/users/{userId}/permissions/{permission}/deletion-confirmation-token` | — | `{ "word": string }` | 200 / 403 |
| POST | `/api/staff/users/{userId}/access-groups/{accessGroupId}/deletion-confirmation-token` | — | `{ "word": string }` | 200 / 403 |

`DeletionConfirmationTokenDto(String word)` is the shared response DTO
(new, `br.com.conectabyte.knowly.deletion.dto`) used by all six.

Modified existing endpoints (all gain a required body, all gain a new
400 outcome; existing 200/403/404 behavior unchanged):

| Method | Path | New request body | New status |
|---|---|---|---|
| DELETE | `/api/tenants/{tenantId}/articles/{articleId}` | `{ "word": string }` | 400 (invalid/expired confirmation) |
| DELETE | `/api/tenants/{tenantId}/members/{membershipId}` | `{ "word": string }` | 400 |
| DELETE | `/api/tenants/{tenantId}/members/{membershipId}/permissions/{permission}` | `{ "word": string }` | 400 |
| DELETE | `/api/tenants/{tenantId}/members/{membershipId}/access-groups/{accessGroupId}` | `{ "word": string }` | 400 |
| DELETE | `/api/staff/users/{userId}/permissions/{permission}` | `{ "word": string }` | 400 |
| DELETE | `/api/staff/users/{userId}/access-groups/{accessGroupId}` | `{ "word": string }` | 400 |

`DeleteConfirmationRequestDto(@NotBlank String word)` is the shared
request DTO for all six `DELETE` bodies.

Per-endpoint authorization reused for the new generation endpoint (same
check that already guards the sibling `DELETE`, confirmed by reading
`TenantService`/`StaffService`):

| resourceType tag | Guarding check | Where enforced today |
|---|---|---|
| `article` | `@RequiresPermission(Permission.ARTICLE_DELETE)` | `ArticleController` method annotation |
| `tenant-member` | `requireAdminOfTenantOrStaff(actor, tenantId, GlobalPermission.TENANT_MEMBER_MANAGE_ANY)` | `TenantService.removeMember` (in-method) |
| `tenant-permission` | `requireAdminOfTenantOrStaff(actor, tenantId, GlobalPermission.TENANT_PERMISSION_GRANT_MANAGE_ANY)` | `TenantService.revokePermission` (in-method) |
| `tenant-access-group` | `requireAdminOfTenantOrStaff(actor, tenantId, GlobalPermission.TENANT_PERMISSION_GRANT_MANAGE_ANY)` | `TenantService.unassignAccessGroup` (in-method) |
| `staff-permission` | `@RequiresGlobalPermission(GlobalPermission.STAFF_PERMISSION_MANAGE)` | `StaffService.revokePermission` method annotation |
| `staff-access-group` | `@RequiresGlobalPermission(GlobalPermission.STAFF_PERMISSION_MANAGE)` | `StaffService.unassignAccessGroup` method annotation |

For the four `TenantService`/`StaffService`-guarded cases, the new
`generate...Token` service method calls the exact same private/annotated
check the `DELETE` method already calls, so there is no risk of the two
checks drifting apart later — the generation endpoint literally shares
the guard, not a re-derived copy of it.

## Dependencies

None new — `StringRedisTemplate`, `PasswordEncoder`, and Redis itself are
already provisioned (`spring-boot-starter-data-redis`,
`knowly-api/compose.yaml`'s `redis` service) and already used by
`LoginCodeService` for structurally the same purpose.

## Package/file structure

- `src/main/java/br/com/conectabyte/knowly/deletion/DeletionConfirmationTokenService.java` (new)
- `src/main/java/br/com/conectabyte/knowly/deletion/DeletionConfirmationWordlist.java` (new — loads both `List<String>`s at startup)
- `src/main/java/br/com/conectabyte/knowly/deletion/DeletionConfirmationLocaleResolver.java` (new)
- `src/main/java/br/com/conectabyte/knowly/deletion/DeletionLocale.java` (new enum: `EN`, `PT_BR`)
- `src/main/java/br/com/conectabyte/knowly/deletion/exception/DeletionConfirmationInvalidException.java` (new — mapped to 400, generic message, via the existing global exception handler pattern; confirm/reuse whatever `@ControllerAdvice` already maps `TenantAccessDeniedException` etc.)
- `src/main/java/br/com/conectabyte/knowly/deletion/dto/DeletionConfirmationTokenDto.java` (new)
- `src/main/java/br/com/conectabyte/knowly/deletion/dto/DeleteConfirmationRequestDto.java` (new)
- `src/main/resources/wordlists/deletion-confirmation-en.txt` (new, ≥512 lines)
- `src/main/resources/wordlists/deletion-confirmation-pt-br.txt` (new, ≥512 lines)
- `src/main/java/br/com/conectabyte/knowly/auth/AuthProperties.java` (modify: add `DeletionConfirmationToken(Duration ttl)` record + field)
- `src/main/resources/application.yaml` (modify: add `knowly.auth.deletion-confirmation-token.ttl: 5m`)
- `src/main/java/br/com/conectabyte/knowly/article/ArticleController.java` (modify: new `POST .../deletion-confirmation-token`; `delete` gains `@RequestBody @Valid DeleteConfirmationRequestDto`)
- `src/main/java/br/com/conectabyte/knowly/article/ArticleService.java` (modify: `delete` validates token first)
- `src/main/java/br/com/conectabyte/knowly/tenancy/TenantController.java` (modify: 3 new sibling endpoints; `removeMember`/`revokePermission`/`unassignAccessGroup` gain the request body)
- `src/main/java/br/com/conectabyte/knowly/tenancy/TenantService.java` (modify: same 3 methods validate token; 3 new `generate...Token` methods reusing `requireAdminOfTenantOrStaff`)
- `src/main/java/br/com/conectabyte/knowly/tenancy/StaffController.java` (modify: 2 new sibling endpoints; `revokePermission`/`unassignAccessGroup` gain the request body)
- `src/main/java/br/com/conectabyte/knowly/tenancy/StaffService.java` (modify: same 2 methods validate token; 2 new `generate...Token` methods, both `@RequiresGlobalPermission(STAFF_PERMISSION_MANAGE)`)

## Testing strategy

- Unit tests: `DeletionConfirmationTokenServiceTest` (generate/validate
  round-trip, wrong word, wrong resourceId, wrong user, expired via
  manipulated TTL/clock, re-generation invalidates prior token, dummy-hash
  timing-safety path on a missing key), `DeletionConfirmationLocaleResolverTest`
  (pt-BR/pt/PT-br case-insensitivity, missing header, garbage header,
  unrelated language → EN), `DeletionConfirmationWordlistTest` (both
  lists load, both ≥512 entries, no duplicate/blank lines).
- Integration tests (`@SpringBootTest`, Testcontainers, mirrors existing
  `ArticleManagementIntegrationTest`/`TenantManagementIntegrationTest`
  style): for each of the 6 wired endpoints — generation requires the
  right permission (403 without it, per REQ-15/18/21/24/27/30);
  delete/revoke/unassign succeeds with a valid word and fails (400,
  generic message) with no word, wrong word, another resource's word, or
  another user's word (REQ-5, REQ-7, REQ-8, REQ-9); a used word can't be
  reused (REQ-6/11); an expired word (TTL forced down via test
  `@ActiveProfiles`/property override) is rejected (REQ-10); requesting
  a second token for the same instance invalidates the first (REQ-12);
  `Accept-Language: pt-BR` yields a pt-BR-list word, absent/garbage
  header yields an EN-list word (REQ-31, acceptance criterion 3); every
  generation and validation attempt (success and failure) produces an
  `AuditEvent` with actor/resourceType/resourceId/outcome.
