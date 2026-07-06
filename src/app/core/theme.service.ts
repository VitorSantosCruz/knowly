import { Injectable, signal } from '@angular/core';

export type Theme = 'light' | 'dark';

const STORAGE_KEY = 'knowly.theme';

@Injectable({ providedIn: 'root' })
export class ThemeService {
  readonly theme = signal<Theme>(this.restore());

  constructor() {
    this.apply(this.theme());
  }

  setTheme(theme: Theme): void {
    this.theme.set(theme);
    localStorage.setItem(STORAGE_KEY, theme);
    this.apply(theme);
  }

  toggle(): void {
    this.setTheme(this.theme() === 'dark' ? 'light' : 'dark');
  }

  private restore(): Theme {
    const stored = localStorage.getItem(STORAGE_KEY);

    if (stored === 'light' || stored === 'dark') {
      return stored;
    }

    return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
  }

  private apply(theme: Theme): void {
    document.documentElement.classList.toggle('dark', theme === 'dark');
  }
}
