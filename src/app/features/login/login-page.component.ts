import { Component, OnDestroy, signal } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { inject } from '@angular/core';
import { AuthService, AuthErrorCode } from '../../core/auth.service';
import { loadTurnstileScript } from '../../core/turnstile-loader';
import { TURNSTILE_SITE_KEY } from '../../core/turnstile.config';

type Step = 'email' | 'credential' | 'loggedIn';
type CredentialTab = 'code' | 'password';

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
      } @else if (step() === 'credential') {
        <div data-testid="credential-step" class="flex w-full max-w-sm flex-col gap-4">
          <div role="tablist" class="flex gap-2">
            <button
              type="button"
              role="tab"
              id="tab-code"
              aria-controls="panel-code"
              [attr.aria-selected]="activeTab() === 'code'"
              (click)="selectTab('code')"
              (keydown)="onTabKeydown($event)"
            >
              {{ 'login.codeTab' | transloco }}
            </button>
            <button
              type="button"
              role="tab"
              id="tab-password"
              aria-controls="panel-password"
              [attr.aria-selected]="activeTab() === 'password'"
              (click)="selectTab('password')"
              (keydown)="onTabKeydown($event)"
            >
              {{ 'login.passwordTab' | transloco }}
            </button>
          </div>

          @if (activeTab() === 'code') {
            <form
              id="panel-code"
              role="tabpanel"
              aria-labelledby="tab-code"
              (submit)="onSubmitCode($event)"
            >
              <label for="code">{{ 'login.codeLabel' | transloco }}</label>
              <input
                id="code"
                name="code"
                type="text"
                required
                [value]="code()"
                (input)="code.set($any($event.target).value)"
              />
              <button type="submit" [disabled]="submitting()">
                {{ 'login.continue' | transloco }}
              </button>
            </form>
          } @else {
            <form
              id="panel-password"
              role="tabpanel"
              aria-labelledby="tab-password"
              (submit)="onSubmitPassword($event)"
            >
              <label for="password">{{ 'login.passwordLabel' | transloco }}</label>
              <input
                id="password"
                name="password"
                type="password"
                required
                [value]="password()"
                (input)="password.set($any($event.target).value)"
              />
              <button type="submit" [disabled]="submitting()">
                {{ 'login.continue' | transloco }}
              </button>
            </form>
          }
        </div>
      } @else {
        <div data-testid="logged-in"></div>
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

  protected readonly activeTab = signal<CredentialTab>('code');
  protected readonly code = signal('');
  protected readonly password = signal('');

  ngOnDestroy(): void {
    delete (window as unknown as Record<string, unknown>)[this.callbackName];
  }

  selectTab(tab: CredentialTab): void {
    this.activeTab.set(tab);
    this.errorCode.set(undefined);
  }

  onTabKeydown(event: KeyboardEvent): void {
    if (event.key !== 'ArrowLeft' && event.key !== 'ArrowRight') {
      return;
    }

    event.preventDefault();
    this.selectTab(this.activeTab() === 'code' ? 'password' : 'code');
    (document.getElementById(`tab-${this.activeTab()}`) as HTMLElement | null)?.focus();
  }

  onSubmitCode(event: Event): void {
    event.preventDefault();
    this.submitting.set(true);
    this.errorCode.set(undefined);

    this.authService.verifyCode(this.email(), this.code(), this.captchaToken()).subscribe({
      next: () => {
        this.submitting.set(false);
        this.step.set('loggedIn');
      },
      error: (err: { error?: { code?: AuthErrorCode } }) => {
        this.submitting.set(false);
        this.errorCode.set(err.error?.code);
      },
    });
  }

  onSubmitPassword(event: Event): void {
    event.preventDefault();
    this.submitting.set(true);
    this.errorCode.set(undefined);

    this.authService.verifyPassword(this.email(), this.password(), this.captchaToken()).subscribe({
      next: () => {
        this.submitting.set(false);
        this.step.set('loggedIn');
      },
      error: (err: { error?: { code?: AuthErrorCode } }) => {
        this.submitting.set(false);
        this.errorCode.set(err.error?.code);
      },
    });
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
