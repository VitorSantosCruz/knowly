import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { LanguageSwitcherComponent } from '../shared/language-switcher.component';
import { ThemeToggleComponent } from '../shared/theme-toggle.component';
import { HelpMenuComponent } from '../shared/help-menu.component';
import { TourOverlayComponent } from '../shared/tour-overlay.component';

@Component({
  selector: 'app-shell',
  imports: [
    RouterOutlet,
    LanguageSwitcherComponent,
    ThemeToggleComponent,
    HelpMenuComponent,
    TourOverlayComponent,
  ],
  template: `
    <div
      class="min-h-dvh bg-slate-100 text-slate-900 transition-colors duration-200 dark:bg-slate-950 dark:text-slate-100"
    >
      <div data-tour-id="main-nav" class="fixed top-4 right-4 z-10 flex items-center gap-1">
        <app-help-menu />
        <app-language-switcher />
        <app-theme-toggle />
      </div>
      <router-outlet />
      <app-tour-overlay />
    </div>
  `,
})
export class AppShellComponent {}
