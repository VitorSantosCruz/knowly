import { Component, inject } from '@angular/core';
import { Router } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { AuthService } from '../core/auth.service';

@Component({
  selector: 'app-logout-button',
  imports: [TranslocoPipe],
  template: `
    @if (authService.isLoggedIn()) {
      <button
        type="button"
        (click)="logout()"
        [attr.aria-label]="'logout.label' | transloco"
        [attr.title]="'logout.label' | transloco"
        class="rounded-lg px-3 py-1.5 text-sm text-ink-300 transition-all duration-fast ease-fluid hover:-translate-y-0.5 hover:bg-ink-800/60 hover:text-white active:translate-y-0 active:scale-[0.98]"
      >
        <svg
          xmlns="http://www.w3.org/2000/svg"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="2"
          stroke-linecap="round"
          stroke-linejoin="round"
          class="h-4 w-4"
          aria-hidden="true"
        >
          <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" />
          <polyline points="16 17 21 12 16 7" />
          <line x1="21" y1="12" x2="9" y2="12" />
        </svg>
      </button>
    }
  `,
})
export class LogoutButtonComponent {
  protected readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  protected logout(): void {
    this.authService.logout().subscribe({
      complete: () => this.router.navigateByUrl('/login'),
      error: () => this.router.navigateByUrl('/login'),
    });
  }
}
