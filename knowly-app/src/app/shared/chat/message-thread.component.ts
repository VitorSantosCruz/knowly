import { Component, DestroyRef, effect, inject, input, output, signal } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { DisplayMessage, splitOnMatch } from '../../core/chat.model';
import { MessageComposerComponent } from './message-composer.component';

/** REQ-35 (chat-message-search PLAN.md, Amended 2026-08-10): how long the flash's finite
 * background-color pulse runs before the persistent highlight (REQ-36) is all that remains. */
const FLASH_DURATION_MS = 1800;

/**
 * Shared message list + progressive/paginated loading UI + optional composer (REQ-19/20/21),
 * used by both the peer conversation view and the support channel view — see PLAN.md.
 * Pure presentational component with no service of its own, parametrized via inputs/outputs.
 *
 * **Amended (2026-08-10)**: gains `highlightMessageId`/`highlightQuery` (REQ-33 through REQ-36 of
 * `chat-message-search`'s SPEC amendment) — when a message search result is clicked, the parent
 * conversation view resolves the target message into these inputs once it's loaded, and this
 * component scrolls it into view, flashes it briefly (`chat-flash`, skipped under
 * `prefers-reduced-motion`), and leaves the matched substring `<mark>`-wrapped persistently.
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
              [id]="'message-thread-item-' + message.id"
              data-testid="message-thread-item"
              [attr.aria-label]="
                ('chat.thread.messageFrom' | transloco: { sender: message.senderNickname }) || ''
              "
              [class]="
                (message.fromViewer
                  ? 'max-w-[80%] self-end rounded-2xl rounded-br-sm bg-signal-600 px-3 py-2 text-sm text-white shadow-sm dark:bg-signal-700'
                  : 'max-w-[80%] self-start rounded-2xl rounded-bl-sm bg-ink-100 px-3 py-2 text-sm text-ink-900 shadow-sm dark:bg-ink-800/80 dark:text-ink-100') +
                (flashTargetId() === message.id ? ' chat-flash' : '')
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
              } @else if (highlightSplit(message); as split) {
                <p class="whitespace-pre-wrap">
                  {{ split.before
                  }}<mark
                    class="rounded bg-signal-200 px-0.5 text-ink-900 dark:bg-signal-400 dark:text-ink-950"
                    >{{ split.match }}</mark
                  >{{ split.after }}
                </p>
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
  //
  // REQ-35 (Amended 2026-08-10): `.chat-flash`'s pulse — 2 iterations (~1.8s total) of a
  // background-color tween using the same signal accent the outgoing bubbles already use, so the
  // flash reads as "this app's own accent momentarily brightening", not an unrelated color.
  // `prefers-reduced-motion: reduce` disables the animation outright (the element still gets the
  // class and the scroll/persistent-highlight still happen — only the motion is suppressed).
  styles: [
    `
      :host {
        display: block;
        flex: 1 1 0%;
        min-height: 0;
      }

      .chat-flash {
        animation: chat-flash-pulse 0.9s ease-in-out 2;
      }

      @keyframes chat-flash-pulse {
        0%,
        100% {
          background-color: inherit;
        }
        50% {
          background-color: var(--color-signal-400, #b666fa);
        }
      }

      @media (prefers-reduced-motion: reduce) {
        .chat-flash {
          animation: none;
        }
      }
    `,
  ],
})
export class MessageThreadComponent {
  private readonly destroyRef = inject(DestroyRef);

  readonly messages = input<DisplayMessage[]>([]);
  readonly hasMore = input(false);
  readonly loading = input(false);
  readonly loadError = input(false);
  readonly showComposer = input(false);
  readonly composerDisabled = input(false);
  /** REQ-33/34: the message to scroll to/flash/highlight once it's among `messages()` —
   * `undefined` under normal use (no pending search-result jump). */
  readonly highlightMessageId = input<number | undefined>(undefined);
  /** REQ-32/36: the query whose literal, case-insensitive first occurrence gets `<mark>`-wrapped
   * inside `highlightMessageId`'s bubble — persists after the flash ends (REQ-36), unlike
   * `flashTargetId` below, which only exists for the animation's duration. */
  readonly highlightQuery = input<string | undefined>(undefined);

  readonly loadMore = output<void>();
  readonly send = output<string>();
  readonly retry = output<DisplayMessage>();

  /** REQ-35: only set for the finite duration of the flash animation — `highlightMessageId`
   * itself (not this) drives the persistent `<mark>`, so the mark outlives the flash. */
  protected readonly flashTargetId = signal<number | undefined>(undefined);
  private lastScrolledId: number | undefined;
  private flashTimeoutId: ReturnType<typeof setTimeout> | undefined;

  constructor() {
    effect(() => {
      const targetId = this.highlightMessageId();
      const found = this.messages().some((message) => message.id === targetId);
      if (targetId === undefined || !found || targetId === this.lastScrolledId) {
        return;
      }
      this.lastScrolledId = targetId;

      /** Bug fix (found live: 1:1/`PEER_DIRECT` conversations mark the target message but never
       * scroll to it, while `PEER_GROUP` conversations do both) — `found` only proves `targetId`
       * is present in the `messages()` array the effect reads; it says nothing about whether
       * Angular has actually painted the corresponding `<li id="message-thread-item-…">` yet.
       * The `<mark>` above renders straight off a template binding (`highlightSplit()`), so it
       * always shows the instant `messages()`/`highlightMessageId()` update — but this
       * `document.getElementById` lookup is imperative DOM access, and `element?.scrollIntoView`
       * silently no-ops via optional chaining if the node isn't in the DOM tree at this exact
       * moment. Nothing about `PEER_DIRECT` vs. `PEER_GROUP` in this component or
       * `ConversationDetailComponent` branches on conversation kind — the asymmetry is a genuine
       * race, just one that happened to resolve safely for `PEER_GROUP` and not for
       * `PEER_DIRECT` in the reported session (e.g. one extra reactive hop, such as
       * `PersonInfoModalComponent`'s `otherParticipantId` input resolving, can shift which CD
       * flush this effect ends up scheduled in relative to the `@for` list's own re-render). Made
       * unconditionally robust with one microtask retry rather than depending on exact effect/CD
       * ordering. */
      const scrollToTarget = (): boolean => {
        const element = document.getElementById(`message-thread-item-${targetId}`);
        if (!element) {
          return false;
        }
        element.scrollIntoView({ block: 'center', behavior: 'auto' });
        return true;
      };
      if (!scrollToTarget()) {
        queueMicrotask(() => scrollToTarget());
      }

      const prefersReducedMotion = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
      if (prefersReducedMotion) {
        return;
      }

      this.flashTargetId.set(targetId);
      if (this.flashTimeoutId !== undefined) {
        clearTimeout(this.flashTimeoutId);
      }
      this.flashTimeoutId = setTimeout(() => this.flashTargetId.set(undefined), FLASH_DURATION_MS);
    });

    this.destroyRef.onDestroy(() => {
      if (this.flashTimeoutId !== undefined) {
        clearTimeout(this.flashTimeoutId);
      }
    });
  }

  /** REQ-32/36: reused split-highlight helper — `null` (renders plain text) unless this is the
   * jump target *and* the current query literally substring-matches this message's content. */
  protected highlightSplit(message: DisplayMessage) {
    if (message.id !== this.highlightMessageId()) {
      return null;
    }
    const query = this.highlightQuery();
    if (!query) {
      return null;
    }
    return splitOnMatch(message.content, query);
  }
}
