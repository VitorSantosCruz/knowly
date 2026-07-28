import { Component, OnChanges, inject, input, signal } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { catchError, of } from 'rxjs';
import { ProfileFields, ProfileService, UserProfile } from '../../core/profile.service';
import { ErrorStateComponent } from '../../shared/error-state.component';
import { NoAccessStateComponent } from '../../shared/no-access-state.component';
import { ProfileFieldsFormComponent } from '../../shared/profile-fields-form.component';

type DetailError = 'network' | 'permission-denied' | null;

@Component({
  selector: 'app-profile-section',
  imports: [TranslocoPipe, ErrorStateComponent, NoAccessStateComponent, ProfileFieldsFormComponent],
  template: `
    <section data-testid="profile-section">
      <h3 class="mb-2 text-sm font-medium text-ink-700 dark:text-ink-300">
        {{ 'profile.section.title' | transloco }}
      </h3>

      @if (profileError() === 'permission-denied') {
        <app-no-access-state />
      } @else if (profileError() === 'network') {
        <app-error-state />
      } @else if (profile(); as profile) {
        @if (conflictError(); as fields) {
          <p
            data-testid="profile-section-conflict"
            class="mb-2 rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700 dark:bg-red-950/30 dark:text-red-400"
          >
            {{ 'profileEditRequests.conflict' | transloco: { fields: fields.join(', ') } }}
          </p>
        }

        @if (!editing()) {
          <p class="text-sm text-ink-600 dark:text-ink-400">{{ profile.fullName }}</p>
          <p class="text-sm text-ink-600 dark:text-ink-400">{{ profile.address }}</p>
          <p class="text-sm text-ink-600 dark:text-ink-400">{{ profile.rg }}</p>
          <p class="text-sm text-ink-600 dark:text-ink-400">{{ profile.cpf }}</p>
          <p class="text-sm text-ink-600 dark:text-ink-400">{{ profile.phone }}</p>
          <p class="text-sm text-ink-600 dark:text-ink-400">{{ profile.email }}</p>

          @if (canEdit()) {
            <button
              data-testid="profile-section-edit-toggle"
              (click)="editing.set(true)"
              class="mt-2 text-signal-600 transition-colors duration-fast ease-fluid hover:text-signal-700 dark:text-signal-400 dark:hover:text-signal-300"
            >
              {{ 'profile.section.edit' | transloco }}
            </button>
          }
        } @else {
          <app-profile-fields-form [fields]="profile" (submitted)="onSubmit($event)" />
          <button
            data-testid="profile-section-cancel"
            (click)="editing.set(false)"
            class="mt-2 text-sm text-ink-500 dark:text-ink-400"
          >
            {{ 'profile.section.cancel' | transloco }}
          </button>
        }
      }
    </section>
  `,
})
export class ProfileSectionComponent implements OnChanges {
  private readonly profileService = inject(ProfileService);

  readonly userId = input.required<number>();
  readonly canEdit = input(false);

  protected readonly profile = signal<UserProfile | null>(null);
  protected readonly profileError = signal<DetailError>(null);
  protected readonly editing = signal(false);
  protected readonly conflictError = signal<string[] | null>(null);

  ngOnChanges(): void {
    this.loadProfile();
  }

  private loadProfile(): void {
    this.profileService
      .getProfile(this.userId())
      .pipe(
        catchError((err) => {
          this.profileError.set(err.status === 403 ? 'permission-denied' : 'network');
          return of(null);
        }),
      )
      .subscribe((profile) => {
        if (profile !== null) {
          this.profile.set(profile);
        }
      });
  }

  protected onSubmit(fields: ProfileFields): void {
    this.conflictError.set(null);

    this.profileService
      .directEdit(this.userId(), fields)
      .pipe(
        catchError((err) => {
          if (err.status === 409) {
            this.conflictError.set(err.error?.conflictingFields ?? []);
          } else {
            this.profileError.set(err.status === 403 ? 'permission-denied' : 'network');
          }
          return of(null);
        }),
      )
      .subscribe((updated) => {
        if (updated !== null) {
          this.profile.set(updated);
          this.editing.set(false);
        }
      });
  }
}
