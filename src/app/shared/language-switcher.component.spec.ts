import { Injectable } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { TranslocoLoader, provideTransloco } from '@jsverse/transloco';
import { of } from 'rxjs';
import { LanguageSwitcherComponent } from './language-switcher.component';
import { LanguageService } from '../core/language.service';

@Injectable()
class FakeTranslocoLoader implements TranslocoLoader {
  getTranslation() {
    return of({});
  }
}

describe('LanguageSwitcherComponent', () => {
  beforeEach(() => {
    localStorage.clear();

    TestBed.configureTestingModule({
      imports: [LanguageSwitcherComponent],
      providers: [
        provideTransloco({
          config: { availableLangs: ['en', 'pt-BR'], defaultLang: 'en' },
          loader: FakeTranslocoLoader,
        }),
      ],
    });
  });

  it('renders a button showing the active language', () => {
    const fixture = TestBed.createComponent(LanguageSwitcherComponent);
    fixture.detectChanges();

    const button: HTMLButtonElement = fixture.nativeElement.querySelector('button');
    expect(button).toBeTruthy();
    expect(button.textContent).toContain('EN');
  });

  it('switches to the other language when clicked', () => {
    const fixture = TestBed.createComponent(LanguageSwitcherComponent);
    fixture.detectChanges();
    const languageService = TestBed.inject(LanguageService);

    const button: HTMLButtonElement = fixture.nativeElement.querySelector('button');
    button.click();
    fixture.detectChanges();

    expect(languageService.currentLang()).toBe('pt-BR');
    expect(button.textContent).toContain('PT');
  });
});
