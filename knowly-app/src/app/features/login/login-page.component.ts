import { Component, OnDestroy, computed, signal } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { buttonClass } from '../../shared/button-classes';
import { AuthService, AuthErrorCode } from '../../core/auth.service';
import { ConfigService } from '../../core/config.service';
import { loadTurnstileScript } from '../../core/turnstile-loader';
import { BrandWordmarkComponent } from '../../shared/brand-wordmark.component';

type Step = 'email' | 'credential';
type CredentialTab = 'code' | 'password';

@Component({
  selector: 'app-login-page',
  imports: [TranslocoPipe, BrandWordmarkComponent],
  template: `
    <div
      class="relative flex min-h-dvh items-center justify-center overflow-hidden bg-ink-50 p-4 dark:bg-ink-950 sm:p-6"
    >
      <div
        aria-hidden="true"
        class="pointer-events-none absolute -top-32 -left-32 h-96 w-96 rounded-full bg-signal-300/40 blur-3xl dark:bg-signal-800/30"
      ></div>
      <div
        aria-hidden="true"
        class="pointer-events-none absolute -right-24 bottom-0 h-80 w-80 rounded-full bg-ink-400/30 blur-3xl dark:bg-signal-900/40"
      ></div>
      @if (step() === 'email') {
        <form [class]="cardClass" class="enter-fluid" (submit)="onSubmitEmail($event)">
          <div
            aria-hidden="true"
            class="mb-6 h-1.5 w-16 rounded-full bg-gradient-to-r from-signal-500 to-ink-500"
          ></div>
          <div class="mb-8 text-center">
            <app-brand-wordmark class="mb-2 text-ink-900 dark:text-white" />
            <h1 class="sr-only">{{ 'login.title' | transloco }}</h1>
            <p class="mt-1 text-sm text-ink-500 dark:text-ink-400">
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
            [class]="submitButtonClass"
            [disabled]="submitting() || (captchaRequired() && !captchaToken())"
          >
            {{ 'login.continue' | transloco }}
          </button>
        </form>
      } @else if (step() === 'credential') {
        <div data-testid="credential-step" [class]="cardClass" class="enter-fluid">
          <div
            aria-hidden="true"
            class="mb-6 h-1.5 w-16 rounded-full bg-gradient-to-r from-signal-500 to-ink-500"
          ></div>
          <button
            type="button"
            (click)="onBackToEmail()"
            class="mb-4 text-sm font-medium text-ink-500 hover:text-ink-700 dark:text-ink-400 dark:hover:text-ink-200"
          >
            &larr; {{ 'login.notMyAccount' | transloco: { email: email() } }}
          </button>
          <div role="tablist" class="mb-8 flex gap-1 rounded-xl bg-ink-100 p-1 dark:bg-ink-800">
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
              <div
                role="group"
                [attr.aria-label]="'login.codeGroupLabel' | transloco"
                class="mb-6 flex justify-between gap-2"
                (paste)="onPaste($event)"
              >
                @for (i of otpIndexes; track i) {
                  <input
                    [id]="'otp-digit-' + i"
                    [attr.data-otp-index]="i"
                    type="text"
                    inputmode="numeric"
                    autocomplete="one-time-code"
                    maxlength="1"
                    required
                    [attr.aria-label]="
                      'login.codeDigitLabel' | transloco: { position: i + 1, total: 6 }
                    "
                    [attr.aria-describedby]="errorCode() ? 'credential-error' : null"
                    [value]="digits()[i]"
                    (keydown)="onDigitKeydown($event, i)"
                    (input)="onDigitInput($event, i)"
                    class="h-12 w-12 rounded-lg border border-ink-200 bg-white text-center text-lg text-ink-900 focus:border-signal-500 focus:ring-1 focus:ring-signal-500 focus:outline-none dark:border-ink-700 dark:bg-ink-800 dark:text-white"
                  />
                }
              </div>
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
              <button type="submit" [class]="submitButtonClass" [disabled]="submitting()">
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
              <button type="submit" [class]="submitButtonClass" [disabled]="submitting()">
                {{ 'login.continue' | transloco }}
              </button>
            </form>
          }
        </div>
      }
    </div>
  `,
})
export class LoginPageComponent implements OnDestroy {
  private readonly authService = inject(AuthService);
  private readonly configService = inject(ConfigService);
  private readonly router = inject(Router);

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
  protected readonly otpIndexes = [0, 1, 2, 3, 4, 5];
  protected readonly digits = signal<string[]>(Array(6).fill(''));
  protected readonly code = computed(() => this.digits().join(''));
  protected readonly password = signal('');

  protected readonly cardClass =
    'relative z-10 w-full max-w-xl rounded-2xl border border-ink-200/70 bg-white p-12 shadow-xl shadow-signal-900/10 ring-1 ring-signal-100 dark:border-ink-800/70 dark:bg-ink-900 dark:shadow-2xl dark:shadow-signal-900/40 dark:ring-ink-800/50';
  protected readonly labelClass = 'mb-2 block text-sm font-medium text-ink-700 dark:text-ink-300';
  protected readonly errorClass =
    'mb-6 rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700 dark:border-red-900/50 dark:bg-red-950/30 dark:text-red-400';
  protected readonly inputClass =
    'mb-6 w-full rounded-lg border border-ink-200 bg-white px-3 py-2 text-sm text-ink-900 focus:border-signal-500 focus:ring-1 focus:ring-signal-500 focus:outline-none dark:border-ink-700 dark:bg-ink-800 dark:text-white';
  protected readonly submitButtonClass = buttonClass('primary') + ' w-full';

  ngOnDestroy(): void {
    delete (window as unknown as Record<string, unknown>)[this.callbackName];
  }

  tabClass(tab: CredentialTab): string {
    const base =
      'flex-1 rounded-lg px-3 py-1.5 text-sm font-medium transition-colors duration-fast ease-fluid';
    return this.activeTab() === tab
      ? `${base} bg-white text-ink-900 shadow-sm dark:bg-ink-700 dark:text-white`
      : `${base} text-ink-500 hover:text-ink-700 dark:text-ink-400 dark:hover:text-ink-200`;
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

  onDigitInput(event: Event, index: number): void {
    const value = (event.target as HTMLInputElement).value;
    const enteredDigits = value.match(/\d/g) ?? [];

    if (enteredDigits.length > 1) {
      // SMS/one-time-code autofill (and fast synthetic typing) can deliver the whole
      // code as a single multi-character value into one box, bypassing maxlength —
      // spread it across this box and the following ones, same as a paste.
      this.digits.update((digits) =>
        digits.map((d, i) => (i >= index ? (enteredDigits[i - index] ?? d) : d)),
      );

      const nextIndex = Math.min(index + enteredDigits.length, this.otpIndexes.length - 1);
      (document.getElementById(`otp-digit-${nextIndex}`) as HTMLInputElement | null)?.focus();
      return;
    }

    const digit = enteredDigits[0] ?? '';

    this.digits.update((digits) => digits.map((d, i) => (i === index ? digit : d)));

    if (digit && index < this.otpIndexes.length - 1) {
      (document.getElementById(`otp-digit-${index + 1}`) as HTMLInputElement | null)?.focus();
    }
  }

  onDigitKeydown(event: KeyboardEvent, index: number): void {
    if (event.ctrlKey || event.metaKey) {
      return;
    }

    if (event.key.length === 1 && !/^\d$/.test(event.key)) {
      event.preventDefault();
      return;
    }

    if (event.key === 'Backspace') {
      const box = event.target as HTMLInputElement;
      if (box.value === '' && index > 0) {
        event.preventDefault();
        const prevIndex = index - 1;
        this.digits.update((digits) => digits.map((d, i) => (i === prevIndex ? '' : d)));
        (document.getElementById(`otp-digit-${prevIndex}`) as HTMLInputElement | null)?.focus();
      }
      return;
    }

    if (event.key === 'ArrowLeft' && index > 0) {
      event.preventDefault();
      (document.getElementById(`otp-digit-${index - 1}`) as HTMLInputElement | null)?.focus();
      return;
    }

    if (event.key === 'ArrowRight' && index < this.otpIndexes.length - 1) {
      event.preventDefault();
      (document.getElementById(`otp-digit-${index + 1}`) as HTMLInputElement | null)?.focus();
    }
  }

  onPaste(event: ClipboardEvent): void {
    event.preventDefault();
    const text = event.clipboardData?.getData('text') ?? '';
    const digits = text.match(/\d/g)?.slice(0, 6) ?? [];

    this.digits.update((current) => current.map((d, i) => digits.at(i) ?? d));

    const nextIndex = Math.min(digits.length, this.otpIndexes.length - 1);
    (document.getElementById(`otp-digit-${nextIndex}`) as HTMLInputElement | null)?.focus();
  }

  onSubmitCode(event: Event): void {
    event.preventDefault();
    this.submitting.set(true);
    this.errorCode.set(undefined);

    this.authService.verifyCode(this.email(), this.code(), this.captchaToken()).subscribe({
      next: ({ pendingProfileCompletion }) => {
        this.submitting.set(false);
        this.router.navigateByUrl(pendingProfileCompletion ? '/complete-profile' : '/welcome');
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
      next: ({ pendingProfileCompletion }) => {
        this.submitting.set(false);
        this.router.navigateByUrl(pendingProfileCompletion ? '/complete-profile' : '/welcome');
      },
      error: (err: { error?: { code?: AuthErrorCode } }) => {
        this.submitting.set(false);
        this.errorCode.set(err.error?.code);
      },
    });
  }

  onBackToEmail(): void {
    this.step.set('email');
    this.errorCode.set(undefined);
    this.digits.set(Array(6).fill(''));
    this.password.set('');
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
