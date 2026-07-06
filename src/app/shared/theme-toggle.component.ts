import { Component, inject } from '@angular/core';
import { ThemeService } from '../core/theme.service';

@Component({
  selector: 'app-theme-toggle',
  template: `
    <button
      type="button"
      (click)="themeService.toggle()"
      [attr.aria-label]="
        themeService.theme() === 'dark' ? 'Switch to light mode' : 'Switch to dark mode'
      "
    >
      {{ themeService.theme() === 'dark' ? '☀️' : '🌙' }}
    </button>
  `,
})
export class ThemeToggleComponent {
  protected readonly themeService = inject(ThemeService);
}
