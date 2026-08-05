import { Component, DestroyRef, HostListener, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterOutlet } from '@angular/router';
import { filter, map, startWith } from 'rxjs';
import { TranslocoPipe } from '@jsverse/transloco';
import { LucidePanelLeftOpen } from '@lucide/angular';
import { LanguageSwitcherComponent } from '../shared/language-switcher.component';
import { ThemeToggleComponent } from '../shared/theme-toggle.component';
import { HelpMenuComponent } from '../shared/help-menu.component';
import { TourOverlayComponent } from '../shared/tour-overlay.component';
import { AvatarMenuComponent } from '../shared/avatar-menu.component';
import { SidebarStateService } from '../core/sidebar-state.service';
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
    AvatarMenuComponent,
    NavMenuComponent,
    LucidePanelLeftOpen,
    TranslocoPipe,
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
        @if (!sidebarState.viewportIsDesktop() && sidebarState.mobileOpen()) {
          <button
            type="button"
            data-testid="mobile-nav-backdrop"
            [attr.aria-label]="'nav.closeMobileMenu' | transloco"
            class="fixed inset-0 z-30 cursor-default bg-black/50"
            (click)="sidebarState.setMobileOpen(false)"
          ></button>
        }
        <aside [class]="asideClass()">
          <app-nav-menu />
        </aside>
        <div class="flex min-w-0 flex-1 flex-col">
          <header
            data-tour-id="main-nav"
            class="dark sticky top-0 z-10 flex h-14 shrink-0 items-center justify-end gap-1 border-b border-ink-800 bg-ink-950/95 px-4 backdrop-blur-md"
          >
            <button
              type="button"
              data-testid="mobile-nav-toggle"
              class="mr-auto rounded-lg p-2 text-ink-300/80 hover:bg-ink-800/60 hover:text-white md:hidden"
              (click)="sidebarState.setMobileOpen(true)"
            >
              <svg lucidePanelLeftOpen class="h-5 w-5" aria-hidden="true"></svg>
            </button>
            <app-help-menu />
            <app-language-switcher />
            <app-theme-toggle />
            <app-avatar-menu />
          </header>
          <main class="min-w-0 flex-1 bg-ink-50 dark:bg-ink-950">
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
  protected readonly sidebarState = inject(SidebarStateService);

  protected readonly isBareRoute = toSignal(
    this.router.events.pipe(
      filter((event) => event instanceof NavigationEnd),
      map((event) => BARE_ROUTES.includes((event as NavigationEnd).urlAfterRedirects)),
      startWith(BARE_ROUTES.includes(this.router.url)),
    ),
    { initialValue: BARE_ROUTES.includes(this.router.url) },
  );

  constructor() {
    // Route change closes the mobile drawer, same Router.events/NavigationEnd pattern
    // isBareRoute() already uses above.
    const subscription = this.router.events
      .pipe(filter((event) => event instanceof NavigationEnd))
      .subscribe(() => this.sidebarState.setMobileOpen(false));

    inject(DestroyRef).onDestroy(() => subscription.unsubscribe());
  }

  @HostListener('document:keydown.escape')
  protected onEscape(): void {
    this.sidebarState.setMobileOpen(false);
  }

  protected asideClass(): string {
    const base =
      'dark flex h-dvh shrink-0 flex-col border-r border-ink-800 bg-ink-950 px-3 py-5 shadow-lg shadow-ink-950/20 transition-all duration-base ease-fluid';

    if (this.sidebarState.viewportIsDesktop()) {
      const width = this.sidebarState.collapsed() ? 'w-[4.5rem]' : 'w-64';
      return `${base} sticky top-0 ${width}`;
    }

    const translate = this.sidebarState.mobileOpen() ? 'translate-x-0' : '-translate-x-full';
    return `${base} fixed inset-y-0 left-0 z-40 w-64 ${translate}`;
  }
}
