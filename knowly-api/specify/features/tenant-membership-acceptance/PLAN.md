# PLAN — tenant-membership-acceptance

> The how. Translates the SPEC into concrete technical decisions.
> References SPEC.md.

## Architectural decisions

- **`TenantMembership` gains a new `status` enum column
  (`MembershipStatus`: `PENDING`, `ACTIVE`, `DECLINED`) instead of
  overloading the existing `boolean active`.** The existing `active`
  column is kept, unchanged in type, and is made a derived mirror of
  `status == ACTIVE` — every write path that sets `status` also sets
  `active` in the same setter call, so `findByUserAndActiveTrue`,
  `findByTenantIdAndActiveTrue`, `countByTenantIdAndActive`, and
  `PermissionAspect.requireActiveMembership`'s
  `.filter(TenantMembership::isActive)` all keep working with **zero
  changes**, correctly excluding `PENDING`/`DECLINED` rows without
  touching a single one of those call sites. Rationale for a new enum
  rather than reusing `active` alone: `active=false` today means exactly
  one thing ("removed"); this feature needs to distinguish three states
  (never-yet-accepted / active / declined-forever) and REQ-7/REQ-13 both
  depend on knowing *which* of the two non-active states a row is in
  (declined rows must never silently reactivate; removed rows —
  `active=false` with no `status` change, see below — must also require a
  fresh invitation per existing `removeMember` behavior, which this
  SPEC's Out-of-scope/acceptance-criterion 11 says is already correct and
  untouched). Concretely: `removeMember` continues to only flip `active`
  (not `status`) — a removed-then-reinvited row goes through `addMember`
  exactly as it does today, which per REQ-13 must reset `status` to
  `PENDING` regardless of the row's prior `status` value. This makes
  `status` purely additive: nothing today reads it except the two new
  code paths this feature adds (notification creation gating and the
  accept/decline endpoints).
- **`isActive()` stays the single source of truth for "does this
  membership grant anything," matching `active`'s existing role** — no
  change to `PermissionAspect`, `requireActiveMembership`,
  `resolveSessionOutcome`, `listOwnMemberships`, or any repository
  method. This directly satisfies REQ-2/REQ-3 (a pending row grants
  nothing, a `STAFF`/`STAFF_ADMIN` user's bypass and any separately-held
  active membership are untouched) without new code, since none of that
  logic today branches on `status` at all — it only ever asks
  `active`.
- **REQ-1a detection: `addMember` needs to know whether the `User` row
  was just created by this call or already existed.** Today's code
  collapses that into one expression:
  `userRepository.findByEmailIgnoreCase(email).orElseGet(() ->
  userRepository.save(new User(email)))`. The minimal change is to stop
  collapsing it — look up first, and branch explicitly:
  ```java
  Optional<User> existingUser = userRepository.findByEmailIgnoreCase(email);
  boolean userAlreadyExisted = existingUser.isPresent();
  User user = existingUser.orElseGet(() -> userRepository.save(new User(email)));
  ```
  `userAlreadyExisted` is then the exact boolean REQ-1/REQ-1a is
  conditioned on. This is a **Tier 1** change — same lookup-or-create
  behavior, only the intermediate `Optional` is no longer discarded.
- **`addMember`'s membership branch, using `userAlreadyExisted`:**
  - `userAlreadyExisted == false` → unchanged from today: create/reuse
    the membership row, `status = ACTIVE`, `active = true`, no
    notification. (REQ-1a, and matches `createTenant`'s untouched
    founding-admin path, which also creates a brand-new `User` and never
    goes through `addMember` at all.)
  - `userAlreadyExisted == true` → `status = PENDING`, `active = false`,
    regardless of the membership row's prior `status`/`active` value if
    one already existed (REQ-1, REQ-13 — re-invitation after decline or
    removal always resets to pending, never silently reuses a stale
    `ACTIVE` or `DECLINED` value). Then create the
    `MEMBERSHIP_INVITATION_PENDING` `Notification` (REQ-4) addressed to
    the invited `User`, referencing the saved membership.
  - The pre-existing `.orElseGet(() -> new TenantMembership(user, tenant,
    role))` lookup-or-create-membership pattern is unchanged — REQ-13's
    "re-create it in pending state" is satisfied by resetting `status`
    on the same row (same unique `(user_id, tenant_id)` constraint),
    not by inserting a second row, consistent with how `active` already
    gets flipped back to `true` on the existing row today.
- **New `Notification` entity** (`br.com.conectabyte.knowly.tenancy`,
  same package as `TenantMembership` since it's keyed to it): recipient
  `User`, `NotificationType` enum (`MEMBERSHIP_INVITATION_PENDING`,
  `MEMBERSHIP_INVITATION_ACCEPTED`), a `TenantMembership` reference,
  `resolved` boolean (default `false`), `createdAt`/audit columns
  matching every other entity's `@CreatedDate`/`@CreatedBy` convention.
  No `@Filter`/tenant-scoping: a notification's own authorization is
  "is this row's recipient the caller" (REQ-10), not tenant membership —
  the referenced `TenantMembership` itself remains subject to
  `TenantFilter` as always, so accept/decline logic must resolve that
  membership through a normal tenant-scoped repository call (see below).
  Not `@Audited`/Envers — this is ephemeral, user-facing state (read/
  resolve, not a business record needing historical revision tracking),
  consistent with how none of `Notification`'s siblings-in-spirit (e.g.
  session/context state) get Envers either; the underlying
  `TenantMembership` state transitions this notification exists to
  surface are already fully audited via `@AuditLog`, which is what
  REQ's "Observability" NFR actually requires.
- **Accept/decline logic lives in a new `NotificationService`** (not
  bolted onto `TenantService`) — REQ-8/9/10/11 are about a distinct
  resource (the caller's own notifications), not tenant-membership
  management proper, and REQ-10's authorization (recipient identity, not
  a `Permission`) doesn't fit `PermissionAspect`'s
  `@RequiresPermission`/tenant-membership model at all. `TenantService`
  gains no new public methods for this; `NotificationService` calls
  `TenantMembershipRepository` directly for the referenced membership's
  status transition (still passes through `TenantFilter` normally, since
  the caller's tenant context is irrelevant here — the recipient check
  alone is REQ-10's stated gate, and the membership being resolved by ID
  through the normal repository still fails closed per the existing
  filter if, hypothetically, no tenant context were active; in practice
  the accept/decline endpoints require an authenticated session but not
  an *active tenant selection*, since REQ-8's whole point is a
  staff/any-user "my notifications" inbox that must work before any
  tenant is selected — see Data schema note below on why the
  `Notification`→`TenantMembership` FK read has to bypass
  `TenantFilter` deliberately for this one query).
  - **Real constraint found while reading `TenantFilterAspect`:** it
    enables `TenantFilter` strictly from `TenantContext.getActiveTenantId()`
    (session state) on every `@Transactional` service method — there is
    no per-call override. A user accepting/declining an invitation into
    a tenant they haven't switched into yet (the normal case — they're
    being invited *into* it) would have no active tenant id; for a
    non-staff caller that means the filter enables with
    `NO_ACTIVE_TENANT_SENTINEL` and **fails closed**, returning nothing
    even for the invitee's own pending row. This is not a new problem —
    `TenantService.resolveSessionOutcome`/`listOwnMemberships`/
    `requireActiveMembership` already hit exactly this, and the existing,
    documented fix (see their javadoc) is to make methods that are scoped
    by **caller identity** rather than **an already-active tenant
    selection** deliberately **not** `@Transactional`, so
    `TenantFilterAspect` never wraps them and the query runs through
    Spring Data's own default per-call transaction instead — the same
    established precedent, reused here rather than reinvented: no new
    isolation exemption, no Tier 3 call needed. `NotificationService`'s
    `listMine`, `accept`, and `decline` all resolve the referenced
    `TenantMembership` via a plain (non-aspect-wrapped, non-
    `@Transactional`) repository call scoped by `(notification.recipient
    == caller)` first, exactly mirroring `requireActiveMembership`'s
    shape — the membership row is then loaded and mutated within its own
    lookup, not inside a filter-gated `@Transactional` boundary. Tenant
    isolation itself is not weakened: the *only* additional thing this
    unlocks is "the caller can see/mutate a specific membership row that
    is provably their own, before selecting that tenant" — never
    cross-tenant listing or another user's data.
- **New endpoints (`NotificationController`, `/api/notifications`)**
  follow `TenantController`'s existing shape 1:1 (record DTOs,
  `ResponseEntity<...>`, `currentUser()` resolved from
  `SecurityContextHolder`, `@AuditLog` on the two state-changing calls).
- **`removeMember` needs no changes** — verified by reading it: it
  already does exactly REQ-12's soft-deactivation
  (`membership.setActive(false)`, `@AuditLog`, gated by
  `requireAdminOfTenantOrStaff`), independent of `status`. This PLAN
  does *not* make `removeMember` touch `status` — a removed row's
  `status` is left as whatever it was (typically `ACTIVE`, now
  semantically stale next to `active=false`); this is fine because every
  active-membership check anywhere in the codebase reads `active`, never
  `status`, and REQ-13's "reset to pending on re-invite" is handled
  entirely in `addMember`, not `removeMember`.
- **Audit logging:** `addMember` already carries `@AuditLog(action =
  "tenant.member.add", ...)` — unchanged, now implicitly covers "pending
  membership created" (its one method, still one action name; the
  `PENDING` vs. `ACTIVE` outcome is visible in `AuditEvent`'s existing
  before/after via the saved entity's `status`, no new action name
  needed). `NotificationService.accept`/`NotificationService.decline`
  each get a new `@AuditLog` (`notification.membership.accept` /
  `notification.membership.decline`, `resourceType =
  "TenantMembership"`), satisfying the SPEC's "every state transition
  this feature introduces" NFR for the two transitions `addMember`
  itself doesn't cover.

## Data schema

`TenantMembership`:
- New column `status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'`
  (`@Enumerated(EnumType.STRING)`, mirrors `role`'s existing pattern).
- `active` column unchanged (still `BOOLEAN NOT NULL DEFAULT true`).

New entity `Notification` / table `notifications`:
```sql
CREATE TABLE notifications (
  id BIGSERIAL PRIMARY KEY,
  recipient_user_id BIGINT NOT NULL REFERENCES users (id),
  type VARCHAR(40) NOT NULL,
  tenant_membership_id BIGINT NOT NULL REFERENCES tenant_memberships (id),
  resolved BOOLEAN NOT NULL DEFAULT false,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by VARCHAR(255) NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by VARCHAR(255) NOT NULL
);

CREATE INDEX idx_notifications_recipient_unresolved
  ON notifications (recipient_user_id) WHERE NOT resolved;
```

New migration `V16__create_notifications_and_membership_status.sql`:
```sql
-- tenant-membership-acceptance: adds pending/accept/decline state to
-- TenantMembership (see specify/features/tenant-membership-acceptance/PLAN.md).
-- `status` is additive alongside the existing `active` boolean, which stays
-- the single source of truth every authorization check already reads
-- (PermissionAspect, findByUserAndActiveTrue, etc.) — untouched by this
-- migration or this feature.
ALTER TABLE tenant_memberships ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';

-- Explicit backfill (not relying on the column DEFAULT alone for existing
-- rows) so every pre-existing row's status is unambiguously ACTIVE,
-- matching its current `active = true`/`false` reality at migration time:
-- a currently-inactive (removed) row is backfilled to ACTIVE too, since
-- `status` has never meant anything for removed rows until this feature and
-- `active` remains the only column any removal-check reads.
UPDATE tenant_memberships SET status = 'ACTIVE';

ALTER TABLE tenant_memberships_aud ADD COLUMN status VARCHAR(20);
UPDATE tenant_memberships_aud SET status = 'ACTIVE';

CREATE TABLE notifications (
  id BIGSERIAL PRIMARY KEY,
  recipient_user_id BIGINT NOT NULL REFERENCES users (id),
  type VARCHAR(40) NOT NULL,
  tenant_membership_id BIGINT NOT NULL REFERENCES tenant_memberships (id),
  resolved BOOLEAN NOT NULL DEFAULT false,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by VARCHAR(255) NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by VARCHAR(255) NOT NULL
);

CREATE INDEX idx_notifications_recipient_unresolved
  ON notifications (recipient_user_id) WHERE NOT resolved;

CREATE TABLE notifications_aud (
  id BIGINT NOT NULL,
  rev BIGINT NOT NULL REFERENCES revinfo (rev),
  revtype SMALLINT,
  recipient_user_id BIGINT,
  type VARCHAR(40),
  tenant_membership_id BIGINT,
  resolved BOOLEAN,
  created_at TIMESTAMPTZ,
  created_by VARCHAR(255),
  updated_at TIMESTAMPTZ,
  updated_by VARCHAR(255),
  PRIMARY KEY (id, rev)
);
```
Note: `Notification` is **not** `@Audited` (see rationale above), so
`notifications_aud` is created for schema-parity/future-proofing
consistency with this codebase's existing 1-migration-per-feature Envers
convention only if a reviewer wants Envers on it later; **flagging this
as a Tier 2 judgment call** — the simpler, and recommended, alternative
is to drop the `notifications_aud` table entirely from V16 and skip
`@Audited` review overhead now, since nothing in the SPEC's NFRs asks
for historical revision tracking of notifications themselves (only for
the `TenantMembership`/`AuditEvent` transitions, already covered). This
PLAN recommends **omitting `notifications_aud` and `@Audited`
entirely** — TASKS.md reflects the simpler (recommended) version;
revisit only if audit review actually asks for it.

## API contracts

New `NotificationController`, `/api/notifications`:

| Method | Path | Request | Response | Status codes |
|---|---|---|---|---|
| GET | `/api/notifications` | — | `List<NotificationDto>` (unresolved only) | 200 |
| POST | `/api/notifications/{id}/accept` | — | — | 200, 403 (not recipient — REQ-10), 409 (membership no longer pending — REQ-11), 404 (no such notification) |
| POST | `/api/notifications/{id}/decline` | — | — | 200, 403, 409, 404 |

`NotificationDto`: `record NotificationDto(Long id, NotificationType
type, Long tenantMembershipId, Long tenantId, String tenantName,
boolean resolved, Instant createdAt)` — mirrors `MemberDto`'s
`from(entity)` static-factory convention.

`addMember`'s existing endpoint contract
(`POST /api/tenants/{tenantId}/members`) is unchanged in shape (still
`204`/`200` no body) — only its *effect* changes per REQ-1/REQ-1a,
which is invisible at the HTTP contract level (the inviter doesn't
learn pending-vs-active synchronously here; the SPEC doesn't ask for
that and it's out of scope to add it now).

## Dependencies

None new (backend `pom.xml` unchanged) — no RabbitMQ/queue involvement
either, since REQ's out-of-scope explicitly excludes push/websocket
delivery; the notification list is plain pollable REST.

## Package/file structure

- `src/main/java/br/com/conectabyte/knowly/tenancy/TenantMembership.java` (modify: add `status` field + `MembershipStatus`)
- `src/main/java/br/com/conectabyte/knowly/tenancy/MembershipStatus.java` (new enum)
- `src/main/java/br/com/conectabyte/knowly/tenancy/TenantService.java` (modify: `addMember`'s `userAlreadyExisted` branch)
- `src/main/java/br/com/conectabyte/knowly/tenancy/Notification.java` (new entity)
- `src/main/java/br/com/conectabyte/knowly/tenancy/NotificationType.java` (new enum)
- `src/main/java/br/com/conectabyte/knowly/tenancy/NotificationRepository.java` (new)
- `src/main/java/br/com/conectabyte/knowly/tenancy/NotificationService.java` (new)
- `src/main/java/br/com/conectabyte/knowly/tenancy/NotificationController.java` (new)
- `src/main/java/br/com/conectabyte/knowly/tenancy/dto/NotificationDto.java` (new)
- `src/main/java/br/com/conectabyte/knowly/tenancy/exception/NotificationAlreadyResolvedException.java` (new, maps to 409 — REQ-11)
- `src/main/resources/db/migration/V16__create_notifications_and_membership_status.sql` (new)

## Testing strategy

- Unit: `TenantServiceTest` — `addMember` for an existing `User` creates
  `PENDING`/`active=false` + a `MEMBERSHIP_INVITATION_PENDING`
  notification (REQ-1, REQ-4); `addMember` for a brand-new email creates
  `ACTIVE`/`active=true`, no notification (REQ-1a); re-invite after
  decline/removal resets to `PENDING` (REQ-13).
- Unit: `NotificationServiceTest` — accept transitions membership to
  `ACTIVE`, resolves the invitee's notification, creates one
  `MEMBERSHIP_INVITATION_ACCEPTED` notification per distinct
  `MEMBER_ADMIN`+inviter (REQ-5, REQ-6, REQ-9, dedup case); decline sets
  `DECLINED`/`active=false`, no new notification (REQ-7, Decision #3);
  wrong-recipient rejected (REQ-10); already-resolved rejected (REQ-11).
- Integration (`@SpringBootTest`, mirrors
  `TenantManagementIntegrationTest`): pending membership grants zero
  `@RequiresPermission`-gated access (REQ-2, acceptance criterion 3);
  `STAFF`/`STAFF_ADMIN` bypass/prior-active-membership access is
  identical before/after a pending row is created for them (REQ-3,
  acceptance criterion 4); full accept flow via
  `GET /api/notifications` → `POST .../accept` → subsequent
  `@RequiresPermission` call succeeds; decline flow leaves access denied
  permanently; every transition emits the expected `AuditEvent`.
