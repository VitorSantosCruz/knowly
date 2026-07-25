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

  it('renders the form', () => {
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="tenant-create-form"]')).toBeTruthy();
  });

  it('does not call the API when fields are empty', () => {
    fixture.detectChanges();

    submit();
    fixture.detectChanges();

    httpMock.expectNone('/api/tenants');
    expect(fixture.nativeElement.querySelector('[data-testid="tenant-create-error"]')).toBeTruthy();
  });

  it('creates the tenant and navigates to /select-tenant on success', () => {
    const navigateSpy = vi.spyOn(router, 'navigateByUrl');
    fixture.detectChanges();

    setValue('tenant-create-name', 'Acme');
    setValue('tenant-create-admin-email', 'admin@acme.test');
    submit();

    const req = httpMock.expectOne('/api/tenants');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ name: 'Acme', adminEmail: 'admin@acme.test' });
    req.flush({});

    expect(navigateSpy).toHaveBeenCalledWith('/select-tenant');
  });

  it('shows an inline error and keeps the entered values when the API call fails', () => {
    fixture.detectChanges();

    setValue('tenant-create-name', 'Acme');
    setValue('tenant-create-admin-email', 'admin@acme.test');
    submit();

    const req = httpMock.expectOne('/api/tenants');
    req.flush({ code: 'CONFLICT' }, { status: 409, statusText: 'Conflict' });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="tenant-create-error"]')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('[data-testid="tenant-create-name"]').value).toBe(
      'Acme',
    );
    expect(
      fixture.nativeElement.querySelector('[data-testid="tenant-create-admin-email"]').value,
    ).toBe('admin@acme.test');
  });
});
