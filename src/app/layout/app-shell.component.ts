import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { LanguageSwitcherComponent } from '../shared/language-switcher.component';
import { ThemeToggleComponent } from '../shared/theme-toggle.component';

@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, LanguageSwitcherComponent, ThemeToggleComponent],
  template: `
    <div
      class="min-h-dvh bg-slate-100 text-slate-900 transition-colors duration-200 dark:bg-slate-950 dark:text-slate-100"
    >
      <div class="fixed top-4 right-4 z-10 flex items-center gap-1">
        <app-language-switcher />
        <app-theme-toggle />
      </div>
      <router-outlet />
    </div>
  `,
})
export class AppShellComponent {}
