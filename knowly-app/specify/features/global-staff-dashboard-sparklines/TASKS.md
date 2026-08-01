# TASKS — global-staff-dashboard-sparklines (frontend)

> Atomic, sequential, verifiable tasks derived from PLAN.md.
> Each task should be small enough for a single implementation iteration.
> Depends on the companion backend feature shipping
> `totalTenantsPerDay`/`staffCountPerDay` on
> `GET /api/staff/metrics/global/trends` — "Novos tenants neste mês"/
> "Total de artigos lidos" wiring (tasks 3-4 below) has no such
> dependency and can land first if the two features are sequenced
> separately.

## 1. Export shared sparkline chart config (no behavior change)

- [ ] 1. Export `SPARKLINE_OPTIONS` from `metric-tile.component.ts`
      (rename from a local `const` to an exported `const`, no value
      change). Run `npm test -- metric-tile.component` to confirm the
      existing suite is still green (this is a refactor, not a behavior
      change — no new test needed for the export itself). Commit
      (`refactor(dashboard): export sparkline chart options for reuse`).

## 2. `GradientStatCardComponent` sparkline support (REQ-1, REQ-2, Accessibility NFR)

- [ ] 2. **Red** — Write `gradient-stat-card.component.spec.ts` cases:
      renders a sparkline chart + sr-only data table when `sparklineData`
      is non-empty; renders neither when `sparklineData` is `undefined`/
      `[]`; `showSparkline="false"` suppresses the chart even with data
      present; `disabled()` never renders a sparkline regardless of
      `sparklineData`.
- [ ] 3. **Green** — Add `sparklineData`/`showSparkline` inputs and the
      sparkline chart + sr-only table block to
      `gradient-stat-card.component.ts`'s template, importing
      `ChartCanvasComponent`, `SparklineDay`, `toSparklineData`,
      `SPARKLINE_OPTIONS` from `metric-tile.component.ts`, per PLAN.md.
- [ ] 4. Run `npm test -- gradient-stat-card.component` and confirm
      green; commit
      (`feat(dashboard): add sparkline support to GradientStatCardComponent`).

## 3. Wire "Novos tenants neste mês"/"Total de artigos lidos" (REQ-1 — no backend dependency)

- [ ] 5. **Red** — Update `global-dashboard-page.component.spec.ts`:
      after a successful trends fetch, the "new-tenants-tile" and
      "articles-read-tile" `app-gradient-stat-card`s receive
      `sparklineData` bound to `trends()?.newTenantsPerDay`/
      `trends()?.articlesReadPerDay` respectively.
- [ ] 6. **Green** — Add `[sparklineData]="trends()?.newTenantsPerDay"`/
      `[sparklineData]="trends()?.articlesReadPerDay"` bindings to the
      matching two `<app-gradient-stat-card>` elements in
      `global-dashboard-page.component.ts`.
- [ ] 7. Run `npm test -- global-dashboard-page.component` and confirm
      green; commit
      (`feat(dashboard): wire new-tenants/articles-read sparklines on global dashboard`).

## 4. Wire "Total de tenants"/"Membros da equipe interna" (REQ-1 — depends on companion backend PLAN shipping first)

- [ ] 8. Confirm the companion backend feature has shipped
      `totalTenantsPerDay`/`staffCountPerDay` on
      `GET /api/staff/metrics/global/trends` (check
      `knowly-api/specify/features/global-staff-dashboard-sparklines/TASKS.md`
      is fully checked, or hit the endpoint locally) before starting this
      section.
- [ ] 9. Add `totalTenantsPerDay`/`staffCountPerDay` fields to this
      file's local `GlobalTrendsDto` interface
      (`global-dashboard-page.component.ts`), matching the backend
      contract exactly.
- [ ] 10. **Red** — Update `global-dashboard-page.component.spec.ts`:
      after a successful trends fetch, the "tenant-count-tile" and
      "staff-count-tile" `app-gradient-stat-card`s receive
      `sparklineData` bound to `trends()?.totalTenantsPerDay`/
      `trends()?.staffCountPerDay` respectively.
- [ ] 11. **Green** — Add the matching `[sparklineData]` bindings to
      those two `<app-gradient-stat-card>` elements.
- [ ] 12. Run `npm test -- global-dashboard-page.component` and confirm
      green; commit
      (`feat(dashboard): wire total-tenants/staff-count sparklines on global dashboard`).

## 5. Graceful degradation + support-tickets regression (REQ-4, REQ-5, REQ-6, REQ-7)

- [ ] 13. **Red** — Add/extend `global-dashboard-page.component.spec.ts`
      cases: before the first successful trends fetch, all four cards
      render with no sparkline; a trends fetch failing after a prior
      success leaves the last successful `sparklineData` bindings intact
      (assert against `trends()`, not just the percent-change badge);
      the support-tickets card has no `sparklineData` binding and never
      renders a chart.
- [ ] 14. **Green** — Fix anything the Red step surfaces (expected: no
      production code change needed here if sections 2-4 were done per
      PLAN.md, since `trends()`'s existing null/stale-on-error behavior
      already covers this — this task exists to catch any gap, not to
      introduce new logic).
- [ ] 15. Run `npm test -- global-dashboard-page.component` and confirm
      green; commit if any fix was needed
      (`fix(dashboard): confirm sparkline graceful-degradation behavior`)
      or skip the commit if task 14 required no code change (note that
      in this file).

## 6. Full regression + doc sync

- [ ] 16. Run `npm run format:check && npm test && npm run build && npm
      run lint` for the whole `knowly-app` project and confirm
      everything is green. Cross-check against SPEC.md's acceptance-
      criteria checklist before considering this feature complete.
- [ ] 17. Update `PLAN.md` with any decision that changed during
      implementation.
- [ ] 18. Update root `PROJECT_STATUS.md` to record
      `global-staff-dashboard-sparklines` (frontend) as implemented.
- [ ] 19. Final commit for any doc-only changes from steps 17/18
      (`docs(dashboard): record global-staff-dashboard-sparklines frontend completion`).
