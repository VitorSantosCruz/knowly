import { Component, effect, input, output, signal } from '@angular/core';
import { defaultDdiFor, ddiLengthFor } from './country-field-config';
import { formatMaskedValue, InputMaskDirective } from './input-mask.directive';

function onlyDigits(value: string): string {
  return value.replace(/\D/g, '');
}

/**
 * REQ-6a (user-profile-v2 amendment, 2026-08-02, resolving SPEC Judgment call 10): splits an
 * E.164 value (`+<ddi><number>`) into its DDI/national-number parts, using `defaultCountry`'s
 * seeded DDI length as a heuristic — E.164 doesn't self-delimit DDI length from the string alone,
 * this is a deliberate approximation (PLAN.md), not a full ITU calling-code table.
 */
export function splitE164(
  value: string,
  defaultCountry: string | null,
): { ddi: string; number: string } {
  const digits = onlyDigits(value);
  const length = ddiLengthFor(defaultCountry);
  return { ddi: digits.slice(0, length), number: digits.slice(length) };
}

/** Composes a DDI + national-number pair into a single E.164 string. */
export function composeE164(ddi: string, number: string): string {
  return `+${onlyDigits(ddi)}${onlyDigits(number)}`;
}

@Component({
  selector: 'app-phone-ddi-input',
  imports: [InputMaskDirective],
  template: `
    <div class="flex min-w-0 flex-1 gap-1">
      <input
        [attr.data-testid]="'phone-ddi-input' + testIdSuffix()"
        type="text"
        [value]="'+' + ddi()"
        [disabled]="disabled()"
        (input)="onDdiChange($event)"
        class="w-14 rounded-lg border border-ink-300/70 bg-white px-2 py-1 text-sm text-ink-900 dark:border-ink-700 dark:bg-ink-800 dark:text-ink-100"
      />
      <input
        [attr.data-testid]="'phone-number-input' + testIdSuffix()"
        type="text"
        [value]="formatMaskedValue('phone', countryCode(), number())"
        [disabled]="disabled()"
        [appInputMask]="'phone'"
        [appInputMaskCountry]="countryCode()"
        (appInputMaskChange)="onNumberChange($event)"
        class="min-w-0 flex-1 rounded-lg border border-ink-300/70 bg-white px-2 py-1 text-sm text-ink-900 dark:border-ink-700 dark:bg-ink-800 dark:text-ink-100"
      />
    </div>
  `,
})
export class PhoneDdiInputComponent {
  readonly value = input<string>('');
  readonly countryCode = input<string | null>(null);
  readonly disabled = input(false);
  /** Distinguishes multiple rows' inner inputs in tests (e.g. `-${rowKey}`), default none. */
  readonly testIdSuffix = input<string>('');
  readonly valueChange = output<string>();

  protected readonly formatMaskedValue = formatMaskedValue;
  protected readonly ddi = signal('');
  protected readonly number = signal('');

  // Guards against fighting our own round-tripped emission: the parent (`ProfileFieldsFormComponent`)
  // stores whatever this component emits straight back into its `contacts` signal, which then
  // flows back into this component's `[value]` input on the very next change-detection pass. If
  // the effect below always re-split that value, a manually-typed DDI whose length differs from
  // `ddiLengthFor(countryCode())`'s guess (e.g. a 1-digit DDI in a country seeded for 2) would get
  // silently re-sliced back to the "wrong" split on every keystroke — the same class of bug the
  // pre-amendment `InputMaskDirective` fix (`formatMaskedValue`) already worked around for masked
  // fields. Only resync from `value()`/`countryCode()` when the incoming value didn't originate
  // from this component's own last emission.
  private lastEmitted: string | null = null;

  constructor() {
    // Re-syncs whenever the parent hands in a genuinely *external* new `value` (initial load,
    // contact `type` switched to PHONE/WHATSAPP, or an existing contact row swapped in) —
    // splitting round-trips correctly with `composeE164` since both sides agree on the same
    // country's DDI-length heuristic, as long as it isn't our own echo (see guard above).
    effect(() => {
      const incoming = this.value();
      const country = this.countryCode();

      if (incoming === this.lastEmitted) {
        return;
      }

      if (incoming) {
        const split = splitE164(incoming, country);
        this.ddi.set(split.ddi);
        this.number.set(split.number);
      } else {
        this.ddi.set(defaultDdiFor(country));
        this.number.set('');
      }
    });
  }

  protected onDdiChange(event: Event): void {
    this.ddi.set(onlyDigits((event.target as HTMLInputElement).value));
    this.emit();
  }

  protected onNumberChange(digits: string): void {
    this.number.set(digits);
    this.emit();
  }

  private emit(): void {
    const composed = composeE164(this.ddi(), this.number());
    this.lastEmitted = composed;
    this.valueChange.emit(composed);
  }
}
