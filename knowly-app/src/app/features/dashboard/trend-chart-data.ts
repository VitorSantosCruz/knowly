/**
 * Shape shared by both new trend chart components (`newTenantsPerDay`/
 * `articlesReadPerDay` from `GlobalTrendsDto`, see
 * knowly-api/specify/features/global-staff-dashboard-trends/PLAN.md).
 * Structurally identical to `conversations-activity-chart.component.ts`'s
 * own `DailyCountRow`, kept as a separate local type here since these two
 * new components are "dumb" pre-fetched-data renderers owned by
 * `GlobalDashboardPageComponent`, not self-fetching siblings of that chart.
 */
export interface DailyCountRow {
  date: string;
  count: number;
}

export interface TrendChartData {
  labels: string[];
  datasets: { data: number[] }[];
}
