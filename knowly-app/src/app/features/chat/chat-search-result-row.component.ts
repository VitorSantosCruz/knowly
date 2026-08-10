import { DatePipe } from '@angular/common';
import { Component, computed, input, output } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import {
  ChatGroupSearchResultDto,
  ChatMessageSearchResultDto,
  ChatPersonSearchResultDto,
  ChatRagConversationSearchResultDto,
  ChatSupportSearchResultDto,
} from '../../core/chat.model';

export type ChatSearchResultKind = 'person' | 'group' | 'support' | 'rag' | 'message';

export type ChatSearchRowResult =
  | ({ kind: 'message' } & ChatMessageSearchResultDto)
  | ({ kind: 'person' } & ChatPersonSearchResultDto)
  | ({ kind: 'group' } & ChatGroupSearchResultDto)
  | ({ kind: 'support' } & ChatSupportSearchResultDto)
  | ({ kind: 'rag' } & ChatRagConversationSearchResultDto);

/**
 * `chat-message-search` PLAN.md — one search result row. **Amended (2026-08-10)**: gains a
 * `kind` discriminator (`'person' | 'group' | 'support' | 'rag' | 'message'`, was message-only)
 * so `chat-unified-search.component.ts` can render all five result groups through one shared
 * presentational component — mirrors this feature area's existing "list row is its own
 * component" precedent.
 */
@Component({
  selector: 'app-chat-search-result-row',
  imports: [TranslocoPipe, DatePipe],
  template: `
    <li
      data-testid="chat-search-result-row"
      role="button"
      tabindex="0"
      [attr.aria-label]="a11yLabelKey() | transloco: a11yLabelParams()"
      (click)="onSelect()"
      (keydown)="onKeydown($event)"
      class="flex cursor-pointer flex-col gap-1 rounded-lg border border-ink-200/70 px-3 py-2 text-sm hover:bg-ink-50 dark:border-ink-800/70 dark:hover:bg-ink-800"
    >
      @switch (result().kind) {
        @case ('message') {
          <div class="flex items-center justify-between gap-2">
            <span class="font-medium text-ink-900 dark:text-white">{{ messageResult().senderNickname }}</span>
            <span
              data-testid="chat-search-result-timestamp"
              class="shrink-0 text-xs text-ink-500 dark:text-ink-400"
            >
              {{ messageResult().createdAt | date: 'short' }}
            </span>
          </div>
          <span class="text-xs text-ink-500 dark:text-ink-400">{{
            messageResult().conversationTitle
          }}</span>
          <p class="text-ink-700 dark:text-ink-300">{{ messageResult().content }}</p>
        }
        @case ('person') {
          <span class="font-medium text-ink-900 dark:text-white">{{ personResult().nickname }}</span>
        }
        @case ('group') {
          <span class="font-medium text-ink-900 dark:text-white">{{ groupResult().title }}</span>
        }
        @case ('support') {
          <span class="font-medium text-ink-900 dark:text-white">{{
            'chat.search.groupLabelSupport' | transloco
          }}</span>
        }
        @case ('rag') {
          <span class="font-medium text-ink-900 dark:text-white">{{ ragResult().title }}</span>
        }
      }
    </li>
  `,
})
export class ChatSearchResultRowComponent {
  readonly result = input.required<ChatSearchRowResult>();
  readonly rowSelected = output<number>();

  protected messageResult = computed(
    () => this.result() as Extract<ChatSearchRowResult, { kind: 'message' }>,
  );
  protected personResult = computed(
    () => this.result() as Extract<ChatSearchRowResult, { kind: 'person' }>,
  );
  protected groupResult = computed(
    () => this.result() as Extract<ChatSearchRowResult, { kind: 'group' }>,
  );
  protected supportResult = computed(
    () => this.result() as Extract<ChatSearchRowResult, { kind: 'support' }>,
  );
  protected ragResult = computed(
    () => this.result() as Extract<ChatSearchRowResult, { kind: 'rag' }>,
  );

  protected a11yLabelKey = computed(() => {
    switch (this.result().kind) {
      case 'message':
        return 'chat.search.resultA11yLabelMessage';
      case 'person':
        return 'chat.search.resultA11yLabelPerson';
      case 'group':
        return 'chat.search.resultA11yLabelGroup';
      case 'support':
        return 'chat.search.resultA11yLabelSupport';
      case 'rag':
        return 'chat.search.resultA11yLabelRag';
    }
  });

  protected a11yLabelParams = computed(() => {
    const r = this.result();
    switch (r.kind) {
      case 'message':
        return { sender: r.senderNickname, conversation: r.conversationTitle, timestamp: r.createdAt };
      case 'person':
        return { nickname: r.nickname };
      case 'group':
        return { title: r.title };
      case 'support':
        return {};
      case 'rag':
        return { title: r.title };
    }
  });

  protected onSelect(): void {
    const r = this.result();
    switch (r.kind) {
      case 'message':
        this.rowSelected.emit(r.conversationId);
        return;
      case 'person':
        this.rowSelected.emit(r.userId);
        return;
      case 'group':
        this.rowSelected.emit(r.id);
        return;
      case 'support':
        this.rowSelected.emit(r.channelId);
        return;
      case 'rag':
        this.rowSelected.emit(r.id);
        return;
    }
  }

  protected onKeydown(event: KeyboardEvent): void {
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault();
      this.onSelect();
    }
  }
}
