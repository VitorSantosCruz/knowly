// user-profile-v2 amendment (2026-08-02, "country-agnostic identity/address model"): a plain,
// static lookup table (no HTTP call, no signal — reference data, not application state, see
// PLAN.md's "why a plain lookup object, not a service/signal") mapping an ISO 3166-1 alpha-2
// `countryCode` to the labels/mask-availability REQ-1a/REQ-21 require. Not an attempt at full
// ISO-3166 coverage (PLAN.md's "Out of scope") — BR/US/GB plus a generic DEFAULT fallback.
export interface CountryFieldConfig {
  taxIdLabel: string;
  postalCodeLabel: string;
  addressLine1Label: string;
  cityLabel: string;
  stateRegionLabel: string;
  /** Whether `InputMaskDirective` has a concrete mask pattern for this field in this country. */
  hasTaxIdMask: boolean;
  hasPostalCodeMask: boolean;
  hasPhoneMask: boolean;
}

export const DEFAULT_COUNTRY_CODE = 'DEFAULT';

// A `Map`, not a plain `Record` — avoids a dynamic-key object-injection lint warning on the
// `getCountryFieldConfig` lookup below (`.get()` isn't a property-access expression the way
// `obj[key]` is), while still being a plain, hand-rolled, no-dependency static data structure.
export const COUNTRY_FIELD_CONFIG = new Map<string, CountryFieldConfig>([
  [
    'BR',
    {
      taxIdLabel: 'CPF',
      postalCodeLabel: 'CEP',
      addressLine1Label: 'Address line 1',
      cityLabel: 'City',
      stateRegionLabel: 'State',
      hasTaxIdMask: true,
      hasPostalCodeMask: true,
      hasPhoneMask: true,
    },
  ],
  [
    'US',
    {
      taxIdLabel: 'SSN',
      postalCodeLabel: 'ZIP Code',
      addressLine1Label: 'Address line 1',
      cityLabel: 'City',
      stateRegionLabel: 'State',
      hasTaxIdMask: true,
      hasPostalCodeMask: true,
      hasPhoneMask: false,
    },
  ],
  [
    'GB',
    {
      taxIdLabel: 'NINO',
      postalCodeLabel: 'Postcode',
      addressLine1Label: 'Address line 1',
      cityLabel: 'City',
      stateRegionLabel: 'County',
      hasTaxIdMask: false,
      hasPostalCodeMask: false,
      hasPhoneMask: false,
    },
  ],
  [
    DEFAULT_COUNTRY_CODE,
    {
      taxIdLabel: 'Tax ID',
      postalCodeLabel: 'Postal Code',
      addressLine1Label: 'Address line 1',
      cityLabel: 'City',
      stateRegionLabel: 'State/Region',
      hasTaxIdMask: false,
      hasPostalCodeMask: false,
      hasPhoneMask: false,
    },
  ],
]);

/** REQ-1a: falls back to the generic `DEFAULT` entry for any `countryCode` not explicitly listed. */
export function getCountryFieldConfig(countryCode: string | null | undefined): CountryFieldConfig {
  return (
    COUNTRY_FIELD_CONFIG.get(countryCode ?? '') ??
    (COUNTRY_FIELD_CONFIG.get(DEFAULT_COUNTRY_CODE) as CountryFieldConfig)
  );
}

/** REQ-6a/Judgment call 10: a small, explicitly-approximate DDI-length seed map (PLAN.md — not a
 * full ITU calling-code table). Falls back to 2 digits for any country not seeded here. */
export const DDI_LENGTH_BY_COUNTRY = new Map<string, number>([
  ['BR', 2],
  ['US', 1],
  ['GB', 2],
]);

export function ddiLengthFor(countryCode: string | null | undefined): number {
  return DDI_LENGTH_BY_COUNTRY.get(countryCode ?? '') ?? 2;
}

/** Default DDI digits (no leading `+`) offered to a brand-new phone/WhatsApp contact row,
 * seeded from the same map — a starting guess, always user-editable, never locked. */
export const DEFAULT_DDI_BY_COUNTRY = new Map<string, string>([
  ['BR', '55'],
  ['US', '1'],
  ['GB', '44'],
]);

export function defaultDdiFor(countryCode: string | null | undefined): string {
  return DEFAULT_DDI_BY_COUNTRY.get(countryCode ?? '') ?? '';
}
