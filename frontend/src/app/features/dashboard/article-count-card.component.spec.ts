import { createMetricWidgetHarness } from '../../testing/metric-widget-harness';
import { ArticleCountCardComponent } from './article-count-card.component';

const URL = '/api/tenants/metrics/articles';

describe('ArticleCountCardComponent', () => {
  it('shows a loading state before the response arrives', () => {
    const { fixture, httpMock } = createMetricWidgetHarness(ArticleCountCardComponent);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="loading-state"]')).toBeTruthy();
    httpMock.expectOne(URL).flush({ totalCount: 3 });
  });

  it('renders the article count on success', () => {
    const { fixture, httpMock } = createMetricWidgetHarness(ArticleCountCardComponent);
    fixture.detectChanges();

    httpMock.expectOne(URL).flush({ totalCount: 42 });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('42');
  });

  it('shows an error state with the trace id on a network/server error', () => {
    const { fixture, httpMock } = createMetricWidgetHarness(ArticleCountCardComponent);
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
    const { fixture, httpMock } = createMetricWidgetHarness(ArticleCountCardComponent);
    fixture.detectChanges();

    httpMock
      .expectOne(URL)
      .flush({ code: 'PERMISSION_DENIED' }, { status: 403, statusText: 'Forbidden' });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="no-access-state"]')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('[data-testid="error-state"]')).toBeFalsy();
  });
});
