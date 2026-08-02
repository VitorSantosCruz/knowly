import { Component, OnInit, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { TranslocoPipe } from '@jsverse/transloco';
import { Router } from '@angular/router';
import { catchError, of } from 'rxjs';
import { MandatoryProfileFields, ProfileFields, ProfileService } from '../../core/profile.service';
import { ErrorStateComponent } from '../../shared/error-state.component';
import {
  ProfileFieldsFormComponent,
  ProfileFieldsFormSubmission,
} from '../../shared/profile-fields-form.component';

// Same local shape `OwnProfilePageComponent` already defines — duplicated per that component's
// own existing pattern (PLAN.md's "State and data" section), not extracted into a shared const.
const EMPTY_FIELDS: ProfileFields = {
  fullName: '',
  cpf: '',
  rg: '',
  rgOrgaoEmissor: '',
  birthDate: '',
  address: {
    cep: '',
    logradouro: '',
    numero: '',
    complemento: '',
    bairro: '',
    cidade: '',
    estado: '',
    pais: '',
  },
  contacts: [],
};

interface BackendFieldError {
  field?: string;
}

@Component({
  selector: 'app-complete-profile-page',
  imports: [TranslocoPipe, ErrorStateComponent, ProfileFieldsFormComponent],
  template: `
    <div data-testid="complete-profile-page" class="page-shell">
      @if (error() === 'network') {
        <app-error-state />
      } @else if (profile(); as profile) {
        <div
          class="enter-fluid rounded-2xl border border-ink-200/70 bg-white p-6 shadow-sm shadow-ink-900/5 dark:border-ink-800/70 dark:bg-ink-900 dark:shadow-none"
        >
          <h1 class="mb-2 font-semibold text-ink-900 dark:text-white">
            {{ 'completeProfile.title' | transloco }}
          </h1>
          <p class="mb-4 text-sm text-ink-600 dark:text-ink-400">
            {{ 'completeProfile.intro' | transloco }}
          </p>

          <p
            data-testid="complete-profile-email"
            class="mb-4 text-sm text-ink-600 dark:text-ink-400"
          >
            {{ 'completeProfile.email' | transloco }}: {{ profile.email }}
          </p>

          @if (fieldErrors(); as errors) {
            <div
              data-testid="complete-profile-field-errors"
              class="mb-3 rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700 dark:bg-red-950/30 dark:text-red-400"
            >
              <p>{{ 'completeProfile.fieldErrorsIntro' | transloco }}</p>
              <ul>
                @for (field of errors; track field) {
                  <li>{{ field }}</li>
                }
              </ul>
            </div>
          }

          <app-profile-fields-form
            [fields]="formFields()"
            [requireAllFields]="true"
            [disabled]="submitting()"
            (submitted)="onSubmit($event)"
          />
        </div>
      }
    </div>
  `,
})
export class CompleteProfilePageComponent implements OnInit {
  private readonly profileService = inject(ProfileService);
  private readonly router = inject(Router);

  protected readonly profile = signal<{ email: string } | null>(null);
  protected readonly formFields = signal<ProfileFields>(EMPTY_FIELDS);
  protected readonly submitting = signal(false);
  protected readonly error = signal<'network' | null>(null);
  protected readonly fieldErrors = signal<string[] | null>(null);

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
          this.profile.set({ email: profile.email });
          this.formFields.set(profile.fields);
        }
      });
  }

  // REQ-6/7/8/9/10: maps the form's submission into MandatoryProfileFields (contacts stripped
  // of `id` — every contact submitted here is new by construction, `contactChanges` is ignored,
  // there is no prior state to diff against) and calls the one-time self-completion endpoint.
  protected onSubmit({ fields }: ProfileFieldsFormSubmission): void {
    if (this.submitting()) {
      return;
    }

    this.formFields.set(fields);
    this.error.set(null);
    this.fieldErrors.set(null);
    this.submitting.set(true);

    const dto: MandatoryProfileFields = {
      ...fields,
      contacts: fields.contacts.map((contact) => ({
        type: contact.type,
        value: contact.value,
        label: contact.label,
        isPrimary: contact.isPrimary,
      })),
    };

    this.profileService
      .completeOwnProfile(dto)
      .pipe(
        catchError((err: HttpErrorResponse) => {
          this.submitting.set(false);

          // REQ-9: already-complete is the state the caller wanted anyway — treat as success.
          if (err.status === 409 && err.error?.code === 'PROFILE_ALREADY_COMPLETE') {
            this.router.navigateByUrl('/welcome');
            return of(null);
          }

          if (err.status === 400) {
            // Only derived field *names* are ever surfaced/logged — never the raw error body,
            // which may carry cpf/rg values (SPEC.md's security NFR, task 17a).
            const backendErrors = Array.isArray(err.error?.errors)
              ? (err.error.errors as BackendFieldError[])
              : [];
            const names = backendErrors
              .map((fieldError) => fieldError.field)
              .filter((field): field is string => typeof field === 'string');
            this.fieldErrors.set(names.length > 0 ? names : ['unknown']);
            return of(null);
          }

          this.error.set('network');
          return of(null);
        }),
      )
      .subscribe((result) => {
        if (result !== null) {
          this.submitting.set(false);
          this.router.navigateByUrl('/welcome');
        }
      });
  }
}
