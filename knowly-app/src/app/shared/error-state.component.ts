import { Component, input } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';

@Component({
  selector: 'app-error-state',
  imports: [TranslocoPipe],
  template: `
    <div
      data-testid="error-state"
      role="alert"
      class="enter-fluid block rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700 dark:border-red-900/50 dark:bg-red-950/30 dark:text-red-400"
    >
      {{ 'dashboard.error' | transloco: { traceId: traceId() } }}
    </div>
  `,
})
export class ErrorStateComponent {
  readonly traceId = input<string | undefined>(undefined);
}
