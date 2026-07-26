import { Component, inject } from '@angular/core';
import { Router } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { LucideLogOut } from '@lucide/angular';
import { buttonClass } from './button-classes';
import { AuthService } from '../core/auth.service';

@Component({
  selector: 'app-logout-button',
  imports: [TranslocoPipe, LucideLogOut],
  template: `
    @if (authService.isLoggedIn()) {
      <button
        type="button"
        [class]="buttonClass"
        (click)="logout()"
        [attr.aria-label]="'logout.label' | transloco"
        [attr.title]="'logout.label' | transloco"
      >
        <svg lucideLogOut class="h-4 w-4" aria-hidden="true"></svg>
      </button>
    }
  `,
})
export class LogoutButtonComponent {
  protected readonly authService = inject(AuthService);
  protected readonly buttonClass = buttonClass('secondary', { ghost: true, rounded: true });
  private readonly router = inject(Router);

  protected logout(): void {
    this.authService.logout().subscribe({
      complete: () => this.router.navigateByUrl('/login'),
      error: () => this.router.navigateByUrl('/login'),
    });
  }
}
