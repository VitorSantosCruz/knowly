import { Component, OnChanges, computed, inject, input, signal } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { EMPTY, Observable, catchError, of } from 'rxjs';
import { ALL_PERMISSIONS, Permission } from '../../core/permission';
import { PermissionsService } from '../../core/permissions.service';
import { ProfileService } from '../../core/profile.service';
import { AccessGroup, MemberDetail, MemberService } from '../../core/member.service';
import { ErrorStateComponent } from '../../shared/error-state.component';
import { NoAccessStateComponent } from '../../shared/no-access-state.component';
import { ConfirmDialogComponent } from '../../shared/confirm-dialog.component';
import { ProfileSectionComponent } from '../user-management/profile-section.component';

type DetailError = 'network' | 'permission-denied' | null;

@Component({
  selector: 'app-member-detail-panel',
  imports: [
    TranslocoPipe,
    ErrorStateComponent,
    NoAccessStateComponent,
    ProfileSectionComponent,
    ConfirmDialogComponent,
  ],
  template: `
    @if (error() === 'permission-denied') {
      <app-no-access-state />
    } @else if (error() === 'network') {
      <app-error-state />
    } @else if (detail(); as detail) {
      <div
        data-testid="member-detail-panel"
        class="enter-fluid rounded-2xl border border-ink-200/70 bg-white p-6 shadow-sm shadow-ink-900/5 dark:border-ink-800/70 dark:bg-ink-900 dark:shadow-none"
      >
        <h2 class="mb-4 font-semibold text-ink-900 dark:text-white">{{ detail.email }}</h2>

        <section data-testid="direct-permissions" class="mb-5">
          <h3 class="mb-2 text-sm font-medium text-ink-700 dark:text-ink-300">
            {{ 'members.directPermissions' | transloco }}
          </h3>
          @for (permission of allPermissions; track permission) {
            <label
              class="mr-3 inline-flex items-center gap-1.5 text-sm text-ink-700 dark:text-ink-300"
            >
              <input
                type="checkbox"
                [attr.data-testid]="'permission-toggle-' + permission"
                [checked]="detail.directPermissions.includes(permission)"
                (click)="
                  onTogglePermission(permission, detail.directPermissions.includes(permission))
                "
                class="accent-signal-500"
              />
              {{ permission }}
            </label>
          }
        </section>

        <section data-testid="access-groups" class="mb-5">
          <h3 class="mb-2 text-sm font-medium text-ink-700 dark:text-ink-300">
            {{ 'members.accessGroups' | transloco }}
          </h3>
          <ul class="mb-2 flex flex-col gap-1">
            @for (group of detail.accessGroups; track group.id) {
              <li
                class="flex items-center justify-between rounded-lg bg-ink-50 px-3 py-1.5 text-sm text-ink-800 dark:bg-ink-800/50 dark:text-ink-100"
              >
                {{ group.name }}
                <button
                  [attr.data-testid]="'unassign-access-group-' + group.id"
                  (click)="onUnassignAccessGroup(group.id)"
                  class="text-red-600 transition-colors duration-fast ease-fluid hover:text-red-700 dark:text-red-400 dark:hover:text-red-300"
                >
                  {{ 'members.unassign' | transloco }}
                </button>
              </li>
            }
          </ul>

          <ul class="flex flex-col gap-1">
            @for (group of assignableAccessGroups(detail); track group.id) {
              <li class="flex items-center justify-between text-sm text-ink-600 dark:text-ink-400">
                {{ group.name }}
                <button
                  [attr.data-testid]="'assign-access-group-' + group.id"
                  (click)="onAssignAccessGroup(group.id)"
                  class="text-signal-600 transition-colors duration-fast ease-fluid hover:text-signal-700 dark:text-signal-400 dark:hover:text-signal-300"
                >
                  {{ 'members.assign' | transloco }}
                </button>
              </li>
            }
          </ul>

          <form
            data-testid="new-access-group-form"
            class="mt-3 flex gap-2"
            (submit)="onCreateAccessGroup($event)"
          >
            <input
              data-testid="new-access-group-name"
              type="text"
              [value]="newAccessGroupName()"
              (input)="newAccessGroupName.set($any($event.target).value)"
              class="flex-1 rounded-xl border border-ink-300/70 bg-white px-3 py-1.5 text-sm text-ink-900 shadow-sm transition-shadow duration-fast ease-fluid focus:border-signal-400 focus:ring-2 focus:ring-signal-400/30 focus:outline-none dark:border-ink-700 dark:bg-ink-800 dark:text-ink-100"
            />
            <button
              type="submit"
              class="rounded-xl bg-ink-800 px-3 py-1.5 text-sm font-medium text-white transition-colors duration-fast ease-fluid hover:bg-signal-600 active:bg-signal-700 dark:bg-ink-600 dark:hover:bg-signal-500"
            >
              {{ 'members.createGroup' | transloco }}
            </button>
          </form>
        </section>

        <section data-testid="effective-permissions">
          <h3 class="mb-1 text-sm font-medium text-ink-700 dark:text-ink-300">
            {{ 'members.effectivePermissions' | transloco }}
          </h3>
          <p class="text-sm text-ink-600 dark:text-ink-400">
            {{ detail.effectivePermissions.join(', ') }}
          </p>
        </section>

        <app-profile-section
          [userId]="detail.userId"
          [canEdit]="canEdit()"
          [ownUserId]="ownUserId()"
        />
      </div>

      @if (pendingPermissionRevoke(); as permission) {
        <app-confirm-dialog
          [open]="true"
          [message]="
            'members.confirmRevokePermission' | transloco: { permission, email: detail.email }
          "
          [fetchToken]="permissionRevocationTokenFetcher(permission)"
          [retryToken]="permissionRevokeRetryToken()"
          (confirm)="confirmPermissionRevoke($event)"
          (dismissed)="cancelPermissionRevoke()"
        />
      }

      @if (pendingGroupUnassign(); as group) {
        <app-confirm-dialog
          [open]="true"
          [message]="
            'members.confirmUnassignGroup' | transloco: { group: group.name, email: detail.email }
          "
          [fetchToken]="groupUnassignmentTokenFetcher(group.id)"
          [retryToken]="groupUnassignRetryToken()"
          (confirm)="confirmGroupUnassign($event)"
          (dismissed)="cancelGroupUnassign()"
        />
      }
    }
  `,
})
export class MemberDetailPanelComponent implements OnChanges {
  private readonly memberService = inject(MemberService);
  private readonly permissionsService = inject(PermissionsService);
  private readonly profileService = inject(ProfileService);

  readonly tenantId = input.required<number>();
  readonly membershipId = input.required<number>();
  readonly viewerIsMemberAdminOfThisTenant = input.required<boolean>();

  protected readonly canEdit = computed(
    () => this.viewerIsMemberAdminOfThisTenant() || this.permissionsService.has('PROFILE_EDIT'),
  );

  protected readonly detail = signal<MemberDetail | null>(null);
  protected readonly availableAccessGroups = signal<AccessGroup[]>([]);
  protected readonly newAccessGroupName = signal('');
  protected readonly allPermissions = ALL_PERMISSIONS;
  protected readonly error = signal<DetailError>(null);

  protected readonly pendingPermissionRevoke = signal<Permission | null>(null);
  protected readonly permissionRevokeRetryToken = signal(0);
  protected readonly pendingGroupUnassign = signal<AccessGroup | null>(null);
  protected readonly groupUnassignRetryToken = signal(0);

  // REQ-12/SPEC judgment call 5: sourced once per panel-open, threaded down to
  // `ProfileSectionComponent` so it can hide the inline-edit affordance on the viewer's own row.
  protected readonly ownUserId = signal<number | null>(null);

  ngOnChanges(): void {
    this.loadDetail();
    this.loadAccessGroups();
    this.loadOwnUserId();
  }

  private loadOwnUserId(): void {
    this.profileService
      .getOwnProfile()
      .pipe(catchError(() => of(null)))
      .subscribe((profile) => {
        if (profile !== null) {
          this.ownUserId.set(profile.userId);
        }
      });
  }

  protected assignableAccessGroups(detail: MemberDetail): AccessGroup[] {
    const assignedIds = new Set(detail.accessGroups.map((group) => group.id));
    return this.availableAccessGroups().filter((group) => !assignedIds.has(group.id));
  }

  private reportError(err: { status: number }): void {
    this.error.set(err.status === 403 ? 'permission-denied' : 'network');
  }

  private loadDetail(): void {
    this.memberService
      .getDetail(this.tenantId(), this.membershipId())
      .pipe(
        catchError((err) => {
          this.reportError(err);
          return of(null);
        }),
      )
      .subscribe((detail) => {
        if (detail !== null) {
          this.detail.set(detail);
        }
      });
  }

  private loadAccessGroups(): void {
    this.memberService
      .listAccessGroups(this.tenantId())
      .pipe(
        catchError((err) => {
          this.reportError(err);
          return of(null);
        }),
      )
      .subscribe((groups) => {
        if (groups !== null) {
          this.availableAccessGroups.set(groups);
        }
      });
  }

  protected onTogglePermission(permission: Permission, isGranted: boolean): void {
    if (isGranted) {
      this.pendingPermissionRevoke.set(permission);
      return;
    }

    this.memberService
      .grantPermission(this.tenantId(), this.membershipId(), permission)
      .pipe(
        catchError((err) => {
          this.reportError(err);
          return of(null);
        }),
      )
      .subscribe((result) => {
        if (result !== null) {
          this.loadDetail();
        }
      });
  }

  protected permissionRevocationTokenFetcher(permission: Permission): () => Observable<string> {
    return () =>
      this.memberService.generatePermissionRevocationToken(
        this.tenantId(),
        this.membershipId(),
        permission,
      );
  }

  protected confirmPermissionRevoke(word: string): void {
    const permission = this.pendingPermissionRevoke();

    if (permission === null) {
      return;
    }

    this.memberService
      .revokePermission(this.tenantId(), this.membershipId(), permission, word)
      .pipe(
        catchError((err) => {
          if (err.status === 400) {
            this.permissionRevokeRetryToken.update((n) => n + 1);
          } else {
            this.pendingPermissionRevoke.set(null);
            this.permissionRevokeRetryToken.set(0);
            this.reportError(err);
          }
          return EMPTY;
        }),
      )
      .subscribe(() => {
        this.pendingPermissionRevoke.set(null);
        this.permissionRevokeRetryToken.set(0);
        this.loadDetail();
      });
  }

  protected cancelPermissionRevoke(): void {
    this.pendingPermissionRevoke.set(null);
    this.permissionRevokeRetryToken.set(0);
  }

  protected onAssignAccessGroup(accessGroupId: number): void {
    this.memberService
      .assignAccessGroup(this.tenantId(), this.membershipId(), accessGroupId)
      .pipe(
        catchError((err) => {
          this.reportError(err);
          return of(null);
        }),
      )
      .subscribe((result) => {
        if (result !== null) {
          this.loadDetail();
        }
      });
  }

  protected onUnassignAccessGroup(accessGroupId: number): void {
    const group = this.detail()?.accessGroups.find((g) => g.id === accessGroupId);

    if (group === undefined) {
      return;
    }

    this.pendingGroupUnassign.set(group);
  }

  protected groupUnassignmentTokenFetcher(accessGroupId: number): () => Observable<string> {
    return () =>
      this.memberService.generateAccessGroupUnassignmentToken(
        this.tenantId(),
        this.membershipId(),
        accessGroupId,
      );
  }

  protected confirmGroupUnassign(word: string): void {
    const group = this.pendingGroupUnassign();

    if (group === null) {
      return;
    }

    this.memberService
      .unassignAccessGroup(this.tenantId(), this.membershipId(), group.id, word)
      .pipe(
        catchError((err) => {
          if (err.status === 400) {
            this.groupUnassignRetryToken.update((n) => n + 1);
          } else {
            this.pendingGroupUnassign.set(null);
            this.groupUnassignRetryToken.set(0);
            this.reportError(err);
          }
          return EMPTY;
        }),
      )
      .subscribe(() => {
        this.pendingGroupUnassign.set(null);
        this.groupUnassignRetryToken.set(0);
        this.loadDetail();
      });
  }

  protected cancelGroupUnassign(): void {
    this.pendingGroupUnassign.set(null);
    this.groupUnassignRetryToken.set(0);
  }

  protected onCreateAccessGroup(event: Event): void {
    event.preventDefault();
    const name = this.newAccessGroupName();

    if (!name) {
      return;
    }

    this.memberService
      .createAccessGroup(this.tenantId(), name)
      .pipe(
        catchError((err) => {
          this.reportError(err);
          return of(null);
        }),
      )
      .subscribe((result) => {
        if (result !== null) {
          this.newAccessGroupName.set('');
          this.loadAccessGroups();
        }
      });
  }
}
