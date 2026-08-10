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
    <div data-testid="message-thread" class="flex h-full min-h-0 flex-col gap-3">
      <div
        data-testid="message-thread-list"
        class="flex min-h-32 flex-1 flex-col gap-2 overflow-y-auto rounded-2xl bg-ink-50/60 p-4 dark:bg-ink-950/40"
      >
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
              [class]="
                message.fromViewer
                  ? 'max-w-[80%] self-end rounded-2xl rounded-br-sm bg-signal-600 px-3 py-2 text-sm text-white shadow-sm dark:bg-signal-700'
                  : 'max-w-[80%] self-start rounded-2xl rounded-bl-sm bg-ink-100 px-3 py-2 text-sm text-ink-900 shadow-sm dark:bg-ink-800/80 dark:text-ink-100'
              "
            >
              @if (!message.fromViewer) {
                <p class="mb-0.5 text-xs font-medium opacity-70">{{ message.senderNickname }}</p>
              }

              @if (message.sendState === 'streaming' && message.content === '') {
                <span
                  data-testid="message-thread-typing-indicator"
                  class="inline-flex items-center gap-1"
                  aria-live="polite"
                >
                  <span
                    class="h-1.5 w-1.5 animate-bounce rounded-full bg-signal-500 [animation-delay:-0.2s]"
                  ></span>
                  <span class="h-1.5 w-1.5 animate-bounce rounded-full bg-signal-500"></span>
                  <span
                    class="h-1.5 w-1.5 animate-bounce rounded-full bg-signal-500 [animation-delay:0.2s]"
                  ></span>
                </span>
              } @else {
                <p class="whitespace-pre-wrap">{{ message.content }}</p>
              }

              @if (message.sendState === 'pending') {
                <p data-testid="message-pending" class="mt-1 text-xs opacity-70">
                  {{ 'chat.thread.pending' | transloco }}
                </p>
              }

              @if (message.sendState === 'failed') {
                <div class="mt-1 flex items-center gap-2">
                  <p data-testid="message-failed" class="text-xs text-red-300">
                    {{ 'chat.thread.failed' | transloco }}
                  </p>
                  <button
                    type="button"
                    data-testid="message-retry"
                    [attr.aria-label]="'chat.thread.retrySend' | transloco"
                    (click)="retry.emit(message)"
                    class="text-xs font-medium underline"
                  >
                    {{ 'chat.thread.retrySend' | transloco }}
                  </button>
                </div>
              }
            </li>
          }
        </ul>
      </div>

      @if (showComposer()) {
        <app-message-composer [disabled]="composerDisabled()" (send)="send.emit($event)" />
      }
    </div>
  `,
  // See ChatShellComponent's :host comment — this component is used across peer/group,
  // support-channel, and RAG conversation views, always as the flex child expected to fill the
  // remaining vertical space and let its own message list scroll internally.
  styles: [':host { display: block; flex: 1 1 0%; min-height: 0; }'],
})
export class MessageThreadComponent {
  readonly messages = input<DisplayMessage[]>([]);
  readonly hasMore = input(false);
  readonly loading = input(false);
  readonly loadError = input(false);
  readonly showComposer = input(false);
  readonly composerDisabled = input(false);

  readonly loadMore = output<void>();
  readonly send = output<string>();
  readonly retry = output<DisplayMessage>();
}
