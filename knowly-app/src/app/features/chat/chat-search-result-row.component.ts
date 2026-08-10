import { DatePipe } from '@angular/common';
import { Component, input, output } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { ChatMessageSearchResultDto } from '../../core/chat.model';

/**
 * `chat-message-search` PLAN.md — one search result row: sender, conversation title,
 * timestamp, content snippet (REQ-9). Small, presentational — mirrors this feature area's
 * existing "list row is its own component" precedent, keeping `chat-search-dialog.component.ts`
 * itself focused on query/filter/pagination orchestration.
 */
@Component({
  selector: 'app-chat-search-result-row',
  imports: [TranslocoPipe, DatePipe],
  template: `
    <li
      data-testid="chat-search-result-row"
      role="button"
      tabindex="0"
      [attr.aria-label]="
        'chat.search.resultA11yLabel'
          | transloco
            : {
                sender: result().senderNickname,
                conversation: result().conversationTitle,
                timestamp: result().createdAt,
              }
      "
      (click)="onSelect()"
      (keydown)="onKeydown($event)"
      class="flex cursor-pointer flex-col gap-1 rounded-lg border border-ink-200/70 px-3 py-2 text-sm hover:bg-ink-50 dark:border-ink-800/70 dark:hover:bg-ink-800"
    >
      <div class="flex items-center justify-between gap-2">
        <span class="font-medium text-ink-900 dark:text-white">{{ result().senderNickname }}</span>
        <span
          data-testid="chat-search-result-timestamp"
          class="shrink-0 text-xs text-ink-500 dark:text-ink-400"
        >
          {{ result().createdAt | date: 'short' }}
        </span>
      </div>
      <span class="text-xs text-ink-500 dark:text-ink-400">{{ result().conversationTitle }}</span>
      <p class="text-ink-700 dark:text-ink-300">{{ result().content }}</p>
    </li>
  `,
})
export class ChatSearchResultRowComponent {
  readonly result = input.required<ChatMessageSearchResultDto>();
  readonly select = output<number>();

  protected onSelect(): void {
    this.select.emit(this.result().conversationId);
  }

  protected onKeydown(event: KeyboardEvent): void {
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault();
      this.onSelect();
    }
  }
}
