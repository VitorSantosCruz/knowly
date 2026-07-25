import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import { provideTransloco } from '@jsverse/transloco';
import { SelectTenantPageComponent } from './select-tenant-page.component';
import { FakeTranslocoLoader } from '../../testing/fake-transloco-loader';

describe('SelectTenantPageComponent', () => {
  let fixture: ComponentFixture<SelectTenantPageComponent>;
  let httpMock: HttpTestingController;
  let router: Router;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SelectTenantPageComponent],
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

    fixture = TestBed.createComponent(SelectTenantPageComponent);
    httpMock = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
  });

  afterEach(() => {
    httpMock.verify();
  });

  function flushGlobalPermissions(permissions: string[] = []): void {
    httpMock.expectOne('/api/staff/permissions').flush({ permissions });
  }

  it('lists the memberships to choose from', () => {
    fixture.detectChanges();
    flushGlobalPermissions();
    httpMock.expectOne('/api/tenants/memberships').flush([
      { tenantId: 1, tenantName: 'Acme', role: 'ADMIN', active: false },
      { tenantId: 2, tenantName: 'Other Co', role: 'MEMBER', active: false },
    ]);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Acme');
    expect(fixture.nativeElement.textContent).toContain('Other Co');
  });

  it('selecting a tenant posts the choice and navigates to the dashboard', () => {
    const navigateSpy = vi.spyOn(router, 'navigateByUrl');
    fixture.detectChanges();
    flushGlobalPermissions();
    httpMock.expectOne('/api/tenants/memberships').flush([
      { tenantId: 1, tenantName: 'Acme', role: 'ADMIN', active: false },
      { tenantId: 2, tenantName: 'Other Co', role: 'MEMBER', active: false },
    ]);
    fixture.detectChanges();

    fixture.nativeElement.querySelector('[data-testid="select-tenant-2"]').click();

    const req = httpMock.expectOne('/api/tenants/active');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ tenantId: 2 });
    req.flush({});

    expect(navigateSpy).toHaveBeenCalledWith('/welcome');
  });

  it('falls back to listing every tenant in the system when there are no memberships (staff)', () => {
    fixture.detectChanges();
    flushGlobalPermissions();
    httpMock.expectOne('/api/tenants/memberships').flush([]);
    httpMock.expectOne('/api/tenants').flush([
      { id: 1, name: 'Acme' },
      { id: 2, name: 'Other Co' },
    ]);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Acme');
    expect(fixture.nativeElement.textContent).toContain('Other Co');
  });

  it('selecting a tenant from the staff fallback posts the choice and navigates to the dashboard', () => {
    const navigateSpy = vi.spyOn(router, 'navigateByUrl');
    fixture.detectChanges();
    flushGlobalPermissions();
    httpMock.expectOne('/api/tenants/memberships').flush([]);
    httpMock.expectOne('/api/tenants').flush([{ id: 1, name: 'Acme' }]);
    fixture.detectChanges();

    fixture.nativeElement.querySelector('[data-testid="select-tenant-1"]').click();

    const req = httpMock.expectOne('/api/tenants/active');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ tenantId: 1 });
    req.flush({});

    expect(navigateSpy).toHaveBeenCalledWith('/welcome');
  });

  it('shows a create-tenant link when the caller holds TENANT_CREATE', () => {
    fixture.detectChanges();
    flushGlobalPermissions(['TENANT_CREATE']);
    httpMock.expectOne('/api/tenants/memberships').flush([]);
    httpMock.expectOne('/api/tenants').flush([{ id: 1, name: 'Acme' }]);
    fixture.detectChanges();

    const link = fixture.nativeElement.querySelector('[data-testid="create-tenant-link"]');
    expect(link).toBeTruthy();
    expect(link.getAttribute('href')).toBe('/tenants/new');
  });

  it('does not show a create-tenant link when the caller lacks TENANT_CREATE', () => {
    fixture.detectChanges();
    flushGlobalPermissions([]);
    httpMock.expectOne('/api/tenants/memberships').flush([
      { tenantId: 1, tenantName: 'Acme', role: 'ADMIN', active: false },
      { tenantId: 2, tenantName: 'Other Co', role: 'MEMBER', active: false },
    ]);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="create-tenant-link"]')).toBeFalsy();
  });

  it('shows an empty state when the memberships and all-tenants fallback are both empty', () => {
    fixture.detectChanges();
    flushGlobalPermissions();
    httpMock.expectOne('/api/tenants/memberships').flush([]);
    httpMock.expectOne('/api/tenants').flush([]);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="select-tenant-empty"]')).toBeTruthy();
  });

  it('shows an empty state when the all-tenants fallback request itself fails', () => {
    fixture.detectChanges();
    flushGlobalPermissions();
    httpMock.expectOne('/api/tenants/memberships').flush([]);
    httpMock
      .expectOne('/api/tenants')
      .flush({ code: 'PERMISSION_DENIED' }, { status: 403, statusText: 'Forbidden' });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="select-tenant-empty"]')).toBeTruthy();
  });
});
