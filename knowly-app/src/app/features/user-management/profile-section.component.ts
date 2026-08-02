import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { catchError, of } from 'rxjs';
import { ProfileService, UserProfile } from '../../core/profile.service';
import { ErrorStateComponent } from '../../shared/error-state.component';
import { NoAccessStateComponent } from '../../shared/no-access-state.component';
import {
  ProfileFieldsFormComponent,
  ProfileFieldsFormSubmission,
} from '../../shared/profile-fields-form.component';

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

        @if (profile.avatarUrl) {
          <img
            data-testid="profile-section-avatar"
            [src]="profile.avatarUrl"
            alt=""
            class="mb-2 h-12 w-12 rounded-full object-cover"
          />
        }

        @if (!editing()) {
          <p class="text-sm text-ink-600 dark:text-ink-400">{{ profile.fields.fullName }}</p>
          <p class="text-sm text-ink-600 dark:text-ink-400">{{ profile.fields.taxId }}</p>
          <p class="text-sm text-ink-600 dark:text-ink-400">{{ profile.fields.countryCode }}</p>
          @if (profile.fields.address; as address) {
            <p class="text-sm text-ink-600 dark:text-ink-400">
              {{ address.addressLine1 }}, {{ address.addressLine2 }} - {{ address.city }}/{{
                address.stateRegion
              }}
              - {{ address.postalCode }} - {{ address.countryCode }}
            </p>
          }
          @for (contact of profile.fields.contacts; track contact.id) {
            <p class="text-sm text-ink-600 dark:text-ink-400">
              {{ contact.type }}: {{ contact.value }}
              @if (contact.isPrimary) {
                ({{ 'profile.fields.contacts.primary' | transloco }})
              }
            </p>
          }
          <p class="text-sm text-ink-600 dark:text-ink-400">{{ profile.email }}</p>

          @if (showEditToggle() && !hideEditToggle()) {
            <button
              data-testid="profile-section-edit-toggle"
              (click)="editing.set(true)"
              class="mt-2 text-signal-600 transition-colors duration-fast ease-fluid hover:text-signal-700 dark:text-signal-400 dark:hover:text-signal-300"
            >
              {{ 'profile.section.edit' | transloco }}
            </button>
          }
        } @else {
          <app-profile-fields-form [fields]="profile.fields" (submitted)="onSubmit($event)" />
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
export class ProfileSectionComponent {
  private readonly profileService = inject(ProfileService);

  readonly userId = input.required<number>();
  readonly canEdit = input(false);
  // REQ-12/SPEC judgment call 5: the edit affordance is hidden — not merely disabled — when the
  // viewer is looking at their own row, since REQ-11 (identity-profile-model-v2) removed
  // self-direct-edit entirely, for anyone.
  readonly ownUserId = input<number | null>(null);
  // REQ-28: hides this component's own bottom-of-content edit toggle when the parent detail
  // screen renders "Editar perfil" in its top header instead — the parent triggers editing via
  // `editTrigger` (an incrementing counter, same pattern as `ConfirmDialogComponent#retryToken`).
  readonly hideEditToggle = input(false);
  readonly editTrigger = input(0);

  protected readonly profile = signal<UserProfile | null>(null);
  protected readonly profileError = signal<DetailError>(null);
  protected readonly editing = signal(false);
  protected readonly conflictError = signal<string[] | null>(null);

  protected readonly showEditToggle = computed(
    () => this.canEdit() && this.userId() !== this.ownUserId(),
  );

  constructor() {
    // Only reacts to `userId()` changes — `canEdit`/`ownUserId` are read outside this effect's
    // tracking scope so they never trigger a duplicate re-fetch of the same profile (a real bug
    // caught by TDAD: `ngOnChanges` previously re-ran `loadProfile()` on *any* input change,
    // including `ownUserId` arriving asynchronously after the initial render).
    effect(() => {
      const userId = this.userId();
      this.loadProfile(userId);
    });

    let lastEditTrigger = 0;
    effect(() => {
      const trigger = this.editTrigger();

      if (trigger > 0 && trigger !== lastEditTrigger) {
        lastEditTrigger = trigger;
        this.editing.set(true);
      }
    });
  }

  private loadProfile(userId: number): void {
    this.profileService
      .getProfile(userId)
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

  // Deviation from PLAN.md resolved (knowly-api c0a817d): `PUT /api/users/{id}/profile` now
  // genuinely applies contact changes, so the contacts editor is shown (default `showContacts`)
  // and `contactChanges` from the form submission is threaded through to `directEdit`.
  protected onSubmit({ fields, contactChanges }: ProfileFieldsFormSubmission): void {
    this.conflictError.set(null);

    this.profileService
      .directEdit(this.userId(), fields, contactChanges)
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
