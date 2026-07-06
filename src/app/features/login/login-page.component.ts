import { Component, OnDestroy, signal } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { inject } from '@angular/core';
import { AuthService, AuthErrorCode } from '../../core/auth.service';
import { loadTurnstileScript } from '../../core/turnstile-loader';
import { TURNSTILE_SITE_KEY } from '../../core/turnstile.config';

type Step = 'email' | 'credential';

@Component({
  selector: 'app-login-page',
  imports: [TranslocoPipe],
  template: `
    <div class="flex min-h-dvh items-center justify-center">
      @if (step() === 'email') {
        <form class="flex w-full max-w-sm flex-col gap-4" (submit)="onSubmitEmail($event)">
          <label for="email">{{ 'login.emailLabel' | transloco }}</label>
          <input
            id="email"
            name="email"
            type="email"
            required
            [value]="email()"
            (input)="email.set($any($event.target).value)"
            placeholder="{{ 'login.emailPlaceholder' | transloco }}"
          />
          @if (captchaRequired()) {
            <div
              class="cf-turnstile"
              [attr.data-sitekey]="turnstileSiteKey"
              [attr.data-callback]="callbackName"
            ></div>
          }
          <button type="submit" [disabled]="submitting() || (captchaRequired() && !captchaToken())">
            {{ 'login.continue' | transloco }}
          </button>
        </form>
      } @else {
        <div data-testid="credential-step"></div>
      }
    </div>
  `,
})
export class LoginPageComponent implements OnDestroy {
  private readonly authService = inject(AuthService);

  protected readonly turnstileSiteKey = TURNSTILE_SITE_KEY;
  protected readonly callbackName = `onTurnstileVerified_${crypto.randomUUID().replaceAll('-', '')}`;

  protected readonly step = signal<Step>('email');
  protected readonly email = signal('');
  protected readonly submitting = signal(false);
  protected readonly errorCode = signal<AuthErrorCode | undefined>(undefined);
  protected readonly captchaRequired = signal(false);
  protected readonly captchaToken = signal<string | undefined>(undefined);

  ngOnDestroy(): void {
    delete (window as unknown as Record<string, unknown>)[this.callbackName];
  }

  onSubmitEmail(event: Event): void {
    event.preventDefault();
    this.submitting.set(true);
    this.errorCode.set(undefined);

    this.authService.requestLogin(this.email(), this.captchaToken()).subscribe({
      next: () => {
        this.submitting.set(false);
        this.step.set('credential');
      },
      error: (err: { error?: { code?: AuthErrorCode } }) => {
        this.submitting.set(false);
        const code = err.error?.code;
        this.errorCode.set(code);

        if (code === 'CAPTCHA_REQUIRED' && !this.captchaRequired()) {
          this.captchaRequired.set(true);
          (window as unknown as Record<string, (token: string) => void>)[this.callbackName] = (
            token: string,
          ) => this.captchaToken.set(token);
          loadTurnstileScript();
        }
      },
    });
  }
}
