import { DatePipe } from '@angular/common';
import { Component, computed, inject, input, output } from '@angular/core';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { LucideBot, LucideUser } from '@lucide/angular';
import {
  ChatGroupSearchResultDto,
  ChatMessageSearchResultDto,
  ChatPersonSearchResultDto,
  ChatRagConversationSearchResultDto,
  ChatSupportSearchResultDto,
  splitOnMatch,
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
  imports: [TranslocoPipe, DatePipe, LucideUser, LucideBot],
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
            <span class="font-medium text-ink-900 dark:text-white">{{
              messageResult().senderNickname
            }}</span>
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
          <p class="text-ink-700 dark:text-ink-300">
            @if (contentMatch(); as match) {
              {{ match.before
              }}<mark
                class="rounded bg-signal-200 px-0.5 text-ink-900 dark:bg-signal-700 dark:text-white"
                >{{ match.match }}</mark
              >{{ match.after }}
            } @else {
              {{ messageResult().content }}
            }
          </p>
        }
        @case ('person') {
          <span class="font-medium text-ink-900 dark:text-white">{{
            personResult().nickname
          }}</span>
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
          @if (ragResult().matchedSnippet) {
            <p data-testid="chat-search-result-rag-snippet" class="text-ink-700 dark:text-ink-300">
              @if (ragSnippetMatch(); as match) {
                {{ match.before
                }}<mark
                  class="rounded bg-signal-200 px-0.5 text-ink-900 dark:bg-signal-700 dark:text-white"
                  >{{ match.match }}</mark
                >{{ match.after }}
              } @else {
                {{ ragResult().matchedSnippet }}
              }
            </p>
            @if (ragRoleLabel(); as roleLabel) {
              <span
                data-testid="chat-search-result-rag-role"
                class="inline-flex items-center gap-1 text-xs text-ink-500 dark:text-ink-400"
              >
                @if (ragResult().matchedRole === 'USER') {
                  <svg lucideUser class="h-3.5 w-3.5"></svg>
                } @else {
                  <svg lucideBot class="h-3.5 w-3.5"></svg>
                }
                {{ roleLabel }}
              </span>
            }
          }
        }
      }
    </li>
  `,
})
export class ChatSearchResultRowComponent {
  private readonly transloco = inject(TranslocoService);

  readonly result = input.required<ChatSearchRowResult>();
  /** REQ-32 (Amended 2026-08-10): current search query, used to highlight the matched substring
   * within a message-kind result's content. Optional/blank for non-search-result-list call sites
   * (none exist today, but keeps this row reusable without forcing every caller to pass one). */
  readonly query = input<string>('');
  readonly rowSelected = output<number>();

  /** REQ-32: only meaningful for `kind: 'message'` rows — `null` when there's no literal
   * substring match (blank query, or the backend's match didn't literally substring-match this
   * row's own `content`), in which case the template falls back to plain, unmarked text. */
  protected readonly contentMatch = computed(() => {
    const r = this.result();
    if (r.kind !== 'message') {
      return null;
    }
    return splitOnMatch(r.content, this.query());
  });

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

  /** REQ-38/39 (Amended 2026-08-11): only meaningful for `kind: 'rag'` rows with a non-empty
   * `matchedSnippet` — reuses `splitOnMatch` unchanged, same function/semantics as `contentMatch`
   * above, just against the snippet instead of a message's `content`. */
  protected readonly ragSnippetMatch = computed(() => {
    const r = this.result();
    if (r.kind !== 'rag' || !r.matchedSnippet) {
      return null;
    }
    return splitOnMatch(r.matchedSnippet, this.query());
  });

  /** REQ-40/41 (Amended 2026-08-11): the translated role-indicator label — `null` when
   * `matchedRole` is absent/null, so the template's nested `@if` independently omits the role
   * indicator while still rendering the snippet (REQ-41's independent-degradation case). */
  protected readonly ragRoleLabel = computed(() => {
    const r = this.result();
    if (r.kind !== 'rag' || !r.matchedRole) {
      return null;
    }
    return r.matchedRole === 'USER'
      ? this.transloco.translate('chat.search.ragMatchedByUser')
      : this.transloco.translate('chat.search.ragMatchedByAssistant');
  });

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
        return {
          sender: r.senderNickname,
          conversation: r.conversationTitle,
          timestamp: r.createdAt,
        };
      case 'person':
        return { nickname: r.nickname };
      case 'group':
        return { title: r.title };
      case 'support':
        return {};
      case 'rag': {
        const roleLabel = this.ragRoleLabel();
        return { title: r.title, roleSuffix: roleLabel ? `, ${roleLabel}` : '' };
      }
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
