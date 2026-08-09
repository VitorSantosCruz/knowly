import { Component, OnInit, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { ActiveTenantService } from '../../core/active-tenant.service';
import { SupportService } from '../../core/support.service';
import { TicketStatusBadgeComponent } from './ticket-status-badge.component';

/**
 * REQ-12/13: unclaimed-ticket inbox across every tenant the viewer has resolved access to.
 * Deviation from PLAN.md's provisional cross-tenant aggregation assumption: the backend has
 * no cross-tenant unclaimed-tickets endpoint, so this calls `fetchInbox` once per tenant id
 * resolved from `ActiveTenantService.listAllTenants` — see PLAN.md's own note that a true
 * single-call aggregate is a follow-up backend change, not decided here.
 *
 * `claim()`/`transfer()`/`close()` all key off `ticketId` alone on the backend — the
 * `{tenantId}` path segment is not used to scope those three lookups (see
 * `SupportTicketService`), so any already-resolved tenant id is safe to submit it with.
 */
@Component({
  selector: 'app-staff-support-inbox',
  imports: [TranslocoPipe, TicketStatusBadgeComponent],
  template: `
    <div data-testid="staff-support-inbox" class="flex flex-col gap-3">
      <h1 class="font-semibold text-ink-900 dark:text-white">
        {{ 'support.inbox.title' | transloco }}
      </h1>

      @if (supportService.inboxTickets().length === 0) {
        <p class="text-sm text-ink-500 dark:text-ink-400">
          {{ 'support.inbox.empty' | transloco }}
        </p>
      }

      <ul class="flex flex-col gap-2">
        @for (ticket of supportService.inboxTickets(); track ticket.id) {
          <li
            data-testid="inbox-ticket"
            class="flex items-center justify-between gap-3 rounded-lg border border-ink-200/70 px-3 py-2 dark:border-ink-800/70"
          >
            <app-ticket-status-badge [status]="ticket.status" />
            <button
              type="button"
              data-testid="claim-button"
              [attr.aria-label]="'support.inbox.claim' | transloco"
              (click)="claim(ticket.id)"
              class="rounded-lg bg-signal-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-signal-700"
            >
              {{ 'support.inbox.claim' | transloco }}
            </button>
          </li>
        }
      </ul>
    </div>
  `,
})
export class StaffSupportInboxComponent implements OnInit {
  protected readonly supportService = inject(SupportService);
  private readonly activeTenantService = inject(ActiveTenantService);
  private readonly router = inject(Router);

  private readonly anyTenantId = signal<number | null>(null);

  ngOnInit(): void {
    this.activeTenantService.listAllTenants(0, 100).subscribe((page) => {
      for (const tenant of page.content) {
        this.anyTenantId.set(tenant.id);
        this.supportService.fetchInbox(tenant.id);
      }
    });
  }

  claim(ticketId: number): void {
    const tenantId = this.anyTenantId();
    if (tenantId === null) {
      return;
    }
    this.supportService
      .claim(tenantId, ticketId)
      .subscribe(() => this.router.navigate(['/chat'], { queryParams: { section: 'support' } }));
  }
}
