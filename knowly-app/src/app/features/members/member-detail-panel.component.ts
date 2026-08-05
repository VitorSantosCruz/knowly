import { Component, OnChanges, computed, inject, input, signal } from '@angular/core';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { EMPTY, Observable, catchError, of } from 'rxjs';
import { ALL_PERMISSIONS, Permission } from '../../core/permission';
import { PermissionsService } from '../../core/permissions.service';
import { GlobalPermissionsService } from '../../core/global-permissions.service';
import { ProfileService } from '../../core/profile.service';
import { AccessGroup, MemberDetail, MemberService } from '../../core/member.service';
import { buttonClass } from '../../shared/button-classes';
import { ErrorStateComponent } from '../../shared/error-state.component';
import { NoAccessStateComponent } from '../../shared/no-access-state.component';
import { ConfirmDialogComponent } from '../../shared/confirm-dialog.component';
import { translatePermissionLabel } from '../../shared/permission-labels';
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
        <header class="mb-6 flex items-center justify-between">
          <h2 class="font-semibold text-ink-900 dark:text-white">{{ detail.email }}</h2>

          @if (showEditProfileButton()) {
            <button
              type="button"
              data-testid="member-edit-profile-button"
              [class]="secondaryButtonClass"
              (click)="editProfileTrigger.set(editProfileTrigger() + 1)"
            >
              {{ 'members.editProfile' | transloco }}
            </button>
          }
        </header>

        @if (detail.role === 'MEMBER_ADMIN') {
          <section data-testid="member-admin-tier-actions" class="mb-5">
            @if (viewerIsMemberAdminOfThisTenant()) {
              @if (pendingDemote()) {
                <div class="flex items-center gap-2">
                  <span class="text-sm text-ink-600 dark:text-ink-400">{{
                    'members.demoteConfirm' | transloco: { email: detail.email }
                  }}</span>
                  <button
                    type="button"
                    data-testid="member-demote-confirm"
                    [class]="dangerButtonClass"
                    (click)="confirmDemote()"
                  >
                    {{ 'common.confirm' | transloco }}
                  </button>
                  <button
                    type="button"
                    data-testid="member-demote-cancel"
                    [class]="secondaryButtonClass"
                    (click)="pendingDemote.set(false)"
                  >
                    {{ 'common.cancel' | transloco }}
                  </button>
                </div>
              } @else {
                <button
                  type="button"
                  data-testid="member-demote-button"
                  [class]="secondaryButtonClass"
                  [disabled]="detail.isLastAdminOfType"
                  [attr.title]="
                    detail.isLastAdminOfType
                      ? ('members.demoteDisabledLastAdmin' | transloco)
                      : null
                  "
                  [attr.aria-describedby]="
                    detail.isLastAdminOfType ? 'member-demote-disabled-reason' : null
                  "
                  (click)="pendingDemote.set(true)"
                >
                  {{ 'members.demote' | transloco }}
                </button>
                @if (detail.isLastAdminOfType) {
                  <p
                    id="member-demote-disabled-reason"
                    data-testid="member-demote-disabled-reason"
                    class="mt-1 text-xs text-ink-500 dark:text-ink-400"
                  >
                    {{ 'members.demoteDisabledLastAdmin' | transloco }}
                  </p>
                }
              }
            }
          </section>
        } @else {
          <section data-testid="direct-permissions" class="mb-5">
            <div class="mb-2 flex items-center justify-between">
              <h3 class="text-sm font-medium text-ink-700 dark:text-ink-300">
                {{ 'members.directPermissions' | transloco }}
              </h3>
              @if (viewerIsMemberAdminOfThisTenant()) {
                @if (pendingPromote()) {
                  <div class="flex items-center gap-2">
                    <span class="text-sm text-ink-600 dark:text-ink-400">{{
                      'members.promoteConfirm' | transloco: { email: detail.email }
                    }}</span>
                    <button
                      type="button"
                      data-testid="member-promote-confirm"
                      [class]="secondaryButtonClass"
                      (click)="confirmPromote()"
                    >
                      {{ 'common.confirm' | transloco }}
                    </button>
                    <button
                      type="button"
                      data-testid="member-promote-cancel"
                      [class]="secondaryButtonClass"
                      (click)="pendingPromote.set(false)"
                    >
                      {{ 'common.cancel' | transloco }}
                    </button>
                  </div>
                } @else {
                  <button
                    type="button"
                    data-testid="member-promote-button"
                    [class]="secondaryButtonClass"
                    (click)="pendingPromote.set(true)"
                  >
                    {{ 'members.promote' | transloco }}
                  </button>
                }
              }
            </div>

            @for (permission of allPermissions; track permission) {
              <span
                class="mr-3 mb-1 inline-flex items-center gap-2 text-sm text-ink-700 dark:text-ink-300"
              >
                <button
                  type="button"
                  role="switch"
                  [attr.aria-checked]="pendingPermissions().has(permission)"
                  [attr.aria-label]="permissionLabel(permission)"
                  [attr.data-testid]="'permission-toggle-' + permission"
                  [disabled]="!viewerCanManageDirectPermissions()"
                  (click)="onTogglePermission(permission)"
                  [class]="
                    'relative inline-flex h-5 w-9 items-center rounded-full transition-colors duration-fast ease-fluid disabled:pointer-events-none disabled:opacity-50 ' +
                    (pendingPermissions().has(permission)
                      ? 'bg-signal-600'
                      : 'bg-ink-300 dark:bg-ink-700')
                  "
                >
                  <span
                    [class]="
                      'inline-block h-3.5 w-3.5 transform rounded-full bg-white transition-transform duration-fast ease-fluid ' +
                      (pendingPermissions().has(permission) ? 'translate-x-4' : 'translate-x-1')
                    "
                  ></span>
                </button>
                {{ permissionLabel(permission) }}
              </span>
            }

            @if (hasPendingPermissionChanges()) {
              <div class="mt-3">
                <button
                  type="button"
                  data-testid="member-save-permissions-button"
                  [class]="buttonClassPrimary"
                  [disabled]="!viewerCanManageDirectPermissions()"
                  (click)="onSaveBatchPermissions()"
                >
                  {{ 'members.save' | transloco }}
                </button>
              </div>
            }
          </section>
        }

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
        </section>

        <section data-testid="effective-permissions">
          <h3 class="mb-1 text-sm font-medium text-ink-700 dark:text-ink-300">
            {{ 'members.effectivePermissions' | transloco }}
          </h3>
          <p class="text-sm text-ink-600 dark:text-ink-400">
            {{ effectivePermissionLabels(detail) }}
          </p>
        </section>

        <app-profile-section
          [userId]="detail.userId"
          [canEdit]="canEdit()"
          [ownUserId]="ownUserId()"
          [hideEditToggle]="true"
          [editTrigger]="editProfileTrigger()"
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

      @if (pendingBatchSave()) {
        <app-confirm-dialog
          [open]="true"
          [message]="'members.confirmBatchSave' | transloco: { email: detail.email }"
          [fetchToken]="batchTokenFetcher()"
          [retryToken]="batchRetryToken()"
          (confirm)="confirmBatchSave($event)"
          (dismissed)="cancelBatchSave()"
        />
      }
    }
  `,
})
export class MemberDetailPanelComponent implements OnChanges {
  private readonly memberService = inject(MemberService);
  private readonly permissionsService = inject(PermissionsService);
  private readonly globalPermissionsService = inject(GlobalPermissionsService);
  private readonly profileService = inject(ProfileService);
  private readonly transloco = inject(TranslocoService);

  readonly tenantId = input.required<number>();
  readonly membershipId = input.required<number>();
  readonly viewerIsMemberAdminOfThisTenant = input.required<boolean>();

  protected readonly secondaryButtonClass = buttonClass('secondary');
  protected readonly dangerButtonClass = buttonClass('danger');
  protected readonly buttonClassPrimary = buttonClass('primary');

  protected readonly canEdit = computed(
    () => this.viewerIsMemberAdminOfThisTenant() || this.permissionsService.has('PROFILE_EDIT'),
  );

  // Backend's TenantService#grantPermission/revokePermission accept either a real tenant
  // MEMBER_ADMIN or a staff caller holding the *global* TENANT_PERMISSION_GRANT_CREATE/DELETE
  // permission (requireAdminOfTenantOrStaff) — a STAFF_ADMIN acting as a tenant with no real
  // TenantMembership row was previously locked out of these switches entirely, even though the
  // backend would accept the exact same call. `permissionsService` only exposes the tenant-scoped
  // permission set, which never includes this staff-only permission — needs GlobalPermissionsService.
  protected readonly viewerCanManageDirectPermissions = computed(
    () =>
      this.viewerIsMemberAdminOfThisTenant() ||
      this.globalPermissionsService.has('TENANT_PERMISSION_GRANT_CREATE'),
  );

  // REQ-11 (identity-profile-model-v2): self-edit is removed entirely, not merely disabled — the
  // header "Editar perfil" trigger mirrors `ProfileSectionComponent#showEditToggle`'s own rule.
  protected readonly showEditProfileButton = computed(
    () => this.canEdit() && this.detail()?.userId !== this.ownUserId(),
  );

  protected readonly detail = signal<MemberDetail | null>(null);
  protected readonly availableAccessGroups = signal<AccessGroup[]>([]);
  protected readonly allPermissions = ALL_PERMISSIONS;
  protected readonly error = signal<DetailError>(null);

  protected readonly pendingPermissionRevoke = signal<Permission | null>(null);
  protected readonly permissionRevokeRetryToken = signal(0);
  protected readonly pendingGroupUnassign = signal<AccessGroup | null>(null);
  protected readonly groupUnassignRetryToken = signal(0);

  protected readonly pendingDemote = signal(false);
  protected readonly pendingPromote = signal(false);

  protected readonly pendingPermissions = signal<Set<Permission>>(new Set());
  protected readonly initialPermissions = signal<Set<Permission>>(new Set());
  protected readonly pendingBatchSave = signal(false);
  protected readonly batchRetryToken = signal(0);

  protected readonly hasPendingPermissionChanges = computed(() => {
    const pending = this.pendingPermissions();
    const initial = this.initialPermissions();

    if (pending.size !== initial.size) {
      return true;
    }

    for (const permission of pending) {
      if (!initial.has(permission)) {
        return true;
      }
    }

    return false;
  });

  // REQ-12/SPEC judgment call 5: sourced once per panel-open, threaded down to
  // `ProfileSectionComponent` so it can hide the inline-edit affordance on the viewer's own row.
  protected readonly ownUserId = signal<number | null>(null);
  protected readonly editProfileTrigger = signal(0);

  /**
   * Called by `members-page.component.ts`'s edit row action (REQ-6) — opens the
   * embedded `ProfileSectionComponent` directly in edit mode via the same
   * `editTrigger` counter the panel's own header "Editar perfil" button already
   * drives, so both entry points share one code path.
   */
  openInEditMode(): void {
    this.editProfileTrigger.update((n) => n + 1);
  }

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

  protected permissionLabel(permission: Permission): string {
    return translatePermissionLabel(permission, this.transloco);
  }

  protected effectivePermissionLabels(detail: MemberDetail): string {
    return detail.effectivePermissions
      .map((permission) => this.permissionLabel(permission))
      .join(', ');
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
          const direct = new Set(detail.directPermissions);
          this.pendingPermissions.set(new Set(direct));
          this.initialPermissions.set(new Set(direct));
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

  protected onTogglePermission(permission: Permission): void {
    if (!this.viewerCanManageDirectPermissions()) {
      return;
    }

    this.pendingPermissions.update((current) => {
      const next = new Set(current);

      if (next.has(permission)) {
        next.delete(permission);
      } else {
        next.add(permission);
      }

      return next;
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
          return EMPTY;
        }),
      )
      .subscribe(() => {
        this.loadDetail();
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

  // REQ-7: demote — no security-phrase token endpoint exists for this action (backend PLAN),
  // so this uses a plain inline confirm step rather than `ConfirmDialogComponent`.
  protected confirmDemote(): void {
    this.pendingDemote.set(false);

    this.memberService
      .demote(this.tenantId(), this.membershipId())
      .pipe(
        catchError((err) => {
          this.reportError(err);
          return EMPTY;
        }),
      )
      .subscribe(() => this.loadDetail());
  }

  // REQ-7e: promote — same plain-confirm shape as demote, never disabled on a last-admin basis.
  protected confirmPromote(): void {
    this.pendingPromote.set(false);

    this.memberService
      .promote(this.tenantId(), this.membershipId())
      .pipe(
        catchError((err) => {
          this.reportError(err);
          return EMPTY;
        }),
      )
      .subscribe(() => this.loadDetail());
  }

  // REQ-17/18/19: single confirmation for the whole batch, submitting the full pending set.
  protected onSaveBatchPermissions(): void {
    if (!this.hasPendingPermissionChanges()) {
      return;
    }

    this.pendingBatchSave.set(true);
  }

  protected batchTokenFetcher(): () => Observable<string> {
    return () =>
      this.memberService.generateBatchPermissionUpdateToken(this.tenantId(), this.membershipId());
  }

  protected confirmBatchSave(word: string): void {
    this.memberService
      .batchUpdatePermissions(
        this.tenantId(),
        this.membershipId(),
        Array.from(this.pendingPermissions()),
        word,
      )
      .pipe(
        catchError((err) => {
          if (err.status === 400) {
            this.batchRetryToken.update((n) => n + 1);
          } else {
            this.pendingBatchSave.set(false);
            this.batchRetryToken.set(0);
            this.reportError(err);
          }
          return EMPTY;
        }),
      )
      .subscribe(() => {
        this.pendingBatchSave.set(false);
        this.batchRetryToken.set(0);
        this.loadDetail();
      });
  }

  protected cancelBatchSave(): void {
    this.pendingBatchSave.set(false);
    this.batchRetryToken.set(0);
  }
}
