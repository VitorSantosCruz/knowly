import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { ActiveTenantService } from './active-tenant.service';

describe('ActiveTenantService', () => {
  let service: ActiveTenantService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ActiveTenantService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('starts with no active tenant known', () => {
    expect(service.activeTenantId()).toBeNull();
    expect(service.activeTenantName()).toBeNull();
  });

  it('starts unresolved and resolves after fetch() completes', () => {
    expect(service.activeTenantResolved()).toBe(false);

    service.fetch();
    expect(service.activeTenantResolved()).toBe(false);

    httpMock.expectOne('/api/tenants/memberships').flush([]);

    expect(service.activeTenantResolved()).toBe(true);
  });

  it('resolves even on the "no active membership found, preserve prior value" branch', () => {
    service.fetch();
    httpMock
      .expectOne('/api/tenants/memberships')
      .flush([{ tenantId: 1, tenantName: 'Tenant A', role: 'MEMBER', active: false }]);

    expect(service.activeTenantId()).toBeNull();
    expect(service.activeTenantResolved()).toBe(true);
  });

  it('exposes the active membership after fetching', () => {
    service.fetch();

    const req = httpMock.expectOne('/api/tenants/memberships');
    req.flush([
      { tenantId: 1, tenantName: 'Tenant A', role: 'MEMBER', active: false },
      { tenantId: 2, tenantName: 'Tenant B', role: 'ADMIN', active: true },
    ]);

    expect(service.activeTenantId()).toBe(2);
    expect(service.activeTenantName()).toBe('Tenant B');
  });

  it('leaves the active tenant null when no membership is marked active', () => {
    service.fetch();

    const req = httpMock.expectOne('/api/tenants/memberships');
    req.flush([{ tenantId: 1, tenantName: 'Tenant A', role: 'MEMBER', active: false }]);

    expect(service.activeTenantId()).toBeNull();
  });

  it('preserves an already-known active tenant (staff, no real membership row) when fetch finds none active', () => {
    service.selectTenant(5, 'Staffed Co').subscribe();
    httpMock.expectOne('/api/tenants/active').flush({});
    expect(service.activeTenantName()).toBe('Staffed Co');

    service.fetch();
    httpMock.expectOne('/api/tenants/memberships').flush([]);

    expect(service.activeTenantId()).toBe(5);
    expect(service.activeTenantName()).toBe('Staffed Co');
  });

  it('list() fetches the memberships without mutating the active-tenant signals', () => {
    let result: unknown;
    service.list().subscribe((memberships) => (result = memberships));

    const req = httpMock.expectOne('/api/tenants/memberships');
    expect(req.request.method).toBe('GET');
    req.flush([{ tenantId: 1, tenantName: 'Tenant A', role: 'MEMBER', active: false }]);

    expect(result).toEqual([
      { tenantId: 1, tenantName: 'Tenant A', role: 'MEMBER', active: false },
    ]);
    expect(service.activeTenantId()).toBeNull();
  });

  it('listAllTenants() fetches a page of every tenant in the system', () => {
    let result: unknown;
    service.listAllTenants(0, 20).subscribe((page) => (result = page));

    const req = httpMock.expectOne(
      (r) =>
        r.url === '/api/tenants' && r.params.get('page') === '0' && r.params.get('size') === '20',
    );
    expect(req.request.method).toBe('GET');
    expect(req.request.params.has('search')).toBe(false);
    const envelope = {
      content: [
        { id: 1, name: 'Tenant A' },
        { id: 2, name: 'Tenant B' },
      ],
      page: 0,
      size: 20,
      totalElements: 2,
      totalPages: 1,
    };
    req.flush(envelope);

    expect(result).toEqual(envelope);
  });

  it('listAllTenants() includes the search param only when supplied', () => {
    service.listAllTenants(1, 10, 'acme').subscribe();

    const req = httpMock.expectOne(
      (r) =>
        r.url === '/api/tenants' &&
        r.params.get('page') === '1' &&
        r.params.get('size') === '10' &&
        r.params.get('search') === 'acme',
    );
    req.flush({ content: [], page: 1, size: 10, totalElements: 0, totalPages: 0 });
  });

  it('createTenant() posts the name and admin email', () => {
    let completed = false;
    service.createTenant('Acme', 'admin@acme.test').subscribe(() => (completed = true));

    const req = httpMock.expectOne('/api/tenants');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ name: 'Acme', adminEmail: 'admin@acme.test' });
    req.flush({});

    expect(completed).toBe(true);
  });

  it('selectTenant() posts the choice and updates the active tenant signals', () => {
    service.selectTenant(2, 'Tenant B').subscribe();

    const req = httpMock.expectOne('/api/tenants/active');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ tenantId: 2 });
    req.flush({});

    expect(service.activeTenantId()).toBe(2);
    expect(service.activeTenantName()).toBe('Tenant B');
  });
});
