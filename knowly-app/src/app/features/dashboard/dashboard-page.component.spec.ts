import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { provideTransloco } from '@jsverse/transloco';
import { DashboardPageComponent } from './dashboard-page.component';
import { FakeTranslocoLoader } from '../../testing/fake-transloco-loader';

describe('DashboardPageComponent', () => {
  let fixture: ComponentFixture<DashboardPageComponent>;
  let httpMock: HttpTestingController;

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
  });

  afterEach(() => {
    httpMock.verify();
  });

  function flushMetricRequests() {
    httpMock.expectOne('/api/tenants/metrics/articles').flush({ totalCount: 0 });
    httpMock.expectOne('/api/tenants/metrics/articles/usage').flush({ articles: [] });
    httpMock.expectOne('/api/tenants/metrics/conversations').flush({ startedCount: 0 });
    httpMock.expectOne('/api/tenants/metrics/messages').flush({ sentCount: 0, receivedCount: 0 });
    httpMock.expectOne('/api/tenants/metrics/members').flush({ activeCount: 0, inactiveCount: 0 });
  }

  it('renders the dashboard root', () => {
    fixture.detectChanges();
    flushMetricRequests();

    expect(fixture.nativeElement.querySelector('[data-testid="dashboard-page"]')).toBeTruthy();
  });

  it('links to the articles screen', () => {
    fixture.detectChanges();
    flushMetricRequests();

    expect(fixture.nativeElement.querySelector('[data-testid="articles-link"]')).toBeTruthy();
  });

  it('composes all four metric widgets', () => {
    fixture.detectChanges();
    flushMetricRequests();

    expect(fixture.nativeElement.querySelector('[data-testid="article-count-card"]')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('[data-testid="top-articles-table"]')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('[data-testid="conversations-card"]')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('[data-testid="messages-card"]')).toBeTruthy();
    expect(
      fixture.nativeElement.querySelector('[data-testid="members-breakdown-card"]'),
    ).toBeTruthy();
    expect(fixture.nativeElement.querySelector('[data-testid="export-button"]')).toBeTruthy();
  });
});
