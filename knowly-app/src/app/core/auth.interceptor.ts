import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';

export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const router = inject(Router);

  return next(request).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse && error.status === 401) {
        router.navigateByUrl('/login');
      }

      if (
        error instanceof HttpErrorResponse &&
        error.status === 409 &&
        error.error?.code === 'PROFILE_COMPLETION_REQUIRED'
      ) {
        router.navigateByUrl('/profile');
      }

      return throwError(() => error);
    }),
  );
};
