import { createMetricWidgetHarness } from '../../testing/metric-widget-harness';
import { ArticleUsageListComponent } from './article-usage-list.component';

const URL = '/api/tenants/metrics/articles/usage';

describe('ArticleUsageListComponent', () => {
  it('shows a loading state before the response arrives', () => {
    const { fixture, httpMock } = createMetricWidgetHarness(ArticleUsageListComponent);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="loading-state"]')).toBeTruthy();
    httpMock.expectOne(URL).flush({ articles: [] });
  });

  it('renders the articles most-used first', () => {
    const { fixture, httpMock } = createMetricWidgetHarness(ArticleUsageListComponent);
    fixture.detectChanges();

    httpMock.expectOne(URL).flush({
      articles: [
        { id: 1, title: 'Onboarding guide', useCount: 50 },
        { id: 2, title: 'API reference', useCount: 10 },
      ],
    });
    fixture.detectChanges();

    const items: NodeListOf<HTMLElement> = fixture.nativeElement.querySelectorAll(
      '[data-testid="usage-item"]',
    );
    expect(items).toHaveLength(2);
    expect(items[0].textContent).toContain('Onboarding guide');
  });

  it('shows an error state with the trace id on a network/server error', () => {
    const { fixture, httpMock } = createMetricWidgetHarness(ArticleUsageListComponent);
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

    expect(fixture.nativeElement.querySelector('[data-testid="error-state"]')).toBeTruthy();
  });

  it('shows a no-access state on a permission-denied response', () => {
    const { fixture, httpMock } = createMetricWidgetHarness(ArticleUsageListComponent);
    fixture.detectChanges();

    httpMock
      .expectOne(URL)
      .flush({ code: 'PERMISSION_DENIED' }, { status: 403, statusText: 'Forbidden' });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="no-access-state"]')).toBeTruthy();
  });
});
