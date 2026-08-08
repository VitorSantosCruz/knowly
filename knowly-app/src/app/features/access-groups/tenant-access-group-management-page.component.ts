import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { LucideSquarePen, LucideTrash } from '@lucide/angular';
import { EMPTY, Observable, catchError, finalize, forkJoin, of } from 'rxjs';
import { buttonClass } from '../../shared/button-classes';
import { ConfirmDialogComponent } from '../../shared/confirm-dialog.component';
import { ErrorStateComponent } from '../../shared/error-state.component';
import { NoAccessStateComponent } from '../../shared/no-access-state.component';
import { SharedListComponent } from '../../shared/shared-list/shared-list.component';
import { SharedListColumn, SharedListRowAction } from '../../shared/shared-list/shared-list.model';
import { ALL_PERMISSIONS, Permission } from '../../core/permission';
import { AccessGroup, Member, MemberDetail, MemberService } from '../../core/member.service';
import { ActiveTenantService } from '../../core/active-tenant.service';
import { GlobalPermissionsService } from '../../core/global-permissions.service';
import { MemberAccessGroupAssignmentComponent } from './member-access-group-assignment.component';
import { PermissionListComponent } from '../../shared/permission-list/permission-list.component';
import { PermissionListRow } from '../../shared/permission-list/permission-list.model';

type PageError = 'network' | 'permission-denied' | null;

/**
 * REQ-1 through REQ-17 (tenant-access-group-management): tenant-scoped `AccessGroup`s as their
 * own screen, mirroring `AccessGroupManagementPageComponent` (the *global* staff equivalent)'s
 * layout, plus this screen's two extra capabilities: granting a permission to a group and
 * deleting one outright.
 *
 * Every granular action is gated on `viewerIsMemberAdmin() || globalPermissionsService.has(...)`
 * — see `tenant-access-group-management.guard.ts`'s own doc comment for why: the backend's
 * `requireAdminOfTenantOrStaff` lets a real tenant `MEMBER_ADMIN` through unconditionally on
 * every one of these endpoints regardless of any `GlobalPermission`, so gating purely on the
 * `GlobalPermission` (as PLAN.md's per-action table names them) would incorrectly hide every
 * control from an ordinary `MEMBER_ADMIN` — this mirrors
 * `member-detail-panel.component.ts`'s established `viewerCanManageDirectPermissions` shape
 * (see PLAN.md's "Deviations from this PLAN").
 *
 * The roster (REQ-3) is derived from one `list()` call plus one `getDetail()` call per member,
 * cached in `memberDetails` for the lifetime of the current screen visit — switching the
 * selected group re-filters that cache rather than re-fetching (PLAN's performance decision).
 */
@Component({
  selector: 'app-tenant-access-group-management-page',
  imports: [
    TranslocoPipe,
    ErrorStateComponent,
    NoAccessStateComponent,
    SharedListComponent,
    ConfirmDialogComponent,
    MemberAccessGroupAssignmentComponent,
    PermissionListComponent,
  ],
  template: `
    <div data-testid="tenant-access-group-management-page" class="page-shell">
      @if (error() === 'permission-denied') {
        <app-no-access-state />
      } @else if (error() === 'network') {
        <app-error-state />
      } @else {
        @if (canCreate()) {
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
        }

        <app-shared-list
          data-testid="tenant-access-groups-list"
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
            data-testid="tenant-access-group-detail"
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
                @if (currentRoster().length === 0) {
                  <p class="text-sm text-ink-500 dark:text-ink-400">
                    {{ 'accessGroupManagement.noMembers' | transloco }}
                  </p>
                }
                <ul class="flex flex-col gap-1">
                  @for (member of currentRoster(); track member.membershipId) {
                    <li
                      class="flex items-center justify-between rounded-lg bg-ink-50 px-3 py-1.5 text-sm text-ink-800 dark:bg-ink-800/50 dark:text-ink-100"
                    >
                      {{ member.email }}
                      @if (canUnassign()) {
                        <button
                          type="button"
                          [attr.data-testid]="'tenant-access-group-unassign-' + member.membershipId"
                          (click)="onUnassign(member.membershipId)"
                          class="text-red-600 transition-colors duration-fast ease-fluid hover:text-red-700 dark:text-red-400 dark:hover:text-red-300"
                        >
                          {{ 'staffDirectory.unassign' | transloco }}
                        </button>
                      }
                    </li>
                  }
                </ul>
              </section>

              @if (canAssign()) {
                <section>
                  <h4 class="mb-2 text-sm font-medium text-ink-700 dark:text-ink-300">
                    {{ 'accessGroupManagement.assignable' | transloco }}
                  </h4>
                  <ul class="flex flex-col gap-1">
                    @for (member of assignableCandidates(); track member.membershipId) {
                      <li class="flex flex-col gap-1 text-sm text-ink-600 dark:text-ink-400">
                        <div class="flex items-center justify-between">
                          {{ member.email }}
                          <button
                            type="button"
                            [attr.data-testid]="'tenant-access-group-assign-' + member.membershipId"
                            (click)="onOpenAssignment(member)"
                            class="text-signal-600 transition-colors duration-fast ease-fluid hover:text-signal-700 dark:text-signal-400 dark:hover:text-signal-300"
                          >
                            {{ 'staffDirectory.assign' | transloco }}
                          </button>
                        </div>
                        @if (assigningMember()?.membershipId === member.membershipId) {
                          <app-member-access-group-assignment
                            [allGroups]="groups()"
                            [assignedGroupIds]="assignedGroupIdsOf(member)"
                            (submitted)="onAssignmentSubmitted(member.membershipId, $event)"
                          />
                        }
                      </li>
                    }
                  </ul>
                </section>
              }
            }

            @if (canDelete()) {
              <div class="mt-6 border-t border-ink-200/70 pt-4 dark:border-ink-800/70">
                <button
                  type="button"
                  data-testid="tenant-access-group-delete-button"
                  (click)="onDeleteGroup(group)"
                  class="text-sm font-medium text-red-600 transition-colors duration-fast ease-fluid hover:text-red-700 dark:text-red-400 dark:hover:text-red-300"
                >
                  {{ 'tenantAccessGroupManagement.deleteGroup' | transloco }}
                </button>
              </div>
            }
          </div>
        }
      }
    </div>

    @if (pendingUnassign(); as membershipId) {
      <app-confirm-dialog
        [open]="true"
        [message]="
          'members.confirmUnassignGroup'
            | transloco: { group: selectedGroup()?.name ?? '', email: '' }
        "
        [fetchToken]="unassignTokenFetcher(membershipId)"
        [retryToken]="unassignRetryToken()"
        (confirm)="confirmUnassign($event)"
        (dismissed)="cancelUnassign()"
      />
    }

    @if (pendingDelete(); as group) {
      <app-confirm-dialog
        [open]="true"
        [message]="
          'tenantAccessGroupManagement.confirmDeleteGroup' | transloco: { group: group.name }
        "
        [fetchToken]="deleteTokenFetcher(group.id)"
        [retryToken]="deleteRetryToken()"
        (confirm)="confirmDelete($event)"
        (dismissed)="cancelDelete()"
      />
    }
  `,
})
export class TenantAccessGroupManagementPageComponent implements OnInit {
  private readonly memberService = inject(MemberService);
  private readonly activeTenantService = inject(ActiveTenantService);
  private readonly globalPermissionsService = inject(GlobalPermissionsService);

  protected readonly primaryButtonClass = buttonClass('primary');
  protected readonly secondaryButtonClass = buttonClass('secondary');
  protected readonly allPermissions = ALL_PERMISSIONS;

  protected readonly groups = signal<AccessGroup[]>([]);
  protected readonly groupsLoading = signal(true);
  protected readonly error = signal<PageError>(null);
  protected readonly newGroupName = signal('');

  protected readonly selectedGroup = signal<AccessGroup | null>(null);
  protected readonly candidatesLoading = signal(false);
  // Cached for the lifetime of the current screen visit, keyed by membershipId -- switching
  // groups re-filters this rather than re-fetching (PLAN's performance decision).
  protected readonly memberDetails = signal<Map<number, MemberDetail>>(new Map());

  // role-permission-management-ui: seeded from the selected group's `permissions` field on
  // `selectGroup()`; the permission-list rows/toggle-optimism read/write this alone, so a toggle
  // failure can revert it without re-fetching or mutating the cached `groups()` array.
  protected readonly groupPermissions = signal<Set<Permission>>(new Set());
  // AppSec review (2026-08-08): in-flight guard -- disables the whole list while any row's own
  // grant/revoke call hasn't resolved yet, preventing a fast double-click from firing two
  // out-of-order requests on the same row.
  protected readonly pendingPermissionToggles = signal<Set<Permission>>(new Set());
  protected readonly permissionActionError = signal<'network' | 'permission-denied' | null>(null);

  protected readonly permissionListRows = computed<PermissionListRow[]>(() =>
    this.allPermissions.map((permission) => ({
      value: permission,
      granted: this.groupPermissions().has(permission),
    })),
  );

  protected readonly canRevokePermission = computed(
    () =>
      this.viewerIsMemberAdmin() ||
      this.globalPermissionsService.has('TENANT_PERMISSION_GRANT_DELETE'),
  );

  // Always shown (req 6 says the granted set is always visible) -- editable only when at least
  // one of grant/revoke is allowed, per PLAN.
  protected readonly permissionListMode = computed(() =>
    this.canGrantPermission() || this.canRevokePermission() ? 'editable' : 'readonly',
  );

  protected readonly assigningMember = signal<MemberDetail | null>(null);

  protected readonly pendingUnassign = signal<number | null>(null);
  protected readonly unassignRetryToken = signal(0);

  protected readonly pendingDelete = signal<AccessGroup | null>(null);
  protected readonly deleteRetryToken = signal(0);

  private readonly viewerIsMemberAdmin = computed(
    () => this.activeTenantService.activeTenantRole() === 'MEMBER_ADMIN',
  );

  protected readonly canCreate = computed(
    () =>
      this.viewerIsMemberAdmin() || this.globalPermissionsService.has('TENANT_ACCESS_GROUP_CREATE'),
  );

  protected readonly canDelete = computed(
    () =>
      this.viewerIsMemberAdmin() || this.globalPermissionsService.has('TENANT_ACCESS_GROUP_DELETE'),
  );

  protected readonly canGrantPermission = computed(
    () =>
      this.viewerIsMemberAdmin() ||
      this.globalPermissionsService.has('TENANT_PERMISSION_GRANT_CREATE'),
  );

  protected readonly canAssign = computed(
    () =>
      this.viewerIsMemberAdmin() ||
      this.globalPermissionsService.has('TENANT_PERMISSION_GRANT_CREATE'),
  );

  protected readonly canUnassign = computed(
    () =>
      this.viewerIsMemberAdmin() ||
      this.globalPermissionsService.has('TENANT_PERMISSION_GRANT_DELETE'),
  );

  protected readonly rowId = (row: AccessGroup): number => row.id;

  protected readonly columns: SharedListColumn<AccessGroup>[] = [
    {
      key: 'name',
      headerKey: 'accessGroupManagement.columns.name',
      sortable: true,
      render: (row) => ({ type: 'text', value: row.name }),
    },
  ];

  protected readonly rowActions: SharedListRowAction<AccessGroup>[] = [
    {
      icon: LucideSquarePen,
      labelKey: 'sharedList.actions.edit',
      variant: 'secondary',
      onClick: (row) => this.selectGroup(row),
    },
    {
      icon: LucideTrash,
      labelKey: 'sharedList.actions.delete',
      variant: 'danger',
      hidden: () => !this.canDelete(),
      onClick: (row) => this.onDeleteGroup(row),
    },
  ];

  protected readonly currentRoster = computed(() => {
    const group = this.selectedGroup();
    if (!group) {
      return [];
    }
    return [...this.memberDetails().values()].filter((detail) =>
      detail.accessGroups.some((g) => g.id === group.id),
    );
  });

  protected readonly assignableCandidates = computed(() => {
    const group = this.selectedGroup();
    if (!group) {
      return [];
    }
    return [...this.memberDetails().values()].filter(
      (detail) => !detail.accessGroups.some((g) => g.id === group.id),
    );
  });

  ngOnInit(): void {
    this.loadGroups();
  }

  private get tenantId(): number {
    return this.activeTenantService.activeTenantId() ?? 0;
  }

  private loadGroups(): void {
    this.groupsLoading.set(true);
    this.error.set(null);

    this.memberService
      .listAccessGroups(this.tenantId)
      .pipe(
        catchError((err) => {
          this.error.set(err.status === 403 ? 'permission-denied' : 'network');
          return of<AccessGroup[]>([]);
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

    this.memberService
      .createAccessGroup(this.tenantId, name)
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

  selectGroup(group: AccessGroup): void {
    this.selectedGroup.set(group);
    this.groupPermissions.set(new Set(group.permissions));
    this.permissionActionError.set(null);
    this.assigningMember.set(null);

    if (this.memberDetails().size > 0 || this.candidatesLoading()) {
      // Already fetched once this visit -- currentRoster()/assignableCandidates() re-filter
      // reactively off the newly selected group id, no re-fetch needed (PLAN's cache-reuse rule).
      return;
    }

    this.loadRoster();
  }

  private loadRoster(): void {
    this.candidatesLoading.set(true);

    this.memberService
      .list(this.tenantId)
      .pipe(
        catchError((err) => {
          this.error.set(err.status === 403 ? 'permission-denied' : 'network');
          return of<Member[]>([]);
        }),
      )
      .subscribe((members) => {
        if (members.length === 0) {
          this.memberDetails.set(new Map());
          this.candidatesLoading.set(false);
          return;
        }

        forkJoin(
          members.map((member) =>
            this.memberService
              .getDetail(this.tenantId, member.membershipId)
              .pipe(catchError(() => EMPTY)),
          ),
        ).subscribe((details) => {
          const map = new Map<number, MemberDetail>();
          details.forEach((detail) => map.set(detail.membershipId, detail));
          this.memberDetails.set(map);
          this.candidatesLoading.set(false);
        });
      });
  }

  protected grantPermission(permission: Permission): void {
    const group = this.selectedGroup();
    if (!group) {
      return;
    }

    this.memberService
      .grantAccessGroupPermission(this.tenantId, group.id, permission)
      .pipe(
        catchError((err) => {
          this.error.set(err.status === 403 ? 'permission-denied' : 'network');
          return EMPTY;
        }),
      )
      .subscribe();
  }

  // role-permission-management-ui SPEC req 9/10, PLAN's optimistic-toggle-with-rollback +
  // AppSec's in-flight guard: flips `groupPermissions` immediately, calls grant (toggled on) or
  // revoke (toggled off) for that scope, and reverts + surfaces an inline error on a non-2xx --
  // never leaving a stuck optimistic toggle. Ignored entirely while any row's own call is still
  // in flight (double-click race guard).
  protected onTogglePermission(permission: Permission): void {
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
      ? this.memberService.revokeAccessGroupPermission(this.tenantId, group.id, permission)
      : this.memberService.grantAccessGroupPermission(this.tenantId, group.id, permission);

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

  protected assignedGroupIdsOf(member: MemberDetail): Set<number> {
    return new Set(member.accessGroups.map((g) => g.id));
  }

  protected onOpenAssignment(member: MemberDetail): void {
    this.assigningMember.set(
      this.assigningMember()?.membershipId === member.membershipId ? null : member,
    );
  }

  // REQ-7/REQ-9: exactly one selected id reuses the existing single-assign endpoint; more than
  // one goes through the new batch endpoint (see PLAN's "single-vs-batch call selection").
  // REQ-10: never patches roster state optimistically -- always re-fetches after the call
  // completes, success or failure.
  protected onAssignmentSubmitted(membershipId: number, accessGroupIds: number[]): void {
    this.assigningMember.set(null);

    const request: Observable<void> =
      accessGroupIds.length === 1
        ? this.memberService.assignAccessGroup(this.tenantId, membershipId, accessGroupIds[0])
        : this.memberService.batchAssignAccessGroups(this.tenantId, membershipId, accessGroupIds);

    // REQ-10/11: the roster is re-fetched from the backend's confirmed state whether the call
    // succeeds, 400s (partial validation failure), or 403s -- never left showing a stale/
    // optimistic view of what was requested.
    request
      .pipe(
        catchError((err) => {
          this.error.set(err.status === 403 ? 'permission-denied' : 'network');
          return of(undefined);
        }),
      )
      .subscribe(() => this.refreshRoster());
  }

  protected onUnassign(membershipId: number): void {
    this.pendingUnassign.set(membershipId);
  }

  protected unassignTokenFetcher(membershipId: number): () => Observable<string> {
    return () => {
      const group = this.selectedGroup();
      if (!group) {
        return of('');
      }
      return this.memberService.generateAccessGroupUnassignmentToken(
        this.tenantId,
        membershipId,
        group.id,
      );
    };
  }

  protected confirmUnassign(word: string): void {
    const membershipId = this.pendingUnassign();
    const group = this.selectedGroup();

    if (membershipId === null || !group) {
      return;
    }

    this.memberService
      .unassignAccessGroup(this.tenantId, membershipId, group.id, word)
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
        this.refreshRoster();
      });
  }

  protected cancelUnassign(): void {
    this.pendingUnassign.set(null);
    this.unassignRetryToken.set(0);
  }

  protected onDeleteGroup(group: AccessGroup): void {
    this.pendingDelete.set(group);
  }

  protected deleteTokenFetcher(accessGroupId: number): () => Observable<string> {
    return () => this.memberService.generateAccessGroupDeletionToken(this.tenantId, accessGroupId);
  }

  protected confirmDelete(word: string): void {
    const group = this.pendingDelete();

    if (!group) {
      return;
    }

    this.memberService
      .deleteAccessGroup(this.tenantId, group.id, word)
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
        this.groups.update((groups) => groups.filter((g) => g.id !== group.id));
        if (this.selectedGroup()?.id === group.id) {
          this.selectedGroup.set(null);
        }
      });
  }

  protected cancelDelete(): void {
    this.pendingDelete.set(null);
    this.deleteRetryToken.set(0);
  }

  // Forces a re-fetch (not a cache re-filter) since the underlying data just changed --
  // distinct from selectGroup()'s cache-reuse path, which only applies when nothing has
  // been mutated since the cache was last populated.
  private refreshRoster(): void {
    this.candidatesLoading.set(true);
    this.memberDetails.set(new Map());
    this.loadRoster();
  }
}
