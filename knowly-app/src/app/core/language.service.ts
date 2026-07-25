import { Injectable, Signal, inject } from '@angular/core';
import { TranslocoService } from '@jsverse/transloco';

const STORAGE_KEY = 'knowly.lang';

@Injectable({ providedIn: 'root' })
export class LanguageService {
  private readonly transloco = inject(TranslocoService);
  readonly currentLang: Signal<string> = this.transloco.activeLang;

  constructor() {
    const stored = localStorage.getItem(STORAGE_KEY);

    if (stored) {
      this.transloco.setActiveLang(stored);
    }
  }

  setLanguage(lang: string): void {
    this.transloco.setActiveLang(lang);
    localStorage.setItem(STORAGE_KEY, lang);
  }
}
