import { Component, inject } from '@angular/core';
import { LucideMoon, LucideSun } from '@lucide/angular';
import { buttonClass } from './button-classes';
import { ThemeService } from '../core/theme.service';

@Component({
  selector: 'app-theme-toggle',
  imports: [LucideSun, LucideMoon],
  template: `
    <button
      type="button"
      [class]="buttonClass"
      (click)="themeService.toggle()"
      [attr.aria-label]="
        themeService.theme() === 'dark' ? 'Switch to light mode' : 'Switch to dark mode'
      "
    >
      @if (themeService.theme() === 'dark') {
        <svg lucideSun class="h-4 w-4"></svg>
      } @else {
        <svg lucideMoon class="h-4 w-4"></svg>
      }
    </button>
  `,
})
export class ThemeToggleComponent {
  protected readonly themeService = inject(ThemeService);
  protected readonly buttonClass = buttonClass('secondary', { ghost: true, rounded: true });
}
