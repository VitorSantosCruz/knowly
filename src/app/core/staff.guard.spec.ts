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

  it('allows navigation when the caller can list every tenant (staff)', async () => {
    const resultPromise = firstValueFrom(runGuard());
    httpMock.expectOne('/api/tenants').flush([{ id: 1, name: 'Acme' }]);

    expect(await resultPromise).toBe(true);
  });

  it('redirects to /select-tenant when the caller is not staff', async () => {
    const resultPromise = firstValueFrom(runGuard());
    httpMock.expectOne('/api/tenants').flush(null, { status: 403, statusText: 'Forbidden' });

    const result = await resultPromise;
    expect(result).toBeInstanceOf(UrlTree);
    expect(router.serializeUrl(result as UrlTree)).toBe('/select-tenant');
  });
});
