import {
  AfterViewInit,
  Component,
  ElementRef,
  OnDestroy,
  effect,
  input,
  signal,
  viewChild,
} from '@angular/core';
import { Chart, ChartConfiguration, ChartData, ChartType } from 'chart.js';

// Replaces PrimeNG's `UIChart` (`p-chart`) wrapper — Chart.js is already a
// direct dependency, so this thin standalone component instantiates it
// directly instead of pulling in a new charting library. See
// specify/features/primeng-removal/PLAN.md.
@Component({
  selector: 'app-chart-canvas',
  template: `<canvas #canvas [style.height]="height()"></canvas>`,
})
export class ChartCanvasComponent implements AfterViewInit, OnDestroy {
  readonly type = input.required<ChartType>();
  readonly data = input.required<ChartData>();
  readonly options = input<Record<string, unknown>>();
  readonly height = input<string>('220px');

  private readonly canvasRef = viewChild.required<ElementRef<HTMLCanvasElement>>('canvas');
  private readonly viewReady = signal(false);

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
      this.chart = new Chart(this.canvasRef().nativeElement, {
        type,
        data,
        options,
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
