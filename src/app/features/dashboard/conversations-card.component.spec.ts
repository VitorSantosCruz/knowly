import { createMetricWidgetHarness } from '../../testing/metric-widget-harness';
import { ConversationsCardComponent } from './conversations-card.component';

const URL = '/api/tenants/metrics/conversations';

describe('ConversationsCardComponent', () => {
  it('shows a loading state before the response arrives', () => {
    const { fixture, httpMock } = createMetricWidgetHarness(ConversationsCardComponent);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="loading-state"]')).toBeTruthy();
    httpMock.expectOne(URL).flush({ startedCount: 0 });
  });

  it('renders the started-conversations count on success', () => {
    const { fixture, httpMock } = createMetricWidgetHarness(ConversationsCardComponent);
    fixture.detectChanges();

    httpMock.expectOne(URL).flush({ startedCount: 17 });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('17');
  });

  it('shows an error state with the trace id on a network/server error', () => {
    const { fixture, httpMock } = createMetricWidgetHarness(ConversationsCardComponent);
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
    const { fixture, httpMock } = createMetricWidgetHarness(ConversationsCardComponent);
    fixture.detectChanges();

    httpMock
      .expectOne(URL)
      .flush({ code: 'PERMISSION_DENIED' }, { status: 403, statusText: 'Forbidden' });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="no-access-state"]')).toBeTruthy();
  });
});
