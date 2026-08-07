import { Component, signal } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTransloco } from '@jsverse/transloco';
import { FakeTranslocoLoader } from '../../testing/fake-transloco-loader';
import { MessageSplitChartComponent, toDonutData } from './message-split-chart.component';
import { Period } from './period-filter.component';

describe('toDonutData', () => {
  it('sums USER/ASSISTANT counts across days into a labels/datasets shape', () => {
    const result = toDonutData({
      days: [
        { date: '2026-07-01', userCount: 3, assistantCount: 4 },
        { date: '2026-07-02', userCount: 2, assistantCount: 5 },
      ],
    });

    expect(result).toEqual({
      labels: ['USER', 'ASSISTANT'],
      datasets: [{ data: [5, 9] }],
    });
  });

  it('returns zeros for no days', () => {
    expect(toDonutData({ days: [] })).toEqual({
      labels: ['USER', 'ASSISTANT'],
      datasets: [{ data: [0, 0] }],
    });
  });
});

@Component({
  selector: 'app-host',
  imports: [MessageSplitChartComponent],
  template: `<app-message-split-chart [period]="period()" />`,
})
class HostComponent {
  readonly period = signal<Period>('30d');
}

describe('MessageSplitChartComponent', () => {
  const URL = '/api/tenants/metrics/messages/timeseries';

  function harness() {
    TestBed.configureTestingModule({
      imports: [HostComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideTransloco({
          config: { availableLangs: ['en', 'pt-BR'], defaultLang: 'en' },
          loader: FakeTranslocoLoader,
        }),
      ],
    });
    const fixture: ComponentFixture<HostComponent> = TestBed.createComponent(HostComponent);
    const httpMock = TestBed.inject(HttpTestingController);
    return { fixture, httpMock };
  }

  it('shows a loading state before the response arrives', () => {
    const { fixture, httpMock } = harness();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="loading-state"]')).toBeTruthy();
    httpMock.expectOne((r) => r.url === URL).flush({ days: [] });
  });

  it('renders a doughnut chart and a sr-only mirror table on success', () => {
    const { fixture, httpMock } = harness();
    fixture.detectChanges();

    httpMock
      .expectOne((r) => r.url === URL)
      .flush({
        days: [{ date: '2026-07-01', userCount: 3, assistantCount: 4 }],
      });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('app-chart-canvas')).toBeTruthy();
    const table = fixture.nativeElement.querySelector('[data-testid="a11y-table"] table');
    expect(table).toBeTruthy();
    const rows = table.querySelectorAll('tbody tr');
    expect(rows).toHaveLength(2);
  });

  it('shows an error state with the trace id on a network/server error', () => {
    const { fixture, httpMock } = harness();
    fixture.detectChanges();

    httpMock
      .expectOne((r) => r.url === URL)
      .flush(
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
    const { fixture, httpMock } = harness();
    fixture.detectChanges();

    httpMock
      .expectOne((r) => r.url === URL)
      .flush({ code: 'PERMISSION_DENIED' }, { status: 403, statusText: 'Forbidden' });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="no-access-state"]')).toBeTruthy();
  });

  it('re-fetches with the new period when the period input changes', () => {
    const { fixture, httpMock } = harness();
    fixture.detectChanges();

    httpMock
      .expectOne((r) => r.url === URL && r.params.get('period') === '30d')
      .flush({ days: [] });

    fixture.componentInstance.period.set('7d');
    fixture.detectChanges();

    httpMock.expectOne((r) => r.url === URL && r.params.get('period') === '7d').flush({ days: [] });
  });
});
