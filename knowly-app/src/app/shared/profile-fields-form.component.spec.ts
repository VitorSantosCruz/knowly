import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTransloco } from '@jsverse/transloco';
import {
  ProfileFieldsFormComponent,
  ProfileFieldsFormSubmission,
} from './profile-fields-form.component';
import { FakeTranslocoLoader } from '../testing/fake-transloco-loader';
import { ProfileFields } from '../core/profile.service';

describe('ProfileFieldsFormComponent', () => {
  let fixture: ComponentFixture<ProfileFieldsFormComponent>;

  const fields: ProfileFields = {
    fullName: 'Jane Doe',
    rg: '11.111.111-1',
    cpf: '111.111.111-11',
    rgOrgaoEmissor: 'SSP',
    birthDate: '1990-01-01',
    address: {
      cep: '01000-000',
      logradouro: 'Main St',
      numero: '123',
      complemento: null,
      bairro: 'Centro',
      cidade: 'Sao Paulo',
      estado: 'SP',
      pais: 'BR',
    },
    contacts: [{ id: 1, type: 'PHONE', value: '+15550000', label: null, isPrimary: true }],
  };

  async function createFixture(disabled = false, showContacts = true): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [ProfileFieldsFormComponent],
      providers: [
        provideTransloco({
          config: { availableLangs: ['en', 'pt-BR'], defaultLang: 'en' },
          loader: FakeTranslocoLoader,
        }),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ProfileFieldsFormComponent);
    fixture.componentRef.setInput('fields', fields);
    fixture.componentRef.setInput('disabled', disabled);
    fixture.componentRef.setInput('showContacts', showContacts);
    fixture.detectChanges();
  }

  function input(testId: string): HTMLInputElement {
    return fixture.nativeElement.querySelector(`[data-testid="${testId}"]`);
  }

  function submitForm(): void {
    fixture.nativeElement
      .querySelector('[data-testid="profile-fields-form"]')
      .dispatchEvent(new Event('submit', { cancelable: true }));
  }

  it('renders the flat fields, the structured address fieldset, and never an email input', async () => {
    await createFixture();

    expect(input('profile-field-fullName').value).toBe('Jane Doe');
    expect(input('profile-field-rg').value).toBe('11.111.111-1');
    expect(input('profile-field-cpf').value).toBe('111.111.111-11');
    expect(input('profile-field-rgOrgaoEmissor').value).toBe('SSP');
    expect(input('profile-field-birthDate').value).toBe('1990-01-01');
    expect(input('profile-address-field-cep').value).toBe('01000-000');
    expect(input('profile-address-field-logradouro').value).toBe('Main St');
    expect(input('profile-address-field-numero').value).toBe('123');
    expect(input('profile-address-field-bairro').value).toBe('Centro');
    expect(input('profile-address-field-cidade').value).toBe('Sao Paulo');
    expect(input('profile-address-field-estado').value).toBe('SP');
    expect(input('profile-address-field-pais').value).toBe('BR');
    expect(fixture.nativeElement.querySelector('[data-testid="profile-field-email"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('[data-testid="profile-field-address"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('[data-testid="profile-field-phone"]')).toBeNull();
  });

  it('emits submitted with the entered flat/address values, never including email', async () => {
    await createFixture();

    const emitted: ProfileFieldsFormSubmission[] = [];
    fixture.componentInstance.submitted.subscribe((value) => emitted.push(value));

    input('profile-field-fullName').value = 'Jane Smith';
    input('profile-field-fullName').dispatchEvent(new Event('input'));
    fixture.detectChanges();

    submitForm();

    expect(emitted[0].fields.fullName).toBe('Jane Smith');
    expect(emitted[0]).not.toHaveProperty('email');
  });

  it('prevents submission/emits nothing when disabled', async () => {
    await createFixture(true);

    const emitted: unknown[] = [];
    fixture.componentInstance.submitted.subscribe((value) => emitted.push(value));

    submitForm();

    expect(emitted).toEqual([]);
    expect(input('profile-field-fullName').disabled).toBe(true);
  });

  it('renders existing contacts, supports adding up to 5, and blocks a 6th client-side', async () => {
    await createFixture();

    expect(
      fixture.nativeElement.querySelector('[data-testid="profile-contact-row-id-1"]'),
    ).toBeTruthy();

    const addButton = () => input('profile-contact-add');

    for (let i = 0; i < 4; i++) {
      addButton().click();
      fixture.detectChanges();
    }

    expect(
      fixture.nativeElement.querySelectorAll('[data-testid^="profile-contact-row-"]').length,
    ).toBe(5);
    expect(addButton().disabled).toBe(true);
    expect(
      fixture.nativeElement.querySelector('[data-testid="profile-contacts-limit-message"]'),
    ).toBeNull();
  });

  it('setting a contact primary clears other primaries of the same type', async () => {
    await createFixture();

    input('profile-contact-add').click();
    fixture.detectChanges();

    const rows = fixture.nativeElement.querySelectorAll('[data-testid^="profile-contact-row-"]');
    const newRowKey = rows[1].getAttribute('data-testid').replace('profile-contact-row-', '');

    // second row defaults to PHONE too — make it primary, first PHONE row should un-primary
    input(`profile-contact-primary-${newRowKey}`).click();
    fixture.detectChanges();

    expect(input('profile-contact-primary-id-1').checked).toBe(false);
    expect(input(`profile-contact-primary-${newRowKey}`).checked).toBe(true);
  });

  it('submitting a mix of unchanged/edited/added/removed contacts emits the correctly diffed contactChanges', async () => {
    await createFixture();

    // add a new contact
    input('profile-contact-add').click();
    fixture.detectChanges();
    const rows = fixture.nativeElement.querySelectorAll('[data-testid^="profile-contact-row-"]');
    const newRowKey = rows[1].getAttribute('data-testid').replace('profile-contact-row-', '');
    input(`profile-contact-value-${newRowKey}`).value = 'jane@example.com';
    input(`profile-contact-value-${newRowKey}`).dispatchEvent(new Event('input'));
    fixture.detectChanges();

    // edit the existing contact
    input('profile-contact-value-id-1').value = '+15551234';
    input('profile-contact-value-id-1').dispatchEvent(new Event('input'));
    fixture.detectChanges();

    const emitted: ProfileFieldsFormSubmission[] = [];
    fixture.componentInstance.submitted.subscribe((value) => emitted.push(value));
    submitForm();

    const changes = emitted[0].contactChanges;
    expect(changes).toEqual(
      expect.arrayContaining([
        expect.objectContaining({ action: 'UPDATE', contactId: 1, value: '+15551234' }),
        expect.objectContaining({ action: 'ADD', contactId: null, value: 'jane@example.com' }),
      ]),
    );
    expect(changes.length).toBe(2);
  });

  it('a REMOVE-only diff (deleting an original contact) emits a REMOVE change', async () => {
    await createFixture();

    input('profile-contact-remove-id-1').click();
    fixture.detectChanges();

    const emitted: ProfileFieldsFormSubmission[] = [];
    fixture.componentInstance.submitted.subscribe((value) => emitted.push(value));
    submitForm();

    expect(emitted[0].contactChanges).toEqual([
      { action: 'REMOVE', contactId: 1, type: null, value: null, label: null, isPrimary: null },
    ]);
  });

  it('[showContacts]=false hides the contacts fieldset entirely', async () => {
    await createFixture(false, false);

    expect(
      fixture.nativeElement.querySelector('[data-testid="profile-contacts-fieldset"]'),
    ).toBeNull();
  });
});
