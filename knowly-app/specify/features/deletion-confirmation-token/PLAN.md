# PLAN — deletion-confirmation-token (frontend)

> The how. Translates the SPEC into concrete technical decisions.
> References SPEC.md. API contracts are consumed from
> `knowly-api/specify/features/deletion-confirmation-token/PLAN.md`
> (see "Flag back to backend" note below for one discrepancy found
> between that PLAN and the actual controller code).

## Architectural decisions

- **`ConfirmDialogComponent` itself owns the token-fetch/retype
  lifecycle as internal signals**, not the six call sites — the dialog
  already owns its own open/close effect against the native `<dialog>`;
  loading/word/typed-text/error are the same kind of transient,
  dialog-scoped UI state, not shared app state, so per this project's
  "state lives in services as signals" rule they stay *component-local*
  signals (there is nothing here another feature needs to read). This
  keeps all 6 call sites thin: they supply a `fetchToken` callback and a
  confirm handler, they don't reimplement loading/retype/paste-block
  logic 6 times.
- **New required input `fetchToken = input.required<() => Observable<string>>()`**
  — the dialog calls this itself when it opens (REQ-1/11/13/15/17/19),
  each call site closes over its own resource identity (article id,
  `membershipId`, `(membershipId, permission)`, etc.) in the function it
  passes, so the dialog stays generic and doesn't need six different
  "resource descriptor" input shapes. **Novel decision, no exact
  precedent in this codebase** (no existing component takes a
  function-typed `input()`) — see the corresponding `DECISIONS.md` entry
  below for the full why/alternative-considered.
- **New input `retryToken = input<number>(0)`, bumped by the caller on
  every REQ-8 "invalid/expired/used token" 400** — the dialog's effect
  treats any transition to a value `> 0` different from the last-seen
  value as "the word you just used was rejected," and reacts by: discard
  stale `word`/typed text, show the REQ-8-specific message (distinct
  from the REQ-7 fetch-failure message), and re-invoke `fetchToken()`.
  This avoids adding a second callback/event just to signal "please
  refetch" — bumping a plain counter input is the smallest primitive
  that does it, and it composes with the existing `open`-driven effect
  (both paths funnel into the same private `requestToken()` method).
- **`confirm` output changes from `output<void>()` to `output<string>()`**
  — it now emits the matched, retyped word (REQ-4/5), so the caller can
  pass it straight through to the corresponding `DELETE` call's request
  body without re-reading dialog-internal state.
- **Paste/drop blocking (REQ-22) is three native DOM event bindings on
  the retype `<input>`**: `(paste)="$event.preventDefault()"`,
  `(drop)="$event.preventDefault()"`, `(dragover)="$event.preventDefault()"`.
  The native `paste` event already fires uniformly for Ctrl/Cmd+V *and*
  the browser/OS context-menu "Paste" action (confirmed: both dispatch
  through the same `ClipboardEvent`), so REQ-22's three attack vectors
  reduce to two event types, not three separate handlers to write and
  reason about. No library needed, consistent with "no component
  library" and REQ-22's own "not a defense against a
  programmatically-controlled client" out-of-scope note (native
  `preventDefault()` is exactly the boundary the SPEC asks for, no
  more).
- **Confirm stays disabled via a `computed()`**:
  `confirmDisabled = computed(() => loading() || word() === null || typed() !== word())`
  — satisfies REQ-3/4/6 in one expression, no separate boolean signal to
  keep in sync.
- **`Accept-Language` needs an explicit interceptor sourced from
  `TranslocoService`, not left to the browser default** — confirmed by
  reading `language.service.ts`: the active UI language is
  `TranslocoService`'s `activeLang`, persisted in `localStorage`
  (`knowly.lang`) and settable independently of the browser/OS locale.
  A user who switches the in-app language away from their browser's
  default would otherwise get a token word in the *wrong* list (backend
  resolves purely from the raw `Accept-Language` header per its own
  PLAN, and Angular's `HttpClient` does not let application code set
  `Accept-Language` — it's a
  [forbidden header name for `fetch`/`XMLHttpRequest`] only when the
  browser sets it automatically from OS/browser settings, so it must be
  set explicitly per request to reflect the app's actual displayed
  language). **New `localeInterceptor` (`HttpInterceptorFn`)**, same
  shape as the existing `authInterceptor`, added to `app.config.ts`'s
  `withInterceptors([...])` list; injects `TranslocoService`, clones
  every outgoing request with header
  `Accept-Language: <transloco.getActiveLang()>`. `'en'`/`'pt-BR'` (this
  app's only two `availableLangs`) map directly onto the backend's
  `pt`/else-EN primary-tag check, so no translation table is needed.
  **Novel decision, no exact precedent** — see `DECISIONS.md` entry
  below.
- **`word` is sent as an Angular `HttpClient` `delete(url, { body })`
  option**, not a header/query param — mirrors the backend PLAN's
  `@RequestBody` choice on the `DELETE` handlers exactly; `HttpClient`
  supports a body on `delete()` via the options object, no workaround
  needed.
- **No shared cross-call-site abstraction beyond `ConfirmDialogComponent`
  itself** — each of the 6 call sites keeps its own small
  `pending*`/`retryToken` signal pair (mirroring `articles-page`'s
  existing `pendingDelete` pattern) rather than factoring a generic
  "pending deletion" composable/service. Considered and rejected: the
  six "what's pending" shapes are genuinely different (a single id vs.
  four different compound-key pairs), so a generic version would need
  its own type parameter and still not save more than a few lines per
  site — not worth introducing a new shared pattern for. This keeps
  every site's existing signal-per-component convention intact instead
  of reaching for something new.

## Components and routes

No new routes/guards — all six flows are on already-guarded pages/panels.

- `knowly-app/src/app/shared/confirm-dialog.component.ts` (modified —
  see above; used by all six flows below, article flow already wired).
- `knowly-app/src/app/features/articles/articles-page.component.ts`
  (modified — `pendingDelete` flow gains `fetchToken`/`retryToken`
  wiring and the `confirm` handler now receives the matched word).
- `knowly-app/src/app/features/members/members-page.component.ts`
  (modified — REQ-11/12: `onRemoveMember` now opens
  `ConfirmDialogComponent` via a new `pendingRemoval` signal instead of
  deleting immediately).
- `knowly-app/src/app/features/members/member-detail-panel.component.ts`
  (modified — REQ-13–16: `onTogglePermission`'s revoke branch and
  `onUnassignAccessGroup` each gain their own `pending*`/`retryToken`
  pair and route through `ConfirmDialogComponent`; the grant branch of
  `onTogglePermission` and `onAssignAccessGroup` are unchanged, per
  SPEC's scope — only revoke/unassign are destructive).
- `knowly-app/src/app/features/user-management/staff-user-detail-panel.component.ts`
  (modified — REQ-17–20: same shape as `member-detail-panel`, for the
  staff/global side).
- New template markup: each of the 3 modified pages/panels above adds
  one `@if (pending*(); as target) { <app-confirm-dialog ... /> }` block
  and one confirm/cancel/dismiss handler pair, following
  `articles-page.component.ts`'s existing `pendingDelete`/`confirmDelete`/
  `cancelDelete` shape exactly (REQ-9/21 "cancel discards locally" is
  satisfied for free by not calling any service method in the dismiss
  handler, same as today's article flow).
- New file: `knowly-app/src/app/core/locale.interceptor.ts` (new —
  `localeInterceptor`, see above).

## Consumed API contracts

Per `knowly-api/specify/features/deletion-confirmation-token/PLAN.md`'s
"API contracts" section. All `POST .../deletion-confirmation-token`
calls require no request body and return `{ "word": string }`; all
`DELETE` calls now require `{ "word": string }` in the body and can
return a new `400` (invalid/expired/already-used word) alongside their
existing `200`/`403`/`404`.

| Flow | Generate (`POST`) | Delete (`DELETE`, body `{ word }`) |
|---|---|---|
| Article delete | `/api/tenants/{tenantId}/articles/{articleId}/deletion-confirmation-token` | `/api/tenants/{tenantId}/articles/{articleId}` |
| Member removal | `/api/tenants/{tenantId}/members/{membershipId}/deletion-confirmation-token` | `/api/tenants/{tenantId}/members/{membershipId}` |
| Tenant permission revoke | `/api/tenants/{tenantId}/members/{membershipId}/permissions/{permission}/deletion-confirmation-token` | `/api/tenants/{tenantId}/members/{membershipId}/permissions/{permission}` |
| Tenant access-group unassign | `/api/tenants/{tenantId}/members/{membershipId}/access-groups/{accessGroupId}/deletion-confirmation-token` | `/api/tenants/{tenantId}/members/{membershipId}/access-groups/{accessGroupId}` |
| Staff permission revoke | `/api/staff/users/{userId}/permissions/{permission}/deletion-confirmation-token` | `/api/staff/users/{userId}/permissions/{permission}` |
| Staff access-group unassign | `/api/staff/users/{userId}/access-groups/{accessGroupId}/deletion-confirmation-token` | `/api/staff/users/{userId}/access-groups/{accessGroupId}` |

**Flag back to backend (not a scope decision, a contract-precision
correction):** the backend PLAN's own contract table lists the staff
paths as `/api/users/{userId}/...`, but `StaffController.java` is
`@RequestMapping("/api/staff")` with `@DeleteMapping("/users/{userId}/permissions/{permission}")`
etc. — i.e. the real path is `/api/staff/users/{userId}/permissions/{permission}`,
confirmed against `staff-user.service.ts`'s existing (already-shipped)
calls. This frontend PLAN uses the real, existing path (above table).
This looks like a copy-paste omission in the backend PLAN's table, not
an intentional divergence — worth a one-line fix there, but it doesn't
block or change anything on the frontend side since the actual
generation-endpoint routes will live under the same controller/base
path once the backend implements them.

New/changed service methods:

- `ArticleService.generateDeletionToken(tenantId, articleId): Observable<string>`
  (new); `remove(tenantId, articleId, word): Observable<void>` (modified
  signature — adds `word`, sent as `{ body: { word } }`).
- `MemberService.generateRemovalToken(tenantId, membershipId): Observable<string>`,
  `generatePermissionRevocationToken(tenantId, membershipId, permission): Observable<string>`,
  `generateAccessGroupUnassignmentToken(tenantId, membershipId, accessGroupId): Observable<string>`
  (all new); `remove`, `revokePermission`, `unassignAccessGroup` (all
  modified — add trailing `word` param, sent as delete body).
- `StaffUserService.generatePermissionRevocationToken(userId, permission): Observable<string>`,
  `generateAccessGroupUnassignmentToken(userId, accessGroupId): Observable<string>`
  (new); `revokePermission`, `unassignAccessGroup` (modified — add
  trailing `word` param, sent as delete body).
- Each `generate*Token` method unwraps the `{ word }` response shape to
  a plain `Observable<string>` at the service boundary (`map(res =>
  res.word)`) so `ConfirmDialogComponent`'s `fetchToken` input type stays
  `() => Observable<string>` regardless of which of the six DTOs it's
  backed by.

## State and data

- All new state introduced by this feature is component-local
  (`ConfirmDialogComponent`'s `word`/`typed`/`loading`/`fetchError`/
  `invalidWordNotice` signals; each call site's `pending*`/`retryToken`
  signal pair) — nothing goes through a service/shared-signal, per the
  "no new shared state without the established service+signal shape"
  rule, because nothing here needs to be read outside the dialog/its
  immediate parent.
- No reactive forms — the retype input is a plain `signal`-bound
  `<input>` with `(input)`/`(paste)`/`(drop)`/`(dragover)` bindings,
  matching every other plain input in this codebase (e.g.
  `articles-page.component.ts`'s `uploadTitle`).

## Dependencies

None new. `HttpClient`, Angular signals, `@jsverse/transloco`'s
`TranslocoService` (already a dependency, just newly consumed by the new
interceptor) are all already present.

## Testing strategy

- `confirm-dialog.component.spec.ts` (extend existing/add): opening
  calls `fetchToken()` once and displays the resolved word (REQ-1/2);
  Confirm stays disabled until typed text matches exactly (REQ-3/4);
  Confirm emits the matched word (REQ-5); loading state disables Confirm
  and is visible while `fetchToken()` is pending (REQ-6); a `fetchToken()`
  error shows a retry affordance, retry re-invokes `fetchToken()`, Confirm
  stays disabled throughout (REQ-7); bumping `retryToken` discards the
  displayed word/typed input, shows the REQ-8 message, and re-invokes
  `fetchToken()` (REQ-8); `dismissed` discards word/typed state without
  calling any service (REQ-9/21); the word is never passed to
  `console.log`/`console.error` anywhere in the component (REQ-10, code
  inspection assertion — grep-style check in the spec); paste via a
  synthetic `ClipboardEvent`, drop via a synthetic `DragEvent`, and a
  simulated Ctrl+V keydown-then-paste-event sequence all leave the input
  unchanged and Confirm still disabled even if the blocked text would
  have matched (REQ-22); manual `(input)` events still update the typed
  signal normally after paste-blocking is wired (non-regression
  companion to REQ-22).
- `locale.interceptor.spec.ts` (new): outgoing request gains
  `Accept-Language` matching `TranslocoService.getActiveLang()`; changing
  the active lang (as `LanguageService.setLanguage` would) changes the
  header on the next request.
- Per-call-site specs (`articles-page.component.spec.ts` modified;
  `members-page.component.spec.ts`, `member-detail-panel.component.spec.ts`,
  `staff-user-detail-panel.component.spec.ts` extended): activating the
  destructive action opens the dialog instead of calling the delete
  service immediately (REQ-11/13/15/17/19); confirming with a matched
  word calls the correct `DELETE` service method with that word and, on
  success, updates the list/detail exactly as it does today (REQ-12/14/
  16/18/20); cancelling leaves the underlying resource/list unchanged
  and calls no service method.
