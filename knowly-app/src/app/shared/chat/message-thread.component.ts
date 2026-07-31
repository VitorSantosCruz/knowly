import { Component, input, output } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { DisplayMessage } from '../../core/chat.model';
import { MessageComposerComponent } from './message-composer.component';

/**
 * Shared message list + progressive/paginated loading UI + optional composer (REQ-19/20/21),
 * used by both the peer conversation view and the support channel view — see PLAN.md.
 * Pure presentational component with no service of its own, parametrized via inputs/outputs.
 */
@Component({
  selector: 'app-message-thread',
  imports: [TranslocoPipe, MessageComposerComponent],
  template: `
    <div data-testid="message-thread" class="flex flex-col gap-3">
      @if (hasMore()) {
        <button
          type="button"
          data-testid="message-thread-load-more"
          [attr.aria-label]="'chat.thread.loadMore' | transloco"
          (click)="loadMore.emit()"
          class="self-center rounded-full border border-ink-200/70 px-3 py-1 text-xs text-ink-600 hover:bg-ink-50 dark:border-ink-800/70 dark:text-ink-300 dark:hover:bg-ink-800"
        >
          {{ 'chat.thread.loadMore' | transloco }}
        </button>
      }

      @if (loading()) {
        <p
          data-testid="message-thread-loading"
          class="self-center text-xs text-ink-400"
          role="status"
        >
          {{ 'chat.thread.loading' | transloco }}
        </p>
      }

      @if (loadError()) {
        <div data-testid="message-thread-error" class="flex flex-col items-center gap-1">
          <p class="text-xs text-red-600 dark:text-red-400">
            {{ 'chat.thread.loadError' | transloco }}
          </p>
          <button
            type="button"
            data-testid="message-thread-retry"
            [attr.aria-label]="'chat.thread.retry' | transloco"
            (click)="loadMore.emit()"
            class="rounded-full border border-red-300 px-3 py-1 text-xs text-red-700 hover:bg-red-50 dark:border-red-900/50 dark:text-red-400"
          >
            {{ 'chat.thread.retry' | transloco }}
          </button>
        </div>
      }

      <ul class="flex flex-col gap-2" aria-label="{{ 'chat.thread.messages' | transloco }}">
        @for (message of messages(); track message.id + (message.localId ?? '')) {
          <li
            data-testid="message-thread-item"
            [attr.aria-label]="
              ('chat.thread.messageFrom' | transloco: { sender: message.senderNickname }) || ''
            "
            class="rounded-lg bg-ink-50 px-3 py-2 text-sm dark:bg-ink-800"
          >
            <p class="font-medium text-ink-900 dark:text-white">{{ message.senderNickname }}</p>
            <p class="text-ink-700 dark:text-ink-300">{{ message.content }}</p>

            @if (message.sendState === 'pending') {
              <p data-testid="message-pending" class="mt-1 text-xs text-ink-400">
                {{ 'chat.thread.pending' | transloco }}
              </p>
            }

            @if (message.sendState === 'failed') {
              <div class="mt-1 flex items-center gap-2">
                <p data-testid="message-failed" class="text-xs text-red-600 dark:text-red-400">
                  {{ 'chat.thread.failed' | transloco }}
                </p>
                <button
                  type="button"
                  data-testid="message-retry"
                  [attr.aria-label]="'chat.thread.retrySend' | transloco"
                  (click)="retry.emit(message)"
                  class="text-xs font-medium text-signal-600 hover:underline dark:text-signal-400"
                >
                  {{ 'chat.thread.retrySend' | transloco }}
                </button>
              </div>
            }
          </li>
        }
      </ul>

      @if (showComposer()) {
        <app-message-composer (send)="send.emit($event)" />
      }
    </div>
  `,
})
export class MessageThreadComponent {
  readonly messages = input<DisplayMessage[]>([]);
  readonly hasMore = input(false);
  readonly loading = input(false);
  readonly loadError = input(false);
  readonly showComposer = input(false);

  readonly loadMore = output<void>();
  readonly send = output<string>();
  readonly retry = output<DisplayMessage>();
}
