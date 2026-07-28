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

function fillOtpBoxes(fixture: ReturnType<typeof setup>, code: string) {
  const boxes: NodeListOf<HTMLInputElement> =
    fixture.nativeElement.querySelectorAll('input[data-otp-index]');
  for (let i = 0; i < code.length && i < boxes.length; i++) {
    boxes[i].value = code[i];
    boxes[i].dispatchEvent(new Event('input'));
  }
  fixture.detectChanges();
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

    it('shows the boxed code input on the Code tab by default', () => {
      const fixture = setup();
      goToCredentialStep(fixture);

      expect(fixture.nativeElement.querySelectorAll('input[data-otp-index]').length).toBe(6);
      expect(fixture.nativeElement.querySelector('input[name="code"]')).toBeFalsy();
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
      expect(fixture.nativeElement.querySelectorAll('input[data-otp-index]').length).toBe(0);
    });

    it('logs the user in when the code is correct', () => {
      const fixture = setup();
      goToCredentialStep(fixture);
      const authService = TestBed.inject(AuthService);
      const router = TestBed.inject(Router);
      vi.spyOn(authService, 'verifyCode').mockReturnValue(of(undefined));
      vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);

      fillOtpBoxes(fixture, '123456');

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

      fillOtpBoxes(fixture, '000000');

      const form: HTMLFormElement = fixture.nativeElement.querySelector(
        '[data-testid="credential-step"] form',
      );
      form.dispatchEvent(new Event('submit'));
      fixture.detectChanges();

      const tooltip: HTMLElement = fixture.nativeElement.querySelector('[role="alert"]');
      expect(tooltip).toBeTruthy();
      expect(tooltip.textContent).not.toContain('locked');
      const boxes: NodeListOf<HTMLInputElement> =
        fixture.nativeElement.querySelectorAll('input[data-otp-index]');
      expect(
        Array.from(boxes)
          .map((box) => box.value)
          .join(''),
      ).toBe('000000');
    });

    it('shows a distinct account-locked tooltip', () => {
      const fixture = setup();
      goToCredentialStep(fixture);
      const authService = TestBed.inject(AuthService);
      vi.spyOn(authService, 'verifyCode').mockReturnValue(
        throwError(() => new HttpErrorResponse({ status: 429, error: { code: 'ACCOUNT_LOCKED' } })),
      );

      fillOtpBoxes(fixture, '000000');

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

      const form: HTMLFormElement = fixture.nativeElement.querySelector(
        '[data-testid="credential-step"] form',
      );
      form.dispatchEvent(new Event('submit'));
      fixture.detectChanges();

      const tooltip: HTMLElement = fixture.nativeElement.querySelector('[role="alert"]');
      const boxes: NodeListOf<HTMLInputElement> =
        fixture.nativeElement.querySelectorAll('input[data-otp-index]');
      boxes.forEach((box) => {
        expect(box.getAttribute('aria-describedby')).toBe(tooltip.id);
      });
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

    describe('boxed OTP input', () => {
      function boxes(fixture: ReturnType<typeof setup>): NodeListOf<HTMLInputElement> {
        return fixture.nativeElement.querySelectorAll('input[data-otp-index]');
      }

      it('renders six digit boxes and no legacy single input', () => {
        const fixture = setup();
        goToCredentialStep(fixture);

        expect(boxes(fixture).length).toBe(6);
        expect(fixture.nativeElement.querySelector('input[name="code"]')).toBeFalsy();
      });

      it('advances focus to the next box when a digit is typed', () => {
        const fixture = setup();
        goToCredentialStep(fixture);
        const box0 = boxes(fixture)[0];

        box0.value = '1';
        box0.dispatchEvent(new Event('input'));
        fixture.detectChanges();

        expect(box0.value).toBe('1');
        expect(document.activeElement).toBe(boxes(fixture)[1]);
      });

      it('rejects a non-digit keystroke and does not advance focus', () => {
        const fixture = setup();
        goToCredentialStep(fixture);
        const box0 = boxes(fixture)[0];
        box0.focus();

        const event = new KeyboardEvent('keydown', { key: 'a', cancelable: true });
        box0.dispatchEvent(event);
        fixture.detectChanges();

        expect(event.defaultPrevented).toBe(true);
        expect(box0.value).toBe('');
        expect(document.activeElement).toBe(box0);
      });

      it('moves focus back and clears the previous box on Backspace from an empty box', () => {
        const fixture = setup();
        goToCredentialStep(fixture);
        const [box0, box1] = boxes(fixture);

        box0.value = '1';
        box0.dispatchEvent(new Event('input'));
        fixture.detectChanges();
        box1.focus();

        box1.dispatchEvent(new KeyboardEvent('keydown', { key: 'Backspace', cancelable: true }));
        fixture.detectChanges();

        expect(document.activeElement).toBe(box0);
        expect(box0.value).toBe('');
      });

      it('distributes a pasted 6-digit code across all boxes and focuses the last box', () => {
        const fixture = setup();
        goToCredentialStep(fixture);
        const group: HTMLElement = fixture.nativeElement.querySelector('[role="group"]');

        const clipboardData = { getData: () => '12-3456 extra' };
        const event = Object.assign(new Event('paste', { cancelable: true }), { clipboardData });
        group.dispatchEvent(event);
        fixture.detectChanges();

        const allBoxes = boxes(fixture);
        expect(
          Array.from(allBoxes)
            .map((box) => box.value)
            .join(''),
        ).toBe('123456');
        expect(document.activeElement).toBe(allBoxes[5]);
      });

      it('distributes a partial paste and focuses the box after the last filled digit', () => {
        const fixture = setup();
        goToCredentialStep(fixture);
        const group: HTMLElement = fixture.nativeElement.querySelector('[role="group"]');

        const clipboardData = { getData: () => '123' };
        const event = Object.assign(new Event('paste', { cancelable: true }), { clipboardData });
        group.dispatchEvent(event);
        fixture.detectChanges();

        const allBoxes = boxes(fixture);
        expect(
          Array.from(allBoxes)
            .map((box) => box.value)
            .join(''),
        ).toBe('123');
        expect(document.activeElement).toBe(allBoxes[3]);
      });

      it('moves focus with ArrowLeft/ArrowRight between boxes', () => {
        const fixture = setup();
        goToCredentialStep(fixture);
        const allBoxes = boxes(fixture);
        allBoxes[2].focus();

        allBoxes[2].dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight' }));
        fixture.detectChanges();
        expect(document.activeElement).toBe(allBoxes[3]);

        allBoxes[3].dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowLeft' }));
        fixture.detectChanges();
        expect(document.activeElement).toBe(allBoxes[2]);
      });

      it('prevents submission when a box is left empty', () => {
        const fixture = setup();
        goToCredentialStep(fixture);
        const authService = TestBed.inject(AuthService);
        vi.spyOn(authService, 'verifyCode').mockReturnValue(of(undefined));

        fillOtpBoxes(fixture, '12345');

        const button: HTMLButtonElement = fixture.nativeElement.querySelector(
          '[data-testid="credential-step"] form button[type="submit"]',
        );
        button.click();
        fixture.detectChanges();

        expect(authService.verifyCode).not.toHaveBeenCalled();
      });
    });
  });
});
