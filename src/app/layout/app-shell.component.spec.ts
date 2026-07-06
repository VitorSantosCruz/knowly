import { Injectable } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslocoLoader, provideTransloco } from '@jsverse/transloco';
import { of } from 'rxjs';
import { AppShellComponent } from './app-shell.component';
import { mockMatchMedia } from '../testing/mock-match-media';

@Injectable()
class FakeTranslocoLoader implements TranslocoLoader {
  getTranslation() {
    return of({});
  }
}

describe('AppShellComponent', () => {
  beforeEach(() => {
    localStorage.clear();
    document.documentElement.classList.remove('dark');
    mockMatchMedia();

    TestBed.configureTestingModule({
      imports: [AppShellComponent],
      providers: [
        provideRouter([]),
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
});
