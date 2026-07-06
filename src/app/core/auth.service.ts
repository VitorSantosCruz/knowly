import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export type AuthErrorCode = 'CAPTCHA_REQUIRED' | 'INVALID_CREDENTIALS' | 'ACCOUNT_LOCKED';

export interface AuthErrorBody {
  code: AuthErrorCode;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);

  requestLogin(email: string, captchaToken?: string): Observable<void> {
    return this.http.post<void>('/api/auth/login-request', { email, captchaToken });
  }

  verifyCode(email: string, code: string, captchaToken?: string): Observable<void> {
    return this.http.post<void>('/api/auth/login-code/verify', { email, code, captchaToken });
  }

  verifyPassword(email: string, password: string, captchaToken?: string): Observable<void> {
    return this.http.post<void>('/api/auth/login-password/verify', {
      email,
      password,
      captchaToken,
    });
  }
}
