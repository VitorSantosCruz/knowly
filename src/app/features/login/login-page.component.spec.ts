import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { provideTransloco } from '@jsverse/transloco';
import { of, throwError } from 'rxjs';
import { LoginPageComponent } from './login-page.component';
import { AuthService } from '../../core/auth.service';
import { FakeTranslocoLoader } from '../../testing/fake-transloco-loader';

function setup() {
  TestBed.configureTestingModule({
    imports: [LoginPageComponent],
    providers: [
      provideHttpClient(),
      provideHttpClientTesting(),
      provideRouter([]),
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

  describe('credential step', () => {
    function goToCredentialStep(fixture: ReturnType<typeof setup>) {
      const authService = TestBed.inject(AuthService);
      vi.spyOn(authService, 'requestLogin').mockReturnValue(of(undefined));
      submitEmail(fixture, 'user@example.com');
    }

    it('renders Code and Password tabs', () => {
      const fixture = setup();
      goToCredentialStep(fixture);

      const tabs: NodeListOf<HTMLElement> = fixture.nativeElement.querySelectorAll('[role="tab"]');
      expect(tabs.length).toBe(2);
      expect(tabs[0].textContent).toContain('Code');
      expect(tabs[1].textContent).toContain('Password');
    });

    it('shows the code input on the Code tab by default', () => {
      const fixture = setup();
      goToCredentialStep(fixture);

      expect(fixture.nativeElement.querySelector('input[name="code"]')).toBeTruthy();
      expect(fixture.nativeElement.querySelector('input[name="password"]')).toBeFalsy();
    });

    it('shows the password input when the Password tab is selected', () => {
      const fixture = setup();
      goToCredentialStep(fixture);

      const tabs: NodeListOf<HTMLButtonElement> =
        fixture.nativeElement.querySelectorAll('[role="tab"]');
      tabs[1].click();
      fixture.detectChanges();

      expect(fixture.nativeElement.querySelector('input[name="password"]')).toBeTruthy();
      expect(fixture.nativeElement.querySelector('input[name="code"]')).toBeFalsy();
    });

    it('logs the user in when the code is correct', () => {
      const fixture = setup();
      goToCredentialStep(fixture);
      const authService = TestBed.inject(AuthService);
      const router = TestBed.inject(Router);
      vi.spyOn(authService, 'verifyCode').mockReturnValue(of(undefined));
      vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);

      const codeInput: HTMLInputElement = fixture.nativeElement.querySelector('input[name="code"]');
      codeInput.value = '123456';
      codeInput.dispatchEvent(new Event('input'));
      fixture.detectChanges();

      const form: HTMLFormElement = fixture.nativeElement.querySelector(
        '[data-testid="credential-step"] form',
      );
      form.dispatchEvent(new Event('submit'));
      fixture.detectChanges();

      expect(authService.verifyCode).toHaveBeenCalledWith('user@example.com', '123456', undefined);
      expect(router.navigateByUrl).toHaveBeenCalledWith('/welcome');
    });

    it('logs the user in when the password is correct', () => {
      const fixture = setup();
      goToCredentialStep(fixture);
      const authService = TestBed.inject(AuthService);
      const router = TestBed.inject(Router);
      vi.spyOn(authService, 'verifyPassword').mockReturnValue(of(undefined));
      vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);

      const tabs: NodeListOf<HTMLButtonElement> =
        fixture.nativeElement.querySelectorAll('[role="tab"]');
      tabs[1].click();
      fixture.detectChanges();

      const passwordInput: HTMLInputElement =
        fixture.nativeElement.querySelector('input[name="password"]');
      passwordInput.value = 'abc123456789';
      passwordInput.dispatchEvent(new Event('input'));
      fixture.detectChanges();

      const form: HTMLFormElement = fixture.nativeElement.querySelector(
        '[data-testid="credential-step"] form',
      );
      form.dispatchEvent(new Event('submit'));
      fixture.detectChanges();

      expect(authService.verifyPassword).toHaveBeenCalledWith(
        'user@example.com',
        'abc123456789',
        undefined,
      );
      expect(router.navigateByUrl).toHaveBeenCalledWith('/welcome');
    });

    it('shows an invalid-credentials tooltip without clearing the code input', () => {
      const fixture = setup();
      goToCredentialStep(fixture);
      const authService = TestBed.inject(AuthService);
      vi.spyOn(authService, 'verifyCode').mockReturnValue(
        throwError(
          () => new HttpErrorResponse({ status: 401, error: { code: 'INVALID_CREDENTIALS' } }),
        ),
      );

      const codeInput: HTMLInputElement = fixture.nativeElement.querySelector('input[name="code"]');
      codeInput.value = '000000';
      codeInput.dispatchEvent(new Event('input'));
      fixture.detectChanges();

      const form: HTMLFormElement = fixture.nativeElement.querySelector(
        '[data-testid="credential-step"] form',
      );
      form.dispatchEvent(new Event('submit'));
      fixture.detectChanges();

      const tooltip: HTMLElement = fixture.nativeElement.querySelector('[role="alert"]');
      expect(tooltip).toBeTruthy();
      expect(tooltip.textContent).not.toContain('locked');
      expect(codeInput.value).toBe('000000');
    });

    it('shows a distinct account-locked tooltip', () => {
      const fixture = setup();
      goToCredentialStep(fixture);
      const authService = TestBed.inject(AuthService);
      vi.spyOn(authService, 'verifyCode').mockReturnValue(
        throwError(() => new HttpErrorResponse({ status: 429, error: { code: 'ACCOUNT_LOCKED' } })),
      );

      const codeInput: HTMLInputElement = fixture.nativeElement.querySelector('input[name="code"]');
      codeInput.value = '000000';
      codeInput.dispatchEvent(new Event('input'));
      fixture.detectChanges();

      const form: HTMLFormElement = fixture.nativeElement.querySelector(
        '[data-testid="credential-step"] form',
      );
      form.dispatchEvent(new Event('submit'));
      fixture.detectChanges();

      const tooltip: HTMLElement = fixture.nativeElement.querySelector('[role="alert"]');
      expect(tooltip).toBeTruthy();
      expect(tooltip.getAttribute('data-error-code')).toBe('ACCOUNT_LOCKED');
    });

    it('associates the tooltip with the input via aria-describedby', () => {
      const fixture = setup();
      goToCredentialStep(fixture);
      const authService = TestBed.inject(AuthService);
      vi.spyOn(authService, 'verifyCode').mockReturnValue(
        throwError(
          () => new HttpErrorResponse({ status: 401, error: { code: 'INVALID_CREDENTIALS' } }),
        ),
      );

      const codeInput: HTMLInputElement = fixture.nativeElement.querySelector('input[name="code"]');
      const form: HTMLFormElement = fixture.nativeElement.querySelector(
        '[data-testid="credential-step"] form',
      );
      form.dispatchEvent(new Event('submit'));
      fixture.detectChanges();

      const tooltip: HTMLElement = fixture.nativeElement.querySelector('[role="alert"]');
      expect(codeInput.getAttribute('aria-describedby')).toBe(tooltip.id);
    });

    it('clears the tooltip when switching tabs', () => {
      const fixture = setup();
      goToCredentialStep(fixture);
      const authService = TestBed.inject(AuthService);
      vi.spyOn(authService, 'verifyCode').mockReturnValue(
        throwError(
          () => new HttpErrorResponse({ status: 401, error: { code: 'INVALID_CREDENTIALS' } }),
        ),
      );

      const form: HTMLFormElement = fixture.nativeElement.querySelector(
        '[data-testid="credential-step"] form',
      );
      form.dispatchEvent(new Event('submit'));
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelector('[role="alert"]')).toBeTruthy();

      const tabs: NodeListOf<HTMLButtonElement> =
        fixture.nativeElement.querySelectorAll('[role="tab"]');
      tabs[1].click();
      fixture.detectChanges();

      expect(fixture.nativeElement.querySelector('[role="alert"]')).toBeFalsy();
    });
  });
});
