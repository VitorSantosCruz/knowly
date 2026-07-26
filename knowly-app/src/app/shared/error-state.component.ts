import { Component, input } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { Message } from 'primeng/message';

@Component({
  selector: 'app-error-state',
  imports: [TranslocoPipe, Message],
  template: `
    <p-message data-testid="error-state" role="alert" severity="error" class="enter-fluid block">
      {{ 'dashboard.error' | transloco: { traceId: traceId() } }}
    </p-message>
  `,
})
export class ErrorStateComponent {
  readonly traceId = input<string | undefined>(undefined);
}
