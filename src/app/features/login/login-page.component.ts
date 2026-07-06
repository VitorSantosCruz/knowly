import { Component, OnDestroy, signal } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { inject } from '@angular/core';
import { AuthService, AuthErrorCode } from '../../core/auth.service';
import { ConfigService } from '../../core/config.service';
import { loadTurnstileScript } from '../../core/turnstile-loader';

type Step = 'email' | 'credential' | 'loggedIn';
type CredentialTab = 'code' | 'password';

@Component({
  selector: 'app-login-page',
  imports: [TranslocoPipe],
  template: `
    <div class="flex min-h-dvh items-center justify-center p-4">
      @if (step() === 'email') {
        <form
          class="w-full max-w-sm rounded-2xl border border-gray-200 bg-white p-8 shadow-sm dark:border-gray-800 dark:bg-gray-900"
          (submit)="onSubmitEmail($event)"
        >
          <label for="email" [class]="labelClass">{{ 'login.emailLabel' | transloco }}</label>
          <input
            id="email"
            name="email"
            type="email"
            required
            [value]="email()"
            (input)="email.set($any($event.target).value)"
            placeholder="{{ 'login.emailPlaceholder' | transloco }}"
            [class]="inputClass"
          />
          @if (captchaRequired()) {
            <div
              class="cf-turnstile mb-4"
              [attr.data-sitekey]="turnstileSiteKey"
              [attr.data-callback]="callbackName"
            ></div>
          }
          <button
            type="submit"
            [disabled]="submitting() || (captchaRequired() && !captchaToken())"
            [class]="buttonClass"
          >
            {{ 'login.continue' | transloco }}
          </button>
        </form>
      } @else if (step() === 'credential') {
        <div
          data-testid="credential-step"
          class="w-full max-w-sm rounded-2xl border border-gray-200 bg-white p-8 shadow-sm dark:border-gray-800 dark:bg-gray-900"
        >
          <div role="tablist" class="mb-6 flex gap-1 rounded-lg bg-gray-100 p-1 dark:bg-gray-800">
            <button
              type="button"
              role="tab"
              id="tab-code"
              aria-controls="panel-code"
              [attr.aria-selected]="activeTab() === 'code'"
              (click)="selectTab('code')"
              (keydown)="onTabKeydown($event)"
              [class]="tabClass('code')"
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
              [class]="tabClass('password')"
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
              <label for="code" [class]="labelClass">{{ 'login.codeLabel' | transloco }}</label>
              <input
                id="code"
                name="code"
                type="text"
                required
                [value]="code()"
                [attr.aria-describedby]="errorCode() ? 'credential-error' : null"
                (input)="code.set($any($event.target).value)"
                [class]="inputClass"
              />
              @if (errorCode(); as code) {
                <p
                  id="credential-error"
                  role="alert"
                  [attr.data-error-code]="code"
                  class="-mt-2 mb-4 text-sm text-red-600 dark:text-red-400"
                >
                  {{
                    (code === 'ACCOUNT_LOCKED' ? 'login.accountLocked' : 'login.invalidCredentials')
                      | transloco
                  }}
                </p>
              }
              <button type="submit" [disabled]="submitting()" [class]="buttonClass">
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
              <label for="password" [class]="labelClass">{{
                'login.passwordLabel' | transloco
              }}</label>
              <input
                id="password"
                name="password"
                type="password"
                required
                [value]="password()"
                [attr.aria-describedby]="errorCode() ? 'credential-error' : null"
                (input)="password.set($any($event.target).value)"
                [class]="inputClass"
              />
              @if (errorCode(); as code) {
                <p
                  id="credential-error"
                  role="alert"
                  [attr.data-error-code]="code"
                  class="-mt-2 mb-4 text-sm text-red-600 dark:text-red-400"
                >
                  {{
                    (code === 'ACCOUNT_LOCKED' ? 'login.accountLocked' : 'login.invalidCredentials')
                      | transloco
                  }}
                </p>
              }
              <button type="submit" [disabled]="submitting()" [class]="buttonClass">
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
  private readonly configService = inject(ConfigService);

  protected get turnstileSiteKey(): string {
    return this.configService.turnstileSiteKey;
  }
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

  protected readonly labelClass = 'mb-1 block text-sm font-medium text-gray-700 dark:text-gray-300';
  protected readonly inputClass =
    'mb-4 w-full rounded-md border border-gray-300 bg-white px-3 py-2 text-sm text-gray-900 placeholder-gray-400 focus:border-blue-500 focus:ring-1 focus:ring-blue-500 focus:outline-none dark:border-gray-700 dark:bg-gray-800 dark:text-gray-100';
  protected readonly buttonClass =
    'w-full rounded-md bg-blue-600 px-4 py-2 text-sm font-semibold text-white transition hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-50 dark:bg-blue-500 dark:hover:bg-blue-600';

  ngOnDestroy(): void {
    delete (window as unknown as Record<string, unknown>)[this.callbackName];
  }

  tabClass(tab: CredentialTab): string {
    const base = 'flex-1 rounded-md px-3 py-1.5 text-sm font-medium transition';
    return this.activeTab() === tab
      ? `${base} bg-white text-gray-900 shadow-sm dark:bg-gray-700 dark:text-gray-100`
      : `${base} text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-200`;
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
