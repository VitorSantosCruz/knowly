import { Directive, HostListener, input, output } from '@angular/core';

// REQ-21/22/23 (user-profile-v2 amendment, 2026-08-02): mask-as-you-type display formatting
// for taxId, postalCode, and phone-type contact values. `rg` was deliberately excluded (removed
// entirely, per that same day's separate RG-removal amendment) and stays excluded.
//
// Amendment (2026-08-02, "country-agnostic identity/address model"): mask keys renamed
// `cpf`/`cep` -> `taxId`/`postalCode` and mask *selection* is now country-conditional via
// `[appInputMaskCountry]`, looked up against a small per-country pattern table below (not a
// generic mask-pattern-string interpreter — see PLAN.md's amendment). Where no pattern is
// defined for the given `(mask, country)` pair, the directive is a no-op passthrough (REQ-21's
// "plain, unmasked text input" behavior for a country with no known mask).
export type InputMaskType = 'taxId' | 'postalCode' | 'phone';

function onlyDigits(value: string): string {
  return value.replace(/\D/g, '');
}

function formatGrouped(digits: string, groupLengths: number[], separators: string[]): string {
  let result = '';
  let index = 0;
  let isFirst = true;

  for (const [groupIndex, groupLength] of groupLengths.entries()) {
    const part = digits.slice(index, index + groupLength);
    if (!part) {
      break;
    }
    const separator = isFirst ? '' : (separators.at(groupIndex - 1) ?? '');
    result += separator + part;
    isFirst = false;
    index += groupLength;
  }
  return result;
}

// -- Brazil (unchanged from the pre-amendment implementation) --
function formatCpf(digits: string): string {
  return formatGrouped(digits, [3, 3, 3, 2], ['.', '.', '-']);
}

function formatCep(digits: string): string {
  return formatGrouped(digits, [5, 3], ['-']);
}

function formatPhoneBr(digits: string): string {
  if (digits.length === 0) {
    return '';
  }

  const ddd = digits.slice(0, 2);
  if (digits.length <= 2) {
    return `(${ddd}`;
  }

  const isMobile = digits.length > 10;
  const midLength = isMobile ? 5 : 4;
  const mid = digits.slice(2, 2 + midLength);
  const rest = digits.slice(2 + midLength);

  let result = `(${ddd}) ${mid}`;
  if (rest) {
    result += `-${rest}`;
  }
  return result;
}

// -- United States --
function formatSsn(digits: string): string {
  return formatGrouped(digits, [3, 2, 4], ['-', '-']);
}

function formatZip(digits: string): string {
  return digits.slice(0, 5);
}

interface MaskDefinition {
  maxDigits: number;
  format: (digits: string) => string;
}

// Per-`(mask, country)` concrete patterns. Any pair not listed here has no known mask (REQ-21) —
// the directive/`formatMaskedValue` fall back to a plain, capped-nowhere passthrough of the raw
// digits, unformatted. Nested `Map`s (not `Record`s) — avoids a dynamic-key object-injection lint
// warning on the lookup below.
const MASK_TABLE = new Map<InputMaskType, Map<string, MaskDefinition>>([
  [
    'taxId',
    new Map([
      ['BR', { maxDigits: 11, format: formatCpf }],
      ['US', { maxDigits: 9, format: formatSsn }],
    ]),
  ],
  [
    'postalCode',
    new Map([
      ['BR', { maxDigits: 8, format: formatCep }],
      ['US', { maxDigits: 5, format: formatZip }],
    ]),
  ],
  ['phone', new Map([['BR', { maxDigits: 11, format: formatPhoneBr }]])],
]);

function definitionFor(
  mask: InputMaskType,
  country: string | null | undefined,
): MaskDefinition | null {
  return MASK_TABLE.get(mask)?.get(country ?? '') ?? null;
}

function formatByMask(
  mask: InputMaskType,
  country: string | null | undefined,
  rawValue: string,
): string {
  const definition = definitionFor(mask, country);

  if (!definition) {
    // REQ-21: no known mask for this country — plain, unmasked passthrough. The raw value is
    // left exactly as typed (not stripped to digits-only) since this is a genuine free-text
    // field for a country with no fixed national format.
    return rawValue;
  }

  return definition.format(onlyDigits(rawValue).slice(0, definition.maxDigits));
}

// Exported so consumers can format the *initial*/externally-driven `[value]` binding the same
// way the directive formats user keystrokes — without this, a `[value]` bound straight to the
// underlying unmasked signal would fight the directive's own DOM write on every input event.
export function formatMaskedValue(
  mask: InputMaskType,
  country: string | null | undefined,
  rawValue: string,
): string {
  return formatByMask(mask, country, rawValue);
}

// Counts how many digit characters appear in `value` up to (and excluding) `caret`, and
// returns the index in `masked` right after that same number of digits — the technique that
// keeps the caret "in place" (relative to the digits typed) instead of jumping to the end
// whenever the mask reinserts/removes punctuation around an edit.
function locateCaret(masked: string, digitsBeforeCaret: number): number {
  if (digitsBeforeCaret <= 0) {
    return 0;
  }

  let seen = 0;
  for (let i = 0; i < masked.length; i++) {
    if (/\d/.test(masked.charAt(i))) {
      seen++;
      if (seen === digitsBeforeCaret) {
        return i + 1;
      }
    }
  }
  return masked.length;
}

@Directive({
  selector: '[appInputMask]',
  standalone: true,
})
export class InputMaskDirective {
  readonly appInputMask = input.required<InputMaskType>();
  readonly appInputMaskCountry = input<string | null>(null);
  readonly appInputMaskChange = output<string>();

  @HostListener('input', ['$event'])
  protected onInput(event: Event): void {
    const element = event.target as HTMLInputElement;
    const rawValue = element.value;
    const mask = this.appInputMask();
    const country = this.appInputMaskCountry();
    const definition = definitionFor(mask, country);

    if (!definition) {
      // REQ-21: no-op passthrough — leave the DOM value/caret untouched, emit exactly what was
      // typed (this is a genuine free-text field for a country with no fixed national format).
      this.appInputMaskChange.emit(rawValue);
      return;
    }

    const caret = element.selectionStart ?? rawValue.length;
    const digitsBeforeCaret = onlyDigits(rawValue.slice(0, caret)).length;
    const masked = formatByMask(mask, country, rawValue);

    element.value = masked;
    const newCaret = locateCaret(masked, digitsBeforeCaret);
    element.setSelectionRange(newCaret, newCaret);

    // REQ-22: emitted value is always the plain digits, regardless of the masked display.
    this.appInputMaskChange.emit(onlyDigits(rawValue));
  }
}
