import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import { provideTransloco } from '@jsverse/transloco';
import { TenantCreatePageComponent } from './tenant-create-page.component';
import { FakeTranslocoLoader } from '../../testing/fake-transloco-loader';

describe('TenantCreatePageComponent', () => {
  let fixture: ComponentFixture<TenantCreatePageComponent>;
  let httpMock: HttpTestingController;
  let router: Router;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TenantCreatePageComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        provideTransloco({
          config: { availableLangs: ['en', 'pt-BR'], defaultLang: 'en' },
          loader: FakeTranslocoLoader,
        }),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(TenantCreatePageComponent);
    httpMock = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
  });

  afterEach(() => {
    httpMock.verify();
  });

  function setValue(testId: string, value: string): void {
    const input = fixture.nativeElement.querySelector(`[data-testid="${testId}"]`);
    input.value = value;
    input.dispatchEvent(new Event('input'));
  }

  function submit(): void {
    fixture.nativeElement
      .querySelector('[data-testid="tenant-create-form"]')
      .dispatchEvent(new Event('submit', { cancelable: true }));
  }

  function fillCompanySection(overrides: Partial<Record<string, string>> = {}): void {
    const values: Record<string, string> = {
      name: 'Acme',
      legalName: 'Acme Ltda',
      taxId: '12345678000199',
      country: 'Brazil',
      contactEmail: 'contact@acme.test',
      contactPhone: '+55 11 90000-0000',
      ...overrides,
    };
    for (const [key, value] of Object.entries(values)) {
      setValue(`tenant-create-${key}`, value);
    }
    setValue('address-field-postalCode', '01310-000');
    setValue('address-field-street', 'Av. Paulista');
    setValue('address-field-number', '1000');
    setValue('address-field-neighborhood', 'Bela Vista');
    setValue('address-field-city', 'São Paulo');
    setValue('address-field-state', 'SP');
  }

  function fillUserSection(): void {
    setValue('tenant-create-adminEmail', 'admin@acme.test');
    setValue('tenant-create-userProfile-fullName', 'Jane Admin');
    setValue('tenant-create-userProfile-birthDate', '1990-01-01');
    setValue('tenant-create-userProfile-cpf', '12345678900');
    setValue('tenant-create-userProfile-rg', '123456789');
    setValue('tenant-create-userProfile-rgOrgaoEmissor', 'SSP');
    setValue('address-field-cep', '01310-000');
    setValue('address-field-logradouro', 'Av. Paulista');
    setValue('address-field-numero', '1000');
    setValue('address-field-bairro', 'Bela Vista');
    setValue('address-field-cidade', 'São Paulo');
    setValue('address-field-estado', 'SP');
    setValue('address-field-pais', 'Brazil');
    setValue('contacts-value-0', 'admin@acme.test');
  }

  function fillValidForm(): void {
    fillCompanySection();
    fillUserSection();
  }

  it('renders the form with its three sections', () => {
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="tenant-create-form"]')).toBeTruthy();
    expect(
      fixture.nativeElement.querySelector('[data-testid="tenant-create-company-heading"]'),
    ).toBeTruthy();
    expect(
      fixture.nativeElement.querySelector('[data-testid="tenant-create-user-heading"]'),
    ).toBeTruthy();
    expect(
      fixture.nativeElement.querySelector('[data-testid="tenant-create-role-heading"]'),
    ).toBeTruthy();
  });

  it('does not call the API when all fields are empty', () => {
    fixture.detectChanges();

    submit();
    fixture.detectChanges();

    httpMock.expectNone('/api/tenants');
    expect(fixture.nativeElement.querySelector('[data-testid="tenant-create-error"]')).toBeTruthy();
  });

  it('blocks submit with a required company field missing, showing a field-level error, no API call (REQ-8/REQ-9)', () => {
    fixture.detectChanges();
    fillValidForm();
    setValue('tenant-create-legalName', '');

    submit();
    fixture.detectChanges();

    httpMock.expectNone('/api/tenants');
    expect(
      fixture.nativeElement.querySelector('[data-testid="tenant-create-error-legalName"]'),
    ).toBeTruthy();
  });

  it('blocks submit with a malformed contactEmail (REQ-9)', () => {
    fixture.detectChanges();
    fillValidForm();
    setValue('tenant-create-contactEmail', 'not-an-email');

    submit();
    fixture.detectChanges();

    httpMock.expectNone('/api/tenants');
    expect(
      fixture.nativeElement.querySelector('[data-testid="tenant-create-error-contactEmail"]'),
    ).toBeTruthy();
  });

  it('blocks submit with a required first-user field missing (REQ-13/REQ-14)', () => {
    fixture.detectChanges();
    fillValidForm();
    setValue('tenant-create-userProfile-fullName', '');

    submit();
    fixture.detectChanges();

    httpMock.expectNone('/api/tenants');
    expect(
      fixture.nativeElement.querySelector(
        '[data-testid="tenant-create-error-userProfile-fullName"]',
      ),
    ).toBeTruthy();
  });

  it('blocks submit with zero contacts (REQ-14)', () => {
    fixture.detectChanges();
    fillValidForm();
    fixture.nativeElement
      .querySelector('[data-testid="contacts-remove-row-0"]')
      .dispatchEvent(new Event('click'));
    fixture.detectChanges();

    submit();
    fixture.detectChanges();

    httpMock.expectNone('/api/tenants');
    expect(
      fixture.nativeElement.querySelector('[data-testid="contacts-min-length-error"]'),
    ).toBeTruthy();
  });

  it('does not block submit for a non-Brazil country with a non-empty taxId of any shape (REQ-10)', () => {
    fixture.detectChanges();
    fillValidForm();
    setValue('tenant-create-country', 'United States');
    setValue('tenant-create-taxId', 'abc-123');

    submit();

    httpMock.expectOne('/api/tenants').flush({});
  });

  it('blocks submit for Brazil with a taxId not matching the 14-digit CNPJ shape, with or without punctuation (REQ-10)', () => {
    fixture.detectChanges();
    fillValidForm();
    setValue('tenant-create-taxId', '12.345.678/0001-9');

    submit();
    fixture.detectChanges();

    httpMock.expectNone('/api/tenants');
    expect(
      fixture.nativeElement.querySelector('[data-testid="tenant-create-error-taxId"]'),
    ).toBeTruthy();
  });

  it('allows a punctuated 14-digit CNPJ for Brazil (REQ-10)', () => {
    fixture.detectChanges();
    fillValidForm();
    setValue('tenant-create-taxId', '12.345.678/0001-99');

    submit();

    httpMock.expectOne('/api/tenants').flush({});
  });

  it('defaults the role selector to MEMBER_ADMIN (REQ-18)', () => {
    fixture.detectChanges();

    const select = fixture.nativeElement.querySelector('[data-testid="tenant-create-role"]');
    expect(select.value).toBe('MEMBER_ADMIN');
  });

  it('sends the chosen role on submit (REQ-17-19)', () => {
    fixture.detectChanges();
    fillValidForm();

    const select = fixture.nativeElement.querySelector('[data-testid="tenant-create-role"]');
    select.value = 'MEMBER';
    select.dispatchEvent(new Event('change'));

    submit();

    const req = httpMock.expectOne('/api/tenants');
    expect(req.request.body.role).toBe('MEMBER');
    req.flush({});
  });

  it('creates the tenant with the exact CreateTenantRequest shape and navigates to /select-tenant on success (REQ-4)', () => {
    const navigateSpy = vi.spyOn(router, 'navigateByUrl');
    fixture.detectChanges();
    fillValidForm();

    submit();

    const req = httpMock.expectOne('/api/tenants');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      name: 'Acme',
      legalName: 'Acme Ltda',
      taxId: '12345678000199',
      country: 'Brazil',
      contactEmail: 'contact@acme.test',
      contactPhone: '+55 11 90000-0000',
      address: {
        postalCode: '01310-000',
        street: 'Av. Paulista',
        number: '1000',
        complement: null,
        neighborhood: 'Bela Vista',
        city: 'São Paulo',
        state: 'SP',
      },
      adminEmail: 'admin@acme.test',
      profile: {
        fullName: 'Jane Admin',
        birthDate: '1990-01-01',
        cpf: '12345678900',
        rg: '123456789',
        rgOrgaoEmissor: 'SSP',
        address: {
          cep: '01310-000',
          logradouro: 'Av. Paulista',
          numero: '1000',
          complemento: null,
          bairro: 'Bela Vista',
          cidade: 'São Paulo',
          estado: 'SP',
          pais: 'Brazil',
        },
        contacts: [{ type: 'EMAIL', value: 'admin@acme.test' }],
      },
      role: 'MEMBER_ADMIN',
    });
    req.flush({});

    expect(navigateSpy).toHaveBeenCalledWith('/select-tenant');
  });

  it('sets a field-level error on taxId and preserves other values for a 409 taxId conflict (REQ-11)', () => {
    fixture.detectChanges();
    fillValidForm();

    submit();

    const req = httpMock.expectOne('/api/tenants');
    req.flush({ code: 'TENANT_ALREADY_EXISTS' }, { status: 409, statusText: 'Conflict' });
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[data-testid="tenant-create-error-taxId"]'),
    ).toBeTruthy();
    expect(fixture.nativeElement.querySelector('[data-testid="tenant-create-name"]').value).toBe(
      'Acme',
    );
  });

  it('maps a 400 field-identifying response onto the matching first-user field (REQ-15)', () => {
    fixture.detectChanges();
    fillValidForm();

    submit();

    const req = httpMock.expectOne('/api/tenants');
    req.flush(
      { errors: [{ field: 'profile.fullName', message: 'must not be blank' }] },
      { status: 400, statusText: 'Bad Request' },
    );
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector(
        '[data-testid="tenant-create-error-userProfile-fullName"]',
      ),
    ).toBeTruthy();
    expect(fixture.nativeElement.querySelector('[data-testid="tenant-create-error"]')).toBeFalsy();
  });

  it('shows the generic banner and preserves entered values for a 400 that identifies no field (REQ-15 fallback, REQ-5)', () => {
    fixture.detectChanges();
    fillValidForm();

    submit();

    const req = httpMock.expectOne('/api/tenants');
    req.flush({ code: 'SOME_OTHER_ERROR' }, { status: 400, statusText: 'Bad Request' });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="tenant-create-error"]')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('[data-testid="tenant-create-name"]').value).toBe(
      'Acme',
    );
  });

  it('shows the generic banner and preserves entered values for any other error (REQ-5)', () => {
    fixture.detectChanges();
    fillValidForm();

    submit();

    const req = httpMock.expectOne('/api/tenants');
    req.flush({ code: 'PERMISSION_DENIED' }, { status: 403, statusText: 'Forbidden' });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="tenant-create-error"]')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('[data-testid="tenant-create-name"]').value).toBe(
      'Acme',
    );
  });

  it('accepts independent values in the company and first-user address sections (REQ-16)', () => {
    fixture.detectChanges();

    setValue('address-field-postalCode', 'company-postal');
    setValue('address-field-cep', 'user-cep');

    expect(
      fixture.nativeElement.querySelector('[data-testid="address-field-postalCode"]').value,
    ).toBe('company-postal');
    expect(fixture.nativeElement.querySelector('[data-testid="address-field-cep"]').value).toBe(
      'user-cep',
    );
  });
});
