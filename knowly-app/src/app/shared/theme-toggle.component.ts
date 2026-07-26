import { Component, inject } from '@angular/core';
import { ButtonDirective } from 'primeng/button';
import { ThemeService } from '../core/theme.service';

@Component({
  selector: 'app-theme-toggle',
  imports: [ButtonDirective],
  template: `
    <button
      type="button"
      pButton
      text
      rounded
      severity="secondary"
      (click)="themeService.toggle()"
      [attr.aria-label]="
        themeService.theme() === 'dark' ? 'Switch to light mode' : 'Switch to dark mode'
      "
    >
      <i [class]="themeService.theme() === 'dark' ? 'pi pi-sun' : 'pi pi-moon'"></i>
    </button>
  `,
})
export class ThemeToggleComponent {
  protected readonly themeService = inject(ThemeService);
}
