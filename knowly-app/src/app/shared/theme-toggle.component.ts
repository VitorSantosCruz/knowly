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
      class="rounded-full px-3 py-1.5 text-sm transition-colors duration-fast ease-fluid hover:bg-ink-200/70 dark:hover:bg-ink-800"
    >
      {{ themeService.theme() === 'dark' ? '☀️' : '🌙' }}
    </button>
  `,
})
export class ThemeToggleComponent {
  protected readonly themeService = inject(ThemeService);
}
