import { Component, OnInit, computed, inject, input } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { SupportService } from '../../core/support.service';
import { NoAccessStateComponent } from '../../shared/no-access-state.component';
import { MessageThreadComponent } from '../../shared/chat/message-thread.component';

/**
 * REQ-17/18: rendered only when the viewer holds `SUPPORT_CHANNEL_VIEW` — browsing another
 * member's Support Channel history, read-only (`showComposer=false`).
 */
@Component({
  selector: 'app-member-support-browse',
  imports: [TranslocoPipe, NoAccessStateComponent, MessageThreadComponent],
  template: `
    <div data-testid="member-support-browse" class="flex flex-col gap-3">
      <h1 class="font-semibold text-ink-900 dark:text-white">
        {{ 'support.browse.title' | transloco }}
      </h1>

      @if (accessDenied()) {
        <app-no-access-state />
      } @else {
        <app-message-thread
          [messages]="entry().messages"
          [hasMore]="entry().hasMore"
          [loading]="entry().loading"
          [loadError]="entry().loadError"
          [showComposer]="false"
          (loadMore)="supportService.loadOlderMessages(tenantId(), memberUserId())"
        />
      }
    </div>
  `,
})
export class MemberSupportBrowseComponent implements OnInit {
  readonly tenantId = input.required<number>();
  readonly memberUserId = input.required<number>();

  protected readonly supportService = inject(SupportService);
  protected readonly accessDenied = computed(() =>
    this.supportService.channelAccessDenied(this.tenantId(), this.memberUserId()),
  );

  protected readonly entry = computed(() =>
    this.supportService.entryOf(this.tenantId(), this.memberUserId()),
  );

  ngOnInit(): void {
    this.supportService.openChannel(this.tenantId(), this.memberUserId());
  }
}
