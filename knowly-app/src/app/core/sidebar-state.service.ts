import { DestroyRef, Injectable, inject, signal } from '@angular/core';

const STORAGE_KEY = 'knowly.sidebar.collapsed';
const DESKTOP_QUERY = '(min-width: 768px)';

/**
 * Desktop collapsed/expanded rail state (persisted, REQ-14) plus mobile
 * off-canvas open/closed state (session-only, REQ-13) and a
 * `viewportIsDesktop` breakpoint signal, shared between
 * `nav-menu.component.ts` and `app-shell.component.ts` — see PLAN.md's
 * "Sidebar collapse state lives in a new SidebarStateService" decision.
 */
@Injectable({ providedIn: 'root' })
export class SidebarStateService {
  private readonly _collapsed = signal(this.restoreCollapsed());
  readonly collapsed = this._collapsed.asReadonly();

  private readonly _mobileOpen = signal(false);
  readonly mobileOpen = this._mobileOpen.asReadonly();

  private readonly mediaQueryList = window.matchMedia(DESKTOP_QUERY);
  private readonly _viewportIsDesktop = signal(this.mediaQueryList.matches);
  readonly viewportIsDesktop = this._viewportIsDesktop.asReadonly();

  constructor() {
    const listener = (event: MediaQueryListEvent): void => {
      this._viewportIsDesktop.set(event.matches);
    };

    this.mediaQueryList.addEventListener('change', listener);

    inject(DestroyRef).onDestroy(() => {
      this.mediaQueryList.removeEventListener('change', listener);
    });
  }

  toggle(): void {
    this.setCollapsed(!this._collapsed());
  }

  setCollapsed(collapsed: boolean): void {
    this._collapsed.set(collapsed);
    localStorage.setItem(STORAGE_KEY, String(collapsed));
  }

  setMobileOpen(open: boolean): void {
    this._mobileOpen.set(open);
  }

  private restoreCollapsed(): boolean {
    return localStorage.getItem(STORAGE_KEY) === 'true';
  }
}
