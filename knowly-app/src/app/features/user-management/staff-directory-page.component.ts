import { Component, OnInit, computed, effect, inject, signal, viewChild } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { LucideHistory, LucideSquarePen, LucideTrash } from '@lucide/angular';
import { EMPTY, Observable, catchError, of } from 'rxjs';
import { buttonClass } from '../../shared/button-classes';
import { ALL_GLOBAL_PERMISSIONS } from '../../core/global-permission';
import { GlobalPermissionsService } from '../../core/global-permissions.service';
import { StaffUserService, StaffUserSummary } from '../../core/staff-user.service';
import { MandatoryProfileFields, ProfileFields } from '../../core/profile.service';
import { ErrorStateComponent } from '../../shared/error-state.component';
import { NoAccessStateComponent } from '../../shared/no-access-state.component';
import { ConfirmDialogComponent } from '../../shared/confirm-dialog.component';
import { SharedListComponent } from '../../shared/shared-list/shared-list.component';
import { SharedListColumn, SharedListRowAction } from '../../shared/shared-list/shared-list.model';
import {
  ProfileFieldsFormComponent,
  ProfileFieldsFormSubmission,
} from '../../shared/profile-fields-form.component';
import { StaffUserDetailPanelComponent } from './staff-user-detail-panel.component';

type StaffDirectoryError = 'network' | 'permission-denied' | null;

// Same empty starting point complete-profile-page.component.ts uses for the same form.
const EMPTY_FIELDS: ProfileFields = {
  fullName: '',
  taxId: '',
  countryCode: '',
  address: {
    addressLine1: '',
    addressLine2: '',
    city: '',
    stateRegion: '',
    postalCode: '',
    countryCode: '',
  },
  contacts: [],
};

@Component({
  selector: 'app-staff-directory-page',
  imports: [
    TranslocoPipe,
    ErrorStateComponent,
    NoAccessStateComponent,
    SharedListComponent,
    ProfileFieldsFormComponent,
    StaffUserDetailPanelComponent,
    ConfirmDialogComponent,
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
          @if (!showCreateProfileForm()) {
            <form
              data-testid="create-staff-user-form"
              class="enter-fluid mb-6 flex gap-2 rounded-2xl border border-ink-200/70 bg-white p-4 shadow-sm shadow-ink-900/5 dark:border-ink-800/70 dark:bg-ink-900 dark:shadow-none"
              (submit)="onStartCreateStaffUser($event)"
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
          } @else {
            <div
              data-testid="create-staff-user-profile-form"
              class="enter-fluid mb-6 rounded-2xl border border-ink-200/70 bg-white p-4 shadow-sm shadow-ink-900/5 dark:border-ink-800/70 dark:bg-ink-900 dark:shadow-none"
            >
              <p class="mb-3 text-sm text-ink-600 dark:text-ink-400">
                {{
                  'staffDirectory.createProfileIntro' | transloco: { email: newStaffUserEmail() }
                }}
              </p>
              @if (createError(); as createErrorMessage) {
                <p
                  data-testid="create-staff-user-error"
                  class="mb-3 text-sm text-red-600 dark:text-red-400"
                >
                  {{ createErrorMessage | transloco }}
                </p>
              }
              <app-profile-fields-form
                [fields]="newStaffUserProfileFields()"
                [requireAllFields]="true"
                [disabled]="creating()"
                (submitted)="onCreateStaffUser($event)"
              />
              <button
                type="button"
                data-testid="create-staff-user-cancel"
                [class]="secondaryButtonClass"
                [disabled]="creating()"
                (click)="onCancelCreateStaffUser()"
              >
                {{ 'common.cancel' | transloco }}
              </button>
            </div>
          }
        }

        <app-shared-list
          data-testid="staff-users-list"
          [title]="'staffDirectory.title' | transloco"
          [rows]="staffUsers()"
          [columns]="columns"
          [rowActions]="rowActions()"
          [rowId]="rowId"
          [emptyMessageKey]="'sharedList.empty.staffDirectory'"
        />

        @if (selectedUserId(); as userId) {
          <div class="mt-6">
            <app-staff-user-detail-panel
              #staffUserDetailPanel
              [userId]="userId"
              [viewerIsStaffAdmin]="viewerIsStaffAdmin()"
            />
          </div>
        }

        @if (pendingDelete(); as staffUser) {
          <app-confirm-dialog
            [open]="true"
            [message]="'staffDirectory.confirmDelete' | transloco: { email: staffUser.email }"
            [fetchToken]="deletionTokenFetcher(staffUser.id)"
            [retryToken]="deleteRetryToken()"
            (confirm)="confirmDelete($event)"
            (dismissed)="cancelDelete()"
          />
        }
      }
    </div>
  `,
})
export class StaffDirectoryPageComponent implements OnInit {
  private readonly staffUserService = inject(StaffUserService);
  protected readonly globalPermissionsService = inject(GlobalPermissionsService);

  protected readonly addButtonClass = buttonClass('primary');
  protected readonly secondaryButtonClass = buttonClass('secondary');
  protected readonly staffUsers = signal<StaffUserSummary[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<StaffDirectoryError>(null);
  protected readonly searchTerm = signal('');
  protected readonly newStaffUserEmail = signal('');
  protected readonly selectedUserId = signal<number | null>(null);
  protected readonly pendingDelete = signal<StaffUserSummary | null>(null);
  protected readonly deleteRetryToken = signal(0);

  private readonly staffUserDetailPanel =
    viewChild<StaffUserDetailPanelComponent>('staffUserDetailPanel');
  private pendingEditMode = false;

  // mandatory-complete-profile (backend): creating a staff user requires a full
  // MandatoryProfileFieldsDto — this two-step flow collects it via the same
  // ProfileFieldsFormComponent complete-profile-page.component.ts already uses.
  protected readonly showCreateProfileForm = signal(false);
  protected readonly newStaffUserProfileFields = signal<ProfileFields>(EMPTY_FIELDS);
  protected readonly creating = signal(false);
  protected readonly createError = signal<string | null>(null);

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

  // REQ-6/7/8: edit/delete/history become list row actions; history is itself gated
  // (appsec review, 2026-08-05) on AUDIT_TRAIL_VIEW — offered only to a viewer the
  // backend endpoint would actually accept, not merely disabled, to avoid a
  // permission-denied flash after navigating in. Follow-up: History used to navigate to its own
  // /staff/users/:userId/audit route, landing on a visually different screen than Edit's inline
  // panel for what a user flagged as the same kind of action on the same row — it now opens that
  // same panel too (the audit trail is an always-visible section inside it).
  protected readonly rowActions = computed<SharedListRowAction<StaffUserSummary>[]>(() => {
    const actions: SharedListRowAction<StaffUserSummary>[] = [
      {
        icon: LucideSquarePen,
        labelKey: 'sharedList.actions.edit',
        variant: 'secondary',
        onClick: (row) => this.openInEditMode(row.id),
      },
      {
        icon: LucideTrash,
        labelKey: 'sharedList.actions.delete',
        variant: 'danger',
        onClick: (row) => this.onDeleteStaffUser(row),
      },
    ];

    if (this.globalPermissionsService.has('AUDIT_TRAIL_VIEW')) {
      actions.push({
        icon: LucideHistory,
        labelKey: 'sharedList.actions.history',
        variant: 'secondary',
        onClick: (row) => this.openPanel(row.id),
      });
    }

    return actions;
  });

  protected readonly viewerIsStaffAdmin = computed(() =>
    ALL_GLOBAL_PERMISSIONS.every((permission) => this.globalPermissionsService.has(permission)),
  );

  protected readonly canCreate = computed(
    () => this.viewerIsStaffAdmin() || this.globalPermissionsService.has('STAFF_USER_CREATE'),
  );

  constructor() {
    // Mirrors members-page.component.ts's own deferred-call handling — the panel only
    // mounts once selectedUserId() is set, so openInEditMode() may need to wait for its
    // viewChild to resolve after the change-detection pass that renders it.
    effect(() => {
      const panel = this.staffUserDetailPanel();

      if (panel !== undefined && this.pendingEditMode) {
        this.pendingEditMode = false;
        panel.openInEditMode();
      }
    });
  }

  ngOnInit(): void {
    this.loadStaffUsers();
  }

  protected openInEditMode(userId: number): void {
    const panel = this.staffUserDetailPanel();
    this.selectedUserId.set(userId);

    if (panel !== undefined) {
      panel.openInEditMode();
    } else {
      this.pendingEditMode = true;
    }
  }

  /** Called by the "History" row action — same panel as Edit, just without triggering its
   * profile-edit toggle; the audit trail is an always-visible section further down the panel. */
  protected openPanel(userId: number): void {
    this.selectedUserId.set(userId);
  }

  protected onDeleteStaffUser(staffUser: StaffUserSummary): void {
    this.pendingDelete.set(staffUser);
  }

  protected deletionTokenFetcher(userId: number): () => Observable<string> {
    return () => this.staffUserService.generateDeletionConfirmationToken(userId);
  }

  protected confirmDelete(word: string): void {
    const staffUser = this.pendingDelete();

    if (staffUser === null) {
      return;
    }

    this.staffUserService
      .delete(staffUser.id, word)
      .pipe(
        catchError((err) => {
          if (err.status === 400) {
            this.deleteRetryToken.update((n) => n + 1);
          } else {
            this.pendingDelete.set(null);
            this.deleteRetryToken.set(0);
            this.error.set(err.status === 403 ? 'permission-denied' : 'network');
          }
          return EMPTY;
        }),
      )
      .subscribe(() => {
        this.pendingDelete.set(null);
        this.deleteRetryToken.set(0);
        this.loadStaffUsers();
      });
  }

  protected cancelDelete(): void {
    this.pendingDelete.set(null);
    this.deleteRetryToken.set(0);
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

  protected onStartCreateStaffUser(event: Event): void {
    event.preventDefault();

    if (!this.newStaffUserEmail()) {
      return;
    }

    this.createError.set(null);
    this.newStaffUserProfileFields.set(EMPTY_FIELDS);
    this.showCreateProfileForm.set(true);
  }

  protected onCancelCreateStaffUser(): void {
    this.showCreateProfileForm.set(false);
    this.newStaffUserEmail.set('');
    this.createError.set(null);
  }

  protected onCreateStaffUser({ fields }: ProfileFieldsFormSubmission): void {
    if (this.creating()) {
      return;
    }

    const email = this.newStaffUserEmail();
    this.newStaffUserProfileFields.set(fields);
    this.createError.set(null);
    this.creating.set(true);

    const profile: MandatoryProfileFields = {
      ...fields,
      contacts: fields.contacts.map((contact) => ({
        type: contact.type,
        value: contact.value,
        label: contact.label,
        isPrimary: contact.isPrimary,
      })),
    };

    this.staffUserService
      .create(email, profile)
      .pipe(
        catchError((err) => {
          this.creating.set(false);

          if (err.status === 403) {
            this.error.set('permission-denied');
          } else if (err.status === 409 && err.error?.code === 'TAX_ID_ALREADY_EXISTS') {
            this.createError.set('staffDirectory.createProfileTaxIdConflict');
          } else if (err.status === 400 || err.status === 409) {
            this.createError.set('staffDirectory.createProfileError');
          } else {
            this.error.set('network');
          }

          return of(null);
        }),
      )
      .subscribe((result) => {
        if (result !== null) {
          this.creating.set(false);
          this.showCreateProfileForm.set(false);
          this.newStaffUserEmail.set('');
          this.loadStaffUsers();
        }
      });
  }
}
