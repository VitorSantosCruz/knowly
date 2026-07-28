import { Component, OnChanges, inject, input, signal } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { catchError, of } from 'rxjs';
import { ALL_GLOBAL_PERMISSIONS, GlobalPermission } from '../../core/global-permission';
import {
  AuditEvent,
  GlobalAccessGroup,
  StaffUserDetail,
  StaffUserService,
} from '../../core/staff-user.service';
import { ErrorStateComponent } from '../../shared/error-state.component';
import { NoAccessStateComponent } from '../../shared/no-access-state.component';

type DetailError = 'network' | 'permission-denied' | null;

@Component({
  selector: 'app-staff-user-detail-panel',
  imports: [TranslocoPipe, ErrorStateComponent, NoAccessStateComponent],
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
        <h2 class="mb-4 font-semibold text-ink-900 dark:text-white">{{ detail.email }}</h2>

        <section data-testid="staff-direct-permissions" class="mb-5">
          <h3 class="mb-2 text-sm font-medium text-ink-700 dark:text-ink-300">
            {{ 'staffDirectory.directPermissions' | transloco }}
          </h3>
          @for (permission of allPermissions; track permission) {
            <label
              class="mr-3 inline-flex items-center gap-1.5 text-sm text-ink-700 dark:text-ink-300"
            >
              <input
                type="checkbox"
                [attr.data-testid]="'staff-permission-toggle-' + permission"
                [checked]="detail.directPermissions.includes(permission)"
                [disabled]="!viewerIsStaffAdmin()"
                (click)="
                  onTogglePermission(permission, detail.directPermissions.includes(permission))
                "
                class="accent-signal-500"
              />
              {{ permission }}
            </label>
          }
        </section>

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

          @if (viewerIsStaffAdmin()) {
            <form
              data-testid="staff-new-access-group-form"
              class="mt-3 flex gap-2"
              (submit)="onCreateAccessGroup($event)"
            >
              <input
                data-testid="staff-new-access-group-name"
                type="text"
                [value]="newAccessGroupName()"
                (input)="newAccessGroupName.set($any($event.target).value)"
                class="flex-1 rounded-xl border border-ink-300/70 bg-white px-3 py-1.5 text-sm text-ink-900 shadow-sm transition-shadow duration-fast ease-fluid focus:border-signal-400 focus:ring-2 focus:ring-signal-400/30 focus:outline-none dark:border-ink-700 dark:bg-ink-800 dark:text-ink-100"
              />
              <button
                type="submit"
                class="rounded-xl bg-ink-800 px-3 py-1.5 text-sm font-medium text-white transition-colors duration-fast ease-fluid hover:bg-signal-600 active:bg-signal-700 dark:bg-ink-600 dark:hover:bg-signal-500"
              >
                {{ 'staffDirectory.createGroup' | transloco }}
              </button>
            </form>
          }
        </section>

        <section data-testid="staff-effective-permissions" class="mb-5">
          <h3 class="mb-1 text-sm font-medium text-ink-700 dark:text-ink-300">
            {{ 'staffDirectory.effectivePermissions' | transloco }}
          </h3>
          <p class="text-sm text-ink-600 dark:text-ink-400">
            {{ detail.effectivePermissions.join(', ') }}
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
      </div>
    }
  `,
})
export class StaffUserDetailPanelComponent implements OnChanges {
  private readonly staffUserService = inject(StaffUserService);

  readonly userId = input.required<number>();
  readonly viewerIsStaffAdmin = input.required<boolean>();

  protected readonly detail = signal<StaffUserDetail | null>(null);
  protected readonly availableAccessGroups = signal<GlobalAccessGroup[]>([]);
  protected readonly newAccessGroupName = signal('');
  protected readonly allPermissions = ALL_GLOBAL_PERMISSIONS;
  protected readonly error = signal<DetailError>(null);

  // Independent of `error`/`detail` above, matching this panel's existing per-section
  // signal pattern — a 403 from the audit-trail endpoint only sets this, never the
  // permissions/access-groups sections' own state (REQ-12).
  protected readonly auditTrail = signal<AuditEvent[] | null>(null);
  protected readonly auditTrailError = signal<DetailError>(null);

  ngOnChanges(): void {
    this.loadDetail();
    this.loadAccessGroups();
    this.loadAuditTrail();
  }

  protected assignableAccessGroups(detail: StaffUserDetail): GlobalAccessGroup[] {
    const assignedIds = new Set(detail.accessGroups.map((group) => group.id));
    return this.availableAccessGroups().filter((group) => !assignedIds.has(group.id));
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

  protected onTogglePermission(permission: GlobalPermission, isGranted: boolean): void {
    if (!this.viewerIsStaffAdmin()) {
      return;
    }

    const request$ = isGranted
      ? this.staffUserService.revokePermission(this.userId(), permission)
      : this.staffUserService.grantPermission(this.userId(), permission);

    request$
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

    this.staffUserService
      .unassignAccessGroup(this.userId(), accessGroupId)
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

  protected onCreateAccessGroup(event: Event): void {
    event.preventDefault();

    if (!this.viewerIsStaffAdmin()) {
      return;
    }

    const name = this.newAccessGroupName();

    if (!name) {
      return;
    }

    this.staffUserService
      .createAccessGroup(name)
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
