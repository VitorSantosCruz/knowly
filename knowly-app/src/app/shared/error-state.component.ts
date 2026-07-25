import { Component, input } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';

@Component({
  selector: 'app-error-state',
  imports: [TranslocoPipe],
  template: `
    <p
      data-testid="error-state"
      role="alert"
      class="enter-fluid rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-700 dark:border-red-900/50 dark:bg-red-950/40 dark:text-red-300"
    >
      {{ 'dashboard.error' | transloco: { traceId: traceId() } }}
    </p>
  `,
})
export class ErrorStateComponent {
  readonly traceId = input<string | undefined>(undefined);
}
