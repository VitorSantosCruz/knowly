import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { Router, UrlTree } from '@angular/router';
import { Observable, firstValueFrom } from 'rxjs';
import { rootRedirectGuard } from './root-redirect.guard';

describe('rootRedirectGuard', () => {
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
      () => rootRedirectGuard(null as never, null as never) as Observable<boolean | UrlTree>,
    );
  }

  it('redirects to /welcome when the session is valid', async () => {
    const resultPromise = firstValueFrom(runGuard());
    httpMock.expectOne('/api/staff/permissions').flush({ permissions: [] });

    const result = await resultPromise;
    expect(result).toBeInstanceOf(UrlTree);
    expect(router.serializeUrl(result as UrlTree)).toBe('/welcome');
  });

  it('redirects to /login when there is no valid session', async () => {
    const resultPromise = firstValueFrom(runGuard());
    httpMock
      .expectOne('/api/staff/permissions')
      .flush({}, { status: 401, statusText: 'Unauthorized' });

    const result = await resultPromise;
    expect(result).toBeInstanceOf(UrlTree);
    expect(router.serializeUrl(result as UrlTree)).toBe('/login');
  });
});
