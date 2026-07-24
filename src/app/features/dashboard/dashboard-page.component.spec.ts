import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { provideTransloco } from '@jsverse/transloco';
import { DashboardPageComponent } from './dashboard-page.component';
import { TourService } from '../../core/tour.service';
import { FakeTranslocoLoader } from '../../testing/fake-transloco-loader';

describe('DashboardPageComponent', () => {
  let fixture: ComponentFixture<DashboardPageComponent>;
  let httpMock: HttpTestingController;
  let tourService: TourService;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DashboardPageComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        provideTransloco({
          config: { availableLangs: ['en', 'pt-BR'], defaultLang: 'en' },
          loader: FakeTranslocoLoader,
        }),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(DashboardPageComponent);
    httpMock = TestBed.inject(HttpTestingController);
    tourService = TestBed.inject(TourService);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('renders the dashboard root', () => {
    fixture.detectChanges();
    httpMock.expectOne('/api/users/me/onboarding-status').flush({ completed: true });

    expect(fixture.nativeElement.querySelector('[data-testid="dashboard-page"]')).toBeTruthy();
  });

  it('starts the tour automatically when onboarding is not yet completed', () => {
    const startSpy = vi.spyOn(tourService, 'start');
    fixture.detectChanges();

    httpMock.expectOne('/api/users/me/onboarding-status').flush({ completed: false });
    fixture.detectChanges();

    expect(startSpy).toHaveBeenCalled();
  });

  it('does not start the tour automatically when onboarding was already completed', () => {
    const startSpy = vi.spyOn(tourService, 'start');
    fixture.detectChanges();

    httpMock.expectOne('/api/users/me/onboarding-status').flush({ completed: true });
    fixture.detectChanges();

    expect(startSpy).not.toHaveBeenCalled();
  });

  it('links to the articles screen', () => {
    fixture.detectChanges();
    httpMock.expectOne('/api/users/me/onboarding-status').flush({ completed: true });

    expect(fixture.nativeElement.querySelector('[data-testid="articles-link"]')).toBeTruthy();
  });
});
