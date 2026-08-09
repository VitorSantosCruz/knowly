import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { SupportService } from '../../core/support.service';
import { MessageThreadComponent } from '../../shared/chat/message-thread.component';
import { AvatarComponent } from '../../shared/avatar.component';

/**
 * REQ-10/11: the member's own Support Channel. `SupportService.myChannelNotFound()` on a 404
 * is the reliable "never opened a ticket" signal (the channel is lazily created on first
 * `openTicket()` — see `support.service.ts`'s doc comment for the documented backend-contract
 * gap this works around: there is no GET returning "is my current ticket open" directly).
 */
@Component({
  selector: 'app-member-support-channel',
  imports: [TranslocoPipe, MessageThreadComponent, AvatarComponent],
  template: `
    <div data-testid="member-support-channel" class="flex h-full min-h-0 flex-col gap-3">
      <header class="flex shrink-0 items-center gap-2">
        <!-- No real staff avatar to show here (support is handled by whichever staff
             member claims the ticket, not a fixed person), so this is always the generic
             fallback, same pattern as chat-header.component.ts. -->
        <app-avatar [avatarUrl]="null" />
        <h1 class="font-semibold text-ink-900 dark:text-white">
          {{ 'support.member.title' | transloco }}
        </h1>
      </header>

      @if (supportService.myChannelNotFound()) {
        <button
          type="button"
          data-testid="start-ticket-button"
          [attr.aria-label]="'support.member.startTicket' | transloco"
          (click)="startTicket()"
          class="self-start rounded-lg bg-signal-600 px-4 py-2 text-sm font-medium text-white hover:bg-signal-700"
        >
          {{ 'support.member.startTicket' | transloco }}
        </button>
      } @else if (supportService.myChannel(); as channel) {
        <app-message-thread
          [messages]="displayMessages()"
          [hasMore]="entry().hasMore"
          [loading]="entry().loading"
          [loadError]="entry().loadError"
          [showComposer]="true"
          (loadMore)="supportService.loadOlderMessages(tenantId(), memberUserId())"
          (send)="onSend($event)"
          (retry)="onRetry($event)"
        />

        @if (canStartNewTicket()) {
          <button
            type="button"
            data-testid="start-ticket-button"
            [attr.aria-label]="'support.member.startTicket' | transloco"
            (click)="startTicket()"
            class="self-start rounded-lg bg-signal-600 px-4 py-2 text-sm font-medium text-white hover:bg-signal-700"
          >
            {{ 'support.member.startTicket' | transloco }}
          </button>
        }
      }

      @if (openTicketError()) {
        <p
          data-testid="open-ticket-error"
          role="alert"
          class="text-sm text-red-600 dark:text-red-400"
        >
          {{ 'support.member.openTicketError' | transloco }}
        </p>
      }
    </div>
  `,
  // See ChatShellComponent's :host comment.
  styles: [':host { display: block; flex: 1 1 0%; min-height: 0; }'],
})
export class MemberSupportChannelComponent implements OnInit {
  readonly tenantId = input.required<number>();
  readonly memberUserId = input.required<number>();

  protected readonly openTicketError = signal(false);

  protected readonly supportService = inject(SupportService);

  protected readonly entry = computed(() =>
    this.supportService.entryOf(this.tenantId(), this.memberUserId()),
  );

  protected readonly displayMessages = computed(() => {
    const memberUserId = this.memberUserId();
    return this.entry().messages.map((message) => ({
      ...message,
      fromViewer: message.senderUserId === memberUserId,
    }));
  });

  protected readonly canStartNewTicket = computed(
    () =>
      this.supportService.myOpenTicket()?.status !== 'ASSIGNED' &&
      this.supportService.myOpenTicket()?.status !== 'OPEN',
  );

  ngOnInit(): void {
    this.supportService.fetchMyChannel(this.tenantId(), this.memberUserId());
    this.supportService.openChannel(this.tenantId(), this.memberUserId());
  }

  startTicket(): void {
    this.openTicketError.set(false);
    this.supportService.openTicket(this.tenantId()).subscribe({
      next: () => {
        this.supportService.fetchMyChannel(this.tenantId(), this.memberUserId());
        this.supportService.openChannel(this.tenantId(), this.memberUserId());
      },
      error: () => this.openTicketError.set(true),
    });
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
