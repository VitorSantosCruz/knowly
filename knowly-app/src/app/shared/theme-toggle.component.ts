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
      class="rounded-lg px-3 py-1.5 text-sm text-ink-300 transition-all duration-fast ease-fluid hover:-translate-y-0.5 hover:bg-ink-800/60 hover:text-white active:translate-y-0 active:scale-[0.98]"
    >
      {{ themeService.theme() === 'dark' ? '☀️' : '🌙' }}
    </button>
  `,
})
export class ThemeToggleComponent {
  protected readonly themeService = inject(ThemeService);
}
