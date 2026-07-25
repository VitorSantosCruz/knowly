import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideTransloco } from '@jsverse/transloco';
import { HelpMenuComponent } from './help-menu.component';
import { TourService } from '../core/tour.service';
import { FakeTranslocoLoader } from '../testing/fake-transloco-loader';

describe('HelpMenuComponent', () => {
  let fixture: ComponentFixture<HelpMenuComponent>;
  let tourService: TourService;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [HelpMenuComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideTransloco({
          config: { availableLangs: ['en', 'pt-BR'], defaultLang: 'en' },
          loader: FakeTranslocoLoader,
        }),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(HelpMenuComponent);
    tourService = TestBed.inject(TourService);
    fixture.detectChanges();
  });

  it('opens the menu and restarts the tour, regardless of prior onboarding state', () => {
    const startSpy = vi.spyOn(tourService, 'start');

    const toggle: HTMLButtonElement = fixture.nativeElement.querySelector(
      '[data-testid="help-menu-toggle"]',
    );
    toggle.click();
    fixture.detectChanges();

    const restartButton: HTMLButtonElement = fixture.nativeElement.querySelector(
      '[data-testid="restart-tour"]',
    );
    restartButton.click();

    expect(startSpy).toHaveBeenCalled();
  });
});
