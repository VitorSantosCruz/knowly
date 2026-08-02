import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { catchError, of } from 'rxjs';
import { ProfileFields, ProfileService, UserProfile } from '../../core/profile.service';
import { ErrorStateComponent } from '../../shared/error-state.component';
import { AvatarUploadComponent } from '../../shared/avatar-upload.component';
import {
  ProfileFieldsFormComponent,
  ProfileFieldsFormSubmission,
} from '../../shared/profile-fields-form.component';

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
  selector: 'app-own-profile-page',
  imports: [TranslocoPipe, ErrorStateComponent, ProfileFieldsFormComponent, AvatarUploadComponent],
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

          <app-avatar-upload
            class="mb-4 block"
            [avatarUrl]="profile.avatarUrl"
            (fileSelected)="onAvatarSelected($event)"
          />

          @if (avatarError(); as messageKey) {
            <p
              data-testid="profile-avatar-error"
              class="mb-3 rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700 dark:bg-red-950/30 dark:text-red-400"
            >
              {{ messageKey | transloco }}
            </p>
          }

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

  protected readonly profile = signal<UserProfile | null>(null);
  protected readonly formFields = signal<ProfileFields>(EMPTY_FIELDS);
  protected readonly error = signal<'network' | null>(null);
  protected readonly pending = signal(false);
  protected readonly pendingReason = signal<'submitted' | 'conflict'>('submitted');
  protected readonly conflictError = signal<string[] | null>(null);
  protected readonly avatarError = signal<string | null>(null);

  protected readonly pendingMessage = computed(() =>
    this.pendingReason() === 'conflict' ? 'profile.alreadyPending' : 'profile.pending',
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
          this.formFields.set(profile.fields);
        }
      });
  }

  // REQ-2: always the pending-request path, for every session — no direct-edit branch remains
  // for a caller's own non-avatar fields, for anyone (this replaces `user-profile`'s old
  // `hasDirectEditRight`/admin-shortcut logic entirely).
  protected onSubmit({ fields, contactChanges }: ProfileFieldsFormSubmission): void {
    if (this.pending()) {
      return;
    }

    this.formFields.set(fields);
    this.conflictError.set(null);

    this.profileService
      .submitEditRequest(fields, contactChanges)
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

  // REQ-8/9: self-only, direct, unconditional avatar upload — independent of the non-avatar
  // form's pending state.
  protected onAvatarSelected(file: File): void {
    this.avatarError.set(null);

    this.profileService
      .uploadAvatar(file)
      .pipe(
        catchError(() => {
          this.avatarError.set('profile.avatar.error');
          return of(null);
        }),
      )
      .subscribe((updated) => {
        if (updated !== null) {
          this.profile.set(updated);
        }
      });
  }
}
