import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { LanguageSwitcherComponent } from '../shared/language-switcher.component';
import { ThemeToggleComponent } from '../shared/theme-toggle.component';
import { HelpMenuComponent } from '../shared/help-menu.component';
import { TourOverlayComponent } from '../shared/tour-overlay.component';
import { LogoutButtonComponent } from '../shared/logout-button.component';
import { NavMenuComponent } from './nav-menu.component';

@Component({
  selector: 'app-shell',
  imports: [
    RouterOutlet,
    LanguageSwitcherComponent,
    ThemeToggleComponent,
    HelpMenuComponent,
    TourOverlayComponent,
    LogoutButtonComponent,
    NavMenuComponent,
  ],
  template: `
    <div
      class="min-h-dvh bg-slate-100 text-slate-900 transition-colors duration-200 dark:bg-slate-950 dark:text-slate-100"
    >
      <div data-tour-id="main-nav" class="fixed top-4 right-4 z-10 flex items-center gap-1">
        <app-help-menu />
        <app-language-switcher />
        <app-theme-toggle />
        <app-logout-button />
      </div>
      <app-nav-menu />
      <router-outlet />
      <app-tour-overlay />
    </div>
  `,
})
export class AppShellComponent {}
