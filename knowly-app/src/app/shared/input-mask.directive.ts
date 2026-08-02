import { Directive, HostListener, input, output } from '@angular/core';

// REQ-21/22/23 (user-profile-v2 amendment, 2026-08-02): mask-as-you-type display formatting
// for CPF, CEP, and phone-type contact values. `rg` is deliberately excluded — Brazilian RG
// has no single national format/checksum — see PLAN.md's amendment section and DECISIONS.md.
export type InputMaskType = 'cpf' | 'cep' | 'phone';

function maxDigitsFor(mask: InputMaskType): number {
  switch (mask) {
    case 'cpf':
      return 11;
    case 'cep':
      return 8;
    case 'phone':
      return 11;
  }
}

function onlyDigits(value: string): string {
  return value.replace(/\D/g, '');
}

function formatCpf(digits: string): string {
  const part1 = digits.slice(0, 3);
  const part2 = digits.slice(3, 6);
  const part3 = digits.slice(6, 9);
  const part4 = digits.slice(9, 11);

  let result = part1;
  if (part2) {
    result += `.${part2}`;
  }
  if (part3) {
    result += `.${part3}`;
  }
  if (part4) {
    result += `-${part4}`;
  }
  return result;
}

function formatCep(digits: string): string {
  const part1 = digits.slice(0, 5);
  const part2 = digits.slice(5, 8);

  let result = part1;
  if (part2) {
    result += `-${part2}`;
  }
  return result;
}

function formatPhone(digits: string): string {
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

function formatByMask(mask: InputMaskType, digits: string): string {
  switch (mask) {
    case 'cpf':
      return formatCpf(digits);
    case 'cep':
      return formatCep(digits);
    case 'phone':
      return formatPhone(digits);
  }
}

// Exported so consumers can format the *initial*/externally-driven `[value]` binding the same
// way the directive formats user keystrokes — without this, a `[value]` bound straight to the
// underlying unmasked signal would fight the directive's own DOM write on every input event
// (the signal updates to the unmasked digits, then the `[value]` binding re-applies that
// unmasked string over the directive's masked one on the same change-detection pass).
export function formatMaskedValue(mask: InputMaskType, rawValue: string): string {
  return formatByMask(mask, onlyDigits(rawValue).slice(0, maxDigitsFor(mask)));
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
  readonly appInputMaskChange = output<string>();

  @HostListener('input', ['$event'])
  protected onInput(event: Event): void {
    const element = event.target as HTMLInputElement;
    const rawValue = element.value;
    const caret = element.selectionStart ?? rawValue.length;
    const digitsBeforeCaret = onlyDigits(rawValue.slice(0, caret)).length;

    const mask = this.appInputMask();
    const digits = onlyDigits(rawValue).slice(0, maxDigitsFor(mask));
    const masked = formatByMask(mask, digits);

    element.value = masked;
    const newCaret = locateCaret(masked, digitsBeforeCaret);
    element.setSelectionRange(newCaret, newCaret);

    this.appInputMaskChange.emit(digits);
  }
}
