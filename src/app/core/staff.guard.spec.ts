import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { Router, UrlTree } from '@angular/router';
import { Observable, firstValueFrom } from 'rxjs';
import { staffGuard } from './staff.guard';

describe('staffGuard', () => {
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
      () => staffGuard(null as never, null as never) as Observable<boolean | UrlTree>,
    );
  }

  it('allows navigation for a STAFF user granted only TENANT_CREATE, not TENANT_ACT_AS_ANY', async () => {
    // Regression test: previously this guard checked GET /api/tenants (which needs
    // TENANT_ACT_AS_ANY) instead of the permission this route actually requires.
    const resultPromise = firstValueFrom(runGuard());
    httpMock.expectOne('/api/staff/permissions').flush({ permissions: ['TENANT_CREATE'] });

    expect(await resultPromise).toBe(true);
  });

  it('redirects to /select-tenant when the caller lacks TENANT_CREATE', async () => {
    const resultPromise = firstValueFrom(runGuard());
    httpMock.expectOne('/api/staff/permissions').flush({ permissions: ['TENANT_ACT_AS_ANY'] });

    const result = await resultPromise;
    expect(result).toBeInstanceOf(UrlTree);
    expect(router.serializeUrl(result as UrlTree)).toBe('/select-tenant');
  });

  it('redirects to /select-tenant when the caller holds no global permissions at all', async () => {
    const resultPromise = firstValueFrom(runGuard());
    httpMock.expectOne('/api/staff/permissions').flush({ permissions: [] });

    const result = await resultPromise;
    expect(result).toBeInstanceOf(UrlTree);
    expect(router.serializeUrl(result as UrlTree)).toBe('/select-tenant');
  });
});
