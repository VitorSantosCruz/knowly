# TASKS — Dashboard metrics

> Atomic, sequential, verifiable tasks derived from PLAN.md.
> Each "Implement" task ends with `./mvnw spotless:apply && ./mvnw verify`
> and a small Conventional Commit before moving on.

## 0. Foundations

- [x] 1. Add `DASHBOARD_VIEW` to the `Permission` enum.
- [x] 2. `V12__create_message_article_citations_table.sql`.

## 1. Citation recording (REQ-3, REQ-4)

- [x] 3. Test: a completed stream that retrieved chunks from two
      articles (one via two chunks) persists exactly two
      `MessageArticleCitation` rows tied to the assistant message (Red).
- [x] 4. Implement citation recording in
      `MessageStreamingService#onComplete` (Green).
- [x] 5. Test: a failed/errored stream persists no citations (Red).
- [x] 6. Implement (Green) — already held given `onComplete` only runs
      on success; asserted explicitly.

## 2. Metrics endpoints (REQ-1, REQ-2, REQ-5, REQ-6, REQ-7)

- [x] 7. Test: `GET .../metrics/articles` requires `DASHBOARD_VIEW`,
      returns the active tenant's active article count, excludes other
      tenants (Red).
- [x] 8. Implement `MetricsController#articleCount` +
      `ArticleRepository#countByTenantIdAndActiveTrue` (Green).
- [x] 9. Test: `GET .../metrics/articles/usage` ranks articles by
      citation count, most first, excludes other tenants' articles
      (Red).
- [x] 10. Implement `MetricsController#articleUsage` + the citation
       aggregation query (Green).
- [x] 11. Test: `GET .../metrics/conversations` returns the tenant-wide
       conversation count, including another user's conversation in the
       same tenant, excluding other tenants (Red).
- [x] 12. Implement `MetricsController#conversationCount` +
       `ConversationRepository#countByTenantId` (Green).
- [x] 13. Test: `GET .../metrics/messages` returns separate
       user/assistant counts, tenant-wide (Red).
- [x] 14. Implement `MetricsController#messageCounts` +
       `MessageRepository#countByConversation_Tenant_IdAndRole` (Green).
- [x] 15. Test: each of the four endpoints independently returns 403
       without `DASHBOARD_VIEW` (Red).
- [x] 16. Implement the `@RequiresPermission` annotations (Green).
- [x] 16a. (Emergent, not in the original plan) Discovered the frontend's
       already-committed `onboarding-dashboard` PLAN.md calls
       `/api/tenants/metrics/*` with **no** `tenantId` path segment — it
       resolves the active tenant from the session, same as
       `GET /api/tenants/memberships`. The initial implementation used
       `/api/tenants/{tenantId}/metrics/*`; corrected the controller to
       `/api/tenants/metrics/*` and `MetricsService` to resolve the
       tenant via `TenantContext.getActiveTenantId()` instead of a path
       parameter, updating the integration test URIs to match, before
       this was ever committed.

## 3. Final verification

- [x] 17. Run `./mvnw spotless:apply && ./mvnw verify` and confirm
       everything is green.
- [x] 18. Update `PLAN.md` if any decision changed during
       implementation (see 16a above).
- [x] 19. Update `SPEC.md`'s acceptance-criteria checkboxes.
- [x] 20. Update `knowly-app/specify/features/onboarding-dashboard/`'s
       PLAN.md note that these endpoints "don't exist in `knowly` yet"
       — they now do.
