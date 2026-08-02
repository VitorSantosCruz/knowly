import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { provideTransloco } from '@jsverse/transloco';
import { CompleteProfilePageComponent } from './complete-profile-page.component';
import { FakeTranslocoLoader } from '../../testing/fake-transloco-loader';

describe('CompleteProfilePageComponent', () => {
  let fixture: ComponentFixture<CompleteProfilePageComponent>;
  let httpMock: HttpTestingController;

  const fields = {
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

  const profile = { userId: 1, email: 'jane@example.com', fields, avatarUrl: null };

  async function createFixture(): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [CompleteProfilePageComponent],
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

    fixture = TestBed.createComponent(CompleteProfilePageComponent);
    httpMock = TestBed.inject(HttpTestingController);
  }

  function flushOwnProfile(): void {
    httpMock.expectOne('/api/users/me/profile').flush(profile);
  }

  function submit(): void {
    fixture.nativeElement.querySelector('[data-testid="profile-fields-submit"]').click();
  }

  afterEach(() => {
    httpMock.verify();
  });

  it('renders the full mandatory field set, no avatar control, no nav, email read-only', async () => {
    await createFixture();
    fixture.detectChanges();
    flushOwnProfile();
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[data-testid="profile-field-fullName"]'),
    ).toBeTruthy();
    expect(
      fixture.nativeElement.querySelector('[data-testid="profile-address-fieldset"]'),
    ).toBeTruthy();
    expect(
      fixture.nativeElement.querySelector('[data-testid="profile-contacts-fieldset"]'),
    ).toBeTruthy();
    expect(fixture.nativeElement.querySelector('[data-testid="avatar-upload-input"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('nav')).toBeNull();
    expect(fixture.nativeElement.querySelector('a')).toBeNull();

    const emailEl = fixture.nativeElement.querySelector('[data-testid="complete-profile-email"]');
    expect(emailEl).toBeTruthy();
    expect(emailEl.textContent).toContain('jane@example.com');
    expect(fixture.nativeElement.querySelector('input[type="email"]')).toBeNull();
  });

  it('submits the mapped MandatoryProfileFields (contacts stripped of id) and navigates to /welcome on success', async () => {
    await createFixture();
    fixture.detectChanges();
    flushOwnProfile();
    fixture.detectChanges();

    const router = TestBed.inject(Router);
    vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);

    submit();

    const req = httpMock.expectOne('/api/users/me/profile/complete');
    expect(req.request.method).toBe('POST');
    expect(req.request.body.contacts[0]).not.toHaveProperty('id');
    expect(req.request.body.fullName).toBe('Jane Doe');
    req.flush(profile);
    fixture.detectChanges();

    expect(router.navigateByUrl).toHaveBeenCalledWith('/welcome');
  });

  it('a 400 response renders field-error messages without resetting the form', async () => {
    await createFixture();
    fixture.detectChanges();
    flushOwnProfile();
    fixture.detectChanges();

    submit();

    httpMock
      .expectOne('/api/users/me/profile/complete')
      .flush(
        { errors: [{ field: 'taxId', message: 'invalid checksum' }] },
        { status: 400, statusText: 'Bad Request' },
      );
    fixture.detectChanges();

    const errorEl = fixture.nativeElement.querySelector(
      '[data-testid="complete-profile-field-errors"]',
    );
    expect(errorEl).toBeTruthy();
    // Renders the human-readable, country-driven label (CPF for BR) instead of the raw
    // backend field key.
    expect(errorEl.textContent).toContain('CPF');
    expect(
      fixture.nativeElement.querySelector('[data-testid="profile-field-fullName"]').value,
    ).toBe('Jane Doe');
  });

  it('maps a 400 INVALID_CPF response onto the taxId field, not "unknown"', async () => {
    await createFixture();
    fixture.detectChanges();
    flushOwnProfile();
    fixture.detectChanges();

    submit();

    httpMock
      .expectOne('/api/users/me/profile/complete')
      .flush({ code: 'INVALID_CPF' }, { status: 400, statusText: 'Bad Request' });
    fixture.detectChanges();

    const errorEl = fixture.nativeElement.querySelector(
      '[data-testid="complete-profile-field-errors"]',
    );
    expect(errorEl).toBeTruthy();
    expect(errorEl.textContent).not.toContain('unknown');
    expect(errorEl.textContent).toContain('CPF');

    // The offending input itself gets inline error styling/message too, not just the banner.
    expect(
      fixture.nativeElement.querySelector('[data-testid="profile-field-taxId"]').className,
    ).toContain('border-red-500');
    expect(
      fixture.nativeElement.querySelector('[data-testid="profile-field-error-taxId"]'),
    ).toBeTruthy();
  });

  it('maps a 400 address.countryCode NotBlank error onto the human-readable "Country" label', async () => {
    await createFixture();
    fixture.detectChanges();
    flushOwnProfile();
    fixture.detectChanges();

    submit();

    httpMock
      .expectOne('/api/users/me/profile/complete')
      .flush(
        { errors: [{ field: 'address.countryCode', message: 'must not be blank' }] },
        { status: 400, statusText: 'Bad Request' },
      );
    fixture.detectChanges();

    const errorEl = fixture.nativeElement.querySelector(
      '[data-testid="complete-profile-field-errors"]',
    );
    expect(errorEl).toBeTruthy();
    expect(errorEl.textContent).toContain('Country');
    expect(
      fixture.nativeElement.querySelector('[data-testid="profile-field-countryCode"]').className,
    ).toContain('border-red-500');
  });

  it('never passes the raw error/body to any console.* call on a 400 response', async () => {
    await createFixture();
    fixture.detectChanges();
    flushOwnProfile();
    fixture.detectChanges();

    const logSpy = vi.spyOn(console, 'log').mockImplementation(vi.fn());
    const errorSpy = vi.spyOn(console, 'error').mockImplementation(vi.fn());
    const warnSpy = vi.spyOn(console, 'warn').mockImplementation(vi.fn());
    const debugSpy = vi.spyOn(console, 'debug').mockImplementation(vi.fn());

    submit();

    httpMock
      .expectOne('/api/users/me/profile/complete')
      .flush(
        { errors: [{ field: 'taxId', message: 'invalid checksum', value: '123.456.789-00' }] },
        { status: 400, statusText: 'Bad Request' },
      );
    fixture.detectChanges();

    for (const spy of [logSpy, errorSpy, warnSpy, debugSpy]) {
      for (const call of spy.mock.calls) {
        for (const arg of call) {
          const serialized = JSON.stringify(arg);
          expect(serialized === undefined ? String(arg) : serialized).not.toContain(
            '123.456.789-00',
          );
          expect(serialized === undefined ? String(arg) : serialized).not.toContain(
            'invalid checksum',
          );
        }
      }
    }

    logSpy.mockRestore();
    errorSpy.mockRestore();
    warnSpy.mockRestore();
    debugSpy.mockRestore();
  });

  it('treats a 409 PROFILE_ALREADY_COMPLETE response as success, navigating to /welcome with no error', async () => {
    await createFixture();
    fixture.detectChanges();
    flushOwnProfile();
    fixture.detectChanges();

    const router = TestBed.inject(Router);
    vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);

    submit();

    httpMock
      .expectOne('/api/users/me/profile/complete')
      .flush({ code: 'PROFILE_ALREADY_COMPLETE' }, { status: 409, statusText: 'Conflict' });
    fixture.detectChanges();

    expect(router.navigateByUrl).toHaveBeenCalledWith('/welcome');
    expect(
      fixture.nativeElement.querySelector('[data-testid="complete-profile-field-errors"]'),
    ).toBeNull();
    expect(fixture.nativeElement.querySelector('[data-testid="error-state"]')).toBeNull();
  });

  it('a network/5xx response renders the shared error state without resetting the form', async () => {
    await createFixture();
    fixture.detectChanges();
    flushOwnProfile();
    fixture.detectChanges();

    submit();

    httpMock
      .expectOne('/api/users/me/profile/complete')
      .flush({}, { status: 500, statusText: 'Internal Server Error' });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="error-state"]')).toBeTruthy();
    expect(
      (
        fixture.componentInstance as unknown as { formFields: () => { fullName: string } }
      ).formFields().fullName,
    ).toBe('Jane Doe');
  });

  it('confirms masking is inherited from ProfileFieldsFormComponent (taxId display, unmasked submit)', async () => {
    await createFixture();
    fixture.detectChanges();
    flushOwnProfile();
    fixture.detectChanges();

    const router = TestBed.inject(Router);
    vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);

    const taxIdInput: HTMLInputElement = fixture.nativeElement.querySelector(
      '[data-testid="profile-field-taxId"]',
    );
    taxIdInput.value = '12345678900';
    taxIdInput.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    expect(taxIdInput.value).toBe('123.456.789-00');

    submit();

    const req = httpMock.expectOne('/api/users/me/profile/complete');
    expect(req.request.body.taxId).toBe('12345678900');
    req.flush(profile);
  });
});
