import { TestBed } from '@angular/core/testing';
import { ThemeService } from './theme.service';

function mockMatchMedia(prefersDark: boolean) {
  Object.defineProperty(window, 'matchMedia', {
    writable: true,
    configurable: true,
    value: (query: string) => ({
      matches: query === '(prefers-color-scheme: dark)' && prefersDark,
      media: query,
      addEventListener: () => {},
      removeEventListener: () => {},
    }),
  });
}

describe('ThemeService', () => {
  beforeEach(() => {
    localStorage.clear();
    document.documentElement.classList.remove('dark');
  });

  it('restores a persisted theme from localStorage', () => {
    localStorage.setItem('knowly.theme', 'dark');
    mockMatchMedia(false);

    const service = TestBed.inject(ThemeService);

    expect(service.theme()).toBe('dark');
    expect(document.documentElement.classList.contains('dark')).toBe(true);
  });

  it('falls back to prefers-color-scheme when nothing is persisted', () => {
    mockMatchMedia(true);

    const service = TestBed.inject(ThemeService);

    expect(service.theme()).toBe('dark');
  });

  it('falls back to light when the system has no dark preference and nothing is persisted', () => {
    mockMatchMedia(false);

    const service = TestBed.inject(ThemeService);

    expect(service.theme()).toBe('light');
  });

  it('persists the theme and toggles the dark class when set', () => {
    mockMatchMedia(false);
    const service = TestBed.inject(ThemeService);

    service.setTheme('dark');

    expect(localStorage.getItem('knowly.theme')).toBe('dark');
    expect(document.documentElement.classList.contains('dark')).toBe(true);

    service.setTheme('light');

    expect(localStorage.getItem('knowly.theme')).toBe('light');
    expect(document.documentElement.classList.contains('dark')).toBe(false);
  });

  it('toggles between light and dark', () => {
    mockMatchMedia(false);
    const service = TestBed.inject(ThemeService);

    service.toggle();
    expect(service.theme()).toBe('dark');

    service.toggle();
    expect(service.theme()).toBe('light');
  });
});
