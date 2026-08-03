import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { LucideSquarePen } from '@lucide/angular';
import { catchError, of } from 'rxjs';
import { buttonClass } from '../../shared/button-classes';
import { ALL_GLOBAL_PERMISSIONS } from '../../core/global-permission';
import { GlobalPermissionsService } from '../../core/global-permissions.service';
import { StaffUserService, StaffUserSummary } from '../../core/staff-user.service';
import { ErrorStateComponent } from '../../shared/error-state.component';
import { NoAccessStateComponent } from '../../shared/no-access-state.component';
import { SharedListComponent } from '../../shared/shared-list/shared-list.component';
import { SharedListColumn, SharedListRowAction } from '../../shared/shared-list/shared-list.model';
import { StaffUserDetailPanelComponent } from './staff-user-detail-panel.component';

type StaffDirectoryError = 'network' | 'permission-denied' | null;

@Component({
  selector: 'app-staff-directory-page',
  imports: [
    TranslocoPipe,
    ErrorStateComponent,
    NoAccessStateComponent,
    SharedListComponent,
    StaffUserDetailPanelComponent,
  ],
  template: `
    <div data-testid="staff-directory-page" class="page-shell">
      @if (loading()) {
        <p data-testid="loading-state" class="text-sm text-ink-400">…</p>
      } @else if (error() === 'permission-denied') {
        <app-no-access-state />
      } @else if (error() === 'network') {
        <app-error-state />
      } @else {
        <input
          data-testid="staff-search-email"
          type="search"
          [value]="searchTerm()"
          (input)="onSearch($any($event.target).value)"
          [placeholder]="'staffDirectory.searchPlaceholder' | transloco"
          class="mb-4 w-full rounded-lg border border-ink-200 bg-white px-3 py-2 text-sm text-ink-900 focus:border-signal-500 focus:ring-1 focus:ring-signal-500 focus:outline-none dark:border-ink-700 dark:bg-ink-800 dark:text-white"
        />

        @if (canCreate()) {
          <form
            data-testid="create-staff-user-form"
            class="enter-fluid mb-6 flex gap-2 rounded-2xl border border-ink-200/70 bg-white p-4 shadow-sm shadow-ink-900/5 dark:border-ink-800/70 dark:bg-ink-900 dark:shadow-none"
            (submit)="onCreateStaffUser($event)"
          >
            <input
              data-testid="create-staff-user-email"
              type="email"
              name="email"
              [value]="newStaffUserEmail()"
              (input)="newStaffUserEmail.set($any($event.target).value)"
              class="flex-1 rounded-lg border border-ink-200 bg-white px-3 py-2 text-sm text-ink-900 focus:border-signal-500 focus:ring-1 focus:ring-signal-500 focus:outline-none dark:border-ink-700 dark:bg-ink-800 dark:text-white"
            />
            <button type="submit" [class]="addButtonClass">
              {{ 'staffDirectory.create' | transloco }}
            </button>
          </form>
        }

        <app-shared-list
          data-testid="staff-users-list"
          [title]="'staffDirectory.title' | transloco"
          [rows]="staffUsers()"
          [columns]="columns"
          [rowActions]="rowActions"
          [rowId]="rowId"
          [emptyMessageKey]="'sharedList.empty.staffDirectory'"
        />

        @if (selectedUserId(); as userId) {
          <div class="mt-6">
            <app-staff-user-detail-panel
              [userId]="userId"
              [viewerIsStaffAdmin]="viewerIsStaffAdmin()"
            />
          </div>
        }
      }
    </div>
  `,
})
export class StaffDirectoryPageComponent implements OnInit {
  private readonly staffUserService = inject(StaffUserService);
  protected readonly globalPermissionsService = inject(GlobalPermissionsService);

  protected readonly addButtonClass = buttonClass('primary');
  protected readonly staffUsers = signal<StaffUserSummary[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<StaffDirectoryError>(null);
  protected readonly searchTerm = signal('');
  protected readonly newStaffUserEmail = signal('');
  protected readonly selectedUserId = signal<number | null>(null);

  protected readonly rowId = (row: StaffUserSummary): number => row.id;

  protected readonly columns: SharedListColumn<StaffUserSummary>[] = [
    {
      key: 'email',
      headerKey: 'staffDirectory.columns.email',
      sortable: true,
      render: (row) => ({
        type: 'identity',
        primary: row.email,
        initials: row.email.charAt(0).toUpperCase(),
      }),
    },
    {
      key: 'role',
      headerKey: 'staffDirectory.columns.role',
      essential: false,
      render: (row) => ({
        type: 'pill',
        labelKey: `staffDirectory.roles.${row.globalRole}`,
        colorClass:
          row.globalRole === 'STAFF_ADMIN'
            ? 'bg-signal-100 text-signal-700 dark:bg-signal-900/40 dark:text-signal-300'
            : 'bg-ink-100 text-ink-700 dark:bg-ink-800 dark:text-ink-300',
      }),
    },
  ];

  protected readonly rowActions: SharedListRowAction<StaffUserSummary>[] = [
    {
      icon: LucideSquarePen,
      labelKey: 'sharedList.actions.edit',
      variant: 'secondary',
      onClick: (row) => this.selectedUserId.set(row.id),
    },
  ];

  protected readonly viewerIsStaffAdmin = computed(() =>
    ALL_GLOBAL_PERMISSIONS.every((permission) => this.globalPermissionsService.has(permission)),
  );

  protected readonly canCreate = computed(
    () => this.viewerIsStaffAdmin() || this.globalPermissionsService.has('STAFF_USER_CREATE'),
  );

  ngOnInit(): void {
    this.loadStaffUsers();
  }

  private loadStaffUsers(): void {
    this.loading.set(true);
    this.error.set(null);

    this.staffUserService
      .list(this.searchTerm() || undefined)
      .pipe(
        catchError((err) => {
          this.error.set(err.status === 403 ? 'permission-denied' : 'network');
          return of<StaffUserSummary[]>([]);
        }),
      )
      .subscribe((staffUsers) => {
        this.staffUsers.set(staffUsers);
        this.loading.set(false);
      });
  }

  protected onSearch(term: string): void {
    this.searchTerm.set(term);
    this.loadStaffUsers();
  }

  protected onCreateStaffUser(event: Event): void {
    event.preventDefault();
    const email = this.newStaffUserEmail();

    if (!email) {
      return;
    }

    this.staffUserService
      .create(email)
      .pipe(
        catchError((err) => {
          this.error.set(err.status === 403 ? 'permission-denied' : 'network');
          return of(null);
        }),
      )
      .subscribe((result) => {
        if (result !== null) {
          this.newStaffUserEmail.set('');
          this.loadStaffUsers();
        }
      });
  }
}
