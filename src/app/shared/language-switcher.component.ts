import { Component, inject } from '@angular/core';
import { LanguageService } from '../core/language.service';

const OTHER_LANG: Record<string, string> = {
  en: 'pt-BR',
  'pt-BR': 'en',
};

@Component({
  selector: 'app-language-switcher',
  template: `
    <button
      type="button"
      (click)="switch()"
      [attr.aria-label]="'Change language'"
      class="rounded-full px-3 py-1.5 text-sm font-medium text-slate-600 transition hover:bg-slate-200/70 dark:text-slate-300 dark:hover:bg-slate-800"
    >
      {{ languageService.currentLang() === 'pt-BR' ? 'PT' : 'EN' }}
    </button>
  `,
})
export class LanguageSwitcherComponent {
  protected readonly languageService = inject(LanguageService);

  switch(): void {
    const next = OTHER_LANG[this.languageService.currentLang()] ?? 'en';
    this.languageService.setLanguage(next);
  }
}
