import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { Injectable } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { TranslocoLoader, provideTransloco } from '@jsverse/transloco';
import { of, throwError } from 'rxjs';
import { LoginPageComponent } from './login-page.component';
import { AuthService } from '../../core/auth.service';

@Injectable()
class FakeTranslocoLoader implements TranslocoLoader {
  getTranslation() {
    return of({});
  }
}

function setup() {
  TestBed.configureTestingModule({
    imports: [LoginPageComponent],
    providers: [
      provideHttpClient(),
      provideHttpClientTesting(),
      provideTransloco({
        config: { availableLangs: ['en', 'pt-BR'], defaultLang: 'en' },
        loader: FakeTranslocoLoader,
      }),
    ],
  });

  const fixture = TestBed.createComponent(LoginPageComponent);
  fixture.detectChanges();
  return fixture;
}

function submitEmail(fixture: ReturnType<typeof setup>, email: string) {
  const input: HTMLInputElement = fixture.nativeElement.querySelector('input[type="email"]');
  input.value = email;
  input.dispatchEvent(new Event('input'));
  fixture.detectChanges();

  const form: HTMLFormElement = fixture.nativeElement.querySelector('form');
  form.dispatchEvent(new Event('submit'));
  fixture.detectChanges();
}

describe('LoginPageComponent', () => {
  it('renders the email step with a centered email input as the primary action', () => {
    const fixture = setup();

    const input: HTMLInputElement = fixture.nativeElement.querySelector('input[type="email"]');
    expect(input).toBeTruthy();
  });

  it('navigates to the credential step when the email is submitted successfully', () => {
    const fixture = setup();
    const authService = TestBed.inject(AuthService);
    vi.spyOn(authService, 'requestLogin').mockReturnValue(of(undefined));

    submitEmail(fixture, 'user@example.com');

    expect(authService.requestLogin).toHaveBeenCalledWith('user@example.com', undefined);
    expect(fixture.nativeElement.querySelector('[data-testid="credential-step"]')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('input[type="email"]')).toBeFalsy();
  });

  it('stays on the email step when the request fails for a reason other than CAPTCHA_REQUIRED', () => {
    const fixture = setup();
    const authService = TestBed.inject(AuthService);
    vi.spyOn(authService, 'requestLogin').mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 500 })),
    );

    submitEmail(fixture, 'user@example.com');

    expect(fixture.nativeElement.querySelector('input[type="email"]')).toBeTruthy();
  });

  it('renders the Turnstile widget when the backend requires a CAPTCHA', () => {
    const fixture = setup();
    const authService = TestBed.inject(AuthService);
    vi.spyOn(authService, 'requestLogin').mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 400, error: { code: 'CAPTCHA_REQUIRED' } })),
    );

    submitEmail(fixture, 'user@example.com');

    const widget: HTMLElement = fixture.nativeElement.querySelector('.cf-turnstile');
    expect(widget).toBeTruthy();
    expect(widget.getAttribute('data-sitekey')).toBeDefined();
  });

  it('keeps the submit button disabled until the CAPTCHA is solved', () => {
    const fixture = setup();
    const authService = TestBed.inject(AuthService);
    vi.spyOn(authService, 'requestLogin').mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 400, error: { code: 'CAPTCHA_REQUIRED' } })),
    );

    submitEmail(fixture, 'user@example.com');

    const button: HTMLButtonElement = fixture.nativeElement.querySelector('button[type="submit"]');
    expect(button.disabled).toBe(true);

    const widget: HTMLElement = fixture.nativeElement.querySelector('.cf-turnstile');
    const callbackName = widget.getAttribute('data-callback')!;
    (window as unknown as Record<string, (token: string) => void>)[callbackName]('solved-token');
    fixture.detectChanges();

    expect(button.disabled).toBe(false);
  });

  it('resubmits with the solved CAPTCHA token', () => {
    const fixture = setup();
    const authService = TestBed.inject(AuthService);
    vi.spyOn(authService, 'requestLogin').mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 400, error: { code: 'CAPTCHA_REQUIRED' } })),
    );

    submitEmail(fixture, 'user@example.com');

    const widget: HTMLElement = fixture.nativeElement.querySelector('.cf-turnstile');
    const callbackName = widget.getAttribute('data-callback')!;
    (window as unknown as Record<string, (token: string) => void>)[callbackName]('solved-token');
    fixture.detectChanges();

    vi.spyOn(authService, 'requestLogin').mockReturnValue(of(undefined));
    const form: HTMLFormElement = fixture.nativeElement.querySelector('form');
    form.dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    expect(authService.requestLogin).toHaveBeenCalledWith('user@example.com', 'solved-token');
  });
});
