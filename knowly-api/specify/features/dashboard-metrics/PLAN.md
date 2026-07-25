# PLAN — Dashboard metrics

> The how. Translates the SPEC into concrete technical decisions.
> References SPEC.md.

## Architectural decisions

- New package `br.com.conectabyte.knowly.metrics`: `MetricsController`,
  `MetricsService`, DTOs. Kept separate from `article`/`conversation`
  rather than adding controller methods to those packages, since this is
  a distinct read-only reporting surface over both.
- New `Permission.DASHBOARD_VIEW` constant, added to the existing enum
  (REQ-1), same reasoning as every prior feature's permission additions.
- New entity `br.com.conectabyte.knowly.conversation.MessageArticleCitation`
  (`message` `@ManyToOne`, `article` `@ManyToOne`, `createdAt`), recorded
  by `MessageStreamingService` once a chat stream completes successfully
  — one row per distinct article id among the retrieved chunks for that
  response (REQ-4), deduped in application code before insert rather
  than relying on a DB unique constraint to silently swallow duplicates.
- Tenant-wide counts (REQ-5, REQ-6) deliberately query by an explicit
  `tenantId` parameter rather than relying solely on the Hibernate
  tenant filter, matching the project's established precedent (e.g.
  `ArticleRepository.findByTenantIdAndActiveTrue`) — explicit and
  readable at the query-method level, not implicit on whatever filter
  happens to be enabled.
- `Message` isn't itself tenant-filtered (see `conversations` PLAN.md);
  tenant-scoped message counts join through `Conversation.tenant`
  (`countByConversation_Tenant_IdAndRole`).

## Data schema

`V12__create_message_article_citations_table.sql`:

```sql
CREATE TABLE message_article_citations (
  id BIGSERIAL PRIMARY KEY,
  message_id BIGINT NOT NULL REFERENCES messages(id),
  article_id BIGINT NOT NULL REFERENCES articles(id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (message_id, article_id)
);
CREATE INDEX ix_message_article_citations_article ON message_article_citations (article_id);
```

No Envers audit table — citations are append-only, system-generated
metadata about a message, not a user-editable record (same reasoning as
`messages` itself not being Envers-audited).

## API contracts

All under `/api/tenants/metrics` — **no** `tenantId` path segment, the
active tenant is resolved server-side from the session
(`TenantContext`), same convention as `GET /api/tenants/memberships` —
behind `@RequiresPermission(DASHBOARD_VIEW)` + `@AuditLog`, matching the
exact shapes the frontend's `onboarding-dashboard` PLAN.md already
committed to (so the frontend needs zero changes):

- `GET /api/tenants/metrics/articles` → `200 { totalCount: number }`
- `GET /api/tenants/metrics/articles/usage` →
  `200 { articles: Array<{ id: number, title: string, useCount: number }> }`,
  most-cited first.
- `GET /api/tenants/metrics/conversations` →
  `200 { startedCount: number }`
- `GET /api/tenants/metrics/messages` →
  `200 { sentCount: number, receivedCount: number }` — `sentCount` is
  user-authored messages, `receivedCount` is assistant-authored ones,
  both tenant-wide.

## Dependencies

- None new — reuses existing JPA/Envers/tenancy infrastructure.

## Package/file structure

- `br.com.conectabyte.knowly.metrics`: `MetricsController`,
  `MetricsService`, `ArticleCountDto`, `ArticleUsageDto`,
  `ArticleUsageResponseDto`, `ConversationsMetricDto`, `MessagesMetricDto`.
- `br.com.conectabyte.knowly.conversation`: add `MessageArticleCitation`,
  `MessageArticleCitationRepository`; `MessageStreamingService` records
  citations in its `onComplete` callback.
- `br.com.conectabyte.knowly.article.ArticleRepository`: add
  `countByTenantIdAndActiveTrue`.
- `br.com.conectabyte.knowly.conversation.ConversationRepository`: add
  `countByTenantId`.
- `br.com.conectabyte.knowly.conversation.MessageRepository`: add
  `countByConversation_Tenant_IdAndRole`.
- `br.com.conectabyte.knowly.tenancy.Permission`: add `DASHBOARD_VIEW`.

## Testing strategy

- `MessageStreamingServiceTest` addition: a stream that retrieved chunks
  from two different articles (one via two chunks, one via one chunk)
  persists exactly two `MessageArticleCitation` rows, not three (REQ-4);
  a failed/errored stream persists none.
- `MetricsControllerIntegrationTest` (Testcontainers): each endpoint
  requires `DASHBOARD_VIEW` (403 otherwise, independently per endpoint);
  article count reflects only active articles in the active tenant;
  usage ranking orders by citation count and excludes another tenant's
  articles/citations entirely; conversation/message counts are
  tenant-wide (include another user's conversation in the same tenant)
  but never include another tenant's.
