import { TestBed } from '@angular/core/testing';
import { SidebarStateService } from './sidebar-state.service';
import { mockViewportMatchMedia } from '../testing/mock-match-media';

const STORAGE_KEY = 'knowly.sidebar.collapsed';

describe('SidebarStateService', () => {
  afterEach(() => {
    localStorage.removeItem(STORAGE_KEY);
  });

  it('defaults to expanded (not collapsed) when localStorage has no stored value', () => {
    mockViewportMatchMedia(true);
    const service = TestBed.inject(SidebarStateService);

    expect(service.collapsed()).toBe(false);
  });

  it('reads a previously-persisted collapsed value back on construction', () => {
    mockViewportMatchMedia(true);
    localStorage.setItem(STORAGE_KEY, 'true');

    const service = TestBed.inject(SidebarStateService);

    expect(service.collapsed()).toBe(true);
  });

  it('toggle() flips collapsed() and persists it to localStorage', () => {
    mockViewportMatchMedia(true);
    const service = TestBed.inject(SidebarStateService);

    service.toggle();
    expect(service.collapsed()).toBe(true);
    expect(localStorage.getItem(STORAGE_KEY)).toBe('true');

    service.toggle();
    expect(service.collapsed()).toBe(false);
    expect(localStorage.getItem(STORAGE_KEY)).toBe('false');
  });

  it('setCollapsed(boolean) sets collapsed() and persists it to localStorage', () => {
    mockViewportMatchMedia(true);
    const service = TestBed.inject(SidebarStateService);

    service.setCollapsed(true);
    expect(service.collapsed()).toBe(true);
    expect(localStorage.getItem(STORAGE_KEY)).toBe('true');
  });

  it('setMobileOpen() sets mobileOpen() but never persists to localStorage', () => {
    mockViewportMatchMedia(true);
    const service = TestBed.inject(SidebarStateService);

    service.setMobileOpen(true);
    expect(service.mobileOpen()).toBe(true);
    expect(localStorage.getItem(STORAGE_KEY)).toBeNull();
  });

  it('viewportIsDesktop reflects the mocked matchMedia result and updates on a change event', () => {
    const mql = mockViewportMatchMedia(true);
    const service = TestBed.inject(SidebarStateService);

    expect(service.viewportIsDesktop()).toBe(true);

    mql.dispatch(false);
    expect(service.viewportIsDesktop()).toBe(false);

    mql.dispatch(true);
    expect(service.viewportIsDesktop()).toBe(true);
  });
});
