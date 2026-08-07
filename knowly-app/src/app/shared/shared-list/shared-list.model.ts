import { Type } from '@angular/core';

/**
 * Discriminated union for a rendered cell value. `render()` returns one
 * of these instead of a bare string so `SharedListComponent` can render
 * the identity cell (avatar + name + secondary line) and status pills
 * (`TicketStatusBadgeComponent`'s shape) without per-column template
 * projection — see PLAN.md's "columns/row-actions via plain input
 * objects, not projected templates" decision. This extends that PLAN's
 * literal `string | { pillKey; colorClass }` sketch with an explicit
 * `'identity'` variant, since the SPEC's reference layout requires an
 * avatar+name+secondary identity cell that a bare string/pill can't
 * express — documented here rather than re-litigated at review time.
 */
export type SharedListCellValue =
  | { type: 'text'; value: string }
  | { type: 'pill'; labelKey: string; colorClass: string }
  | {
      type: 'identity';
      primary: string;
      secondary?: string;
      avatarUrl?: string;
      initials?: string;
    };

export interface SharedListColumn<T> {
  /** Stable identifier, also used as the sort key sent in `sortChange`. */
  key: string;
  /** i18n key for the column header label. */
  headerKey: string;
  sortable?: boolean;
  /**
   * Non-essential columns collapse below `sm:` per SPEC's responsive
   * behavior. Defaults to `true` (essential/always visible) so callers
   * must opt a column *out* of small-screen visibility explicitly.
   */
  essential?: boolean;
  render: (row: T) => SharedListCellValue;
}

export interface SharedListRowAction<T> {
  icon: Type<unknown>;
  labelKey: string;
  variant: 'secondary' | 'danger';
  disabled?: (row: T) => boolean;
  disabledReasonKey?: (row: T) => string | null;
  /**
   * Per-row omission, not merely disabling — e.g. `members-page.component.ts`'s own-row
   * swap (REQ-10), where the viewer's own row drops edit/delete entirely rather than
   * showing them disabled. Defaults to always-visible when omitted. Distinct from
   * `select-tenant-page.component.ts`'s all-or-nothing `computed(rowActions())` case
   * (PLAN's judgment call against adding this for that single case) — this is the
   * genuinely per-row case that justifies it.
   */
  hidden?: (row: T) => boolean;
  onClick: (row: T) => void;
}

export type SharedListSortDirection = 'asc' | 'desc' | null;

export interface SharedListSortState {
  key: string;
  direction: 'asc' | 'desc';
}

export type SharedListError = 'network' | 'permission-denied' | null;

/**
 * Server-pagination mode contract — see PLAN.md's "shared-list.model.ts
 * /shared-list.component.ts gain an optional server-pagination mode, not
 * a second component" decision. When passed to `SharedListComponent`,
 * `rows()` is expected to already be the current page's content (host
 * fetches per-page), and `visibleRows()`/`totalCount()` no longer
 * filter/sort client-side.
 */
export interface SharedListServerPagination {
  page: number;
  pageSize: number;
  totalPages: number;
  totalElements: number;
}
