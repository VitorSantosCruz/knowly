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
      class="flex min-h-dvh flex-col bg-slate-100 text-slate-900 transition-colors duration-200 dark:bg-slate-950 dark:text-slate-100"
    >
      <header
        class="sticky top-0 z-10 flex h-14 shrink-0 items-center justify-between border-b border-slate-200 bg-white/80 px-4 backdrop-blur transition-colors duration-200 dark:border-slate-800 dark:bg-slate-950/80"
      >
        <app-nav-menu />
        <div data-tour-id="main-nav" class="flex items-center gap-1">
          <app-help-menu />
          <app-language-switcher />
          <app-theme-toggle />
          <app-logout-button />
        </div>
      </header>
      <main class="flex-1">
        <router-outlet />
      </main>
      <app-tour-overlay />
    </div>
  `,
})
export class AppShellComponent {}
