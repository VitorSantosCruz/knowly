import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import { provideTransloco } from '@jsverse/transloco';
import { AppShellComponent } from './app-shell.component';
import { mockMatchMedia, mockViewportMatchMedia } from '../testing/mock-match-media';
import { SidebarStateService } from '../core/sidebar-state.service';
import { FakeTranslocoLoader } from '../testing/fake-transloco-loader';

@Component({ selector: 'app-blank-test-route', template: '' })
class BlankTestRouteComponent {}

describe('AppShellComponent', () => {
  beforeEach(() => {
    localStorage.clear();
    document.documentElement.classList.remove('dark');
    mockMatchMedia();

    TestBed.configureTestingModule({
      imports: [AppShellComponent],
      providers: [
        provideRouter([{ path: '**', component: BlankTestRouteComponent }]),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideTransloco({
          config: { availableLangs: ['en', 'pt-BR'], defaultLang: 'en' },
          loader: FakeTranslocoLoader,
        }),
      ],
    });
  });

  it('renders the language switcher and theme toggle', () => {
    const fixture = TestBed.createComponent(AppShellComponent);
    fixture.detectChanges();

    const nativeElement: HTMLElement = fixture.nativeElement;
    expect(nativeElement.querySelector('app-language-switcher')).toBeTruthy();
    expect(nativeElement.querySelector('app-theme-toggle')).toBeTruthy();
  });

  it('renders the router outlet', () => {
    const fixture = TestBed.createComponent(AppShellComponent);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('router-outlet')).toBeTruthy();
  });

  it('renders the avatar menu instead of a standalone logout button', () => {
    const fixture = TestBed.createComponent(AppShellComponent);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('app-avatar-menu')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('app-logout-button')).toBeFalsy();
  });

  describe('mobile off-canvas sidebar', () => {
    afterEach(() => {
      localStorage.removeItem('knowly.sidebar.collapsed');
    });

    it('renders a mobile toggle button (visible only below md:) that calls setMobileOpen(true)', () => {
      mockViewportMatchMedia(false);
      const fixture = TestBed.createComponent(AppShellComponent);
      fixture.detectChanges();

      const toggle: HTMLButtonElement = fixture.nativeElement.querySelector(
        '[data-testid="mobile-nav-toggle"]',
      );
      expect(toggle).toBeTruthy();
      expect(toggle.className).toContain('md:hidden');

      toggle.click();
      fixture.detectChanges();

      expect(TestBed.inject(SidebarStateService).mobileOpen()).toBe(true);
    });

    it('renders the backdrop only when mobileOpen() is true, on a mobile viewport', () => {
      mockViewportMatchMedia(false);
      const fixture = TestBed.createComponent(AppShellComponent);
      fixture.detectChanges();

      expect(
        fixture.nativeElement.querySelector('[data-testid="mobile-nav-backdrop"]'),
      ).toBeFalsy();

      TestBed.inject(SidebarStateService).setMobileOpen(true);
      fixture.detectChanges();

      expect(
        fixture.nativeElement.querySelector('[data-testid="mobile-nav-backdrop"]'),
      ).toBeTruthy();
    });

    it('clicking the backdrop calls setMobileOpen(false)', () => {
      mockViewportMatchMedia(false);
      const fixture = TestBed.createComponent(AppShellComponent);
      fixture.detectChanges();

      const sidebarState = TestBed.inject(SidebarStateService);
      sidebarState.setMobileOpen(true);
      fixture.detectChanges();

      fixture.nativeElement.querySelector('[data-testid="mobile-nav-backdrop"]').click();

      expect(sidebarState.mobileOpen()).toBe(false);
    });

    it('pressing Escape calls setMobileOpen(false)', () => {
      mockViewportMatchMedia(false);
      const fixture = TestBed.createComponent(AppShellComponent);
      fixture.detectChanges();

      const sidebarState = TestBed.inject(SidebarStateService);
      sidebarState.setMobileOpen(true);
      fixture.detectChanges();

      document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));

      expect(sidebarState.mobileOpen()).toBe(false);
    });

    it('a route change (NavigationEnd) calls setMobileOpen(false)', async () => {
      mockViewportMatchMedia(false);
      const fixture = TestBed.createComponent(AppShellComponent);
      fixture.detectChanges();

      const sidebarState = TestBed.inject(SidebarStateService);
      sidebarState.setMobileOpen(true);
      fixture.detectChanges();

      const router = TestBed.inject(Router);
      await router.navigateByUrl('/welcome');
      fixture.detectChanges();

      expect(sidebarState.mobileOpen()).toBe(false);
    });

    it('the <aside> is width-conditional on collapsed() (desktop) and translate-x-conditional on mobileOpen() (mobile)', () => {
      mockViewportMatchMedia(true);
      const fixture = TestBed.createComponent(AppShellComponent);
      fixture.detectChanges();

      const aside: HTMLElement = fixture.nativeElement.querySelector('aside');
      expect(aside.className).toContain('w-64');

      TestBed.inject(SidebarStateService).setCollapsed(true);
      fixture.detectChanges();
      expect(aside.className).not.toContain('w-64');
    });
  });
});
