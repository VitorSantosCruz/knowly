import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { LanguageSwitcherComponent } from '../shared/language-switcher.component';
import { ThemeToggleComponent } from '../shared/theme-toggle.component';

@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, LanguageSwitcherComponent, ThemeToggleComponent],
  template: `
    <div class="fixed top-4 right-4 z-10 flex items-center gap-2">
      <app-language-switcher />
      <app-theme-toggle />
    </div>
    <router-outlet />
  `,
})
export class AppShellComponent {}
