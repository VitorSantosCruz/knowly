import { NgComponentOutlet } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  input,
  output,
  signal,
  viewChild,
} from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import {
  LucideChevronDown,
  LucideChevronLeft,
  LucideChevronRight,
  LucideChevronUp,
  LucideChevronsUpDown,
  LucideInbox,
} from '@lucide/angular';
import { buttonClass } from '../button-classes';
import { ErrorStateComponent } from '../error-state.component';
import { NoAccessStateComponent } from '../no-access-state.component';
import {
  SharedListColumn,
  SharedListError,
  SharedListRowAction,
  SharedListServerPagination,
  SharedListSortState,
} from './shared-list.model';

/**
 * Generic, reusable list/table layout — the "Ink & Signal translation"
 * design contract in
 * `specify/features/staff-members-management-redesign/SPEC.md`. Consumed
 * by staff directory / tenant members / (future) tenant list screens.
 * Columns and row-actions are configured via plain input objects, not
 * projected templates — see PLAN.md's "Architectural decisions".
 */
@Component({
  selector: 'app-shared-list',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    TranslocoPipe,
    NgComponentOutlet,
    ErrorStateComponent,
    NoAccessStateComponent,
    LucideChevronsUpDown,
    LucideChevronUp,
    LucideChevronDown,
    LucideChevronLeft,
    LucideChevronRight,
    LucideInbox,
  ],
  template: `
    <div
      data-testid="shared-list"
      class="rounded-2xl border border-ink-200/70 bg-white shadow-lg shadow-slate-200/60 dark:border-ink-800/70 dark:bg-ink-900 dark:shadow-none"
    >
      <div
        class="flex items-center justify-between border-b border-ink-200/70 px-6 py-4 dark:border-ink-800/70"
      >
        <h2 class="font-display text-lg font-semibold text-ink-900 dark:text-white">
          {{ title() }}
        </h2>
        <span
          data-testid="shared-list-count"
          aria-live="polite"
          class="text-sm text-ink-500 dark:text-ink-400"
        >
          {{
            'sharedList.showingRange'
              | transloco: { from: rangeFrom(), to: rangeTo(), total: totalCount() }
          }}
        </span>
      </div>

      @if (searchable()) {
        <div
          class="flex items-center justify-between gap-3 border-b border-ink-200/70 px-6 py-3 dark:border-ink-800/70"
        >
          <input
            data-testid="shared-list-search"
            type="search"
            [value]="searchTerm()"
            (input)="onSearch($any($event.target).value)"
            [placeholder]="searchPlaceholder()"
            class="rounded-lg border border-ink-200 bg-white px-3 py-2 text-sm text-ink-900 focus:border-signal-500 focus:ring-1 focus:ring-signal-500 focus:outline-none focus-visible:ring-2 focus-visible:ring-signal-500 focus-visible:ring-offset-2 dark:border-ink-700 dark:bg-ink-800 dark:text-white dark:focus-visible:ring-offset-ink-900"
          />
        </div>
      }

      @if (!loading() && !error() && sortableColumns().length > 0) {
        <div class="border-b border-ink-200/70 px-4 py-2 sm:hidden dark:border-ink-800/70">
          <label class="sr-only" for="shared-list-sort-by">{{
            'sharedList.sortBy' | transloco
          }}</label>
          <select
            id="shared-list-sort-by"
            data-testid="shared-list-sort-by"
            class="w-full rounded-lg border border-ink-200 bg-white px-2 py-1.5 text-sm text-ink-900 focus-visible:ring-2 focus-visible:ring-signal-500 focus-visible:ring-offset-2 dark:border-ink-700 dark:bg-ink-800 dark:text-white dark:focus-visible:ring-offset-ink-900"
            (change)="onSortBySelect($any($event.target).value)"
          >
            <option value="">{{ 'sharedList.sortBy' | transloco }}</option>
            @for (column of sortableColumns(); track column.key) {
              <option [value]="column.key">{{ column.headerKey | transloco }}</option>
            }
          </select>
        </div>
      }

      <table data-testid="shared-list-table" class="w-full">
        <thead class="bg-ink-50/60 dark:bg-ink-950/40">
          <tr>
            @if (selectable()) {
              <th scope="col" class="w-10 px-4 py-3">
                <input
                  #selectAllCheckbox
                  type="checkbox"
                  data-testid="shared-list-select-all"
                  [attr.aria-label]="'sharedList.selectAll' | transloco"
                  [checked]="allVisibleSelected()"
                  (change)="onToggleAll()"
                  class="h-4 w-4 rounded border-ink-300 text-signal-600 focus:ring-signal-500 focus-visible:ring-2 focus-visible:ring-signal-500 focus-visible:ring-offset-2 dark:border-ink-600 dark:bg-ink-800 dark:focus-visible:ring-offset-ink-900"
                />
              </th>
            }
            @for (column of columns(); track column.key) {
              <th
                scope="col"
                [attr.aria-sort]="ariaSortFor(column.key)"
                [class]="headerCellClass(column)"
              >
                @if (column.sortable) {
                  <button
                    type="button"
                    [attr.data-testid]="'shared-list-sort-' + column.key"
                    (click)="onSortClick(column.key)"
                    class="inline-flex cursor-pointer items-center gap-1 transition-colors duration-fast ease-fluid select-none hover:text-ink-700 focus-visible:ring-2 focus-visible:ring-signal-500 focus-visible:ring-offset-2 dark:hover:text-ink-200 dark:focus-visible:ring-offset-ink-900"
                  >
                    {{ column.headerKey | transloco }}
                    @if (sortState()?.key === column.key && sortState()?.direction === 'asc') {
                      <svg lucideChevronUp class="h-3.5 w-3.5" aria-hidden="true"></svg>
                    } @else if (
                      sortState()?.key === column.key && sortState()?.direction === 'desc'
                    ) {
                      <svg lucideChevronDown class="h-3.5 w-3.5" aria-hidden="true"></svg>
                    } @else {
                      <svg lucideChevronsUpDown class="h-3.5 w-3.5" aria-hidden="true"></svg>
                    }
                  </button>
                } @else {
                  {{ column.headerKey | transloco }}
                }
              </th>
            }
            @if (rowActions().length > 0) {
              <th scope="col" class="w-20 px-4 py-3 text-right"></th>
            }
          </tr>
        </thead>
        <tbody>
          @if (loading()) {
            @for (skeletonRow of skeletonRows; track skeletonRow) {
              <tr data-testid="shared-list-skeleton-row" class="animate-pulse">
                @if (selectable()) {
                  <td class="px-4 py-3">
                    <div class="h-4 w-4 rounded bg-ink-100 dark:bg-ink-800"></div>
                  </td>
                }
                @for (column of columns(); track column.key) {
                  <td [class]="dataCellClass(column)">
                    <div class="h-3 w-24 rounded bg-ink-100 dark:bg-ink-800"></div>
                  </td>
                }
                @if (rowActions().length > 0) {
                  <td class="px-4 py-3"></td>
                }
              </tr>
            }
          } @else if (error() === 'permission-denied') {
            <tr>
              <td [attr.colspan]="totalColumnCount()" class="px-6 py-8">
                <app-no-access-state />
              </td>
            </tr>
          } @else if (error() === 'network') {
            <tr>
              <td [attr.colspan]="totalColumnCount()" class="px-6 py-8">
                <app-error-state />
              </td>
            </tr>
          } @else if (visibleRows().length === 0) {
            <tr>
              <td [attr.colspan]="totalColumnCount()" class="px-6 py-12 text-center">
                @if (searchTerm()) {
                  <svg
                    lucideInbox
                    class="mx-auto mb-3 h-8 w-8 text-ink-300 dark:text-ink-600"
                    aria-hidden="true"
                  ></svg>
                  <p
                    data-testid="shared-list-no-results"
                    class="text-sm text-ink-500 dark:text-ink-400"
                  >
                    {{ 'sharedList.noResults' | transloco }}
                  </p>
                  <button
                    type="button"
                    data-testid="shared-list-clear-filters"
                    (click)="onClearFilters()"
                    [class]="clearFiltersButtonClass"
                  >
                    {{ 'sharedList.clearFilters' | transloco }}
                  </button>
                } @else {
                  <svg
                    lucideInbox
                    class="mx-auto mb-3 h-8 w-8 text-ink-300 dark:text-ink-600"
                    aria-hidden="true"
                  ></svg>
                  <p data-testid="shared-list-empty" class="text-sm text-ink-500 dark:text-ink-400">
                    {{ emptyMessageKey() | transloco }}
                  </p>
                }
              </td>
            </tr>
          } @else {
            @for (row of visibleRows(); track rowId()(row)) {
              <tr
                [attr.data-testid]="'shared-list-row-' + rowId()(row)"
                (click)="rowClick.emit(row)"
                class="border-b border-ink-100 transition-colors duration-fast ease-fluid last:border-b-0 hover:bg-ink-50 dark:border-ink-800/50 dark:hover:bg-ink-800/60"
              >
                @if (selectable()) {
                  <td class="px-4 py-3 sm:px-4">
                    <input
                      type="checkbox"
                      [attr.data-testid]="'shared-list-select-' + rowId()(row)"
                      [checked]="isSelected(row)"
                      (click)="$event.stopPropagation()"
                      (change)="onToggleRow(row)"
                      class="h-4 w-4 rounded border-ink-300 text-signal-600 focus:ring-signal-500 focus-visible:ring-2 focus-visible:ring-signal-500 focus-visible:ring-offset-2 dark:border-ink-600 dark:bg-ink-800 dark:focus-visible:ring-offset-ink-900"
                    />
                  </td>
                }
                @for (column of columns(); track column.key) {
                  <td
                    [class]="dataCellClass(column)"
                    [attr.data-testid]="'shared-list-cell-' + column.key"
                  >
                    @let cell = column.render(row);
                    @switch (cell.type) {
                      @case ('identity') {
                        <div class="flex items-center gap-3">
                          @if (cell.avatarUrl) {
                            <img
                              [src]="cell.avatarUrl"
                              alt=""
                              class="h-9 w-9 rounded-full object-cover"
                            />
                          } @else {
                            <div
                              class="flex h-9 w-9 items-center justify-center rounded-full bg-ink-100 text-xs font-medium text-ink-600 dark:bg-ink-800 dark:text-ink-300"
                            >
                              {{ cell.initials }}
                            </div>
                          }
                          <div class="leading-tight">
                            <p class="text-sm font-semibold text-ink-900 dark:text-white">
                              {{ cell.primary }}
                            </p>
                            @if (cell.secondary) {
                              <p class="text-xs text-ink-500 dark:text-ink-400">
                                {{ cell.secondary }}
                              </p>
                            }
                          </div>
                        </div>
                      }
                      @case ('pill') {
                        <span
                          class="rounded-full px-2 py-1 text-xs font-medium"
                          [class]="cell.colorClass"
                        >
                          {{ cell.labelKey | transloco }}
                        </span>
                      }
                      @default {
                        {{ cell.value }}
                      }
                    }
                  </td>
                }
                @if (rowActions().length > 0) {
                  <td class="px-4 py-3 text-right">
                    <div class="inline-flex items-center gap-1">
                      @for (action of rowActions(); track action.labelKey) {
                        <button
                          type="button"
                          [attr.data-testid]="
                            'shared-list-action-' + action.labelKey + '-' + rowId()(row)
                          "
                          [disabled]="isActionDisabled(action, row)"
                          [attr.title]="actionTitle(action, row)"
                          [class]="rowActionButtonClass(action)"
                          (click)="$event.stopPropagation(); action.onClick(row)"
                        >
                          <span [attr.aria-label]="action.labelKey | transloco">
                            <ng-container [ngComponentOutlet]="action.icon" />
                          </span>
                        </button>
                      }
                    </div>
                  </td>
                }
              </tr>
            }
          }
        </tbody>
      </table>
      @if (serverPagination(); as pagination) {
        @if (pagination.totalPages > 1) {
          <div
            class="flex items-center justify-between gap-3 border-t border-ink-200/70 px-6 py-4 dark:border-ink-800/70"
          >
            <button
              type="button"
              data-testid="shared-list-prev-page"
              [disabled]="pagination.page === 0"
              [class]="paginationButtonClass"
              (click)="pageChange.emit(-1)"
            >
              <svg lucideChevronLeft class="h-4 w-4" aria-hidden="true"></svg>
              {{ 'sharedList.previousPage' | transloco }}
            </button>
            <button
              type="button"
              data-testid="shared-list-next-page"
              [disabled]="pagination.page === pagination.totalPages - 1"
              [class]="paginationButtonClass"
              (click)="pageChange.emit(1)"
            >
              {{ 'sharedList.nextPage' | transloco }}
              <svg lucideChevronRight class="h-4 w-4" aria-hidden="true"></svg>
            </button>
          </div>
        }
      }
    </div>
  `,
})
export class SharedListComponent<T> {
  readonly title = input<string>('');
  readonly rows = input.required<T[]>();
  readonly columns = input.required<SharedListColumn<T>[]>();
  readonly rowActions = input<SharedListRowAction<T>[]>([]);
  readonly rowId = input.required<(row: T) => string | number>();
  readonly loading = input<boolean>(false);
  readonly error = input<SharedListError>(null);
  readonly emptyMessageKey = input<string>('sharedList.empty.default');
  readonly selectable = input<boolean>(false);
  readonly searchable = input<boolean>(false);
  readonly searchPlaceholder = input<string>('');
  readonly serverPagination = input<SharedListServerPagination | null>(null);

  readonly selectionChange = output<(string | number)[]>();
  readonly sortChange = output<SharedListSortState | null>();
  readonly pageChange = output<-1 | 1>();
  readonly searchChange = output<string>();
  readonly rowClick = output<T>();

  protected readonly paginationButtonClass = buttonClass('secondary');

  protected readonly searchTerm = signal('');
  protected readonly sortState = signal<SharedListSortState | null>(null);
  protected readonly selectedIds = signal<Set<string | number>>(new Set());

  protected readonly skeletonRows = [0, 1, 2, 3, 4];
  protected readonly clearFiltersButtonClass = buttonClass('secondary', { ghost: true });

  protected readonly sortableColumns = computed(() => this.columns().filter((c) => c.sortable));

  protected readonly filteredRows = computed(() => {
    const term = this.searchTerm().trim().toLowerCase();
    if (!term) {
      return this.rows();
    }

    return this.rows().filter((row) =>
      this.columns().some((column) => {
        const cell = column.render(row);
        const haystack =
          cell.type === 'identity'
            ? `${cell.primary} ${cell.secondary ?? ''}`
            : cell.type === 'text'
              ? cell.value
              : '';
        return haystack.toLowerCase().includes(term);
      }),
    );
  });

  protected readonly visibleRows = computed(() => {
    if (this.serverPagination() !== null) {
      return this.rows();
    }

    const sort = this.sortState();
    const rows = [...this.filteredRows()];

    if (!sort) {
      return rows;
    }

    const column = this.columns().find((c) => c.key === sort.key);
    if (!column) {
      return rows;
    }

    const cellText = (row: T): string => {
      const cell = column.render(row);
      if (cell.type === 'identity') {
        return cell.primary;
      }
      if (cell.type === 'pill') {
        return cell.labelKey;
      }
      return cell.value;
    };

    rows.sort((a, b) => {
      const comparison = cellText(a).localeCompare(cellText(b));
      return sort.direction === 'asc' ? comparison : -comparison;
    });

    return rows;
  });

  protected readonly totalCount = computed(() => {
    const serverPagination = this.serverPagination();
    return serverPagination !== null ? serverPagination.totalElements : this.filteredRows().length;
  });
  protected readonly rangeFrom = computed(() => (this.totalCount() === 0 ? 0 : 1));
  protected readonly rangeTo = computed(() => this.visibleRows().length);

  protected readonly allVisibleSelected = computed(() => {
    const visible = this.visibleRows();
    if (visible.length === 0) {
      return false;
    }
    const selected = this.selectedIds();
    return visible.every((row) => selected.has(this.rowId()(row)));
  });

  protected readonly someVisibleSelected = computed(() => {
    const visible = this.visibleRows();
    const selected = this.selectedIds();
    return visible.some((row) => selected.has(this.rowId()(row))) && !this.allVisibleSelected();
  });

  private readonly selectAllCheckbox = viewChild<{ nativeElement: HTMLInputElement }>(
    'selectAllCheckbox',
  );

  constructor() {
    effect(() => {
      const checkbox = this.selectAllCheckbox();
      if (checkbox) {
        checkbox.nativeElement.indeterminate = this.someVisibleSelected();
      }
    });
  }

  protected totalColumnCount(): number {
    return (
      this.columns().length + (this.selectable() ? 1 : 0) + (this.rowActions().length > 0 ? 1 : 0)
    );
  }

  protected headerCellClass(column: SharedListColumn<T>): string {
    const base =
      'px-4 py-3 text-left text-xs font-semibold tracking-wide text-ink-500 uppercase dark:text-ink-400';
    return column.essential === false ? `${base} hidden sm:table-cell` : base;
  }

  protected dataCellClass(column: SharedListColumn<T>): string {
    const base = 'px-4 py-3 text-sm text-ink-700 sm:px-4 sm:py-3 dark:text-ink-300';
    return column.essential === false ? `${base} hidden sm:table-cell` : base;
  }

  protected ariaSortFor(key: string): 'ascending' | 'descending' | 'none' {
    const sort = this.sortState();
    if (!sort || sort.key !== key) {
      return 'none';
    }
    return sort.direction === 'asc' ? 'ascending' : 'descending';
  }

  protected onSortClick(key: string): void {
    const current = this.sortState();

    let next: SharedListSortState | null;
    if (!current || current.key !== key) {
      next = { key, direction: 'asc' };
    } else if (current.direction === 'asc') {
      next = { key, direction: 'desc' };
    } else {
      next = null;
    }

    this.sortState.set(next);
    this.sortChange.emit(next);
  }

  protected onSortBySelect(key: string): void {
    if (!key) {
      this.sortState.set(null);
      this.sortChange.emit(null);
      return;
    }
    const next: SharedListSortState = { key, direction: 'asc' };
    this.sortState.set(next);
    this.sortChange.emit(next);
  }

  protected onSearch(term: string): void {
    this.searchTerm.set(term);
    this.searchChange.emit(term);
  }

  protected onClearFilters(): void {
    this.searchTerm.set('');
  }

  protected isSelected(row: T): boolean {
    return this.selectedIds().has(this.rowId()(row));
  }

  protected onToggleRow(row: T): void {
    const id = this.rowId()(row);
    const next = new Set(this.selectedIds());
    if (next.has(id)) {
      next.delete(id);
    } else {
      next.add(id);
    }
    this.selectedIds.set(next);
    this.selectionChange.emit([...next]);
  }

  protected onToggleAll(): void {
    const next = new Set(this.selectedIds());
    const visible = this.visibleRows();

    if (this.allVisibleSelected()) {
      visible.forEach((row) => next.delete(this.rowId()(row)));
    } else {
      visible.forEach((row) => next.add(this.rowId()(row)));
    }

    this.selectedIds.set(next);
    this.selectionChange.emit([...next]);
  }

  protected isActionDisabled(action: SharedListRowAction<T>, row: T): boolean {
    return action.disabled?.(row) ?? false;
  }

  protected actionTitle(action: SharedListRowAction<T>, row: T): string | null {
    if (!this.isActionDisabled(action, row)) {
      return null;
    }
    return action.disabledReasonKey?.(row) ?? null;
  }

  protected rowActionButtonClass(action: SharedListRowAction<T>): string {
    return buttonClass(action.variant, { ghost: true, rounded: true });
  }
}
