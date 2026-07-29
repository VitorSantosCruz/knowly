import { Component, effect, input, output, signal } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import {
  Address,
  Contact,
  ContactChange,
  ContactType,
  ProfileFields,
} from '../core/profile.service';

const CONTACT_TYPES: ContactType[] = ['PHONE', 'WHATSAPP', 'EMAIL', 'OTHER'];
const MAX_CONTACTS = 5;

const EMPTY_ADDRESS: Address = {
  cep: '',
  logradouro: '',
  numero: '',
  complemento: '',
  bairro: '',
  cidade: '',
  estado: '',
  pais: '',
};

const EMPTY_FIELDS: ProfileFields = {
  fullName: '',
  cpf: '',
  rg: '',
  rgOrgaoEmissor: '',
  birthDate: '',
  address: EMPTY_ADDRESS,
  contacts: [],
};

// Client-side-only row identity for `@for` tracking — separate from `id` (`null` until the
// backend assigns one on approval), so newly-added rows in the same session stay stable.
interface ContactRow extends Contact {
  rowKey: string;
}

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
        {{ 'profile.fields.rgOrgaoEmissor' | transloco }}
        <input
          data-testid="profile-field-rgOrgaoEmissor"
          type="text"
          [value]="localFields().rgOrgaoEmissor"
          [disabled]="disabled()"
          (input)="onFieldChange('rgOrgaoEmissor', $any($event.target).value)"
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
        {{ 'profile.fields.birthDate' | transloco }}
        <input
          data-testid="profile-field-birthDate"
          type="date"
          [value]="localFields().birthDate"
          [disabled]="disabled()"
          (input)="onFieldChange('birthDate', $any($event.target).value)"
          class="rounded-xl border border-ink-300/70 bg-white px-3 py-1.5 text-sm text-ink-900 shadow-sm focus:border-signal-400 focus:ring-2 focus:ring-signal-400/30 focus:outline-none dark:border-ink-700 dark:bg-ink-800 dark:text-ink-100"
        />
      </label>

      <fieldset data-testid="profile-address-fieldset" class="flex flex-col gap-2">
        <legend class="text-sm text-ink-700 dark:text-ink-300">
          {{ 'profile.fields.address.title' | transloco }}
        </legend>
        @for (field of addressFieldNames; track field) {
          <label class="flex flex-col gap-1 text-sm text-ink-700 dark:text-ink-300">
            {{ 'profile.fields.address.' + field | transloco }}
            <input
              [attr.data-testid]="'profile-address-field-' + field"
              type="text"
              [value]="localFields().address?.[field] ?? ''"
              [disabled]="disabled()"
              (input)="onAddressFieldChange(field, $any($event.target).value)"
              class="rounded-xl border border-ink-300/70 bg-white px-3 py-1.5 text-sm text-ink-900 shadow-sm focus:border-signal-400 focus:ring-2 focus:ring-signal-400/30 focus:outline-none dark:border-ink-700 dark:bg-ink-800 dark:text-ink-100"
            />
          </label>
        }
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
                [value]="row.type"
                [disabled]="disabled()"
                (change)="onContactFieldChange(row.rowKey, 'type', $any($event.target).value)"
                class="rounded-lg border border-ink-300/70 bg-white px-2 py-1 text-sm text-ink-900 dark:border-ink-700 dark:bg-ink-800 dark:text-ink-100"
              >
                @for (type of contactTypes; track type) {
                  <option [value]="type">{{ type }}</option>
                }
              </select>
              <input
                [attr.data-testid]="'profile-contact-value-' + row.rowKey"
                type="text"
                [value]="row.value"
                [disabled]="disabled()"
                (input)="onContactFieldChange(row.rowKey, 'value', $any($event.target).value)"
                [placeholder]="'profile.fields.contacts.value' | transloco"
                class="min-w-0 flex-1 rounded-lg border border-ink-300/70 bg-white px-2 py-1 text-sm text-ink-900 dark:border-ink-700 dark:bg-ink-800 dark:text-ink-100"
              />
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
  readonly submitted = output<ProfileFieldsFormSubmission>();

  protected readonly contactTypes = CONTACT_TYPES;
  protected readonly maxContacts = MAX_CONTACTS;
  protected readonly addressFieldNames: (keyof Address)[] = [
    'cep',
    'logradouro',
    'numero',
    'complemento',
    'bairro',
    'cidade',
    'estado',
    'pais',
  ];

  protected readonly localFields = signal<ProfileFields>(EMPTY_FIELDS);
  protected readonly contacts = signal<ContactRow[]>([]);
  protected readonly contactLimitMessage = signal(false);

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
      this.localFields.set({
        ...incoming,
        address: incoming.address ?? { ...EMPTY_ADDRESS },
      });

      const rows = incoming.contacts.map(toRow);
      this.contacts.set(rows);
      this.initialContactsById = new Map(
        incoming.contacts.filter((contact) => contact.id !== null).map((c) => [c.id as number, c]),
      );
      this.contactLimitMessage.set(false);
    });
  }

  protected onFieldChange(
    field: Exclude<keyof ProfileFields, 'address' | 'contacts'>,
    value: string,
  ): void {
    this.localFields.update((current) => ({ ...current, [field]: value }));
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

    for (const [id] of this.initialContactsById) {
      if (!currentIds.has(id)) {
        changes.push({
          action: 'REMOVE',
          contactId: id,
          type: null,
          value: null,
          label: null,
          isPrimary: null,
        });
      }
    }

    return changes;
  }
}
