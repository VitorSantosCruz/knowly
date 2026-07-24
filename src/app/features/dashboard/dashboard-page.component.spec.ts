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

  function flushMetricRequests() {
    httpMock.expectOne('/api/tenants/metrics/articles').flush({ totalCount: 0 });
    httpMock.expectOne('/api/tenants/metrics/articles/usage').flush({ articles: [] });
    httpMock.expectOne('/api/tenants/metrics/conversations').flush({ startedCount: 0 });
    httpMock.expectOne('/api/tenants/metrics/messages').flush({ sentCount: 0, receivedCount: 0 });
  }

  it('renders the dashboard root', () => {
    fixture.detectChanges();
    httpMock.expectOne('/api/users/me/onboarding-status').flush({ completed: true });
    flushMetricRequests();

    expect(fixture.nativeElement.querySelector('[data-testid="dashboard-page"]')).toBeTruthy();
  });

  it('starts the tour automatically when onboarding is not yet completed', () => {
    const startSpy = vi.spyOn(tourService, 'start');
    fixture.detectChanges();

    httpMock.expectOne('/api/users/me/onboarding-status').flush({ completed: false });
    fixture.detectChanges();
    flushMetricRequests();

    expect(startSpy).toHaveBeenCalled();
  });

  it('does not start the tour automatically when onboarding was already completed', () => {
    const startSpy = vi.spyOn(tourService, 'start');
    fixture.detectChanges();

    httpMock.expectOne('/api/users/me/onboarding-status').flush({ completed: true });
    fixture.detectChanges();
    flushMetricRequests();

    expect(startSpy).not.toHaveBeenCalled();
  });

  it('links to the articles screen', () => {
    fixture.detectChanges();
    httpMock.expectOne('/api/users/me/onboarding-status').flush({ completed: true });
    flushMetricRequests();

    expect(fixture.nativeElement.querySelector('[data-testid="articles-link"]')).toBeTruthy();
  });

  it('composes all four metric widgets', () => {
    fixture.detectChanges();
    httpMock.expectOne('/api/users/me/onboarding-status').flush({ completed: true });
    flushMetricRequests();

    expect(fixture.nativeElement.querySelector('[data-testid="article-count-card"]')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('[data-testid="article-usage-list"]')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('[data-testid="conversations-card"]')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('[data-testid="messages-card"]')).toBeTruthy();
  });
});
