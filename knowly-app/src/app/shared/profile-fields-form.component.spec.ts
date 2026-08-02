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
    taxId: '111.111.111-11',
    countryCode: 'BR',
    address: {
      addressLine1: 'Main St, 123',
      addressLine2: 'Centro',
      city: 'Sao Paulo',
      stateRegion: 'SP',
      postalCode: '01000-000',
      countryCode: 'BR',
    },
    contacts: [{ id: 1, type: 'PHONE', value: '+5511987654321', label: null, isPrimary: true }],
  };

  async function createFixture(
    disabled = false,
    showContacts = true,
    requireAllFields = false,
  ): Promise<void> {
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
    fixture.componentRef.setInput('requireAllFields', requireAllFields);
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

  it('renders the flat fields and the country-agnostic 6-field address block, never an email input', async () => {
    await createFixture();

    expect(input('profile-field-fullName').value).toBe('Jane Doe');
    expect(input('profile-field-countryCode').value).toBe('BR');
    expect(input('profile-field-taxId').value).toBe('111.111.111-11');
    expect(input('profile-address-field-addressLine1').value).toBe('Main St, 123');
    expect(input('profile-address-field-addressLine2').value).toBe('Centro');
    expect(input('profile-address-field-city').value).toBe('Sao Paulo');
    expect(input('profile-address-field-stateRegion').value).toBe('SP');
    expect(input('profile-address-field-postalCode').value).toBe('01000-000');
    // No old Brazil-only field names remain.
    expect(fixture.nativeElement.querySelector('[data-testid="profile-field-cpf"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('[data-testid="profile-field-rg"]')).toBeNull();
    expect(
      fixture.nativeElement.querySelector('[data-testid="profile-field-rgOrgaoEmissor"]'),
    ).toBeNull();
    expect(
      fixture.nativeElement.querySelector('[data-testid="profile-field-birthDate"]'),
    ).toBeNull();
    expect(
      fixture.nativeElement.querySelector('[data-testid="profile-address-field-cep"]'),
    ).toBeNull();
    expect(fixture.nativeElement.querySelector('[data-testid="profile-field-email"]')).toBeNull();
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
    // New rows default to PHONE (rendered via PhoneDdiInputComponent); switch this one to EMAIL
    // so an arbitrary email string is a realistic value and stays unmasked, per REQ-21.
    const select = fixture.nativeElement.querySelector(
      `[data-testid="profile-contact-type-${newRowKey}"]`,
    ) as HTMLSelectElement;
    select.value = 'EMAIL';
    select.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    input(`profile-contact-value-${newRowKey}`).value = 'jane@example.com';
    input(`profile-contact-value-${newRowKey}`).dispatchEvent(new Event('input'));
    fixture.detectChanges();

    // edit the existing PHONE contact via its DDI + national-number inputs — the diff carries
    // the composed E.164 value (REQ-6a).
    input('phone-ddi-input-id-1').value = '1';
    input('phone-ddi-input-id-1').dispatchEvent(new Event('input'));
    input('phone-number-input-id-1').value = '5551234';
    input('phone-number-input-id-1').dispatchEvent(new Event('input'));
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

  it('masks taxId as-you-type (BR) but submits the unmasked digits (REQ-21/22)', async () => {
    await createFixture();

    input('profile-field-taxId').value = '12345678900';
    input('profile-field-taxId').dispatchEvent(new Event('input'));
    fixture.detectChanges();

    expect(input('profile-field-taxId').value).toBe('123.456.789-00');

    const emitted: ProfileFieldsFormSubmission[] = [];
    fixture.componentInstance.submitted.subscribe((value) => emitted.push(value));
    submitForm();

    expect(emitted[0].fields.taxId).toBe('12345678900');
  });

  it('masks postalCode as-you-type (BR) but submits the unmasked digits (REQ-21/22)', async () => {
    await createFixture();

    input('profile-address-field-postalCode').value = '01310100';
    input('profile-address-field-postalCode').dispatchEvent(new Event('input'));
    fixture.detectChanges();

    expect(input('profile-address-field-postalCode').value).toBe('01310-100');

    const emitted: ProfileFieldsFormSubmission[] = [];
    fixture.componentInstance.submitted.subscribe((value) => emitted.push(value));
    submitForm();

    expect(emitted[0].fields.address?.postalCode).toBe('01310100');
  });

  it('does not block submission of a mask-incomplete taxId (REQ-23, no client-side format validation)', async () => {
    await createFixture();

    input('profile-field-taxId').value = '123';
    input('profile-field-taxId').dispatchEvent(new Event('input'));
    fixture.detectChanges();

    expect(input('profile-field-taxId').value).toBe('123');
    expect(input('profile-field-taxId').getAttribute('aria-invalid')).toBeNull();

    const emitted: ProfileFieldsFormSubmission[] = [];
    fixture.componentInstance.submitted.subscribe((value) => emitted.push(value));
    submitForm();

    expect(emitted.length).toBe(1);
    expect(emitted[0].fields.taxId).toBe('123');
  });

  describe('country-driven labels/masks', () => {
    it('selecting a different countryCode updates taxId/postalCode labels and mask behavior live, without reload', async () => {
      await createFixture();

      expect(fixture.nativeElement.textContent).toContain('CPF');
      expect(fixture.nativeElement.textContent).toContain('CEP');

      const select = input('profile-field-countryCode') as unknown as HTMLSelectElement;
      select.value = 'GB';
      select.dispatchEvent(new Event('change'));
      fixture.detectChanges();

      expect(fixture.nativeElement.textContent).toContain('NINO');
      expect(fixture.nativeElement.textContent).toContain('Postcode');

      // GB has no known postalCode mask — plain passthrough.
      input('profile-address-field-postalCode').value = 'EC1A 1BB';
      input('profile-address-field-postalCode').dispatchEvent(new Event('input'));
      fixture.detectChanges();

      expect(input('profile-address-field-postalCode').value).toBe('EC1A 1BB');
    });

    // Bugfix (2026-08-02): with no country selected (or a country outside the BR/US/GB
    // country-specific set), taxId/postalCode/stateRegion must fall back to the generic,
    // Transloco-driven label — never a bare, always-English literal.
    it('falls back to the generic Transloco-driven taxId/postalCode/stateRegion labels when no country is selected', async () => {
      await createFixture();

      const select = input('profile-field-countryCode') as unknown as HTMLSelectElement;
      select.value = '';
      select.dispatchEvent(new Event('change'));
      fixture.detectChanges();

      expect(fixture.nativeElement.textContent).toContain('Tax ID');
      expect(fixture.nativeElement.textContent).toContain('Postal Code');
      expect(fixture.nativeElement.textContent).not.toContain('CPF');
      expect(fixture.nativeElement.textContent).not.toContain('CEP');
    });

    // Bugfix (2026-08-02): addressLine1/city are identical across every country entry, so they
    // must always be Transloco-driven — never the always-English `CountryFieldConfig` literal
    // (this was the root cause of "Address line 1" never localizing in pt-BR).
    it('renders the addressLine1/city labels from Transloco, not a hardcoded English literal', async () => {
      await createFixture();

      expect(fixture.nativeElement.textContent).toContain('Address line 1');
      expect(fixture.nativeElement.textContent).toContain('City');
    });

    // Bugfix (2026-08-02): unlike taxId/postalCode (CPF/CEP), stateRegion is a generic field
    // name, not a country-specific document/form name — BR/US must not hardcode an English
    // "State" literal, they should fall back to the Transloco-driven generic label instead.
    it('renders the Transloco-driven generic stateRegion label for BR/US, not a hardcoded "State"', async () => {
      await createFixture();

      // BR is the fixture's initial country.
      expect(input('profile-address-field-stateRegion')).toBeTruthy();
      const brLabel = fixture.nativeElement.querySelector(
        '[data-testid="profile-address-field-stateRegion"]',
      )?.parentElement?.textContent;
      expect(brLabel).toContain('State / Region');

      const select = input('profile-field-countryCode') as unknown as HTMLSelectElement;
      select.value = 'US';
      select.dispatchEvent(new Event('change'));
      fixture.detectChanges();

      const usLabel = fixture.nativeElement.querySelector(
        '[data-testid="profile-address-field-stateRegion"]',
      )?.parentElement?.textContent;
      expect(usLabel).toContain('State / Region');

      // GB keeps its genuinely country-specific "County" label.
      select.value = 'GB';
      select.dispatchEvent(new Event('change'));
      fixture.detectChanges();

      const gbLabel = fixture.nativeElement.querySelector(
        '[data-testid="profile-address-field-stateRegion"]',
      )?.parentElement?.textContent;
      expect(gbLabel).toContain('County');
    });

    it('localizes the country <select> option text while keeping the alpha-2 code as the value', async () => {
      await createFixture();

      const select = input('profile-field-countryCode') as unknown as HTMLSelectElement;
      const options = Array.from(select.querySelectorAll('option'));
      const brOption = options.find((option) => option.value === 'BR') as HTMLOptionElement;

      expect(brOption.value).toBe('BR');
      expect(brOption.textContent?.trim()).toBe('Brazil');
    });
  });

  describe('contact type translation', () => {
    it('renders translated labels for the contact type <option>s, not the raw enum', async () => {
      await createFixture();

      const select = fixture.nativeElement.querySelector(
        '[data-testid="profile-contact-type-id-1"]',
      ) as HTMLSelectElement;
      const optionTexts = Array.from(select.querySelectorAll('option')).map((option) =>
        option.textContent?.trim(),
      );

      expect(optionTexts).toEqual(['Phone', 'WhatsApp', 'Email', 'Other']);
      expect(optionTexts).not.toContain('PHONE');
    });
  });

  describe('phone/WhatsApp contact rows', () => {
    it('shows PhoneDdiInputComponent for a PHONE/WHATSAPP row, and a plain input for EMAIL/OTHER', async () => {
      await createFixture();

      expect(input('phone-ddi-input-id-1')).toBeTruthy();
      expect(input('phone-number-input-id-1')).toBeTruthy();

      const select = fixture.nativeElement.querySelector(
        '[data-testid="profile-contact-type-id-1"]',
      ) as HTMLSelectElement;
      select.value = 'EMAIL';
      select.dispatchEvent(new Event('change'));
      fixture.detectChanges();

      expect(
        fixture.nativeElement.querySelector('[data-testid="phone-ddi-input-id-1"]'),
      ).toBeNull();
      expect(input('profile-contact-value-id-1')).toBeTruthy();
    });
  });

  describe('requireAllFields', () => {
    it('renders required on every mandatory input except addressLine2/stateRegion when true', async () => {
      await createFixture(false, true, true);

      expect(input('profile-field-fullName').required).toBe(true);
      expect(input('profile-field-taxId').required).toBe(true);
      expect(input('profile-address-field-addressLine1').required).toBe(true);
      expect(input('profile-address-field-city').required).toBe(true);
      expect(input('profile-address-field-postalCode').required).toBe(true);
      expect(input('profile-address-field-addressLine2').required).toBe(false);
      expect(input('profile-address-field-stateRegion').required).toBe(false);
    });

    it('blocks submission with zero contacts and shows contactsRequiredMessage when true', async () => {
      await createFixture(false, true, true);

      input('profile-contact-remove-id-1').click();
      fixture.detectChanges();

      const emitted: ProfileFieldsFormSubmission[] = [];
      fixture.componentInstance.submitted.subscribe((value) => emitted.push(value));
      submitForm();
      fixture.detectChanges();

      expect(emitted).toEqual([]);
      expect(
        fixture.nativeElement.querySelector('[data-testid="profile-contacts-required-message"]'),
      ).toBeTruthy();
    });

    it('does not render required attributes and allows zero contacts when false (default)', async () => {
      await createFixture(false, true, false);

      expect(input('profile-field-fullName').required).toBe(false);
      expect(input('profile-address-field-addressLine2').required).toBe(false);

      input('profile-contact-remove-id-1').click();
      fixture.detectChanges();

      const emitted: ProfileFieldsFormSubmission[] = [];
      fixture.componentInstance.submitted.subscribe((value) => emitted.push(value));
      submitForm();

      expect(emitted.length).toBe(1);
      expect(
        fixture.nativeElement.querySelector('[data-testid="profile-contacts-required-message"]'),
      ).toBeNull();
    });
  });
});
