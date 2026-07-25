import { Component } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';

@Component({
  selector: 'app-no-access-state',
  imports: [TranslocoPipe],
  template: `
    <p data-testid="no-access-state" class="enter-fluid text-sm text-ink-500 dark:text-ink-400">
      {{ 'dashboard.noAccess' | transloco }}
    </p>
  `,
})
export class NoAccessStateComponent {}
