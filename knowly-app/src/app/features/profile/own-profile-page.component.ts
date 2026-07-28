import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { catchError, of } from 'rxjs';
import { ActiveTenantService } from '../../core/active-tenant.service';
import { ALL_GLOBAL_PERMISSIONS } from '../../core/global-permission';
import { GlobalPermissionsService } from '../../core/global-permissions.service';
import { ProfileFields, ProfileService, UserProfile } from '../../core/profile.service';
import { ErrorStateComponent } from '../../shared/error-state.component';
import { ProfileFieldsFormComponent } from '../../shared/profile-fields-form.component';

@Component({
  selector: 'app-own-profile-page',
  imports: [TranslocoPipe, ErrorStateComponent, ProfileFieldsFormComponent],
  template: `
    <div data-testid="own-profile-page" class="page-shell">
      @if (error() === 'network') {
        <app-error-state />
      } @else if (profile(); as profile) {
        <div
          class="enter-fluid rounded-2xl border border-ink-200/70 bg-white p-6 shadow-sm shadow-ink-900/5 dark:border-ink-800/70 dark:bg-ink-900 dark:shadow-none"
        >
          <h1 class="mb-4 font-semibold text-ink-900 dark:text-white">
            {{ 'profile.title' | transloco }}
          </h1>

          <p data-testid="profile-email" class="mb-4 text-sm text-ink-600 dark:text-ink-400">
            {{ 'profile.email' | transloco }}: {{ profile.email }}
          </p>

          @if (pending()) {
            <p
              data-testid="profile-pending"
              class="mb-3 rounded-lg bg-amber-50 px-3 py-2 text-sm text-amber-700 dark:bg-amber-950/30 dark:text-amber-400"
            >
              {{ pendingMessage() | transloco }}
            </p>
          }

          @if (conflictError(); as fields) {
            <p
              data-testid="profile-conflict"
              class="mb-3 rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700 dark:bg-red-950/30 dark:text-red-400"
            >
              {{ 'profile.conflict' | transloco: { fields: fields.join(', ') } }}
            </p>
          }

          <app-profile-fields-form
            [fields]="formFields()"
            [disabled]="pending()"
            (submitted)="onSubmit($event)"
          />
        </div>
      }
    </div>
  `,
})
export class OwnProfilePageComponent implements OnInit {
  private readonly profileService = inject(ProfileService);
  private readonly globalPermissionsService = inject(GlobalPermissionsService);
  private readonly activeTenantService = inject(ActiveTenantService);

  protected readonly profile = signal<UserProfile | null>(null);
  protected readonly formFields = signal<ProfileFields>({
    fullName: '',
    address: '',
    rg: '',
    cpf: '',
    phone: '',
  });
  protected readonly error = signal<'network' | null>(null);
  protected readonly pending = signal(false);
  protected readonly pendingReason = signal<'submitted' | 'conflict'>('submitted');
  protected readonly conflictError = signal<string[] | null>(null);

  protected readonly pendingMessage = computed(() =>
    this.pendingReason() === 'conflict' ? 'profile.alreadyPending' : 'profile.pending',
  );

  // Third occurrence of this page-local computed, precedented by staff-global-dashboard's PLAN
  // (StaffDirectoryPageComponent/WelcomePageComponent) — not extracted, same accepted tradeoff.
  private readonly viewerIsStaffAdmin = computed(() =>
    ALL_GLOBAL_PERMISSIONS.every((permission) => this.globalPermissionsService.has(permission)),
  );

  private readonly memberships = signal<{ role: 'ADMIN' | 'MEMBER' }[]>([]);

  // REQ-11/REQ-12 only — tenant/global PROFILE_EDIT holders are deliberately excluded per
  // REQ-13a/14a (that grant never covers self).
  protected readonly hasDirectEditRight = computed(
    () =>
      this.viewerIsStaffAdmin() || this.memberships().some((membership) => membership.role === 'ADMIN'),
  );

  ngOnInit(): void {
    this.profileService
      .getOwnProfile()
      .pipe(
        catchError(() => {
          this.error.set('network');
          return of(null);
        }),
      )
      .subscribe((profile) => {
        if (profile !== null) {
          this.profile.set(profile);
          this.formFields.set(profile);
        }
      });

    this.globalPermissionsService.fetch();
    this.activeTenantService.list().subscribe((memberships) => {
      this.memberships.set(memberships);
    });
  }

  protected onSubmit(fields: ProfileFields): void {
    if (this.pending()) {
      return;
    }

    this.formFields.set(fields);
    this.conflictError.set(null);

    if (this.hasDirectEditRight()) {
      const userId = this.profile()?.userId;
      if (userId === undefined) {
        return;
      }

      this.profileService
        .directEdit(userId, fields)
        .pipe(
          catchError((err) => {
            if (err.status === 409) {
              this.conflictError.set(err.error?.conflictingFields ?? []);
            } else {
              this.error.set('network');
            }
            return of(null);
          }),
        )
        .subscribe((updated) => {
          if (updated !== null) {
            this.profile.set(updated);
            this.formFields.set(updated);
          }
        });
      return;
    }

    this.profileService
      .submitEditRequest(fields)
      .pipe(
        catchError((err) => {
          if (err.status === 409) {
            this.pendingReason.set('conflict');
            this.pending.set(true);
          } else {
            this.error.set('network');
          }
          return of(null);
        }),
      )
      .subscribe((request) => {
        if (request !== null) {
          this.pendingReason.set('submitted');
          this.pending.set(true);
        }
      });
  }
}
