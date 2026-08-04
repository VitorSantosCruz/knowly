import { Component, input } from '@angular/core';
import { FormArray, FormControl, FormGroup, Validators } from '@angular/forms';
import { TranslocoPipe } from '@jsverse/transloco';
import { buttonClass } from './button-classes';

const CONTACT_TYPES = ['EMAIL', 'PHONE', 'WHATSAPP', 'OTHER'] as const;

const INPUT_CLASS =
  'min-w-0 flex-1 rounded-lg border border-ink-200 bg-white px-3 py-2 text-sm text-ink-900 focus:border-signal-500 focus:ring-1 focus:ring-signal-500 focus:outline-none dark:border-ink-700 dark:bg-ink-800 dark:text-white';

/**
 * One `{ type, value, isPrimary }` row matching backend `ContactDto`'s minimal shape
 * (REQ-13). `isPrimary` is fixed `true` and not exposed in this form's UI — `ContactDto.
 * isPrimary` is a primitive `boolean` on the backend record, so omitting it entirely fails
 * JSON deserialization outright rather than defaulting to `false`; `ContactService.addContact`
 * demotes any earlier same-type primary automatically, so a later row of the same type simply
 * becomes the new primary instead of violating a one-primary-per-type invariant.
 */
export function createContactGroup(): FormGroup {
  return new FormGroup({
    type: new FormControl<(typeof CONTACT_TYPES)[number]>('EMAIL', { nonNullable: true }),
    value: new FormControl('', { nonNullable: true, validators: Validators.required }),
    isPrimary: new FormControl(true, { nonNullable: true }),
  });
}

/**
 * `[formArray]`-bound repeatable-row editor for the first user's contacts (REQ-13/REQ-14) —
 * presentational only, the parent owns the `FormArray` and its initial one-row state.
 */
@Component({
  selector: 'app-contacts-list-editor',
  imports: [TranslocoPipe],
  template: `
    <div class="flex flex-col gap-3">
      @for (row of formArray().controls; track $index) {
        <div [attr.data-testid]="'contacts-row-' + $index" class="flex items-center gap-2">
          <select
            [attr.data-testid]="'contacts-type-' + $index"
            [value]="row.get('type')?.value"
            (change)="onTypeChange($index, $event)"
            class="rounded-lg border border-ink-200 bg-white px-2 py-2 text-sm text-ink-900 dark:border-ink-700 dark:bg-ink-800 dark:text-white"
          >
            @for (type of contactTypes; track type) {
              <option [value]="type">{{ type }}</option>
            }
          </select>
          <input
            [attr.data-testid]="'contacts-value-' + $index"
            type="text"
            [value]="row.get('value')?.value"
            (input)="onValueChange($index, $event)"
            [class]="inputClass"
          />
          <button
            type="button"
            [attr.data-testid]="'contacts-remove-row-' + $index"
            [class]="removeButtonClass"
            (click)="onRemove($index)"
          >
            {{ 'tenantCreate.contacts.remove' | transloco }}
          </button>
        </div>
      }

      @if (showErrors() && formArray().length === 0) {
        <p data-testid="contacts-min-length-error" class="text-sm text-red-600 dark:text-red-400">
          {{ 'tenantCreate.contacts.minLength' | transloco }}
        </p>
      }

      <button
        type="button"
        data-testid="contacts-add-row"
        [class]="addButtonClass"
        (click)="onAdd()"
      >
        {{ 'tenantCreate.contacts.add' | transloco }}
      </button>
    </div>
  `,
})
export class ContactsListEditorComponent {
  readonly formArray = input.required<FormArray>();
  readonly showErrors = input(false);

  protected readonly contactTypes = CONTACT_TYPES;
  protected readonly inputClass = INPUT_CLASS;
  protected readonly addButtonClass = buttonClass('secondary', { ghost: true });
  protected readonly removeButtonClass = buttonClass('danger', { ghost: true });

  protected onTypeChange(index: number, event: Event): void {
    const value = (event.target as HTMLSelectElement).value;
    this.formArray().at(index).get('type')?.setValue(value);
  }

  protected onValueChange(index: number, event: Event): void {
    const value = (event.target as HTMLInputElement).value;
    this.formArray().at(index).get('value')?.setValue(value);
  }

  protected onAdd(): void {
    this.formArray().push(createContactGroup());
  }

  protected onRemove(index: number): void {
    this.formArray().removeAt(index);
  }
}
