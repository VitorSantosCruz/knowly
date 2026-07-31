import { Component, OnInit, computed, inject, input } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslocoPipe } from '@jsverse/transloco';
import { SupportService } from '../../core/support.service';
import { MessageThreadComponent } from '../../shared/chat/message-thread.component';
import { TicketStatusBadgeComponent } from './ticket-status-badge.component';

/**
 * REQ-13..16: the claimed-ticket view for the assigned staff user (or read-only for any other
 * staff/tenant viewer). Loads the member's *full* Support Channel history (every prior ticket,
 * open or closed) via `SupportService.openChannel`, not just the newly-claimed ticket's own
 * messages, since the channel is a single persistent `ChatConversation` spanning every ticket.
 */
@Component({
  selector: 'app-staff-support-channel',
  imports: [FormsModule, TranslocoPipe, MessageThreadComponent, TicketStatusBadgeComponent],
  template: `
    <div data-testid="staff-support-channel" class="flex flex-col gap-3">
      @if (supportService.activeTicket(); as ticket) {
        <div class="flex items-center gap-3">
          <app-ticket-status-badge [status]="ticket.status" />

          @if (ticket.status !== 'CLOSED') {
            <input
              type="number"
              data-testid="transfer-target-input"
              [(ngModel)]="transferTargetId"
              name="transferTarget"
              [attr.aria-label]="'support.channel.transferLabel' | transloco"
              class="w-24 rounded-lg border border-ink-200/70 px-2 py-1 text-sm dark:border-ink-800/70"
            />
            <button
              type="button"
              data-testid="transfer-button"
              [attr.aria-label]="'support.channel.transferLabel' | transloco"
              (click)="transfer()"
              class="rounded-lg border border-ink-200/70 px-3 py-1.5 text-sm dark:border-ink-800/70"
            >
              {{ 'support.channel.transfer' | transloco }}
            </button>
            <button
              type="button"
              data-testid="close-button"
              [attr.aria-label]="'support.channel.closeLabel' | transloco"
              (click)="close()"
              class="rounded-lg border border-red-300 px-3 py-1.5 text-sm text-red-700 dark:border-red-900/50 dark:text-red-400"
            >
              {{ 'support.channel.close' | transloco }}
            </button>
          }
        </div>

        @if (!isAssignedToMe() && ticket.status !== 'CLOSED') {
          <p class="text-sm text-ink-500 dark:text-ink-400">
            {{ 'support.channel.readOnly' | transloco }}
          </p>
        }
      }

      <app-message-thread
        [messages]="entry().messages"
        [hasMore]="entry().hasMore"
        [loading]="entry().loading"
        [loadError]="entry().loadError"
        [showComposer]="showComposer()"
        (loadMore)="supportService.loadOlderMessages(tenantId(), memberUserId())"
        (send)="onSend($event)"
        (retry)="onRetry($event)"
      />
    </div>
  `,
})
export class StaffSupportChannelComponent implements OnInit {
  readonly tenantId = input.required<number>();
  readonly memberUserId = input.required<number>();
  readonly currentUserId = input.required<number>();

  protected readonly supportService = inject(SupportService);
  protected transferTargetId: number | null = null;

  protected readonly entry = computed(() =>
    this.supportService.entryOf(this.tenantId(), this.memberUserId()),
  );

  protected readonly isAssignedToMe = computed(
    () => this.supportService.activeTicket()?.assignedStaffUserId === this.currentUserId(),
  );

  protected readonly showComposer = computed(
    () => this.isAssignedToMe() && this.supportService.activeTicket()?.status !== 'CLOSED',
  );

  ngOnInit(): void {
    this.supportService.openChannel(this.tenantId(), this.memberUserId());
  }

  transfer(): void {
    const ticket = this.supportService.activeTicket();
    if (!ticket || this.transferTargetId === null) {
      return;
    }
    this.supportService.transfer(this.tenantId(), ticket.id, this.transferTargetId).subscribe();
  }

  close(): void {
    const ticket = this.supportService.activeTicket();
    if (!ticket) {
      return;
    }
    this.supportService.close(this.tenantId(), ticket.id).subscribe();
  }

  onSend(content: string): void {
    this.supportService
      .sendMessage(this.tenantId(), this.memberUserId(), content, crypto.randomUUID())
      .subscribe();
  }

  onRetry(message: { localId?: string; content: string }): void {
    if (!message.localId) {
      return;
    }
    this.supportService
      .sendMessage(this.tenantId(), this.memberUserId(), message.content, message.localId)
      .subscribe();
  }
}
