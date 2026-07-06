import { Component, signal } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { inject } from '@angular/core';
import { AuthService, AuthErrorCode } from '../../core/auth.service';

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
          <button type="submit" [disabled]="submitting()">
            {{ 'login.continue' | transloco }}
          </button>
        </form>
      } @else {
        <div data-testid="credential-step"></div>
      }
    </div>
  `,
})
export class LoginPageComponent {
  private readonly authService = inject(AuthService);

  protected readonly step = signal<Step>('email');
  protected readonly email = signal('');
  protected readonly submitting = signal(false);
  protected readonly errorCode = signal<AuthErrorCode | undefined>(undefined);

  onSubmitEmail(event: Event): void {
    event.preventDefault();
    this.submitting.set(true);
    this.errorCode.set(undefined);

    this.authService.requestLogin(this.email(), undefined).subscribe({
      next: () => {
        this.submitting.set(false);
        this.step.set('credential');
      },
      error: (err: { error?: { code?: AuthErrorCode } }) => {
        this.submitting.set(false);
        this.errorCode.set(err.error?.code);
      },
    });
  }
}
