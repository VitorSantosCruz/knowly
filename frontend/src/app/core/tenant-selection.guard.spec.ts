import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { Router, UrlTree } from '@angular/router';
import { Observable, firstValueFrom } from 'rxjs';
import { tenantSelectionGuard } from './tenant-selection.guard';

describe('tenantSelectionGuard', () => {
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
      () => tenantSelectionGuard(null as never, null as never) as Observable<boolean | UrlTree>,
    );
  }

  it('allows navigation when a membership is already active', async () => {
    const resultPromise = firstValueFrom(runGuard());
    httpMock
      .expectOne('/api/tenants/memberships')
      .flush([{ tenantId: 1, tenantName: 'Acme', role: 'ADMIN', active: true }]);

    expect(await resultPromise).toBe(true);
  });

  it('allows navigation when there is only one membership', async () => {
    const resultPromise = firstValueFrom(runGuard());
    httpMock
      .expectOne('/api/tenants/memberships')
      .flush([{ tenantId: 1, tenantName: 'Acme', role: 'ADMIN', active: false }]);

    expect(await resultPromise).toBe(true);
  });

  it('redirects to /select-tenant when multiple memberships exist and none is active', async () => {
    const resultPromise = firstValueFrom(runGuard());
    httpMock.expectOne('/api/tenants/memberships').flush([
      { tenantId: 1, tenantName: 'Acme', role: 'ADMIN', active: false },
      { tenantId: 2, tenantName: 'Other', role: 'MEMBER', active: false },
    ]);

    const result = await resultPromise;
    expect(result).toBeInstanceOf(UrlTree);
    expect(router.serializeUrl(result as UrlTree)).toBe('/select-tenant');
  });

  it('allows navigation when there are zero memberships (staff — lands on dashboard directly)', async () => {
    const resultPromise = firstValueFrom(runGuard());
    httpMock.expectOne('/api/tenants/memberships').flush([]);

    expect(await resultPromise).toBe(true);
  });
});
