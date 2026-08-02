# SPEC — Deletion confirmation token

## Context and motivation

Today, deletion endpoints across `knowly` (article delete, tenant member
removal, permission/access-group revocation) execute as soon as the
caller has the right permission and hits the endpoint — there is no
extra proof-of-intent step beyond whatever the frontend's confirmation
UI chooses to show, which the backend has no way to enforce or verify.
The product owner wants a system-wide standard: for **any** deletion,
the backend generates a short-lived, single-use "security word" tied to
the specific resource instance and the caller who requested it; the
delete endpoint must be supplied that exact word to actually perform the
deletion. This closes the gap between "the UI showed a confirmation
dialog" (a client-side courtesy) and "the server can prove the caller
deliberately confirmed intent to delete this specific instance" (a
server-enforced precondition).

This SPEC defines the general, reusable mechanism (generation + single-use,
short-TTL, resource-instance-and-caller-scoped validation) and wires it
into **every** existing delete endpoint in the system:

- `ArticleController` `DELETE /api/tenants/{tenantId}/articles/{articleId}`
  (already backed by a shipped UI confirmation dialog — see
  `knowly-app`'s `article-management` SPEC's REQ-11–13).
- `TenantController` `DELETE /api/tenants/{tenantId}/members/{membershipId}`
  (member removal).
- `TenantController`
  `DELETE /api/tenants/{tenantId}/members/{membershipId}/permissions/{permission}`
  (per-tenant permission revocation).
- `TenantController`
  `DELETE /api/tenants/{tenantId}/members/{membershipId}/access-groups/{accessGroupId}`
  (per-tenant access-group unassignment).
- `StaffController` `DELETE /api/staff/users/{userId}/permissions/{permission}`
  (global/staff permission revocation).
- `StaffController`
  `DELETE /api/staff/users/{userId}/access-groups/{accessGroupId}` (global/staff
  access-group unassignment).

Each of the five non-article endpoints gets its own sibling
`POST .../deletion-confirmation-token` generation endpoint, gated by
whatever permission already guards the corresponding delete call, and
each delete call itself is required to validate a token per the generic
mechanism — mirroring exactly the pattern already used for article
deletion (REQ-13–15).

**Word-format constraint (added after initial approval):** the frontend
retype field for this word explicitly blocks paste — a human must type
the word by hand, with no clipboard shortcut, on every single deletion.
A mixed-case alphanumeric string (the initial, unconstrained phrasing of
REQ-2) risks producing something like `xK9m2Qw7` — visually ambiguous
characters (`0`/`O`, `1`/`l`/`I`), case-shifting, no pronounceability —
which is unreasonably hostile to manual, no-paste retyping. This SPEC
resolves that by constraining word generation to a **dictionary-word**
scheme rather than a character-soup one: see REQ-2 below. This is a
deliberate trade-off of raw entropy for human typability — appropriate
here because the token is a single-use, 5-minute-TTL proof-of-intent
check gated behind an already-authenticated, already-permissioned
session, not a long-lived credential, so full password-grade entropy
was never the actual requirement.

**Locale constraint (added after second amendment):** the initial
dictionary-word scheme (REQ-2) fixed the wordlist to English, on the
assumption that the confirmation word's content was arbitrary as long
as it was pronounceable and typable. That assumption doesn't hold for
trust: a Brazilian user on a pt-BR session seeing an English word (or an
international user seeing a Portuguese one) reads as out-of-place in a
security-confirmation flow, precisely the moment where the UI most
needs to feel legitimate. This SPEC now requires the wordlist to be
selected by the caller's active locale — see REQ-31. The mechanism
carrying that locale is the standard HTTP `Accept-Language` request
header: as of this amendment, `knowly-api` has no existing locale-
detection mechanism anywhere in `src/main/java` (confirmed by
inspection — no prior `Accept-Language`/`Locale` handling exists to
reuse), so rather than inventing a bespoke query parameter or a
persisted server-side user preference, this SPEC adopts
`Accept-Language` because it is the HTTP-native, stateless channel for
exactly this purpose, is natively understood by Spring's
`LocaleResolver`/`@RequestHeader` machinery, and requires no new
frontend state beyond what `knowly-app`'s Transloco-driven active
locale already implies about the browser's/request's language — how the
frontend ensures that header reflects Transloco's active locale (e.g.
an `HttpInterceptor` that sets it) is `knowly-app`'s concern, not this
SPEC's.

## User stories

- As a user with the delete permission on a resource, I want to be given
  a one-time security word when I'm about to delete something, and have
  to prove I actually saw and typed it, so an accidental or unauthorized
  deletion can't slip through on a stray click or a replayed request.
- As the system, I want deletion confirmation words to be impossible to
  guess, reuse, share across resource instances, or use after they've
  expired, so the mechanism is a real safeguard and not just UI theater.
- As a tenant member admin removing a member, revoking a permission, or
  unassigning an access group, I want the same proof-of-intent guarantee
  that article deletion has, so these equally consequential actions
  aren't a weaker link in the chain.
- As a staff admin revoking a global permission or unassigning a global
  access group from a user, I want the same guarantee, since these
  actions can affect cross-tenant access.
- As a developer wiring a future delete endpoint into this mechanism, I
  want the token-generation and validation contract to be generic across
  resource types, so I don't have to invent a new scheme per endpoint.
- As a user who has to type the confirmation word by hand with no paste
  shortcut available, I want that word to be made of ordinary,
  unambiguous words rather than random-looking characters, so I can type
  it correctly without needing to squint at it character by character.
- As a pt-BR user, I want my confirmation word to be a Portuguese word,
  and as an EN user I want mine to be an English word, so the word reads
  as a natural, legitimate part of my own session rather than a jarring,
  suspicious-looking anomaly at the exact moment I'm asked to trust a
  destructive confirmation flow.

## Requirements (EARS/GEARS)

- **REQ-1 [Ubiquitous]** The system shall provide a generic deletion
  confirmation token mechanism keyed by resource type, resource instance
  id, and the acting user's session, usable by any delete endpoint that
  opts in.
- **REQ-2 [Event-Driven]** When a caller who holds the relevant delete
  permission requests a deletion confirmation token for a specific
  resource instance, the system shall generate a confirmation word by
  drawing, using a cryptographically secure random source, **exactly two
  distinct words from a fixed, curated, in-repo wordlist of common,
  short (4–8 letter), unambiguous, all-lowercase words**, selected per
  the caller's active locale per REQ-31 (e.g. `garden-lamp` for an EN
  caller, `jardim-lampada` for a pt-BR caller — both illustrative only),
  and joining them with a single hyphen, then persist that word
  associated with that resource type, resource instance id, and the
  requesting user's identity, and return it in the response. Each
  locale's wordlist shall contain at least 512 entries so the two-word
  combination space remains large enough to resist guessing despite the
  5-minute TTL and single-use, single-session scoping (REQ-4, REQ-6).
  Each locale's wordlist shall exclude words that are homophones/near-
  duplicates of one another, profanity, or ambiguous to type on a
  standard keyboard (no diacritics, no punctuation other than the
  joining hyphen).
- **REQ-3 [Ubiquitous]** The system shall never auto-populate or
  transmit a deletion confirmation word to any caller other than the one
  who explicitly requested it via REQ-2 — the word is never generated or
  injected automatically as part of the delete call itself.
- **REQ-4 [Ubiquitous]** A generated deletion confirmation token shall
  expire 5 minutes after generation. This duration shall be configurable
  via application configuration, not hardcoded.
- **REQ-5 [Event-Driven]** When a delete endpoint wired to this
  mechanism is called, the system shall require the caller to supply the
  confirmation word alongside the request and shall reject the deletion
  if no word is supplied.
- **REQ-6 [Event-Driven]** When a supplied confirmation word exactly
  matches an unexpired, unused token previously generated for that exact
  resource type, resource instance id, and requesting user's session, the
  system shall proceed with the deletion and immediately invalidate that
  token so it cannot be used again. This is a literal string match
  against the exact word stored at generation time (REQ-2/REQ-31) —
  there is no cross-locale equivalence: if the word was generated as
  `jardim-lampada` (pt-BR), only that exact string is accepted, and its
  English equivalent (`garden-lamp`) is rejected as a non-match, even
  though both would have been valid *generation* outputs for different
  locales. The locale only determines which wordlist REQ-2 draws from at
  generation time; it plays no role in validation.
- **REQ-7 [Unwanted Behavior]** If a supplied confirmation word does not
  match any unexpired, unused token for that exact resource type,
  resource instance id, and calling user's session, then the system shall
  reject the deletion, leave the resource untouched, and not reveal
  whether the word was wrong, expired, already used, or generated for a
  different resource/user (a single generic "invalid or expired
  confirmation" error).
- **REQ-8 [Unwanted Behavior]** If a confirmation word was generated for
  one resource instance (e.g. article A) and supplied on a delete request
  for a different resource instance (e.g. article B) — even of the same
  resource type — then the system shall reject the deletion (per REQ-7).
- **REQ-9 [Unwanted Behavior]** If a confirmation word was generated by
  one user/session and supplied by a different user/session, then the
  system shall reject the deletion (per REQ-7).
- **REQ-10 [Unwanted Behavior]** If a confirmation token has expired
  (per REQ-4) and its word is then supplied on a delete request, then the
  system shall reject the deletion (per REQ-7) and the expired token
  shall remain unusable even if requested again before a new token is
  generated.
- **REQ-11 [Event-Driven]** When a deletion succeeds using a given
  confirmation token (per REQ-6), the system shall invalidate that token
  such that it cannot be reused for a subsequent deletion attempt on the
  same or any other resource instance.
- **REQ-32 [Event-Driven]** When a delete request supplies a word that
  matches a live token's resource instance and requesting user but the
  word itself is wrong (a mismatch, not expiry/reuse/wrong-resource/
  wrong-user), the system shall invalidate that token immediately, the
  same as a correct match (REQ-11), rather than leaving it live for
  further attempts. This closes the brute-force window a 5-minute TTL
  would otherwise leave open for a caller who already holds delete
  permission on the resource: a single wrong guess costs the caller a
  fresh token request (REQ-12), it does not cost them nothing. This
  takes priority over convenience for typos — a mistyped word requires
  generating and displaying a new one, the same UX as if it had expired.
- **REQ-12 [Event-Driven]** When a new deletion confirmation token is
  requested for a resource instance that already has a live (unexpired,
  unused) token from the same requesting user, the system shall generate
  a fresh token and invalidate the previous one, so only one live token
  per (resource instance, requesting user) pair exists at a time.
- **REQ-13 [Event-Driven]** When `DELETE
  /api/tenants/{tenantId}/articles/{articleId}` is called, the system
  shall require and validate a deletion confirmation token scoped to that
  article instance and the calling user, per REQ-5 through REQ-11, before
  performing the deletion.
- **REQ-14 [Event-Driven]** When a caller with `ARTICLE_DELETE`
  permission requests a deletion confirmation token for a specific
  article (`POST
  /api/tenants/{tenantId}/articles/{articleId}/deletion-confirmation-token`
  or equivalent), the system shall generate and return the token per
  REQ-2, scoped to that tenant and article instance.
- **REQ-15 [Unwanted Behavior]** If a caller without `ARTICLE_DELETE`
  permission requests a deletion confirmation token for an article, then
  the system shall reject the request (403) and generate no token.
- **REQ-16 [Event-Driven]** When `DELETE
  /api/tenants/{tenantId}/members/{membershipId}` is called, the system
  shall require and validate a deletion confirmation token scoped to that
  membership instance and the calling user, per REQ-5 through REQ-11,
  before removing the member.
- **REQ-17 [Event-Driven]** When a caller with the permission that
  already guards member removal requests a deletion confirmation token
  for a specific membership (`POST
  /api/tenants/{tenantId}/members/{membershipId}/deletion-confirmation-token`
  or equivalent), the system shall generate and return the token per
  REQ-2, scoped to that tenant and membership instance.
- **REQ-18 [Unwanted Behavior]** If a caller without the permission that
  guards member removal requests a deletion confirmation token for a
  membership, then the system shall reject the request (403) and
  generate no token.
- **REQ-19 [Event-Driven]** When `DELETE
  /api/tenants/{tenantId}/members/{membershipId}/permissions/{permission}`
  is called, the system shall require and validate a deletion
  confirmation token scoped to that specific (membership, permission)
  revocation instance and the calling user, per REQ-5 through REQ-11,
  before revoking the permission.
- **REQ-20 [Event-Driven]** When a caller with the permission that
  already guards tenant permission revocation requests a deletion
  confirmation token for a specific (membership, permission) pair
  (`POST
  /api/tenants/{tenantId}/members/{membershipId}/permissions/{permission}/deletion-confirmation-token`
  or equivalent), the system shall generate and return the token per
  REQ-2, scoped to that tenant, membership, and permission instance.
- **REQ-21 [Unwanted Behavior]** If a caller without the permission that
  guards tenant permission revocation requests a deletion confirmation
  token for a (membership, permission) pair, then the system shall reject
  the request (403) and generate no token.
- **REQ-22 [Event-Driven]** When `DELETE
  /api/tenants/{tenantId}/members/{membershipId}/access-groups/{accessGroupId}`
  is called, the system shall require and validate a deletion
  confirmation token scoped to that specific (membership, access group)
  unassignment instance and the calling user, per REQ-5 through REQ-11,
  before unassigning the access group.
- **REQ-23 [Event-Driven]** When a caller with the permission that
  already guards tenant access-group unassignment requests a deletion
  confirmation token for a specific (membership, access group) pair
  (`POST
  /api/tenants/{tenantId}/members/{membershipId}/access-groups/{accessGroupId}/deletion-confirmation-token`
  or equivalent), the system shall generate and return the token per
  REQ-2, scoped to that tenant, membership, and access-group instance.
- **REQ-24 [Unwanted Behavior]** If a caller without the permission that
  guards tenant access-group unassignment requests a deletion
  confirmation token for a (membership, access group) pair, then the
  system shall reject the request (403) and generate no token.
- **REQ-25 [Event-Driven]** When `DELETE
  /api/staff/users/{userId}/permissions/{permission}` is called, the system
  shall require and validate a deletion confirmation token scoped to that
  specific (user, permission) revocation instance and the calling user,
  per REQ-5 through REQ-11, before revoking the permission.
- **REQ-26 [Event-Driven]** When a caller with the permission that
  already guards staff permission revocation requests a deletion
  confirmation token for a specific (user, permission) pair (`POST
  /api/staff/users/{userId}/permissions/{permission}/deletion-confirmation-token`
  or equivalent), the system shall generate and return the token per
  REQ-2, scoped to that user and permission instance.
- **REQ-27 [Unwanted Behavior]** If a caller without the permission that
  guards staff permission revocation requests a deletion confirmation
  token for a (user, permission) pair, then the system shall reject the
  request (403) and generate no token.
- **REQ-28 [Event-Driven]** When `DELETE
  /api/staff/users/{userId}/access-groups/{accessGroupId}` is called, the
  system shall require and validate a deletion confirmation token scoped
  to that specific (user, access group) unassignment instance and the
  calling user, per REQ-5 through REQ-11, before unassigning the access
  group.
- **REQ-29 [Event-Driven]** When a caller with the permission that
  already guards staff access-group unassignment requests a deletion
  confirmation token for a specific (user, access group) pair (`POST
  /api/staff/users/{userId}/access-groups/{accessGroupId}/deletion-confirmation-token`
  or equivalent), the system shall generate and return the token per
  REQ-2, scoped to that user and access-group instance.
- **REQ-30 [Unwanted Behavior]** If a caller without the permission that
  guards staff access-group unassignment requests a deletion confirmation
  token for a (user, access group) pair, then the system shall reject the
  request (403) and generate no token.
- **REQ-31 [Complex]** When any deletion confirmation token generation
  endpoint (REQ-14, REQ-17, REQ-20, REQ-23, REQ-26, REQ-29) is called,
  the system shall determine the caller's active locale from the
  request's `Accept-Language` header and:
  - if the header's highest-priority tag matches `pt-BR` (or the bare
    `pt` primary tag), the system shall draw REQ-2's two words from the
    pt-BR wordlist;
  - otherwise — including a missing, empty, unparseable, or any
    unrecognized `Accept-Language` value — the system shall draw REQ-2's
    two words from the EN wordlist, i.e. EN is the default/fallback
    locale for word generation.
  This resolution happens once per token generation and does not persist
  a locale preference anywhere; it is derived fresh from each request's
  header.

## Non-functional requirements

- Security: confirmation words are generated using a cryptographically
  secure random source, are stored hashed (never plaintext) — mirroring
  this codebase's existing one-time-secret convention (see
  `constitution.md`'s "one-time secrets ... always stored hashed, never
  in plaintext") — and are never logged in plaintext anywhere (structured
  logs, audit events, traces). Token generation and validation are
  themselves audit-logged (actor, resource type/id, outcome), consistent
  with this project's "every state-changing action ... must emit a
  structured log/audit event" rule. This applies uniformly to all six
  wired endpoints.
- Security: token validation must not leak, via response shape or timing,
  which specific failure mode occurred (wrong word vs. expired vs.
  already used vs. wrong resource vs. wrong user) — see REQ-7. This
  applies uniformly to all six wired endpoints.
- Usability: the confirmation word (REQ-2) must be typable by hand,
  without paste, in a single short attempt — this is why word generation
  is constrained to a fixed, curated, all-lowercase, two-word,
  hyphen-joined dictionary scheme rather than mixed-case/alphanumeric
  random characters. The curated wordlist itself is a static, versioned,
  in-repo resource (not fetched or generated at runtime), so its content
  is reviewable and stable across deployments.
- Usability/i18n: the pt-BR wordlist's no-diacritics constraint (REQ-2,
  carried over unchanged from the EN list's typability rule) is a
  deliberate, explicitly-flagged trade-off. Standard Portuguese
  orthography uses diacritics pervasively (`á`, `ã`, `ç`, `é`, `í`, `ó`,
  `õ`, `ú`), so an accent-free pt-BR wordlist is necessarily one of two
  things, and this SPEC requires the former:
  1. **entries that are already accent-free in correct Portuguese**
     (e.g. `jardim`, `lampada` is actually `lâmpada` correctly, so this
     option alone is too restrictive to reach 512 unambiguous, common,
     4–8 letter entries), or
  2. **accent-stripped versions of accented words** (e.g. `lâmpada` →
     `lampada`, `código` → `codigo`), which are not correctly spelled
     standalone Portuguese but remain unambiguous to read and type on a
     standard keyboard.
  Given option 1 alone cannot realistically fill a 512-entry list of
  common, short, unambiguous words, the pt-BR wordlist is built
  primarily from option 2: accent-stripped Portuguese words. This is the
  same typability trade-off already accepted for the EN scheme (real
  entropy in exchange for guaranteed no-paste-needed manual typing) and
  is consistent with it, but it does mean pt-BR users will see words
  that are recognizable but not their standard dictionary spelling
  (`lampada`, not `lâmpada`) — this is a conscious, documented
  compromise, not an oversight, and is called out here so it isn't
  mistaken for a pt-BR wordlist quality bug during review or QA.
- Performance/SLA: token generation and validation are single-row
  lookups/writes and must not introduce a noticeable delay to any of the
  six existing delete flows.
- Observability: token expiry duration (REQ-4) is read from
  configuration so it is visible/auditable in deployed config, not
  buried in code.
- i18n: locale resolution (REQ-31) reads only the standard
  `Accept-Language` request header — no new persisted user-preference
  field, session attribute, or query parameter is introduced by this
  SPEC for this purpose.

## Acceptance criteria

- [x] A generic mechanism exists for generating and validating deletion
      confirmation tokens, keyed by resource type + resource instance id
      + requesting user/session.
- [x] A generated token's word is composed of exactly two distinct,
      all-lowercase, unambiguous dictionary words joined by a hyphen,
      drawn from a fixed in-repo wordlist of at least 512 entries in the
      locale resolved per REQ-31, using a cryptographically secure random
      source; the word is stored hashed and returned in plaintext exactly
      once, in the generation response.
- [x] Both an EN wordlist and a pt-BR wordlist exist in-repo at launch,
      each with at least 512 entries, each excluding ambiguous,
      diacritic-bearing, punctuation-bearing (other than the joining
      hyphen), or profane/near-duplicate entries — the pt-BR list is
      built primarily from accent-stripped Portuguese words per the
      "Usability/i18n" non-functional requirement's documented
      trade-off.
- [x] When a token generation request's `Accept-Language` header
      indicates `pt-BR`/`pt`, the returned word is drawn from the pt-BR
      wordlist; for any other, missing, or unparseable header value, the
      returned word is drawn from the EN wordlist (default).
- [x] A token expires 5 minutes after generation by default, and this
      duration is configurable.
- [x] A token is invalidated after a single successful use.
- [x] A token cannot be used to delete a resource instance other than
      the one it was generated for.
- [x] A token generated by one user cannot be used by another user.
- [x] An expired or already-used token is rejected the same way as a
      wrong word (no distinguishing information in the response).
- [x] Requesting a new token for a resource instance that already has a
      live token from the same user invalidates the previous one.
- [x] `DELETE /api/tenants/{tenantId}/articles/{articleId}` requires a
      valid confirmation token scoped to that article and caller, and
      rejects the deletion without one.
- [x] A new endpoint lets a caller with `ARTICLE_DELETE` permission
      generate a confirmation token for a specific article; callers
      without that permission cannot.
- [x] `DELETE /api/tenants/{tenantId}/members/{membershipId}` requires a
      valid confirmation token scoped to that membership and caller, and
      rejects the removal without one; a sibling generation endpoint
      exists, gated by the same permission as member removal.
- [x] `DELETE
      /api/tenants/{tenantId}/members/{membershipId}/permissions/{permission}`
      requires a valid confirmation token scoped to that (membership,
      permission) pair and caller, and rejects the revocation without
      one; a sibling generation endpoint exists, gated by the same
      permission as tenant permission revocation.
- [x] `DELETE
      /api/tenants/{tenantId}/members/{membershipId}/access-groups/{accessGroupId}`
      requires a valid confirmation token scoped to that (membership,
      access group) pair and caller, and rejects the unassignment without
      one; a sibling generation endpoint exists, gated by the same
      permission as tenant access-group unassignment.
- [x] `DELETE /api/staff/users/{userId}/permissions/{permission}` requires a
      valid confirmation token scoped to that (user, permission) pair and
      caller, and rejects the revocation without one; a sibling
      generation endpoint exists, gated by the same permission as staff
      permission revocation.
- [x] `DELETE /api/staff/users/{userId}/access-groups/{accessGroupId}`
      requires a valid confirmation token scoped to that (user, access
      group) pair and caller, and rejects the unassignment without one; a
      sibling generation endpoint exists, gated by the same permission as
      staff access-group unassignment.
- [x] Token generation and validation (success and failure) are
      audit-logged with actor, resource type/id, and outcome, across all
      six wired endpoints.

## Out of scope

- Any change to how the delete confirmation *UI* behaves — that is
  `knowly-app`'s `deletion-confirmation-token` SPEC's concern, including
  ensuring the `Accept-Language` header sent with token-generation
  requests reflects Transloco's active locale.
- Rate-limiting/throttling token-generation requests beyond what already
  applies to authenticated endpoints generally — no new abuse-specific
  throttling is introduced here.
- Notifying or auditing a user whose confirmation token was invalidated
  by someone else generating a fresh one for the same resource (REQ-12)
  — silent invalidation is sufficient for this SPEC.
- A "list my live confirmation tokens" or cancellation endpoint — tokens
  are only ever created (implicitly superseding any prior live token per
  REQ-12) or consumed/expired; no separate management surface.
- Any other delete-shaped endpoint not explicitly named in this SPEC's
  "Context and motivation" list (i.e. any delete endpoint added to the
  system after this SPEC is written) — wiring a future new delete
  endpoint into this mechanism is a separate SPEC when that endpoint is
  introduced.
- User-configurable word count/length or a character-based
  (non-dictionary) generation mode — REQ-2's two-word, fixed-wordlist
  scheme is the only generation mode this SPEC defines; introducing
  alternatives is a separate SPEC if ever needed.
- Any locale beyond EN and pt-BR — these are, as of this amendment, the
  only two locales `knowly-app` ships (`public/i18n/en.json` and
  `public/i18n/pt-BR.json`); adding a third UI locale and its
  corresponding confirmation wordlist is a separate SPEC when/if
  `knowly-app` adds a third locale.
- A persisted, server-side "user's preferred locale" field, or any
  locale source other than the `Accept-Language` header of the token
  generation request itself (e.g. a stored account setting, a tenant
  default, a query parameter) — REQ-31 resolves locale per-request from
  the header only.
- Retroactively re-generating or translating a confirmation word for an
  already-issued, still-live token if the caller's locale changes
  mid-TTL (e.g. they switch the UI language between requesting the token
  and typing it) — the word's locale is fixed at generation time for the
  life of that token; the user would need to request a fresh token to
  get a word in the new locale, which is the existing REQ-12 behavior
  and not new here.
</content>
