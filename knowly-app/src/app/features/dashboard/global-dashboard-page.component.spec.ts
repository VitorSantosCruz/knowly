import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideTransloco } from '@jsverse/transloco';
import { GlobalDashboardPageComponent } from './global-dashboard-page.component';
import { FakeTranslocoLoader } from '../../testing/fake-transloco-loader';

const METRICS_URL = '/api/staff/metrics/global';
const TRENDS_URL = '/api/staff/metrics/global/trends';

const SAMPLE_METRICS = {
  tenantCount: 12,
  newTenantsThisMonth: 3,
  articlesReadTotal: 999,
  staffCount: 7,
};

function sampleTrends(overrides: Partial<Record<string, unknown>> = {}) {
  return {
    newTenantsPerDay: [{ date: '2026-07-01', count: 3 }],
    articlesReadPerDay: [{ date: '2026-07-01', count: 10 }],
    totalTenants: { current: 12, previous: 10, percentChange: 20 },
    newTenants: { current: 3, previous: 2, percentChange: 50 },
    totalArticlesRead: { current: 999, previous: 900, percentChange: 11 },
    staffCount: { current: 7, previous: 6, percentChange: 16.7 },
    totalTenantsPerDay: [{ date: '2026-07-01', count: 12 }],
    staffCountPerDay: [{ date: '2026-07-01', count: 7 }],
    ...overrides,
  };
}

describe('GlobalDashboardPageComponent', () => {
  let fixture: ComponentFixture<GlobalDashboardPageComponent>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [GlobalDashboardPageComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideTransloco({
          config: { availableLangs: ['en', 'pt-BR'], defaultLang: 'en' },
          loader: FakeTranslocoLoader,
        }),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(GlobalDashboardPageComponent);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('renders 4 populated stat cards plus 1 disabled tile after a successful fetch', () => {
    fixture.detectChanges();

    httpMock.expectOne((r) => r.url === METRICS_URL).flush(SAMPLE_METRICS);
    fixture.detectChanges();
    httpMock.expectOne((r) => r.url === TRENDS_URL).flush(sampleTrends());
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[data-testid="tenant-count-tile"]').textContent,
    ).toContain('12');
    expect(
      fixture.nativeElement.querySelector('[data-testid="new-tenants-tile"]').textContent,
    ).toContain('3');
    expect(
      fixture.nativeElement.querySelector('[data-testid="articles-read-tile"]').textContent,
    ).toContain('999');
    expect(
      fixture.nativeElement.querySelector('[data-testid="staff-count-tile"]').textContent,
    ).toContain('7');
    expect(
      fixture.nativeElement
        .querySelector('[data-testid="support-tickets-tile"]')
        .querySelector('[data-testid="stat-card-coming-soon"]'),
    ).toBeTruthy();
  });

  it('renders app-no-access-state once at the page level on a 403', () => {
    fixture.detectChanges();

    httpMock
      .expectOne((r) => r.url === METRICS_URL)
      .flush({ code: 'PERMISSION_DENIED' }, { status: 403, statusText: 'Forbidden' });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelectorAll('[data-testid="no-access-state"]').length).toBe(
      1,
    );
    expect(fixture.nativeElement.querySelector('[data-testid="tenant-count-tile"]')).toBeFalsy();
  });

  it('renders app-error-state on a non-403 error', () => {
    fixture.detectChanges();

    httpMock
      .expectOne((r) => r.url === METRICS_URL)
      .flush({ message: 'boom' }, { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="error-state"]')).toBeTruthy();
  });

  it('REQ-7: never attempts /trends when /global itself fails', () => {
    fixture.detectChanges();

    httpMock
      .expectOne((r) => r.url === METRICS_URL)
      .flush({ message: 'boom' }, { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();

    httpMock.expectNone(TRENDS_URL);
  });

  it('REQ-8: metrics succeeds, trends fails -> cards show current values with no badge, charts show error state', () => {
    fixture.detectChanges();

    httpMock.expectOne((r) => r.url === METRICS_URL).flush(SAMPLE_METRICS);
    fixture.detectChanges();
    httpMock
      .expectOne((r) => r.url === TRENDS_URL)
      .flush({ message: 'boom' }, { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();

    const el = fixture.nativeElement;
    expect(el.querySelector('[data-testid="tenant-count-tile"]').textContent).toContain('12');
    expect(el.querySelectorAll('[data-testid="stat-card-badge"]').length).toBe(0);
    expect(el.querySelectorAll('[data-testid="error-state"]').length).toBeGreaterThanOrEqual(2);
    expect(el.querySelector('[data-testid="global-dashboard-page"]')).toBeTruthy();
  });

  it('REQ-9: period=all shows no badges even when the backend sends a non-null percentChange', () => {
    fixture.detectChanges();

    httpMock.expectOne((r) => r.url === METRICS_URL).flush(SAMPLE_METRICS);
    fixture.detectChanges();
    httpMock.expectOne((r) => r.url === TRENDS_URL).flush(sampleTrends());
    fixture.detectChanges();

    fixture.nativeElement.querySelector('[data-testid="period-option-all"]').click();
    fixture.detectChanges();

    httpMock
      .expectOne((r) => r.url === TRENDS_URL && r.params.get('period') === 'all')
      .flush(sampleTrends());
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelectorAll('[data-testid="stat-card-badge"]').length).toBe(
      0,
    );
  });

  it('REQ-10: a metric with percentChange null renders that card with no badge, others with theirs', () => {
    fixture.detectChanges();

    httpMock.expectOne((r) => r.url === METRICS_URL).flush(SAMPLE_METRICS);
    fixture.detectChanges();
    httpMock
      .expectOne((r) => r.url === TRENDS_URL)
      .flush(sampleTrends({ newTenants: { current: 3, previous: 0, percentChange: null } }));
    fixture.detectChanges();

    const el = fixture.nativeElement;
    expect(
      el
        .querySelector('[data-testid="new-tenants-tile"]')
        .querySelector('[data-testid="stat-card-badge"]'),
    ).toBeFalsy();
    expect(
      el
        .querySelector('[data-testid="tenant-count-tile"]')
        .querySelector('[data-testid="stat-card-badge"]'),
    ).toBeTruthy();
  });

  it('changing the period triggers exactly one new /trends request and none to /global', () => {
    fixture.detectChanges();

    httpMock.expectOne((r) => r.url === METRICS_URL).flush(SAMPLE_METRICS);
    fixture.detectChanges();
    httpMock
      .expectOne((r) => r.url === TRENDS_URL && r.params.get('period') === '30d')
      .flush(sampleTrends());
    fixture.detectChanges();

    fixture.nativeElement.querySelector('[data-testid="period-option-7d"]').click();
    fixture.detectChanges();

    httpMock
      .expectOne((r) => r.url === TRENDS_URL && r.params.get('period') === '7d')
      .flush(sampleTrends());
    httpMock.expectNone(METRICS_URL);
  });

  it('renders the two trend charts and the coming-soon tile as a disabled stat card', () => {
    fixture.detectChanges();

    httpMock.expectOne((r) => r.url === METRICS_URL).flush(SAMPLE_METRICS);
    fixture.detectChanges();
    httpMock.expectOne((r) => r.url === TRENDS_URL).flush(sampleTrends());
    fixture.detectChanges();

    const el = fixture.nativeElement;
    expect(el.querySelector('app-new-tenants-trend-chart')).toBeTruthy();
    expect(el.querySelector('app-articles-read-trend-chart')).toBeTruthy();
    expect(
      el
        .querySelector('[data-testid="support-tickets-tile"]')
        .querySelector('[data-testid="stat-card-coming-soon"]'),
    ).toBeTruthy();
  });

  it('REQ-1: after a successful trends fetch, all four cards render a sparkline chart', () => {
    fixture.detectChanges();

    httpMock.expectOne((r) => r.url === METRICS_URL).flush(SAMPLE_METRICS);
    fixture.detectChanges();
    httpMock.expectOne((r) => r.url === TRENDS_URL).flush(sampleTrends());
    fixture.detectChanges();

    const el = fixture.nativeElement;
    for (const testId of [
      'tenant-count-tile',
      'new-tenants-tile',
      'articles-read-tile',
      'staff-count-tile',
    ]) {
      expect(
        el.querySelector(`[data-testid="${testId}"] app-chart-canvas`),
        `${testId} should render a sparkline`,
      ).toBeTruthy();
    }
    expect(el.querySelector('[data-testid="support-tickets-tile"] app-chart-canvas')).toBeFalsy();
  });

  it('REQ-4/6: before the first successful trends fetch, no card renders a sparkline', () => {
    fixture.detectChanges();

    httpMock.expectOne((r) => r.url === METRICS_URL).flush(SAMPLE_METRICS);
    fixture.detectChanges();

    const el = fixture.nativeElement;
    for (const testId of [
      'tenant-count-tile',
      'new-tenants-tile',
      'articles-read-tile',
      'staff-count-tile',
    ]) {
      expect(el.querySelector(`[data-testid="${testId}"] app-chart-canvas`)).toBeFalsy();
    }

    httpMock.expectOne((r) => r.url === TRENDS_URL);
  });

  it('REQ-5: a trends fetch failing after a prior success leaves sparklines rendered', () => {
    fixture.detectChanges();

    httpMock.expectOne((r) => r.url === METRICS_URL).flush(SAMPLE_METRICS);
    fixture.detectChanges();
    httpMock.expectOne((r) => r.url === TRENDS_URL).flush(sampleTrends());
    fixture.detectChanges();

    fixture.nativeElement.querySelector('[data-testid="period-option-7d"]').click();
    fixture.detectChanges();

    httpMock
      .expectOne((r) => r.url === TRENDS_URL && r.params.get('period') === '7d')
      .flush({ message: 'boom' }, { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();

    const el = fixture.nativeElement;
    for (const testId of [
      'tenant-count-tile',
      'new-tenants-tile',
      'articles-read-tile',
      'staff-count-tile',
    ]) {
      expect(el.querySelector(`[data-testid="${testId}"] app-chart-canvas`)).toBeTruthy();
    }
  });
});
