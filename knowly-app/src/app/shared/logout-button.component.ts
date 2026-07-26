import { Component, inject } from '@angular/core';
import { Router } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { ButtonDirective } from 'primeng/button';
import { AuthService } from '../core/auth.service';

@Component({
  selector: 'app-logout-button',
  imports: [TranslocoPipe, ButtonDirective],
  template: `
    @if (authService.isLoggedIn()) {
      <button
        type="button"
        pButton
        text
        rounded
        severity="secondary"
        (click)="logout()"
        [attr.aria-label]="'logout.label' | transloco"
        [attr.title]="'logout.label' | transloco"
      >
        <i class="pi pi-sign-out" aria-hidden="true"></i>
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
