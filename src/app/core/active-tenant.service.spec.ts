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
});
