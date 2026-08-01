import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { TranslocoService } from '@jsverse/transloco';

/**
 * Sets `Accept-Language` explicitly from the in-app active UI language
 * (`TranslocoService`), rather than relying on the browser/OS default —
 * a user can switch the app's language independently of their
 * browser/OS locale, and the backend resolves purely from this header
 * (see `deletion-confirmation-token`'s backend PLAN).
 */
export const localeInterceptor: HttpInterceptorFn = (request, next) => {
  const transloco = inject(TranslocoService);

  return next(
    request.clone({
      setHeaders: { 'Accept-Language': transloco.getActiveLang() },
    }),
  );
};
