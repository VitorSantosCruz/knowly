import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Chart } from 'chart.js';
import { ChartCanvasComponent } from './chart-canvas.component';

// No pre-existing Chart.js mocking pattern was found in
// conversations-activity-chart.component.spec.ts/message-split-chart.component.spec.ts
// (those specs assert on rendered markup only, not on Chart.js
// construction) — this is a new mocking pattern for this shared component.
vi.mock('chart.js', () => {
  const ChartMock = vi.fn().mockImplementation(function (this: { destroy: () => void }) {
    this.destroy = vi.fn();
  });
  return { Chart: ChartMock };
});

@Component({
  selector: 'app-host',
  imports: [ChartCanvasComponent],
  template: `<app-chart-canvas
    [type]="type()"
    [data]="data()"
    [options]="options()"
    [height]="height()"
  />`,
})
class HostComponent {
  readonly type = signal<'bar' | 'doughnut' | 'line'>('bar');
  readonly data = signal({ labels: ['a'], datasets: [{ data: [1] }] });
  readonly options = signal<Record<string, unknown> | undefined>(undefined);
  readonly height = signal('220px');
}

describe('ChartCanvasComponent', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  function harness() {
    TestBed.configureTestingModule({ imports: [HostComponent] });
    const fixture: ComponentFixture<HostComponent> = TestBed.createComponent(HostComponent);
    return { fixture };
  }

  it('constructs a Chart with the given type/data on init', async () => {
    const { fixture } = harness();
    fixture.detectChanges();
    await fixture.whenStable();

    expect(Chart).toHaveBeenCalledTimes(1);
    const [, config] = vi.mocked(Chart).mock.calls[0];
    expect(config).toMatchObject({
      type: 'bar',
      data: { labels: ['a'], datasets: [{ data: [1] }] },
    });
  });

  it('renders a canvas element sized by the height input', async () => {
    const { fixture } = harness();
    fixture.detectChanges();
    await fixture.whenStable();

    const canvas = fixture.nativeElement.querySelector('canvas');
    expect(canvas).toBeTruthy();
    expect(canvas.style.height).toBe('220px');
  });

  it('destroys the previous chart and creates a new one when data changes', async () => {
    const { fixture } = harness();
    fixture.detectChanges();
    await fixture.whenStable();

    const firstInstance = vi.mocked(Chart).mock.results[0].value;

    fixture.componentInstance.data.set({ labels: ['b'], datasets: [{ data: [2] }] });
    fixture.detectChanges();
    await fixture.whenStable();

    expect(firstInstance.destroy).toHaveBeenCalledTimes(1);
    expect(Chart).toHaveBeenCalledTimes(2);
  });

  it('destroys the chart on component destroy', async () => {
    const { fixture } = harness();
    fixture.detectChanges();
    await fixture.whenStable();

    const instance = vi.mocked(Chart).mock.results[0].value;
    fixture.destroy();

    expect(instance.destroy).toHaveBeenCalledTimes(1);
  });
});
