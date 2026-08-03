import { Component, inject, signal } from '@angular/core';
import {
  AbstractControl,
  FormArray,
  FormBuilder,
  FormGroup,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { Router } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { HttpErrorResponse } from '@angular/common/http';
import { catchError, of } from 'rxjs';
import { buttonClass } from '../../shared/button-classes';
import { AddressFieldsComponent, AddressFieldSpec } from '../../shared/address-fields.component';
import {
  ContactsListEditorComponent,
  createContactGroup,
} from '../../shared/contacts-list-editor.component';
import { ActiveTenantService, CreateTenantRequest } from '../../core/active-tenant.service';

const INPUT_CLASS =
  'w-full rounded-lg border border-ink-200 bg-white px-3 py-2 text-sm text-ink-900 focus:border-signal-500 focus:ring-1 focus:ring-signal-500 focus:outline-none dark:border-ink-700 dark:bg-ink-800 dark:text-white';

const COMPANY_ADDRESS_FIELDS: AddressFieldSpec[] = [
  { name: 'postalCode', labelKey: 'tenantCreate.address.postalCode' },
  { name: 'street', labelKey: 'tenantCreate.address.street' },
  { name: 'number', labelKey: 'tenantCreate.address.number' },
  { name: 'complement', labelKey: 'tenantCreate.address.complement' },
  { name: 'neighborhood', labelKey: 'tenantCreate.address.neighborhood' },
  { name: 'city', labelKey: 'tenantCreate.address.city' },
  { name: 'state', labelKey: 'tenantCreate.address.state' },
];

// user-profile-v2 amendment (2026-08-02): the first admin's mandatory address is now the same
// country-agnostic 6-field shape as `MandatoryProfileFieldsDto.address` everywhere else. A single
// shared `userProfile.countryCode` control (rendered alongside `taxId`, see `userProfileFields`
// below) drives both the profile-level and address-level `countryCode` values on submit —
// resolving the same "one shared control" judgment call `user-profile-v2/PLAN.md` made, no
// second, independent address-country selector here either.
const USER_ADDRESS_FIELDS: AddressFieldSpec[] = [
  { name: 'addressLine1', labelKey: 'tenantCreate.address.addressLine1' },
  { name: 'addressLine2', labelKey: 'tenantCreate.address.addressLine2' },
  { name: 'city', labelKey: 'tenantCreate.address.city' },
  { name: 'stateRegion', labelKey: 'tenantCreate.address.stateRegion' },
  { name: 'postalCode', labelKey: 'tenantCreate.address.postalCode' },
];

// REQ-10: only Brazil enforces the 14-digit CNPJ shape; every other country only needs a
// non-empty taxId. Matched loosely (case-insensitive) since `country` is free text here.
const BRAZIL_NAMES = ['brazil', 'brasil', 'br'];

function isBrazil(country: string | null | undefined): boolean {
  return BRAZIL_NAMES.includes((country ?? '').trim().toLowerCase());
}

// REQ-22-24: pure, non-authoritative mirror of backend CnpjChecksumValidator -- exact same weight
// sequences and alphanumeric-value adjustment, for early UX feedback only. The backend always
// re-validates regardless of what this accepts (REQ-24); this mirror is deliberately not shared
// code with the backend (different language, no cross-subproject sharing mechanism) and is allowed
// to be tested/maintained independently.
function isValidCnpj(value: string): boolean {
  const chars = value.toUpperCase();
  if (chars.length !== 14) {
    return false;
  }

  const charValue = (c: string) => c.charCodeAt(0) - 48;
  const weightedSum = (base: string, weights: number[]) =>
    base
      .split('')
      .map(charValue)
      .reduce((sum, value, i) => sum + value * (weights.at(i) ?? 0), 0);
  const expectedDigit = (sum: number) => {
    const remainder = sum % 11;
    return remainder < 2 ? 0 : 11 - remainder;
  };

  const base12 = chars.slice(0, 12);
  const digit1 = expectedDigit(weightedSum(base12, [5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2]));
  if (digit1 !== charValue(chars[12])) {
    return false;
  }

  const digit2 = expectedDigit(
    weightedSum(base12 + digit1, [6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2]),
  );
  return digit2 === charValue(chars[13]);
}

function taxIdValidator(control: AbstractControl): ValidationErrors | null {
  const value = (control.value ?? '').toString().trim();
  if (!value) {
    return { required: true };
  }

  const country = control.parent?.get('country')?.value as string | undefined;
  if (isBrazil(country)) {
    const digits = value.replace(/\D/g, '');
    if (digits.length !== 14) {
      return { cnpjShape: true };
    }

    const stripped = value.replace(/[.\-/]/g, '');
    if (!isValidCnpj(stripped)) {
      return { cnpjChecksum: true };
    }
  }

  return null;
}

@Component({
  selector: 'app-tenant-create-page',
  imports: [
    TranslocoPipe,
    ReactiveFormsModule,
    AddressFieldsComponent,
    ContactsListEditorComponent,
  ],
  template: `
    <div data-testid="tenant-create-page" class="page-shell flex flex-col gap-6">
      <h1 class="font-display text-2xl font-semibold tracking-tight text-ink-900 dark:text-white">
        {{ 'tenantCreate.title' | transloco }}
      </h1>

      <form
        data-testid="tenant-create-form"
        class="flex flex-col gap-8"
        [formGroup]="form"
        (submit)="onSubmit($event)"
      >
        <fieldset
          class="flex flex-col gap-4 rounded-2xl border border-ink-200/70 p-6 dark:border-ink-800/70"
        >
          <legend
            data-testid="tenant-create-company-heading"
            class="px-2 text-lg font-semibold text-ink-900 dark:text-white"
          >
            {{ 'tenantCreate.companySection' | transloco }}
          </legend>

          @for (field of flatCompanyFields; track field.name) {
            <label class="flex flex-col gap-1.5">
              <span class="text-sm font-medium text-ink-700 dark:text-ink-300">{{
                field.labelKey | transloco
              }}</span>
              <input
                [attr.data-testid]="'tenant-create-' + field.name"
                type="text"
                [formControlName]="field.name"
                (blur)="markTouched(field.name)"
                [class]="inputClass"
              />
              @if (showFlatError(field.name)) {
                <p
                  [attr.data-testid]="'tenant-create-error-' + field.name"
                  class="text-sm text-red-600 dark:text-red-400"
                >
                  {{ flatErrorMessage(field.name) | transloco }}
                </p>
              }
            </label>
          }

          <h2 class="text-sm font-semibold text-ink-700 dark:text-ink-300">
            {{ 'tenantCreate.companyAddressTitle' | transloco }}
          </h2>
          <app-address-fields [formGroup]="companyAddressGroup" [fields]="companyAddressFields" />
        </fieldset>

        <fieldset
          class="flex flex-col gap-4 rounded-2xl border border-ink-200/70 p-6 dark:border-ink-800/70"
        >
          <legend
            data-testid="tenant-create-user-heading"
            class="px-2 text-lg font-semibold text-ink-900 dark:text-white"
          >
            {{ 'tenantCreate.userSection' | transloco }}
          </legend>

          <label class="flex flex-col gap-1.5">
            <span class="text-sm font-medium text-ink-700 dark:text-ink-300">{{
              'tenantCreate.adminEmail' | transloco
            }}</span>
            <input
              data-testid="tenant-create-adminEmail"
              type="email"
              formControlName="adminEmail"
              (blur)="markTouched('adminEmail')"
              [class]="inputClass"
            />
            @if (showFlatError('adminEmail')) {
              <p
                data-testid="tenant-create-error-adminEmail"
                class="text-sm text-red-600 dark:text-red-400"
              >
                {{ flatErrorMessage('adminEmail') | transloco }}
              </p>
            }
          </label>

          <div formGroupName="userProfile" class="flex flex-col gap-4">
            @for (field of userProfileFields; track field.name) {
              <label class="flex flex-col gap-1.5">
                <span class="text-sm font-medium text-ink-700 dark:text-ink-300">{{
                  field.labelKey | transloco
                }}</span>
                <input
                  [attr.data-testid]="'tenant-create-userProfile-' + field.name"
                  type="text"
                  [formControlName]="field.name"
                  (blur)="markUserProfileTouched(field.name)"
                  [class]="inputClass"
                />
                @if (showUserProfileError(field.name)) {
                  <p
                    [attr.data-testid]="'tenant-create-error-userProfile-' + field.name"
                    class="text-sm text-red-600 dark:text-red-400"
                  >
                    {{ 'shared.fieldRequired' | transloco }}
                  </p>
                }
              </label>
            }
          </div>

          <h2 class="text-sm font-semibold text-ink-700 dark:text-ink-300">
            {{ 'tenantCreate.userAddressTitle' | transloco }}
          </h2>
          <app-address-fields
            [formGroup]="userAddressGroup"
            [fields]="userAddressFields"
            idPrefix="user-"
          />

          <h2 class="text-sm font-semibold text-ink-700 dark:text-ink-300">
            {{ 'tenantCreate.contacts.title' | transloco }}
          </h2>
          <app-contacts-list-editor [formArray]="contactsArray" [showErrors]="submitAttempted" />
        </fieldset>

        <fieldset
          class="flex flex-col gap-4 rounded-2xl border border-ink-200/70 p-6 dark:border-ink-800/70"
        >
          <legend
            data-testid="tenant-create-role-heading"
            class="px-2 text-lg font-semibold text-ink-900 dark:text-white"
          >
            {{ 'tenantCreate.roleSection' | transloco }}
          </legend>

          <label class="flex flex-col gap-1.5">
            <span class="text-sm font-medium text-ink-700 dark:text-ink-300">{{
              'tenantCreate.role' | transloco
            }}</span>
            <select data-testid="tenant-create-role" formControlName="role" [class]="inputClass">
              <option value="MEMBER_ADMIN">{{ 'tenantCreate.roleMemberAdmin' | transloco }}</option>
              <option value="MEMBER">{{ 'tenantCreate.roleMember' | transloco }}</option>
            </select>
          </label>
        </fieldset>

        @if (errorMessage(); as message) {
          <p
            data-testid="tenant-create-error"
            class="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700 dark:border-red-900/50 dark:bg-red-950/30 dark:text-red-400"
          >
            {{ message | transloco }}
          </p>
        }

        <button type="submit" [class]="submitButtonClass" [disabled]="submitting()">
          {{ 'tenantCreate.submit' | transloco }}
        </button>
      </form>
    </div>
  `,
})
export class TenantCreatePageComponent {
  private readonly fb = inject(FormBuilder);
  private readonly activeTenantService = inject(ActiveTenantService);
  private readonly router = inject(Router);

  protected readonly inputClass = INPUT_CLASS;
  protected readonly submitButtonClass = buttonClass('primary');
  protected readonly submitting = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected submitAttempted = false;

  protected readonly companyAddressFields = COMPANY_ADDRESS_FIELDS;
  protected readonly userAddressFields = USER_ADDRESS_FIELDS;

  protected readonly flatCompanyFields: AddressFieldSpec[] = [
    { name: 'name', labelKey: 'tenantCreate.name' },
    { name: 'legalName', labelKey: 'tenantCreate.legalName' },
    { name: 'taxId', labelKey: 'tenantCreate.taxId' },
    { name: 'country', labelKey: 'tenantCreate.country' },
    { name: 'contactEmail', labelKey: 'tenantCreate.contactEmail' },
    { name: 'contactPhone', labelKey: 'tenantCreate.contactPhone' },
  ];

  protected readonly userProfileFields: AddressFieldSpec[] = [
    { name: 'fullName', labelKey: 'tenantCreate.fullName' },
    { name: 'taxId', labelKey: 'tenantCreate.userTaxId' },
    { name: 'countryCode', labelKey: 'tenantCreate.country' },
  ];

  protected readonly form: FormGroup = this.fb.group({
    name: ['', Validators.required],
    legalName: ['', Validators.required],
    taxId: ['', taxIdValidator],
    country: ['', Validators.required],
    contactEmail: ['', [Validators.required, Validators.email]],
    contactPhone: ['', Validators.required],
    companyAddress: this.fb.group({
      postalCode: ['', Validators.required],
      street: ['', Validators.required],
      number: ['', Validators.required],
      complement: [''],
      neighborhood: ['', Validators.required],
      city: ['', Validators.required],
      state: ['', Validators.required],
    }),
    adminEmail: ['', [Validators.required, Validators.email]],
    userProfile: this.fb.group({
      fullName: ['', Validators.required],
      taxId: ['', Validators.required],
      countryCode: ['', Validators.required],
      address: this.fb.group({
        addressLine1: ['', Validators.required],
        addressLine2: [''],
        city: ['', Validators.required],
        stateRegion: [''],
        postalCode: ['', Validators.required],
      }),
      contacts: this.fb.array([createContactGroup()]),
    }),
    role: ['MEMBER_ADMIN', Validators.required],
  });

  constructor() {
    // REQ-10: re-evaluate taxId's conditional CNPJ-shape validator whenever `country` changes.
    this.form.get('country')?.valueChanges.subscribe(() => {
      this.form.get('taxId')?.updateValueAndValidity();
    });
  }

  protected get companyAddressGroup(): FormGroup {
    return this.form.get('companyAddress') as FormGroup;
  }

  protected get userProfileGroup(): FormGroup {
    return this.form.get('userProfile') as FormGroup;
  }

  protected get userAddressGroup(): FormGroup {
    return this.userProfileGroup.get('address') as FormGroup;
  }

  protected get contactsArray(): FormArray {
    return this.userProfileGroup.get('contacts') as FormArray;
  }

  protected markTouched(name: string): void {
    this.form.get(name)?.markAsTouched();
  }

  protected markUserProfileTouched(name: string): void {
    this.userProfileGroup.get(name)?.markAsTouched();
  }

  protected showFlatError(name: string): boolean {
    const control = this.form.get(name);
    return !!control && control.invalid && control.touched;
  }

  protected showUserProfileError(name: string): boolean {
    const control = this.userProfileGroup.get(name);
    return !!control && control.invalid && control.touched;
  }

  protected flatErrorMessage(name: string): string {
    const control = this.form.get(name);
    if (name === 'taxId' && control?.errors?.['cnpjShape']) {
      return 'tenantCreate.taxIdCnpjShape';
    }
    if (name === 'taxId' && control?.errors?.['cnpjChecksum']) {
      return 'tenantCreate.taxIdCnpjChecksum';
    }
    if (name === 'taxId' && control?.errors?.['invalidTaxId']) {
      return 'tenantCreate.taxIdInvalid';
    }
    return 'shared.fieldRequired';
  }

  protected onSubmit(event: Event): void {
    event.preventDefault();

    this.submitAttempted = true;
    this.form.markAllAsTouched();
    this.contactsArray.markAllAsTouched();

    if (this.form.invalid || this.contactsArray.length === 0) {
      this.errorMessage.set('tenantCreate.invalid');
      return;
    }

    this.errorMessage.set(null);
    this.submitting.set(true);

    const request = this.buildRequest();

    this.activeTenantService
      .createTenant(request)
      .pipe(
        catchError((error: HttpErrorResponse) => {
          this.handleSubmitError(error);
          return of(null);
        }),
      )
      .subscribe((result) => {
        this.submitting.set(false);

        if (result !== null) {
          this.router.navigateByUrl('/select-tenant');
        }
      });
  }

  private buildRequest(): CreateTenantRequest {
    const raw = this.form.getRawValue();

    return {
      name: raw.name,
      legalName: raw.legalName,
      taxId: raw.taxId,
      country: raw.country,
      contactEmail: raw.contactEmail,
      contactPhone: raw.contactPhone,
      address: {
        postalCode: raw.companyAddress.postalCode,
        street: raw.companyAddress.street,
        number: raw.companyAddress.number,
        complement: raw.companyAddress.complement || null,
        neighborhood: raw.companyAddress.neighborhood,
        city: raw.companyAddress.city,
        state: raw.companyAddress.state,
      },
      adminEmail: raw.adminEmail,
      profile: {
        fullName: raw.userProfile.fullName,
        taxId: raw.userProfile.taxId,
        countryCode: raw.userProfile.countryCode,
        address: {
          addressLine1: raw.userProfile.address.addressLine1,
          addressLine2: raw.userProfile.address.addressLine2 || null,
          city: raw.userProfile.address.city,
          stateRegion: raw.userProfile.address.stateRegion || null,
          postalCode: raw.userProfile.address.postalCode,
          countryCode: raw.userProfile.countryCode,
        },
        contacts: raw.userProfile.contacts,
      },
      role: raw.role,
    };
  }

  // REQ-11/REQ-15: map an identifiable field-level error onto the matching control; fall back
  // to the generic banner (REQ-5) whenever the response doesn't identify a specific field.
  private handleSubmitError(error: HttpErrorResponse): void {
    const code = error.error?.code as string | undefined;

    if (error.status === 409 && code === 'TENANT_ALREADY_EXISTS') {
      this.form.get('taxId')?.setErrors({ conflict: true });
      this.form.get('taxId')?.markAsTouched();
      return;
    }

    if (error.status === 400 && code === 'INVALID_TAX_ID') {
      this.form.get('taxId')?.setErrors({ invalidTaxId: true });
      this.form.get('taxId')?.markAsTouched();
      return;
    }

    const fieldErrors = error.error?.errors as { field?: string }[] | undefined;
    if (error.status === 400 && Array.isArray(fieldErrors)) {
      let mapped = false;
      for (const fieldError of fieldErrors) {
        const control = this.resolveUserProfileControl(fieldError.field);
        if (control) {
          control.setErrors({ server: true });
          control.markAsTouched();
          mapped = true;
        }
      }
      if (mapped) {
        return;
      }
    }

    this.errorMessage.set('tenantCreate.submitError');
  }

  private resolveUserProfileControl(field: string | undefined): AbstractControl | null {
    if (!field) {
      return null;
    }
    const name = field.startsWith('profile.') ? field.slice('profile.'.length) : field;
    return this.userProfileGroup.get(name);
  }
}
