import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { PermissionsService } from './permissions.service';

describe('PermissionsService', () => {
  let service: PermissionsService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(PermissionsService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('starts with no known permissions', () => {
    expect(service.permissions()).toBeNull();
    expect(service.has('ARTICLE_CREATE')).toBe(false);
  });

  it('exposes the fetched permissions and has() reflects them', () => {
    service.fetch();

    const req = httpMock.expectOne('/api/tenants/permissions');
    expect(req.request.method).toBe('GET');
    req.flush({ permissions: ['ARTICLE_VIEW', 'ARTICLE_CREATE'] });

    expect(service.permissions()).toEqual(['ARTICLE_VIEW', 'ARTICLE_CREATE']);
    expect(service.has('ARTICLE_CREATE')).toBe(true);
    expect(service.has('ARTICLE_DELETE')).toBe(false);
  });

  it('treats a 403 (no active tenant) as zero permissions rather than an unhandled error', () => {
    service.fetch();

    httpMock
      .expectOne('/api/tenants/permissions')
      .flush({ code: 'TENANT_ACCESS_DENIED' }, { status: 403, statusText: 'Forbidden' });

    expect(service.permissions()).toEqual([]);
    expect(service.has('ARTICLE_VIEW')).toBe(false);
  });

  it('starts with hasInAnyTenant() false for any permission before fetchInAnyTenant()', () => {
    expect(service.hasInAnyTenant('PROFILE_EDIT')).toBe(false);
  });

  it('fetchInAnyTenant() calls the any-tenant endpoint and hasInAnyTenant() reflects the grant', () => {
    service.fetchInAnyTenant('PROFILE_EDIT');

    const req = httpMock.expectOne('/api/tenants/permissions/any-tenant?permission=PROFILE_EDIT');
    expect(req.request.method).toBe('GET');
    req.flush({ granted: true });

    expect(service.hasInAnyTenant('PROFILE_EDIT')).toBe(true);
  });

  it('fetchInAnyTenant() treats a 401/error as ungranted rather than an unhandled error', () => {
    service.fetchInAnyTenant('PROFILE_EDIT');

    httpMock
      .expectOne('/api/tenants/permissions/any-tenant?permission=PROFILE_EDIT')
      .flush({}, { status: 401, statusText: 'Unauthorized' });

    expect(service.hasInAnyTenant('PROFILE_EDIT')).toBe(false);
  });
});
