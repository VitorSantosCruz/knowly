import { Component, computed, effect, inject, input, output, signal } from '@angular/core';
import { LucideCircleAlert } from '@lucide/angular';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import {
  Address,
  Contact,
  ContactChange,
  ContactType,
  ProfileFields,
} from '../core/profile.service';
import { COUNTRY_FIELD_CONFIG, getCountryFieldConfig } from './country-field-config';
import { formatMaskedValue, InputMaskDirective } from './input-mask.directive';
import { PhoneDdiInputComponent } from './phone-ddi-input.component';

const CONTACT_TYPES: ContactType[] = ['PHONE', 'WHATSAPP', 'EMAIL', 'OTHER'];
const MAX_CONTACTS = 5;

const EMPTY_ADDRESS: Address = {
  addressLine1: '',
  addressLine2: '',
  city: '',
  stateRegion: '',
  postalCode: '',
  countryCode: '',
};

const EMPTY_FIELDS: ProfileFields = {
  fullName: '',
  taxId: '',
  countryCode: '',
  address: EMPTY_ADDRESS,
  contacts: [],
};

// Client-side-only row identity for `@for` tracking — separate from `id` (`null` until the
// backend assigns one on approval), so newly-added rows in the same session stay stable.
interface ContactRow extends Contact {
  rowKey: string;
}

// `w-full` here (bugfix, 2026-08-02): the input/select used to be a direct child of a
// `flex flex-col` `<label>`, whose default `align-items: stretch` filled the available width
// without any explicit width class. Wrapping each input in `<div class="relative">` (for the
// inline error icon) took the input out of that flex-item position, so it silently reverted to
// intrinsic/auto sizing and shrank to content width. Explicit `w-full` restores full-width
// sizing regardless of the parent's layout mode.
const BASE_INPUT_CLASS =
  'w-full rounded-xl border border-ink-300/70 bg-white px-3 py-1.5 text-sm text-ink-900 shadow-sm focus:border-signal-400 focus:ring-2 focus:ring-signal-400/30 focus:outline-none dark:border-ink-700 dark:bg-ink-800 dark:text-ink-100';

// Inline per-field validation error style (bugfix, 2026-08-02): reuses the same rounded/padding
// shape as `BASE_INPUT_CLASS`, only swapping the border/ring color to red, so a field named in
// `fieldErrors` gets a visibly distinct (but layout-identical) state. `pr-9` (both variants, so
// swapping between them never shifts layout) reserves room for the inline warning icon rendered
// inside the `relative`-positioned wrapper around each input — see the template.
const ERROR_INPUT_CLASS =
  'w-full rounded-xl border border-red-500 bg-white px-3 py-1.5 pr-9 text-sm text-ink-900 shadow-sm focus:border-red-500 focus:ring-2 focus:ring-red-400/40 focus:outline-none dark:border-red-500 dark:bg-ink-800 dark:text-ink-100';

// Bugfix (2026-08-02, round 2): client-side required-field checks (`clientRequiredErrors`) use
// their own field-name -> Transloco-key map, so `hasFieldError`'s generic message doesn't
// collide with each field's specific "X is required" copy.
const CLIENT_REQUIRED_MESSAGE_KEYS: ReadonlyMap<string, string> = new Map([
  ['fullName', 'profile.fields.fullNameRequired'],
  ['taxId', 'profile.fields.taxIdRequired'],
  ['address.addressLine1', 'profile.fields.address.addressLine1Required'],
  ['address.city', 'profile.fields.address.cityRequired'],
  ['address.stateRegion', 'profile.fields.address.stateRegionRequired'],
  ['address.postalCode', 'profile.fields.address.postalCodeRequired'],
]);

let rowKeySeed = 0;

function toRow(contact: Contact): ContactRow {
  return { ...contact, rowKey: contact.id !== null ? `id-${contact.id}` : `new-${rowKeySeed++}` };
}

export interface ProfileFieldsFormSubmission {
  fields: ProfileFields;
  contactChanges: ContactChange[];
}

@Component({
  selector: 'app-profile-fields-form',
  imports: [TranslocoPipe, InputMaskDirective, PhoneDdiInputComponent, LucideCircleAlert],
  template: `
    <form
      data-testid="profile-fields-form"
      (submit)="onSubmit($event)"
      novalidate
      class="flex flex-col gap-3"
    >
      <label class="flex flex-col gap-1 text-sm text-ink-700 dark:text-ink-300">
        {{ 'profile.fields.fullName' | transloco }}
        <div class="relative w-full">
          <input
            data-testid="profile-field-fullName"
            type="text"
            [value]="localFields().fullName"
            [disabled]="disabled()"
            [required]="requireAllFields()"
            [placeholder]="'profile.fields.fullNamePlaceholder' | transloco"
            (input)="onFieldChange('fullName', $any($event.target).value)"
            [class]="inputClassFor('fullName')"
          />
          @if (hasFieldError('fullName')) {
            <svg
              lucideCircleAlert
              class="pointer-events-none absolute top-1/2 right-2.5 h-4 w-4 -translate-y-1/2 text-red-500 dark:text-red-400"
              aria-hidden="true"
            ></svg>
          }
        </div>
        @if (hasFieldError('fullName')) {
          <p
            data-testid="profile-field-error-fullName"
            class="text-xs text-red-600 dark:text-red-400"
          >
            {{ fieldErrorMessageKey('fullName') | transloco }}
          </p>
        }
      </label>

      <label class="flex flex-col gap-1 text-sm text-ink-700 dark:text-ink-300">
        {{ 'profile.fields.country' | transloco }}
        <div class="relative w-full">
          <select
            data-testid="profile-field-countryCode"
            [disabled]="disabled()"
            [required]="requireAllFields()"
            (change)="onCountryChange($any($event.target).value)"
            [class]="
              inputClassFor('address.countryCode') +
              (countryRequiredMessage() ? ' border-red-500 pr-9' : '')
            "
          >
            <option value="" [selected]="!localFields().countryCode">
              {{ 'profile.fields.countryNotSpecified' | transloco }}
            </option>
            @for (code of countryCodes; track code) {
              <option [value]="code" [selected]="code === localFields().countryCode">
                {{ 'profile.fields.countryNames.' + code | transloco }}
              </option>
            }
          </select>
          @if (hasFieldError('address.countryCode') || countryRequiredMessage()) {
            <svg
              lucideCircleAlert
              class="pointer-events-none absolute top-1/2 right-2.5 h-4 w-4 -translate-y-1/2 text-red-500 dark:text-red-400"
              aria-hidden="true"
            ></svg>
          }
        </div>
        @if (countryRequiredMessage()) {
          <p
            data-testid="profile-country-required-message"
            class="text-xs text-red-600 dark:text-red-400"
          >
            {{ 'profile.fields.countryRequired' | transloco }}
          </p>
        }
      </label>

      <label class="flex flex-col gap-1 text-sm text-ink-700 dark:text-ink-300">
        @if (hasCountrySpecificLabels()) {
          {{ activeCountryConfig().taxIdLabel }}
        } @else {
          {{ 'profile.fields.taxIdGeneric' | transloco }}
        }
        <div class="relative w-full">
          <input
            data-testid="profile-field-taxId"
            type="text"
            [value]="
              formatMaskedValue('taxId', localFields().countryCode, localFields().taxId ?? '')
            "
            [disabled]="disabled()"
            [required]="requireAllFields()"
            [placeholder]="taxIdPlaceholder()"
            [appInputMask]="'taxId'"
            [appInputMaskCountry]="localFields().countryCode"
            (appInputMaskChange)="onFieldChange('taxId', $event)"
            [class]="inputClassFor('taxId')"
          />
          @if (hasFieldError('taxId')) {
            <svg
              lucideCircleAlert
              class="pointer-events-none absolute top-1/2 right-2.5 h-4 w-4 -translate-y-1/2 text-red-500 dark:text-red-400"
              aria-hidden="true"
            ></svg>
          }
        </div>
        @if (hasFieldError('taxId')) {
          <p data-testid="profile-field-error-taxId" class="text-xs text-red-600 dark:text-red-400">
            {{ fieldErrorMessageKey('taxId', 'profile.fields.taxIdInvalid') | transloco }}
          </p>
        }
      </label>

      <fieldset data-testid="profile-address-fieldset" class="flex flex-col gap-2">
        <legend class="text-sm text-ink-700 dark:text-ink-300">
          {{ 'profile.fields.address.title' | transloco }}
        </legend>

        <label class="flex flex-col gap-1 text-sm text-ink-700 dark:text-ink-300">
          {{ 'profile.fields.address.addressLine1' | transloco }}
          <div class="relative w-full">
            <input
              data-testid="profile-address-field-addressLine1"
              type="text"
              [value]="localFields().address?.addressLine1 ?? ''"
              [disabled]="disabled()"
              [required]="requireAllFields()"
              [placeholder]="'profile.fields.address.addressLine1Placeholder' | transloco"
              [title]="'profile.fields.address.addressLine1Tooltip' | transloco"
              (input)="onAddressFieldChange('addressLine1', $any($event.target).value)"
              [class]="inputClassFor('address.addressLine1')"
            />
            @if (hasFieldError('address.addressLine1')) {
              <svg
                lucideCircleAlert
                class="pointer-events-none absolute top-1/2 right-2.5 h-4 w-4 -translate-y-1/2 text-red-500 dark:text-red-400"
                aria-hidden="true"
              ></svg>
            }
          </div>
          @if (hasFieldError('address.addressLine1')) {
            <p
              data-testid="profile-field-error-address.addressLine1"
              class="text-xs text-red-600 dark:text-red-400"
            >
              {{ fieldErrorMessageKey('address.addressLine1') | transloco }}
            </p>
          }
        </label>

        <label class="flex flex-col gap-1 text-sm text-ink-700 dark:text-ink-300">
          {{ 'profile.fields.address.addressLine2' | transloco }}
          <div class="relative w-full">
            <input
              data-testid="profile-address-field-addressLine2"
              type="text"
              [value]="localFields().address?.addressLine2 ?? ''"
              [disabled]="disabled()"
              [placeholder]="'profile.fields.address.addressLine2Placeholder' | transloco"
              [title]="'profile.fields.address.addressLine2Tooltip' | transloco"
              (input)="onAddressFieldChange('addressLine2', $any($event.target).value)"
              [class]="inputClassFor('address.addressLine2')"
            />
            @if (hasFieldError('address.addressLine2')) {
              <svg
                lucideCircleAlert
                class="pointer-events-none absolute top-1/2 right-2.5 h-4 w-4 -translate-y-1/2 text-red-500 dark:text-red-400"
                aria-hidden="true"
              ></svg>
            }
          </div>
          @if (hasFieldError('address.addressLine2')) {
            <p
              data-testid="profile-field-error-address.addressLine2"
              class="text-xs text-red-600 dark:text-red-400"
            >
              {{ fieldErrorMessageKey('address.addressLine2') | transloco }}
            </p>
          }
        </label>

        <label class="flex flex-col gap-1 text-sm text-ink-700 dark:text-ink-300">
          {{ 'profile.fields.address.city' | transloco }}
          <div class="relative w-full">
            <input
              data-testid="profile-address-field-city"
              type="text"
              [value]="localFields().address?.city ?? ''"
              [disabled]="disabled()"
              [required]="requireAllFields()"
              [placeholder]="'profile.fields.address.cityPlaceholder' | transloco"
              (input)="onAddressFieldChange('city', $any($event.target).value)"
              [class]="inputClassFor('address.city')"
            />
            @if (hasFieldError('address.city')) {
              <svg
                lucideCircleAlert
                class="pointer-events-none absolute top-1/2 right-2.5 h-4 w-4 -translate-y-1/2 text-red-500 dark:text-red-400"
                aria-hidden="true"
              ></svg>
            }
          </div>
          @if (hasFieldError('address.city')) {
            <p
              data-testid="profile-field-error-address.city"
              class="text-xs text-red-600 dark:text-red-400"
            >
              {{ fieldErrorMessageKey('address.city') | transloco }}
            </p>
          }
        </label>

        <label class="flex flex-col gap-1 text-sm text-ink-700 dark:text-ink-300">
          @if (activeCountryConfig().stateRegionLabel; as stateRegionLabel) {
            {{ stateRegionLabel }}
          } @else {
            {{ 'profile.fields.address.stateRegion' | transloco }}
          }
          <div class="relative w-full">
            <input
              data-testid="profile-address-field-stateRegion"
              type="text"
              [value]="localFields().address?.stateRegion ?? ''"
              [disabled]="disabled()"
              [placeholder]="stateRegionPlaceholder()"
              (input)="onAddressFieldChange('stateRegion', $any($event.target).value)"
              [class]="inputClassFor('address.stateRegion')"
            />
            @if (hasFieldError('address.stateRegion')) {
              <svg
                lucideCircleAlert
                class="pointer-events-none absolute top-1/2 right-2.5 h-4 w-4 -translate-y-1/2 text-red-500 dark:text-red-400"
                aria-hidden="true"
              ></svg>
            }
          </div>
          @if (hasFieldError('address.stateRegion')) {
            <p
              data-testid="profile-field-error-address.stateRegion"
              class="text-xs text-red-600 dark:text-red-400"
            >
              {{ fieldErrorMessageKey('address.stateRegion') | transloco }}
            </p>
          }
        </label>

        <label class="flex flex-col gap-1 text-sm text-ink-700 dark:text-ink-300">
          @if (hasCountrySpecificLabels()) {
            {{ activeCountryConfig().postalCodeLabel }}
          } @else {
            {{ 'profile.fields.postalCodeGeneric' | transloco }}
          }
          <div class="relative w-full">
            <input
              data-testid="profile-address-field-postalCode"
              type="text"
              [value]="
                formatMaskedValue(
                  'postalCode',
                  localFields().countryCode,
                  localFields().address?.postalCode ?? ''
                )
              "
              [disabled]="disabled()"
              [required]="requireAllFields()"
              [placeholder]="postalCodePlaceholder()"
              [title]="postalCodeTooltip()"
              [appInputMask]="'postalCode'"
              [appInputMaskCountry]="localFields().countryCode"
              (appInputMaskChange)="onAddressFieldChange('postalCode', $event)"
              [class]="inputClassFor('address.postalCode')"
            />
            @if (hasFieldError('address.postalCode')) {
              <svg
                lucideCircleAlert
                class="pointer-events-none absolute top-1/2 right-2.5 h-4 w-4 -translate-y-1/2 text-red-500 dark:text-red-400"
                aria-hidden="true"
              ></svg>
            }
          </div>
          @if (hasFieldError('address.postalCode')) {
            <p
              data-testid="profile-field-error-address.postalCode"
              class="text-xs text-red-600 dark:text-red-400"
            >
              {{ fieldErrorMessageKey('address.postalCode') | transloco }}
            </p>
          }
        </label>
      </fieldset>

      @if (showContacts()) {
        <fieldset data-testid="profile-contacts-fieldset" class="flex flex-col gap-2">
          <legend class="text-sm text-ink-700 dark:text-ink-300">
            {{ 'profile.fields.contacts.title' | transloco }}
          </legend>

          @for (row of contacts(); track row.rowKey) {
            <div
              [attr.data-testid]="'profile-contact-row-' + row.rowKey"
              class="flex flex-wrap items-center gap-2 rounded-lg border border-ink-200/70 p-2 dark:border-ink-700"
            >
              <select
                [attr.data-testid]="'profile-contact-type-' + row.rowKey"
                [disabled]="disabled()"
                (change)="onContactFieldChange(row.rowKey, 'type', $any($event.target).value)"
                class="rounded-lg border border-ink-300/70 bg-white px-2 py-1 text-sm text-ink-900 dark:border-ink-700 dark:bg-ink-800 dark:text-ink-100"
              >
                @for (type of contactTypes; track type) {
                  <option [value]="type" [selected]="type === row.type">
                    {{ 'profile.fields.contacts.types.' + type | transloco }}
                  </option>
                }
              </select>
              @if (isPhoneContact(row.type)) {
                <app-phone-ddi-input
                  [testIdSuffix]="'-' + row.rowKey"
                  [value]="row.value"
                  [countryCode]="localFields().countryCode"
                  [disabled]="disabled()"
                  (valueChange)="onContactFieldChange(row.rowKey, 'value', $event)"
                />
              } @else {
                <input
                  [attr.data-testid]="'profile-contact-value-' + row.rowKey"
                  type="text"
                  [value]="row.value"
                  [disabled]="disabled()"
                  (input)="onContactFieldChange(row.rowKey, 'value', $any($event.target).value)"
                  [placeholder]="'profile.fields.contacts.value' | transloco"
                  class="min-w-0 flex-1 rounded-lg border border-ink-300/70 bg-white px-2 py-1 text-sm text-ink-900 dark:border-ink-700 dark:bg-ink-800 dark:text-ink-100"
                />
              }
              <input
                [attr.data-testid]="'profile-contact-label-' + row.rowKey"
                type="text"
                [value]="row.label ?? ''"
                [disabled]="disabled()"
                (input)="onContactFieldChange(row.rowKey, 'label', $any($event.target).value)"
                [placeholder]="'profile.fields.contacts.label' | transloco"
                class="min-w-0 flex-1 rounded-lg border border-ink-300/70 bg-white px-2 py-1 text-sm text-ink-900 dark:border-ink-700 dark:bg-ink-800 dark:text-ink-100"
              />
              <label class="flex items-center gap-1 text-sm text-ink-700 dark:text-ink-300">
                <input
                  [attr.data-testid]="'profile-contact-primary-' + row.rowKey"
                  type="checkbox"
                  [checked]="row.isPrimary"
                  [disabled]="disabled()"
                  (change)="onContactPrimaryChange(row.rowKey)"
                  class="accent-signal-500"
                />
                {{ 'profile.fields.contacts.primary' | transloco }}
              </label>
              <button
                type="button"
                [attr.data-testid]="'profile-contact-remove-' + row.rowKey"
                [disabled]="disabled()"
                (click)="onRemoveContact(row.rowKey)"
                class="text-red-600 transition-colors duration-fast ease-fluid hover:text-red-700 disabled:pointer-events-none disabled:opacity-50 dark:text-red-400 dark:hover:text-red-300"
              >
                {{ 'profile.fields.contacts.remove' | transloco }}
              </button>
            </div>
          }

          @if (contactLimitMessage()) {
            <p
              data-testid="profile-contacts-limit-message"
              class="text-sm text-red-600 dark:text-red-400"
            >
              {{ 'profile.fields.contacts.limitReached' | transloco }}
            </p>
          }

          @if (contactsRequiredMessage()) {
            <p
              data-testid="profile-contacts-required-message"
              class="text-sm text-red-600 dark:text-red-400"
            >
              {{ 'profile.fields.contacts.required' | transloco }}
            </p>
          }

          <button
            type="button"
            data-testid="profile-contact-add"
            [disabled]="disabled() || contacts().length >= maxContacts"
            (click)="onAddContact()"
            class="self-start rounded-xl border border-ink-300/70 px-3 py-1.5 text-sm font-medium text-ink-700 transition-colors duration-fast ease-fluid hover:bg-ink-50 disabled:pointer-events-none disabled:opacity-50 dark:border-ink-700 dark:text-ink-300 dark:hover:bg-ink-800/50"
          >
            {{ 'profile.fields.contacts.add' | transloco }}
          </button>
        </fieldset>
      }

      <button
        type="submit"
        data-testid="profile-fields-submit"
        [disabled]="disabled()"
        class="self-start rounded-xl bg-ink-800 px-3 py-1.5 text-sm font-medium text-white transition-colors duration-fast ease-fluid hover:bg-signal-600 active:bg-signal-700 disabled:pointer-events-none disabled:opacity-50 dark:bg-ink-600 dark:hover:bg-signal-500"
      >
        {{ 'profile.fields.save' | transloco }}
      </button>
    </form>
  `,
})
export class ProfileFieldsFormComponent {
  readonly fields = input.required<ProfileFields>();
  readonly disabled = input(false);
  // Deviation from PLAN.md (Tier 2, reflects the shipped backend contract): the shipped
  // `PUT /api/users/{id}/profile` never applies contact changes (`UserProfileService#directEdit`
  // hardcodes an empty `contactChanges` list), so `ProfileSectionComponent`'s inline edit of an
  // *other* user hides this fieldset entirely — showing controls that silently no-op would be
  // misleading. Defaults to `true` (own-profile edit-request flow, which does support contacts).
  readonly showContacts = input(true);
  // REQ-3/5/14 (bootstrap-profile-completion): when `true`, every mandatory input renders
  // `required` and submission is blocked client-side if `contacts().length === 0`. Defaults to
  // `false` so every existing call site (`OwnProfilePageComponent`/`ProfileSectionComponent`)
  // stays behaviorally unchanged.
  readonly requireAllFields = input(false);
  // Bugfix (2026-08-02): field names the caller (backend error response, mapped by the parent
  // page) says are currently invalid — e.g. `['taxId']` or `['address.city']`. Purely presentational
  // here: this component doesn't validate anything itself, it just renders a red border + inline
  // message for whichever of its own known field names appear in this list.
  readonly fieldErrors = input<string[]>([]);
  readonly submitted = output<ProfileFieldsFormSubmission>();

  protected readonly contactTypes = CONTACT_TYPES;
  protected readonly maxContacts = MAX_CONTACTS;
  protected readonly countryCodes = [...COUNTRY_FIELD_CONFIG.keys()].filter(
    (code) => code !== 'DEFAULT',
  );

  // Exposed for the template's `[value]` bindings on masked fields — see `input-mask.directive.ts`
  // for why the initial/externally-driven display must be formatted the same way the directive
  // formats keystrokes.
  protected readonly formatMaskedValue = formatMaskedValue;

  protected readonly localFields = signal<ProfileFields>(EMPTY_FIELDS);
  protected readonly contacts = signal<ContactRow[]>([]);
  protected readonly contactLimitMessage = signal(false);
  protected readonly contactsRequiredMessage = signal(false);
  // Bugfix (2026-08-02): client-side guard for the country selector, mirroring
  // `contactsRequiredMessage`'s pattern — blocks submission before the request ever reaches the
  // backend instead of relying solely on the server's `NotBlank` rejection of `address.countryCode`.
  protected readonly countryRequiredMessage = signal(false);
  // Bugfix (2026-08-02, round 2): field names currently failing a client-side "required" check
  // (evaluated on submit, only when `requireAllFields()` is true) — see `hasFieldError`/
  // `fieldErrorMessageKey`. Distinct from `fieldErrors` (backend-driven) so a field can show its
  // "is required" message before the request ever reaches the server, and its backend-driven
  // invalid-format message once it does.
  protected readonly clientRequiredErrors = signal<Set<string>>(new Set());

  // REQ-1a: drives the taxId/postalCode/address-line labels and mask availability live, without
  // requiring a page reload — resolves SPEC Judgment call 9 in favor of one shared control.
  protected readonly activeCountryConfig = computed(() =>
    getCountryFieldConfig(this.localFields().countryCode),
  );

  // Bugfix (2026-08-02): only BR/US/GB have a genuinely country-specific taxId/postalCode
  // label (CPF/CEP, SSN/ZIP Code, NINO/Postcode) — those stay hardcoded, untranslated proper
  // nouns/acronyms regardless of UI locale, by design. Any other/unset country falls back to
  // `COUNTRY_FIELD_CONFIG`'s `DEFAULT` entry, whose labels are plain English words that must go
  // through Transloco instead of always rendering in English.
  // `stateRegionLabel` is handled separately (see the template above) since it's a generic field
  // name, not a country-specific document/form name — only GB (`County`) has a genuinely distinct
  // term; BR/US/DEFAULT omit it entirely so they fall back to the Transloco-driven generic label.
  protected readonly hasCountrySpecificLabels = computed(() =>
    COUNTRY_FIELD_CONFIG.has(this.localFields().countryCode ?? ''),
  );

  private readonly transloco = inject(TranslocoService);

  // Placeholder/tooltip UX polish (2026-08-02): example values so users know the expected
  // shape of ambiguous fields ("Address line 1" alone doesn't say street vs. street+number).
  // Country-specific formats (taxId, state/region, postal code) mirror the existing
  // `hasCountrySpecificLabels()`/`activeCountryConfig()` convention — a hardcoded, untranslated
  // literal per country when one exists, falling back to a Transloco-driven generic example
  // otherwise (address line 1/2 and city aren't country-specific, so those stay plain Transloco
  // keys directly in the template).
  protected readonly taxIdPlaceholder = computed(() => {
    const override = this.activeCountryConfig().taxIdPlaceholder;
    return override ?? this.transloco.translate('profile.fields.taxIdGenericPlaceholder');
  });

  protected readonly stateRegionPlaceholder = computed(() => {
    const override = this.activeCountryConfig().stateRegionPlaceholder;
    return override ?? this.transloco.translate('profile.fields.stateRegionGenericPlaceholder');
  });

  protected readonly postalCodePlaceholder = computed(() => {
    const override = this.activeCountryConfig().postalCodePlaceholder;
    return override ?? this.transloco.translate('profile.fields.postalCodeGenericPlaceholder');
  });

  protected readonly postalCodeTooltip = computed(() => {
    const override = this.activeCountryConfig().postalCodeTooltip;
    return override ?? this.transloco.translate('profile.fields.postalCodeGenericTooltip');
  });

  // The array this component was initialized/re-synced with, keyed by id, used to diff at
  // submit time (PLAN.md's "diff-on-submit, not a running change-log" judgment call).
  private initialContactsById = new Map<number, Contact>();

  constructor() {
    // Re-syncs whenever the parent hands in a new `fields` value (initial load, or a
    // refreshed value after a successful edit) — never overwrites values the user is
    // actively typing outside of a genuine parent-driven update, matching this codebase's
    // "own the signal, react to input changes via effect" pattern.
    effect(() => {
      const incoming = this.fields();
      const incomingAddress = incoming.address ?? { ...EMPTY_ADDRESS };
      // Bugfix (2026-08-02, round 2): the "País" <select>'s visible selection is driven by the
      // top-level `countryCode`, not `address.countryCode` — see the template's `[selected]`
      // binding. A returning/pre-populated profile can arrive with a top-level `countryCode` set
      // but a stale/empty `address.countryCode` (the two are independent fields server-side), so
      // the dropdown renders an already-correct-looking value the user never touches, and
      // `onCountryChange()` (which only runs on a user-driven `(change)` event) never fires to
      // sync it. Keeping both in lockstep here — the same way `onCountryChange` does for the
      // user-driven path — means whatever the select visibly shows is always what gets submitted,
      // regardless of whether a `(change)` event ever fired.
      this.localFields.set({
        ...incoming,
        address: { ...incomingAddress, countryCode: incoming.countryCode },
      });

      const rows = incoming.contacts.map(toRow);
      this.contacts.set(rows);
      this.initialContactsById = new Map(
        incoming.contacts.filter((contact) => contact.id !== null).map((c) => [c.id as number, c]),
      );
      this.contactLimitMessage.set(false);
    });
  }

  // REQ-21: mask-as-you-type only applies while a contact row's type is a phone-shaped one;
  // switching to EMAIL/OTHER via the `<select>` reverts to a plain, unmasked input.
  protected isPhoneContact(type: ContactType): boolean {
    return type === 'PHONE' || type === 'WHATSAPP';
  }

  // Bugfix (2026-08-02): inline per-field validation error state — see `fieldErrors` input doc.
  // Extended (round 2) to also cover client-side required-field checks (`clientRequiredErrors`),
  // so a blank mandatory field gets the same red border/icon/message treatment as a backend
  // rejection, without waiting on a round trip.
  protected hasFieldError(name: string): boolean {
    return this.fieldErrors().includes(name) || this.clientRequiredErrors().has(name);
  }

  // Resolves which Transloco key to show in a field's inline message: the client-required
  // message when this field is currently blank per `clientRequiredErrors`, otherwise `fallback`
  // (the backend-driven "invalid" message for that field — `profile.fields.fieldInvalid` unless
  // the caller passes a more specific one, e.g. taxId's `taxIdInvalid`).
  protected fieldErrorMessageKey(name: string, fallback = 'profile.fields.fieldInvalid'): string {
    if (this.clientRequiredErrors().has(name)) {
      return CLIENT_REQUIRED_MESSAGE_KEYS.get(name) ?? fallback;
    }
    return fallback;
  }

  protected inputClassFor(name: string): string {
    return this.hasFieldError(name) ? ERROR_INPUT_CLASS : BASE_INPUT_CLASS;
  }

  protected onFieldChange(
    field: Exclude<keyof ProfileFields, 'address' | 'contacts'>,
    value: string,
  ): void {
    this.localFields.update((current) => ({ ...current, [field]: value }));
  }

  // Bugfix (2026-08-02): the "País" <select> previously only updated the top-level `countryCode`
  // (which drives taxId/postalCode/stateRegion labels and masks) and never touched
  // `address.countryCode` — a separate field the backend's `MandatoryProfileFieldsDto` actually
  // validates as `@NotBlank`. That left the submitted payload's `address.countryCode` blank/stale
  // even when a country was visibly selected, so the backend always rejected the request. Both
  // fields are driven by the same single dropdown, so both must be kept in sync from it.
  protected onCountryChange(value: string): void {
    this.localFields.update((current) => ({
      ...current,
      countryCode: value,
      address: { ...(current.address ?? EMPTY_ADDRESS), countryCode: value },
    }));
  }

  protected onAddressFieldChange(field: keyof Address, value: string): void {
    this.localFields.update((current) => ({
      ...current,
      address: { ...(current.address ?? EMPTY_ADDRESS), [field]: value },
    }));
  }

  protected onContactFieldChange(
    rowKey: string,
    field: 'type' | 'value' | 'label',
    value: string,
  ): void {
    this.contacts.update((rows) =>
      rows.map((row) => (row.rowKey === rowKey ? { ...row, [field]: value } : row)),
    );
  }

  // REQ-6: at most one primary per `type`, enforced client-side.
  protected onContactPrimaryChange(rowKey: string): void {
    this.contacts.update((rows) => {
      const target = rows.find((row) => row.rowKey === rowKey);
      if (!target) {
        return rows;
      }

      return rows.map((row) => {
        if (row.rowKey === rowKey) {
          return { ...row, isPrimary: true };
        }
        if (row.type === target.type) {
          return { ...row, isPrimary: false };
        }
        return row;
      });
    });
  }

  // REQ-7: block a 6th contact client-side, before any submit.
  protected onAddContact(): void {
    if (this.contacts().length >= this.maxContacts) {
      this.contactLimitMessage.set(true);
      return;
    }

    this.contactLimitMessage.set(false);
    this.contacts.update((rows) => [
      ...rows,
      toRow({ id: null, type: 'PHONE', value: '', label: null, isPrimary: false }),
    ]);
  }

  protected onRemoveContact(rowKey: string): void {
    this.contacts.update((rows) => rows.filter((row) => row.rowKey !== rowKey));
  }

  protected onSubmit(event: Event): void {
    event.preventDefault();

    if (this.disabled()) {
      return;
    }

    if (this.requireAllFields() && !this.localFields().countryCode) {
      this.countryRequiredMessage.set(true);
      return;
    }
    this.countryRequiredMessage.set(false);

    if (this.requireAllFields() && this.contacts().length === 0) {
      this.contactsRequiredMessage.set(true);
      return;
    }
    this.contactsRequiredMessage.set(false);

    if (this.requireAllFields()) {
      const missing = new Set<string>();
      const current = this.localFields();
      if (!current.fullName?.trim()) missing.add('fullName');
      if (!current.taxId?.trim()) missing.add('taxId');
      if (!current.address?.addressLine1?.trim()) missing.add('address.addressLine1');
      if (!current.address?.city?.trim()) missing.add('address.city');
      if (!current.address?.stateRegion?.trim()) missing.add('address.stateRegion');
      if (!current.address?.postalCode?.trim()) missing.add('address.postalCode');

      this.clientRequiredErrors.set(missing);
      if (missing.size > 0) {
        return;
      }
    } else {
      this.clientRequiredErrors.set(new Set());
    }

    this.submitted.emit({
      fields: { ...this.localFields(), contacts: this.contacts() },
      contactChanges: this.diffContactChanges(),
    });
  }

  private diffContactChanges(): ContactChange[] {
    const changes: ContactChange[] = [];
    const currentIds = new Set<number>();

    for (const row of this.contacts()) {
      if (row.id === null) {
        changes.push({
          action: 'ADD',
          contactId: null,
          type: row.type,
          value: row.value,
          label: row.label,
          isPrimary: row.isPrimary,
        });
        continue;
      }

      currentIds.add(row.id);
      const original = this.initialContactsById.get(row.id);
      const changed =
        !original ||
        original.type !== row.type ||
        original.value !== row.value ||
        original.label !== row.label ||
        original.isPrimary !== row.isPrimary;

      if (changed) {
        changes.push({
          action: 'UPDATE',
          contactId: row.id,
          type: row.type,
          value: row.value,
          label: row.label,
          isPrimary: row.isPrimary,
        });
      }
    }

    for (const [id, original] of this.initialContactsById) {
      if (!currentIds.has(id)) {
        // Carrying the original contact's type/value/label along with a REMOVE (rather than
        // nulling them out, as before) is what lets an approver's profile-edit-requests inbox
        // show which contact is being removed instead of a bare "REMOVE" with no detail --
        // the backend stores whatever this sends verbatim, no server-side stripping.
        changes.push({
          action: 'REMOVE',
          contactId: id,
          type: original.type,
          value: original.value,
          label: original.label,
          isPrimary: original.isPrimary,
        });
      }
    }

    return changes;
  }
}
