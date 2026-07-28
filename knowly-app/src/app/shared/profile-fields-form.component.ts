import { Component, OnInit, input, output, signal } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { ProfileFields } from '../core/profile.service';

@Component({
  selector: 'app-profile-fields-form',
  imports: [TranslocoPipe],
  template: `
    <form data-testid="profile-fields-form" (submit)="onSubmit($event)" class="flex flex-col gap-3">
      <label class="flex flex-col gap-1 text-sm text-ink-700 dark:text-ink-300">
        {{ 'profile.fields.fullName' | transloco }}
        <input
          data-testid="profile-field-fullName"
          type="text"
          [value]="localFields().fullName"
          [disabled]="disabled()"
          (input)="onFieldChange('fullName', $any($event.target).value)"
          class="rounded-xl border border-ink-300/70 bg-white px-3 py-1.5 text-sm text-ink-900 shadow-sm focus:border-signal-400 focus:ring-2 focus:ring-signal-400/30 focus:outline-none dark:border-ink-700 dark:bg-ink-800 dark:text-ink-100"
        />
      </label>
      <label class="flex flex-col gap-1 text-sm text-ink-700 dark:text-ink-300">
        {{ 'profile.fields.address' | transloco }}
        <input
          data-testid="profile-field-address"
          type="text"
          [value]="localFields().address"
          [disabled]="disabled()"
          (input)="onFieldChange('address', $any($event.target).value)"
          class="rounded-xl border border-ink-300/70 bg-white px-3 py-1.5 text-sm text-ink-900 shadow-sm focus:border-signal-400 focus:ring-2 focus:ring-signal-400/30 focus:outline-none dark:border-ink-700 dark:bg-ink-800 dark:text-ink-100"
        />
      </label>
      <label class="flex flex-col gap-1 text-sm text-ink-700 dark:text-ink-300">
        {{ 'profile.fields.rg' | transloco }}
        <input
          data-testid="profile-field-rg"
          type="text"
          [value]="localFields().rg"
          [disabled]="disabled()"
          (input)="onFieldChange('rg', $any($event.target).value)"
          class="rounded-xl border border-ink-300/70 bg-white px-3 py-1.5 text-sm text-ink-900 shadow-sm focus:border-signal-400 focus:ring-2 focus:ring-signal-400/30 focus:outline-none dark:border-ink-700 dark:bg-ink-800 dark:text-ink-100"
        />
      </label>
      <label class="flex flex-col gap-1 text-sm text-ink-700 dark:text-ink-300">
        {{ 'profile.fields.cpf' | transloco }}
        <input
          data-testid="profile-field-cpf"
          type="text"
          [value]="localFields().cpf"
          [disabled]="disabled()"
          (input)="onFieldChange('cpf', $any($event.target).value)"
          class="rounded-xl border border-ink-300/70 bg-white px-3 py-1.5 text-sm text-ink-900 shadow-sm focus:border-signal-400 focus:ring-2 focus:ring-signal-400/30 focus:outline-none dark:border-ink-700 dark:bg-ink-800 dark:text-ink-100"
        />
      </label>
      <label class="flex flex-col gap-1 text-sm text-ink-700 dark:text-ink-300">
        {{ 'profile.fields.phone' | transloco }}
        <input
          data-testid="profile-field-phone"
          type="text"
          [value]="localFields().phone"
          [disabled]="disabled()"
          (input)="onFieldChange('phone', $any($event.target).value)"
          class="rounded-xl border border-ink-300/70 bg-white px-3 py-1.5 text-sm text-ink-900 shadow-sm focus:border-signal-400 focus:ring-2 focus:ring-signal-400/30 focus:outline-none dark:border-ink-700 dark:bg-ink-800 dark:text-ink-100"
        />
      </label>
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
export class ProfileFieldsFormComponent implements OnInit {
  readonly fields = input.required<ProfileFields>();
  readonly disabled = input(false);
  readonly submitted = output<ProfileFields>();

  protected readonly localFields = signal<ProfileFields>({
    fullName: '',
    address: '',
    rg: '',
    cpf: '',
    phone: '',
  });

  ngOnInit(): void {
    this.localFields.set(this.fields());
  }

  protected onFieldChange(field: keyof ProfileFields, value: string): void {
    this.localFields.update((current) => ({ ...current, [field]: value }));
  }

  protected onSubmit(event: Event): void {
    event.preventDefault();

    if (this.disabled()) {
      return;
    }

    this.submitted.emit(this.localFields());
  }
}
