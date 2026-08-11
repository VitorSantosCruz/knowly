# Project status

> **Read this before starting any work in this repo — in any conversation,
> with any AI assistant.** This file exists so that a fresh conversation
> (no memory of prior sessions) can pick up exactly where the last one left
> off, without re-deriving context from scratch. It is checked into git,
> so it travels with the repo regardless of which tool or model opens it.
> Since the 2026-07-25 monorepo migration, there is exactly **one** of
> this file, covering both `knowly-api/` (backend) and `knowly-app/`
> (frontend) — there used to be a separate copy per repo; don't recreate
> that split.
>
> **You must also update it before finishing your work.** This is not
> optional and not just for Claude — any AI assistant (Claude, GPT, Gemini,
> whatever) that implements or changes a feature in this repo is expected
> to edit this file as part of that task, the same way it's expected to
> run the test suite. Concretely, before considering a task done:
> - Update the feature's row in the relevant table below (status, one-line
>   note) if you finished, started, or changed the shape of a feature.
> - Add a bullet to "Known operational/tooling notes" if you hit and fixed
>   a gotcha someone else would otherwise waste time on again.
> - If the long-term direction in [`VISION.md`](VISION.md) changed based
>   on something the user said, update that file too — it's meant to stay
>   current, not be a historical snapshot.
> If you finish a session without touching this file and something
> changed, the next conversation (possibly a different AI, possibly the
> user talking to a teammate's assistant) starts from stale information —
> that defeats the entire point of this file existing.

## Next up

> **This section exists specifically for whenever the user opens a
> conversation without specifying what to work on** — regardless of how
> that's phrased or in what language; judge intent, not wording. It must
> always name a concrete, literal next action — not a restatement of the
> backlog tables below. Whoever finishes a task (any AI) updates this
> section before signing off, so the *next* conversation — possibly
> opened cold, possibly by a different AI — knows exactly what to do
> without the user having to re-explain anything.
>
> Protocol for handling a direction-less request with no other context:
> 1. Read this section. If it names a concrete next action, do that (or
>    propose it and ask for a quick go-ahead if it's a meaningfully sized
>    new feature) — following SDD (SPEC → PLAN → TASKS → Implement →
>    Analyze) as normal.
> 2. If this section says there's nothing queued (current state, see
>    below), **do not silently invent a feature and start building it.**
>    Propose 2-4 concrete candidate directions and ask the user to pick —
>    draw them from `VISION.md`'s "What's deliberately not decided yet"
>    section, or from anything not-yet-built that's implied by the
>    product vision. Then update this section with whatever they choose,
>    even before writing the SPEC, so a crash/restart mid-conversation
>    doesn't lose that decision either.
> 3. Once a direction is chosen and there's an in-progress SPEC/PLAN/TASKS
>    for it, this section should say so directly (e.g. "Implementing
>    `<feature>` — TASKS.md items 5-12 remain, currently on item 7:
>    <what it is>"), not just "in progress."

**Current state (2026-08-10): AppSec follow-up fix on
`chat-message-search`'s role-based scoping (REQ-5e-REQ-5j) — the PO's
"if I'm in tenant X, only tenant X's stuff shows, even if I have
conversations/groups in other tenants or in staff scope" requirement
was not fully held for the staff-no-active-tenant case.**
`ChatMessageSearchRepository`'s `SCOPE_PARTICIPANT_AND_DISCOVERABLE`
fragment's `(:activeTenantId IS NULL OR cc.tenant_id = :activeTenantId)`
guard was trivially true for every row once `activeTenantId` was
`null`, so a caller who is simultaneously a tenant `MEMBER` and a
`STAFF` user got an unrestricted cross-tenant participant scan (every
tenant they happen to hold a `chat_participants` row in) instead of
staff-scope-only (`cc.tenant_id IS NULL`) results when searching with
no active tenant selected — this applied to `PEER_GROUP` and
`PEER_DIRECT` (1:1) alike, since the scope fragment never branches on
conversation kind. Fixed by requiring `cc.tenant_id IS NULL` in that
branch: `((:activeTenantId IS NULL AND cc.tenant_id IS NULL) OR
cc.tenant_id = :activeTenantId)`. New/updated regression coverage in
`ChatMessageSearchControllerIntegrationTest`:
`peerDirectConversationsAreIsolatedByActiveTenantAndStaffScopeJustLikeGroups`
(a user who is a `MEMBER` of two tenants and also has a staff-scope 1:1
sees only the active tenant's content while a tenant is active, and
only the staff-scope content once cleared) and
`staffWithNoActiveTenantGetsResultsFromTheirOwnDirectParticipantConversation`
(rewritten to also assert a tenant-owned conversation the same staff
user participates in no longer leaks into staff-scope results). All 43
tests across `ChatMessageSearchRepositoryTest`/`ChatMessageSearchServiceTest`/
`ChatMessageSearchControllerIntegrationTest` green.

**Previous state (2026-08-10): `chat-message-search`'s Amended
(2026-08-10) unified search bar shipped end-to-end on both sides,
replacing the item-16 filter-dialog cut noted below.** Backend adds
`GET /api/chat/search` (combined people/groups/Support/RAG entity
search + "recent places", see that feature's own PROJECT_STATUS row)
alongside the still-live `GET /api/chat/messages/search`. Frontend
retires `chat-search-dialog.component.ts` entirely (deleted, along
with its sidebar "Buscar mensagens" icon button) and ships
`chat-unified-search.component.ts` — a Slack-style, debounced (400ms)
type-ahead bar firing both `ChatMessageSearchService.search(q)` (now
narrowed to `search(q: string)`, filter params removed) and the new
`ChatEntitySearchService.search(q)` in parallel, rendering five result
groups (People, Groups, Base de artigos, Support, Messages — a group
with zero matches is omitted, not shown empty), per-group "see more"
(`expandSection()` for People/Groups/RAG, `loadMore()` for Messages,
none for Support), and a "recent places" list on blank query/focus.
Top-level status is a `computed()` with the accepted two-domain
partial-failure granularity (entities vs. messages, not five-way) —
see that feature's own SPEC.md/PLAN.md "known gap" notes; a group
whose own entity domain failed shows an inline per-group error badge
rather than blanking the whole dropdown. Companion `chat-unified-ui`
change (same date): `ChatShellComponent` gains a new
`chat-search-bar-region` header above its existing 3-column layout,
hosting `<app-chat-unified-search>` with no inputs — outside the
narrow-viewport single-pane collapse by construction, so it never
disappears; column 1 (`chat-directory.component.ts`) and column 3
(`chat-full-directory.component.ts`) lose their own per-column search
inputs entirely (their browsing/partition/click-to-open-or-create/
join/request-to-join logic and sort order are unchanged), and the
now-dead `chat-directory-search.util.ts` is deleted. 962/962 frontend
tests green, `format:check`/`build`/`lint` all clean. **Nothing is
currently queued next.** One candidate direction remains documented
as deliberately deferred, not started, and explicitly *not* to be picked
up silently (per this section's own protocol): item 17, frictionless/
passkey login for trusted devices — further below in this list with full
technical groundwork already written up. Per protocol: propose this
(plus anything from `VISION.md`'s "What's
deliberately not decided yet") to the user and get a pick before
starting a new SPEC.

**Previous state (2026-08-08): `soft-delete-default-filter` backend feature
done** — soft-delete filtering is now a Hibernate `@Filter`-enforced
standing default (mirrors `TenantFilter`) for 11 of the 13 originally
in-scope entities, closing the `ChatEligibilityService`/
`ChatConversationService` unfiltered-query bug class. `AccessGroup`/
`AccessGroupPermission` were a known, deliberately-left-open gap at the
time (no `deleted_at` column) — **now closed** by `role-permission-revoke`
above, which added the `global_access_group_permissions` migration this
note asked for (the tenant-side `access_group_permissions` table already
had it from `tenant-access-group-bulk-and-delete`'s `V29`). A real Phase 5
(dropping the now-redundant `*DeletedAtIsNull` method-name suffixes, SPEC
requirement 8) was also deliberately left undone after finding one call
site (`ProfileEditRequestService`) that genuinely needs the explicit
predicate (runs outside any `@Transactional` context) — a full pass
needs a per-call-site `@Transactional` audit across ~20 usages first;
not started. Full-suite `./mvnw verify` green.

**Previous state (2026-08-08): `tenant-access-group-management` (frontend)
+ `tenant-access-group-bulk-and-delete` (backend) shipped end-to-end —
SPEC → PLAN → TASKS → implement → merge, both AppSec-reviewed before
their TASKS.md, no blocking findings. Gives the tenant scope its own
dedicated access-group management screen (`/tenants/access-groups`,
nav entry under Team) — previously the only way to reach a tenant's
access groups at all was through a specific member's detail panel, even
though a group is independent of any one member. Adds a real bulk
multi-group assignment endpoint (`POST .../access-groups:batch`) and
group deletion with cascading soft-delete (group + its member
assignments + its permission grants, one transaction), closing two gaps
that had no backend support before today. Both subprojects verified
green in isolation after merge (911/911 backend tests via
`./mvnw verify`, frontend format/test/build/lint clean) — an initial
combined run showed spurious Spring context failures that turned out to
be Testcontainers/CPU contention from running both subprojects' full
suites at once, not a real regression; re-run alone came back clean.
**Nothing else queued next** — see this section's own protocol above
(propose candidates from `VISION.md`, don't silently start a new
feature).

**Previous state (2026-08-05): follow-up Playwright QA pass over the
soft-delete-everywhere work below found and fixed 3 more real gaps, plus ruled
out a 4th as a false alarm from a broken dev-server process (not a code bug).
All fixes are committed and both subprojects are fully green (859/859 backend
tests via `./mvnw verify`, 669/669 frontend tests, format/build/lint clean).**
(1) `ActiveTenantService#fetch()` (frontend) had no error handler on
`GET /api/tenants/active`, so once a session's active tenant was soft-deleted
(a 403 `TENANT_ACCESS_DENIED`), `activeTenantResolved()` never flipped `true`
and every page gating on it (member dashboard, articles, conversations, ...)
hung forever on its loading state — fixed with `catchError`, and the backend
endpoint itself now self-heals: it clears the stale session attribute and
returns `204` instead of repeating `403` forever. (2) The
unassign-access-group/revoke-permission confirmation dialogs still said "This
cannot be undone" in both locales, no longer accurate now that these are
reactivatable soft-deletes — copy updated to reflect they can be reversed via
the same panel. (3) Tenant deletion had a complete backend flow
(`POST .../deletion-confirmation-token` + `DELETE /api/tenants/{id}`,
`TENANT_DELETE`-gated) but zero frontend UI anywhere in `knowly-app` — added a
staff-only "Delete" button + the same confirmation-dialog pattern as
staff-user/member deletion to `select-tenant-page.component.ts` (the existing
staff all-tenants listing). Also fixed in passing while investigating (2): a
profile-edit-request's contact REMOVE change showed only the bare word
"REMOVE" in the approver's inbox, no indication of which contact — root cause
was `profile-fields-form.component.ts#diffContactChanges()` nulling out
type/value/label for a REMOVE instead of carrying the original contact's
values along (the backend already stores whatever it's sent verbatim). (4)
A suspected 500 on `POST /api/auth/login-code/verify` for a soft-deleted user
(contradicting the passing `verifyCodeForASoftDeletedUserIsRejectedLikeInvalidCredentials`
integration test) turned out to be an artifact of the old dev server process
having cascaded through unrelated startup failures (a stale `logback-spring.xml`
parse error, then an `S3Client` bean failure) earlier in the session — the
source `logback-spring.xml` itself was never actually broken. Reproduced
cleanly against a freshly-restarted backend: correct `401 INVALID_CREDENTIALS`,
matching the test. No code fix needed there.

**Current state (2026-08-04): three manual, full-app Playwright QA passes (not
a feature) found and fixed 17 real bugs across login, i18n, tenant-scoped
routing, tenant creation, staff/tenant permission granting, chat, member/
staff-user creation, identity, and support tickets — see the "Known
operational/tooling notes" section and each affected feature's row below for
detail, and `git log` since `66a4ae9` for the individual commits. All 17 are
now closed; there is no carried-over bug from this pass.** Bug #17
(`59cd5d5`, the session's biggest): deleting any real staff user 500'd —
`StaffService#deleteStaffUser` called `userRepository.delete(user)` directly,
and every staff account has at least a mandatory `user_profiles` row (and
virtually all have `audit_events` rows from just logging in), with none of
the 15 FK references onto `users.id` carrying `ON DELETE CASCADE`. Root-cause
discussion escalated into a standing, system-wide architectural decision —
**no destructive operation may physically remove a row, ever** — see
`DECISIONS.md`'s "Logical delete is now a standing, system-wide rule" entry
for the full rationale and consequences (partial unique indexes, reactivate-
on-regrant, deletedAt-filtered permission-resolution reads, tenant-delete
now cascading to its own articles/conversations, login rejection for a
deleted account). Migration `V28` retrofits `deleted_at` onto every entity
that was previously hard-deleted or needed the stronger marker; 850/850
backend tests green (`./mvnw verify`), spotless clean. Bug #16
(`65c8c23`): viewing a staff user's audit trail after a batch permission
update, a deletion-confirmation-token generate/validate, or a denied
tenant-member-creation attempt rendered the raw backend action string
instead of a label, with a transloco "Missing translation" console warning
per occurrence — 4 `auditActions.*` keys were missing from both locale
files; fixed by cross-checking every `@AuditLog`/`AuditEvent` action string
in `knowly-api` against both locale files (these 4 were the only gaps). The second pass
specifically exercised (via Playwright, against the real backend, not just
component specs): staff user creation, granting a permission to a member,
publishing an article, 1:1 chat between two real tenant members, group chat,
and a full profile-edit-request submit-then-approve cycle — all now work end
to end. "Conversar com a doc" (RAG chat grounded in an uploaded article) could
not be fully verified beyond the retrieval step — the dev environment's
OpenAI key has no remaining credits, an environment limitation, not a code
bug. The third pass (commits `b49e741`, `398f339`) found and fixed four
related support-ticket bugs, all surfaced while exercising the full
claim → transfer → close lifecycle as staff: (1) backend —
`SupportTicketService#findChannel` already filters by an explicit `tenantId`,
but `TenantFilterAspect`'s ambient Hibernate `@Filter` ANDed in the staff's
*currently-active* tenant on top of it, permanently 404ing
(`CHAT_CONVERSATION_NOT_FOUND`) any ticket for a tenant other than the one
currently active — fixed with `@BypassTenantFilterForOversight`, same
pattern as `ChatOversightConversationLoader`; (2) frontend —
`SupportPageComponent` rendered its member-channel branch as soon as
`activeTenantId()` resolved, without waiting for
`globalPermissionsService`/`profileService` to also resolve, occasionally
firing `GET /api/tenants/{activeTenantId}/support/members/null/channel`
(wrong tenant, null member) — fixed by gating rendering on a new
`viewerReady` computed; (3) `StaffSupportChannelComponent#transfer()`/`close()`
called `.subscribe()` with no error handler, so a failed transfer (bad target
id, target lacking `STAFF_SUPPORT_HANDLE`) or a failed close left the staff
viewer with zero feedback — now surfaces a dismissable error message; (4)
`SupportService.activeTicket()` was only ever populated by the
claim/transfer/close response bodies, so a page reload (or a direct
`/support/:channelId` link) after claiming a ticket permanently lost the
transfer/close controls for that session even though the backend still
considered the viewer the assignee — added
`GET /api/tenants/{tenantId}/support/members/{memberUserId}/ticket`
(404 when the channel has no non-CLOSED ticket) to re-hydrate that state on
init.

**Before that (2026-08-02): `staff-members-management-redesign` is now
fully done — see its row below in this list. This was picked up outside
the confirmed 2026-07-25 backlog order (which closed out with
`internal-team-chat`, see below), at the user's explicit direction.**
Per this section's own protocol:
propose 2-4 concrete candidate directions to the user and ask them to
pick, drawing from `VISION.md`'s "What's deliberately not decided yet"
section or anything not-yet-built implied by the product vision — do
not silently invent one. One candidate worth surfacing precisely because
it's low-effort/high-value: `internal-team-chat`'s own PLAN flagged
real-time delivery (currently 5s client polling, see `DECISIONS.md`) as
a documented future direction, not a gap — only worth picking up if/when
an actual latency complaint justifies it, not proactively.

**Older history below, preserved for context:**

`staff-bootstrap-user`, `staff-rbac-split`,
`staff-user-provisioning`, `navigation-menu`, `welcome-screen`, and the
`dashboard-analytics` backend are all done. Item 5 (user management
screens) is now done on both sides**: the backend half
(`staff-user-listing`, `GET /api/staff/users`) is fully done — verified,
`qa-test-automation`/`appsec`-reviewed, and committed; the frontend half
(`user-management-screens`) is fully implemented, tested (253/253
frontend tests green), AppSec-reviewed (no blocking findings), and
committed — see `knowly-app/specify/features/user-management-screens/`.
`tenant-membership-acceptance`, `identity-profile-model`, and
`global-staff-dashboard-metrics` are also now fully done (verified,
reviewed, committed) — see their table rows below. `staff-audit-trail-view`
(`GET /api/staff/users/{userId}/audit-trail`, backend) is now also fully
done — implemented, tested, `./mvnw verify` green,
`qa-test-automation`/`appsec` reviewed with no blocking findings
(cross-tenant REQ-4 exposure re-confirmed as intentional/approved, not a
gap), and committed. The `dashboard-analytics` frontend (item 6's
tenant-scoped half) is now also fully implemented, tested (253/253
frontend tests green), and committed — see its table row below.
**Item 6's remaining staff global-view half is also now done**
(`staff-global-dashboard`, frontend, 2026-07-28): global metrics on
`/dashboard` for staff-outside-tenant, a `/welcome` quick-link, and an
audit-trail section on the staff directory's detail panel — 271/271
frontend tests green, committed, see its table row below and item 6's
detail above. Item 6 is now fully closed on both sides.

**`tenant-pagination-search` is now also fully closed on both sides**
(frontend, 2026-07-28): `/select-tenant`'s 0-membership staff fallback
now consumes the backend's paginated `GET /api/tenants` envelope, with
a debounced search input and prev/next pagination — 284/284 frontend
tests green, `qa-test-automation`/`appsec` reviewed with no blocking
findings, committed. See its table row below.

**`identity-profile-model-v2` retrofit is now fully done on both
sides (2026-07-29), including both backend follow-ups (`c0a817d`).**
Backend (`f9f2426`, `./mvnw verify` green) split personal data into
`UserProfile`/`Address`/`Contact` tables, made `avatarUrl` the only
directly self-editable field (a dedicated multipart `POST
/api/users/me/profile/avatar`), and removed the old
`STAFF_ADMIN`/`MEMBER_ADMIN` self-direct-edit bypass entirely — see
that feature's table row above. Frontend (`user-profile-v2`,
2026-07-29) retrofits `user-profile` in place to match — see its table
row below for the full detail. The two backend follow-ups discovered
during that frontend work — (1) `PUT /api/users/{id}/profile` never
applied `contactChanges`, and (2) `ProfileEditRequestDto.proposedFields
.address` was always `null` in the list/submit responses despite being
persisted — are now both closed (`c0a817d`): direct-edit accepts
`{fields, contactChanges}` and applies them via the same
`UserProfileService#applyFields` choke point the approve path uses,
and `proposedFieldsOf` populates `address` from the persisted request
row in both endpoints (`.contacts` stays `null` by deliberate design —
the proposed contact set is represented via the separate
`proposedContactChanges` field instead). 336/336 frontend tests green,
`format:check`/`build` clean.

**Both remaining `identity-profile-model-v2`/`user-profile-v2` rough
edges are now closed (backend, 2026-07-30).** (1) `ProfileEditRequestDto`
gains additive `requesterName`/`requesterEmail` fields (populated from
`request.getRequester()`'s `UserProfile.fullName`/`User.email`, nullable
when the requester never set a full name), populated in both
`ProfileEditRequestController.toDto`/`UserProfileController.toDto` — the
edit-request inbox no longer shows requesters as `"User #{id}"` only.
(2) New `GET /api/tenants/permissions/any-tenant?permission=X` →
`{"granted": boolean}`, always scoped to the session's own caller,
evaluating `PermissionService.hasPermissionInAnyTenant` across *every*
tenant membership (`TenantMembershipRepository.findByUserAndActiveTrue`,
not the active-tenant Hibernate filter) — `STAFF_ADMIN` short-circuits
`true` with zero membership lookups. Required adding this new path to
`TenantContextFilter`'s exempt-path list (it must work regardless of
whether the caller has selected an active tenant, since it deliberately
checks across all memberships). Endpoint deliberately sits under
`/api/tenants/**` for routing convenience only — this is a `GET`/
no-state-change endpoint and does **not** rely on that prefix's legacy
CSRF exemption; do not treat it as precedent for a future state-changing
endpoint skipping CSRF under this prefix. Both gaps tracked as follow-ups
in `identity-profile-model-v2/PLAN.md`/`TASKS.md` (items 32-38) —
targeted tests green (`TenantSessionIntegrationTest`,
`ProfileEditRequestControllerIntegrationTest`); combined full-suite
`./mvnw verify` (covering this work together with the concurrent
`staff-rbac-split` work) is green: 448 tests, 0 failures, `spotless:check`
clean.

**Frontend consumption of both is now also done (2026-07-30), closing
`user-profile-v2`'s last two documented rough edges.**
`ProfileEditRequest` (`core/profile.service.ts`) gains `requesterName:
string | null`/`requesterEmail: string`, mapped straight through from the
now-extended `ProfileEditRequestDto`; `ProfileEditRequestsInboxPageComponent`
renders `requesterName` when present, falls back to `requesterEmail`,
then finally to the existing `"User #{id}"` string only when both are
null (new `requesterDisplayName()` helper, new
`profileEditRequests.requesterNamed` i18n key in `en`/`pt-BR`). New
`PermissionsService.fetchInAnyTenant(permission)`/`.hasInAnyTenant(permission)`
(same private-signal-+-`fetch()` shape the rest of the service already
uses, `_anyTenantGrants` keyed by `Permission`) backed by `GET
/api/tenants/permissions/any-tenant?permission=X`, 401/error caught and
treated as "not granted" (same posture as the existing `fetch()`).
`nav-menu.component.ts`'s `canSeeProfileEditRequests` now calls
`hasInAnyTenant('PROFILE_EDIT')` instead of the previous active-tenant-only
`permissionsService.has('PROFILE_EDIT')`, removing the Tier-2-accepted-gap
comment that used to document this limitation; fetched once at
session-start alongside `permissions.fetch()`/`globalPermissionsService
.fetch()` so the link doesn't flash in/out. Both `identity-profile-model-v2`
and `user-profile-v2` are now fully closed on both sides, no outstanding
rough edges. 353/353 frontend tests green, `format:check`/`build` clean.

**`member-admin-tenant-bypass` is now fully done (backend, 2026-07-29,
appsec-approved-with-notes at PLAN stage).** Closes the long-standing
asymmetry where `MembershipRole.MEMBER_ADMIN` had no equivalent of
`STAFF_ADMIN`'s unconditional `PermissionAspect` bypass: a `MEMBER_ADMIN`
with an active membership in `TenantContext`'s active tenant now
bypasses `@RequiresPermission` checks, scoped strictly to that tenant
via the existing `requireActiveMembership()` lookup (no new DB round
trip, no client-supplied tenant id — REQ-1/2/3/6). A new
`TenantService.requireNotSelfTarget` guard (REQ-4) blocks any caller —
not just `MEMBER_ADMIN` — from targeting their own account via
`addMember`/`grantPermission`/`revokePermission`/`assignAccessGroup`/
`unassignAccessGroup`; denial is recorded as a `DENIED` audit event for
free via the existing `AuditLogAspect`/`@AuditLog` wiring (REQ-5, zero
new audit code). PLAN-time correction of the SPEC's factual premise:
`requireAdminOfTenantOrStaff` already had a `MEMBER_ADMIN` bypass branch
(REQ-1b) before this feature — only `PermissionAspect`'s bypass and the
self-escalation guard were actually missing. Full-suite `./mvnw verify`
green (416/416, spotless-check clean). See
`knowly-api/specify/features/member-admin-tenant-bypass/`.

**Follow-up closed (2026-07-30):** `TenantService.removeMember` now also
calls `requireNotSelfTarget` (same guard, same placement pattern as the
other five methods — after the target membership is resolved, before the
mutation), so no caller, including a `MEMBER_ADMIN` acting via the
bypass, can remove their own membership. Denial recorded as a `DENIED`
`tenant.member.remove` audit event via the existing `@AuditLog`/
`AuditLogAspect` wiring, same as the other five. The previously-noted
gap ("deliberately not covered", appsec follow-up) is now resolved.

**Frontend follow-up (2026-08-01): done.** `nav-menu.component.ts` gated
every tenant-scoped nav item (Dashboard/Articles/Conversations/Members)
purely on `PermissionsService.has(...)`, so a `MEMBER_ADMIN` with no
explicit `AccessGroup`/direct permission grants saw almost no nav
options even though the backend bypass already let them hit every
underlying endpoint. Fixed by mirroring the existing
`canSeeProfileEditRequests()` pattern: each of those items now also
shows when `ActiveTenantService.activeTenantRole() === 'MEMBER_ADMIN'`
(OR'd with the existing `.has(...)` check), tenant-scoped only — no
change to global/staff-scope gating, no backend change. New test in
`nav-menu.component.spec.ts` asserts a `MEMBER_ADMIN` with zero explicit
grants sees Dashboard/Articles/Conversations/Members. 476/476 frontend
tests green, `format:check`/`build`/`lint` clean.

**Backend consistency fix (2026-08-01): done.** The frontend workaround
above was a stopgap — `GET /api/tenants/permissions`
(`TenantController.ownPermissions` →
`TenantService.ownEffectivePermissions`) was still only returning a
`MEMBER_ADMIN`'s explicit grants, inconsistent with `PermissionAspect`
already bypassing the check for them. Should mirror
`StaffController.ownPermissions`'s `STAFF_ADMIN` special-case exactly:
`ownEffectivePermissions` returning `List.of(Permission.values())`
when the caller's active-tenant membership role is `MEMBER_ADMIN`, same
shape as the `staffAdmin` branch immediately above it. That exact
source change turned out to already be shipped as a side effect of the
concurrent `deletion-confirmation-token` feature's commit `de2742d`
(its own `TenantService.java` edits happened to include the identical
`MEMBER_ADMIN` branch), so this item's remaining contribution is the
regression test
`ownPermissionsReturnsTheFullPermissionSetForMemberAdminWithNoExplicitGrants`
in `TenantSessionIntegrationTest.java` confirming the behavior. **Follow-up:** `nav-menu.component.ts`'s
OR-check (commit `18e505d`) is now redundant now that the backend is the
single source of truth again — slated for removal in a separate
follow-up, not done here (out of scope for a backend-only task).

**Frontend stopgap removed (2026-08-01): done.** Now that
`TenantService.ownEffectivePermissions` returns the full `Permission` set
for a `MEMBER_ADMIN` (see above), `nav-menu.component.ts`'s
`viewerIsActiveTenantMemberAdmin` computed (added in `18e505d`) is gone,
along with every OR-check it fed into (Dashboard, Articles,
Conversations, Members) — each now depends purely on
`permissionsService.has(x)` again, exactly mirroring how the equivalent
staff-scoped items rely purely on `globalPermissionsService.has(x)` with
no role-check OR'd in. `canSeeProfileEditRequests()`'s
`membership.role === 'MEMBER_ADMIN'` check is untouched (REQ-19,
"anywhere" visibility across every membership, not just the active
tenant — a different rationale, not a stopgap). The
`nav-menu.component.spec.ts` test from `18e505d` (MEMBER_ADMIN with
`tenantPermissions: []` still visible) is replaced by one asserting a
MEMBER_ADMIN whose `tenantPermissions` fixture is the full
`ALL_PERMISSIONS` set (matching what the backend now actually returns)
sees the nav items — testing the real contract, not the old workaround's
mechanism. 494/494 frontend tests green, `format:check`/`build`/`lint`
clean. This closes the loop opened above.

**Also queued, independent of the item-5 priority order below:**
`primeng-migration` (2026-07-25) was fully replaced one day later by
`primeng-removal` (2026-07-26) — the owner reverted the PrimeNG
adoption entirely, back to pure Tailwind + hand-rolled Angular
components, with `@lucide/angular` for icons. See `DECISIONS.md`'s two
consecutive dated entries and `knowly-app/specify/features/
primeng-removal/PLAN.md`/`TASKS.md` for the full record (component-by-
component replacement patterns, the Chart.js-direct approach for the
dashboard charts, and two implementation-detail deviations from the
plan — `@lucide/angular` instead of the deprecated `lucide-angular`,
and a `CHART_CTOR` injection token for deterministic Chart.js mocking
across bundled specs). `primeng-migration`'s row below is superseded;
its old follow-ups (Tailwind `cssLayer` integration, `tour-overlay` not
migrated) are moot now that PrimeNG is gone. 221/221 frontend
tests, `format:check`, and `build` all green after the removal.
**Follow-up (2026-07-30)** — `tour-overlay.component.ts` (the only
screen left on the old look) now carries the "Ink & Signal" treatment:
canonical card shadow (`shadow-lg shadow-ink-900/5 dark:shadow-none`),
`font-display` on the step title, and the same primary-CTA hover
lift/press feedback (`hover:-translate-y-0.5 hover:shadow-md
active:translate-y-0 active:scale-[0.98]`) used on the welcome page's
CTA button — pure visual/class change, no logic/testid/i18n touched.

The user confirmed this order for the next several features (2026-07-25):

1. ~~Bootstrap staff-admin one-shot user~~ — done, see
   `knowly-api/specify/features/staff-bootstrap-user/`.
2. ~~RBAC split~~ — done, see
   `knowly-api/specify/features/staff-rbac-split/`. `GlobalRole` is now
   `STAFF_ADMIN` (unrestricted, was the only value before) / `STAFF`
   (permission-gated, mirrors `tenancy`'s
   `Permission`/`AccessGroup`/`DirectPermissionGrant` model at the global
   scope via `GlobalPermission`/`GlobalAccessGroup`/
   `GlobalAccessGroupPermission`/`DirectGlobalPermissionGrant`/
   `UserGlobalAccessGroup`, new `/api/staff/**` endpoints). The
   `staff-bootstrap-user` migration's row is mapped to `STAFF_ADMIN` by
   `V14`'s data migration, per that decision. The two shared gating
   helpers in `TenantService` (`requireStaff`/
   `requireAdminOfTenantOrStaff`) are now integration-tested against all
   11 call sites individually (`createTenant`/`listAllTenants` plus
   `addMember`, `removeMember`, `listMembers`, `createAccessGroup`,
   `listAccessGroups`, `grantPermission`, `revokePermission`,
   `assignAccessGroup`, `unassignAccessGroup`, `getMemberDetail`) in
   `StaffRbacIntegrationTest` — the previously known small test-coverage
   gap (`staff-rbac-split/TASKS.md` task 6) is closed.
3. ~~Login/provisioning flow completion~~ — done, see
   `knowly-api/specify/features/staff-user-provisioning/`. New
   `GlobalPermission.STAFF_USER_CREATE` (independent from
   `STAFF_PERMISSION_MANAGE`) gates `POST /api/staff/users`, which
   creates a `GlobalRole.STAFF` user (never `STAFF_ADMIN`) and emails
   them a one-time password via the existing
   `OneTimePasswordService`/`MailService` mechanism. Tenant member
   provisioning (`addMember`) needed no change — it already worked via
   the passwordless login-code flow. Promoting/demoting `STAFF_ADMIN`
   and deactivating a staff user are explicitly out of scope (see that
   SPEC) — flag if either becomes needed later.
4. ~~Navigation menus + welcome screen~~ — done, frontend-only, see
   `knowly-app/specify/features/navigation-menu/` and
   `knowly-app/specify/features/welcome-screen/`. Initially consumed the
   existing `GET /api/tenants/permissions` and `staff-rbac-split`'s `GET
   /api/staff/permissions` as-is; that surfaced a real gap (couldn't tell
   a zero-grant `STAFF` account apart from a plain `MEMBER` from an empty
   `permissions` list alone), closed 2026-08-01 by `staff-rbac-split`
   SPEC REQ-9: `GET /api/staff/permissions` now also returns
   `isStaffAccount` (`OwnGlobalPermissionsDto`, sourced from
   `TenantContext.isStaff()`), `true` for both `STAFF` and `STAFF_ADMIN`
   regardless of grants, `false` for a plain tenant member. Fixed
   several real bugs uncovered along the way: `staff.guard.ts` inferred
   "is staff" from `GET /api/tenants` succeeding, which broke once
   `staff-rbac-split` made staff access individually granted (a `STAFF`
   user granted only `TENANT_CREATE`, not `TENANT_ACT_AS_ANY`, was
   wrongly blocked from tenant creation); login and the root route (`''`)
   both used to send an already-authenticated session to the wrong place
   (tenant list, or unconditionally `/login`); staff sessions (0
   memberships) never got nav links needing `PermissionsService`/
   `ActiveTenantService`, since both assumed an *active membership*
   existed.
5. ~~User management screens~~ — **done** (staff user management
   globally; tenant user management per-tenant). Backend gap closed by
   `staff-user-listing` (`GET /api/staff/users`); frontend built as
   `user-management-screens` — see
   `knowly-app/specify/features/user-management-screens/`. One
   `UserManagementPageComponent` switches between the untouched
   tenant-scoped `MembersPageComponent` and a new
   `StaffDirectoryPageComponent` based on `ActiveTenantService`'s active-
   tenant signal, per the "one screen, two contexts" rule below. AppSec
   reviewed with no blocking findings: the `STAFF` ceiling (a `STAFF`
   user, however permissioned, can never manage `STAFF`/`STAFF_ADMIN`
   targets) is enforced server-side by `role-model-refinement`'s
   `enforceStaffCeiling`; the frontend's `viewerIsStaffAdmin` flag only
   hides/disables dead-end actions and cannot itself grant capability.
   253/253 frontend tests green, `format:check`/`build` clean, committed.
   **Rules confirmed by the user 2026-07-26**, applied in this
   implementation:
   - **One screen, two contexts, never both menus at once.** The
     user-management screen exists in both contexts (inside a tenant:
     manage that tenant's members only; outside/staff context: manage
     staff users globally) — but which one a given staff user sees
     depends purely on whether they're currently "inside" a tenant or
     not, mirroring the general nav rule below. Inside a tenant, a staff
     user managing users can only ever act on that tenant's members —
     never staff/global users — precisely to preserve the
     staff-escalation protections already documented in item 8/9 above
     (a tenant-scoped context must never expose staff/global user
     management, or a tenant could indirectly manipulate staff there).
   - **General nav rule this generalizes from**: once a staff user is
     "inside" a specific tenant, every staff-only/global-scope nav
     option (tenant create/list/edit/delete, staff user management,
     global metrics dashboard, etc.) disappears from the menu entirely
     — the UI at that point should look and behave exactly like a
     regular tenant member's UI (plus whatever extra the staff user's
     tenant-level role/permissions grant them within that tenant, same
     as any other member). Those options only reappear once the staff
     user leaves the tenant back to the tenant list. This is a strict
     either/or, not a "staff sees extra options inside a tenant too."
6. ~~Expanded metrics dashboard~~ — **done, both sides**, see
   `knowly-api/specify/features/dashboard-analytics/`,
   `knowly-app/specify/features/dashboard-analytics/`, and
   `knowly-app/specify/features/staff-global-dashboard/`, and their rows
   in the feature tables above/below. The tenant-scoped in-app dashboard
   (period filter, five metric tiles with sparklines, message-split
   donut, conversations bar chart, members breakdown, searchable
   top-articles table, CSV export) is fully implemented, tested
   (253/253 frontend tests green), and committed. The **staff
   global-view half is now also done** (`staff-global-dashboard`,
   frontend, 2026-07-28): `/dashboard` (`DashboardWrapperPageComponent`)
   now branches on `ActiveTenantService.activeTenantResolved()` the same
   "one screen, two contexts" way `/members` already does —
   `DashboardPageComponent` unchanged when a tenant is active, a new
   `GlobalDashboardPageComponent` when staff has no active tenant, one
   page-level fetch to `GET /api/staff/metrics/global` rendering 4
   `metric-tile.component.ts` tiles (extended with an additive,
   backward-compatible pre-fetched-`[value]`/`[disabled]` mode — see
   `DECISIONS.md`) plus a 5th visibly-disabled "support tickets — coming
   soon" tile, `app-no-access-state` on a page-level 403. `/welcome`
   gains one additive quick-link card to `/dashboard` gated on new
   `GlobalPermission.DASHBOARD_VIEW_GLOBAL` (or `STAFF_ADMIN`-shaped),
   without adding any metrics content to `/welcome` itself.
   `StaffUserDetailPanelComponent` gains a new, independent
   `auditTrail`/`auditTrailError` section (own `ngOnChanges`-driven
   `loadAuditTrail()`, same per-section-error pattern as its existing
   permissions/access-groups sections) consuming
   `GET /api/staff/users/{userId}/audit-trail`, gated by new
   `GlobalPermission.AUDIT_TRAIL_VIEW`; a 403 there only affects that
   section. Nav's `nav.dashboard` entry now also shows for
   `DASHBOARD_VIEW_GLOBAL`, mirroring `nav.members`'s existing dual-gate
   shape. 271/271 frontend tests green, `format:check`/`build` clean,
   committed. **Deferred, per that SPEC's "Out of scope"**: profile
   view/edit UI (belongs to item 13's not-yet-built frontend half),
   audit-trail viewing from the tenant-scoped `MembersPageComponent`
   (staff-directory-only for now), support-ticket real data (still a
   placeholder tile — backend doesn't return it), and any
   pagination/filtering of the audit trail beyond the backend's existing
   500-row cap.
   - **Inside a tenant**: done — see above.
   - **Outside any tenant (staff global view)**: done — see above.
   - **Tenant CRUD stays staff-only and only visible outside a
     tenant** — see item 5's "general nav rule" above; staff can
     create/list/edit/delete tenants only from the staff-side (outside
     any tenant) screens, and that capability disappears from the menu
     entirely once the staff user is inside a tenant, even for
     `STAFF_ADMIN`.

**Backlog (user-reported 2026-07-25, not yet SPEC'd) — each needs its own
SPEC before implementation, roughly in this order:**

7. **Auth event audit logging gap.** `AuthController` (login-request,
   login-code/verify, login-password/verify, logout) has zero `@AuditLog`
   coverage today — every other mutating action in the system is
   audit-logged (`TenantService`/`StaffService`/article/conversation/
   onboarding), but authentication events leave no trace of who did what
   (e.g. no record of successful/failed login attempts, lockouts, or
   logout). User wants this closed so everything is traceable. Needs a
   backend SPEC — decide what to log per auth event (success/failure/
   lockout, masked email, IP?) consistent with `PiiMasker` conventions
   already used in `AuthController`'s regular logs.
   **Status (2026-07-26): done.** SPEC/PLAN/TASKS at
   `knowly-api/specify/features/auth-audit-logging/`. Every auth event
   (login-request, code/password verify success/failure, lockout,
   logout) now audited via the existing `@AuditLog`/`AuditLogAspect`
   mechanism (manual `AuditEventRepository`/new `AuditEventWriter` write
   for `logout`/lockout, since `@AuditLog` can't observe post-`proceed()`
   session teardown). An AppSec review flagged the source-IP capture as
   raw/unmasked and scoped system-wide instead of to auth events; the
   product owner delegated the fix choice to the agents, who decided to
   mask (`/24`/`/48` truncation, new `PiiMasker.maskIp`) and scope to
   auth-only (new `AuditLog.captureSourceIp()` opt-in flag, default
   `false`, so every non-auth `@AuditLog` consumer is unaffected). See
   `DECISIONS.md`'s 2026-07-26 entries for the full reasoning. QA and
   AppSec re-verified targeted tests green; full-suite `mvn verify` is
   deliberately deferred until all in-flight backlog work this session
   is done (see item 8 and this section's own note below), then run
   once for the whole batch.
8. **Role model rename + a real 4th tier (`MEMBER_ADMIN`).**
   **Status (2026-07-26): done.** SPEC/PLAN/TASKS at
   `knowly-api/specify/features/role-model-refinement/`.
   `MembershipRole.ADMIN` renamed to `MEMBER_ADMIN` everywhere
   (entity, all call sites, `V15` data migration for existing
   `tenant_memberships`/its Envers audit table). `StaffService`'s
   `createStaffUser`/`getStaffUserDetail`/`grantPermission`/
   `revokePermission`/`assignAccessGroup`/`unassignAccessGroup` now
   route through a new `enforceStaffCeiling` check: a `STAFF` user,
   however permissioned, can never manage a `STAFF`/`STAFF_ADMIN`
   target — `STAFF_ADMIN` is unaffected. A real bug was caught and fixed
   during combined testing: `getStaffUserDetail`'s new `@AuditLog` tried
   to `INSERT` inside that method's `readOnly = true` transaction and
   Postgres rejected it — fixed with a new `AuditEventWriter`
   (`REQUIRES_NEW` propagation) that `AuditLogAspect` now uses for every
   audit write, so no future `@AuditLog` usage on a read-only method can
   hit the same failure. **Frontend follow-up (2026-07-30): done.**
   `knowly-app/` now consumes `"MEMBER_ADMIN"` (not `"ADMIN"`) for the
   tenant-membership role everywhere it's typed/compared —
   `active-tenant.service.ts`, `member.service.ts`,
   `nav-menu.component.ts`, `members-page.component.ts`, and every spec
   fixture using that literal; `npm run format:check`, full `npm test`
   (344/344), and `npm run build` all green. Confirmed
   2026-07-26 with the user: the model becomes exactly `STAFF_ADMIN` /
   `STAFF` / `MEMBER_ADMIN` / `MEMBER` (renaming today's per-tenant
   `MembershipRole.ADMIN` → `MEMBER_ADMIN`, and `MEMBER` stays `MEMBER`
   — no separate "USER" role exists or is needed, "USER" was just
   informal language for a `MEMBER` with no permissions). Needs a backend
   SPEC covering the enum rename (migration + all call sites) plus:
   - **`STAFF` power ceiling**: a `STAFF` user, no matter how many
     `GlobalPermission`s they hold, can NEVER manage `STAFF`/`STAFF_ADMIN`
     users (create, edit, grant/revoke their global permissions, etc.) —
     that stays exclusively `STAFF_ADMIN`-only, specifically so a
     fully-permissioned `STAFF` can't self-escalate, mint another
     `STAFF_ADMIN` under an email they control, or elevate a friend.
     Every other action is fine for `STAFF` to reach, provided some
     `STAFF_ADMIN` explicitly granted the relevant `GlobalPermission` —
     this is the one hardcoded exception carved out of "STAFF fully
     permissioned == STAFF_ADMIN".
   - **`GET /api/tenants/memberships` stays as-is** — no backend
     single-vs-multi-membership restriction. Confirmed 2026-07-26: hiding
     the tenant list when the caller belongs to only one tenant is
     purely a frontend UX decision; calling the endpoint directly
     (outside the app) always returns the caller's own tenants
     regardless of count. Do not add a backend gate for this.
9. ~~Staff-joins-tenant requires in-app acceptance~~ / 10. ~~Tenant
   membership invitation requires acceptance~~ — **both done**, covered
   together by one feature: see `tenant-membership-acceptance`'s row in
   the backend feature table above and
   `knowly-api/specify/features/tenant-membership-acceptance/`
   (SPEC/PLAN/TASKS) for the full rules and implementation record.
11. ~~Tenant list pagination + search-by-name on `/select-tenant` and the
   backend's `GET /api/tenants`~~ — **backend half done**, see
   `tenant-pagination-search`'s row in the backend feature table above
   and `knowly-api/specify/features/tenant-pagination-search/`
   (SPEC/PLAN/TASKS). `GET /api/tenants` now returns a
   `PageResponseDto<TenantSummaryDto>` envelope with `page`/`size`/
   `search` query params instead of an unbounded array (breaking
   response-shape change, accepted per SPEC). **Frontend half also now
   done** (2026-07-28) — see `tenant-pagination-search`'s row in the
   frontend feature table above: `/select-tenant`'s 0-membership staff
   fallback consumes the paginated envelope with a debounced search
   input and prev/next pagination. Both sides fully closed.
12. ~~Boxed/segmented one-time-code input on the login screen~~ — **done**,
   see `boxed-otp-input`'s row in the frontend feature table above and
   `knowly-app/specify/features/boxed-otp-input/` (SPEC/PLAN/TASKS).
13. **Full identity/profile model — big, LGPD-sensitive, needs its own
   SPEC(s) before any code.** **Tier 3 data-protection decision confirmed
   by the user 2026-07-26 — unblocked**: CPF/RG shall be encrypted at
   rest (e.g. a JPA `AttributeConverter` doing the cipher/decipher, key
   managed outside the codebase — a secrets manager or env-injected key,
   never hardcoded/committed), decrypted only in memory when shown to
   someone holding the relevant permission. Retention: **indefinite**
   while the `User` record exists — no automatic expiry/anonymization
   job; deletion is manual/on-demand only (e.g. an LGPD data-subject
   erasure request), not a scheduled process. This SPEC can now proceed.
   Both the `Tenant` (company: CNPJ + other
   legally-unique company fields, tbd) and every `User` (person: email,
   full address, RG, CPF, phone, each enforced unique across all users
   — DB-level uniqueness, not just app validation) need complete
   records. Today's profiles are effectively anonymous — no field
   actually identifies the real-world person behind an account; this
   item is what introduces that (name, address, documents). **Profile
   editing rule, corrected 2026-07-26 (previous write-up here had this
   backwards)**: `MEMBER_ADMIN`/`STAFF_ADMIN` CAN edit their own profile
   — it's part of their general admin power, no exception for self.
   Everyone else needs a specific granted "edit profiles" permission to
   edit personal-data fields (name/address/documents/etc.) at all, and
   for **that non-admin permission holder specifically**, it excludes
   their own record — they can edit everyone else's profile but not
   their own, closing the obvious self-escalation hole (grant yourself
   edit rights, then edit your own record). So the "can't edit own
   profile" restriction applies only to the permission-holder path, not
   to admins. **Self-requested edit, corrected 2026-07-26**: a plain
   user with no edit-profiles permission still isn't fully locked out of
   changing their own data — they can *submit* a change request for
   their own profile (name/document/email/etc.), but it doesn't apply
   immediately; it goes into a pending state and requires explicit
   approval, via an in-app notification, from someone holding the
   edit-profiles permission (or an admin) before it takes effect.
   Purpose: stop a bad actor from unilaterally rewriting their own
   identity fields to pin blame on someone else or dodge accountability
   for something already tied to their profile, while still letting
   people fix a genuine typo/change of address without needing to beg
   someone to type it in for them. This reuses the same in-app
   notification/request-approval infrastructure already needed for
   items 9/10 (tenant-membership acceptance) — worth designing that
   infra once, shared across all three approval flows, rather than
   building it three times. **This "edit profiles" permission is a
   distinct capability
   from user/role management in item 5** — editing someone's personal
   data (name/address/documents) is separate from managing their
   role/tenant-membership/permissions; holding one does not imply the
   other, and — per item 8's `STAFF` ceiling and the general
   nobody-grants-themselves-permissions rule — nobody (staff or member,
   admin or not) can change their *own* role/permission grants regardless
   of what permissions they hold; that's exclusively a `STAFF_ADMIN`/
   `MEMBER_ADMIN`-on-others action. A user only sees their own profile
   and their own chat display nickname; must decide retention/at-rest-
   encryption/
   access-control for CPF/RG *before* writing any migration — this is
   Tier 3 (new kind of sensitive-data exposure) per `DECISIONS.md`, stop
   and confirm the data-protection approach with the user first. Backend:
   new entity/entities, migration(s), permission gating, DB-level
   uniqueness constraints. Frontend: profile view/edit screens.
14. ~~Internal team chat~~ — **done, both sides (2026-07-31)**. See
    `knowly-api/specify/features/internal-team-chat/` and
    `knowly-app/specify/features/internal-team-chat/`
    (SPEC/PLAN/TASKS). Backend: 97/97 tasks, new `chat` package
    (`ChatConversation`/`ChatParticipant`/`ChatMessage`/`SupportTicket`,
    `V20` migration + Envers audit tables except message content),
    `ChatEligibilityService` (capacity-based, per-tenant, re-derived
    server-side), `TenantFilterAspect` extended with
    `@BypassTenantFilterForOversight` for the `STAFF_ADMIN`/
    `MEMBER_ADMIN` look-in (never creates a `chat_participants` row,
    never grants send rights, distinct audited action
    `chat.group.oversight_view`), `SUPPORT_CHANNEL_VIEW`/
    `STAFF_SUPPORT_HANDLE` permissions, id-only cursor pagination. 499
    backend tests green, `spotless:check` clean, `./mvnw verify` BUILD
    SUCCESS. Frontend: 119/119 tasks, `chat.service.ts`/
    `support.service.ts` (signals), shared `message-thread.component.ts`
    (paginated + 5s visibility-gated polling), `/chat` and `/support`
    routes (no `tenantSelectionGuard`, in-component permission dispatch
    per the "one screen, N contexts" pattern), `viewerRelation:
    PARTICIPANT | LOOKING_IN` framing for the oversight look-in. 405
    frontend tests green, `format:check`/build clean. **Real bugs found
    and fixed during the mandatory review passes** (not just theoretical
    review notes): (1) AppSec's pre-merge pass found `GET
    /api/chat/eligible-participants?scope=direct` was leaking every
    registered user's raw email unfiltered by eligibility — fixed to
    reuse `ChatEligibilityService`'s own shared-anchor rule and prefer
    `fullName` over email (`c71b73f`); (2) QA's independent pass found a
    malformed pagination cursor threw an uncaught 500 instead of a 400 —
    fixed in `ChatExceptionHandler`; (3) implementation-time bugs: missing
    `@EntityListeners(AuditingEntityListener.class)` on
    `ChatParticipant`/`ChatMessage`, and `ChatConversationRepository`'s
    inherited `findById` silently bypassing the tenant `@Filter` on
    primary-key lookups (fixed via a `findByIdRespectingFilter` JPQL
    method used everywhere tenant-scoping matters). **Known gap,
    reviewed and accepted, not blocking**: closed-ticket immutability
    (REQ-16) is application-level only (`SupportTicketService`), no DB
    constraint — flagged by AppSec at PLAN stage as a defensible
    tradeoff, not required for merge. **Judgment call flagged for future
    attention**: the SPEC/PLAN reference a "profile nickname" field that
    doesn't actually exist anywhere in the codebase (`UserProfile` has no
    `nickname` column) — both sides fall back to `UserProfile.fullName`,
    then email, when resolving display names. If/when a real nickname
    field is added (this item's own original scope note expected one),
    revisit every `nicknameOf`-style resolver in the `chat` package.
    **Also deferred, per PLAN/DECISIONS.md's 2026-07-31 entries, not a
    gap**: real-time delivery is client polling only (no
    WebSocket/SSE/push) — the future direction (SSE-per-user over
    RabbitMQ) is documented in `DECISIONS.md` for whenever an actual
    latency complaint justifies building it. Also deferred: full-text
    search across message history (explicitly out of scope per SPEC).
    Original scope note, preserved below for the rules it captured
    (all implemented per the above): 1:1 conversations and group
    conversations between team members (distinct from the existing
    chat-with-the-knowledge-base feature) — uses the profile nickname
    from item 13 to identify people in the UI. Needs its own SPEC(s) in
    both subprojects once prioritized. **Rules confirmed by the user
    2026-07-26** (ahead of the SPEC, for whenever this is picked up):
    - 1:1 and group chat between team members is open to all four roles
      (`STAFF_ADMIN`/`STAFF`/`MEMBER_ADMIN`/`MEMBER`) with **no
      permission required just to talk to another person** — messaging
      itself isn't permission-gated, unlike chatting with the
      documentation/article knowledge base, which stays
      permission-gated for `MEMBER`s (existing `conversations` feature
      behavior, unchanged).
    - **Confirmed 2026-07-26, expanded scope**: the peer-to-peer model
      covers 1:1 staff↔staff, 1:1 member↔member, staff-only groups
      (multiple staff together), and member-only groups (multiple
      tenant members together) — all distinct from the single fixed
      member↔staff support channel described below. So there are three
      shapes in total: (a) open peer-to-peer 1:1/group chat among
      staff-with-staff or member-with-member, (b) member↔staff support
      channel (one fixed thread per member, see below), and (c) the
      pre-existing member↔knowledge-base article chat (already shipped,
      unrelated to this item). **Confirmed 2026-07-31, corrected same
      day**: staff and members cannot be mixed in the same peer-to-peer
      group *except* via the same exception already established for 1:1
      (item 14's staff↔member 1:1 rule above) — a staff user who also
      holds an active membership in that tenant can be in a member-only
      group as a peer member of that tenant, same as they can DM that
      tenant's members 1:1. What's still never allowed is a staff person
      joining a member-only group of a tenant they're *not* a member of
      (acting purely in their staff capacity), and a staff-only group
      never admits a plain tenant member. So the rule is: group
      membership eligibility mirrors the 1:1 eligibility rule exactly —
      "staff-only" vs. "member-only" is about the *capacity* each
      participant is acting in (staff-without-that-tenant's-membership,
      vs. member/staff-with-that-tenant's-membership), not a hard
      role-field check. This resolves the SPEC's "open question (blocking
      full approval)" — the `internal-team-chat` SPECs' current
      no-mixing-at-all assumption is **too strict** and needs amending to
      reflect this exception before approval.
    - **Confirmed 2026-07-31, groups are private + admin override**:
      peer-to-peer groups (staff-only or member-only) are private —
      participants only, nobody else can see or enter one by default.
      The one exception is admin oversight: `STAFF_ADMIN` can see and
      enter *any* group, staff-only or member-only, across every tenant
      (mirrors `STAFF_ADMIN`'s existing unconditional-bypass posture
      elsewhere in the system — see `PermissionAspect`'s bypass,
      item 5/8/9 above). `MEMBER_ADMIN` can see and enter every
      member-only group belonging to any tenant where that same person
      holds the `MEMBER_ADMIN` role — i.e. their admin-driven visibility
      is scoped per-tenant to tenants they administer, not global like
      `STAFF_ADMIN`'s. This does not grant either admin visibility into
      1:1 conversations (only groups) unless already a participant —
      **confirmed 2026-07-31**: 1:1 conversations stay fully private
      between their two participants regardless of admin role; the
      admin-override visibility above is group-only. **Confirmed
      2026-07-31, framing of the override matters**: when `STAFF_ADMIN`
      (or an in-scope `MEMBER_ADMIN`) opens a group via this oversight
      override, they must be presented/labeled as an external
      guest/support-style presence, not as a regular member of that
      group — the UI should read as "someone from support/admin is
      looking in," not as "a new member joined." Concretely: the admin
      does **not** become a member of the group through this override
      (no join event, no addition to the group's member list) unless
      they are separately, actually a member of that tenant (the
      pre-existing 1:1/group-eligibility exception above) — oversight
      access must never itself grant membership.
    - Staff↔member support is **not** a normal 1:1/group conversation —
      it's a single, fixed, per-member support channel: each tenant
      member has exactly one ongoing support thread, and whichever staff
      member is handling support replies through that same thread (not
      one thread per staff person). Needs its own entity/relationship
      design distinct from the peer-to-peer 1:1/group model above.
    - **Confirmed 2026-07-31, peer-to-peer 1:1 is private (WhatsApp-style)**:
      staff↔staff and member↔member 1:1 conversations are private between
      the two participants, same mental model as a normal DM app. A
      staff↔member 1:1 is **not allowed** unless that staff person also
      holds an active membership in the same tenant as that member — i.e.
      a staff user acting purely in their staff capacity (no membership in
      the member's tenant) cannot open a private 1:1 with a tenant member
      outside the fixed support channel; only a staff user who is *also* a
      member of that same tenant can DM that member as a peer.
    - **Confirmed 2026-07-31, support channel data model**: the support
      channel is **one single persistent channel per member**, not one
      channel per ticket. Opening a "new ticket" after a previous one
      closed does **not** create a new channel — it creates a new ticket
      *within* the same existing per-member channel, which keeps the full
      history of every ticket (open or closed) that member has ever had.
      Closing a ticket is still terminal exactly as already confirmed
      below (closed tickets never reopen, a new support need starts a new
      ticket) — this only changes the underlying entity model: ticket ≠
      channel, ticket is a bounded episode inside the member's one
      long-lived channel.
    - **Confirmed 2026-07-31, purpose of the shared per-member history**:
      whichever staff member picks up a *new* ticket for that member must
      be able to see that member's prior tickets/history in the same
      channel — explicitly so a recurring/already-known issue is visible
      to whoever picks up the new ticket, not just to whoever handled it
      originally. This is the reason the channel (not just the ticket) is
      the unit of history. **Confirmed 2026-07-31**: this history must
      load progressively (paginated/lazy-loaded, e.g. older messages
      fetched on scroll-up or "load more"), not all at once — a channel
      accumulating many tickets over a long relationship should never
      force the backend/frontend to load the entire history in a single
      request/render.
    - **Confirmed 2026-07-26, visibility rules for the support
      channel**: it's opened by one specific `MEMBER`/`MEMBER_ADMIN` and
      is effectively "that member vs. the staff team" until a staff
      member picks it up. **Tenant side — corrected 2026-07-26**: not
      just the opener + `MEMBER_ADMIN`s — *any* tenant member who holds
      whatever permission gates access to the support/history area can
      see the channel's message history, not only its opener. The exact
      permission gating that "history area" itself (new permission?
      reuse an existing one?) is TBD for the SPEC. **Staff side**: every
      staff user holding the relevant
      support permission can see the channel while it's unclaimed, but
      once a staff member accepts/picks it up, the thread effectively
      becomes a 1:1 between that specific staff member and the member
      who opened it — i.e. "accepting" assigns the conversation to one
      staff person, it isn't simultaneously multi-staff after that point
      (needs the SPEC to define whether other staff retain read access
      after assignment, or lose it, and how reassignment/handoff works).
    - **Confirmed 2026-07-26, view vs. participate are different**:
      everyone covered above (tenant side: any member with the
      support/history permission; staff side: any staff with the
      support permission, pre-acceptance) can only *view* the history —
      the only two people who can actually *send messages* in that
      thread are the member who opened it and the one staff member who
      accepted it. So visibility (who can read) is broader than
      participation (who can write), and participation narrows to
      exactly those two people once a staff member accepts.
    - **Confirmed 2026-07-26, closing is terminal**: a support ticket/
      thread can be closed; once closed, it permanently stops accepting
      new messages and cannot be reopened (a closed ticket stays
      closed forever — no reopen action). If the same member needs
      support again after closing, that requires starting a brand-new
      ticket/thread, not resuming the closed one. History remains
      viewable per the visibility rules above even after closing.
15. ~~Design system overhaul~~ — done (2026-07-25), no SPEC written (pure
    visual/frontend, user explicitly chose full-app scope over
    incremental). Defined knowly's first visual brand identity, "Ink &
    Signal" — no logo exists yet, so it's typographic-only: a deep navy
    `ink-*` palette (Tailwind v4 `@theme` tokens in
    `knowly-app/src/styles.css`) replaces `slate-*`/`indigo-*`/`blue-*`
    everywhere; a warm amber `signal-*` scale is the one accent, reserved
    for primary actions/focus/highlights — never a base surface color;
    Fraunces (serif, `font-display`) for the wordmark (`knowly.`, dot in
    signal) and large headings, Inter for everything else; motion tokens
    `duration-fast/base/slow` + `ease-fluid` and a reusable `.enter-fluid`
    entrance animation, no new npm dependency. Applied across every
    screen (app shell/nav, login, welcome, dashboard widgets, articles,
    conversations, members, select-tenant, tenant-create, shared UI) —
    visual/class changes only, no logic/testid/i18n changes, all 186
    frontend tests + build still green. **If a real logo/mark gets
    designed later, it should adopt this same ink/signal palette rather
    than starting the brand over.**
    **Follow-up correction (2026-07-25)** — the full-app palette/font swap
    above was accepted, but the initial pass read as flat/stiff ("tá tudo
    duro, sem transição"); this pass fixes *feel*, not tokens: (a) app
    shell/nav restyled from a horizontal top bar into a real left sidebar
    (`app-shell.component.ts`, `layout/nav-menu.component.ts`) — permanently
    dark `ink-950` chrome (Linear/Vercel-style, independent of the app's own
    light/dark toggle), grouped nav links with inline SVG icons (no new icon
    dependency), active items get a filled signal-tinted pill with an inset
    signal border rather than a plain background swap, tenant-switch/create
    links visually separated below a divider; (b) primary CTA buttons across
    login/welcome/articles/conversations/members/select-tenant/tenant-create
    gained `hover:-translate-y-0.5` lift + `active:scale-[0.98]` press
    feedback on top of the existing color transition; (c) first brand mark:
    `app-brand-wordmark` (`knowly-app/src/app/shared/brand-wordmark.component.ts`)
    — inline SVG wordmark-only logotype (Fraunces `<text>`, signal-colored
    dot), now the single implementation behind both the nav sidebar and the
    login page (previously duplicated markup); a companion "K" symbol mark
    (`knowly-app/public/favicon.svg`, ink-950 tile / white K / signal
    accent-cut) now backs the favicon and `apple-touch-icon` link in
    `index.html` (replacing the stock Angular favicon) — the stock
    `favicon.ico` binary itself was left in place as a fallback link since
    this environment has no way to rasterize a real `.ico`; all evergreen
    browsers will use the new SVG icon. **Tier 2 judgment calls made without
    asking:** sidebar is always dark regardless of the app's light/dark
    toggle (matches the requested reference dashboards, which all use a
    fixed-dark chrome); the shared utility buttons in the sidebar footer
    (`help-menu`/`language-switcher`/`theme-toggle`/`logout-button`) had
    their light-mode classes replaced with dark-chrome-only classes since
    they're now only ever rendered on that permanently-dark surface — if
    either of those components is reused somewhere with a light background
    in the future, its classes will need reintroducing dark/light variants.
    **Not run this pass:** `npm run format` / `format:check` / `test` /
    `build` — no shell/bash tool was available in this agent invocation;
    a human or `frontend-engineer` must run knowly-app's verification
    commands before trusting this is green.
    **Second follow-up (2026-07-25)**, after the sidebar/motion pass above
    was actually seen running (not just reviewed as a diff): borders were
    too low-contrast to read as real dividers, the welcome screen was a
    small floating card in an otherwise empty canvas, and the
    theme/language/help/logout controls sat oddly at the bottom of the
    sidebar. Fixed: those four controls now live in a horizontal top
    header bar (`app-shell.component.ts`), separate from primary
    navigation, which stays permanently dark chrome like the sidebar;
    sidebar links are grouped under category labels (Overview/Knowledge/
    Team/Workspace, new `nav.category.*` i18n keys) for faster scanning;
    a single `.page-shell` spacing convention (`styles.css`) was
    introduced and adopted by dashboard/articles/conversations/members so
    page gutters and vertical rhythm aren't reinvented per screen; the
    welcome screen gained permission-gated quick-link cards (articles/
    conversations/members) instead of just a lone greeting card. Verified
    this time: `npm run format`, `format:check`, `test` (186 passing),
    `build` all green; committed.

16. **Chat message full-text search — deferred future improvement, not yet
    scheduled (documented 2026-08-08, not started).** Currently mid-flight:
    two SPECs are being written to overhaul the chat UI/permissions —
    `knowly-app/specify/features/chat-unified-ui/SPEC.md` (single nav
    surface for 1:1/groups/Support/RAG-article-chat, client-side name
    search, clickable people list instead of a "Nova conversa" button,
    "Criar grupo" flow) and `knowly-api/specify/features/chat-group-membership-management/SPEC.md`
    (per-group admin role separate from `STAFF_ADMIN`/`MEMBER_ADMIN`,
    add/remove/leave, `PRIVATE`/`REQUEST_TO_JOIN`/`PUBLIC` group
    visibility with join-request approval, auto-succession when a group's
    last admin leaves, permanent group deletion with 4 authorization
    paths). **Explicitly out of scope for both of those SPECs**: a
    Slack-level search over message *content* — "de quem / para quem-onde
    (grupo) / quando" filters plus free-text recall of a remembered
    fragment when the user doesn't recall who or which group they said it
    in. User's own words when deferring it: "como é uma coisa mais
    complexa, deixe como melhoria futura, mas documente muito bem para não
    ficar perdido." Full technical analysis already done by
    `data-architect-dba` so a future SPEC doesn't have to redo the
    investigation:
    - `chat_messages.content` today is plain `TEXT`, zero search indexing
      beyond the `(conversation_id, id DESC)` cursor index — a substring
      search today would be an unindexed `ILIKE` sequential scan.
    - **Recommended approach: native Postgres full-text search, not
      pgvector.** pgvector/embeddings solve *semantic* recall ("about
      topic X" even if reworded); the stated need is *lexical* recall
      ("I remember roughly what I typed") — `tsvector`/`tsquery` + GIN is
      the right tool, far cheaper (no embedding pipeline, no per-message
      API calls, no drift risk) at this corpus size. Do **not** reach for
      Elasticsearch either — a second search datastore means a
      sync/dual-write pipeline and re-implementing `chat_participants`
      tenant/ACL scoping inside it, pure overkill for an internal-chat
      corpus (thousands–low tens of thousands of rows per tenant, not
      Slack-corporate scale).
    - Sketch migration (next free number, confirm against `ls` at
      implementation time — `V31` as of this writing, but
      `chat-group-membership-management` will likely land its own
      migration(s) first): `ALTER TABLE chat_messages ADD COLUMN
      content_tsv tsvector GENERATED ALWAYS AS (to_tsvector('portuguese',
      content)) STORED;` + `CREATE INDEX ix_chat_messages_content_tsv ON
      chat_messages USING GIN (content_tsv);`. Stored/generated column is
      safe because messages are immutable (no edit/delete of content is
      in scope anywhere in chat today). Language config (`'portuguese'`
      vs `'simple'`) needs an explicit call at implementation time — stemming
      helps recall but can hurt on mixed PT/EN/jargon content.
    - Sketch endpoint: `GET /api/chat/messages/search` with `q` (parsed
      via `websearch_to_tsquery()`, not raw `to_tsquery()`, so free-text
      input like quotes/`-exclude` just works), optional `senderId`,
      optional `conversationId` (covers both `PEER_GROUP` and
      `PEER_DIRECT` — no separate `groupId` param needed),
      optional `dateFrom`/`dateTo`, required cursor pagination. Default
      sort **chronological, not `ts_rank` relevance** for v1 (matches the
      "I don't remember who/where, only roughly what" use case better,
      and avoids an unindexed per-row rank computation); relevance
      ordering is a valid future increment, not a v1 requirement.
    - **Critical constraint carried over from both current SPECs, do not
      relax without an explicit product decision**: search must join
      through `chat_participants` filtered to the caller, or the
      Support permission model, before any filter is applied — never
      trust a client-supplied `conversationId` without re-verifying the
      caller's access first. The `STAFF_ADMIN`/`MEMBER_ADMIN`
      "oversight look-in" bypass (`chat.group.oversight_view`,
      `REQ-5a`/`REQ-5b`, and the archived-group staff-visibility grants
      in `chat-group-membership-management`) was scoped as *one-group,
      explicitly-opened inspection* — silently reusing it for a search
      endpoint would let staff keyword-search every group platform-wide
      in one request, which is a materially different capability and
      needs its own explicit product yes/no, not an inferred
      generalization. Whether Support-channel content is in scope for
      this search at all also needs an explicit call — the user's stated
      use case sounds peer/group-chat specific, not support-ticket
      specific. Once `chat-group-membership-management` ships its
      soft-delete (REQ-49) and archive semantics, search must also
      respect those — a deleted/archived group's messages must not
      surface for anyone who's lost access.
    - Main implementation risk to test explicitly: an integration test
      asserting search never returns a message from a conversation the
      caller isn't currently a participant of (this is the standard
      cross-tenant-leak failure mode for this kind of feature).
    Needs its own SPEC (EARS requirements, Tier 3 decisions on the
    look-in/Support-scope questions above, language config) before any
    PLAN/implementation — do not start from this bullet alone.

17. **Frictionless login for trusted devices — deferred future
    improvement, not yet scheduled (documented 2026-08-08, not started).**
    User's original idea: skip email+code entry entirely on a device
    already trusted, using signals like a paired Bluetooth device or a
    known Wi-Fi SSID as the shortcut condition, opt-in the first time the
    user logs in the traditional way. Full discussion happened in chat,
    not yet a SPEC — captured here so the reasoning isn't lost:
    - **Bluetooth-paired / known-Wi-Fi-SSID as the *sole* trust signal is
      explicitly rejected as unsafe** — both are spoofable (MAC/SSID) and
      "same network" doesn't prove "same user, same trusted device."
      Anyone else on the same Wi-Fi (e.g. a public/office network) must
      not inherit the session. If network signals are used at all, they
      can only ever be a secondary UX nicety layered on top of a real
      credential, never the credential itself.
    - **Recommended direction instead: WebAuthn/FIDO2 passkeys**, not a
      custom "trusted device" refresh-token scheme. The user's own
      restatement of the idea ("um token que já tenha usuário e senha que
      fica no Bitwarden, o sistema vê que tenho o Bitwarden instalado e
      loga só com a chave") *is* passkeys — the user's Bitwarden vault
      already acts as a WebAuthn authenticator today, no custom protocol
      needed. Passkeys beat the device-trust-token idea on every axis
      that matters here: private key never leaves the authenticator
      (nothing to steal from a cookie/localStorage), phishing-resistant
      (bound to the exact origin), portable across devices via the
      synced vault (not pinned to one machine like a device-trust token
      would be), and it's a W3C standard with mature library support
      (`webauthn4j` or Yubico's `java-webauthn-server` on the backend,
      native `navigator.credentials` in the browser) instead of
      hand-rolled session-security code to audit.
    - If a lower-friction *secondary* signal is still wanted on top of
      passkeys later (e.g. auto-focus/pre-fill based on network context),
      that's a separate, much smaller UX decision to make only after
      passkeys exist — not a substitute for them, and not in scope for
      the initial SPEC.
    - **Current state confirmed by investigation (2026-08-08): zero
      groundwork exists.** No WebAuthn/FIDO2 dependency in
      `knowly-api/pom.xml`, no passkey/credential controllers/entities,
      no `navigator.credentials` usage anywhere in `knowly-app`. Today's
      auth is entirely the passwordless email login-code (OTP) flow
      (`AuthController`, `LoginCodeService`, `OneTimePasswordService`,
      `FailedAttemptService`, `CaptchaService`), backed by
      `HttpSession`/`HttpSessionSecurityContextRepository` — not
      stateless JWT. This flow's shape (credential-verification step →
      session creation → audit log entry, all through one choke point)
      is a reasonable pattern to extend: a passkey assertion could plug
      in as an alternative credential-verification step feeding the same
      session-creation/audit code path. Everything WebAuthn-specific
      (challenge generation/storage, credential public-key storage per
      user/device, relying-party config for the domain, browser API
      integration on the frontend) would be new, not a retrofit of
      existing code.
    Needs its own SPEC on both sides (`knowly-api` for the
    challenge/credential endpoints and storage, `knowly-app` for the
    browser WebAuthn integration and the "register this device" opt-in
    UI reachable after a normal login) before any PLAN/implementation —
    do not start from this bullet alone.

18. ~~Chat unified UI + group membership management~~ — **done, both sides
    (2026-08-09)**. Amends item 14 (`internal-team-chat`) per user request
    to restructure chat UX: search, a clickable people list instead of a
    "Nova conversa" button, group creation with visibility, and merging
    chat + conversations (RAG) into one navigation surface. See
    `knowly-api/specify/features/chat-group-membership-management/` and
    `knowly-app/specify/features/chat-unified-ui/` (SPEC/PLAN/TASKS).
    AppSec-reviewed twice (initial PLAN review + a follow-up spot-check
    after the frontend contract-reconciliation pass); one blocking finding
    (join-request approval wasn't re-deriving `ChatEligibilityService` at
    approval time, only at submission — REQ-30a) was fixed before TASKS.md.
    Backend: 138/138 tasks, `./mvnw verify` BUILD SUCCESS. New on
    `ChatConversation`: `visibility` (`PRIVATE`/`REQUEST_TO_JOIN`/`PUBLIC`,
    admin-changeable post-creation), `archived_at` (distinct from
    `deleted_at` — archival is staff-visible per REQ-44/45, deletion is
    not); `is_admin` added to `chat_participants` (not a separate table —
    keeps "admin implies participant" structural); new `chat_join_requests`
    table; `Permission.CHAT_GROUP_DELETE`; `deleted_at`/`SoftDeleteFilter`
    (from the earlier `soft-delete-default-filter` feature) extended to
    `ChatConversation`/`ChatParticipant`/`ChatMessage` for REQ-49's
    permanent-deletion semantics. Group admin: creator auto-admin, can
    promote others, auto-succession (earliest `joined_at`, user-id
    tie-break) if a group loses its only admin. Group deletion has 4
    independent authorization paths: `STAFF_ADMIN` always, `MEMBER_ADMIN`
    always for tenant groups only (never staff groups — scoped by
    `conversation.getTenant() != null`), `CHAT_GROUP_DELETE` permission
    holders (tenant groups only), or the group's own admin. "Tenant group"
    vs. "staff group" was assumed to be a new concept mid-spec but turned
    out to already exist via `ChatConversation.tenant` nullability from
    `internal-team-chat` — no new schema concept needed, just new rules
    layered on it. **Known deviation from PLAN's most literal reading**:
    archived-vs-deleted returns a distinct 409 only inside `deleteConversation`
    itself (where REQ-53 makes it a hard tested criterion); every other
    group-mutating endpoint just gets a plain 404 on an already-deleted
    group via `SoftDeleteFilter`'s default exclusion, which satisfies
    REQ-49's "no longer reachable via any normal path" but isn't identical
    to the PLAN's per-endpoint 409 language — worth a look if that
    distinction ever matters in practice. Frontend: 133/133 tasks, 834/834
    tests green (up from 405), build+lint clean. New: `ChatShellComponent`/
    `ChatSidebarComponent` (single `/chat` route + `section` query param,
    replacing separate `/chat`/`/support`/`/conversations` routes — old
    paths now `redirectTo`, not removed, so bookmarks/links keep working),
    `ChatDirectoryComponent` (combined People+Groups list with client-side
    name search — RAG section never filtered, PRIVATE groups never
    client-filtered either since the backend already excludes them from
    every discovery response), `CreateGroupDialogComponent`,
    `GroupVisibilityBadgeComponent`, `GroupAdminPanelComponent`,
    `ChatDirectoryService`/`ChatGroupService` (new, signals-based, all
    governance actions non-optimistic). `isAdmin`/`isParticipant` are
    `computed()` from `adminUserIds`/`participantUserIds` already present
    in `ChatConversationDetailDto` — client-side only for UX (show/hide),
    every mutating action still gets independently re-validated
    server-side (AppSec-confirmed, no client-trust path). Retired:
    `ChatPageComponent`, `new-conversation-dialog.component.ts`,
    `conversation-list.component.ts`, `conversation-list-item.component.ts`
    (unused once `ChatDirectoryComponent`'s combined row model replaced it).
    **Emergent deviations from PLAN** (documented in the PLAN's own
    "Emergent decisions" section): `create-group-dialog.component.ts` uses
    a signal + `(input)` binding instead of `[(ngModel)]`/`FormsModule` —
    plain `ngModel` binding didn't propagate into a `computed()` dependency
    under this app's zoneless setup, worth remembering as a general trap in
    this codebase; `GroupAdminPanelComponent`'s remove/delete confirmations
    use a lightweight inline two-click confirm instead of the existing
    `ConfirmDialogComponent`, since that component requires a
    backend-issued typed confirmation word with no equivalent endpoint in
    this feature's contract; `chat-sidebar.component.ts` has no search
    field by design — search state stays in `ChatDirectoryComponent` per
    the PLAN, avoiding prop-drilling. **Deferred, not built**: full-text
    search over message *content* (see item 16) and remove-participant vs.
    self-leave both exist, but there is no message-search bar anywhere in
    this UI.

    **Post-ship Playwright pass (2026-08-09), layout redesign + real bugs
    found and fixed.** Playwright end-to-end testing (the pass flagged as
    pending above) surfaced that the shipped tab-strip layout was
    genuinely unintuitive in practice, plus several real defects the
    unit-test suite had missed:
    - **Layout redesign, 2 columns not 4-tab-strip.** Tab strip replaced
      with a persistent messaging-app-style layout modeled on WhatsApp
      Web/Telegram/Slack (product owner's explicit direction: "veja o
      layout de apps de mensagem... imite para um modelo que funcione
      para o knowly"): column 1 has 3 quick-action buttons (open a
      support ticket, talk to the knowledge base, create group) above a
      single always-visible list combining people (partitioned into
      "Already talked to"/"Haven't talked yet", each independently
      searchable), groups, the Support row, and every RAG conversation;
      column 2 is the open conversation. A 3-column intermediate design
      (separate contacts column) was tried and rejected by the product
      owner for duplicating the people list, folded back into column 1's
      partition instead. `ChatSidebarComponent` demoted from a
      4-tab-switcher to just the quick-actions header;
      `ChatContactsPanelComponent` was built then removed, its logic
      moved into `chat-directory-rows.service.ts`. The chat header is now
      clickable (`chat-header.component.ts`) and opens a modal
      (`person-info-modal.component.ts`/`group-info-modal.component.ts`)
      instead of showing admin controls permanently inline —
      `GroupAdminPanelComponent` (visibility/promote/remove/delete),
      member list, invite, and leave-group all moved inside that modal.
      Avatars added (`shared/avatar.component.ts`, Lucide
      `LucideUser`/`LucideUsersRound`/`LucideLibrary` icons — a detour
      through user-supplied custom SVG files was tried, looked wrong even
      after resizing, and was reverted back to Lucide per explicit
      product-owner instruction; don't reach for custom SVG icons in this
      app, Lucide is the established convention for a reason).
    - **Real security bug found and fixed: cross-tenant PII enumeration.**
      `GET /api/chat/eligible-participants?scope=group&tenantId=<any>`
      never checked that the *actor* had any relationship to the
      requested `tenantId` — any authenticated user could enumerate
      another tenant's members (name/avatar/id) by supplying its id, a
      genuine multi-tenant isolation break. Fixed in `fa68743`
      (`ChatEligibilityService`, same anchor-validation pattern as the
      other fixes below), confirmed by a second appsec pass with a
      "vulnerability sweep" of the whole `chat` package for the same
      class of bug (none else found).
    - **Real bug: staff/tenant identity mixing, several rounds.** The
      original PLAN's assumption "a staff actor's session-active tenant
      should just add to their staff-only anchor" doesn't match what the
      product actually wants: a staff member working inside a tenant
      should see and be limited to that tenant's people/groups, not a mix
      of tenant members and other staff. Fixed across five commits
      (`7565ee0`, `80f3353`, `fa68743`, `17ac10f`, `7383212`,
      `bccb5c1`/`781188b`) covering: direct-scope candidate listing,
      group creation's tenant anchor (was reading the client-supplied
      `tenantId` instead of the session's), `listConversations`'s
      already-talked-to list, blocking staff-to-staff DIRECT creation
      while a tenant is active (not just hiding it from the list —
      explicit product decision: "sim, bloquear também"), and letting a
      staff creator's own group-membership eligibility check pass without
      a real `TenantMembership` row. Each landed commit got its own
      dedicated AppSec review before being considered closed — this
      area needed six review rounds, not one, because each fix surfaced
      the next adjacent gap.
    - **The `bccb5c1` fix initially didn't work despite passing all
      tests — worth remembering.** `User` has no `@EqualsAndHashCode`,
      so `participant.equals(actor)` (comparing two separately-loaded JPA
      instances of the same row) is always `false` — silent reference
      equality, not id equality. The unit test for that fix passed anyway
      because it stubbed the repository to return the exact same object
      reference as `actor`, masking the bug. Only Playwright testing
      against the live app caught it (`400 CHAT_INELIGIBLE_PARTICIPANT`
      on group creation persisted after the "fix" shipped). Corrected in
      `781188b` by comparing `.getId()` instead; see
      `feedback_entity_equals_by_reference_trap` in the AI assistant's
      memory for the general lesson — **trust a live/E2E check over a
      green unit-test suite specifically for entity-identity bugs.**
    - **Functional bug: group visibility silently ignored on creation.**
      `CreateChatConversationRequestDto` never had a `visibility` field —
      the frontend sent it, Jackson silently dropped the unknown field,
      every group was created `PRIVATE` regardless of what the creation
      dialog said. Fixed in `fe5444e`. A related frontend-only bug (the
      "change visibility" `<select>` inside the group-info modal always
      showed `PRIVATE` selected regardless of the group's real
      visibility, due to Angular's `<select>` `[value]` binding racing
      the `@for`-rendered `<option>` elements it depends on) was fixed in
      `b563317` by moving to per-`<option>` `[selected]` instead.
    - **Net effect**: none of this was caught by the pre-ship
      unit/integration test suites (both sides were reported green
      before this pass) — it took literally driving the app as the
      target user (staff working inside a tenant, per this feature's own
      motivating request) to find. Worth internalizing for future
      features with a similar "same code path, different actor context"
      shape: STAFF/STAFF_ADMIN acting inside a tenant session is a
      recurring source of gaps because it's a state combination that's
      easy to under-specify and easy for a mocked unit test to paper
      over.

Backend and frontend work can proceed in parallel per feature once each
one has an approved SPEC/PLAN that defines the API contract.

**`internal-team-chat` (item 14) backend is now fully implemented
(2026-07-31)** — all 97 `knowly-api/specify/features/internal-team-chat/
TASKS.md` items done, committed. New `br.com.conectabyte.knowly.chat`
package: `ChatConversation`/`ChatParticipant`/`ChatMessage`/`SupportTicket`
entities (migration `V20__create_chat_tables.sql`, `_aud` counterparts for
everything but `chat_messages`, matching `messages`' existing
not-Envers-audited precedent); `ChatEligibilityService` (REQ-3/4/5's
shared per-tenant capacity rule, unit-tested including the
same-staff-user-eligible-for-T-ineligible-for-U acceptance criterion);
`@BypassTenantFilterForOversight`, a narrow addition to
`TenantFilterAspect`'s existing `@Around` advice (not a second/manual
`Session.disableFilter` call, per the AppSec-corrected PLAN) backing
REQ-5a/5b's `STAFF_ADMIN`/active-`MEMBER_ADMIN` group look-in, which
never writes a `chat_participants` row and never grants send rights
(verified by dedicated tests); `ChatConversationService`/
`SupportTicketService` covering create/list/read/send and the full
ticket lifecycle (open/claim/transfer/close, REQ-9–18); id-only cursor
pagination (`ChatCursor`, default 30/max 100 clamp) shared by peer
conversations and support channels; new `SUPPORT_CHANNEL_VIEW`
(`Permission`)/`STAFF_SUPPORT_HANDLE` (`GlobalPermission`) wired through
the existing `@RequiresPermission`/`@RequiresGlobalPermission` aspects;
`ChatController` (`/api/chat/**`) and `SupportChannelController`
(`/api/tenants/{tenantId}/support/**`, correctly **not** CSRF-exempt,
tests obtain real CSRF tokens per the already-narrowed `SecurityConfig`).
`./mvnw spotless:check`/`verify` both green; full unit + Testcontainers
integration coverage (eligibility, admin oversight scoping incl.
MEMBER_ADMIN-wrong-tenant/staff-only-group rejection, ticket lifecycle
edge cases, tenant isolation, pagination, audit trail for
`chat.group.oversight_view`/`support.ticket.*`). One judgment call made
without stopping to ask: the SPEC/PLAN refer to a "profile nickname"
that doesn't exist as a field anywhere in the codebase (`UserProfile`
has no `nickname` column) — nickname resolution falls back to
`UserProfile.fullName`, then the user's email, rather than inventing a
new column; revisit if a real nickname concept lands later. Real-time
delivery is deferred to polling per the already-recorded `DECISIONS.md`
entry — not built here.
(per `feedback_appsec_gate_skipped` — this touches new attack surface:
group/1:1 access control, admin oversight override, support-channel
permission gating).

**`chat-group-membership-management` backend is now implemented
(2026-08-09)** — amends `internal-team-chat` per its own SPEC/PLAN in
`knowly-api/specify/features/chat-group-membership-management/`. New
migration `V31__chat_group_membership_management.sql`: `is_admin`
(boolean) on `chat_participants`; `visibility`
(`PRIVATE`/`REQUEST_TO_JOIN`/`PUBLIC`, default `PRIVATE`) and
`archived_at` on `chat_conversations`; **`SoftDeleteFilter` extended to
the three chat entities it didn't cover yet** (`ChatConversation`,
`ChatParticipant`, `ChatMessage` — each now carries the `@FilterDef`/
`@Filter` pairing alongside `ChatConversation`'s existing `TenantFilter`,
mirroring `Conversation`/`AccessGroup`'s dual-filter shape); new
`chat_join_requests`/`chat_join_requests_aud` tables (own lifecycle,
partial-unique index for "one PENDING request per (conversation,
requester)"). New `Permission.CHAT_GROUP_DELETE` (no `viewDependency()`).
`ChatConversationService` gained: `promoteToAdmin`/`addParticipants`/
`removeParticipant`/`leaveConversation` (with `handleAdminDepartureIfNeeded`
auto-succession, REQ-54, tie-broken by `joinedAt` then `user.id`, run
inside the same transaction as the triggering removal)/`archiveIfEmptied`
(leave-only, `PRIVATE`/`REQUEST_TO_JOIN` archives on emptying,
`PUBLIC` never does)/`changeVisibility`/`listDiscoverableGroups`
(`PageResponseDto<ChatDiscoverableGroupDto>`, eligibility+already-joined
filtered in-memory after the DB page per PLAN)/`submitJoinRequest`/
`listJoinRequests`/`approveJoinRequest` (**re-derives
`ChatEligibilityService.isEligible` a second time, at approval, not just
at submission — REQ-30a, an AppSec-mandated fix to a real TOCTOU gap;
leaves the request `PENDING` rather than auto-rejecting on failure**)/
`rejectJoinRequest`/`joinPublicGroup`/`deleteConversation` (REQ-48's four
authorization paths tried in order — `STAFF_ADMIN` unconditional, active
`MEMBER_ADMIN` of the group's tenant, a `CHAT_GROUP_DELETE` holder in
that tenant, or a current group admin of that specific group — the only
method in this feature carrying `@AllowDeletedForOversight` +
`@BypassTenantFilterForOversight`, needed so it can distinguish REQ-51
not-found from REQ-53 already-deleted before re-deriving authorization
from scratch). `requireReadableConversation` gained a new archived-group
branch (REQ-44/45/46: any `STAFF` role for an archived **tenant** group,
`STAFF_ADMIN`-only for an archived **staff** group — a deliberately new,
broader-than-REQ-5a/5b grant, checked *before* the existing active-group
`STAFF_ADMIN`/`MEMBER_ADMIN` look-in so `MEMBER_ADMIN` does **not**
inherit residual access to archived history). `ChatConversationDetailDto`
extended additively (`visibility`, `archivedAt`, `adminUserIds`). All new
endpoints live on the existing `ChatController` (`/api/chat/**`) per the
PLAN's contract table; five new exception types map to
`ChatExceptionHandler` (`ChatGroupStateConflictException` carries a
`Detail` enum — `NOT_PEER_GROUP`/`ARCHIVED`/`ALREADY_DELETED`/
`WRONG_VISIBILITY_MODE`/`WOULD_EMPTY_GROUP` — covering four-plus distinct
409 conditions with one exception class, per PLAN's own precedent).
`./mvnw spotless:apply`/`verify` green; service-level unit coverage
(`ChatGroupMembershipServiceTest`) includes the full admin-role/succession
matrix (including the tie-break-determinism and REQ-30a
eligible-then-revoked scenarios), the AppSec-required 403 two-case
negative matrix (admin-of-a-different-group vs. non-admin-participant-
of-this-group) for `addParticipants`/`removeParticipant`, and all four
deletion-path positives with their negative-scope proofs; one controller
integration test (`ChatGroupMembershipControllerIntegrationTest`)
exercises the full lifecycle end-to-end through real CSRF/session.
**One pragmatic scope reduction, not silently made**: REQ-12/17/26/37/41's
"archived/deleted → 409, not 404" distinction is only implemented with
the `@AllowDeletedForOversight` load path for `deleteConversation`
itself (where REQ-53 makes it a hard acceptance criterion); every other
group-mutating method relies on `SoftDeleteFilter`'s default-on
exclusion, so a request against an already-*deleted* (not just archived)
group surfaces as a plain 404 there instead of a distinct 409 — consistent
with REQ-49's "no longer reachable through any normal path" wording, but
worth flagging if a future SPEC amendment wants that distinction
surfaced everywhere. Full formal per-task TDAD commit granularity (one
commit per one of TASKS.md's 138 items) was not practical at this scope;
work landed as a small number of logically-scoped commits instead
(migration/schema, entities+filters, service+controller+DTOs+exceptions,
tests+docs), each internally following Red→Green.

**`internal-team-chat` (item 14) frontend is now fully implemented
(2026-07-31)** — all 119 `knowly-app/specify/features/internal-team-chat/
TASKS.md` items done, committed. `chat.service.ts`/`support.service.ts`
(signals, mirroring `PermissionsService`/`ActiveTenantService`'s shape),
shared `message-thread.component.ts`/`message-composer.component.ts`
(progressive load-more/retry, REQ-19/20/21), peer chat (`/chat`,
`/chat/:conversationId`, no guard — conversation list/detail, "looking
in" oversight banner/badge for `STAFF_ADMIN`/`MEMBER_ADMIN` look-ins,
participant picker + new-conversation dialog covering all three
eligibility modes), and support channel (`/support`,
`/support/:channelId`, no guard — `SupportPageComponent`'s three-way
permission dispatch: `STAFF_SUPPORT_HANDLE` → staff inbox + claimed
channel, `SUPPORT_CHANNEL_VIEW` → member-browse alongside own channel,
else → own channel only). i18n keys added for both locales (parity
verified). One backend-contract gap worked around at the frontend layer:
`SupportTicketDto` carries neither `tenantId` nor `memberUserId`, so
`SupportPageComponent` resolves a claimed ticket's `supportChannelId`
via the existing `ChatService.openConversation()` peer-chat endpoint
instead (valid because the member is a support channel's only formal
`ChatParticipant`) — see PLAN.md's "Emergent decisions" for this and two
smaller routing/component judgment calls made without stopping to ask.
Verified: `npm run format:check`, `npm test` (405 passing, 74 files),
`npm run build` all green.
19. **Tenant-supplied AI key for RAG answers — not yet SPEC'd.** RAG chat
    already answers without any AI configured (today's baseline is
    presumed to be a full-text/keyword search over the knowledge base,
    not LLM-generated answers — needs confirming against the actual
    current RAG implementation before writing the SPEC, not assumed from
    this note). User wants a per-tenant settings screen where a tenant
    owner can supply their own AI provider key so RAG answers become
    smarter/LLM-backed; without a key, the tenant keeps today's
    non-AI behavior. Needs its own backend SPEC (key storage — almost
    certainly encrypted at rest, never logged/returned in plaintext after
    initial save, consistent with existing secret-handling conventions;
    which provider(s) are supported; fallback behavior on invalid/missing
    key; per-tenant scoping) and frontend SPEC (settings screen, key
    entry/rotation/removal UX). AppSec review is mandatory before TASKS.md
    given this stores a tenant-supplied external-provider credential.

## How to work in this repo

This project follows **Spec-Driven Development (SDD)** — see
[`specify/memory/constitution.md`](specify/memory/constitution.md) and
`sdd-methodology.md` (in the same folder) for the full process (SPEC →
PLAN → TASKS → Implement → Analyze). In short:

1. Never implement from a vague request. If
   `<subproject>/specify/features/<name>/SPEC.md` doesn't exist for
   what's being asked, write it first (EARS/GEARS syntax) and get it
   approved.
2. Then PLAN.md (technical decisions) and TASKS.md (atomic,
   checkbox-tracked steps).
3. Implement task by task: test first (Red), minimal code (Green),
   `./mvnw test` (backend, from `knowly-api/`) or `npm test` (frontend,
   from `knowly-app/`).
4. Before calling a task done: `./mvnw spotless:apply && ./mvnw verify`
   (backend) or `npm run format:check && npm test && npm run build`
   (frontend).
5. Analyze: re-check every SPEC.md acceptance-criterion checkbox against
   the finished implementation before calling the *feature* (not just
   the task) done.

## Feature status — backend (`knowly-api/`)

Every feature below has its own
`knowly-api/specify/features/<name>/{SPEC,PLAN,TASKS}.md` — read those
for the actual requirements and decisions. This table is only a map of
*what exists* and *how done it is*; it is not a substitute for reading
the feature's own SPEC.

| Feature | Status | Notes |
|---|---|---|
| `authentication` | ✅ Done | Login-code (passwordless) flow, sessions, logout (`POST /api/auth/logout`). **Known minor gap (2026-07-26, reviewed, no action taken):** `/login-code/verify` and `/login-password/verify` already compare against a constant dummy hash when no real credential exists (`LoginCodeService.verify`, `OneTimePasswordService.verifyAndRotate`), so the hash-compare step itself doesn't leak email existence. However, the success branch does strictly more work than the failure branch (extra DB lookup(s), possible OTP regeneration, email dispatch, session creation) with no artificial padding, so a correct vs. incorrect credential attempt is distinguishable by response latency — this doesn't leak whether an email exists (only reachable after a valid credential), just success/failure. User reviewed fix options (fixed latency floor vs. deferring success-path side-effects to after the response) and explicitly chose not to implement either for now, prioritizing frontend work instead. Revisit if this ever needs to be closed. |
| `tenancy` | ✅ Done | Multi-tenant session model, memberships, roles, permissions, access groups, audit log. Staff (global-admin) users can list every tenant and act as any of them without holding a membership (added after a live bug where a staff account with 0 memberships got stuck behind `TENANT_SELECTION_REQUIRED`). |
| `article-management` | ✅ Done | Upload (text/image/PDF, OCR via tesseract), embeddings (pgvector), permission-gated CRUD. |
| `conversations` | ✅ Done | Chat over the tenant's articles, SSE streaming, citations. |
| `dashboard-metrics` | ✅ Done | Usage widgets backed by `MessageArticleCitation`. |
| `onboarding-status` | ✅ Done | Tracks first-run completion server-side. |
| `api-documentation` | ✅ Done | OpenAPI/Swagger exposure. |
| `tags-crud` | 📄 Reference only | **Not implemented on purpose** — exists solely as the canonical example of the SPEC/PLAN/TASKS format. Don't build it unless explicitly asked to turn it into a real feature. |
| `staff-bootstrap-user` | ✅ Done | One migration-created staff `User` (email via required `KNOWLY_BOOTSTRAP_STAFF_EMAIL` env var, no password) so a fresh deployment has a first login via the existing login-code flow. No new mechanism, no freeze/expiry — see SPEC's "Out of scope" for why. |
| `staff-rbac-split` | ✅ Done | `GlobalRole` splits into `STAFF_ADMIN` (unrestricted) / `STAFF` (permission-gated via `GlobalPermission`, mirrors tenant-side `Permission`/`AccessGroup` model at global scope). New `/api/staff/**` endpoints. All 11 `requireStaff`/`requireAdminOfTenantOrStaff` call sites now have dedicated `StaffRbacIntegrationTest` coverage (previous known gap closed). **2026-08-01 addition (REQ-9)**: `GET /api/staff/permissions` response gains `isStaffAccount` (`OwnGlobalPermissionsDto`), sourced from `TenantContext.isStaff()` — `true` for any `STAFF`/`STAFF_ADMIN` account regardless of grant count, `false` for a plain tenant member; closes the gap `navigation-menu` flagged (couldn't tell a zero-grant `STAFF` account apart from a plain `MEMBER`). |
| `staff-user-provisioning` | ✅ Done | `POST /api/staff/users` lets `STAFF_ADMIN` (or a granted `STAFF`) create a new `STAFF` user, gated by its own `GlobalPermission.STAFF_USER_CREATE`; emails a one-time password via the existing mechanism. Tenant member provisioning needed no change. |
| `dashboard-analytics` | ✅ Done (backend) | Extends `metrics` with date-bucketed time-series (`/conversations/timeseries`, `/messages/timeseries`, `/articles/timeseries`, UTC calendar-day, zero-count days included), a tenant membership active/inactive snapshot (`/members`), `period` filtering (`7d`/`30d`/`90d`/`all`, default `all`) on every metrics endpoint via a new `MetricsPeriod` enum + `InvalidPeriodException`/`MetricsExceptionHandler`, and a hand-built CSV export (`/export`, no new dependency). All still `DASHBOARD_VIEW`-gated, tenant-isolated via `TenantFilter`. Frontend consuming these is a separate SPEC (`knowly-app/specify/features/dashboard-analytics/`). See `DECISIONS.md` for the UTC-bucketing rationale. |
| `staff-user-listing` | ✅ Done | `GET /api/staff/users` (optional `?email=` case-insensitive substring filter) lists every `STAFF`/`STAFF_ADMIN` user, gated by new `GlobalPermission.STAFF_USER_VIEW` (independent of `STAFF_USER_CREATE`/management ceiling checks). `StaffController.listStaffUsers`/`StaffService.listStaffUsers`/`StaffUserSummaryDto` implemented and tested (`StaffUserListingIntegrationTest`). Full-suite `./mvnw verify` green (339/339), `qa-test-automation`/`appsec` reviewed with no blocking findings. |
| `tenant-membership-acceptance` | ✅ Done | New `Notification`/`NotificationType` model (`V16` migration) plus `NotificationController`/`NotificationService`/`NotificationDto` (`/api/notifications`) for accept/decline-style tenant membership notifications, with `NotificationAlreadyResolvedException`/`NotificationNotFoundException` wired into the existing exception-handling convention. Confirmed `removeMember` needs no code change. Full-suite `./mvnw verify` green (339/339), `qa-test-automation`/`appsec` reviewed with no blocking findings (IDOR/replay/privilege-escalation checks all confirmed clean). |
| `identity-profile-model` | ⛔ Superseded by `identity-profile-model-v2` | Was: encrypted `cpf`/`rg` identity fields directly on `User` (`CpfRgEncryptionConverter`, blind-index lookup via `BlindIndexService`) plus a profile-edit-request flow. Retrofitted 2026-07-28 — see `identity-profile-model-v2` below and `DECISIONS.md`'s "`identity-profile-model` retrofit" entry. Row kept only as history; do not treat any detail below as current for the shipped shape (the encryption/blind-index mechanism itself is unchanged, just relocated). |
| `identity-profile-model-v2` | ✅ Done (backend) | Retrofits `identity-profile-model`: personal data split into new `UserProfile` (1:1, eager row per `User`, `cpf`/`rg`/`rgOrgaoEmissor`/`birthDate`/`avatarUrl`), `Address` (1:1, lazy), and `Contact` (1:n, max 5, one-primary-per-type, `ContactService`) tables/entities, replacing `User`'s old flat `fullName`/`address`/`rg`/`cpf`/`phone` columns (still present on `User`/`users` — dropped only in a later `V19` migration, deliberately deferred per PLAN.md until this code path is verified running in production, not bundled into this session). `V18__retrofit_identity_profile_tables.sql` creates the new tables + `profile_edit_request_contacts` (1:n proposed contact changes), backfills `full_name`/`cpf`/`rg`/`phone` from `users`, cancels any in-flight `PENDING` request (new `ProfileEditRequestStatus.CANCELLED`), and adds a DB-level `CHECK` blocking self-approval (defense-in-depth alongside the existing service-layer guard). REQ-11 is a genuine behavior *removal* from the shipped feature: `directEdit`'s self-exclusion is now unconditional — `STAFF_ADMIN`/`MEMBER_ADMIN` can no longer self-edit even fields they could edit on someone else; only the new dedicated `POST /api/users/me/profile/avatar` (multipart, reuses `ArticleStorageService`'s MinIO/S3 pattern via new `AvatarStorageService`/`AvatarProperties`, distinct `avatarBucket`) is self-editable, unconditionally, no approval step. `ContactType` (`PHONE`/`WHATSAPP`/`EMAIL`/`OTHER`) format validation is a plain service-layer `if` in `ContactService`, not a custom Bean Validation `@Constraint` (documented Tier 2 call in `DECISIONS.md` — first feature that could have reached for one and deliberately didn't). Full-suite `./mvnw verify` green. Companion frontend (`user-profile-v2`) is now also done — see that row in the frontend feature table above; both sides fully closed. **Follow-up (2026-07-31, TASKS.md item 27) closed**: PO confirmed the gate ("this code path verified running in production") satisfied, so `V19__drop_legacy_user_identity_columns.sql` now drops `full_name`/`address`/`rg`/`cpf`/`phone`/`rg_blind_index`/`cpf_blind_index` from `users`/`users_aud`, and `User.java` lost the corresponding fields/`@Convert`/blind-index columns. `CpfRgEncryptionConverter`/`BlindIndexService` stay (still used by `UserProfile`). |
| `global-staff-dashboard-metrics` | ✅ Done | `GET /api/staff/metrics/global` (`GlobalMetricsController`/`GlobalMetricsService`/`GlobalMetricsDto`) exposes global counts for `STAFF_ADMIN` or a `STAFF` caller holding `GlobalPermission.DASHBOARD_VIEW_GLOBAL`, including a "new tenants this month" UTC-calendar-month boundary case (tightened during QA review to assert the exact millisecond boundary, not just a ±1-day margin). Full-suite `./mvnw verify` green (339/339), `qa-test-automation`/`appsec` reviewed with no blocking findings. |
| `tenant-pagination-search` | ✅ Done (backend) | `GET /api/tenants` breaking-changed from an unbounded `List<TenantSummaryDto>` to a paginated `PageResponseDto<TenantSummaryDto>` envelope (`content`/`page`/`size`/`totalElements`/`totalPages`). New `page`/`size` query params (defaults `0`/`20`, `size` clamped to `100`, negative `page` or `size<=0` rejected with `400 INVALID_PAGINATION` via new `InvalidPaginationException`), plus an optional `search` param matching `Tenant.name`/`cnpj`/`razaoSocial` case-insensitively (OR'd) via a new DB-level `TenantRepository.search(String, Pageable)` `@Query`, sorted server-side by `name` ascending only (no client-supplied sort). Authorization unchanged (`requireStaff`/`GlobalPermission.TENANT_ACT_AS_ANY`). This is the first page/size pagination contract in this codebase — see `DECISIONS.md` for the `@Query`-over-`Specification`/fixed-sort/`PageResponseDto`-placement judgment calls, intended as the default template for future paginated endpoints. Full-suite `./mvnw verify` green (377/377, ~21 min real elapsed — the ~12 min figure previously in this file was stale). Companion frontend (`/select-tenant`'s consumption of the new envelope shape) is now also done — see that row in the frontend feature table above; both sides fully closed. |
| `staff-audit-trail-view` | ✅ Done | `GET /api/staff/users/{userId}/audit-trail` (`StaffController.auditTrail`/`StaffService.getAuditTrail`/`AuditEventDto`) returns a target user's full audit history — deliberately **cross-tenant, `TenantFilter`-bypassing by design** (`AuditEvent` isn't a `TenantAwareEntity`, so no special plumbing was needed) — capped at the 500 most recent rows via a new `AuditEventRepository.findTop500ByActorUserIdOrderByOccurredAtDesc` (DB-enforced `LIMIT`, backed by the pre-existing `ix_audit_events_actor_time` composite index, no new migration). Gated by new `GlobalPermission.AUDIT_TRAIL_VIEW`, ceiling-independent (REQ-9: viewing a `STAFF`/`STAFF_ADMIN` target's trail is unaffected by the `role-model-refinement` management ceiling). The call itself is audited (`staff.audit_trail.view`). Full-suite `./mvnw verify` green; `qa-test-automation` independently confirmed every REQ/acceptance criterion (including the cross-tenant, 500-cap, ceiling-independence, and self-audit cases) is covered by a real passing test; `appsec` re-reviewed the implementation against the SPEC's confirmed REQ-4 exposure and found no new issue — verdict "ship it," no blocking findings. |
| `member-admin-tenant-bypass` | ✅ Done | `MembershipRole.MEMBER_ADMIN` now gets an unconditional `PermissionAspect.checkPermission` bypass in their own active tenant (mirrors `STAFF_ADMIN`'s global bypass), scoped via the existing `requireActiveMembership()` lookup — no new DB round trip, no client-supplied tenant id. New `TenantService.requireNotSelfTarget` guard blocks any caller (role-agnostic) from targeting their own account via `addMember`/`grantPermission`/`revokePermission`/`assignAccessGroup`/`unassignAccessGroup`; denial is a `DENIED` audit event via the pre-existing `AuditLogAspect`, no new audit code. Full-suite `./mvnw verify` green (416/416). Follow-up (2026-07-30): `removeMember` now also covered by the same guard — see above. Frontend follow-up (2026-08-01): `nav-menu.component.ts` initially added a role-check OR-workaround, since removed now that `ownEffectivePermissions` returns the full permission set for `MEMBER_ADMIN` server-side — the component's nav-item visibility is purely `permissionsService.has(x)`-driven again, no frontend special-casing left — see above. |
| `mandatory-complete-profile` | ✅ Done | `CreateStaffUserRequestDto`/`AddMemberRequestDto` both gain a required `profile: MandatoryProfileFieldsDto` field (`fullName`/`birthDate`/`cpf`/`rg`/`rgOrgaoEmissor`/nested `MandatoryAddressDto`/`ContactDto[]`) — a staff-creation or `addMember` request missing any mandatory field is rejected by Bean Validation before `StaffService.createStaffUser`/`TenantService.addMember` is ever entered. A Bean Validation failure on exactly those two endpoints is recorded as a `DENIED` audit event (`staff.user.creation.denied`/`tenant.member.creation.denied`, `missingFields` in `metadata`) via a new `CreationValidationAuditAdvice` (`@RestControllerAdvice`), since the existing per-method `@AuditLog` mechanism can't fire before the annotated service method is entered. `addMember`'s brand-new-email path now writes the mandatory profile immediately (`TenantService#createUserWithProfile(String, MandatoryProfileFieldsDto)`); an already-existing account's profile is never silently overwritten by an inviter's submission (preserves `identity-profile-model-v2`'s self-request/approval requirement). **Frontend follow-up closed (2026-08-04)**: neither `StaffDirectoryPageComponent`'s "Create staff user" form nor `MembersPageComponent`'s "Add member" form had ever been updated to collect the now-required `profile` — both actions 400'd unconditionally (confirmed live via Playwright/`fetch` against the real backend). Both are now two-step: the existing email input, then the same `ProfileFieldsFormComponent` (`requireAllFields=true`) `complete-profile-page.component.ts` already uses, reusing its exact `fields → MandatoryProfileFields` mapping. `MemberService.add()`/`StaffUserService.create()` both gained a required `profile` parameter. While fixing this, found and fixed two more real bugs it had been masking: (1) a duplicate `taxId` reached `user_profiles`' unique index (`ux_user_profiles_tax_id_blind_index`) unchecked, aborting the transaction and surfacing as an unhandled 500 with a leaked Hibernate/SQL stack trace instead of a clean conflict — `UserProfileService#applyMandatoryProfile` now proactively checks (same "check before insert, not insert-and-catch" reasoning as `TenantService#createTenant`'s existing taxId/adminEmail checks, since this method carries its own `@Transactional`/`@AuditLog` boundary), throwing a new `TaxIdAlreadyExistsException` → 409 `TAX_ID_ALREADY_EXISTS`; (2) `MembersPageComponent#onAddMember`'s success handler was gated on `if (result !== null)`, but `POST /api/tenants/{id}/members` returns `ResponseEntity.ok().build()` — a genuinely empty body, which Angular parses as `null`, indistinguishable from `catchError`'s `of(null)` fallback — so a real success against the live backend left the form stuck disabled forever (never previously observable, since the missing-profile 400 always fired first); fixed the same way this file's own `confirmRemoval` already handles an identical empty-body DELETE, `catchError` returning `EMPTY` instead of `of(null)`. |
| `user-role-selection-at-creation` | ✅ Done | Both creation endpoints above gain an optional `role` field, layered on top of `mandatory-complete-profile`'s DTOs without reverting anything from that feature. `CreateStaffUserRequestDto.role: GlobalRole` (`STAFF`/`STAFF_ADMIN`, default `STAFF`); `AddMemberRequestDto.role` relaxed from `@NotNull` to optional (`MEMBER`/`MEMBER_ADMIN`, default `MEMBER`) — default resolution happens in the service layer (`role == null ? <default> : role`), not the DTO. `role=STAFF_ADMIN` requires the caller to themselves be `STAFF_ADMIN` (new private `StaffService.requireCallerIsStaffAdmin()`, no permission-grant substitution — a `STAFF` caller holding `STAFF_USER_CREATE` still fails); `role=MEMBER_ADMIN` requires the caller to be `STAFF_ADMIN` or that same tenant's active `MEMBER_ADMIN` (new private `TenantService.requireCallerIsAdminOfTenant()`, deliberately not reusing `requireAdminOfTenantOrStaff` since that method lets a granted `STAFF`/`MEMBER` through). No "last admin" floor/ceiling check applies to either path — that safeguard only exists for demotion/deletion. New `@AuditLog(metadataExpression = "...")` attribute (evaluated via the same SpEL machinery as `resourceIdExpression`, merged into `AuditEvent.metadata` alongside `captureSourceIp`'s existing output) records the raw requested `role` argument on both `createStaffUser`/`addMember` audit events. Full-suite `./mvnw verify` green. Both `staff-user-provisioning`'s and `tenancy`'s 2026-08-02 SPEC amendments (mandatory profile + role selection) are now fully closed on the backend — `mandatory-complete-profile` and this feature both shipped. |
| `staff-rbac-management-operations` | ✅ Done | Demote/promote/hard-delete/batch-permission-update for both scopes: `POST /api/staff/users/{userId}/demote`\|`promote`, `DELETE /api/staff/users/{userId}` (+ its own `.../deletion-confirmation-token`), `PUT /api/staff/users/{userId}/permissions/batch` (+ token endpoint) on `StaffService`/`StaffController`; the tenant-scope mirror (`.../members/{membershipId}/demote`\|`promote`\|`hard-delete`\|`permissions/batch`) on `TenantService`/`TenantController`. Hard-delete/batch reuse `DeletionConfirmationTokenService` (new resource types `staff-user`/`staff-permission-batch`/`tenant-member-hard-delete`/`tenant-permission-batch`); demote/delete reuse `requireCallerIsStaffAdmin()`/`requireCallerIsAdminOfTenant()` verbatim per PLAN.md. Last-admin floor (`STAFF_ADMIN` platform-wide, `MEMBER_ADMIN` per-tenant) is enforced with a pessimistic row lock (`UserRepository#findByGlobalRoleForUpdate`/`TenantMembershipRepository#findByTenantIdAndRoleAndActiveTrueForUpdate`, new `LastAdminRemainingException` → 409) to close the TOCTOU window — verified with an actual two-thread race in both `*RbacManagementOperationsTest` classes. `grantPermission`/`revokePermission`(tenant only)/`assignAccessGroup` on both scopes now reject an admin-tier target outright (`PermissionDeniedException`) — **behavior change**, superseded two `StaffServiceCeilingIntegrationTest` cases that previously asserted the opposite (renamed/inverted, not deleted). Batch update is full-set replacement (not add/remove diff), computes the added/removed sets server-side, requires a token only on a real change (no-op never touches Redis), and emits one `AuditEvent` per added/removed permission. `StaffUserDetailDto`/`MemberDetailDto` gain `isLastAdminOfType`/`globalRole` (advisory only, read-only `countByGlobalRoleIn`/`countByTenantIdAndRoleAndActiveTrue`, not the locked variant — real enforcement stays server-side on the mutation path). **Conservative deviation**: PLAN.md's `STAFF_USER_DELETE`/`TENANT_MEMBER_DELETE`/`TENANT_PERMISSION_GRANT_CREATE` gates reference `permission-granularity-model` constants that don't exist in the codebase yet (that feature hasn't landed) — falls back to each scope's existing `STAFF_PERMISSION_MANAGE`/`TENANT_MEMBER_MANAGE_ANY`/`TENANT_PERMISSION_GRANT_MANAGE_ANY` gates until it does. Full-suite `./mvnw verify` green (694 tests). |
| `tenant-access-group-bulk-and-delete` | ✅ Done | Backend contract `knowly-app/specify/features/tenant-access-group-management/SPEC.md` was waiting on: `POST /api/tenants/{tenantId}/members/{membershipId}/access-groups:batch` (`TenantService#batchAssignAccessGroups`, `BatchAccessGroupAssignmentRequestDto{accessGroupIds}` `@NotEmpty`/`@Size(max=50)`) assigns/reactivates N groups to one membership in one transaction, gated by `TENANT_PERMISSION_GRANT_CREATE`; rejects the whole request (400 `INVALID_ACCESS_GROUP_BATCH`) on an empty/duplicate list or any id that doesn't resolve to a live group for the tenant (checked via one bulk `AccessGroupRepository#findByTenantAndIdInAndDeletedAtIsNull` query, size-compared against the submitted set — no partial writes). `AccessGroup`/`AccessGroupPermission` both gain a cascading-soft-delete `deletedAt` (new migration `V29__access_group_soft_delete.sql`, partial unique indexes replacing their old table-level uniqueness constraints, same shape as `V28`'s `UserAccessGroup` retrofit) plus a matching `_aud` column. New `GET .../access-groups/{id}/deletion-confirmation-token` + `DELETE .../access-groups/{id}` (`TenantService#generateAccessGroupDeletionConfirmationToken`/`#deleteAccessGroup`, gated by `TENANT_ACCESS_GROUP_DELETE`, new `tenant-access-group-delete` token resource type distinct from the existing unassign-scoped `tenant-access-group`) soft-deletes the group and, in the same `@Transactional` method, bulk-`UPDATE`s every live `UserAccessGroup`/`AccessGroupPermission` row referencing it (new `@Modifying` `softDeleteByAccessGroupId` on both repositories) — one-way, no restore path. New `AccessGroupNotFoundException` (404, distinct from every other `AccessGroup`-id lookup's existence-hiding 403, since the SPEC pins 404 for this endpoint) and `InvalidAccessGroupBatchException` (400), both wired into `TenancyExceptionHandler`. REQ-17 read-path sweep: `listAccessGroups`/`grantAccessGroupPermission`/`assignAccessGroup`/`PermissionService`'s effective-permission resolution all now filter `deletedAt IS NULL` on `AccessGroup`/`AccessGroupPermission` (new `findByTenantAndDeletedAtIsNull`/`findByIdAndDeletedAtIsNull`/`findByTenantAndIdInAndDeletedAtIsNull`/`findByAccessGroupInAndDeletedAtIsNull`); the grant path's own reactivate-or-create lookup (`findByAccessGroupAndPermission`) stays intentionally unfiltered, mirroring `UserAccessGroup`'s existing write-path-sees-deleted-rows split. New tests: `AccessGroupRepositoryTest`/`AccessGroupPermissionRepositoryTest`/`UserAccessGroupRepositoryTest` (partial-index reuse, bulk-update scoping), `BatchAccessGroupAssignmentRequestDtoTest`, `TenancyExceptionHandlerTest` additions, ~15 new `TenantServiceTest` cases (batch happy/empty/duplicate/invalid-id/permission-denied, delete happy/cascade/wrong-token/not-found/already-deleted/permission-denied, REQ-17 regression on all four call sites, name/permission reuse after delete), and a new `AccessGroupBulkAndDeleteIntegrationTest` (`MockMvcTester`, session+CSRF, 204/400/403/404 across all three new endpoints). Full-suite `./mvnw verify` green, all 62 TASKS.md items complete, atomic commits per section. |
| `observability-stack` | ✅ Done (backend, local dev only) | Local-only observability, see `specify/features/observability-stack/`. Most of this already existed *undocumented* before this feature: `compose.yaml`'s `grafana-lgtm` service (`grafana/otel-lgtm:0.29.2` — Grafana + Prometheus + Loki + Tempo + an OTel Collector in one image, `127.0.0.1:3000` Grafana UI, `127.0.0.1:4317`/`4318` OTLP ingest, datasources/cross-links auto-provisioned by the image itself) and `spring-boot-starter-opentelemetry` in `pom.xml` (Spring Boot 4.1's own OTel starter) were already pushing real `knowly` metrics/traces to Prometheus/Tempo — confirmed live via Grafana's datasource proxy before any code changed. **What this feature actually added**: (1) log export to Loki, via a new `opentelemetry-logback-appender-1.0` dependency + `logback-spring.xml` + `OpenTelemetryLogbackConfig` (installs the appender against the real `OpenTelemetry` bean once the Spring context is up) — Spring Boot's OTel starter auto-wires metrics/traces but has no logging bridge; (2) a version pin (`opentelemetry-api-incubator:1.62.0-alpha`) for a real `NoClassDefFoundError` the appender's own transitive dependency introduced (caught by `KnowlyApplicationTests#contextLoads`, not compilation); (3) `CorrelationIdFilter` now uses the real OTel span's trace/span id (via `Span.current()`) when one is active instead of an unrelated random id, so Grafana's Loki→Tempo trace-id link-out actually resolves — falls back to the old random-hex behavior when no span is active. **Local dev only, not production-ready**: no auth in front of Grafana, no TLS, no retention tuning; ports stay `127.0.0.1`-bound. **Known gap, not closed by this feature**: nothing in the codebase actually puts `actorUserId`/`tenantId` into MDC (only `traceId` does, via `CorrelationIdFilter`) — `logback-spring.xml` lists them in `captureMdcAttributes` so they'll flow to Loki for free once something does populate them, but that's a real follow-up, not done. **Blocker discovered while verifying this feature, unrelated to it, now resolved (2026-08-04)**: a fresh `./mvnw spring-boot:run` appeared to fail at startup (`ArticleStorageService#ensureBucketExists` got a `403 Forbidden` from MinIO on `headBucket`) and was initially mis-bisected to `06d1fac` (`software.amazon.awssdk:bom` 2.44.9→2.49.3); actual root cause was an unresolved `${MINIO_ROOT_USER}` Spring placeholder (env var never exported into the shell running Maven) — see "Known operational/tooling notes" below for the full writeup and the `README.md` fix. Frontend telemetry/error-logging hook to this stack is a noted follow-up, not implemented (out of scope per the brief). **Follow-up (2026-08-07)**: added a third dashboard, `knowly-logs.json` (uid `knowly-logs`) — a full-height Loki logs panel + log-volume-by-level chart, replacing reliance on Grafana's generic Drilldown → Logs view (small fixed panel, no persisted filters). Log level (`detected_level`) and other OTel fields arrive in Loki as structured metadata, not labels — only `service_name` is an indexed label — so panels filter via `| detected_level=~"$level"` directly, no `logfmt`/`json` parser stage needed. See `specify/features/observability-stack/PLAN.md` for the full writeup, including confirmation that this stack has no dev/homolog/prod separation (local dev only, single `service_name`). |

| `soft-delete-default-filter` | ✅ Done (backend), partial | Hibernate `@Filter`/`SoftDeleteFilterAspect` (mirrors `TenantFilter`/`TenantFilterAspect` exactly) now excludes soft-deleted rows by default from every standard entity-load-time query, closing the `ChatEligibilityService`/`ChatConversationService` unfiltered-`findById` bug class this feature was written to fix. New `AllowDeletedForOversight` escape hatch (mirrors `BypassTenantFilterForOversight`) exists but has no consumer yet, same status `BypassTenantFilterForOversight` had before `internal-team-chat` — now used once, by `TenantService#listDeactivatedTenants` (Phase 6 fix). **Applied to 11 of the 13 SPEC-listed entities**: `User`, `Conversation`, `UserProfile`, `Contact`, `Address`, `Tenant`, `TenantMembership`, `UserAccessGroup`, `UserGlobalAccessGroup`, `DirectPermissionGrant`, `DirectGlobalPermissionGrant`. **`AccessGroup`/`AccessGroupPermission` are a known, documented gap** — neither table ever received a `deleted_at` column in this codebase snapshot (confirmed via migration grep), contradicting the SPEC's premise that all 13 already had one; adding the column is out of scope for this feature, so these two are left unfiltered pending a `data-architect-dba`-coordinated migration decision (see TASKS.md tasks 12/14). Also fixed a latent `TenantFilterAspect` bug this surfaced: `enableFilter(...).setParameter(...)` evaluated its argument (a query against the now-filtered `Tenant`) before the filter's own parameter was set, intermittently throwing "Filter parameter 'tenantFilter' has neither an argument nor a resolver". **Phase 5 (SPEC requirement 8, dropping the now-redundant `*DeletedAtIsNull` method-name suffix) is intentionally incomplete**: discovered mid-task that `ProfileEditRequestService` calls one such method from a deliberately non-`@Transactional` method (the aspect never fires there, so the explicit predicate is still load-bearing) — auditing every other call site's `@Transactional` status individually was out of this task's budget, so no existing method was renamed/removed; only new, additive no-predicate methods were added where a Phase 4 test needed one. A real key discovery for future soft-delete/tenant-filter work: **`JpaRepository`'s inherited `findById` does not honor Hibernate `@Filter`s at all** (a known Hibernate limitation, already worked around once before via `ChatConversationRepository#findByIdRespectingFilter` for `TenantFilter`) — `UserRepository`/`UserProfileRepository`/`AddressRepository#findById(ByUserId)` now use an explicit JPQL `@Query` instead. Full-suite `./mvnw verify` green.
| `role-permission-revoke` | ✅ Done | Adds the missing "take a permission back off a role" half of both scopes' grant-only access-group permission model: `DELETE /api/tenants/{tenantId}/access-groups/{accessGroupId}/permissions/{permission}` (`TenantService#revokeAccessGroupPermission`, gated by `TENANT_ACCESS_GROUP_EDIT`, same as the sibling grant endpoint) and `DELETE /api/staff/access-groups/{accessGroupId}/permissions/{permission}` (`StaffService#revokeAccessGroupPermission`, gated by `STAFF_PERMISSION_MANAGE`) both soft-delete the matching permission row rather than removing it, and are audit-logged (`tenant.access_group.revoke_permission`/`staff.access_group.revoke_permission`). New `AccessGroupPermissionNotGrantedException` (400 `ACCESS_GROUP_PERMISSION_NOT_GRANTED`) rejects revoking a permission with no active grant (never granted, or already revoked) — a deliberate divergence from `revokePermission`'s existing silent-no-op precedent for direct grants. New migration `V30__global_access_group_permission_soft_delete.sql` adds `deleted_at` to `global_access_group_permissions`/`..._aud` (the tenant-side `access_group_permissions` table already had it from `tenant-access-group-bulk-and-delete`'s `V29`); since the table's original `UNIQUE (global_access_group_id, permission)` constraint from `V14` was never explicitly named, its Postgres-autogenerated name gets silently truncated at 63 bytes and can't be guessed/hardcoded reliably — the migration drops it via a `DO $$ ... pg_constraint` introspection block instead, then adds the same `deleted_at IS NULL` partial unique index `V29` used on the tenant side. Two real bugs were fixed as groundwork before revoke could work correctly: (1) both scopes' grant endpoints previously never cleared `deletedAt` on their reactivate-or-create lookup's `orElseGet` path (unreachable dead code until this feature added a way to set `deletedAt` in the first place) — now `.map(existing -> clear).orElseGet(create)`; (2) `GlobalPermissionService#effectivePermissions` (staff/global scope) called the unfiltered `findByGlobalAccessGroupIn` — AppSec caught this naming distinction from `PermissionService` (tenant-scope, already correctly filtered) during PLAN review — now swapped to a new `findByGlobalAccessGroupInAndDeletedAtIsNull`. Also closes a second, unrelated gap found while scoping the SPEC: neither scope's list endpoint (`GET .../access-groups`) exposed a role's granted permissions at all — `AccessGroupDto`/`GlobalAccessGroupDto` gain a `permissions` field, populated via one bulk query per list call (grouped by role id in memory), not one query per role; the DTOs' original single-arg `from(...)` factory stays for callers that don't need it (e.g. `getMemberDetail`'s per-membership mapping), defaulting to an empty list. No deletion-confirmation-token step on either revoke endpoint (deliberate Tier 2 call, documented in the SPEC's Non-functional section — losslessly reversible via regrant, unlike account/tenant/access-group deletion). Full-suite `./mvnw verify` green.
| `chat-message-search` | ✅ Done (backend) | Item 16's deferred lexical message-content search, closed end-to-end per `specify/features/chat-message-search/`. New `GET /api/chat/messages/search` (`q` required non-blank, optional `senderId`/`conversationId`/`dateFrom`/`dateTo`/`cursor`/`size`, `Accept-Language` header) on the existing `ChatController`, backed by a new `ChatMessageSearchService`/`ChatMessageSearchRepository` (not folded into `ChatConversationService`/`ChatMessageRepository` — materially different authorization shape and query-composition strategy, per PLAN.md). Migration `V34__add_chat_message_search.sql` adds two generated `STORED` `tsvector` columns on `chat_messages` (`content_tsv_pt`/`content_tsv_en`, `to_tsvector('portuguese'|'english', content)`) with their own GIN indexes — Postgres backfills existing rows automatically via the `ALTER TABLE` rewrite, no separate backfill step. `ChatMessageSearchRepository#searchPt`/`searchEn` are native `@Query(nativeQuery = true)` methods (required for `websearch_to_tsquery()`/the `chat_participants` `EXISTS` subquery/dynamic optional filters — not expressible in HQL) carrying the **AppSec-mandated tenant-scoping fix** in the same `WHERE` clause as every other predicate: `EXISTS (... chat_participants cp WHERE cp.conversation_id = cc.id AND cp.user_id = :callerId AND cp.deleted_at IS NULL)`, `cc.tenant_id = :activeTenantId`, `cc.kind IN ('PEER_DIRECT','PEER_GROUP')` (REQ-1, excludes `SUPPORT`), `cc.archived_at IS NULL AND cc.deleted_at IS NULL` (REQ-4), `m.deleted_at IS NULL`. **Critical gotcha this repository's own Javadoc documents for future contributors**: as native SQL, `searchPt`/`searchEn` are *not* covered by Hibernate's `TenantFilter`/`SoftDeleteFilter` `@Filter` mechanism (only HQL/JPQL queries and collection fetches respect it, same class of gap `ChatConversationRepository#findByIdRespectingFilter`'s Javadoc already calls out for primary-key lookups) — every scoping condition had to be hand-written into the query text itself. `ChatMessageSearchService` resolves `TenantContext#getActiveTenantId()` itself, **before** invoking the repository, and fails closed (empty `ChatMessageSearchPageDto`, zero repository interaction at all) when absent — deliberately never reads `tenantContext.isStaff()`/`isStaffAdmin()` anywhere, so a `STAFF_ADMIN` with no active tenant gets the exact same empty result as anyone else (REQ-5: no oversight/look-in bypass of any kind for this endpoint, unlike `TenantFilterAspect`'s own staff-no-active-tenant bypass for every other endpoint). New `ChatSearchLocale`(`PT`/`EN`)/`ChatMessageSearchLocaleResolver` (REQ-13-15) mirror `DeletionConfirmationLocaleResolver`'s exact `Accept-Language`-highest-priority-range resolution logic byte-for-byte, deliberately duplicated rather than reused to avoid coupling chat search to that unrelated feature's `DeletionLocale` type. `q` parsing is entirely `websearch_to_tsquery()` inside Postgres (quoted phrases, `-exclude`d terms); REQ-11 (blank/missing rejected) is checked in Java first (new `ChatBlankSearchQueryException` → 400 `CHAT_SEARCH_QUERY_BLANK`), REQ-12 (`dateFrom` after `dateTo`) similarly (new `ChatInvalidSearchDateRangeException` → 400 `CHAT_SEARCH_INVALID_DATE_RANGE`), both wired into `ChatExceptionHandler`. `q` is `@RequestParam(required = false)` on the controller specifically so a *missing* `q` reaches this validation (and its named error code) instead of Spring's generic `MissingServletRequestParameterException` 400. Cursor pagination reuses `ChatCursor`'s existing id-only opaque-cursor shape against `chat_messages.id` (monotonic with `created_at`, immutable messages), most-recent-first. No `@AuditLog` (search is a read, not a state change, per PLAN's non-functional requirements) — structured `INFO` logging instead (actor id, `hasQuery`, filter-presence booleans, result count; explicit Javadoc reminder never to log the raw `q` string). REQ-3 (an inaccessible/nonexistent/`SUPPORT`/archived `conversationId` filter) returns a normal `200` with an empty/unaffected page, never a distinguishable 403/404 — verified indistinguishable across all four cases. Both AppSec-required regression tests pass: two-tenant cross-tenant-isolation (a user with live memberships/messages in two tenants, Tenant 1 active, sees only Tenant 1's matches) and no-active-tenant fail-closed for both a plain `STAFF` and a `STAFF_ADMIN` caller (zero results, zero unfiltered scan). Also covers the SPEC's own flagged main implementation risk — one parameterized integration test across all four removal modes (left/removed/archived/soft-deleted) confirming a former participant's search excludes that conversation while a still-current one still surfaces. Full-suite `./mvnw verify` green (migration test, locale/exception/DTO unit tests, `ChatMessageSearchRepositoryTest` (Testcontainers, 9 cases), `ChatMessageSearchServiceTest` (Mockito, 9 cases), `ChatMessageSearchControllerIntegrationTest` (Testcontainers/MockMvcTester, 20 cases)), atomic commits per TASKS.md section. Frontend companion (`knowly-app/specify/features/chat-message-search/`) shipped in parallel against this same API contract — see its own row above for status; this backend endpoint is now live for that frontend to confirm against, closing the "not yet confirmed against the real running backend" caveat noted there. **Amended (2026-08-10): unified entity search shipped, adding a separate `GET /api/chat/search` endpoint alongside the unchanged `GET /api/chat/messages/search` above (REQ-16 through REQ-26).** New `ChatEntitySearchService` orchestrates four independently-tenant-scoped, per-section try/catch-wrapped sections — people (`ChatEligibilityService#searchEligibleDirectCandidates`, extracted from `listCandidates`'s `"direct"`-branch anchor-intersection logic via a shared private helper, name-prefiltered via a new `UserProfileRepository` JPQL query with an explicit `deletedAt IS NULL` guard), groups (`ChatConversationService#searchDiscoverableGroups`, composing `listDiscoverableGroups`'s eligibility/`!isParticipant` filters with a title match plus a union of the caller's own matching participant groups), Support (`SupportTicketService#findOwnOrClaimableChannel`, composing the existing member-channel/unclaimed-inbox/claimed-ticket lookups, matched against a fixed locale-aware "Suporte"/"Support" label reusing `ChatMessageSearchLocaleResolver`/`ChatSearchLocale` unchanged), and RAG (`ConversationService#searchOwn`) — plus a blank-`q` "recent places" merge of `ChatConversationService#listConversations` (chat kinds) with the caller's own RAG conversations (REQ-26's design correction: two existing sources merged, not one, since `listConversations` alone structurally cannot reach the sibling `conversation.Conversation` table — flagged and confirmed in PLAN.md, not a scope change). Two new explicit-tenant-predicate JPQL repository methods (`ChatConversationRepository#findDiscoverableByTitle`, `ConversationRepository#searchByOwnerAndTitle`) carry `tenant_id`/`tenant.id = :param` written directly into the query text — **the same native-query tenant-scoping gotcha `ChatMessageSearchRepository` documents also applies to ordinary JPQL**: `TenantFilterAspect` is a global `@Around` advice that disables `TenantFilter` session-wide for any staff caller with no active tenant selected, regardless of whether the calling code itself ever reads `isStaff()` — a future JPQL query in this codebase must not assume `@Filter` alone is sufficient just because it isn't native SQL. **Two AppSec-mandated cross-tenant gaps were found and fixed before this shipped** (see PLAN.md's "AppSec re-review (2026-08-10)" section for the full writeup): **Gap 1** — the participant-groups union branch originally trusted `chatParticipantRepository.findByUserId` (no tenant column/`@Filter` of its own) without scoping; fixed by reusing `ChatConversationService#isVisibleUnderActiveTenant` (promoted from `private` to package-private so `listConversations` and `searchDiscoverableGroups` share one implementation) — **and a second round of full-stack integration testing then caught a residual leak `isVisibleUnderActiveTenant` alone doesn't close**: that method's own Javadoc only ever restricted STAFF/STAFF_ADMIN actors ("a non-staff actor's conversations are never filtered here"), but a plain non-staff caller can genuinely hold `TenantMembership`/`chat_participants` rows in more than one tenant — fixed with an additional explicit equals-tenant predicate on every `findByUserId` row in the participant-groups branch, closing the leak for every caller, not just staff (regression test: `ChatEntitySearchControllerIntegrationTest#participantGroupsUnionNeverLeaksASameTitledGroupFromAnInactiveTenant`, plus the service-level companion in `ChatGroupMembershipServiceTest`). **Gap 2** — both new JPQL queries now carry the explicit tenant bind parameter described above instead of relying on `@Filter`; `ChatConversationService.searchDiscoverableGroups`/`ConversationService.searchOwn` each resolve `TenantContext#getActiveTenantId()` themselves and fail closed (empty result, zero repository interaction) when absent — regression tests confirm a `STAFF`/`STAFF_ADMIN` caller with no active tenant gets zero group and zero RAG results across two tenants, not a merged cross-tenant set. No oversight bypass of any kind anywhere in `ChatEntitySearchService` (REQ-18) — no method reads `tenantContext.isStaff()`/`isStaffAdmin()`. Response is polymorphic by request shape: `ChatEntitySearchResponseDto` (non-blank `q`, four capped-at-5 `ChatEntitySearchSectionDto`s plus a nullable `ChatSupportSearchResultDto`), `ChatEntitySearchResultDto` (blank `q`, capped `recentPlaces` list), or a single `ChatEntitySearchSectionDto` for the `type`(`people`\|`groups`\|`rag`)+`offset` "see more" expand form (new `ChatInvalidSearchExpandParamException` → 400 `CHAT_SEARCH_INVALID_EXPAND_PARAM` when only one of the pair is supplied or `type` is out of enum). Full `./mvnw verify` green including the amendment's own unit (`ChatEligibilityServiceTest`, `ChatGroupMembershipServiceTest`, `SupportTicketServiceTest`, `ConversationServiceTest`, `ChatEntitySearchServiceTest`) and integration (`ChatConversationRepositoryTest`, `ConversationRepositoryTest`, `UserProfileRepositoryTest`, `ChatEntitySearchControllerIntegrationTest`) coverage, atomic commits per TASKS.md's Amendment section. **AppSec follow-up fix (2026-08-10): staff-scope/tenant isolation gap in `GET /api/chat/messages/search`'s role-based scoping.** `ChatMessageSearchRepository`'s `SCOPE_PARTICIPANT_AND_DISCOVERABLE` fragment's `(:activeTenantId IS NULL OR cc.tenant_id = :activeTenantId)` guard was trivially true for every row once `activeTenantId` was `null` (REQ-5f's staff-no-active-tenant case), so a caller who is simultaneously a tenant `MEMBER` and a `STAFF` user got an unrestricted cross-tenant participant scan instead of staff-scope-only (`cc.tenant_id IS NULL`) results — held for both `PEER_GROUP` and `PEER_DIRECT` alike (the scope fragment never branches on conversation kind, confirmed by reading `BASE_PREDICATE`/the scope fragments directly). Fixed by requiring `cc.tenant_id IS NULL` in that branch: `((:activeTenantId IS NULL AND cc.tenant_id IS NULL) OR cc.tenant_id = :activeTenantId)`. New regression test `peerDirectConversationsAreIsolatedByActiveTenantAndStaffScopeJustLikeGroups` (a `MEMBER`-of-two-tenants-and-STAFF caller sees only the active tenant's `PEER_DIRECT` content while a tenant is active, and only staff-scope content once cleared) plus a rewrite of `staffWithNoActiveTenantGetsResultsFromTheirOwnDirectParticipantConversation` to assert a tenant-owned conversation no longer leaks into staff-scope results. All 43 tests across `ChatMessageSearchRepositoryTest`/`ChatMessageSearchServiceTest`/`ChatMessageSearchControllerIntegrationTest` green. **Second AppSec-confirmed fix (2026-08-10, context-boundary correction), caught by a live Playwright test**: a `STAFF_ADMIN` viewing chat in staff scope (no active tenant) could match a tenant member's message, because `ChatMessageSearchService.search()` checked `tenantContext.isStaffAdmin()` *before* resolving the caller's current context, so the `PLATFORM_UNRESTRICTED` scope fragment (`searchUnrestrictedPt`/`En`, no tenant predicate at all) ran regardless of active tenant. Fixed by resolving `activeTenantId` first, then branching admin-vs-non-admin strictly within that resolved context; `PLATFORM_UNRESTRICTED`/`searchUnrestrictedPt`/`En` deleted entirely (no dead code left behind) and replaced with a new `STAFF_SCOPE_UNRESTRICTED` fragment (`searchStaffScopeUnrestrictedPt`/`En`, `cc.tenant_id IS NULL`) reachable only from the staff-scope (no-active-tenant) branch. `ChatConversationRepository#findDiscoverableIdsPlatformWide` renamed to `findDiscoverableIdsStaffScope` (cosmetic, no behavior change — its candidates were already safely filtered by the participant/discoverable fragment's own tenant guard). Regression tests: `ChatMessageSearchControllerIntegrationTest#staffAdminWithNoActiveTenantGetsUnrestrictedStaffScopeResultsButNeverTenantContent` (the exact reported scenario — a `STAFF_ADMIN` who also holds a genuine tenant membership, searching in staff scope, must never match that tenant's content), plus `memberAdminActiveInATenantNeverMatchesStaffScopeContentEvenAsAParticipant` (mirror direction) and `staffAdminActiveInATenantAsOrdinaryMemberDoesNotGetUnrestrictedTenantAccess` (a `STAFF_ADMIN` who is merely a tenant `MEMBER`, not `MEMBER_ADMIN`, no longer gets unrestricted tenant access — the admin bypass is scoped to the specific role held in the current context, not the caller's global role). Service-level mirrors in `ChatMessageSearchServiceTest` (`staffAdminWithNoActiveTenantDispatchesToStaffScopeUnrestrictedSearch`/`staffAdminWithAnActiveTenantNeverDispatchesToStaffScopeUnrestrictedSearch`). All 283 tests across the `br.com.conectabyte.knowly.chat` package green (`./mvnw test`, not full `verify`); see `specify/features/chat-message-search/TASKS.md`'s "Amended (2026-08-10, context-boundary correction)" section for the task breakdown. |

## Feature status — frontend (`knowly-app/`)

Every feature below has its own
`knowly-app/specify/features/<name>/{SPEC,PLAN,TASKS}.md` — read those
for the actual requirements and decisions.

| Feature | Status | Notes |
|---|---|---|
| `login` | ✅ Done | Login-code (passwordless) flow. |
| `logout` | ✅ Done | Logout in the app shell nav; calls `POST /api/auth/logout`. Introduced this app's first CSRF token wiring (`withXsrfConfiguration()`), since logout is the first authenticated, non-CSRF-exempt POST the frontend makes. |
| `select-tenant` | ✅ Done | Multi-membership tenant picker; also handles the 0-membership staff case by falling back to the backend's all-tenants listing (`GET /api/tenants`) when the memberships list comes back empty. Amended REQ-5: 0-membership sessions no longer redirect to `/select-tenant`. |
| `onboarding-dashboard` | ✅ Done | First-run tour + dashboard metric widgets. Tour auto-start moved to `welcome-screen`. |
| `article-management` | ✅ Done | Upload (with polling + an animated status badge for processing/ready/failed), inline edit, delete, permission-gated UI. **REQ-10–17 UX fixes (2026-08-01)**: polling no longer flickers the full list (`loadArticles` splits initial-load vs poll-triggered paths, only the initial call touches `loading`; poll responses are shallow-compared against the current list before writing the signal); deletion now requires confirmation via a new shared `shared/confirm-dialog.component.ts` (first modal pattern in this codebase, built on native `<dialog>` — see `DECISIONS.md`; its Angular output is named `dismissed`, not `cancel`, per `@angular-eslint/no-output-native`); the Upload button is disabled (`canUpload` computed) until both a title and a file are present; the layout is two-state — full width with no article-content `<section>` until one is selected, then a narrower `<aside>` alongside the mounted `<section>`. |
| `conversations` | ✅ Done | Chat UI over SSE (hand-rolled parser — native `EventSource` can't POST a body). |
| `user-management` | ✅ Done | Tenant members/roles/permissions/access-groups admin UI. |
| `user-management-screens` | ✅ Done | Adds the staff-side global user management context alongside `user-management`'s existing tenant-scoped one. `UserManagementPageComponent` (mounted at `/members`) switches between the untouched `MembersPageComponent` and a new `StaffDirectoryPageComponent` (list/search via `staff-user-listing`'s `GET /api/staff/users`, create via `staff-user-provisioning`) based on `ActiveTenantService`'s active-tenant signal — never both at once. `StaffUserDetailPanelComponent` shows direct/access-group/effective global permissions and grants/revokes/assigns via `staff-rbac-split`'s endpoints. The `role-model-refinement` `STAFF` ceiling is UI-reflected via a `viewerIsStaffAdmin` flag (hides/disables actions against `STAFF`/`STAFF_ADMIN` rows) — AppSec-confirmed this is cosmetic only, real enforcement stays server-side (`enforceStaffCeiling`). Nav entry (`nav.members`) now also shows for `STAFF_USER_VIEW`. |
| `tenant-creation` | ✅ Done | Staff-only `/tenants/new` form calling `POST /api/tenants`. Originally gated by an `isStaff` heuristic (whether `GET /api/tenants` succeeded); `navigation-menu` replaced that with the real `GlobalPermission.TENANT_CREATE` check (`GET /api/staff/permissions`) after the backend's `staff-rbac-split` made that heuristic wrong for a `STAFF` user granted `TENANT_CREATE` but not `TENANT_ACT_AS_ANY`. **2026-08-02 amendment (REQ-7–REQ-21)**: the form grew from name+admin-email to full company identification, the first admin's complete mandatory profile, and a role selector, mirroring the backend's now-larger `CreateTenantRequestDto`. New reusable `shared/address-fields.component.ts` (field-name-agnostic — same component renders both the company's English `AddressDto` fields and the first user's Portuguese `MandatoryAddressDto` fields, bound to two different `FormGroup`s) and `shared/contacts-list-editor.component.ts` (`FormArray`-bound repeatable contact rows, starts with one). `TenantCreatePageComponent` converted to Reactive Forms; `taxId` carries a conditional CNPJ-shape validator (14 digits, Brazil only, re-evaluated on `country` change); `ActiveTenantService.createTenant()` now takes a single typed `CreateTenantRequest` object. Submit-error mapping is best-effort (see that feature's PLAN.md "Deviations" section) since the backend's `TenancyErrorResponseDto` carries no field discriminator for `TENANT_ALREADY_EXISTS` and no confirmed 400 validation-error body shape was found in `tenancy`'s exception package — falls back to the generic banner per REQ-15 whenever a field can't be identified. 526/526 frontend tests green, `format:check`/`build`/`lint` all clean. **Bug fix (2026-08-04)**: `contacts-list-editor.component.ts`'s `createContactGroup()` only had `type`/`value` controls, so every submitted `profile.contacts[]` entry omitted `isPrimary` — backend `ContactDto` is a record with a primitive `boolean isPrimary` (no default), so Jackson rejected the missing field with a 400, meaning **every** tenant creation was broken end-to-end. Found by actually submitting the form via Playwright (valid test CNPJ `11222333000181`/CPF `11144477735`) instead of relying on the existing mocked-HTTP unit tests, which asserted the exact (already-broken) request body and so never caught it. Fixed by always sending `isPrimary: true` (not exposed in this form's UI); `ContactService.addContact` already demotes any earlier same-type primary, so this is safe even with multiple added rows of the same type. |
| `tags-list` | 📄 Reference only | **Not implemented on purpose** — exists solely as the canonical example of the SPEC/PLAN/TASKS format, paired with the backend's `tags-crud` reference. Don't build it unless explicitly asked to turn it into a real feature. |
| `navigation-menu` | ✅ Done | Real app-shell navigation (`nav-menu.component.ts`), links filtered by `PermissionsService`/`GlobalPermissionsService`; "switch tenant" link reusing `/select-tenant`. Fixed the `staffGuard`/create-tenant-link bug above as part of the same feature. **2026-08-01 addition (REQ-7 through REQ-13, all closed)**: logo/logout regression-tested as always visible to any logged-in `MEMBER` regardless of tenant permission level (REQ-7/REQ-8, no code change needed — already correct since `cca348a`); multi-membership tenant-switch listing regression-tested (REQ-9, no code change needed); real bug fixed in `canSwitchTenant`/`canLeaveTenant` (REQ-10/REQ-11) — the 0-membership branch no longer unconditionally resolves `true`, now checks `GlobalPermissionsService.has('TENANT_ACT_AS_ANY')`; `MEMBER`/`MEMBER_ADMIN` never see "Create tenant"/"leave tenant" regardless of permission level (REQ-12, already correct, regression-tested); `STAFF` "Create tenant" hides while acting inside a tenant session and reappears on leaving (REQ-13, already correct, regression-tested). The previously-flagged gap ("STAFF account holding exactly one real membership indistinguishable from a plain `MEMBER`") is now closed: `GlobalPermissionsService` exposes the new `isStaffAccount` field (`staff-rbac-split` REQ-9, confirmed against the actual `OwnGlobalPermissionsDto`), and `canSwitchTenant`/`canLeaveTenant` gained an explicit `length === 1 && isStaffAccount()` branch — no backend follow-up remains open for this feature. 502/502 frontend tests green, `format:check`/`build`/`lint` all clean. |
| `welcome-screen` | ✅ Done | Real `/welcome` landing screen (staff-generic or tenant-branded greeting, no sensitive/permission-gated content) — replaces `/dashboard` as the post-login/tenant-selection/root-redirect target. Fixed two real bugs: login and the root route (`''`) both used to send an already-authenticated session to the wrong place (tenant list, or unconditionally `/login`). Onboarding tour trigger moved here from `dashboard`; tour target ids moved to the global nav menu. |
| `dashboard-analytics` | ✅ Done | Period filter (`period-filter.component.ts`, native Tailwind toggle-button group post-`primeng-removal`) owned by `dashboard-page.component.ts`'s `period` signal; five reusable `metric-tile.component.ts` instances (active articles/conversations/USER messages/ASSISTANT messages/active members, each with a line sparkline via the shared `chart-canvas.component.ts` + `toSparklineData()`), superseding the old `article-count-card`/`conversations-card`/`messages-card` (all three deleted); `message-split-chart.component.ts` (USER/ASSISTANT donut, `toDonutData()`); `conversations-activity-chart.component.ts` (per-day bar chart, `toBarData()`); every chart paired with a visually-hidden `.sr-only` mirror `<table>` generated from the same tested mapping function; `members-breakdown-card.component.ts` (`GET /api/tenants/metrics/members`); `top-articles-table.component.ts` (native `<table>` + a local `computed()` filter signal, replaces `article-usage-list.component.ts`); `export-button.component.ts` (native button + CSV blob download); `metric-fetcher.ts`'s `load()` extended to accept `params`. Originally built against PrimeNG (`p-chart`/`SelectButton`/`p-table`) per the SPEC written before `primeng-removal`; every widget was subsequently migrated to the current native-Tailwind conventions (`chart-canvas.component.ts` + its `CHART_CTOR` injection token, native `<table>`/toggle group) as part of `primeng-removal`'s cleanup — `chart.js@^4.5.1` (user-confirmed Tier 3 dependency, `DECISIONS.md`) stays a direct dependency of `chart-canvas.component.ts`, not PrimeNG's `Chart` wrapper. 253/253 frontend tests green, `format:check`/`build` clean (production bundle 577KB raw / ~138KB estimated transfer — well under budget, no PrimeNG chrome anymore). |
| `staff-global-dashboard` | ✅ Done | Closes item 6's remaining staff global-view half. `DashboardWrapperPageComponent` (mounted at `/dashboard`, replacing the direct `DashboardPageComponent` route mapping) branches on `ActiveTenantService.activeTenantResolved()`, same "one screen, two contexts" shape as `UserManagementPageComponent`: `DashboardPageComponent` unchanged when a tenant is active, a new `GlobalDashboardPageComponent` when staff has no active tenant. `GlobalDashboardPageComponent` makes one page-level fetch to `GET /api/staff/metrics/global`, rendering 4 `metric-tile.component.ts` tiles (total tenants, new tenants this month, total articles read, staff count) plus a 5th visibly-disabled "support tickets — coming soon" tile, `app-no-access-state` on a page-level 403 (not per-tile). `metric-tile.component.ts` gained an additive, backward-compatible pre-fetched-`[value]`/`[disabled]` mode (`url`/`valueSelector`/`period` now optional, self-fetch `effect()` gated on `url()` being defined) — every existing self-fetching tenant tile stays byte-for-byte unchanged; see `DECISIONS.md`. (A `[loading]` input was initially added too but removed during `qa-test-automation`'s final review as dead code — the page already gates tile rendering on its own loading state before mounting them.) `/welcome` gains one additive quick-link card to `/dashboard`, gated on new `GlobalPermission.DASHBOARD_VIEW_GLOBAL` (or `STAFF_ADMIN`-shaped, via a page-local `viewerIsStaffAdmin` computed matching `StaffDirectoryPageComponent`'s existing pattern) — no other content added to `/welcome` itself. `StaffUserDetailPanelComponent` gains a 4th, independent `<section data-testid="staff-audit-trail">` (own `auditTrail`/`auditTrailError` signals, own `loadAuditTrail()` wired into the existing `ngOnChanges`), consuming `GET /api/staff/users/{userId}/audit-trail` (new `AuditEvent` type + `getAuditTrail()` on `StaffUserService`), gated by new `GlobalPermission.AUDIT_TRAIL_VIEW`; a 403 there only shows `app-no-access-state` in that section, permissions/access-groups sections keep rendering. `nav-menu.component.ts`'s `nav.dashboard` entry now also shows for `DASHBOARD_VIEW_GLOBAL`, mirroring `nav.members`'s existing `TENANT_MEMBER_MANAGE`-OR-`STAFF_USER_VIEW` dual-gate shape. 271/271 frontend tests green, `format:check`/`build` clean, 9 atomic commits. `qa-test-automation` independently confirmed every REQ covered by a real test (and flagged/fixed the dead `[loading]` input above); `appsec` confirmed nav/link hiding is cosmetic-only (server-side 403 is the real boundary), no new cross-tenant exposure, no XSS surface, and frontend `GlobalPermission` values match the backend exactly — no blocking findings. |
| `tenant-pagination-search` | ✅ Done (frontend) | Adapts `/select-tenant`'s 0-membership staff fallback to the backend's new paginated `GET /api/tenants` envelope (`content`/`page`/`size`/`totalElements`/`totalPages`, `tenant-pagination-search` backend feature). `ActiveTenantService.listAllTenants` signature changed to `(page, size, search?)`, built via `HttpParams`, returning a new local `PageResponse<T>` interface (no shared/generic paginated type introduced — only paginated envelope in this frontend today, per SPEC's explicit scope). `SelectTenantPageComponent` gained a debounced (300ms, `Subject`+`debounceTime`+`distinctUntilChanged`) search input and prev/next pagination buttons, both funneled through one shared `fetchFallbackTenants()` request-builder so page/search state can't drift apart; a `fallbackError: 'network' | null` signal keeps the existing request-failure empty state (`selectTenant.empty`) visually distinct from a new genuine-zero-results state (`selectTenant.noSearchResults`). New Transloco keys (`selectTenant.searchLabel`/`searchPlaceholder`/`previousPage`/`nextPage`/`noSearchResults`) in `en`/`pt-BR`. 284/284 frontend tests green (51 files), `format:check`/`build` clean. `qa-test-automation` independently confirmed every REQ (including the debounce-timing and REQ-7-vs-REQ-8 visible-distinctness cases) is covered by a real test; `appsec` confirmed `search` only ever travels via `HttpParams` (never string-concatenated/`innerHTML`), no new field exposure beyond the old `TenantSummary` shape, and no new authorization surface (staff-only `TENANT_ACT_AS_ANY` gating stays entirely server-side) — no blocking findings. |
| `user-profile` | ⛔ Superseded by `user-profile-v2` | Was: frontend half of item 13 against `identity-profile-model`'s old flat contract (`fullName`/`address` string/`rg`/`cpf`/single `phone`). Retrofitted 2026-07-29 — see `user-profile-v2` below. Row kept only as history; do not treat any detail below as current for the shipped shape. |
| `user-profile-v2` | ✅ Done (frontend) | Retrofits `user-profile` to `identity-profile-model-v2`'s new contract. `ProfileService` (`core/profile.service.ts`) types rewritten: `UserProfile` now composes `fields: ProfileFields` as a **nested** object (`{userId, email, fields, avatarUrl}`, not flattened — a real deviation from PLAN.md's assumed shape, see that PLAN's "Deviations" section), `ProfileFields` gains `rgOrgaoEmissor`/`birthDate`/structured `Address`/`Contact[]`; new `uploadAvatar(file)` → `POST /api/users/me/profile/avatar` (multipart); `submitEditRequest(fields, contactChanges)` posts `{fields, contactChanges}`. `ProfileFieldsFormComponent` retrofitted in place: 8-field structured address fieldset, a repeatable contacts editor (add/edit/remove, 5-cap enforced client-side with a clear message, one-primary-per-type), diffs contacts against their loaded snapshot at submit time into `ContactChange[]` (`ADD`/`UPDATE`/`REMOVE`); new `showContacts` input (default `true`) — **the shipped `PUT /api/users/{id}/profile` never applies contact changes at all** (`UserProfileService#directEdit` hardcodes an empty `contactChanges` list), a real backend contract gap discovered during this implementation, so `ProfileSectionComponent`'s inline edit of an *other* user sets `[showContacts]="false"` rather than showing controls that would silently no-op. New `AvatarUploadComponent` (`shared/avatar-upload.component.ts`, presentational, current avatar or placeholder + native file input) wired into `OwnProfilePageComponent` only (self-only, always-direct upload, independent of the non-avatar form's pending state) — `OwnProfilePageComponent`'s old `hasDirectEditRight`/admin-shortcut branching is deleted entirely; every session now always calls `POST .../edit-requests` for non-avatar fields (no direct-edit path remains for anyone editing their own record, per `identity-profile-model-v2` REQ-11). `ProfileSectionComponent` gains a new `ownUserId` input; `canEdit` narrows to `canEdit() && userId !== ownUserId()` so the inline-edit affordance is hidden (not just disabled) on the viewer's own row — closes `user-profile/PLAN.md`'s previously-accepted self-exclusion gap, now a hard requirement since REQ-11 removed the admin self-edit bypass entirely. Both `StaffUserDetailPanelComponent`/`MemberDetailPanelComponent` now make one `getOwnProfile()` call per panel-open and thread the result down as `ownUserId`. **Real bug caught by TDAD during this work:** `ProfileSectionComponent` was `implements OnChanges`, which re-ran `loadProfile()` on *any* input change (not just `userId`) — once `ownUserId` started arriving asynchronously after initial render, this caused a spurious duplicate `GET /api/users/{id}/profile`; fixed by moving to a constructor `effect()` scoped only to `userId()`. `ProfileEditRequestsInboxPageComponent` row rendering extended for the structured proposed address and a `proposedContactChanges` list — **however, the backend's `GET /api/profile-edit-requests`/submit-request response always returns `proposedFields.address`/`.contacts` as `null`** (`UserProfileController`/`ProfileEditRequestController#toDto` hardcode `null` there even though the address is genuinely persisted and used internally on approval) — a confirmed backend response-mapping bug, not a frontend gap; the frontend UI is ready and will render correctly once that's fixed. `MembersPageComponent`'s member-detail panel now resolves `ProfileSectionComponent` via `MemberDetail.userId` (that field's earlier absence, flagged in `user-profile`'s row, is confirmed already resolved upstream). 336/336 frontend tests green, `format:check`/`build` clean. **Two backend follow-ups to file, not resolvable from this frontend-only feature:** (1) `PUT /api/users/{id}/profile` should apply `contactChanges` the same way the edit-request approval path does, so an admin/permission-holder editing someone else's contacts directly is actually possible; (2) `ProfileEditRequestDto.proposedFields.address`/`.contacts` should be populated in the list/submit responses instead of hardcoded `null`. Previously-flagged rough edges reconfirmed still accurate and not resolved by this retrofit: the edit-request inbox still shows requesters as `"User #{id}"` only (no display name/email in `ProfileEditRequestDto`), and inbox nav-gating still only reflects the *active* tenant's `PROFILE_EDIT` grant. **Follow-up (1) closed**: `knowly-api` `c0a817d` changed `PUT /api/users/{id}/profile` to accept `{fields, contactChanges}` (`ProfileEditRequestFieldsDto`, the same shape `submitEditRequest` already used) and genuinely apply the contact changes; the frontend closed the loop by restoring `ProfileService#directEdit`'s `contactChanges` parameter (sends the `{fields, contactChanges}` body), dropping `ProfileSectionComponent`'s `[showContacts]="false"` (back to the form's default), and threading `contactChanges` from the form submission through to `directEdit` — mirroring `OwnProfilePageComponent`'s existing `submitEditRequest(fields, contactChanges)` wiring. `StaffUserDetailPanelComponent`/`MemberDetailPanelComponent` needed no change (neither passes `[showContacts]`, so both already inherit the default). 337/337 frontend tests green, `format:check`/`build` clean. **Follow-up (2) also closed** (same `c0a817d`): `proposedFields.address` is now genuinely populated in both `GET /api/profile-edit-requests` and the submit response; `proposedFields.contacts` stays `null` by deliberate Tier 2 scoping (documented in `identity-profile-model-v2`'s code) — the proposed contact add/update/remove set has no defined `ContactDto` (snapshot) shape, and is already correctly surfaced via the separate `proposedContactChanges` (`List<ContactChangeDto>`) field, which the frontend inbox already consumes — not a gap. Both `identity-profile-model-v2` follow-ups are now closed. The two previously-reconfirmed rough edges above (`"User #{id}"` display, active-tenant-only nav gating) are now also closed on the backend (2026-07-30): `ProfileEditRequestDto` gained `requesterName`/`requesterEmail`, and a new `GET /api/tenants/permissions/any-tenant?permission=X` lets the frontend check a permission across every membership, not just the active tenant — see `identity-profile-model-v2`'s row above for detail. **Frontend consumption of both is now also done** (2026-07-30, `7746e0f`): `ProfileEditRequestsInboxPageComponent` renders `requesterName` (falling back to `requesterEmail`, then `"User #{id}"`), and the nav-menu's `PROFILE_EDIT` inbox link is gated via new `PermissionsService.fetchInAnyTenant`/`.hasInAnyTenant`, backed by `GET /api/tenants/permissions/any-tenant`, instead of the active-tenant-only check. 353/353 frontend tests green, `format:check`/`build` clean. `identity-profile-model-v2` and `user-profile-v2` are now both fully closed on both sides, no outstanding rough edges. **Amendment (2026-08-02, REQ-21/22/23) closed**: new `InputMaskDirective` (`shared/input-mask.directive.ts`, `HostListener`-based, selector `[appInputMask]="'cpf'\|'cep'\|'phone'"`, see `DECISIONS.md`) reformats `cpf`, `cep`, and phone-type (`PHONE`/`WHATSAPP`) contact-value inputs as-you-type with Brazilian punctuation, emitting the unmasked digits-only value via `(appInputMaskChange)`; `rg`/`rgOrgaoEmissor` deliberately untouched (no national RG format to mask against). Caret position is preserved on mid-string edits via a digit-count-based `setSelectionRange` fix-up. Wired into `ProfileFieldsFormComponent`'s template (conditionally, per contact row, on `type`), with no DOM/selector/test-id changes — every pre-existing test in `profile-fields-form.component.spec.ts` still passes unmodified. One real bug caught during TDAD: binding a masked input's `[value]` straight to the (unmasked, by design) underlying signal fights the directive's own DOM write on every keystroke, since the component's own `[value]` binding re-applies the plain digits right after the directive formats the display — fixed by exporting `formatMaskedValue(mask, rawValue)` from the directive module and using it in the template's `[value]` bindings for the three masked fields too, so both bindings agree on the same formatted string (see PLAN.md's amendment section for the full sequence). No client-side format/checksum validation was introduced (REQ-23) — a mask-incomplete value submits exactly as typed. 603/603 frontend tests green, `format:check`/`build`/`lint` clean. **Amendment — country-agnostic identity/address model (2026-08-02) closed**: `cpf`→`taxId` rename, `rg`/`rgOrgaoEmissor`/`birthDate` removed entirely (matching the same-day backend amendment), and the Brazil-only 8-field address replaced by a country-agnostic `addressLine1`/`addressLine2`/`city`/`stateRegion`/`postalCode`/`countryCode` shape used identically for every country. New `shared/country-field-config.ts` (a plain, hand-rolled `Map`-backed lookup — not a `Record`, avoids an ESLint `security/detect-object-injection` warning on dynamic-key lookups) supplies `BR`/`US`/`GB`/`DEFAULT` country-driven labels/mask availability for `taxId`/`postalCode`; `InputMaskDirective` gained a country input and its own small per-`(mask, country)` pattern table (nested `Map`s, same lint reasoning), so a country with no known mask (e.g. `GB`) renders a plain, unmasked passthrough input. New `shared/phone-ddi-input.component.ts` composes a DDI/national-number pair into a single E.164 `value` for `PHONE`/`WHATSAPP` contact rows and splits an existing one back on load, using a deliberately-approximate seeded DDI-length map (`BR: 2`, `US: 1`, `GB: 2`) rather than a full ITU calling-code table — flagged as the likely next `libphonenumber-js` request if it proves inadequate in practice. Two real bugs caught by TDAD: (1) a plain `[value]` binding on the new `countryCode` `<select>` silently failed to select the matching `<option>` in tests, since Angular applies a parent element's own property bindings before its `@for`-generated children exist on the very first change-detection pass — fixed by binding `[selected]` per-`<option>` instead; (2) `PhoneDdiInputComponent`'s naive resync-from-`value()` effect fought a manually-typed DDI whose length differed from the seeded heuristic, since the parent hands the composed value straight back as this component's `value` input on the next change-detection pass — fixed by tracking the component's own last-emitted value and skipping resync on that self-echo (same class of bug as the pre-existing `formatMaskedValue` fix for masked inputs). Also updated the already-shipped `tenant-creation` feature's `TenantCreatePageComponent`/`ActiveTenantService` (the first admin's `CreateTenantProfile` mirrors `MandatoryProfileFieldsDto`'s shape one-for-one, so the same rename/restructure applies there too) — `AddressFieldsComponent` gained a new `idPrefix` input (default `''`, no other call site affected) so the company's and first admin's address blocks' `data-testid`s stay unique now that they share field *names* for the first time (`city`/`postalCode`). `bootstrap-profile-completion`'s screen needed no direct change beyond its shared `EMPTY_FIELDS` constant, since it composes `ProfileFieldsFormComponent` directly and inherited the new field set automatically — this also finally closes that feature's own previously-deferred RG-removal/birth_date-removal PLAN/TASKS follow-up (see its own row below). Implemented against the applied `V26`/`V27` migrations and the backend PLAN's amendment text as ground truth while `identity-profile-model-v2`'s own backend amendment was still in progress concurrently in the same repo (disjoint subprojects, no file conflicts). 623/623 frontend tests green, `format:check`/`build`/`lint` all clean. |
| `bootstrap-profile-completion` | ✅ Done (frontend) | Dedicated `/complete-profile` route (no guard, backend `ProfileCompletionFilter` re-enforces) for the bootstrap `STAFF_ADMIN` account's one-time, no-approval self-completion of its profile via `POST /api/users/me/profile/complete`, composing `ProfileFieldsFormComponent` with a new `requireAllFields` input (default `false`, every other call site unaffected) instead of forking a second form. `LoginPageComponent`'s verify-success handlers and `auth.interceptor.ts`'s `PROFILE_COMPLETION_REQUIRED` branch both route here off `pendingProfileCompletion`. Implemented/committed same-day as the RG-removal/birth_date-removal/country-agnostic-model decisions (`90777be`), before those decisions landed, so it originally shipped with `rg`/`rgOrgaoEmissor`/`birthDate` inputs and the old Brazil-only address shape — **now closed** as part of `user-profile-v2`'s country-agnostic-model amendment (see that row above): since this screen composes `ProfileFieldsFormComponent` directly and unmodified, it inherited `taxId`/`countryCode`/the new 6-field address/the phone-DDI composer automatically once that shared component was retrofitted — no RG/birthDate input or old address field name remains anywhere on this screen or in its request body. 623/623 frontend tests green (shared suite with `user-profile-v2`), `format:check`/`build`/`lint` clean. |
| `boxed-otp-input` | ✅ Done | Login screen's Code tab: single free-text 6-digit input replaced with six individually-boxed digit `<input>`s, inline markup in `login-page.component.ts` (no new `shared/` component — YAGNI, confirmed with user, no second numeric-OTP flow exists today). `digits = signal<string[]>(Array(6).fill(''))` + `code = computed(() => this.digits().join(''))` replaces the old plain `code` signal 1:1; `onSubmitCode`'s call to `AuthService.verifyCode` unchanged. Keydown-level digit rejection (`onDigitKeydown`, REQ-3) keeps commit/advance (`onDigitInput`, REQ-2) and filter/navigate responsibilities disjoint; Backspace-on-empty-box and Left/Right arrow navigation are imperative `document.getElementById('otp-digit-{i}')?.focus()` calls, matching this component's existing `onTabKeydown` focus idiom (no `ViewChildren`/`ElementRef`). One group-level `(paste)` listener (`onPaste`) extracts the first 6 digits from pasted text and distributes them from box 0 regardless of paste-focus position. Submission validation relies on native per-box `required` (REQ-8) — no manual "all six filled" JS check, matching PLAN's explicit no-redundant-validation-path decision. Two new Transloco keys (`login.codeGroupLabel`, `login.codeDigitLabel`) in `en`/`pt-BR`; unused `login.codeLabel` key removed (grep-confirmed no other reference). Existing spec's `input[name="code"]`-based assertions (six call sites, not the ~four originally estimated — see PLAN's "Deviations" section) rewritten with a new `fillOtpBoxes(fixture, code)` test helper simulating real per-box typing. 279/279 frontend tests green (11 new for this feature), `format:check`/`build` clean, single commit (SPEC/PLAN/TASKS were drafted but never separately committed before implementation — landed together with the code in one commit per user's explicit "implement it" instruction covering the whole feature). **Bug fix (2026-08-04)**: `onDigitInput` took only `value.slice(-1)` of the input event's value, so SMS/one-time-code autofill (this form declares `autocomplete="one-time-code"`) or any input that bypasses `maxlength="1"` and delivers the whole code into one box in a single `input` event silently discarded all but the last digit. Found via Playwright (`pressSequentially` into box 0 reproduced the same truncation as real-device autofill would). Fixed by detecting a multi-digit value and spreading it across the remaining boxes, mirroring the existing `onPaste` logic; one new spec case. |
| `primeng-migration` | ⛔ Superseded by `primeng-removal` | Was: full replacement of hand-rolled Tailwind components with PrimeNG + PrimeIcons (2026-07-25). Reverted one day later — see `primeng-removal` below and `DECISIONS.md`'s two consecutive dated entries. Row kept only as history; do not treat any detail below as current. Setup phase: `primeng@22.0.0`/`@primeuix/themes@3.0.0`/`primeicons@8.0.0`/`@angular/cdk@22.0.0` added; `core/prime-theme.ts` preset maps `ink-*`/`signal-*` onto PrimeNG's tokens for light/dark; `providePrimeNG()` wired in `app.config.ts` with `darkModeSelector: '.dark'`. 2026-07-25 chrome/menu pass (items 1-3): `nav-menu.component.ts` rebuilt with per-category inline `p-menu`s (custom `#submenuheader`/`#item` templates keep every `data-testid`/`data-tour-id`/permission gate), PrimeIcons replace its inline SVGs; `app-shell.component.ts`'s `<aside>`/`<header>` get a static `class="dark"` so PrimeNG components in the permanently-dark chrome always render dark tokens regardless of `ThemeService`'s toggle; `logout-button`/`language-switcher`/`help-menu` migrated to `[pButton]`/`p-menu`. 2026-07-25 final pass (items 4-7, all feature screens): `error-state` → `p-message`; `welcome-page`'s quick-link cards → `p-card`; `login-page`'s inputs/buttons → `pInputText`/`pPassword`/`pButton` directives on the same native elements (no DOM restructuring, so all `querySelector('input[...]')`-based specs kept passing unchanged); `articles-page`'s upload/detail panels → `p-card`, text inputs → `pInputText`/`pTextarea`, buttons → `pButton` (article-list rows left as native markup — two independent actions per row, not a clean `Listbox` fit); `conversations-page`'s conversation list → `p-listbox` with a custom `#item` template (chat bubbles and the new-conversation/send buttons use `pButton`/`pInputText`; bubbles themselves stay bespoke `<div>`s, no PrimeNG fit); `members-page`'s member list → `p-table`; `select-tenant-page`'s tenant list → `p-listbox`, create-tenant link → `pButton`; `tenant-create-page`'s form → `pInputText`/`pButton`. `no-access-state` (single `<p>`) and login's tab UI stayed hand-rolled — no real PrimeNG component fit either. `angular.json`'s budget raised 800kB→900kB warning, 1MB→1.4MB error (bundle reached 1.27MB after the full migration; still not lazy-route-split — flagged as a follow-up, not solved here). |
| `global-staff-dashboard-trends` | ✅ Done | Backend + frontend both shipped; the pairing is now fully done end-to-end. **Backend** (`knowly-api/specify/features/global-staff-dashboard-trends/`): new `GET /api/staff/metrics/global/trends` on the existing `GlobalMetricsController`, delegating to a new `GlobalMetricsService#globalTrends(MetricsPeriod)` — same `@RequiresGlobalPermission(GlobalPermission.DASHBOARD_VIEW_GLOBAL)`/`@AuditLog(action = "metrics.global.trends.view")` pattern as `globalMetrics()`, never scoped through `TenantFilter`/`TenantContext` (REQ-11). Returns `GlobalTrendsDto`: two daily-bucketed series (`newTenantsPerDay`/`articlesReadPerDay`, zero-filled for `7d`/`30d`/`90d`, not for `all`) via new native `@Query` day-bucketed methods on `TenantRepository`/`MessageArticleCitationRepository` (`countTenantsByDay(Since)`/`countCitationsByDay(Since)`, reusing the existing `DailyCountProjection`), plus a `PeriodComparisonDto` (`current`/boxed-nullable `previous`/boxed-nullable `percentChange`) for each of the four `globalMetrics()` counts, computed via new derived `countByCreatedAtGreaterThanEqualAndCreatedAtLessThan` methods on `TenantRepository`/`MessageArticleCitationRepository`/`UserRepository`. New package-private `previousWindowStart(MetricsPeriod, Instant, Clock)` helper computes the non-overlapping `[currentStart - N, currentStart)` previous window (REQ-4's "immediately preceding period of equal length"); `percentChange` is `null` for `period=all` (no previous window, REQ-5) or when `previous == 0` and `current > 0` (REQ-6, never `NaN`/`Infinity`) — `current == previous == 0` yields `0.0` (no fabricated division, not explicitly required but the only non-arbitrary choice). New Flyway migration `V21__add_created_at_indexes_for_global_trends.sql` (`ix_tenants_created_at`, `ix_message_article_citations_created_at`) backs the unbounded `period=all` `GROUP BY` queries, per AppSec's non-blocking PLAN recommendation. New repository tests (`TenantRepositoryTest`, new `MessageArticleCitationRepositoryTest`, `UserRepositoryTest` additions) plus `GlobalMetricsServiceTest`/`GlobalMetricsControllerIntegrationTest` additions (zero-fill, `period=all` no-zero-fill/sorted, percent-change cases, REQ-5/6 null cases, 200/403/400 REQ-7/8/9/10, cross-tenant REQ-11, `metrics.global.trends.view` audit event). Full-suite `./mvnw verify` green (518/518), 4 atomic commits (index migration, repository queries, service method, controller endpoint). **Frontend** (`knowly-app/specify/features/global-staff-dashboard-trends/`): visually redoes `GlobalDashboardPageComponent` (Dashdark X-inspired gradient stat cards + two trend charts) per its own SPEC/PLAN/TASKS. New presentational `GradientStatCardComponent` (`label`/`subtitle`/`value`/`percentChange: number \| null \| undefined` inputs, icon via `<ng-content select="[icon]">`, no badge rendered when `percentChange` is `null`/`undefined`) replaces the plain `metric-tile.component.ts` presentation on this screen only (`metric-tile.component.ts` itself and `dashboard-page.component.ts`'s tenant-scoped tiles are untouched). Two new "dumb" chart components, `NewTenantsTrendChartComponent`/`ArticlesReadTrendChartComponent` (`src/app/features/dashboard/`), each with an exported pure `toXxxData()` mapper, `data = input.required<DailyCountRow[]>()`, `error = input<boolean>(false)`, an `.sr-only` mirror table, and `<app-error-state>` on `error`. `GlobalDashboardPageComponent` restructured to own both fetches (`GET /api/staff/metrics/global` unchanged REQ-7 behavior; `GET /api/staff/metrics/global/trends?period=` only attempted once the first call succeeds, REQ-7/8) behind one `period` signal shared by both charts and all four badges (`app-period-filter`'s existing `Period` union, no new selector); a shared `classifyMetricError()` helper (403-vs-network, mirroring `metric-fetcher.ts`'s inline logic) and a pure `percentChangeFor(comparison, period, trendsFailed)` clamp (no badge when trends failed, `period === 'all'`, or the backend itself sent `percentChange: null`) centralize REQ-8/9/10's "no badge" branching in one place instead of four ad-hoc bindings. New `dashboard.trends.*` i18n keys (subtitles for the four cards, label/subtitle for each chart, coming-soon subtitle) added to both `en.json`/`pt-BR.json` — the only two locale files present, confirmed via TASKS 0.1. Icons via `@lucide/angular`'s attribute-selector convention (`<svg lucideBuilding2>` etc., each imported directly into `GlobalDashboardPageComponent`'s own `imports` array — not `<lucide-xxx>` element tags). Built and tested against the backend `GlobalTrendsDto` contract documented in `knowly-api/specify/features/global-staff-dashboard-trends/PLAN.md`, in parallel with the backend implementation — confirmed matching now that both sides are done. 426/426 frontend tests green (16 new for this feature across 4 spec files), `format:check`/`build`/`lint` all clean, 4 atomic commits (i18n keys, `GradientStatCardComponent`, the two trend chart components, the page restructure). |
| `primeng-removal` | ✅ Done | Reverts `primeng-migration` (see `DECISIONS.md`) — PrimeNG, `@primeuix/themes`, `primeicons`, `@angular/cdk` fully removed; back to pure Tailwind + hand-rolled Angular standalone components. Icons: `@lucide/angular` (not the deprecated `lucide-angular`, which has no Angular 22-compatible peer range) — each icon is its own standalone component with an attribute selector (e.g. `LucideSun` → `<svg lucideSun>`), imported directly per-component, no central provider wiring. New shared `button-classes.ts` (severity/variant class helper) and `chart-canvas.component.ts` (direct Chart.js wrapper, replacing PrimeNG's `p-chart`/`UIChart` — uses a `CHART_CTOR` injection token so specs can mock Chart.js deterministically despite Angular's bundled-spec test runner sharing module instances). All 20 former PrimeNG consumers migrated to native HTML + Tailwind (menus → `<ul role="menu">`, tables → native `<table>` + local `computed()` filter signal, listbox → `<ul role="listbox">`, forms/buttons → native elements + `button-classes.ts`). 26 atomic tasks, one commit each — see `knowly-app/specify/features/primeng-removal/PLAN.md`/`TASKS.md` (including a "Deviations" section for the two implementation-detail corrections above). 221/221 tests, `format:check`, `build` all green. |
| `global-staff-dashboard-trends` | ✅ Done (backend, this branch) | Ported into this worktree as a hard prerequisite for `global-staff-dashboard-sparklines` below (this branch had diverged from `main` before `global-staff-dashboard-trends` landed there) — same shape as `main`'s implementation: new `GET /api/staff/metrics/global/trends` on the existing `GlobalMetricsController`, delegating to a new `GlobalMetricsService#globalTrends(MetricsPeriod)` (`@RequiresGlobalPermission(GlobalPermission.DASHBOARD_VIEW_GLOBAL)`/`@AuditLog(action = "metrics.global.trends.view")`, never `TenantFilter`/`TenantContext`-scoped). `GlobalTrendsDto`: `newTenantsPerDay`/`articlesReadPerDay` (zero-filled `7d`/`30d`/`90d`, not `all`) plus a `PeriodComparisonDto` for each of the four `globalMetrics()` counts. New Flyway `V21__add_created_at_indexes_for_global_trends.sql`. See `knowly-api/specify/features/global-staff-dashboard-trends/` (copied in from `main`) for the full SPEC/PLAN. Frontend half not touched in this branch (out of scope here — this branch only needed the backend contract to build `global-staff-dashboard-sparklines` on top of it). |
| `staff-members-management-redesign` | ✅ Done | Ink & Signal redesign of the staff/tenant-member management screens. New generic `SharedListComponent`/`app-shared-list` (`shared/shared-list/`), `columns`/`rowActions` input-object-driven (not `ng-content`), owning selection/sort/search/pagination as component-local signals; `SharedListCellValue` discriminated union gained an explicit `'identity'` variant (avatar+name+secondary line) beyond PLAN's original `string \| {pillKey; colorClass}` sketch — documented in `shared-list.model.ts` itself. `StaffDirectoryPageComponent`/`MembersPageComponent` migrated to consume it. New `shared/permission-labels.ts` (`translatePermissionLabel`, `permissions.*` i18n keys for every `Permission`/`GlobalPermission` value) and `shared/audit-trail-labels.ts`/`shared/audit-timestamp.ts` (`translateAuditAction`/`formatAuditTimestamp`) replace every raw enum/action-string render in the staff user detail panel's audit-trail table and both detail panels' permission switches — confirmed by a full-repo grep at close-out, nothing left unrouted through these maps. `StaffUserDetailPanelComponent`/`MemberDetailPanelComponent` reorganized: "Editar perfil" moved into a new top `<header>`; an admin-tier (`STAFF_ADMIN`/`MEMBER_ADMIN`) target shows no permission checkboxes, only a demote action gated by the viewer's role and disabled+explained via the detail response's real `isLastAdminOfType` field (no attempt-then-fail round trip); a `STAFF`/`MEMBER` target gets a promote action plus a switches-batch permission UI (`pendingPermissions` local signal, no HTTP call per toggle, one `ConfirmDialogComponent` batch-save covering the whole diff); delete moved into the panel's bottom action area for every role, disabled+explained only for the last `STAFF_ADMIN`/`MEMBER_ADMIN`. **Two deviations from PLAN.md, now recorded there directly (previously only in commit messages)**: (1) promote/demote confirm via a plain inline two-button step, not `ConfirmDialogComponent` — those endpoints have no deletion-confirmation-token endpoint for it to call; `ConfirmDialogComponent` stays reserved for the four flows that do have one (delete, batch-save, permission-revoke, group-unassign). (2) The new standalone `/staff/access-groups` screen (`AccessGroupManagementPageComponent`) has no backend "list a group's members" endpoint to call, so group membership for the expand/view-members UI is derived client-side via an N+1 `forkJoin` over every non-`STAFF_ADMIN` candidate's own detail call — accepted as the only option without adding backend surface out of this PLAN's scope. Inline access-group creation removed from the staff detail panel entirely, superseded by this new screen. 590/590 frontend tests green, `format:check`/`build`/`lint` all clean. |
| `tenant-access-group-management` | ✅ Done (frontend) | New tenant-scoped access-group screen (`/tenants/access-groups`, `TenantAccessGroupManagementPageComponent`), the tenant-scoped equivalent of `staff-members-management-redesign`'s global `/staff/access-groups` screen — list/create/roster (same N+1 `list()`+`getDetail()`-per-member shape as the global screen, cached in a `Map<membershipId, MemberDetail>` per screen visit so switching the selected group re-filters instead of re-fetching), grant-permission-to-group (new `member.service.ts#grantAccessGroupPermission`), single assign/unassign via the existing confirm-token flow, and two capabilities that depend on new backend endpoints from the parallel `knowly-api` feature `tenant-access-group-bulk-and-delete`: multi-group bulk-assign (new `batchAssignAccessGroups`, wired through a new reusable `MemberAccessGroupAssignmentComponent` checkbox-matrix — >1 selected id goes through the batch endpoint, exactly 1 reuses the existing single-assign endpoint) and delete-group behind its own new confirm-token flow (new `generateAccessGroupDeletionToken`/`deleteAccessGroup`). New route guard `tenantAccessGroupManagementGuard` (`core/tenant-access-group-management.guard.ts`), layered after `tenantSelectionGuard` on `/tenants/access-groups`. **Real deviation from PLAN.md, discovered during implementation and recorded there**: every per-action gate (the guard, every granular control on the page, and the new nav-menu link) checks `viewerIsMemberAdmin() || globalPermissionsService.has(<GlobalPermission>)`, not `permissionsService.has(<tenant Permission>)` as originally sketched — `TENANT_ACCESS_GROUP_VIEW`/`_CREATE`/`_DELETE`/`TENANT_PERMISSION_GRANT_CREATE`/`_DELETE` only exist as `GlobalPermission` values, not in the tenant-scoped `Permission` enum at all, and the backend's `requireAdminOfTenantOrStaff` lets any real tenant `MEMBER_ADMIN` through unconditionally regardless of `GlobalPermission` — gating purely on the `GlobalPermission` would have locked every ordinary `MEMBER_ADMIN` out of a screen the backend already lets them use. Mirrors `member-detail-panel.component.ts`'s pre-existing `viewerCanManageDirectPermissions` MEMBER_ADMIN-bypass shape. `app.routes.ts` registers the route with a direct `component:` reference (matching every other route in that file, including its closest sibling `staff/access-groups`) rather than PLAN's `loadComponent()` lazy-import sketch. 733/733 frontend tests green (94 files), `format:check`/`build`/`lint` all clean, 6 atomic commits. **Depends on `knowly-api/specify/features/tenant-access-group-bulk-and-delete`** (bulk-assign + delete-with-confirmation-token endpoints) being implemented in parallel — built and tested against that feature's documented API contract via `HttpTestingController` mocks; not yet confirmed against the real running backend. |
| `global-staff-dashboard-sparklines` | ✅ Done | Adds a true cumulative, carry-forward day-bucketed series for the two point-in-time staff-dashboard metrics that `global-staff-dashboard-trends` deliberately left without a chart ("Total de tenants"/"Membros da equipe interna") — see that SPEC's "Judgment call this SPEC resolves" for why carry-forward (not zero-fill, not "no chart") was chosen. **Backend** (`knowly-api/specify/features/global-staff-dashboard-sparklines/`): `GlobalTrendsDto` gains two appended fields, `totalTenantsPerDay`/`staffCountPerDay`, on the existing `GET /api/staff/metrics/global/trends` endpoint — no new endpoint, no new permission, same gate as before. Two new native `@Query` window-function methods, `TenantRepository.countCumulativeTenantsByDay()`/`UserRepository.countCumulativeStaffByDay()` (`sum(cnt) over (order by day)` over a `count(*) GROUP BY day` subquery), each computed over full history regardless of the requested `period` — a deliberate divergence from every other day-bucketed query in this codebase, since a cumulative running total for a displayed day always depends on all history up to that day, not just the display window. New private `mergeCarryForwardDays(List<DailyCountProjection>, MetricsPeriod)` helper on `GlobalMetricsService` (distinct from the existing `mergeZeroCountDays`): for a bounded period, seeds the first displayed day's carry value from the last cumulative total recorded *before* the range starts (not `0` — e.g. a tenant created 6 months ago still shows its true running total on day 1 of a `7d` window); for `period=all`, spans from the earliest row's day through today (`MetricsPeriod.dateRange` returns empty for `ALL`, so this range is built locally) or returns an empty list when there are zero rows at all; a bounded period with zero rows zero-fills every day at `0` (falls out of the general algorithm, no special case). No migration (both `created_at` columns already indexed by the prerequisite feature's `V21`). New/extended tests: `TenantRepositoryTest`/`UserRepositoryTest` cumulative-query cases (never-decreasing running total, same-day rows aggregate into one bump, staff query excludes non-`STAFF`/`STAFF_ADMIN` roles), a new plain-unit `GlobalMetricsServiceMergeCarryForwardDaysTest` (mocked repositories, fixed `Clock`, no Spring context — covers carry-forward-across-a-quiet-day, seed-from-before-the-range, `period=all` span/empty cases, bounded-zero-rows zero-fill), `GlobalMetricsServiceTest` wiring + REQ-6 regression-guard cases, and a `GlobalMetricsControllerIntegrationTest` addition asserting the two new fields at the API boundary with the same `date`/`count` shape as the existing two series. Full-suite `./mvnw verify` green (528/528), 5 atomic commits for this feature's own TASKS.md (cumulative tenant query, cumulative staff query, `GlobalTrendsDto` field append, `mergeCarryForwardDays` + wiring, API-boundary test coverage), plus one earlier commit porting the `global-staff-dashboard-trends` prerequisite (see its own row above). **Frontend** (`knowly-app/specify/features/global-staff-dashboard-sparklines/`): `GradientStatCardComponent` gains optional `sparklineData: SparklineDay[] | undefined`/`showSparkline: boolean = true` inputs — reuses `metric-tile.component.ts`'s exact Chart.js sparkline treatment (`SPARKLINE_OPTIONS`, exported from that file rather than duplicated) and its `.sr-only` data-table fallback markup, rather than forking a second chart implementation; no chart/table renders when `sparklineData` is `undefined`/empty (covers "before trends succeeds"/"trends never succeeded" states) or when `disabled()` (support-tickets card, regression guard). `GlobalDashboardPageComponent`'s local `GlobalTrendsDto` interface gains the two new fields (`totalTenantsPerDay`/`staffCountPerDay`) matching the now-shipped backend contract; each of its four (non-disabled) `<app-gradient-stat-card>` call sites gains one `[sparklineData]` binding to the matching field on the existing `trends` signal — `[sparklineData]="trends()?.totalTenantsPerDay"` etc. — no new HTTP call, no new signal/service. Graceful degradation (a failed `loadTrends` after a prior success keeps showing the last-good sparklines; before the first success, no chart) needed zero new production logic — it falls entirely out of `trends()`'s existing null/stale-on-error behavior, confirmed by dedicated regression tests. 463/463 frontend tests green (10 new for `GradientStatCardComponent`, 3 new for the page), `format:check`/`build`/`lint` all clean, 3 atomic commits (export `SPARKLINE_OPTIONS`, `GradientStatCardComponent` sparkline support, page wiring for all four cards at once — the backend contract's two series already having shipped together made a task-3-then-task-4 split unnecessary). |
| `chat-message-search` | ✅ Done (frontend) | **Amended (2026-08-10): unified Slack-style search bar, replacing the original filter-dialog cut below.** Retires `chat-search-dialog.component.ts`/its spec entirely (deleted) and the sidebar's "Buscar mensagens" icon button — the persistent search bar (companion `chat-unified-ui` change, see "Current state" above) is now the screen's only search entry point. `ChatMessageSearchService.search()` narrows to `search(q: string)` (the retired `senderId`/`conversationId`/`dateFrom`/`dateTo` filter-param composition is removed; `_results`/`_status`/`_nextCursor`/`_lastQuery`/`loadMore()`/`reset()` unchanged). New `ChatEntitySearchService` (`core/chat-entity-search.service.ts`) owns `GET /api/chat/search`: `search(q)` fans one response out into `people`/`groups`/`support`/`rag` signals + per-section `'idle'|'loading'|'ok'|'error'` status in one write; `recentPlaces()` (blank `q`) populates only `recentPlaces`/its own status, leaving the four entity sections untouched; `expandSection('people'|'groups'|'rag', q)` appends to that section only, sending `type`/`offset`/`q`; a transport-level failure on the one HTTP call marks all four entity sections `'error'` simultaneously (the PLAN's documented "two failure domains, not five" decision — a per-entity-kind partial failure is indistinguishable from zero matches under this contract, flagged as a known granularity gap vs. REQ-30's literal five-way reading). New `chat-unified-search.component.ts` (`features/chat/`) orchestrates both services off one shared, reused 400ms debounced `Subject` (`chat-search-dialog`'s original mechanism, moved here unchanged) — non-blank query fires both `search()` calls; blank query/focus fires only `recentPlaces()`. Renders five ordered groups (People, Groups, Base de artigos, Support, Messages; a zero-match group is omitted entirely, never shown empty), each with its own `role="group""` and labelled heading, plus a capped "recent places" list that fully replaces (never merges with) live results the moment the query goes non-blank. Every result click (person/group/Support/RAG/message/recent-place) resets **both** services and navigates via the PLAN-documented per-kind path (`/chat/:conversationId`, `/chat/support/:channelId`, `/chat/articles/:conversationId`); a person result with no existing 1:1 reuses a new shared `ChatService.openPersonConversation(userId)` helper (also now used by `ChatDirectoryRowsService`'s own person-row click, replacing its inline create-or-open branch — no behavior change). `chat-search-result-row.component.ts` gains a `kind: 'person'|'group'|'support'|'rag'|'message'` discriminator input (was message-only), one render/`aria-label`/click-emit branch per kind, existing message-kind behavior unchanged. Top-level dropdown `status` is a `computed()` mirroring the shipped dialog's explicit-enum convention: `'loading'` while any queried section is in flight, `'error'` only when **every** queried section failed (a mixed all-entities-failed/Messages-succeeded case resolves to `'results'` with an inline per-group error badge, not `'error'`), `'no-results'` when every queried section succeeded with zero rows, `'results'` otherwise. Escape/outside-click and any result click dismiss the dropdown and reset both services, so reopening always starts from a fresh `recentPlaces()` fetch, never stale results. i18n: `chat.search.*` gains `barPlaceholder`, five `groupLabel*` keys, `recentPlacesLabel`, `seeMore`, and five `resultA11yLabel*` keys; retires `entryPointLabel`/`dialogTitle`/`queryPlaceholder`/`filter*Label`/`blankQueryError`/`invalidDateRangeError`/`loadMore`/the single `resultA11yLabel` (all described the retired filter-form surface). 962/962 frontend tests green, `format:check`/`build`/`lint` all clean, atomic commits per TASKS.md subsection. **Original filter-dialog cut, superseded above (kept for history):** Adds a Slack-style "search inside message content" surface on top of `chat-unified-ui`'s existing name-only directory search (that one untouched, per SPEC) — a new `GET /api/chat/messages/search` icon entry point, not a route or a fourth column. New `ChatMessageSearchService` (`core/chat-message-search.service.ts`, signals-based, mirrors `ChatDirectoryService`'s shape): `search()`/`loadMore()` build only the query params actually set (`q` always, `senderId`/`conversationId`/`dateFrom`/`dateTo` omitted entirely when unset — never sent as `null`/empty string), `loadMore()` appends to (not replaces) `_results` and derives `cursor` from the last response's `nextCursor`, and a failed `search()`/`loadMore()` leaves prior results untouched (REQ-13) rather than clearing them. `status` is an explicit `idle \| loading \| results \| no-results \| error` signal, not derived from `results().length` (REQ-12's distinct-state requirement). `ChatSearchDialogComponent`/its "Buscar mensagens" sidebar entry point are both now deleted, superseded above. Built and tested against the API contract documented in `knowly-api/specify/features/chat-message-search/PLAN.md`. **QA pass (2026-08-10), 4 bugs reported by the product owner against this shell, all fixed and committed individually:** (1) *Session expiry not redirecting from chat* — `ConversationService.sendMessage()`'s RAG stream uses a raw `fetch()` call (SSE), bypassing `HttpClient` and therefore `auth.interceptor.ts`'s global 401→`/login` redirect every other screen gets for free; an expired session mid-stream silently rendered a generic "assistant unavailable" error instead. Fixed in `conversation.service.ts`'s `pump()`: a 401 now calls `Router.navigateByUrl('/login')` before completing, same as everywhere else. (2) *Tenant switch not refreshing chat state* — `ChatDirectoryRowsService.ensureLoaded()` fetched `conversations()`/`discoverableGroups()` exactly once, gated only by a `loaded` flag never reset; switching the active tenant (entering/leaving/changing) never re-triggered either fetch, so stale groups/conversations from the previous scope kept showing. Fixed with a new `maybeRefetchConversations()` reactive effect mirroring the already-correct `maybeRefetchEligibleParticipants()`/`maybeFetchArticles()` pattern in the same file. (3) *Search bar visually disconnected* — pure CSS: `chat-shell.component.ts`'s `chat-search-bar-region` header had no card treatment; now carries the same `rounded-2xl`/`border`/`bg-white`/`dark:bg-ink-900`/shadow classes the 3-column panels below it already use. (4) *SECURITY, investigated first, appsec-reviewed (PASS)*: reported as staff-only chat groups leaking into a tenant's chat view. Backend investigation (new regression test, `ChatTenantIsolationIntegrationTest#discoverableGroupsInsideATenantNeverLeaksAStaffOnlyGroup`) found this to be a **false alarm on the backend** — `ChatConversation`'s existing `@Filter(TenantFilter)` already excludes null-tenant rows from `findDiscoverable` whenever a real tenant is active (`TenantFilterAspect` only disables it for staff with no active tenant); the test passed before any backend change. The real root cause was bug (2)'s same frontend staleness — a staff-only group fetched before a tenant switch kept rendering after switching into a tenant, until the (2) fix above landed, which closes this too. AppSec review classified it Medium severity/correctly-scoped-as-frontend-only (the staff user was always authorized to have fetched that group's data; the bug was displaying it under the wrong on-screen tenant context, not an authorization bypass). **Visual redesign + highlight/jump-to-message amendment (2026-08-10)**, two separate commits: (1) *"Ink & Signal" cohesion pass*, direct product-owner visual review against a reference screenshot — `chat-shell.component.ts`'s search-bar header changed from `rounded-2xl` to a full `rounded-full` pill (matching the search input's own pill shape) with `mb-6` (was `mb-4`) for clearer separation from the 3-column panel below; `message-thread.component.ts`'s scroll area dropped its own nested border/shadow (was double-boxing inside the already-carded conversation column) for a muted `bg-ink-50/60`/`dark:bg-ink-950/40` backdrop; outgoing bubbles switched from a grayish `bg-ink-800`/`dark:bg-ink-700` to the brand's `bg-signal-600`/`dark:bg-signal-700` so they read as clearly brighter than incoming `bg-ink-100`/`dark:bg-ink-800/80` bubbles. (2) *Search-result highlight + jump-to-message*, a genuine new interaction, not a bug fix — amends this SPEC (REQ-32 through REQ-37): a message search result now `<mark>`-wraps the matched query substring (new `core/chat.model.ts#splitOnMatch`, literal/case-insensitive/first-occurrence, shared by the result row and the in-thread highlight); clicking a message result passes the target message id + query via router navigation `state` (not a query param — SPEC explicitly excludes deep-linking a message via a shareable URL) to `conversation-detail.component.ts`, which reads it off `history.state` (not `router.getCurrentNavigation()` — that API is only populated during the synchronous navigation transaction, which doesn't hold here since `ChatShellComponent`, not a `RouterOutlet`, mounts this component reactively off its own route subscriptions; confirmed via live Playwright verification that the `getCurrentNavigation()`-based read silently never fired) and runs a bounded (20-page) effect-driven "keep calling `loadOlderMessages` until the target surfaces or `hasMore` is false" loop, reusing the existing cache signal rather than a hand-rolled poll. `message-thread.component.ts` gained `highlightMessageId`/`highlightQuery` inputs driving a `scrollIntoView` + finite 2-iteration `.chat-flash` background pulse (`prefers-reduced-motion`-aware) plus the persistent `<mark>`. PLAN.md amendment signed off by `software-architect`; final result reviewed by `design-system-ui-ux` (no changes needed). Both pieces verified live via Playwright (logged in via MailHog-retrieved login codes, since this app has no password auth): the redesign was screenshotted in dark mode against the reference description; the highlight/jump feature was verified by seeding a real message containing "relatório", searching for it, confirming the result-row mark, and confirming the click navigates + persistently marks the substring in the bubble (the "not yet loaded" load-older-loop path is covered by dedicated unit tests, not re-verified live — no seeded conversation with enough history was available in this session). 979/979 frontend tests green (28 new), `format:check`/`build`/`lint` clean for both commits. |

**As of the last working session:** test suite speed (`forkCount=2` +
JTE precompiled-templates fix, see `DECISIONS.md`), all six backend
features above, and `navigation-menu`/`welcome-screen` on the frontend
are done. Next: user management screens (see "Next up" above) — write
its SPEC(s) first, split by subproject per the "Feature SPEC placement"
rule.

## Known operational/tooling notes worth knowing

### Backend (`knowly-api/`)

- Maven Surefire is deliberately configured for **full isolation per test
  class** (`reuseForks=false`, `spring.test.context.cache.maxSize=1`) —
  this was A/B tested live: disabling it to speed up the suite produced
  flaky failures (shared Redis captcha counters, cross-test-class DB
  collisions from context reuse). Keep `reuseForks=false` as-is unless
  re-validated. `forkCount=2` (concurrent isolated forks) was re-validated
  2026-07-25 — full suite ~14m10s → ~12m (two clean runs); `forkCount=4`
  was rejected (no further speedup, intermittent JTE template-compile race
  — see `DECISIONS.md`). **Re-measured 2026-07-28** (`tenant-pagination-search`,
  377 tests, real elapsed clocked end-to-end): **~21 minutes** — the ~12m
  figure above no longer holds as the suite has grown since 2026-07-25;
  treat "~12 minutes" as stale and budget closer to ~20+ minutes for a
  full `./mvnw verify` going forward. Further speedup would need reducing
  per-class Spring Boot context startup cost itself (~20-25s/class), not
  just more parallelism — not yet attempted.
- Tests must run with `gg.jte.use-precompiled-templates: true` /
  `development-mode: false` (`src/test/resources/application-test.yaml`),
  not main's dev-mode hot-reload — see `DECISIONS.md` for why (CWD-shared
  on-demand compile directory races under concurrent Surefire forks).
- `compose.yaml`'s `minio` service depends on a one-shot
  `minio-init-permissions` container to `chown` its data volume — MinIO's
  own entrypoint does not do this itself under the hardened
  `cap_drop: ALL` + non-root setup this project uses.
- **Local observability**: `compose.yaml`'s `grafana-lgtm` service
  (Grafana + Prometheus + Loki + Tempo, `127.0.0.1:3000`) already
  receives real `knowly` metrics/traces/logs once `./mvnw spring-boot:run`
  is up — see `observability-stack` in the feature table above and
  `specify/features/observability-stack/`. Local dev only; not
  production-ready (no auth in front of Grafana, no TLS). **Dashboards
  (2026-08-05)**: the image's 3 baked-in default dashboards (RED
  classic/native histogram, JVM Overview) are permanently "No data" for
  this stack — they assume Prometheus scrape (`$instance` label, which
  OTLP push never populates) and, for the RED ones, OTel-semconv metric
  names this app doesn't emit (it emits Micrometer's own
  `http_server_requests_milliseconds_*`). Replaced with 2 hand-built
  `service_name`-keyed dashboards provisioned from
  `knowly-api/observability/grafana/dashboards/` (bind-mounted
  read-only, overriding the image's own provisioning file) — see
  `DECISIONS.md`'s "Replaced grafana-lgtm's default dashboards" entry
  and `specify/features/observability-stack/PLAN.md`'s follow-up
  section for the full root-cause and live-verification detail.
- **Resolved (2026-08-04) — was misdiagnosed as an AWS SDK regression,
  actually a missing-env-var doc gap**: a fresh `./mvnw spring-boot:run`
  previously appeared to fail at startup with a `403 Forbidden` from
  MinIO on `ArticleStorageService#ensureBucketExists`'s `headBucket`
  call. A prior pass in this same session bisected it via
  `git log -p -- pom.xml` to `06d1fac` (Dependabot
  `software.amazon.awssdk:bom` bump 2.44.9→2.49.3) and suspected an AWS
  SDK v2 checksum-behavior regression; the
  `AWS_REQUEST_CHECKSUM_CALCULATION`/`AWS_RESPONSE_CHECKSUM_VALIDATION`
  env-var workaround didn't help, which was actually the tell that the
  bisection was a false correlation. **Actual root cause, confirmed via
  wire-level HTTP logging** (`org.apache.hc.client5.http.wire=DEBUG`):
  the outgoing `HEAD /knowly-articles` request's `Authorization` header
  literally read `Credential=${MINIO_ROOT_USER}/...` — Spring resolved
  `application.yaml`'s `knowly.storage.access-key: ${MINIO_ROOT_USER}`
  placeholder to the raw, unresolved string, because `MINIO_ROOT_USER`/
  `MINIO_ROOT_PASSWORD` were never actual OS environment variables in
  that shell — only present in `.env`, which `docker compose`
  auto-loads for the *containers* it starts but never exports into a
  plain `./mvnw spring-boot:run` process. MinIO correctly rejected the
  literal placeholder as an invalid access key
  (`X-Minio-Error-Code: InvalidAccessKeyId`), which the AWS SDK surfaces
  as a generic `403 Forbidden` with no body (HEAD responses have none),
  masking the real cause. `mc stat`/`mc ls` worked because `mc`'s own
  config carries real, separately-configured credentials, not this
  app's Spring property resolution. Confirmed fixed: with
  `set -a; source knowly-api/.env; set +a` run before `./mvnw
  spring-boot:run`, the app starts cleanly (`Started KnowlyApplication`
  in ~14s, no exceptions, bucket already existed so `headBucket`
  succeeded silently). No source/`pom.xml` change was needed or made —
  the AWS SDK bump is unrelated and was not reverted. Fix applied:
  `knowly-api/README.md`'s "Starting the development environment" step
  3 now explicitly documents sourcing `.env` into the shell before
  `./mvnw spring-boot:run`, since that requirement was previously
  undocumented and easy to silently miss (no loud failure — Spring
  substitutes the placeholder text instead of erroring, a second
  real-world case of "looks-defensive-but-doesn't-fail-closed" config,
  same class of gotcha as the `${VAR:?...}` Compose-vs-`application.yaml`
  one already recorded in `DECISIONS.md`).
- `identity-profile-model`'s `cpf`/`rg` encryption (`CpfRgEncryptionConverter`)
  has no key-rotation strategy: `users_aud` (envers audit history) mirrors
  the same ciphertext as the live column, so rotating the encryption key
  would need to also re-encrypt every historical audit row, not just
  `users`. Out of scope for that feature (appsec-reviewed, no blocking
  finding — the blind-index equality-revealing tradeoff is deliberate per
  its SPEC), but flagged here so a future key-rotation task doesn't
  discover the audit-table coupling from scratch.
- `spring.ai.vectorstore.pgvector.dimensions` is pinned explicitly (1536,
  matching `text-embedding-3-small`) in both test and production config —
  without it, Spring AI calls the real OpenAI embeddings endpoint just to
  infer the dimension at every startup.
- `tesseract-ocr` must be installed on the local dev machine (see
  `knowly-api/README.md`'s Prerequisites) for
  `ArticleExtractionListenerTest`'s OCR test to pass; the runtime Docker
  image already has it.

### Frontend (`knowly-app/`)

- Angular is **zoneless** (no zone.js) — tests must use Vitest's
  `vi.useFakeTimers()`, not Angular's `fakeAsync`/`tick`.
- Node version is pinned in `.nvmrc` (use `nvm use` before `npm test`/
  `npm run build` — running with the wrong Node major fails immediately).
- API calls always go through `/api/...`, proxied to the backend in dev
  (`proxy.conf.json`) — no open CORS on the backend side.
- `public/config.json` is gitignored and must exist for `ng serve` to boot
  cleanly (copy `public/config.example.json`); it's read at startup, not
  bundled, so adding it after `ng serve` is already running requires a
  restart — the dev server doesn't pick up a brand-new file in `public/`
  on the fly the way it does for edits to files it already knows about.
- `audit-trail-labels.ts`'s `auditActions.<action>` dictionary in
  `public/i18n/{en,pt-BR}.json` is a manually-maintained inventory of
  every `@AuditLog(action = "...")`/`AuditEventWriter` call site in
  `knowly-api` — it does **not** update itself when a new action string
  is added backend-side. Found stale by 41 missing keys (2026-08-04,
  manual Playwright exploration of the staff audit trail panel); the
  fallback degrades to the raw dotted action string rather than an
  untranslated-key literal, so it's easy to miss visually. Re-diff
  `grep -rhoE '\baction\s*=\s*"[a-zA-Z0-9_.]+"' knowly-api/src/main/java`
  against the `auditActions` keys in both locale files whenever an
  `@AuditLog` call site is added.
- Any tenant-scoped page (`/articles`, `/conversations`, and future ones
  in the same shape) must branch on `ActiveTenantService`'s
  `activeTenantResolved()`/`activeTenantId()` the same way
  `DashboardWrapperPageComponent`/`UserManagementPageComponent` do — a
  staff session with zero tenant memberships is deliberately let through
  by `tenantSelectionGuard` (see its own doc comment), and a component
  that only starts loading once `activeTenantId()` is non-null will hang
  on its "…" loading state forever instead of showing anything. Found
  this way in both `ArticlesPageComponent` and `ConversationsPageComponent`
  (2026-08-04, manual Playwright exploration as staff); fixed with a new
  shared `NoActiveTenantStateComponent`.
- `core/global-permission.ts`'s `GlobalPermission` type/`ALL_GLOBAL_PERMISSIONS`
  is a hand-maintained mirror of `knowly-api`'s `GlobalPermission` enum, same
  staleness risk as the `auditActions` dictionary above — no compiler or test
  catches it drifting since the frontend just treats every value as an opaque
  string. Found badly stale (2026-08-04, manual Playwright test of "grant a
  permission to a staff user"): only 11 of the real 27 backend values were
  modeled, plus 3 renamed/removed legacy names (`*_MANAGE_ANY`) that don't
  exist server-side at all. Since `StaffUserDetailPanelComponent` renders its
  permission-grant toggles off `ALL_GLOBAL_PERMISSIONS`, this meant a
  `STAFF_ADMIN` could not grant 16 real permissions to another staff user
  through the UI — the toggle simply didn't exist. Fixed by rewriting the
  type/array to match the backend enum exactly (all 27 i18n labels already
  existed in `en`/`pt-BR`, untouched). Re-diff
  `knowly-api/src/main/java/br/com/conectabyte/knowly/tenancy/GlobalPermission.java`
  against this file whenever the backend enum changes. Note this contradicts
  `staff-global-dashboard`'s row above, which recorded `appsec` confirming the
  two matched exactly (2026-07-28) — they did at the time; nothing re-checked
  it as more permissions were added since.
- `MemberDetailPanelComponent`'s direct-permission-grant toggles were
  hard-disabled for any viewer who isn't that tenant's own `MEMBER_ADMIN`,
  even though backend `TenantService#grantPermission`/`revokePermission`
  (`requireAdminOfTenantOrStaff`) also accepts a staff caller holding the
  global `TENANT_PERMISSION_GRANT_CREATE`/`_DELETE` permission — a
  `STAFF_ADMIN` acting as a tenant (no real `TenantMembership` row) was
  locked out of granting tenant permissions entirely. Confirmed by replaying
  the exact POST the UI would send, which the backend accepted with a 200.
  Fixed (2026-08-04) with a `viewerCanManageDirectPermissions` computed
  checking `GlobalPermissionsService` too. If a similar "staff bypass"
  permission check is added to a new tenant-scoped admin action, check
  whether it needs the same treatment — `viewerCanDelete` in this same file
  already had the analogous `permissionsService.has('TENANT_MEMBER_MANAGE')`
  fallback, so this class of gap isn't universal, just missed here.
- Chat/support-channel pagination cursors (`ChatCursor`, backend) are
  `base64(String.valueOf(id))`, opaque by design — but the backend's own
  `nextCursor` in `ChatMessagePageDto` only ever points at the *oldest* end
  of whatever page it just returned (used for `before`/"load older"), never
  the newest. `chat.service.ts`/`support.service.ts` mint their own "newest"
  cursor client-side from a known message id for polling/optimistic-send, and
  both were sending the raw id string instead of encoding it — every
  `pollNewMessages` tick 400'd (`CHAT_INVALID_CURSOR`), forever, silently
  (the initially-loaded/just-sent message was already visible from the
  original response, masking that live updates from a peer never arrived).
  Found by sending a real message between two tenant members end-to-end via
  Playwright. Fixed with a shared `encodeMessageCursor()` helper
  (`btoa(String(id))`) in both services — if a third chat-shaped feature is
  added, it needs the same helper, not a raw `String(id)`.
- `<select [value]="...">` silently fails to apply the bound value on an
  element's very first change-detection pass when its `<option>`s come from
  an `@for` — Angular evaluates the parent's own bindings before the
  `@for`-generated children exist yet. Already identified and fixed once for
  `profile-fields-form.component.ts`'s `countryCode` select (see that file's
  "Bugfix" comments); found unfixed a second time (2026-08-04) on the same
  file's contact-`type` select and on `contacts-list-editor.component.ts`'s
  (tenant-creation's admin-contacts editor) equivalent — both always showed
  the *first* `<option>` regardless of the contact's real stored type. Purely
  a display bug (submission reads the tracked local value/FormControl, never
  the DOM), but confusing to look at, and the first-option-happens-to-match
  case (`PHONE`/`EMAIL` respectively) is exactly why it went unnoticed. Fixed
  both the same way as the earlier `countryCode` fix: `[selected]` per
  `<option>` instead of `[value]` on the `<select>`. **Any new `<select>`
  whose `<option>`s come from an `@for`/`*ngFor` in this codebase must use
  this pattern from the start, not `[value]` on the `<select>` itself.**
- A backend endpoint returning `ResponseEntity.ok().build()` (or any genuinely
  empty-bodied 2xx) gets parsed by Angular's `HttpClient` as `null` — not
  `{}`. A success handler written as `.pipe(catchError(() => of(null)))` +
  `.subscribe((result) => { if (result !== null) {...} })` therefore can
  never fire its success branch against the real backend: a caught error and
  a genuine empty-body success are both `null` and indistinguishable. Found
  in `MembersPageComponent#onAddMember` (2026-08-04) — this file's own
  `confirmRemoval` already has the correct pattern (`catchError` returns
  `EMPTY`, `.subscribe(() => {...})` with no argument, so `next()` only ever
  fires on genuine success). **When wiring up a void/empty-body endpoint,
  copy that pattern, not `of(null)` + a null check** — and if writing a test
  for one, flush `null`, not `{}`, to actually exercise this (an object-
  literal `{}` flush parses as `{}`, which happily satisfies a broken
  `!== null` check and hides the bug).

### Monorepo / CI

- The two subprojects each get their own path-filtered GitHub Actions
  workflow (`ci-backend.yml`, `ci-frontend.yml`) plus a shared
  `codeql.yml` — none of these block each other; a change to
  `knowly-app/**` never triggers the backend's Maven build, and CodeQL
  runs as its own independent workflow, not a step the build waits on.
