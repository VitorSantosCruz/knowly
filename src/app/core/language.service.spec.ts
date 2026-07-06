import { Injectable } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { TranslocoLoader, TranslocoService, provideTransloco } from '@jsverse/transloco';
import { of } from 'rxjs';
import { LanguageService } from './language.service';

@Injectable()
class FakeTranslocoLoader implements TranslocoLoader {
  getTranslation() {
    return of({});
  }
}

describe('LanguageService', () => {
  beforeEach(() => {
    localStorage.clear();

    TestBed.configureTestingModule({
      providers: [
        provideTransloco({
          config: { availableLangs: ['en', 'pt-BR'], defaultLang: 'en' },
          loader: FakeTranslocoLoader,
        }),
      ],
    });
  });

  it('restores a persisted language on init', () => {
    localStorage.setItem('knowly.lang', 'pt-BR');

    const service = TestBed.inject(LanguageService);

    expect(service.currentLang()).toBe('pt-BR');
  });

  it('falls back to the default language when nothing is persisted', () => {
    const service = TestBed.inject(LanguageService);

    expect(service.currentLang()).toBe('en');
  });

  it('persists the language when set', () => {
    const service = TestBed.inject(LanguageService);
    const transloco = TestBed.inject(TranslocoService);

    service.setLanguage('pt-BR');

    expect(localStorage.getItem('knowly.lang')).toBe('pt-BR');
    expect(transloco.getActiveLang()).toBe('pt-BR');
  });
});
