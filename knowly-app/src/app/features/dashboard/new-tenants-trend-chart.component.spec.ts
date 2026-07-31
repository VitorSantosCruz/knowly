import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTransloco } from '@jsverse/transloco';
import { FakeTranslocoLoader } from '../../testing/fake-transloco-loader';
import {
  NewTenantsTrendChartComponent,
  toNewTenantsChartData,
} from './new-tenants-trend-chart.component';
import { DailyCountRow } from './trend-chart-data';

describe('toNewTenantsChartData', () => {
  it('maps day/count rows to a labels/datasets shape', () => {
    const result = toNewTenantsChartData([
      { date: '2026-07-01', count: 3 },
      { date: '2026-07-02', count: 5 },
    ]);

    expect(result).toEqual({
      labels: ['2026-07-01', '2026-07-02'],
      datasets: [{ data: [3, 5] }],
    });
  });

  it('returns an empty shape for no rows', () => {
    expect(toNewTenantsChartData([])).toEqual({ labels: [], datasets: [{ data: [] }] });
  });
});

@Component({
  selector: 'app-host',
  imports: [NewTenantsTrendChartComponent],
  template: `<app-new-tenants-trend-chart [data]="data" [error]="error" />`,
})
class HostComponent {
  data: DailyCountRow[] = [];
  error = false;
}

describe('NewTenantsTrendChartComponent', () => {
  let fixture: ComponentFixture<HostComponent>;

  function harness() {
    TestBed.configureTestingModule({
      imports: [HostComponent],
      providers: [
        provideTransloco({
          config: { availableLangs: ['en', 'pt-BR'], defaultLang: 'en' },
          loader: FakeTranslocoLoader,
        }),
      ],
    });
    fixture = TestBed.createComponent(HostComponent);
  }

  it('renders app-chart-canvas with the mapped data when data is set', () => {
    harness();
    fixture.componentInstance.data = [
      { date: '2026-07-01', count: 3 },
      { date: '2026-07-02', count: 5 },
    ];
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('app-chart-canvas')).toBeTruthy();
  });

  it('renders app-error-state when error is true', () => {
    harness();
    fixture.componentInstance.error = true;
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="error-state"]')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('app-chart-canvas')).toBeFalsy();
  });

  it('renders a sr-only mirror table matching the input rows', () => {
    harness();
    fixture.componentInstance.data = [
      { date: '2026-07-01', count: 3 },
      { date: '2026-07-02', count: 5 },
    ];
    fixture.detectChanges();

    const table = fixture.nativeElement.querySelector('table.sr-only');
    expect(table).toBeTruthy();
    const rows = table.querySelectorAll('tbody tr');
    expect(rows).toHaveLength(2);
    expect(rows[0].textContent).toContain('2026-07-01');
    expect(rows[0].textContent).toContain('3');
    expect(rows[1].textContent).toContain('2026-07-02');
    expect(rows[1].textContent).toContain('5');
  });
});
