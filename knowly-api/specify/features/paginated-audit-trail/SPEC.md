# SPEC — Paginated audit trail

## Context and motivation

`GET /api/staff/users/{userId}/audit-trail` returns every audit event for
a user as a single unpaginated `List<AuditEventDto>`. This was fine while
the frontend rendered it as a small embedded table inside a detail panel,
but a design-system consistency pass
(`knowly-app/specify/features/design-system-consistency-pass/SPEC.md`) is
turning audit trail into its own dedicated, paginated view, following the
same server-pagination shape already established by
`specify/features/tenant-pagination-search/SPEC.md`. A user with a long
history (frequent logins, many actions) would otherwise force the client
to fetch and render an unbounded list.

## User stories

- As staff viewing another staff user's or a tenant member's audit
  trail, I want it paginated like every other list in the app, so a long
  history doesn't load or render everything at once.

## Requirements (EARS/GEARS)

- **REQ-1 [Ubiquitous]** The system shall accept `page` (default 0) and
  `size` (default 20, max 100) query parameters on the audit-trail
  endpoint, mirroring `tenant-pagination-search`'s existing pagination
  contract (reject negative `page` or non-positive `size`, clamp
  oversized `size`).
- **REQ-2 [Ubiquitous]** The system shall return audit events sorted by
  `occurredAt` descending (most recent first), paginated, with a total
  element/page count in the response envelope.
- **REQ-3 [Unwanted Behavior]** If the caller lacks the same permission
  currently required to view the unpaginated endpoint, then the system
  shall deny the request identically (403), regardless of pagination
  parameters.

## Non-functional requirements

- Backward compatibility: existing callers of the unpaginated endpoint
  are only the frontend code this pass is also changing — no external
  consumers to preserve, so the endpoint's response shape may change
  directly (envelope instead of bare array) rather than needing a new
  route.
- Performance: query must be indexed on the existing
  `(actor_user_id, occurred_at)` shape already used by
  `findByActorUserIdOrderByOccurredAtDesc`.

## Acceptance criteria

- [ ] `GET /api/staff/users/{userId}/audit-trail?page=0&size=20` returns
      a paginated envelope (same shape as `PageResponseDto` used
      elsewhere), not a bare array.
- [ ] Existing permission checks are unchanged.
- [ ] Existing backend tests for this endpoint are updated to the new
      response shape, plus a new test asserting page/size behavior.

## Out of scope

- Adding the same paginated audit-trail concept for tenant members
  (`member-detail-panel`) — today only staff users have an audit-trail
  section at all; whether tenant members get one is a separate product
  decision the design-system-consistency-pass SPEC left open, not
  assumed here.
- Any change to what gets audited or how `AuditEvent` rows are written —
  purely a read/pagination change.
