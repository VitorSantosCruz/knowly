import { Component, input } from '@angular/core';
import { FormGroup } from '@angular/forms';
import { TranslocoPipe } from '@jsverse/transloco';

const INPUT_CLASS =
  'w-full rounded-lg border border-ink-200 bg-white px-3 py-2 text-sm text-ink-900 focus:border-signal-500 focus:ring-1 focus:ring-signal-500 focus:outline-none dark:border-ink-700 dark:bg-ink-800 dark:text-white';

/** One rendered row: which control to look up on the bound `FormGroup`, and its label key. */
export interface AddressFieldSpec {
  name: string;
  labelKey: string;
}

/**
 * Presentational-only, field-name-agnostic address block (PLAN.md's "Architectural decisions").
 * Two real instances on `TenantCreatePageComponent` bind to differently-named `FormGroup`s
 * (English `AddressDto` for the company, Portuguese `MandatoryAddressDto` for the first user) —
 * this component never hardcodes a control name, it only renders whatever `fields` it's told to.
 */
@Component({
  selector: 'app-address-fields',
  imports: [TranslocoPipe],
  template: `
    <div class="flex flex-col gap-4">
      @for (field of fields(); track field.name) {
        <label class="flex flex-col gap-1.5">
          <span class="text-sm font-medium text-ink-700 dark:text-ink-300">{{
            field.labelKey | transloco
          }}</span>
          <input
            [attr.data-testid]="'address-field-' + field.name"
            type="text"
            [value]="controlValue(field.name)"
            (input)="onInput(field.name, $event)"
            (blur)="onBlur(field.name)"
            [class]="inputClass"
          />
          @if (showError(field.name)) {
            <p
              [attr.data-testid]="'address-field-error-' + field.name"
              class="text-sm text-red-600 dark:text-red-400"
            >
              {{ 'shared.fieldRequired' | transloco }}
            </p>
          }
        </label>
      }
    </div>
  `,
})
export class AddressFieldsComponent {
  readonly formGroup = input.required<FormGroup>();
  readonly fields = input.required<AddressFieldSpec[]>();

  protected readonly inputClass = INPUT_CLASS;

  protected controlValue(name: string): string {
    return this.formGroup().get(name)?.value ?? '';
  }

  protected onInput(name: string, event: Event): void {
    const value = (event.target as HTMLInputElement).value;
    this.formGroup().get(name)?.setValue(value);
  }

  protected onBlur(name: string): void {
    this.formGroup().get(name)?.markAsTouched();
  }

  protected showError(name: string): boolean {
    const control = this.formGroup().get(name);
    return !!control && control.invalid && control.touched;
  }
}
