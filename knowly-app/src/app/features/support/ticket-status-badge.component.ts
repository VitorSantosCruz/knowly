import { Component, computed, input } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { SupportTicketStatus } from '../../core/chat.model';

const CLASSES: Record<SupportTicketStatus, string> = {
  OPEN: 'bg-signal-100 text-signal-800 dark:bg-signal-900/30 dark:text-signal-400',
  ASSIGNED: 'bg-amber-100 text-amber-800 dark:bg-amber-900/30 dark:text-amber-400',
  CLOSED: 'bg-ink-100 text-ink-600 dark:bg-ink-800 dark:text-ink-400',
};

const KEYS: Record<SupportTicketStatus, string> = {
  OPEN: 'support.status.open',
  ASSIGNED: 'support.status.assigned',
  CLOSED: 'support.status.closed',
};

/** Renders the three real `SupportTicketStatus` states (OPEN/ASSIGNED/CLOSED) — not just
 * open/closed, per the backend's `SupportTicketStatus` enum. */
@Component({
  selector: 'app-ticket-status-badge',
  imports: [TranslocoPipe],
  template: `
    <span
      data-testid="ticket-status-badge"
      [attr.data-status]="status()"
      class="rounded-full px-2 py-1 text-xs font-medium"
      [class]="classes()"
    >
      {{ labelKey() | transloco }}
    </span>
  `,
})
export class TicketStatusBadgeComponent {
  readonly status = input.required<SupportTicketStatus>();

  protected readonly classes = computed(() => CLASSES[this.status()]);
  protected readonly labelKey = computed(() => KEYS[this.status()]);
}
