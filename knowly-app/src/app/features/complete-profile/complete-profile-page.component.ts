import { Component, OnInit, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { Router } from '@angular/router';
import { catchError, of } from 'rxjs';
import { MandatoryProfileFields, ProfileFields, ProfileService } from '../../core/profile.service';
import { ErrorStateComponent } from '../../shared/error-state.component';
import { getCountryFieldConfig } from '../../shared/country-field-config';
import {
  ProfileFieldsFormComponent,
  ProfileFieldsFormSubmission,
} from '../../shared/profile-fields-form.component';

// Human-readable Transloco key per flat field name a backend field-error can name — mirrors the
// labels the form itself renders (`profile.fields.*`), so the banner and the inline per-field
// message agree with what the user sees on the input. `taxId` is handled separately (country-driven
// CPF/SSN/NINO label, not a fixed Transloco string) — see `fieldLabel()` below. A `Map`, not a plain
// object, avoids a dynamic-key object-injection lint warning on the lookup (same reasoning as
// `country-field-config.ts`'s `COUNTRY_FIELD_CONFIG`).
const FIELD_LABEL_KEYS = new Map<string, string>([
  ['fullName', 'profile.fields.fullName'],
  ['address.addressLine1', 'profile.fields.address.addressLine1'],
  ['address.addressLine2', 'profile.fields.address.addressLine2'],
  ['address.city', 'profile.fields.address.city'],
  ['address.stateRegion', 'profile.fields.address.stateRegion'],
  ['address.postalCode', 'profile.fields.postalCodeGeneric'],
  // Bugfix (2026-08-02): the "País" dropdown drives `address.countryCode`, which the backend
  // validates as `@NotBlank` — this maps that backend field name onto the same label the form
  // renders for it, instead of falling through to the raw `'address.countryCode'` key.
  ['address.countryCode', 'profile.fields.country'],
]);

// Same local shape `OwnProfilePageComponent` already defines — duplicated per that component's
// own existing pattern (PLAN.md's "State and data" section), not extracted into a shared const.
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
                  <li>{{ fieldLabel(field) }}</li>
                }
              </ul>
            </div>
          }

          <app-profile-fields-form
            [fields]="formFields()"
            [requireAllFields]="true"
            [disabled]="submitting()"
            [fieldErrors]="fieldErrors() ?? []"
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
  private readonly transloco = inject(TranslocoService);

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
            // Bugfix (2026-08-02): `INVALID_CPF` is the checksum-failure code the backend actually
            // sends for this endpoint (`IdentityExceptionHandler#handleInvalidCpf`) — a plain
            // `{ code }` body, no `errors` array — so it never matched the generic mapping below
            // and always fell through to the `'unknown'` fallback. Mirrors `tenant-create`'s
            // existing `INVALID_TAX_ID` handling for the CNPJ case.
            const code = err.error?.code as string | undefined;
            if (code === 'INVALID_CPF') {
              this.fieldErrors.set(['taxId']);
              return of(null);
            }

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

  // Bugfix (2026-08-02): resolves a flat field name (`'taxId'`, `'address.city'`, ...) into the
  // human-readable label the form itself renders for that field, instead of the raw backend key
  // (or the `'unknown'` fallback) previously shown verbatim in the banner. `taxId` is
  // country-driven (CPF/SSN/NINO), same as `ProfileFieldsFormComponent`'s own label logic.
  protected fieldLabel(field: string): string {
    if (field === 'taxId') {
      return getCountryFieldConfig(this.formFields().countryCode).taxIdLabel;
    }

    const key = FIELD_LABEL_KEYS.get(field);
    return key ? this.transloco.translate(key) : field;
  }
}
