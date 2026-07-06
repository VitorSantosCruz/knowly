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
    <div class="flex min-h-dvh items-center justify-center p-4 sm:p-6">
      @if (step() === 'email') {
        <form [class]="cardClass" (submit)="onSubmitEmail($event)">
          <div class="mb-8 text-center">
            <h1 class="text-2xl font-bold tracking-tight text-slate-900 dark:text-white">
              {{ 'login.title' | transloco }}
            </h1>
            <p class="mt-1 text-sm text-slate-500 dark:text-slate-400">
              {{ 'login.subtitle' | transloco }}
            </p>
          </div>
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
              class="cf-turnstile mb-6"
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
        <div data-testid="credential-step" [class]="cardClass">
          <div role="tablist" class="mb-8 flex gap-1 rounded-xl bg-slate-100 p-1 dark:bg-slate-800">
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
                  [class]="errorClass"
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
                  [class]="errorClass"
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

  protected readonly cardClass =
    'w-full max-w-sm rounded-2xl border border-slate-200 bg-white p-8 shadow-lg shadow-slate-200/60 dark:border-slate-800 dark:bg-slate-900 dark:shadow-none';
  protected readonly labelClass =
    'mb-2 block text-sm font-medium text-slate-700 dark:text-slate-300';
  protected readonly inputClass =
    'mb-6 w-full rounded-xl border border-slate-300 bg-white px-4 py-2.5 text-sm text-slate-900 placeholder-slate-400 shadow-sm transition focus:border-indigo-500 focus:ring-2 focus:ring-indigo-500/20 focus:outline-none disabled:cursor-not-allowed disabled:bg-slate-50 disabled:text-slate-400 dark:border-slate-700 dark:bg-slate-800 dark:text-slate-100 dark:placeholder-slate-500 dark:disabled:bg-slate-900';
  protected readonly buttonClass =
    'w-full rounded-xl bg-indigo-600 px-4 py-2.5 text-sm font-semibold text-white shadow-sm shadow-indigo-600/20 transition hover:bg-indigo-700 active:bg-indigo-800 disabled:cursor-not-allowed disabled:bg-slate-300 disabled:text-slate-500 disabled:shadow-none dark:bg-indigo-500 dark:hover:bg-indigo-400 dark:disabled:bg-slate-700';
  protected readonly errorClass =
    'mb-6 rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700 dark:border-red-900/50 dark:bg-red-950/30 dark:text-red-400';

  ngOnDestroy(): void {
    delete (window as unknown as Record<string, unknown>)[this.callbackName];
  }

  tabClass(tab: CredentialTab): string {
    const base = 'flex-1 rounded-lg px-3 py-1.5 text-sm font-medium transition';
    return this.activeTab() === tab
      ? `${base} bg-white text-slate-900 shadow-sm dark:bg-slate-700 dark:text-white`
      : `${base} text-slate-500 hover:text-slate-700 dark:text-slate-400 dark:hover:text-slate-200`;
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
