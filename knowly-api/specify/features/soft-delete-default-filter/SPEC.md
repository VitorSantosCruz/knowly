# SPEC — Soft-delete default filter

> The what and the why. No technical implementation details.

## Context and motivation

A real incident shipped to production: `ChatEligibilityService` and
`ChatConversationService` used unfiltered `userRepository.findAll()` /
`findById()` instead of the ad-hoc `*DeletedAtIsNull` filtered methods
that happen to exist on some repositories, and as a result soft-deleted
users remained reachable in chat. The root cause is structural, not a
one-off typo: soft-delete filtering today is **opt-in per query**, left
to whichever developer happens to remember the `*DeletedAtIsNull` naming
convention exists and use it — exactly the failure mode this project
already refuses to accept for tenant isolation (see `DECISIONS.md`,
"Multi-tenancy is enforced at the ORM layer, fails closed").

This feature makes soft-delete filtering a **standing default** for
every soft-deletable entity, enforced at the ORM layer, the same way
tenant isolation already is — so that "forgetting" to filter out deleted
rows stops being possible for any standard entity-load-time query,
rather than remaining a habit every new query has to individually
remember.

This is a backend-only, cross-cutting architectural change. It has no
new user-facing behavior and no new endpoint — its "user" is effectively
every current and future code path in `knowly-api` that queries one of
the 13 entities listed below, and every developer (or AI agent) writing
such a code path in the future.

## User stories

- As a `developer adding a new query against a soft-deletable entity`, I
  want `soft-deleted rows to be excluded automatically, with no
  per-query opt-in required`, so that `it is structurally impossible to
  accidentally leak a soft-deleted row the way the chat-eligibility bug
  did`.
- As a `code reviewer`, I want `soft-delete exclusion to be visible on
  the entity itself, not scattered across individual repository method
  names`, so that `I can verify the safeguard exists in one place per
  entity instead of auditing every query against it`.
- As a `developer with a legitimate, specific need to see soft-deleted
  rows` (e.g. a staff oversight query or a future restore/reactivate
  flow), I want `an explicit, deliberate way to disable the exclusion for
  that one call site`, so that `legitimate cases remain possible without
  weakening the default for everything else, and without every other
  query having to opt out to get it`.

## Requirements (EARS/GEARS)

1. **[Ubiquitous]** The persistence layer shall exclude rows where
   `deletedAt` is not null from the result of any standard JPA entity
   query (derived query method, JPQL `SELECT`, or entity-load-by-id) for
   each of the following 13 entities: `UserProfile`, `Contact`,
   `Address`, `User`, `Conversation`, `Tenant`, `AccessGroup`,
   `TenantMembership`, `AccessGroupPermission`, `UserAccessGroup`,
   `UserGlobalAccessGroup`, `DirectPermissionGrant`,
   `DirectGlobalPermissionGrant`.

2. **[Ubiquitous]** The soft-delete exclusion described in requirement 1
   shall be enabled by default on every request/session automatically —
   unlike the existing tenant `@Filter`, which requires each request to be
   explicitly enabled by `TenantFilterAspect`, the soft-delete exclusion
   requires no action from the developer to be active; it is on unless a
   specific call site deliberately disables it per requirement 7.

3. **[Event-Driven]** When a developer writes a new derived-query method,
   custom JPQL query, or `findById`/`findAll`-style call against any of
   the 13 entities listed in requirement 1, the system shall exclude
   soft-deleted rows from that query's results without the developer
   needing to add any `*DeletedAtIsNull`-style suffix or manual
   `deletedAt IS NULL` predicate.

4. **[Ubiquitous]** The system shall continue to enforce tenant isolation
   (the existing `TenantFilter`/`TenantFilterAspect` mechanism) unchanged
   and independently of the soft-delete exclusion — both restrictions
   shall apply simultaneously on any entity that carries both (e.g.
   `Conversation`), such that a row is only returned when it passes both
   the active tenant's scope and the soft-delete exclusion.

5. **[Ubiquitous]** The system shall continue to record every entity
   revision (create, update, soft-delete) in Hibernate Envers' `_AUD`
   history tables for every entity currently `@Audited`, unaffected by
   the soft-delete exclusion — a soft-deleted row shall remain fully
   reconstructable through its audit history even though it is no longer
   returned by live entity queries.

6. **[Unwanted Behavior]** If a query against one of the 13 entities is
   executed through a native SQL query or a bulk `@Modifying` JPQL query
   (`UPDATE`/`DELETE` issued directly, bypassing entity-load-time
   mechanics), then the soft-delete exclusion described in requirement 1
   is **not guaranteed to apply** — this is a known, explicitly
   acknowledged gap (see "Out of scope"), not a regression introduced by
   this feature.

7. **[Optional Feature]** Where a legitimate need exists to read
   soft-deleted rows for one of the 13 entities (e.g. staff oversight, a
   restore/reactivate flow), the system shall provide a mechanism to
   deliberately and explicitly disable the soft-delete exclusion for that
   specific call site only (e.g. by naming the filter and disabling it on
   the current session for the duration of that call), leaving the
   exclusion active for every other concurrent and subsequent query —
   never a silent, blanket, or implicit bypass of requirement 1.

8. **[Unwanted Behavior]** If an entity currently has a repository method
   named with a `*DeletedAtIsNull` (or equivalent manual-filter) suffix
   for one of the 13 entities, then that method's name shall be
   simplified to drop the now-redundant suffix once requirement 1 makes
   the exclusion automatic, without changing the set of rows the method
   returns.

## Non-functional requirements

- Security: this feature is itself a security/data-integrity hardening
  measure — its entire purpose is closing a data-leak class of bug (soft-
  deleted, no-longer-valid rows remaining reachable). It must not weaken
  or bypass the existing tenant-isolation `@Filter` mechanism in any way
  (see requirement 4).
- Performance/SLA: no new query round-trips are introduced; the
  exclusion is expressed as an additional SQL predicate appended by
  Hibernate at query-build time (equivalent in cost to the manual
  `deletedAt IS NULL` predicates it replaces).
- Observability: no new logging/audit requirement beyond what Envers
  already provides (requirement 5); no PII or new data exposure is
  introduced by this change.

## Acceptance criteria

- [ ] A test demonstrates that a specific call site can deliberately
      disable the soft-delete exclusion for its own query only, that the
      disabled query then returns soft-deleted rows, and that the
      exclusion remains active for every other concurrent/subsequent
      query in the same and other requests.
- [ ] For each of the 13 named entities, a standard JPA `findById`,
      derived-query method, or JPQL `SELECT` against a row with a
      non-null `deletedAt` returns no result (empty/`Optional.empty()`/
      excluded from a list), with no per-query opt-in code required to
      achieve this.
- [ ] The specific bug scenario that motivated this feature —
      `ChatEligibilityService`/`ChatConversationService` querying a
      soft-deleted `User` — no longer reproduces: a soft-deleted user is
      not reachable via chat eligibility/conversation queries.
- [ ] On an entity carrying both the tenant `@Filter` and the new
      soft-delete exclusion (e.g. `Conversation`), a query returns a row
      only when it is both in-tenant and not soft-deleted; existing
      tenant-isolation tests continue to pass unchanged.
- [ ] Existing Envers `_AUD` history queries for any of the 13 entities
      continue to return the full revision history of a row, including
      revisions recorded after it was soft-deleted.
- [ ] Repository methods across the ~11 affected repositories that
      previously used a `*DeletedAtIsNull`-style name are renamed to
      drop the now-redundant suffix, and all call sites are updated
      accordingly, with no change in the rows returned.
- [ ] The full backend regression suite (`./mvnw verify`) passes with no
      newly introduced failures.
- [ ] Native/bulk-query call sites in `ArticleRepository`,
      `UserRepository`, `ActiveMemberSnapshotRepository`,
      `MessageRepository`, `MessageArticleCitationRepository`,
      `TenantRepository`, `ConversationRepository`, and
      `TenantMembershipRepository` are explicitly enumerated as a
      follow-up audit item (tracked separately, not silently assumed
      covered by this feature) rather than left unmentioned.

## Out of scope

- Auditing or fixing individual native SQL / bulk `@Modifying` JPQL
  queries in `ArticleRepository`, `UserRepository`,
  `ActiveMemberSnapshotRepository`, `MessageRepository`,
  `MessageArticleCitationRepository`, `TenantRepository`,
  `ConversationRepository`, `TenantMembershipRepository`, or any other
  repository — this SPEC only covers standard JPA entity-load-time
  queries (requirement 1). Native/bulk queries need individual, explicit
  auditing as a separate follow-up feature.
- Building any actual "see deleted rows" oversight/restore feature or
  UI. Requirement 7 only states that such a mechanism, if and when
  needed, must be explicit and deliberate — it does not design or
  implement one.
- Adding soft-delete (a `deletedAt` column) to any entity that doesn't
  already have one. This SPEC only changes how the existing 13
  soft-deletable entities are queried, not which entities are
  soft-deletable.
- Any change to how or when a row's `deletedAt` gets set (the soft-
  delete write path itself). This SPEC is about read-side filtering
  only.
- Any change to Envers configuration, `_AUD` table schema, or audit
  history retention.
- Any change to the tenant `@Filter`/`TenantFilterAspect` mechanism
  itself, beyond confirming it continues to coexist correctly with the
  new soft-delete exclusion.
