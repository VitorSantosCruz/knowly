import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CHART_CTOR, ChartCanvasComponent } from './chart-canvas.component';

// Fakes the Chart.js constructor via DI (see CHART_CTOR's doc comment)
// rather than `vi.mock('chart.js')` — module mocking proved unreliable
// when this spec ran alongside the other chart specs in a full-suite run,
// since Angular's unit-test builder bundles every spec file together and
// `chart.js` ends up a shared module instance rather than one scoped per
// spec file.
class FakeChart {
  static instances: FakeChart[] = [];
  readonly destroy = vi.fn();
  readonly ctx: unknown;
  readonly config: unknown;

  constructor(ctx: unknown, config: unknown) {
    this.ctx = ctx;
    this.config = config;
    FakeChart.instances.push(this);
  }
}

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
    FakeChart.instances = [];
  });

  function harness() {
    TestBed.configureTestingModule({
      imports: [HostComponent],
      providers: [{ provide: CHART_CTOR, useValue: FakeChart }],
    });
    const fixture: ComponentFixture<HostComponent> = TestBed.createComponent(HostComponent);
    return { fixture };
  }

  it('constructs a Chart with the given type/data on init', async () => {
    const { fixture } = harness();
    fixture.detectChanges();
    await fixture.whenStable();

    expect(FakeChart.instances).toHaveLength(1);
    expect(FakeChart.instances[0].config).toMatchObject({
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

    const firstInstance = FakeChart.instances[0];

    fixture.componentInstance.data.set({ labels: ['b'], datasets: [{ data: [2] }] });
    fixture.detectChanges();
    await fixture.whenStable();

    expect(firstInstance.destroy).toHaveBeenCalledTimes(1);
    expect(FakeChart.instances).toHaveLength(2);
  });

  it('destroys the chart on component destroy', async () => {
    const { fixture } = harness();
    fixture.detectChanges();
    await fixture.whenStable();

    const instance = FakeChart.instances[0];
    fixture.destroy();

    expect(instance.destroy).toHaveBeenCalledTimes(1);
  });
});
