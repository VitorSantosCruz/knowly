import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { LucideSquarePen } from '@lucide/angular';
import { EMPTY, Observable, catchError, finalize, forkJoin, of } from 'rxjs';
import { buttonClass } from '../../shared/button-classes';
import { ConfirmDialogComponent } from '../../shared/confirm-dialog.component';
import { ErrorStateComponent } from '../../shared/error-state.component';
import { NoAccessStateComponent } from '../../shared/no-access-state.component';
import { SharedListComponent } from '../../shared/shared-list/shared-list.component';
import { SharedListColumn, SharedListRowAction } from '../../shared/shared-list/shared-list.model';
import {
  GlobalAccessGroup,
  StaffUserDetail,
  StaffUserService,
  StaffUserSummary,
} from '../../core/staff-user.service';
import { ALL_GLOBAL_PERMISSIONS, GlobalPermission } from '../../core/global-permission';
import { GlobalPermissionsService } from '../../core/global-permissions.service';
import { PermissionListComponent } from '../../shared/permission-list/permission-list.component';
import { PermissionListRow } from '../../shared/permission-list/permission-list.model';

type PageError = 'network' | 'permission-denied' | null;

/**
 * REQ-20 through REQ-24: access groups as an independent entity, managed on
 * their own screen instead of from a staff user's detail view (which now
 * only shows the groups a given user already belongs to, per Task 5's
 * REQ-24 removal of inline group creation).
 *
 * There is no "list a group's members" backend endpoint (confirmed against
 * `StaffController.java`/`StaffService.java`) — membership is derived by
 * fetching every non-admin candidate's own detail
 * (`GET /api/staff/users/{id}/permissions`, which already returns
 * `accessGroups`) once a group is selected. This is an N+1 pattern, but the
 * only data source available without a new backend endpoint, and acceptable
 * for the staff roster's expected small size (an internal admin screen, not
 * a public-facing list). `STAFF_ADMIN` users are filtered out before this
 * fetch even happens (REQ-23) — they are never candidates and are never
 * queried for membership.
 */
@Component({
  selector: 'app-access-group-management-page',
  imports: [
    TranslocoPipe,
    ErrorStateComponent,
    NoAccessStateComponent,
    SharedListComponent,
    ConfirmDialogComponent,
    PermissionListComponent,
  ],
  template: `
    <div data-testid="access-group-management-page" class="page-shell">
      @if (error() === 'permission-denied') {
        <app-no-access-state />
      } @else if (error() === 'network') {
        <app-error-state />
      } @else {
        <form
          data-testid="create-access-group-form"
          class="enter-fluid mb-6 flex gap-2 rounded-2xl border border-ink-200/70 bg-white p-4 shadow-sm shadow-ink-900/5 dark:border-ink-800/70 dark:bg-ink-900 dark:shadow-none"
          (submit)="onCreateGroup($event)"
        >
          <input
            data-testid="access-group-name-input"
            type="text"
            name="name"
            [value]="newGroupName()"
            (input)="newGroupName.set($any($event.target).value)"
            [placeholder]="'accessGroupManagement.namePlaceholder' | transloco"
            class="flex-1 rounded-lg border border-ink-200 bg-white px-3 py-2 text-sm text-ink-900 focus:border-signal-500 focus:ring-1 focus:ring-signal-500 focus:outline-none dark:border-ink-700 dark:bg-ink-800 dark:text-white"
          />
          <button type="submit" [class]="primaryButtonClass">
            {{ 'staffDirectory.createGroup' | transloco }}
          </button>
        </form>

        <app-shared-list
          data-testid="access-groups-list"
          [title]="'accessGroupManagement.title' | transloco"
          [rows]="groups()"
          [columns]="columns"
          [rowActions]="rowActions"
          [rowId]="rowId"
          [loading]="groupsLoading()"
          [emptyMessageKey]="'accessGroupManagement.empty'"
        />

        @if (selectedGroup(); as group) {
          <div
            data-testid="access-group-members-panel"
            class="enter-fluid mt-6 rounded-2xl border border-ink-200/70 bg-white p-6 shadow-sm shadow-ink-900/5 dark:border-ink-800/70 dark:bg-ink-900 dark:shadow-none"
          >
            <h3 class="mb-4 text-sm font-semibold text-ink-900 dark:text-white">
              {{ 'accessGroupManagement.membersOf' | transloco: { group: group.name } }}
            </h3>

            <section class="mb-5">
              <h4 class="mb-2 text-sm font-medium text-ink-700 dark:text-ink-300">
                {{ 'tenantAccessGroupManagement.permissions' | transloco }}
              </h4>
              @if (permissionActionError()) {
                <p
                  data-testid="permission-action-error"
                  role="alert"
                  class="mb-2 rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700 dark:border-red-900/50 dark:bg-red-950/30 dark:text-red-400"
                >
                  {{ 'tenantAccessGroupManagement.permissionActionError' | transloco }}
                </p>
              }
              <app-permission-list
                [rows]="permissionListRows()"
                [mode]="permissionListMode()"
                [disabled]="pendingPermissionToggles().size > 0"
                (toggle)="onTogglePermission($any($event))"
              />
            </section>

            @if (candidatesLoading()) {
              <p class="text-sm text-ink-400">…</p>
            } @else {
              <section class="mb-5">
                <h4 class="mb-2 text-sm font-medium text-ink-700 dark:text-ink-300">
                  {{ 'accessGroupManagement.members' | transloco }}
                </h4>
                @if (members().length === 0) {
                  <p class="text-sm text-ink-500 dark:text-ink-400">
                    {{ 'accessGroupManagement.noMembers' | transloco }}
                  </p>
                }
                <ul class="flex flex-col gap-1">
                  @for (user of members(); track user.id) {
                    <li
                      class="flex items-center justify-between rounded-lg bg-ink-50 px-3 py-1.5 text-sm text-ink-800 dark:bg-ink-800/50 dark:text-ink-100"
                    >
                      {{ user.email }}
                      <button
                        type="button"
                        [attr.data-testid]="'access-group-unassign-' + user.id"
                        (click)="onUnassign(user)"
                        class="text-red-600 transition-colors duration-fast ease-fluid hover:text-red-700 dark:text-red-400 dark:hover:text-red-300"
                      >
                        {{ 'staffDirectory.unassign' | transloco }}
                      </button>
                    </li>
                  }
                </ul>
              </section>

              <section>
                <h4 class="mb-2 text-sm font-medium text-ink-700 dark:text-ink-300">
                  {{ 'accessGroupManagement.assignable' | transloco }}
                </h4>
                <ul class="flex flex-col gap-1">
                  @for (user of assignable(); track user.id) {
                    <li
                      class="flex items-center justify-between text-sm text-ink-600 dark:text-ink-400"
                    >
                      {{ user.email }}
                      <button
                        type="button"
                        [attr.data-testid]="'access-group-assign-' + user.id"
                        (click)="onAssign(user)"
                        class="text-signal-600 transition-colors duration-fast ease-fluid hover:text-signal-700 dark:text-signal-400 dark:hover:text-signal-300"
                      >
                        {{ 'staffDirectory.assign' | transloco }}
                      </button>
                    </li>
                  }
                </ul>
              </section>
            }
          </div>
        }
      }
    </div>

    @if (pendingUnassign(); as pending) {
      <app-confirm-dialog
        [open]="true"
        [message]="
          'staffDirectory.confirmUnassignGroup'
            | transloco: { group: pending.group.name, email: pending.user.email }
        "
        [fetchToken]="unassignTokenFetcher(pending.user.id, pending.group.id)"
        [retryToken]="unassignRetryToken()"
        (confirm)="confirmUnassign($event)"
        (dismissed)="cancelUnassign()"
      />
    }
  `,
})
export class AccessGroupManagementPageComponent implements OnInit {
  private readonly staffUserService = inject(StaffUserService);
  private readonly globalPermissionsService = inject(GlobalPermissionsService);

  protected readonly primaryButtonClass = buttonClass('primary');
  protected readonly allPermissions = ALL_GLOBAL_PERMISSIONS;

  protected readonly groups = signal<GlobalAccessGroup[]>([]);
  protected readonly groupsLoading = signal(true);
  protected readonly error = signal<PageError>(null);
  protected readonly newGroupName = signal('');

  protected readonly selectedGroup = signal<GlobalAccessGroup | null>(null);
  protected readonly candidatesLoading = signal(false);
  // Non-admin candidates' own fetched details (source of truth for group membership),
  // keyed by userId — see class doc for why this is an N+1 fetch.
  protected readonly candidateDetails = signal<Map<number, StaffUserDetail>>(new Map());

  // role-permission-management-ui: same shape as the tenant roles page (task 7) --
  // seeded from the selected group's `permissions` field on `selectGroup()`.
  protected readonly groupPermissions = signal<Set<GlobalPermission>>(new Set());
  protected readonly pendingPermissionToggles = signal<Set<GlobalPermission>>(new Set());
  protected readonly permissionActionError = signal<'network' | 'permission-denied' | null>(null);

  protected readonly permissionListRows = computed<PermissionListRow[]>(() =>
    this.allPermissions.map((permission) => ({
      value: permission,
      granted: this.groupPermissions().has(permission),
    })),
  );

  // Gated on STAFF_PERMISSION_MANAGE -- the same permission that already gates reaching this
  // page at all (nav guard) -- for edit vs. read-only.
  protected readonly permissionListMode = computed(() =>
    this.globalPermissionsService.has('STAFF_PERMISSION_MANAGE') ? 'editable' : 'readonly',
  );

  protected readonly pendingUnassign = signal<{
    user: StaffUserSummary;
    group: GlobalAccessGroup;
  } | null>(null);
  protected readonly unassignRetryToken = signal(0);

  protected readonly rowId = (row: GlobalAccessGroup): number => row.id;

  protected readonly columns: SharedListColumn<GlobalAccessGroup>[] = [
    {
      key: 'name',
      headerKey: 'accessGroupManagement.columns.name',
      sortable: true,
      render: (row) => ({ type: 'text', value: row.name }),
    },
  ];

  protected readonly rowActions: SharedListRowAction<GlobalAccessGroup>[] = [
    {
      icon: LucideSquarePen,
      labelKey: 'sharedList.actions.edit',
      variant: 'secondary',
      onClick: (row) => this.selectGroup(row),
    },
  ];

  protected readonly members = computed(() => {
    const group = this.selectedGroup();
    if (!group) {
      return [];
    }
    const details = this.candidateDetails();
    return [...details.values()]
      .filter((detail) => detail.accessGroups.some((g) => g.id === group.id))
      .map((detail) => ({ id: detail.userId, email: detail.email, globalRole: detail.globalRole }));
  });

  protected readonly assignable = computed(() => {
    const group = this.selectedGroup();
    if (!group) {
      return [];
    }
    const details = this.candidateDetails();
    return [...details.values()]
      .filter((detail) => !detail.accessGroups.some((g) => g.id === group.id))
      .map((detail) => ({ id: detail.userId, email: detail.email, globalRole: detail.globalRole }));
  });

  ngOnInit(): void {
    this.loadGroups();
  }

  private loadGroups(): void {
    this.groupsLoading.set(true);
    this.error.set(null);

    this.staffUserService
      .listAccessGroups()
      .pipe(
        catchError((err) => {
          this.error.set(err.status === 403 ? 'permission-denied' : 'network');
          return of<GlobalAccessGroup[]>([]);
        }),
      )
      .subscribe((groups) => {
        this.groups.set(groups);
        this.groupsLoading.set(false);
      });
  }

  protected onCreateGroup(event: Event): void {
    event.preventDefault();
    const name = this.newGroupName();

    if (!name) {
      return;
    }

    this.staffUserService
      .createAccessGroup(name)
      .pipe(
        catchError((err) => {
          this.error.set(err.status === 403 ? 'permission-denied' : 'network');
          return of(null);
        }),
      )
      .subscribe((result) => {
        if (result !== null) {
          this.newGroupName.set('');
          this.loadGroups();
        }
      });
  }

  protected selectGroup(group: GlobalAccessGroup): void {
    this.selectedGroup.set(group);
    this.groupPermissions.set(new Set(group.permissions ?? []));
    this.permissionActionError.set(null);
    this.loadCandidates();
  }

  // Mirrors TenantAccessGroupManagementPageComponent#onTogglePermission's optimistic-toggle-with-
  // rollback + in-flight guard shape exactly (task 7), against the staff/global scope.
  protected onTogglePermission(permission: GlobalPermission): void {
    const group = this.selectedGroup();

    if (!group || this.pendingPermissionToggles().size > 0) {
      return;
    }

    const wasGranted = this.groupPermissions().has(permission);

    this.groupPermissions.update((current) => {
      const next = new Set(current);
      if (wasGranted) {
        next.delete(permission);
      } else {
        next.add(permission);
      }
      return next;
    });
    this.permissionActionError.set(null);
    this.pendingPermissionToggles.update((current) => new Set(current).add(permission));

    const request = wasGranted
      ? this.staffUserService.revokeAccessGroupPermission(group.id, permission)
      : this.staffUserService.grantAccessGroupPermission(group.id, permission);

    request
      .pipe(
        catchError((err) => {
          this.groupPermissions.update((current) => {
            const reverted = new Set(current);
            if (wasGranted) {
              reverted.add(permission);
            } else {
              reverted.delete(permission);
            }
            return reverted;
          });
          this.permissionActionError.set(err.status === 403 ? 'permission-denied' : 'network');
          return EMPTY;
        }),
        finalize(() => {
          this.pendingPermissionToggles.update((current) => {
            const next = new Set(current);
            next.delete(permission);
            return next;
          });
        }),
      )
      .subscribe();
  }

  // REQ-23: STAFF_ADMIN is filtered out client-side before any per-user detail fetch —
  // it's never offered as an assignment candidate, and its membership is never queried.
  private loadCandidates(): void {
    this.candidatesLoading.set(true);

    this.staffUserService
      .list()
      .pipe(
        catchError((err) => {
          this.error.set(err.status === 403 ? 'permission-denied' : 'network');
          return of<StaffUserSummary[]>([]);
        }),
      )
      .subscribe((users) => {
        const nonAdmins = users.filter((user) => user.globalRole !== 'STAFF_ADMIN');

        if (nonAdmins.length === 0) {
          this.candidateDetails.set(new Map());
          this.candidatesLoading.set(false);
          return;
        }

        forkJoin(
          nonAdmins.map((user) =>
            this.staffUserService.getDetail(user.id).pipe(catchError(() => EMPTY)),
          ),
        ).subscribe((details) => {
          const map = new Map<number, StaffUserDetail>();
          details.forEach((detail) => map.set(detail.userId, detail));
          this.candidateDetails.set(map);
          this.candidatesLoading.set(false);
        });
      });
  }

  protected onAssign(user: StaffUserSummary): void {
    const group = this.selectedGroup();
    if (!group) {
      return;
    }

    this.staffUserService
      .assignAccessGroup(user.id, group.id)
      .pipe(
        catchError((err) => {
          this.error.set(err.status === 403 ? 'permission-denied' : 'network');
          return EMPTY;
        }),
      )
      .subscribe(() => {
        this.loadCandidates();
      });
  }

  protected onUnassign(user: StaffUserSummary): void {
    const group = this.selectedGroup();
    if (!group) {
      return;
    }
    this.pendingUnassign.set({ user, group });
  }

  protected unassignTokenFetcher(userId: number, accessGroupId: number): () => Observable<string> {
    return () => this.staffUserService.generateAccessGroupUnassignmentToken(userId, accessGroupId);
  }

  protected confirmUnassign(word: string): void {
    const pending = this.pendingUnassign();
    if (!pending) {
      return;
    }

    this.staffUserService
      .unassignAccessGroup(pending.user.id, pending.group.id, word)
      .pipe(
        catchError((err) => {
          if (err.status === 400) {
            this.unassignRetryToken.update((n) => n + 1);
          } else {
            this.pendingUnassign.set(null);
            this.unassignRetryToken.set(0);
            this.error.set(err.status === 403 ? 'permission-denied' : 'network');
          }
          return EMPTY;
        }),
      )
      .subscribe(() => {
        this.pendingUnassign.set(null);
        this.unassignRetryToken.set(0);
        this.loadCandidates();
      });
  }

  protected cancelUnassign(): void {
    this.pendingUnassign.set(null);
    this.unassignRetryToken.set(0);
  }
}
