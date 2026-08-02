import { HttpErrorResponse, HttpHandlerFn, HttpRequest } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { throwError, of } from 'rxjs';
import { authInterceptor } from './auth.interceptor';

describe('authInterceptor', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([])],
    });
  });

  it('redirects to /login on a 401 response', () => {
    const router = TestBed.inject(Router);
    vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
    const request = new HttpRequest('GET', '/api/tenants/memberships');
    const next: HttpHandlerFn = () =>
      throwError(
        () => new HttpErrorResponse({ status: 401, url: '/api/tenants/memberships' }),
      ) as never;

    TestBed.runInInjectionContext(() => {
      authInterceptor(request, next).subscribe({
        error: () => {
          /* noop */
        },
      });
    });

    expect(router.navigateByUrl).toHaveBeenCalledWith('/login');
  });

  it('does not redirect on a non-401 response', () => {
    const router = TestBed.inject(Router);
    vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
    const request = new HttpRequest('GET', '/api/tenants/memberships');
    const next: HttpHandlerFn = () =>
      throwError(
        () => new HttpErrorResponse({ status: 500, url: '/api/tenants/memberships' }),
      ) as never;

    TestBed.runInInjectionContext(() => {
      authInterceptor(request, next).subscribe({
        error: () => {
          /* noop */
        },
      });
    });

    expect(router.navigateByUrl).not.toHaveBeenCalled();
  });

  it('redirects to /complete-profile on a 409 PROFILE_COMPLETION_REQUIRED response', () => {
    const router = TestBed.inject(Router);
    vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
    const request = new HttpRequest('GET', '/api/tenants/active');
    const next: HttpHandlerFn = () =>
      throwError(
        () =>
          new HttpErrorResponse({
            status: 409,
            url: '/api/tenants/active',
            error: { code: 'PROFILE_COMPLETION_REQUIRED' },
          }),
      ) as never;

    TestBed.runInInjectionContext(() => {
      authInterceptor(request, next).subscribe({
        error: () => {
          /* noop */
        },
      });
    });

    expect(router.navigateByUrl).toHaveBeenCalledWith('/complete-profile');
  });

  it('does not redirect on a 409 response with a different code', () => {
    const router = TestBed.inject(Router);
    vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
    const request = new HttpRequest('GET', '/api/tenants/active');
    const next: HttpHandlerFn = () =>
      throwError(
        () =>
          new HttpErrorResponse({
            status: 409,
            url: '/api/tenants/active',
            error: { code: 'TENANT_SELECTION_REQUIRED' },
          }),
      ) as never;

    TestBed.runInInjectionContext(() => {
      authInterceptor(request, next).subscribe({
        error: () => {
          /* noop */
        },
      });
    });

    expect(router.navigateByUrl).not.toHaveBeenCalled();
  });

  it('passes through successful responses untouched', () => {
    const router = TestBed.inject(Router);
    vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
    const request = new HttpRequest('GET', '/api/tenants/memberships');
    const next: HttpHandlerFn = () => of({ ok: true }) as never;

    TestBed.runInInjectionContext(() => {
      authInterceptor(request, next).subscribe();
    });

    expect(router.navigateByUrl).not.toHaveBeenCalled();
  });
});
