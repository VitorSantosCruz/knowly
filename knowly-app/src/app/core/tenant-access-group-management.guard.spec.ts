import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { Router, UrlTree } from '@angular/router';
import { Observable, firstValueFrom } from 'rxjs';
import { tenantAccessGroupManagementGuard } from './tenant-access-group-management.guard';

describe('tenantAccessGroupManagementGuard', () => {
  let httpMock: HttpTestingController;
  let router: Router;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    httpMock = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
  });

  afterEach(() => {
    httpMock.verify();
  });

  function runGuard(): Observable<boolean | UrlTree> {
    return TestBed.runInInjectionContext(
      () =>
        tenantAccessGroupManagementGuard(null as never, null as never) as Observable<
          boolean | UrlTree
        >,
    );
  }

  function flushActive(role: 'MEMBER_ADMIN' | 'MEMBER' | undefined): void {
    httpMock
      .expectOne('/api/tenants/active')
      .flush(
        role === undefined
          ? { tenantId: 1, tenantName: 'Acme' }
          : { tenantId: 1, tenantName: 'Acme', role },
      );
  }

  it('allows navigation for a caller holding TENANT_ACCESS_GROUP_VIEW globally (staff)', async () => {
    const resultPromise = firstValueFrom(runGuard());
    flushActive('MEMBER');
    httpMock
      .expectOne('/api/staff/permissions')
      .flush({ permissions: ['TENANT_ACCESS_GROUP_VIEW'], isStaffAccount: true });

    expect(await resultPromise).toBe(true);
  });

  it('allows navigation for a real MEMBER_ADMIN of the active tenant, regardless of global permissions', async () => {
    const resultPromise = firstValueFrom(runGuard());
    flushActive('MEMBER_ADMIN');
    httpMock.expectOne('/api/staff/permissions').flush({ permissions: [], isStaffAccount: false });

    expect(await resultPromise).toBe(true);
  });

  it('redirects to /select-tenant when the caller is neither a MEMBER_ADMIN nor holds TENANT_ACCESS_GROUP_VIEW', async () => {
    const resultPromise = firstValueFrom(runGuard());
    flushActive('MEMBER');
    httpMock
      .expectOne('/api/staff/permissions')
      .flush({ permissions: ['TENANT_MEMBER_VIEW'], isStaffAccount: false });

    const result = await resultPromise;
    expect(result).toBeInstanceOf(UrlTree);
    expect(router.serializeUrl(result as UrlTree)).toBe('/select-tenant');
  });

  it('redirects to /select-tenant when GET /api/tenants/active errors (network failure)', async () => {
    const resultPromise = firstValueFrom(runGuard());
    httpMock.expectOne('/api/tenants/active').error(new ProgressEvent('error'));
    httpMock.expectOne('/api/staff/permissions').flush({ permissions: [], isStaffAccount: false });

    const result = await resultPromise;
    expect(result).toBeInstanceOf(UrlTree);
    expect(router.serializeUrl(result as UrlTree)).toBe('/select-tenant');
  });

  it('redirects to /select-tenant when GET /api/staff/permissions errors (network failure)', async () => {
    const resultPromise = firstValueFrom(runGuard());
    flushActive('MEMBER');
    httpMock.expectOne('/api/staff/permissions').error(new ProgressEvent('error'));

    const result = await resultPromise;
    expect(result).toBeInstanceOf(UrlTree);
    expect(router.serializeUrl(result as UrlTree)).toBe('/select-tenant');
  });
});
