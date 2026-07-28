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

  function flushAllTenants(
    content: { id: number; name: string }[],
    overrides: Partial<{
      page: number;
      size: number;
      totalElements: number;
      totalPages: number;
    }> = {},
  ): void {
    const req = httpMock.expectOne((r) => r.url === '/api/tenants');
    req.flush({
      content,
      page: overrides.page ?? 0,
      size: overrides.size ?? 20,
      totalElements: overrides.totalElements ?? content.length,
      totalPages: overrides.totalPages ?? 1,
    });
  }

  it('falls back to listing every tenant in the system when there are no memberships (staff)', () => {
    fixture.detectChanges();
    flushGlobalPermissions();
    httpMock.expectOne('/api/tenants/memberships').flush([]);
    flushAllTenants([
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
    flushAllTenants([{ id: 1, name: 'Acme' }]);
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
    flushAllTenants([{ id: 1, name: 'Acme' }]);
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

  it('shows the distinct no-results state (not the network-failure empty state) when the system genuinely has zero tenants', () => {
    fixture.detectChanges();
    flushGlobalPermissions();
    httpMock.expectOne('/api/tenants/memberships').flush([]);
    flushAllTenants([], { totalElements: 0, totalPages: 0 });
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[data-testid="select-tenant-no-results"]'),
    ).toBeTruthy();
    expect(fixture.nativeElement.querySelector('[data-testid="select-tenant-empty"]')).toBeFalsy();
  });

  it('shows an empty state when the all-tenants fallback request itself fails', () => {
    fixture.detectChanges();
    flushGlobalPermissions();
    httpMock.expectOne('/api/tenants/memberships').flush([]);
    httpMock
      .expectOne((r) => r.url === '/api/tenants')
      .flush({ code: 'PERMISSION_DENIED' }, { status: 403, statusText: 'Forbidden' });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="select-tenant-empty"]')).toBeTruthy();
    expect(
      fixture.nativeElement.querySelector('[data-testid="select-tenant-no-results"]'),
    ).toBeFalsy();
  });

  it('shows a distinct "no results" message when a search yields zero matches', () => {
    vi.useFakeTimers();
    fixture.detectChanges();
    flushGlobalPermissions();
    httpMock.expectOne('/api/tenants/memberships').flush([]);
    flushAllTenants([{ id: 1, name: 'Acme' }]);
    fixture.detectChanges();

    fixture.nativeElement.querySelector('[data-testid="select-tenant-search"]').value = 'zzz';
    fixture.nativeElement
      .querySelector('[data-testid="select-tenant-search"]')
      .dispatchEvent(new Event('input'));
    vi.advanceTimersByTime(300);

    flushAllTenants([], { totalElements: 0, totalPages: 0 });
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[data-testid="select-tenant-no-results"]'),
    ).toBeTruthy();
    expect(fixture.nativeElement.querySelector('[data-testid="select-tenant-empty"]')).toBeFalsy();
    vi.useRealTimers();
  });

  it('pagination buttons step through pages and are disabled at the edges', () => {
    fixture.detectChanges();
    flushGlobalPermissions();
    httpMock.expectOne('/api/tenants/memberships').flush([]);
    flushAllTenants([{ id: 1, name: 'Acme' }], { page: 0, totalPages: 2, totalElements: 2 });
    fixture.detectChanges();

    const prevBtn = fixture.nativeElement.querySelector('[data-testid="select-tenant-prev-page"]');
    const nextBtn = fixture.nativeElement.querySelector('[data-testid="select-tenant-next-page"]');
    expect(prevBtn.disabled).toBe(true);
    expect(nextBtn.disabled).toBe(false);

    nextBtn.click();

    const req = httpMock.expectOne((r) => r.url === '/api/tenants' && r.params.get('page') === '1');
    req.flush({
      content: [{ id: 2, name: 'Other Co' }],
      page: 1,
      size: 20,
      totalElements: 2,
      totalPages: 2,
    });
    fixture.detectChanges();

    expect(prevBtn.disabled).toBe(false);
    expect(nextBtn.disabled).toBe(true);
    expect(fixture.nativeElement.textContent).toContain('Other Co');
  });

  it('typing into the search input debounces before requesting, and resets the page to 0', () => {
    vi.useFakeTimers();
    fixture.detectChanges();
    flushGlobalPermissions();
    httpMock.expectOne('/api/tenants/memberships').flush([]);
    flushAllTenants([{ id: 1, name: 'Acme' }], { page: 0, totalPages: 2, totalElements: 2 });
    fixture.detectChanges();

    fixture.nativeElement.querySelector('[data-testid="select-tenant-next-page"]').click();
    httpMock
      .expectOne((r) => r.url === '/api/tenants' && r.params.get('page') === '1')
      .flush({
        content: [{ id: 2, name: 'Other Co' }],
        page: 1,
        size: 20,
        totalElements: 2,
        totalPages: 2,
      });
    fixture.detectChanges();

    const searchInput = fixture.nativeElement.querySelector('[data-testid="select-tenant-search"]');
    searchInput.value = 'ac';
    searchInput.dispatchEvent(new Event('input'));

    httpMock.expectNone((r) => r.url === '/api/tenants');

    vi.advanceTimersByTime(299);
    httpMock.expectNone((r) => r.url === '/api/tenants');

    vi.advanceTimersByTime(1);
    const req = httpMock.expectOne(
      (r) => r.url === '/api/tenants' && r.params.get('search') === 'ac',
    );
    expect(req.request.params.get('page')).toBe('0');
    req.flush({
      content: [{ id: 1, name: 'Acme' }],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    });

    vi.useRealTimers();
  });

  it('navigating pages after a search keeps the search term on the request', () => {
    vi.useFakeTimers();
    fixture.detectChanges();
    flushGlobalPermissions();
    httpMock.expectOne('/api/tenants/memberships').flush([]);
    flushAllTenants([{ id: 1, name: 'Acme' }]);
    fixture.detectChanges();

    const searchInput = fixture.nativeElement.querySelector('[data-testid="select-tenant-search"]');
    searchInput.value = 'co';
    searchInput.dispatchEvent(new Event('input'));
    vi.advanceTimersByTime(300);

    httpMock
      .expectOne((r) => r.url === '/api/tenants' && r.params.get('search') === 'co')
      .flush({
        content: [{ id: 2, name: 'Other Co' }],
        page: 0,
        size: 20,
        totalElements: 2,
        totalPages: 2,
      });
    fixture.detectChanges();

    fixture.nativeElement.querySelector('[data-testid="select-tenant-next-page"]').click();

    const req = httpMock.expectOne(
      (r) =>
        r.url === '/api/tenants' && r.params.get('page') === '1' && r.params.get('search') === 'co',
    );
    req.flush({
      content: [],
      page: 1,
      size: 20,
      totalElements: 2,
      totalPages: 2,
    });

    vi.useRealTimers();
  });
});
