import { createMetricWidgetHarness } from '../../testing/metric-widget-harness';
import { TopArticlesTableComponent } from './top-articles-table.component';

const URL = '/api/tenants/metrics/articles/usage';

describe('TopArticlesTableComponent', () => {
  it('shows a loading state before the response arrives', () => {
    const { fixture, httpMock } = createMetricWidgetHarness(TopArticlesTableComponent);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="loading-state"]')).toBeTruthy();
    httpMock.expectOne(URL).flush({ articles: [] });
  });

  it('renders a table row per article on success', () => {
    const { fixture, httpMock } = createMetricWidgetHarness(TopArticlesTableComponent);
    fixture.detectChanges();

    httpMock.expectOne(URL).flush({
      articles: [
        { id: 1, title: 'Onboarding guide', useCount: 50 },
        { id: 2, title: 'API reference', useCount: 10 },
      ],
    });
    fixture.detectChanges();

    const rows: NodeListOf<HTMLElement> = fixture.nativeElement.querySelectorAll(
      '[data-testid="article-row"]',
    );
    expect(rows).toHaveLength(2);
    expect(rows[0].textContent).toContain('Onboarding guide');
  });

  it('filters rendered rows by title via the search input', () => {
    const { fixture, httpMock } = createMetricWidgetHarness(TopArticlesTableComponent);
    fixture.detectChanges();

    httpMock.expectOne(URL).flush({
      articles: [
        { id: 1, title: 'Onboarding guide', useCount: 50 },
        { id: 2, title: 'API reference', useCount: 10 },
      ],
    });
    fixture.detectChanges();

    vi.useFakeTimers();
    const input: HTMLInputElement = fixture.nativeElement.querySelector(
      '[data-testid="article-search"]',
    );
    input.value = 'API';
    input.dispatchEvent(new Event('input'));
    vi.runAllTimers();
    vi.useRealTimers();
    fixture.detectChanges();

    const rows: NodeListOf<HTMLElement> = fixture.nativeElement.querySelectorAll(
      '[data-testid="article-row"]',
    );
    expect(rows).toHaveLength(1);
    expect(rows[0].textContent).toContain('API reference');
  });

  it('shows an error state with the trace id on a network/server error', () => {
    const { fixture, httpMock } = createMetricWidgetHarness(TopArticlesTableComponent);
    fixture.detectChanges();

    httpMock.expectOne(URL).flush(
      { message: 'boom' },
      {
        status: 500,
        statusText: 'Server Error',
        headers: { traceparent: '00-abc123def456-0000000000000001-01' },
      },
    );
    fixture.detectChanges();

    const errorState = fixture.nativeElement.querySelector('[data-testid="error-state"]');
    expect(errorState).toBeTruthy();
    expect(errorState.textContent).toContain('abc123def456');
  });

  it('shows a no-access state on a permission-denied response', () => {
    const { fixture, httpMock } = createMetricWidgetHarness(TopArticlesTableComponent);
    fixture.detectChanges();

    httpMock
      .expectOne(URL)
      .flush({ code: 'PERMISSION_DENIED' }, { status: 403, statusText: 'Forbidden' });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="no-access-state"]')).toBeTruthy();
  });
});
