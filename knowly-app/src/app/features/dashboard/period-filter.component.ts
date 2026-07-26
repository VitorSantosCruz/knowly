import { Component, model } from '@angular/core';
import { buttonClass } from '../../shared/button-classes';

export type Period = '7d' | '30d' | '90d' | 'all';

interface PeriodOption {
  label: string;
  value: Period;
}

@Component({
  selector: 'app-period-filter',
  template: `
    <div data-testid="period-filter" role="group" class="inline-flex gap-1">
      @for (option of options; track option.value) {
        <button
          type="button"
          [attr.data-testid]="'period-option-' + option.value"
          [attr.aria-pressed]="period() === option.value"
          [class]="buttonClassFor(option.value)"
          (click)="period.set(option.value)"
        >
          {{ option.label }}
        </button>
      }
    </div>
  `,
})
export class PeriodFilterComponent {
  readonly period = model<Period>('30d');

  protected readonly options: PeriodOption[] = [
    { label: '7d', value: '7d' },
    { label: '30d', value: '30d' },
    { label: '90d', value: '90d' },
    { label: 'all', value: 'all' },
  ];

  protected buttonClassFor(value: Period): string {
    return buttonClass('secondary', { ghost: this.period() !== value });
  }
}
