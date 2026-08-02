import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { Router, UrlTree } from '@angular/router';
import { Observable, firstValueFrom } from 'rxjs';
import { accessGroupManagementGuard } from './access-group-management.guard';

describe('accessGroupManagementGuard', () => {
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
        accessGroupManagementGuard(null as never, null as never) as Observable<boolean | UrlTree>,
    );
  }

  it('allows navigation for a caller granted STAFF_PERMISSION_MANAGE', async () => {
    const resultPromise = firstValueFrom(runGuard());
    httpMock
      .expectOne('/api/staff/permissions')
      .flush({ permissions: ['STAFF_PERMISSION_MANAGE'] });

    expect(await resultPromise).toBe(true);
  });

  it('redirects to /select-tenant when the caller lacks STAFF_PERMISSION_MANAGE', async () => {
    const resultPromise = firstValueFrom(runGuard());
    httpMock.expectOne('/api/staff/permissions').flush({ permissions: ['TENANT_CREATE'] });

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
