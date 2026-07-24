import { createMetricWidgetHarness } from '../../testing/metric-widget-harness';
import { MessagesCardComponent } from './messages-card.component';

const URL = '/api/tenants/metrics/messages';

describe('MessagesCardComponent', () => {
  it('shows a loading state before the response arrives', () => {
    const { fixture, httpMock } = createMetricWidgetHarness(MessagesCardComponent);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="loading-state"]')).toBeTruthy();
    httpMock.expectOne(URL).flush({ sentCount: 0, receivedCount: 0 });
  });

  it('renders sent and received counts on success', () => {
    const { fixture, httpMock } = createMetricWidgetHarness(MessagesCardComponent);
    fixture.detectChanges();

    httpMock.expectOne(URL).flush({ sentCount: 12, receivedCount: 30 });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('12');
    expect(fixture.nativeElement.textContent).toContain('30');
  });

  it('shows an error state with the trace id on a network/server error', () => {
    const { fixture, httpMock } = createMetricWidgetHarness(MessagesCardComponent);
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
    const { fixture, httpMock } = createMetricWidgetHarness(MessagesCardComponent);
    fixture.detectChanges();

    httpMock
      .expectOne(URL)
      .flush({ code: 'PERMISSION_DENIED' }, { status: 403, statusText: 'Forbidden' });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="no-access-state"]')).toBeTruthy();
  });
});
