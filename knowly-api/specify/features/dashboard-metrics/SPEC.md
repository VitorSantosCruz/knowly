# SPEC — Dashboard metrics

## Context and motivation

The frontend's `onboarding-dashboard` feature
(`knowly-app/specify/features/onboarding-dashboard/`) was built against
four metrics endpoints that didn't exist yet, deliberately deferred by
the `onboarding-status` SPEC until `Article`/`Conversation`/`Message`
existed in this backend. They now all exist (`article-management`,
`conversations`), so this feature closes that gap: tenant-wide article,
conversation, and message counts, plus a per-article "usage" ranking.

## User stories

- As a tenant user with dashboard access, I want to see how many
  articles exist, which ones get used most, and how much the
  conversational assistant has been used, without having to ask an
  admin or query the database directly.

## Requirements (EARS/GEARS)

- **REQ-1 [Ubiquitous]** The system shall define a `DASHBOARD_VIEW`
  permission, grantable directly or via access group, gating all four
  metrics endpoints, per the tenancy feature's deny-by-default model.
- **REQ-2 [Ubiquitous]** The system shall report the active tenant's
  total count of active (non-deleted) articles.
- **REQ-3 [Ubiquitous]** The system shall report, per active article in
  the active tenant, how many distinct assistant responses cited it,
  ranked most-cited first.
- **REQ-4 [Event-Driven]** When a chat response completes and cited one
  or more article chunks as context, the system shall record one
  citation per distinct article for that response (not one per chunk —
  an article cited via three chunks in the same answer counts once).
- **REQ-5 [Ubiquitous]** The system shall report the active tenant's
  total count of conversations started, across all of that tenant's
  users (not just the caller's own).
- **REQ-6 [Ubiquitous]** The system shall report the active tenant's
  total count of messages sent by users and messages sent by the
  assistant, as two separate counts, across all of that tenant's users.
- **REQ-7 [Unwanted Behavior]** If a caller lacks `DASHBOARD_VIEW`, then
  each metrics endpoint shall reject the request (403) independently —
  a dashboard with four widgets can show a partial "no access" state per
  widget rather than an all-or-nothing failure.
- **REQ-8 [Ubiquitous]** Every metrics read shall be logged via the
  existing `@AuditLog` mechanism, consistent with every other tenant-
  scoped read in the system.

## Non-functional requirements

- Security: every count is scoped to the caller's active tenant only —
  never leaks another tenant's article/conversation/message volume, even
  in aggregate.
- Performance/SLA: citation recording happens synchronously after a
  chat stream completes (not on the request's critical path for the
  first token), and must not block or slow down the stream itself.

## Acceptance criteria

- [x] `GET /api/tenants/metrics/articles` returns the active tenant's
      active article count (tenant resolved from the session, no
      `tenantId` in the path — matching the frontend's already-committed
      contract, same convention as `GET /api/tenants/memberships`).
- [x] `GET /api/tenants/metrics/articles/usage` returns articles ranked
      by distinct-citing-response count, most first.
- [x] A response citing the same article via multiple chunks records one
      citation for that article, not one per chunk.
- [x] `GET /api/tenants/metrics/conversations` returns the tenant-wide
      conversation count (every user's, not just the caller's).
- [x] `GET /api/tenants/metrics/messages` returns separate user-sent and
      assistant-sent counts, tenant-wide.
- [x] Each endpoint independently returns 403 for a caller without
      `DASHBOARD_VIEW`.
- [x] A tenant's counts never include another tenant's data.

## Out of scope

- Time-windowed metrics (last 7 days, etc.) — all counts are all-time
  totals for now.
- Per-user breakdowns of the tenant-wide counts (who started which
  conversation) — the dashboard shows tenant aggregates only.
- Citation UI (showing *which* article backed *which part* of an
  answer) — this feature only counts citations, it doesn't expose them
  in the chat UI.
