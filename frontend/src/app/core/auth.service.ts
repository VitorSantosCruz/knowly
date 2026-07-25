import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, tap, finalize, map, catchError, of } from 'rxjs';

export type AuthErrorCode = 'CAPTCHA_REQUIRED' | 'INVALID_CREDENTIALS' | 'ACCOUNT_LOCKED';

export interface AuthErrorBody {
  code: AuthErrorCode;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly loggedIn = signal(false);

  readonly isLoggedIn = this.loggedIn.asReadonly();

  requestLogin(email: string, captchaToken?: string): Observable<void> {
    return this.http.post<void>('/api/auth/login-request', { email, captchaToken });
  }

  verifyCode(email: string, code: string, captchaToken?: string): Observable<void> {
    return this.http
      .post<void>('/api/auth/login-code/verify', { email, code, captchaToken })
      .pipe(tap(() => this.loggedIn.set(true)));
  }

  verifyPassword(email: string, password: string, captchaToken?: string): Observable<void> {
    return this.http
      .post<void>('/api/auth/login-password/verify', { email, password, captchaToken })
      .pipe(tap(() => this.loggedIn.set(true)));
  }

  logout(): Observable<void> {
    return this.http
      .post<void>('/api/auth/logout', {})
      .pipe(finalize(() => this.loggedIn.set(false)));
  }

  /**
   * Resyncs `isLoggedIn` against the real session (the signal only reflects this tab's
   * in-memory state, so it reads as false after a page reload even with a still-valid session
   * cookie). Calls a lightweight endpoint that succeeds for any authenticated user and 401s
   * otherwise; never throws — callers get a definitive true/false either way.
   */
  checkSession(): Observable<boolean> {
    return this.http.get('/api/staff/permissions').pipe(
      map(() => {
        this.loggedIn.set(true);
        return true;
      }),
      catchError(() => {
        this.loggedIn.set(false);
        return of(false);
      }),
    );
  }
}
