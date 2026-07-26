import { Component, model } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { SelectButton } from 'primeng/selectbutton';

export type Period = '7d' | '30d' | '90d' | 'all';

interface PeriodOption {
  label: string;
  value: Period;
}

@Component({
  selector: 'app-period-filter',
  imports: [FormsModule, SelectButton],
  template: `
    <p-selectbutton
      data-testid="period-filter"
      [options]="options"
      optionLabel="label"
      optionValue="value"
      [allowEmpty]="false"
      [ngModel]="period()"
      (ngModelChange)="period.set($event)"
    >
      <ng-template #item let-option>
        <span [attr.data-testid]="'period-option-' + option.value">{{ option.label }}</span>
      </ng-template>
    </p-selectbutton>
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
}
