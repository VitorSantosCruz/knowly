import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { provideTransloco } from '@jsverse/transloco';
import { By } from '@angular/platform-browser';
import { DashboardPageComponent } from './dashboard-page.component';
import { MetricTileComponent } from './metric-tile.component';
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
    httpMock.expectOne('/api/tenants/metrics/articles/usage').flush({ articles: [] });
    httpMock
      .expectOne((r) => r.url === '/api/tenants/metrics/members' && !r.params.has('period'))
      .flush({ activeCount: 0, inactiveCount: 0 });
    httpMock
      .expectOne(
        (r) =>
          r.url === '/api/tenants/metrics/members/timeseries' && r.params.get('period') === '30d',
      )
      .flush({ days: [] });
    httpMock
      .expectOne((r) => r.url === '/api/tenants/metrics/articles/timeseries')
      .flush({ days: [] });

    const conversationsTimeseriesRequests = httpMock.match(
      (r) => r.url === '/api/tenants/metrics/conversations/timeseries',
    );
    expect(conversationsTimeseriesRequests.length).toBe(2);
    conversationsTimeseriesRequests.forEach((req) => req.flush({ days: [] }));

    const messagesTimeseriesRequests = httpMock.match(
      (r) => r.url === '/api/tenants/metrics/messages/timeseries',
    );
    expect(messagesTimeseriesRequests.length).toBe(3);
    messagesTimeseriesRequests.forEach((req) => req.flush({ days: [] }));
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

  it('composes every dashboard widget', () => {
    fixture.detectChanges();
    flushMetricRequests();

    expect(fixture.nativeElement.querySelector('[data-testid="article-count-tile"]')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('[data-testid="conversations-tile"]')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('[data-testid="user-messages-tile"]')).toBeTruthy();
    expect(
      fixture.nativeElement.querySelector('[data-testid="assistant-messages-tile"]'),
    ).toBeTruthy();
    expect(fixture.nativeElement.querySelector('[data-testid="active-members-tile"]')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('[data-testid="message-split-chart"]')).toBeTruthy();
    expect(
      fixture.nativeElement.querySelector('[data-testid="conversations-activity-chart"]'),
    ).toBeTruthy();
    expect(fixture.nativeElement.querySelector('[data-testid="top-articles-table"]')).toBeTruthy();
    expect(
      fixture.nativeElement.querySelector('[data-testid="members-breakdown-card"]'),
    ).toBeTruthy();
    expect(fixture.nativeElement.querySelector('[data-testid="export-button"]')).toBeTruthy();
  });

  it("fetches the active-members tile's data from the timeseries endpoint with the current period", () => {
    fixture.detectChanges();
    flushMetricRequests();

    // flushMetricRequests already asserts the /members/timeseries?period=30d request was made
    // (via httpMock.expectOne + httpMock.verify() in afterEach failing on any unmatched request).
    expect(true).toBe(true);
  });

  it('renders the active-members tile with sparklines enabled (no showSparkline override)', () => {
    fixture.detectChanges();
    flushMetricRequests();

    const activeMembersTile = fixture.debugElement
      .queryAll(By.css('[data-testid="active-members-tile"]'))
      .find((el) => el.componentInstance instanceof MetricTileComponent);

    const tileInstance = activeMembersTile?.componentInstance as MetricTileComponent | undefined;

    expect(tileInstance?.showSparkline()).toBe(true);
    expect(tileInstance?.sparklineSelector()).toBeDefined();
  });

  it("computes the active-members tile's headline value from the last day's count, not a sum", () => {
    fixture.detectChanges();

    const activeMembersTile = fixture.debugElement
      .queryAll(By.css('[data-testid="active-members-tile"]'))
      .find((el) => el.componentInstance instanceof MetricTileComponent);
    const tileInstance = activeMembersTile?.componentInstance as MetricTileComponent | undefined;

    const sample = {
      days: [
        { date: '2026-07-30', count: 10 },
        { date: '2026-07-31', count: 25 },
      ],
    };

    expect(tileInstance?.valueSelector()?.(sample)).toBe(25);

    flushMetricRequests();
  });

  it("reuses dailyCountSparklineSelector as the active-members tile's sparklineSelector", () => {
    fixture.detectChanges();

    const activeMembersTile = fixture.debugElement
      .queryAll(By.css('[data-testid="active-members-tile"]'))
      .find((el) => el.componentInstance instanceof MetricTileComponent);
    const articleCountTile = fixture.debugElement
      .queryAll(By.css('[data-testid="article-count-tile"]'))
      .find((el) => el.componentInstance instanceof MetricTileComponent);

    const activeMembersInstance = activeMembersTile?.componentInstance as
      MetricTileComponent | undefined;
    const articleCountInstance = articleCountTile?.componentInstance as
      MetricTileComponent | undefined;

    expect(activeMembersInstance?.sparklineSelector()).toBe(
      articleCountInstance?.sparklineSelector(),
    );

    flushMetricRequests();
  });
});
