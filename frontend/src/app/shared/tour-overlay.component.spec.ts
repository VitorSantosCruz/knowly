import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideTransloco } from '@jsverse/transloco';
import { TourOverlayComponent } from './tour-overlay.component';
import { TourService } from '../core/tour.service';
import { FakeTranslocoLoader } from '../testing/fake-transloco-loader';

describe('TourOverlayComponent', () => {
  let fixture: ComponentFixture<TourOverlayComponent>;
  let tourService: TourService;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TourOverlayComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideTransloco({
          config: { availableLangs: ['en', 'pt-BR'], defaultLang: 'en' },
          loader: FakeTranslocoLoader,
        }),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(TourOverlayComponent);
    tourService = TestBed.inject(TourService);
  });

  it('renders nothing when the tour is not active', () => {
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="tour-overlay"]')).toBeFalsy();
  });

  it("renders the current step's title and body when active", () => {
    tourService.start();
    fixture.detectChanges();

    const overlay = fixture.nativeElement.querySelector('[data-testid="tour-overlay"]');
    expect(overlay).toBeTruthy();
    expect(overlay.textContent).toContain('Main navigation');
  });

  it('next button advances to the next step', () => {
    tourService.start();
    fixture.detectChanges();

    const nextButton: HTMLButtonElement = fixture.nativeElement.querySelector(
      '[data-testid="tour-next"]',
    );
    nextButton.click();
    fixture.detectChanges();

    expect(tourService.stepIndex()).toBe(1);
  });

  it('Escape key skips the tour', () => {
    tourService.start();
    fixture.detectChanges();

    const overlay: HTMLElement = fixture.nativeElement.querySelector(
      '[data-testid="tour-overlay"]',
    );
    overlay.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    fixture.detectChanges();

    expect(tourService.active()).toBe(false);
  });

  it('clamps the overlay position so it never overflows the right edge of the viewport', () => {
    const target = document.createElement('div');
    target.setAttribute('data-tour-id', 'main-nav');
    document.body.appendChild(target);
    vi.spyOn(target, 'getBoundingClientRect').mockReturnValue({
      top: 10,
      bottom: 40,
      left: window.innerWidth - 20,
      right: window.innerWidth,
      width: 20,
      height: 30,
      x: window.innerWidth - 20,
      y: 10,
      toJSON: () => ({}),
    });

    try {
      tourService.start();
      fixture.detectChanges();

      const box: HTMLElement = fixture.nativeElement.querySelector(
        '[data-testid="tour-overlay"] > div',
      );
      const left = parseFloat(box.style.left);
      expect(left + 320).toBeLessThanOrEqual(window.innerWidth);
    } finally {
      document.body.removeChild(target);
    }
  });

  it('Tab at the last focusable control wraps focus to the first', () => {
    tourService.start();
    fixture.detectChanges();

    const overlay: HTMLElement = fixture.nativeElement.querySelector(
      '[data-testid="tour-overlay"]',
    );
    const focusable = overlay.querySelectorAll<HTMLElement>('button');
    const last = focusable[focusable.length - 1];
    const first = focusable[0];
    last.focus();

    const event = new KeyboardEvent('keydown', { key: 'Tab', bubbles: true, cancelable: true });
    Object.defineProperty(event, 'target', { value: last });
    overlay.dispatchEvent(event);

    expect(document.activeElement).toBe(first);
  });
});
