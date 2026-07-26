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
      class="flex min-h-dvh bg-ink-50 text-ink-900 transition-colors duration-base ease-fluid dark:bg-ink-950 dark:text-ink-50"
    >
      <aside
        class="sticky top-0 flex h-dvh w-64 shrink-0 flex-col border-r border-ink-800/60 bg-ink-950 px-3 py-5 shadow-lg shadow-ink-950/20"
      >
        <app-nav-menu />
        <div
          data-tour-id="main-nav"
          class="mt-4 flex items-center gap-1 border-t border-ink-800/60 pt-3"
        >
          <app-help-menu />
          <app-language-switcher />
          <app-theme-toggle />
          <app-logout-button />
        </div>
      </aside>
      <main class="min-w-0 flex-1">
        <router-outlet />
      </main>
      <app-tour-overlay />
    </div>
  `,
})
export class AppShellComponent {}
