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
      class="rounded-lg px-3 py-1.5 text-sm font-medium text-ink-300 transition-all duration-fast ease-fluid hover:-translate-y-0.5 hover:bg-ink-800/60 hover:text-white active:translate-y-0 active:scale-[0.98]"
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
