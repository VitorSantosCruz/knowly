import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTransloco } from '@jsverse/transloco';
import { LucideSquarePen, LucideTrash2 } from '@lucide/angular';
import { SharedListComponent } from './shared-list.component';
import {
  SharedListColumn,
  SharedListRowAction,
  SharedListServerPagination,
} from './shared-list.model';
import { FakeTranslocoLoader } from '../../testing/fake-transloco-loader';

interface Row {
  id: number;
  name: string;
  email: string;
  role: 'ADMIN' | 'REGULAR';
}

const ROWS: Row[] = [
  { id: 1, name: 'Jane Doe', email: 'jane@x.com', role: 'ADMIN' },
  { id: 2, name: 'Amy Smith', email: 'amy@x.com', role: 'REGULAR' },
  { id: 3, name: 'Bob Lee', email: 'bob@x.com', role: 'REGULAR' },
];

const COLUMNS: SharedListColumn<Row>[] = [
  {
    key: 'identity',
    headerKey: 'sharedList.selectAll',
    render: (row) => ({
      type: 'identity',
      primary: row.name,
      secondary: row.email,
      initials: 'JD',
    }),
  },
  {
    key: 'role',
    headerKey: 'sharedList.sortBy',
    sortable: true,
    essential: false,
    render: (row) => ({
      type: 'pill',
      labelKey: row.role,
      colorClass:
        row.role === 'ADMIN' ? 'bg-signal-100 text-signal-800' : 'bg-ink-100 text-ink-600',
    }),
  },
];

describe('SharedListComponent', () => {
  let fixture: ComponentFixture<SharedListComponent<Row>>;

  async function setup(overrides: Partial<Record<string, unknown>> = {}) {
    await TestBed.configureTestingModule({
      imports: [SharedListComponent],
      providers: [
        provideTransloco({
          config: { availableLangs: ['en', 'pt-BR'], defaultLang: 'en' },
          loader: FakeTranslocoLoader,
        }),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(SharedListComponent<Row>);
    fixture.componentRef.setInput('title', 'Users');
    fixture.componentRef.setInput('rows', ROWS);
    fixture.componentRef.setInput('columns', COLUMNS);
    fixture.componentRef.setInput('rowId', (row: Row) => row.id);

    for (const [key, value] of Object.entries(overrides)) {
      fixture.componentRef.setInput(key, value);
    }

    fixture.detectChanges();
  }

  function el<T extends Element = Element>(testId: string): T | null {
    return fixture.nativeElement.querySelector(`[data-testid="${testId}"]`);
  }

  function els<T extends Element = Element>(testId: string): T[] {
    return Array.from(fixture.nativeElement.querySelectorAll(`[data-testid="${testId}"]`));
  }

  it('renders header title, live count, column headers, and rows', async () => {
    await setup();

    expect(fixture.nativeElement.textContent).toContain('Users');
    expect(el('shared-list-count')?.textContent).toContain('3');
    expect(el('shared-list-row-1')).toBeTruthy();
    expect(el('shared-list-row-2')).toBeTruthy();
    expect(el('shared-list-row-3')).toBeTruthy();
    expect(fixture.nativeElement.textContent).toContain('Jane Doe');
    expect(fixture.nativeElement.textContent).toContain('jane@x.com');
  });

  describe('selection', () => {
    it('toggles a single row and emits selectionChange', async () => {
      await setup({ selectable: true });
      let emitted: (string | number)[] = [];
      fixture.componentInstance.selectionChange.subscribe((v) => (emitted = v));

      const checkbox = el<HTMLInputElement>('shared-list-select-1')!;
      checkbox.checked = true;
      checkbox.dispatchEvent(new Event('change'));
      fixture.detectChanges();

      expect(emitted).toEqual([1]);
    });

    it('select-all selects every visible row, and is indeterminate with a partial selection', async () => {
      await setup({ selectable: true });
      let emitted: (string | number)[] = [];
      fixture.componentInstance.selectionChange.subscribe((v) => (emitted = v));

      const row1 = el<HTMLInputElement>('shared-list-select-1')!;
      row1.checked = true;
      row1.dispatchEvent(new Event('change'));
      fixture.detectChanges();

      const selectAll = el<HTMLInputElement>('shared-list-select-all')!;
      expect(selectAll.indeterminate).toBe(true);

      selectAll.dispatchEvent(new Event('change'));
      fixture.detectChanges();

      expect(emitted.sort()).toEqual([1, 2, 3]);
      expect(selectAll.indeterminate).toBe(false);
    });
  });

  describe('sorting', () => {
    it('cycles none -> asc -> desc -> none on header click, updating aria-sort and emitting sortChange', async () => {
      await setup();
      const emitted: unknown[] = [];
      fixture.componentInstance.sortChange.subscribe((v) => emitted.push(v));

      const header = el<HTMLButtonElement>('shared-list-sort-role')!;
      const th = header.closest('th')!;

      expect(th.getAttribute('aria-sort')).toBe('none');

      header.click();
      fixture.detectChanges();
      expect(th.getAttribute('aria-sort')).toBe('ascending');
      expect(emitted[0]).toEqual({ key: 'role', direction: 'asc' });

      header.click();
      fixture.detectChanges();
      expect(th.getAttribute('aria-sort')).toBe('descending');
      expect(emitted[1]).toEqual({ key: 'role', direction: 'desc' });

      header.click();
      fixture.detectChanges();
      expect(th.getAttribute('aria-sort')).toBe('none');
      expect(emitted[2]).toBeNull();
    });
  });

  describe('search', () => {
    it('filters rows client-side by search term', async () => {
      await setup({ searchable: true });

      const input = el<HTMLInputElement>('shared-list-search')!;
      input.value = 'amy';
      input.dispatchEvent(new Event('input'));
      fixture.detectChanges();

      expect(el('shared-list-row-2')).toBeTruthy();
      expect(el('shared-list-row-1')).toBeNull();
      expect(el('shared-list-row-3')).toBeNull();
    });
  });

  describe('states', () => {
    it('renders skeleton rows while loading', async () => {
      await setup({ loading: true });
      expect(els('shared-list-skeleton-row').length).toBe(5);
      expect(el('shared-list-row-1')).toBeNull();
    });

    it('renders empty state with default message when there are zero rows and no filter', async () => {
      await setup({ rows: [] });
      expect(el('shared-list-empty')).toBeTruthy();
      expect(el('shared-list-no-results')).toBeNull();
    });

    it('renders no-results state with clear-filters button when search yields zero rows', async () => {
      await setup({ searchable: true });
      const input = el<HTMLInputElement>('shared-list-search')!;
      input.value = 'nobody-matches-this';
      input.dispatchEvent(new Event('input'));
      fixture.detectChanges();

      expect(el('shared-list-no-results')).toBeTruthy();
      const clear = el<HTMLButtonElement>('shared-list-clear-filters')!;
      clear.click();
      fixture.detectChanges();

      expect(input.value).toBe('');
      expect(el('shared-list-row-1')).toBeTruthy();
    });

    it('delegates to app-error-state for a network error', async () => {
      await setup({ error: 'network' });
      expect(fixture.nativeElement.querySelector('app-error-state')).toBeTruthy();
    });

    it('delegates to app-no-access-state for a permission-denied error', async () => {
      await setup({ error: 'permission-denied' });
      expect(fixture.nativeElement.querySelector('app-no-access-state')).toBeTruthy();
    });
  });

  describe('row actions', () => {
    const rowActions: SharedListRowAction<Row>[] = [
      {
        icon: LucideSquarePen,
        labelKey: 'sharedList.actions.edit',
        variant: 'secondary',
        onClick: () => undefined,
      },
      {
        icon: LucideTrash2,
        labelKey: 'sharedList.actions.delete',
        variant: 'danger',
        disabled: (row) => row.role === 'ADMIN',
        disabledReasonKey: () => 'cannot delete admin',
        onClick: () => undefined,
      },
    ];

    it('renders icon buttons, disabling and titling per disabled(row)', async () => {
      await setup({ rowActions });

      const editButton = el<HTMLButtonElement>('shared-list-action-sharedList.actions.edit-1')!;
      expect(editButton.disabled).toBe(false);

      const deleteButtonAdmin = el<HTMLButtonElement>(
        'shared-list-action-sharedList.actions.delete-1',
      )!;
      expect(deleteButtonAdmin.disabled).toBe(true);
      expect(deleteButtonAdmin.getAttribute('title')).toBe('cannot delete admin');

      const deleteButtonRegular = el<HTMLButtonElement>(
        'shared-list-action-sharedList.actions.delete-2',
      )!;
      expect(deleteButtonRegular.disabled).toBe(false);
    });

    it('invokes onClick with the row when clicked', async () => {
      const clicked: Row[] = [];
      const actions: SharedListRowAction<Row>[] = [
        {
          icon: LucideSquarePen,
          labelKey: 'sharedList.actions.edit',
          variant: 'secondary',
          onClick: (row) => clicked.push(row),
        },
      ];
      await setup({ rowActions: actions });

      el<HTMLButtonElement>('shared-list-action-sharedList.actions.edit-1')!.click();

      expect(clicked).toEqual([ROWS[0]]);
    });
  });

  describe('server-pagination mode', () => {
    const serverPagination: SharedListServerPagination = {
      page: 0,
      totalPages: 3,
      totalElements: 3,
    };

    it('does not filter/sort rows client-side when serverPagination is non-null, even with a search term', async () => {
      await setup({ searchable: true, serverPagination });

      const input = el<HTMLInputElement>('shared-list-search')!;
      input.value = 'amy';
      input.dispatchEvent(new Event('input'));
      fixture.detectChanges();

      expect(el('shared-list-row-1')).toBeTruthy();
      expect(el('shared-list-row-2')).toBeTruthy();
      expect(el('shared-list-row-3')).toBeTruthy();
    });

    it('renders prev/next pagination controls only when serverPagination is non-null and totalPages > 1', async () => {
      await setup({ serverPagination });
      expect(el('shared-list-prev-page')).toBeTruthy();
      expect(el('shared-list-next-page')).toBeTruthy();
    });

    it('hides pagination controls when totalPages <= 1', async () => {
      await setup({ serverPagination: { page: 0, totalPages: 1, totalElements: 1 } });
      expect(el('shared-list-prev-page')).toBeNull();
      expect(el('shared-list-next-page')).toBeNull();
    });

    it('hides pagination controls when serverPagination is null', async () => {
      await setup();
      expect(el('shared-list-prev-page')).toBeNull();
      expect(el('shared-list-next-page')).toBeNull();
    });

    it('disables prev at page 0 and next at the last page, emitting pageChange with the right delta', async () => {
      await setup({ serverPagination: { page: 0, totalPages: 2, totalElements: 2 } });
      const emitted: number[] = [];
      fixture.componentInstance.pageChange.subscribe((v) => emitted.push(v));

      const prev = el<HTMLButtonElement>('shared-list-prev-page')!;
      const next = el<HTMLButtonElement>('shared-list-next-page')!;
      expect(prev.disabled).toBe(true);
      expect(next.disabled).toBe(false);

      next.click();
      expect(emitted).toEqual([1]);
    });

    it('disables next at the last page', async () => {
      await setup({ serverPagination: { page: 1, totalPages: 2, totalElements: 2 } });
      const prev = el<HTMLButtonElement>('shared-list-prev-page')!;
      const next = el<HTMLButtonElement>('shared-list-next-page')!;
      expect(prev.disabled).toBe(false);
      expect(next.disabled).toBe(true);

      const emitted: number[] = [];
      fixture.componentInstance.pageChange.subscribe((v) => emitted.push(v));
      prev.click();
      expect(emitted).toEqual([-1]);
    });

    it('emits searchChange with the typed term in server-pagination mode', async () => {
      await setup({ searchable: true, serverPagination });
      let emitted = '';
      fixture.componentInstance.searchChange.subscribe((v) => (emitted = v));

      const input = el<HTMLInputElement>('shared-list-search')!;
      input.value = 'amy';
      input.dispatchEvent(new Event('input'));

      expect(emitted).toBe('amy');
    });

    it('emits searchChange with the typed term in memory-pagination mode too', async () => {
      await setup({ searchable: true });
      let emitted = '';
      fixture.componentInstance.searchChange.subscribe((v) => (emitted = v));

      const input = el<HTMLInputElement>('shared-list-search')!;
      input.value = 'jane';
      input.dispatchEvent(new Event('input'));

      expect(emitted).toBe('jane');
    });

    it('emits rowClick when a row is clicked, in memory mode', async () => {
      await setup();
      let emitted: Row | null = null;
      fixture.componentInstance.rowClick.subscribe((v) => (emitted = v));

      el<HTMLTableRowElement>('shared-list-row-1')!.click();

      expect(emitted).toEqual(ROWS[0]);
    });

    it('emits rowClick when a row is clicked, in server-pagination mode', async () => {
      await setup({ serverPagination });
      let emitted: Row | null = null;
      fixture.componentInstance.rowClick.subscribe((v) => (emitted = v));

      el<HTMLTableRowElement>('shared-list-row-2')!.click();

      expect(emitted).toEqual(ROWS[1]);
    });
  });

  describe('responsive and accessibility', () => {
    it('marks non-essential columns hidden below sm: via hidden sm:table-cell', async () => {
      await setup();

      const roleCell = el('shared-list-cell-role')!;
      expect(roleCell.className).toContain('hidden');
      expect(roleCell.className).toContain('sm:table-cell');

      const identityCell = el('shared-list-cell-identity')!;
      expect(identityCell.className).not.toContain('hidden');
    });

    it('applies focus-visible ring classes to interactive elements', async () => {
      await setup({ selectable: true, searchable: true });

      const selectAll = el('shared-list-select-all')!;
      expect(selectAll.className).toContain('focus-visible:ring-2');

      const search = el('shared-list-search')!;
      expect(search.className).toContain('focus-visible:ring-2');

      const sortButton = el('shared-list-sort-role')!;
      expect(sortButton.className).toContain('focus-visible:ring-2');
    });
  });
});
