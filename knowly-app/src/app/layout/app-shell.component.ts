import { Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterOutlet } from '@angular/router';
import { filter, map, startWith } from 'rxjs';
import { LanguageSwitcherComponent } from '../shared/language-switcher.component';
import { ThemeToggleComponent } from '../shared/theme-toggle.component';
import { HelpMenuComponent } from '../shared/help-menu.component';
import { TourOverlayComponent } from '../shared/tour-overlay.component';
import { LogoutButtonComponent } from '../shared/logout-button.component';
import { NavMenuComponent } from './nav-menu.component';

const BARE_ROUTES = ['/login'];

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
    @if (isBareRoute()) {
      <div
        class="flex min-h-dvh flex-col bg-ink-50 text-ink-900 transition-colors duration-base ease-fluid dark:bg-ink-950 dark:text-ink-50"
      >
        <header class="flex h-14 shrink-0 items-center justify-end gap-1 px-4">
          <app-language-switcher />
          <app-theme-toggle />
        </header>
        <main class="min-w-0 flex-1">
          <router-outlet />
        </main>
      </div>
    } @else {
      <div
        class="flex min-h-dvh bg-ink-50 text-ink-900 transition-colors duration-base ease-fluid dark:bg-ink-950 dark:text-ink-50"
      >
        <aside
          class="dark sticky top-0 flex h-dvh w-64 shrink-0 flex-col border-r border-ink-800 bg-ink-950 px-3 py-5 shadow-lg shadow-ink-950/20"
        >
          <app-nav-menu />
        </aside>
        <div class="flex min-w-0 flex-1 flex-col">
          <header
            data-tour-id="main-nav"
            class="dark sticky top-0 z-10 flex h-14 shrink-0 items-center justify-end gap-1 border-b border-ink-800 bg-ink-950/95 px-4 backdrop-blur-md"
          >
            <app-help-menu />
            <app-language-switcher />
            <app-theme-toggle />
            <span class="mx-1 h-5 w-px shrink-0 bg-ink-200 dark:bg-ink-800"></span>
            <app-logout-button />
          </header>
          <main class="min-w-0 flex-1">
            <router-outlet />
          </main>
        </div>
        <app-tour-overlay />
      </div>
    }
  `,
})
export class AppShellComponent {
  private readonly router = inject(Router);

  protected readonly isBareRoute = toSignal(
    this.router.events.pipe(
      filter((event) => event instanceof NavigationEnd),
      map((event) => BARE_ROUTES.includes((event as NavigationEnd).urlAfterRedirects)),
      startWith(BARE_ROUTES.includes(this.router.url)),
    ),
    { initialValue: BARE_ROUTES.includes(this.router.url) },
  );
}
