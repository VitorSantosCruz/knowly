import { TestBed } from '@angular/core/testing';
import { ThemeToggleComponent } from './theme-toggle.component';
import { ThemeService } from '../core/theme.service';

describe('ThemeToggleComponent', () => {
  beforeEach(() => {
    localStorage.clear();
    document.documentElement.classList.remove('dark');
    Object.defineProperty(window, 'matchMedia', {
      writable: true,
      configurable: true,
      value: () => ({
        matches: false,
        media: '',
        addEventListener: () => {},
        removeEventListener: () => {},
      }),
    });

    TestBed.configureTestingModule({
      imports: [ThemeToggleComponent],
    });
  });

  it('renders a button', () => {
    const fixture = TestBed.createComponent(ThemeToggleComponent);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('button')).toBeTruthy();
  });

  it('toggles the theme when clicked', () => {
    const fixture = TestBed.createComponent(ThemeToggleComponent);
    fixture.detectChanges();
    const themeService = TestBed.inject(ThemeService);

    const button: HTMLButtonElement = fixture.nativeElement.querySelector('button');
    button.click();
    fixture.detectChanges();

    expect(themeService.theme()).toBe('dark');
  });
});
