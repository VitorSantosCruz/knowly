import {
  AfterViewInit,
  Component,
  ElementRef,
  InjectionToken,
  OnDestroy,
  effect,
  inject,
  input,
  signal,
  viewChild,
} from '@angular/core';
import {
  ArcElement,
  BarController,
  BarElement,
  CategoryScale,
  Chart,
  ChartConfiguration,
  ChartData,
  ChartType,
  DoughnutController,
  Filler,
  Legend,
  LineController,
  LineElement,
  LinearScale,
  PointElement,
  Tooltip,
} from 'chart.js';

// Chart.js's tree-shakeable core (imported from `chart.js`, not `chart.js/auto`) renders
// nothing until its controllers/elements/scales are registered — every chart type this app
// renders (line/bar/doughnut) must be registered here once, globally.
Chart.register(
  LineController,
  BarController,
  DoughnutController,
  LineElement,
  BarElement,
  ArcElement,
  PointElement,
  CategoryScale,
  LinearScale,
  Legend,
  Tooltip,
  Filler,
);

/**
 * Injectable seam for the Chart.js constructor. Defaults to the real
 * `Chart` class; overridden in specs (via `TestBed.overrideProvider`) to
 * avoid depending on `vi.mock('chart.js')` module-cache behavior, which
 * proved unreliable when this component's spec ran alongside the other
 * chart specs in the same full-suite run (Angular's unit-test builder
 * bundles all spec files together, so `chart.js` is a shared module
 * instance across files rather than one scoped per spec file).
 */
export const CHART_CTOR = new InjectionToken<typeof Chart>('CHART_CTOR', {
  factory: () => Chart,
});

// Replaces PrimeNG's `UIChart` (`p-chart`) wrapper — Chart.js is already a
// direct dependency, so this thin standalone component instantiates it
// directly instead of pulling in a new charting library. See
// specify/features/primeng-removal/PLAN.md.
@Component({
  selector: 'app-chart-canvas',
  template: `
    <div class="relative w-full" [style.height]="height()">
      <canvas #canvas class="absolute inset-0 h-full w-full"></canvas>
    </div>
  `,
})
export class ChartCanvasComponent implements AfterViewInit, OnDestroy {
  readonly type = input.required<ChartType>();
  readonly data = input.required<ChartData>();
  readonly options = input<Record<string, unknown>>();
  readonly height = input<string>('220px');

  private readonly canvasRef = viewChild.required<ElementRef<HTMLCanvasElement>>('canvas');
  private readonly viewReady = signal(false);
  private readonly chartCtor = inject(CHART_CTOR);

  private chart?: Chart;

  constructor() {
    effect(() => {
      const type = this.type();
      const data = this.data();
      const options = this.options();

      if (!this.viewReady()) {
        return;
      }

      this.chart?.destroy();
      this.chart = new this.chartCtor(this.canvasRef().nativeElement, {
        type,
        data,
        options: {
          responsive: true,
          maintainAspectRatio: false,
          ...options,
        },
      } as ChartConfiguration);
    });
  }

  ngAfterViewInit(): void {
    this.viewReady.set(true);
  }

  ngOnDestroy(): void {
    this.chart?.destroy();
  }
}
