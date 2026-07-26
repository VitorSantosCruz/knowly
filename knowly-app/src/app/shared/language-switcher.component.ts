import { Component, inject } from '@angular/core';
import { ButtonDirective } from 'primeng/button';
import { LanguageService } from '../core/language.service';

const OTHER_LANG: Record<string, string> = {
  en: 'pt-BR',
  'pt-BR': 'en',
};

@Component({
  selector: 'app-language-switcher',
  imports: [ButtonDirective],
  template: `
    <button
      type="button"
      pButton
      text
      severity="secondary"
      (click)="switch()"
      [attr.aria-label]="'Change language'"
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
