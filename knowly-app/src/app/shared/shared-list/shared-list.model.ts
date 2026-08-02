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
  onClick: (row: T) => void;
}

export type SharedListSortDirection = 'asc' | 'desc' | null;

export interface SharedListSortState {
  key: string;
  direction: 'asc' | 'desc';
}

export type SharedListError = 'network' | 'permission-denied' | null;
