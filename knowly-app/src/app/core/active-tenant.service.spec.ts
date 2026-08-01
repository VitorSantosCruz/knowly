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

    httpMock
      .expectOne('/api/tenants/active')
      .flush(null, { status: 204, statusText: 'No Content' });

    expect(service.activeTenantResolved()).toBe(true);
  });

  it('exposes the active tenant reported by GET /api/tenants/active (source of truth for server-side session state)', () => {
    service.fetch();

    const req = httpMock.expectOne('/api/tenants/active');
    expect(req.request.method).toBe('GET');
    req.flush({ tenantId: 2, tenantName: 'Tenant B', role: 'MEMBER_ADMIN' });

    expect(service.activeTenantId()).toBe(2);
    expect(service.activeTenantName()).toBe('Tenant B');
    expect(service.activeTenantRole()).toBe('MEMBER_ADMIN');
  });

  it('reflects a staff session acting as a tenant (role omitted, no real membership row) since GET /api/tenants/active reads TenantContext directly', () => {
    service.fetch();

    httpMock.expectOne('/api/tenants/active').flush({ tenantId: 5, tenantName: 'Staffed Co' });

    expect(service.activeTenantId()).toBe(5);
    expect(service.activeTenantName()).toBe('Staffed Co');
    expect(service.activeTenantRole()).toBeNull();
  });

  it('leaves the active tenant null when GET /api/tenants/active returns 204 (no active tenant)', () => {
    service.fetch();

    httpMock
      .expectOne('/api/tenants/active')
      .flush(null, { status: 204, statusText: 'No Content' });

    expect(service.activeTenantId()).toBeNull();
    expect(service.activeTenantName()).toBeNull();
    expect(service.activeTenantRole()).toBeNull();
  });

  it('nulls out a stale active tenant from a prior fetch when a later fetch finds none active', () => {
    service.fetch();
    httpMock
      .expectOne('/api/tenants/active')
      .flush({ tenantId: 2, tenantName: 'Tenant B', role: 'MEMBER_ADMIN' });

    expect(service.activeTenantId()).toBe(2);

    service.fetch();
    httpMock
      .expectOne('/api/tenants/active')
      .flush(null, { status: 204, statusText: 'No Content' });

    expect(service.activeTenantId()).toBeNull();
    expect(service.activeTenantName()).toBeNull();
    expect(service.activeTenantRole()).toBeNull();
  });

  it('getActive() resolves null for a 204 response', () => {
    let result: unknown = 'unset';
    service.getActive().subscribe((active) => (result = active));

    httpMock
      .expectOne('/api/tenants/active')
      .flush(null, { status: 204, statusText: 'No Content' });

    expect(result).toBeNull();
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

  it('leaveTenant() posts to the clear endpoint and nulls the active tenant signals on success', () => {
    service.selectTenant(5, 'Staffed Co').subscribe();
    httpMock.expectOne('/api/tenants/active').flush({});
    expect(service.activeTenantId()).toBe(5);

    service.leaveTenant().subscribe();

    const req = httpMock.expectOne('/api/tenants/active/clear');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({});
    req.flush({});

    expect(service.activeTenantId()).toBeNull();
    expect(service.activeTenantName()).toBeNull();
    expect(service.activeTenantRole()).toBeNull();

    // fetch() re-reads server-side session state directly from GET /api/tenants/active, so a
    // later fetch() correctly reports no active tenant after leaveTenant() clears the session.
    service.fetch();
    httpMock
      .expectOne('/api/tenants/active')
      .flush(null, { status: 204, statusText: 'No Content' });
    expect(service.activeTenantId()).toBeNull();
  });

  it('leaveTenant() leaves the active tenant signals unchanged when the HTTP call fails', () => {
    service.selectTenant(5, 'Staffed Co').subscribe();
    httpMock.expectOne('/api/tenants/active').flush({});
    expect(service.activeTenantId()).toBe(5);
    expect(service.activeTenantName()).toBe('Staffed Co');

    let capturedError: unknown;
    service.leaveTenant().subscribe({ error: (err) => (capturedError = err) });

    httpMock
      .expectOne('/api/tenants/active/clear')
      .flush({}, { status: 500, statusText: 'Internal Server Error' });

    expect(service.activeTenantId()).toBe(5);
    expect(service.activeTenantName()).toBe('Staffed Co');
    expect(capturedError).toBeTruthy();
  });
});
