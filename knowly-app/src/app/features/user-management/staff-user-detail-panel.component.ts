import { Component, OnChanges, computed, inject, signal, input } from '@angular/core';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { EMPTY, Observable, catchError, of } from 'rxjs';
import { ALL_GLOBAL_PERMISSIONS, GlobalPermission } from '../../core/global-permission';
import { GlobalPermissionsService } from '../../core/global-permissions.service';
import { ProfileService } from '../../core/profile.service';
import {
  AuditEvent,
  GlobalAccessGroup,
  StaffUserDetail,
  StaffUserService,
} from '../../core/staff-user.service';
import { buttonClass } from '../../shared/button-classes';
import { ErrorStateComponent } from '../../shared/error-state.component';
import { NoAccessStateComponent } from '../../shared/no-access-state.component';
import { ConfirmDialogComponent } from '../../shared/confirm-dialog.component';
import { translatePermissionLabel } from '../../shared/permission-labels';
import { ProfileSectionComponent } from './profile-section.component';

type DetailError = 'network' | 'permission-denied' | null;

@Component({
  selector: 'app-staff-user-detail-panel',
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
        data-testid="staff-user-detail-panel"
        class="enter-fluid rounded-2xl border border-ink-200/70 bg-white p-6 shadow-sm shadow-ink-900/5 dark:border-ink-800/70 dark:bg-ink-900 dark:shadow-none"
      >
        <header class="mb-6 flex items-center justify-between">
          <h2 class="font-semibold text-ink-900 dark:text-white">{{ detail.email }}</h2>

          @if (showEditProfileButton()) {
            <button
              type="button"
              data-testid="staff-edit-profile-button"
              [class]="secondaryButtonClass"
              (click)="editProfileTrigger.set(editProfileTrigger() + 1)"
            >
              {{ 'staffDirectory.editProfile' | transloco }}
            </button>
          }
        </header>

        @if (detail.globalRole === 'STAFF_ADMIN') {
          <section data-testid="staff-admin-tier-actions" class="mb-5">
            @if (viewerIsStaffAdmin()) {
              @if (pendingDemote()) {
                <div class="flex items-center gap-2">
                  <span class="text-sm text-ink-600 dark:text-ink-400">{{
                    'staffDirectory.demoteConfirm' | transloco: { email: detail.email }
                  }}</span>
                  <button
                    type="button"
                    data-testid="staff-demote-confirm"
                    [class]="dangerButtonClass"
                    (click)="confirmDemote()"
                  >
                    {{ 'common.confirm' | transloco }}
                  </button>
                  <button
                    type="button"
                    data-testid="staff-demote-cancel"
                    [class]="secondaryButtonClass"
                    (click)="pendingDemote.set(false)"
                  >
                    {{ 'common.cancel' | transloco }}
                  </button>
                </div>
              } @else {
                <button
                  type="button"
                  data-testid="staff-demote-button"
                  [class]="secondaryButtonClass"
                  [disabled]="detail.isLastAdminOfType"
                  [attr.title]="
                    detail.isLastAdminOfType
                      ? ('staffDirectory.demoteDisabledLastAdmin' | transloco)
                      : null
                  "
                  [attr.aria-describedby]="
                    detail.isLastAdminOfType ? 'staff-demote-disabled-reason' : null
                  "
                  (click)="pendingDemote.set(true)"
                >
                  {{ 'staffDirectory.demote' | transloco }}
                </button>
                @if (detail.isLastAdminOfType) {
                  <p
                    id="staff-demote-disabled-reason"
                    data-testid="staff-demote-disabled-reason"
                    class="mt-1 text-xs text-ink-500 dark:text-ink-400"
                  >
                    {{ 'staffDirectory.demoteDisabledLastAdmin' | transloco }}
                  </p>
                }
              }
            }
          </section>
        } @else {
          <section data-testid="staff-direct-permissions" class="mb-5">
            <div class="mb-2 flex items-center justify-between">
              <h3 class="text-sm font-medium text-ink-700 dark:text-ink-300">
                {{ 'staffDirectory.directPermissions' | transloco }}
              </h3>
              @if (viewerIsStaffAdmin()) {
                @if (pendingPromote()) {
                  <div class="flex items-center gap-2">
                    <span class="text-sm text-ink-600 dark:text-ink-400">{{
                      'staffDirectory.promoteConfirm' | transloco: { email: detail.email }
                    }}</span>
                    <button
                      type="button"
                      data-testid="staff-promote-confirm"
                      [class]="secondaryButtonClass"
                      (click)="confirmPromote()"
                    >
                      {{ 'common.confirm' | transloco }}
                    </button>
                    <button
                      type="button"
                      data-testid="staff-promote-cancel"
                      [class]="secondaryButtonClass"
                      (click)="pendingPromote.set(false)"
                    >
                      {{ 'common.cancel' | transloco }}
                    </button>
                  </div>
                } @else {
                  <button
                    type="button"
                    data-testid="staff-promote-button"
                    [class]="secondaryButtonClass"
                    (click)="pendingPromote.set(true)"
                  >
                    {{ 'staffDirectory.promote' | transloco }}
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
                  [attr.data-testid]="'staff-permission-toggle-' + permission"
                  [disabled]="!viewerIsStaffAdmin()"
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
                  data-testid="staff-save-permissions-button"
                  [class]="buttonClassPrimary"
                  [disabled]="!viewerIsStaffAdmin()"
                  (click)="onSaveBatchPermissions()"
                >
                  {{ 'staffDirectory.save' | transloco }}
                </button>
              </div>
            }
          </section>
        }

        <section data-testid="staff-access-groups" class="mb-5">
          <h3 class="mb-2 text-sm font-medium text-ink-700 dark:text-ink-300">
            {{ 'staffDirectory.accessGroups' | transloco }}
          </h3>
          <ul class="mb-2 flex flex-col gap-1">
            @for (group of detail.accessGroups; track group.id) {
              <li
                class="flex items-center justify-between rounded-lg bg-ink-50 px-3 py-1.5 text-sm text-ink-800 dark:bg-ink-800/50 dark:text-ink-100"
              >
                {{ group.name }}
                <button
                  [attr.data-testid]="'staff-unassign-access-group-' + group.id"
                  [disabled]="!viewerIsStaffAdmin()"
                  (click)="onUnassignAccessGroup(group.id)"
                  class="text-red-600 transition-colors duration-fast ease-fluid hover:text-red-700 disabled:pointer-events-none disabled:opacity-50 dark:text-red-400 dark:hover:text-red-300"
                >
                  {{ 'staffDirectory.unassign' | transloco }}
                </button>
              </li>
            }
          </ul>

          <ul class="flex flex-col gap-1">
            @for (group of assignableAccessGroups(detail); track group.id) {
              <li class="flex items-center justify-between text-sm text-ink-600 dark:text-ink-400">
                {{ group.name }}
                <button
                  [attr.data-testid]="'staff-assign-access-group-' + group.id"
                  [disabled]="!viewerIsStaffAdmin()"
                  (click)="onAssignAccessGroup(group.id)"
                  class="text-signal-600 transition-colors duration-fast ease-fluid hover:text-signal-700 disabled:pointer-events-none disabled:opacity-50 dark:text-signal-400 dark:hover:text-signal-300"
                >
                  {{ 'staffDirectory.assign' | transloco }}
                </button>
              </li>
            }
          </ul>
        </section>

        <section data-testid="staff-effective-permissions" class="mb-5">
          <h3 class="mb-1 text-sm font-medium text-ink-700 dark:text-ink-300">
            {{ 'staffDirectory.effectivePermissions' | transloco }}
          </h3>
          <p class="text-sm text-ink-600 dark:text-ink-400">
            {{ effectivePermissionLabels(detail) }}
          </p>
        </section>

        <section data-testid="staff-audit-trail">
          <h3 class="mb-2 text-sm font-medium text-ink-700 dark:text-ink-300">
            {{ 'staffDirectory.auditTrail.title' | transloco }}
          </h3>
          @if (auditTrailError() === 'permission-denied') {
            <app-no-access-state />
          } @else if (auditTrailError() === 'network') {
            <app-error-state />
          } @else if (auditTrail(); as events) {
            @if (events.length === 0) {
              <p
                data-testid="staff-audit-trail-empty"
                class="text-sm text-ink-500 dark:text-ink-400"
              >
                {{ 'staffDirectory.auditTrail.noHistory' | transloco }}
              </p>
            } @else {
              <table class="w-full text-left text-sm text-ink-700 dark:text-ink-300">
                <thead>
                  <tr class="text-xs tracking-wide text-ink-500 uppercase dark:text-ink-400">
                    <th class="py-1 pr-2">
                      {{ 'staffDirectory.auditTrail.occurredAt' | transloco }}
                    </th>
                    <th class="py-1 pr-2">{{ 'staffDirectory.auditTrail.action' | transloco }}</th>
                    <th class="py-1 pr-2">
                      {{ 'staffDirectory.auditTrail.resourceType' | transloco }}
                    </th>
                    <th class="py-1 pr-2">
                      {{ 'staffDirectory.auditTrail.resourceId' | transloco }}
                    </th>
                    <th class="py-1 pr-2">
                      {{ 'staffDirectory.auditTrail.tenantId' | transloco }}
                    </th>
                    <th class="py-1 pr-2">{{ 'staffDirectory.auditTrail.outcome' | transloco }}</th>
                  </tr>
                </thead>
                <tbody>
                  @for (event of events; track $index) {
                    <tr>
                      <td class="py-1 pr-2">{{ event.occurredAt }}</td>
                      <td class="py-1 pr-2">{{ event.action }}</td>
                      <td class="py-1 pr-2">{{ event.resourceType }}</td>
                      <td class="py-1 pr-2">{{ event.resourceId }}</td>
                      <td class="py-1 pr-2">
                        {{ event.tenantId ?? ('staffDirectory.auditTrail.global' | transloco) }}
                      </td>
                      <td class="py-1 pr-2">{{ event.outcome }}</td>
                    </tr>
                  }
                </tbody>
              </table>
            }
          }
        </section>

        <app-profile-section
          [userId]="userId()"
          [canEdit]="viewerCanEditProfile()"
          [ownUserId]="ownUserId()"
          [hideEditToggle]="true"
          [editTrigger]="editProfileTrigger()"
        />

        @if (viewerCanDelete(detail)) {
          <div class="mt-6 border-t border-ink-100 pt-4 dark:border-ink-800/50">
            <button
              type="button"
              data-testid="staff-delete-button"
              [class]="dangerButtonClass"
              [disabled]="detail.isLastAdminOfType"
              [attr.title]="
                detail.isLastAdminOfType
                  ? ('staffDirectory.deleteDisabledLastAdmin' | transloco)
                  : null
              "
              [attr.aria-describedby]="
                detail.isLastAdminOfType ? 'staff-delete-disabled-reason' : null
              "
              (click)="pendingDelete.set(true)"
            >
              {{ 'staffDirectory.delete' | transloco }}
            </button>
            @if (detail.isLastAdminOfType) {
              <p
                id="staff-delete-disabled-reason"
                data-testid="staff-delete-disabled-reason"
                class="mt-1 text-xs text-ink-500 dark:text-ink-400"
              >
                {{ 'staffDirectory.deleteDisabledLastAdmin' | transloco }}
              </p>
            }
          </div>
        }
      </div>

      @if (pendingPermissionRevoke(); as permission) {
        <app-confirm-dialog
          [open]="true"
          [message]="
            'staffDirectory.confirmRevokePermission'
              | transloco: { permission, email: detail.email }
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
            'staffDirectory.confirmUnassignGroup'
              | transloco: { group: group.name, email: detail.email }
          "
          [fetchToken]="groupUnassignmentTokenFetcher(group.id)"
          [retryToken]="groupUnassignRetryToken()"
          (confirm)="confirmGroupUnassign($event)"
          (dismissed)="cancelGroupUnassign()"
        />
      }

      @if (pendingDelete()) {
        <app-confirm-dialog
          [open]="true"
          [message]="'staffDirectory.confirmDelete' | transloco: { email: detail.email }"
          [fetchToken]="deletionTokenFetcher()"
          [retryToken]="deleteRetryToken()"
          (confirm)="confirmDelete($event)"
          (dismissed)="cancelDelete()"
        />
      }

      @if (pendingBatchSave()) {
        <app-confirm-dialog
          [open]="true"
          [message]="'staffDirectory.confirmBatchSave' | transloco: { email: detail.email }"
          [fetchToken]="batchTokenFetcher()"
          [retryToken]="batchRetryToken()"
          (confirm)="confirmBatchSave($event)"
          (dismissed)="cancelBatchSave()"
        />
      }
    }
  `,
})
export class StaffUserDetailPanelComponent implements OnChanges {
  private readonly staffUserService = inject(StaffUserService);
  private readonly profileService = inject(ProfileService);
  private readonly transloco = inject(TranslocoService);
  protected readonly globalPermissionsService = inject(GlobalPermissionsService);

  readonly userId = input.required<number>();
  readonly viewerIsStaffAdmin = input.required<boolean>();

  protected readonly secondaryButtonClass = buttonClass('secondary');
  protected readonly dangerButtonClass = buttonClass('danger');
  protected readonly buttonClassPrimary = buttonClass('primary');

  protected readonly detail = signal<StaffUserDetail | null>(null);
  protected readonly availableAccessGroups = signal<GlobalAccessGroup[]>([]);
  protected readonly allPermissions = ALL_GLOBAL_PERMISSIONS;
  protected readonly error = signal<DetailError>(null);

  // Independent of `error`/`detail` above, matching this panel's existing per-section
  // signal pattern — a 403 from the audit-trail endpoint only sets this, never the
  // permissions/access-groups sections' own state (REQ-12).
  protected readonly auditTrail = signal<AuditEvent[] | null>(null);
  protected readonly auditTrailError = signal<DetailError>(null);

  // REQ-12/SPEC judgment call 5: sourced once per panel-open, threaded down to
  // `ProfileSectionComponent` so it can hide the inline-edit affordance on the viewer's own row.
  protected readonly ownUserId = signal<number | null>(null);
  protected readonly editProfileTrigger = signal(0);

  protected readonly pendingPermissionRevoke = signal<GlobalPermission | null>(null);
  protected readonly permissionRevokeRetryToken = signal(0);
  protected readonly pendingGroupUnassign = signal<GlobalAccessGroup | null>(null);
  protected readonly groupUnassignRetryToken = signal(0);

  protected readonly pendingDemote = signal(false);
  protected readonly pendingPromote = signal(false);
  protected readonly pendingDelete = signal(false);
  protected readonly deleteRetryToken = signal(0);

  // REQ-15/16: local, unsaved switches state. Seeded from `directPermissions` on every
  // load/refresh (see `loadDetail`), only mutated locally by `onTogglePermission`.
  protected readonly pendingPermissions = signal<Set<GlobalPermission>>(new Set());
  protected readonly initialPermissions = signal<Set<GlobalPermission>>(new Set());
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

  protected readonly viewerCanEditProfile = computed(
    () => this.viewerIsStaffAdmin() || this.globalPermissionsService.has('PROFILE_EDIT'),
  );

  // REQ-11 (identity-profile-model-v2): self-edit is removed entirely, not merely disabled — the
  // header "Editar perfil" trigger mirrors `ProfileSectionComponent#showEditToggle`'s own rule.
  protected readonly showEditProfileButton = computed(
    () => this.viewerCanEditProfile() && this.userId() !== this.ownUserId(),
  );

  ngOnChanges(): void {
    this.loadDetail();
    this.loadAccessGroups();
    this.loadAuditTrail();
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

  protected assignableAccessGroups(detail: StaffUserDetail): GlobalAccessGroup[] {
    const assignedIds = new Set(detail.accessGroups.map((group) => group.id));
    return this.availableAccessGroups().filter((group) => !assignedIds.has(group.id));
  }

  protected permissionLabel(permission: GlobalPermission): string {
    return translatePermissionLabel(permission, this.transloco);
  }

  protected effectivePermissionLabels(detail: StaffUserDetail): string {
    return detail.effectivePermissions
      .map((permission) => this.permissionLabel(permission))
      .join(', ');
  }

  // REQ-7a/REQ-12a: an admin-tier target's demote/delete actions are only shown to a viewer who
  // is themselves a STAFF_ADMIN — a STAFF/MEMBER viewer holding broad granted permissions is
  // still not an admin.
  protected viewerCanDelete(detail: StaffUserDetail): boolean {
    if (detail.globalRole === 'STAFF_ADMIN') {
      return this.viewerIsStaffAdmin();
    }

    // No dedicated "delete" GlobalPermission exists (per REQ-11, unlike the admin-tier gate,
    // deleting a plain STAFF target is never disabled/hidden on a granted-permission basis
    // beyond the same STAFF_USER_CREATE-holder bar this panel already applies elsewhere).
    return this.viewerIsStaffAdmin() || this.globalPermissionsService.has('STAFF_USER_CREATE');
  }

  private reportError(err: { status: number }): void {
    this.error.set(err.status === 403 ? 'permission-denied' : 'network');
  }

  private loadDetail(): void {
    this.staffUserService
      .getDetail(this.userId())
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
    this.staffUserService
      .listAccessGroups()
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

  private loadAuditTrail(): void {
    this.staffUserService
      .getAuditTrail(this.userId())
      .pipe(
        catchError((err) => {
          this.auditTrailError.set(err.status === 403 ? 'permission-denied' : 'network');
          return of(null);
        }),
      )
      .subscribe((events) => {
        if (events !== null) {
          this.auditTrail.set(events);
        }
      });
  }

  // REQ-16: toggling only mutates local state — no HTTP call per toggle.
  protected onTogglePermission(permission: GlobalPermission): void {
    if (!this.viewerIsStaffAdmin()) {
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

  protected permissionRevocationTokenFetcher(
    permission: GlobalPermission,
  ): () => Observable<string> {
    return () => this.staffUserService.generatePermissionRevocationToken(this.userId(), permission);
  }

  protected confirmPermissionRevoke(word: string): void {
    const permission = this.pendingPermissionRevoke();

    if (permission === null) {
      return;
    }

    this.staffUserService
      .revokePermission(this.userId(), permission, word)
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
    if (!this.viewerIsStaffAdmin()) {
      return;
    }

    this.staffUserService
      .assignAccessGroup(this.userId(), accessGroupId)
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
    if (!this.viewerIsStaffAdmin()) {
      return;
    }

    const group = this.detail()?.accessGroups.find((g) => g.id === accessGroupId);

    if (group === undefined) {
      return;
    }

    this.pendingGroupUnassign.set(group);
  }

  protected groupUnassignmentTokenFetcher(accessGroupId: number): () => Observable<string> {
    return () =>
      this.staffUserService.generateAccessGroupUnassignmentToken(this.userId(), accessGroupId);
  }

  protected confirmGroupUnassign(word: string): void {
    const group = this.pendingGroupUnassign();

    if (group === null) {
      return;
    }

    this.staffUserService
      .unassignAccessGroup(this.userId(), group.id, word)
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

    this.staffUserService
      .demote(this.userId())
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

    this.staffUserService
      .promote(this.userId())
      .pipe(
        catchError((err) => {
          this.reportError(err);
          return EMPTY;
        }),
      )
      .subscribe(() => this.loadDetail());
  }

  protected deletionTokenFetcher(): () => Observable<string> {
    return () => this.staffUserService.generateDeletionConfirmationToken(this.userId());
  }

  protected confirmDelete(word: string): void {
    this.staffUserService
      .delete(this.userId(), word)
      .pipe(
        catchError((err) => {
          if (err.status === 400) {
            this.deleteRetryToken.update((n) => n + 1);
          } else {
            this.pendingDelete.set(false);
            this.deleteRetryToken.set(0);
            this.reportError(err);
          }
          return EMPTY;
        }),
      )
      .subscribe(() => {
        this.pendingDelete.set(false);
        this.deleteRetryToken.set(0);
        this.loadDetail();
      });
  }

  protected cancelDelete(): void {
    this.pendingDelete.set(false);
    this.deleteRetryToken.set(0);
  }

  // REQ-17/18/19: single confirmation for the whole batch, submitting the full pending set.
  protected onSaveBatchPermissions(): void {
    if (!this.hasPendingPermissionChanges()) {
      return;
    }

    this.pendingBatchSave.set(true);
  }

  protected batchTokenFetcher(): () => Observable<string> {
    return () => this.staffUserService.generateBatchPermissionUpdateToken(this.userId());
  }

  protected confirmBatchSave(word: string): void {
    this.staffUserService
      .batchUpdatePermissions(this.userId(), Array.from(this.pendingPermissions()), word)
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
