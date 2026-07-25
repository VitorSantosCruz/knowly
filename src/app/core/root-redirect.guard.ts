import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map } from 'rxjs';
import { AuthService } from './auth.service';

/**
 * The '' route used to redirect straight to /login unconditionally, sending an
 * already-logged-in user (e.g. reopening the app after a reload) back to the login
 * screen instead of the dashboard — isLoggedIn() alone can't decide this since it's
 * only in-memory and reads false on a fresh page load even with a valid session
 * cookie, so this checks the real session via AuthService#checkSession().
 */
export const rootRedirectGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const router = inject(Router);

  return authService
    .checkSession()
    .pipe(map((loggedIn) => router.parseUrl(loggedIn ? '/welcome' : '/login')));
};
