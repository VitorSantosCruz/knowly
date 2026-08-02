import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { provideTransloco } from '@jsverse/transloco';
import { AddressFieldsComponent, AddressFieldSpec } from './address-fields.component';
import { FakeTranslocoLoader } from '../testing/fake-transloco-loader';

const ENGLISH_FIELDS: AddressFieldSpec[] = [
  { name: 'postalCode', labelKey: 'tenantCreate.address.postalCode' },
  { name: 'street', labelKey: 'tenantCreate.address.street' },
];

const PORTUGUESE_FIELDS: AddressFieldSpec[] = [
  { name: 'cep', labelKey: 'profile.fields.address.cep' },
  { name: 'logradouro', labelKey: 'profile.fields.address.logradouro' },
];

describe('AddressFieldsComponent', () => {
  let fixture: ComponentFixture<AddressFieldsComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AddressFieldsComponent],
      providers: [
        provideTransloco({
          config: { availableLangs: ['en', 'pt-BR'], defaultLang: 'en' },
          loader: FakeTranslocoLoader,
        }),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(AddressFieldsComponent);
  });

  function setInputs(formGroup: FormGroup, fields: AddressFieldSpec[]): void {
    fixture.componentRef.setInput('formGroup', formGroup);
    fixture.componentRef.setInput('fields', fields);
    fixture.detectChanges();
  }

  it('renders whatever field names the bound FormGroup has (English)', () => {
    const group = new FormGroup({
      postalCode: new FormControl(''),
      street: new FormControl(''),
    });
    setInputs(group, ENGLISH_FIELDS);

    expect(
      fixture.nativeElement.querySelector('[data-testid="address-field-postalCode"]'),
    ).toBeTruthy();
    expect(
      fixture.nativeElement.querySelector('[data-testid="address-field-street"]'),
    ).toBeTruthy();
  });

  it('renders whatever field names the bound FormGroup has (Portuguese)', () => {
    const group = new FormGroup({
      cep: new FormControl(''),
      logradouro: new FormControl(''),
    });
    setInputs(group, PORTUGUESE_FIELDS);

    expect(fixture.nativeElement.querySelector('[data-testid="address-field-cep"]')).toBeTruthy();
    expect(
      fixture.nativeElement.querySelector('[data-testid="address-field-logradouro"]'),
    ).toBeTruthy();
  });

  it('shows a field-level error for an invalid+touched control', () => {
    const group = new FormGroup({
      postalCode: new FormControl('', Validators.required),
      street: new FormControl(''),
    });
    setInputs(group, ENGLISH_FIELDS);

    expect(
      fixture.nativeElement.querySelector('[data-testid="address-field-error-postalCode"]'),
    ).toBeFalsy();

    fixture.nativeElement
      .querySelector('[data-testid="address-field-postalCode"]')
      .dispatchEvent(new Event('blur'));
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[data-testid="address-field-error-postalCode"]'),
    ).toBeTruthy();
  });

  it('reflects the bound FormGroup value in each input', () => {
    const group = new FormGroup({
      postalCode: new FormControl('12345-000'),
      street: new FormControl('Main St'),
    });
    setInputs(group, ENGLISH_FIELDS);

    expect(
      fixture.nativeElement.querySelector('[data-testid="address-field-postalCode"]').value,
    ).toBe('12345-000');
  });

  it('typing into one instance never mutates a different instance bound to a different FormGroup', () => {
    const groupA = new FormGroup({ postalCode: new FormControl(''), street: new FormControl('') });
    const groupB = new FormGroup({ postalCode: new FormControl(''), street: new FormControl('') });

    setInputs(groupA, ENGLISH_FIELDS);

    const input = fixture.nativeElement.querySelector('[data-testid="address-field-postalCode"]');
    input.value = '99999-999';
    input.dispatchEvent(new Event('input'));

    expect(groupA.get('postalCode')?.value).toBe('99999-999');
    expect(groupB.get('postalCode')?.value).toBe('');
  });
});
