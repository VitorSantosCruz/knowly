import { MetricTileComponent, toSparklineData } from './metric-tile.component';
import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideTransloco } from '@jsverse/transloco';
import { FakeTranslocoLoader } from '../../testing/fake-transloco-loader';
import { Period } from './period-filter.component';

describe('toSparklineData', () => {
  it('maps day/count rows to a labels/datasets chart shape', () => {
    const result = toSparklineData([
      { date: '2026-07-01', count: 3 },
      { date: '2026-07-02', count: 5 },
    ]);

    expect(result).toEqual({
      labels: ['2026-07-01', '2026-07-02'],
      datasets: [{ data: [3, 5] }],
    });
  });

  it('returns an empty shape for no days', () => {
    expect(toSparklineData([])).toEqual({ labels: [], datasets: [{ data: [] }] });
  });
});

interface TimeseriesResponse {
  days: { date: string; count: number }[];
}

@Component({
  selector: 'app-host',
  imports: [MetricTileComponent],
  template: `
    <app-metric-tile
      testId="conversations-tile"
      [period]="period()"
      url="/api/tenants/metrics/conversations/timeseries"
      label="Conversations"
      [valueSelector]="valueSelector"
      [sparklineSelector]="sparklineSelector"
    />
  `,
})
class HostComponent {
  readonly period = signal<Period>('30d');
  readonly valueSelector = (data: unknown) =>
    (data as TimeseriesResponse).days.reduce((sum, day) => sum + day.count, 0);
  readonly sparklineSelector = (data: unknown) => (data as TimeseriesResponse).days;
}

describe('MetricTileComponent', () => {
  const URL = '/api/tenants/metrics/conversations/timeseries';

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

  it('renders the value and sparkline data from the fetched response', () => {
    const { fixture, httpMock } = harness();
    fixture.detectChanges();

    httpMock
      .expectOne((r) => r.url === URL)
      .flush({
        days: [
          { date: '2026-07-01', count: 3 },
          { date: '2026-07-02', count: 5 },
        ],
      });
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[data-testid="conversations-tile"]').textContent,
    ).toContain('8');
    expect(fixture.nativeElement.querySelector('app-chart-canvas')).toBeTruthy();
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
