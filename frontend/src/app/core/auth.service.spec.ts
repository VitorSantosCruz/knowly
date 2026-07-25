import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { AuthService } from './auth.service';

describe('AuthService', () => {
  let service: AuthService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AuthService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  describe('requestLogin', () => {
    it('posts the email and resolves on 200', () => {
      let resolved = false;
      service.requestLogin('user@example.com').subscribe(() => (resolved = true));

      const req = httpMock.expectOne('/api/auth/login-request');
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({ email: 'user@example.com', captchaToken: undefined });
      req.flush({});

      expect(resolved).toBe(true);
    });

    it('includes the captcha token when provided', () => {
      service.requestLogin('user@example.com', 'token-123').subscribe();

      const req = httpMock.expectOne('/api/auth/login-request');
      expect(req.request.body).toEqual({ email: 'user@example.com', captchaToken: 'token-123' });
      req.flush({});
    });

    it('propagates the CAPTCHA_REQUIRED error', () => {
      let errorCode: string | undefined;
      service.requestLogin('user@example.com').subscribe({
        error: (err: HttpErrorResponse) => (errorCode = err.error.code),
      });

      const req = httpMock.expectOne('/api/auth/login-request');
      req.flush({ code: 'CAPTCHA_REQUIRED' }, { status: 400, statusText: 'Bad Request' });

      expect(errorCode).toBe('CAPTCHA_REQUIRED');
    });
  });

  describe('verifyCode', () => {
    it('posts the email and code and resolves on 200', () => {
      let resolved = false;
      service.verifyCode('user@example.com', '123456').subscribe(() => (resolved = true));

      const req = httpMock.expectOne('/api/auth/login-code/verify');
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({
        email: 'user@example.com',
        code: '123456',
        captchaToken: undefined,
      });
      req.flush({});

      expect(resolved).toBe(true);
    });

    it('propagates INVALID_CREDENTIALS', () => {
      let errorCode: string | undefined;
      service.verifyCode('user@example.com', '000000').subscribe({
        error: (err: HttpErrorResponse) => (errorCode = err.error.code),
      });

      const req = httpMock.expectOne('/api/auth/login-code/verify');
      req.flush({ code: 'INVALID_CREDENTIALS' }, { status: 401, statusText: 'Unauthorized' });

      expect(errorCode).toBe('INVALID_CREDENTIALS');
    });

    it('propagates ACCOUNT_LOCKED', () => {
      let errorCode: string | undefined;
      service.verifyCode('user@example.com', '000000').subscribe({
        error: (err: HttpErrorResponse) => (errorCode = err.error.code),
      });

      const req = httpMock.expectOne('/api/auth/login-code/verify');
      req.flush({ code: 'ACCOUNT_LOCKED' }, { status: 429, statusText: 'Too Many Requests' });

      expect(errorCode).toBe('ACCOUNT_LOCKED');
    });
  });

  describe('verifyPassword', () => {
    it('posts the email and password and resolves on 200', () => {
      let resolved = false;
      service.verifyPassword('user@example.com', 'abc123456789').subscribe(() => (resolved = true));

      const req = httpMock.expectOne('/api/auth/login-password/verify');
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({
        email: 'user@example.com',
        password: 'abc123456789',
        captchaToken: undefined,
      });
      req.flush({});

      expect(resolved).toBe(true);
    });

    it('propagates INVALID_CREDENTIALS', () => {
      let errorCode: string | undefined;
      service.verifyPassword('user@example.com', 'wrong').subscribe({
        error: (err: HttpErrorResponse) => (errorCode = err.error.code),
      });

      const req = httpMock.expectOne('/api/auth/login-password/verify');
      req.flush({ code: 'INVALID_CREDENTIALS' }, { status: 401, statusText: 'Unauthorized' });

      expect(errorCode).toBe('INVALID_CREDENTIALS');
    });
  });

  describe('isLoggedIn', () => {
    it('is false initially', () => {
      expect(service.isLoggedIn()).toBe(false);
    });

    it('becomes true after a successful verifyCode', () => {
      service.verifyCode('user@example.com', '123456').subscribe();
      httpMock.expectOne('/api/auth/login-code/verify').flush({});

      expect(service.isLoggedIn()).toBe(true);
    });

    it('becomes true after a successful verifyPassword', () => {
      service.verifyPassword('user@example.com', 'abc123456789').subscribe();
      httpMock.expectOne('/api/auth/login-password/verify').flush({});

      expect(service.isLoggedIn()).toBe(true);
    });

    it('stays false when verifyCode fails', () => {
      service.verifyCode('user@example.com', '000000').subscribe({ error: () => {} });
      httpMock
        .expectOne('/api/auth/login-code/verify')
        .flush({ code: 'INVALID_CREDENTIALS' }, { status: 401, statusText: 'Unauthorized' });

      expect(service.isLoggedIn()).toBe(false);
    });
  });

  describe('checkSession', () => {
    it('resolves true and sets isLoggedIn when the session is valid', () => {
      let result: boolean | undefined;
      service.checkSession().subscribe((value) => (result = value));

      httpMock.expectOne('/api/staff/permissions').flush({ permissions: [] });

      expect(result).toBe(true);
      expect(service.isLoggedIn()).toBe(true);
    });

    it('resolves false and clears isLoggedIn when there is no valid session', () => {
      let result: boolean | undefined;
      service.checkSession().subscribe((value) => (result = value));

      httpMock
        .expectOne('/api/staff/permissions')
        .flush({}, { status: 401, statusText: 'Unauthorized' });

      expect(result).toBe(false);
      expect(service.isLoggedIn()).toBe(false);
    });
  });

  describe('logout', () => {
    it('posts to /api/auth/logout and clears isLoggedIn', () => {
      service.verifyCode('user@example.com', '123456').subscribe();
      httpMock.expectOne('/api/auth/login-code/verify').flush({});
      expect(service.isLoggedIn()).toBe(true);

      let resolved = false;
      service.logout().subscribe(() => (resolved = true));

      const req = httpMock.expectOne('/api/auth/logout');
      expect(req.request.method).toBe('POST');
      req.flush({});

      expect(resolved).toBe(true);
      expect(service.isLoggedIn()).toBe(false);
    });

    it('clears isLoggedIn even if the request fails', () => {
      service.verifyCode('user@example.com', '123456').subscribe();
      httpMock.expectOne('/api/auth/login-code/verify').flush({});

      service.logout().subscribe({ error: () => {} });
      httpMock
        .expectOne('/api/auth/logout')
        .flush({}, { status: 500, statusText: 'Internal Server Error' });

      expect(service.isLoggedIn()).toBe(false);
    });
  });
});
