# TASKS — global-staff-dashboard-trends (frontend)

> Atomic, sequential, verifiable tasks derived from PLAN.md.
> Each task should be small enough for a single implementation iteration.
> TDAD: Red (failing test) → Green (minimal code) → subproject verify.
> Consumes `knowly-api/specify/features/global-staff-dashboard-trends/PLAN.md`'s
> `GET /api/staff/metrics/global/trends` contract (backend implemented
> first, or at minimum its PLAN.md contract locked, before task 5 below).

## 0. Groundwork

- [x] 0.1. Confirm `public/i18n/` locale siblings to `pt-BR.json` (e.g.
      `en-US.json`) — if any exist, every new key added in section 1 must
      be added to all of them, not just `pt-BR.json`. Adjust PLAN.md's
      "Copy is drafted now" note if more than one locale file needs
      updating.

## 1. i18n keys (REQ-11)

- [x] 1. Add the new `dashboard.trends.*` namespace keys to
      `public/i18n/pt-BR.json` (and any sibling locale file found in
      0.1), exactly as drafted in PLAN.md's "Copy is drafted now" section
      — subtitles for the four stat cards, labels/subtitles for the two
      charts, and the "coming soon" tile subtitle. No test needed for a
      static JSON addition; covered indirectly by the component specs in
      sections 2-4 rendering the resolved strings.

## 2. `GradientStatCardComponent` (REQ-1, REQ-2, REQ-9, REQ-10, REQ-11)

- [x] 2. **Red** — Write `gradient-stat-card.component.spec.ts`:
      renders `label`/`subtitle`/`value`; renders the `<ng-content
      select="[icon]">` slot content; renders a badge with the correct
      sign/color for a positive `percentChange` and for a negative one;
      renders **no** badge element when `percentChange` is `null` or
      `undefined` (REQ-9/10 regression guard — assert absence of the
      badge element, not just absence of `NaN%`/`Infinity%` text).
- [x] 3. **Green** — Implement `gradient-stat-card.component.ts`
      (`src/app/features/dashboard/gradient-stat-card.component.ts`) per
      PLAN.md's "Architectural decisions" — `label`, `subtitle`, `value`,
      `percentChange: number | null | undefined` inputs, gradient
      styling using existing `ink-*`/`signal-*` Tailwind tokens, icon via
      `<ng-content select="[icon]">`.
- [x] 4. Run `npm test -- gradient-stat-card` and confirm green; commit
      (`feat(dashboard): add GradientStatCardComponent`).

## 3. Trend chart components + mappers (REQ-3, REQ-8)

- [x] 5. **Red** — Write `new-tenants-trend-chart.component.spec.ts`:
      unit-test the exported `toNewTenantsChartData()` pure mapper
      directly (labels/dataset shape from a sample `DailyCountRow[]`);
      component renders `<app-chart-canvas>` with the mapped data when
      the `data` input is set; renders `<app-error-state>` when the
      `error` input is `true`; renders an `.sr-only` table mirroring the
      same rows (assert row count and cell text match input data,
      mirroring `conversations-activity-chart.component.spec.ts`'s
      existing assertions).
- [x] 6. **Green** — Implement `new-tenants-trend-chart.component.ts`
      (`src/app/features/dashboard/`) with its `toNewTenantsChartData()`
      mapper, `data = input.required<DailyCountRow[]>()`,
      `error = input<boolean>(false)`, following
      `chart-canvas.component.ts`'s established shape.
- [x] 7. **Red** — Write `articles-read-trend-chart.component.spec.ts`
      with the equivalent assertions for `toArticlesReadChartData()`.
- [x] 8. **Green** — Implement `articles-read-trend-chart.component.ts`
      mirroring task 6's component.
- [x] 9. Run `npm test -- trend-chart` and confirm green; commit
      (`feat(dashboard): add new-tenants and articles-read trend chart components`).

## 4. `GlobalDashboardPageComponent` wiring and error handling (REQ-4, REQ-5, REQ-6, REQ-7, REQ-8, REQ-9, REQ-10)

- [x] 10. **Red** — Extend `global-dashboard-page.component.spec.ts`
      (`HttpTestingController`-based, matching this component's existing
      test setup):
      - REQ-7: `GET /api/staff/metrics/global` fails → existing
        page-level permission-denied/network error state renders; assert
        no outstanding request was made to
        `/api/staff/metrics/global/trends` (trends is never attempted
        when metrics itself fails).
      - REQ-8: metrics succeeds, trends fails (network or 403) → the four
        `GradientStatCardComponent`s render current values with no badge;
        both trend chart components render their error state; some card
        content is present (page is not blank).
      - REQ-9: `period=all` selected → all four cards render with no
        badge even when the mocked `/trends` response includes a
        non-null `percentChange` (guards the frontend-side
        belt-and-suspenders clamp, not just trusting the backend).
      - REQ-10: a single metric with `percentChange: null` in the mocked
        response renders that one card with no badge, the other three
        with theirs.
      - Changing the period selector triggers exactly one new request to
        `/api/staff/metrics/global/trends` and none to
        `/api/staff/metrics/global` (REQ-6: metrics itself isn't
        period-scoped).
      - The "coming soon" tile still renders, disabled, as a
        `GradientStatCardComponent` variant (REQ-4).
- [x] 11. **Green** — Restructure `global-dashboard-page.component.ts` per
      PLAN.md's "Architectural decisions"/"State and data" sections:
      add `period`, `trends`, `trendsError` signals; the
      `percentChangeFor(comparison, period)` pure helper; the extracted
      `classifyMetricError(response)` shared helper; the `effect()`-based
      re-fetch on `period()` change; render the four
      `GradientStatCardComponent`s (with icons per PLAN.md's icon table)
      and the two trend chart components in place of the existing plain
      tiles; keep the existing `metrics`/`loading`/`error` signals and
      REQ-7 page-level behavior unchanged.
- [x] 12. Run `npm test -- global-dashboard-page` and confirm green;
      commit
      (`feat(dashboard): redesign global dashboard with gradient stat cards and trend charts`).

## 5. Full regression + doc sync

- [x] 13. Run
      `npm run format:check && npm test && npm run build && npm run lint`
      for the whole `knowly-app` module and confirm everything is green,
      not just this feature's tests/specs. Cross-check against SPEC.md's
      acceptance-criteria checklist before considering this feature
      complete.
- [x] 14. Update `PLAN.md` with any decision that changed during
      implementation (e.g. task 0.1's actual locale-file findings, any
      icon choice or copy string that changed).
- [x] 15. Update root `PROJECT_STATUS.md` to record
      `global-staff-dashboard-trends` (frontend) as implemented.
- [x] 16. Final commit for any doc-only changes from steps 14/15
      (`docs(dashboard): record global-staff-dashboard-trends frontend completion`).
