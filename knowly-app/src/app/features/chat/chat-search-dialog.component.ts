import {
  Component,
  DestroyRef,
  ElementRef,
  computed,
  effect,
  inject,
  input,
  output,
  signal,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { Subject, debounceTime, distinctUntilChanged } from 'rxjs';
import { ChatMessageSearchService } from '../../core/chat-message-search.service';
import { ChatService } from '../../core/chat.service';
import { ChatSearchResultRowComponent } from './chat-search-result-row.component';

/** 400ms — matches `tenant-pagination-search`'s existing debounce precedent for text-input-
 * driven backend calls (PLAN.md's "Free-text query is debounced 400ms" decision). */
const QUERY_DEBOUNCE_MS = 400;

/**
 * `chat-message-search` PLAN.md — new overlay entry point, native `<dialog>`, opened from a new
 * icon button in `ChatSidebarComponent`, not a fourth persistent column and not a route. Owns
 * transient UI-only state (debounced-`Subject` plumbing, raw filter form values, REQ-7/REQ-8
 * client-side validation) — submitted-result state lives in `ChatMessageSearchService`.
 */
@Component({
  selector: 'app-chat-search-dialog',
  imports: [TranslocoPipe, ChatSearchResultRowComponent],
  template: `
    <dialog
      #dialog
      data-testid="chat-search-dialog"
      class="fixed inset-0 m-auto flex max-h-[80vh] w-full max-w-lg flex-col rounded-2xl border border-ink-200/70 p-6 shadow-lg shadow-ink-900/5 backdrop:bg-ink-950/60 dark:border-ink-800/70 dark:bg-ink-900 dark:shadow-none"
      (cancel)="onNativeDialogCancel($event)"
    >
      <div class="mb-4 flex items-center justify-between">
        <h2 class="font-semibold text-ink-900 dark:text-white">
          {{ 'chat.search.dialogTitle' | transloco }}
        </h2>
        <button
          type="button"
          data-testid="chat-search-close"
          [attr.aria-label]="'chat.createGroup.cancel' | transloco"
          (click)="close()"
        >
          ✕
        </button>
      </div>

      <label class="mb-3 flex flex-col gap-1 text-sm">
        {{ 'chat.search.dialogTitle' | transloco }}
        <input
          type="text"
          data-testid="chat-search-query-input"
          [attr.aria-label]="'chat.search.dialogTitle' | transloco"
          [value]="queryInput()"
          (input)="onQueryInput($any($event.target).value)"
          placeholder="{{ 'chat.search.queryPlaceholder' | transloco }}"
          class="rounded-lg border border-ink-200/70 px-2 py-1 dark:border-ink-800/70"
        />
      </label>

      @if (blankQueryError()) {
        <p
          data-testid="chat-search-blank-error"
          role="alert"
          class="mb-3 text-sm text-red-600 dark:text-red-400"
        >
          {{ 'chat.search.blankQueryError' | transloco }}
        </p>
      }

      <div class="mb-3 grid grid-cols-2 gap-2 text-sm">
        <label class="flex flex-col gap-1">
          {{ 'chat.search.filterSenderLabel' | transloco }}
          <select
            data-testid="chat-search-sender-select"
            [attr.aria-label]="'chat.search.filterSenderLabel' | transloco"
            (change)="onSenderChange($any($event.target).value)"
            class="rounded-lg border border-ink-200/70 px-2 py-1 dark:border-ink-800/70"
          >
            <option value="">—</option>
            @for (sender of senderOptions(); track sender.userId) {
              <option [value]="sender.userId">{{ sender.nickname }}</option>
            }
          </select>
        </label>

        <label class="flex flex-col gap-1">
          {{ 'chat.search.filterConversationLabel' | transloco }}
          <select
            data-testid="chat-search-conversation-select"
            [attr.aria-label]="'chat.search.filterConversationLabel' | transloco"
            (change)="onConversationChange($any($event.target).value)"
            class="rounded-lg border border-ink-200/70 px-2 py-1 dark:border-ink-800/70"
          >
            <option value="">—</option>
            @for (conversation of conversationOptions(); track conversation.id) {
              <option [value]="conversation.id">
                {{ conversation.title ?? conversation.id }}
              </option>
            }
          </select>
        </label>

        <label class="flex flex-col gap-1">
          {{ 'chat.search.filterDateFromLabel' | transloco }}
          <input
            type="date"
            data-testid="chat-search-date-from-input"
            [attr.aria-label]="'chat.search.filterDateFromLabel' | transloco"
            [value]="dateFromInput()"
            (input)="onDateFromChange($any($event.target).value)"
            class="rounded-lg border border-ink-200/70 px-2 py-1 dark:border-ink-800/70"
          />
        </label>

        <label class="flex flex-col gap-1">
          {{ 'chat.search.filterDateToLabel' | transloco }}
          <input
            type="date"
            data-testid="chat-search-date-to-input"
            [attr.aria-label]="'chat.search.filterDateToLabel' | transloco"
            [value]="dateToInput()"
            (input)="onDateToChange($any($event.target).value)"
            class="rounded-lg border border-ink-200/70 px-2 py-1 dark:border-ink-800/70"
          />
        </label>
      </div>

      @if (invalidDateRangeError()) {
        <p
          data-testid="chat-search-date-range-error"
          role="alert"
          class="mb-3 text-sm text-red-600 dark:text-red-400"
        >
          {{ 'chat.search.invalidDateRangeError' | transloco }}
        </p>
      }

      <div class="min-h-0 flex-1 overflow-y-auto">
        @switch (searchService.status()) {
          @case ('idle') {
            <p data-testid="chat-search-status-idle" class="text-sm text-ink-500 dark:text-ink-400">
              {{ 'chat.search.queryPlaceholder' | transloco }}
            </p>
          }
          @case ('loading') {
            <p
              data-testid="chat-search-status-loading"
              role="status"
              class="text-sm text-ink-500 dark:text-ink-400"
            >
              {{ 'chat.search.loading' | transloco }}
            </p>
          }
          @case ('no-results') {
            <p
              data-testid="chat-search-status-no-results"
              class="text-sm text-ink-500 dark:text-ink-400"
            >
              {{ 'chat.search.noResults' | transloco: { query: searchService.lastQuery() } }}
            </p>
          }
          @case ('error') {
            <p
              data-testid="chat-search-status-error"
              role="alert"
              class="text-sm text-red-600 dark:text-red-400"
            >
              {{ 'chat.search.error' | transloco }}
            </p>
          }
        }

        @if (searchService.results().length > 0) {
          <ul class="flex flex-col gap-2">
            @for (result of searchService.results(); track result.id) {
              <app-chat-search-result-row
                [result]="result"
                (rowSelected)="onResultSelect($event)"
              />
            }
          </ul>

          @if (searchService.hasMore()) {
            <button
              type="button"
              data-testid="chat-search-load-more"
              [attr.aria-label]="'chat.search.loadMore' | transloco"
              (click)="searchService.loadMore()"
              class="mt-2 self-center rounded-full border border-ink-200/70 px-3 py-1 text-xs text-ink-600 hover:bg-ink-50 dark:border-ink-800/70 dark:text-ink-300 dark:hover:bg-ink-800"
            >
              {{ 'chat.search.loadMore' | transloco }}
            </button>
            <div data-testid="chat-search-sentinel" #sentinel></div>
          }
        }
      </div>
    </dialog>
  `,
})
export class ChatSearchDialogComponent {
  protected readonly searchService = inject(ChatMessageSearchService);
  private readonly chatService = inject(ChatService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  readonly open = input<boolean>(false);
  readonly dismissed = output<void>();

  protected readonly queryInput = signal('');
  protected readonly senderId = signal<number | undefined>(undefined);
  protected readonly conversationId = signal<number | undefined>(undefined);
  protected readonly dateFromInput = signal('');
  protected readonly dateToInput = signal('');
  protected readonly blankQueryError = signal(false);
  protected readonly invalidDateRangeError = signal(false);

  protected readonly senderOptions = computed(() => {
    const byUserId = new Map<number, string>();
    for (const candidate of this.chatService.eligibleParticipants()) {
      byUserId.set(candidate.userId, candidate.nickname);
    }
    for (const detail of this.chatService.details().values()) {
      for (const [userId, nickname] of Object.entries(detail.participantNicknames)) {
        byUserId.set(Number(userId), nickname);
      }
    }
    return Array.from(byUserId.entries()).map(([userId, nickname]) => ({ userId, nickname }));
  });

  protected readonly conversationOptions = computed(() =>
    this.chatService
      .conversations()
      .filter((c) => c.kind === 'PEER_DIRECT' || c.kind === 'PEER_GROUP'),
  );

  private readonly dialogRef = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');
  private readonly sentinelRef = viewChild<ElementRef<HTMLElement>>('sentinel');
  private wasOpen = false;
  private intersectionObserver?: IntersectionObserver;

  private readonly querySubject = new Subject<string>();

  constructor() {
    this.querySubject
      .pipe(debounceTime(QUERY_DEBOUNCE_MS), distinctUntilChanged(), takeUntilDestroyed())
      .subscribe((query) => this.runSearch(query));

    effect(() => {
      const dialog = this.dialogRef().nativeElement;
      const isOpen = this.open();

      if (isOpen && !dialog.open) {
        if (typeof dialog.showModal === 'function') {
          dialog.showModal();
        } else {
          dialog.setAttribute('open', '');
        }
      } else if (!isOpen && dialog.open) {
        if (typeof dialog.close === 'function') {
          dialog.close();
        } else {
          dialog.removeAttribute('open');
        }
      }

      if (isOpen && !this.wasOpen) {
        this.resetForm();
      }
      this.wasOpen = isOpen;
    });

    effect(() => {
      const sentinel = this.sentinelRef();
      this.intersectionObserver?.disconnect();
      if (!sentinel || typeof IntersectionObserver === 'undefined') {
        return;
      }
      this.intersectionObserver = new IntersectionObserver((entries) => {
        if (entries.some((entry) => entry.isIntersecting)) {
          this.searchService.loadMore();
        }
      });
      this.intersectionObserver.observe(sentinel.nativeElement);
    });

    this.destroyRef.onDestroy(() => this.intersectionObserver?.disconnect());
  }

  protected onQueryInput(value: string): void {
    this.queryInput.set(value);
    this.querySubject.next(value.trim());
  }

  protected onSenderChange(value: string): void {
    this.senderId.set(value === '' ? undefined : Number(value));
    this.runSearch(this.queryInput().trim());
  }

  protected onConversationChange(value: string): void {
    this.conversationId.set(value === '' ? undefined : Number(value));
    this.runSearch(this.queryInput().trim());
  }

  protected onDateFromChange(value: string): void {
    this.dateFromInput.set(value);
    this.runSearch(this.queryInput().trim());
  }

  protected onDateToChange(value: string): void {
    this.dateToInput.set(value);
    this.runSearch(this.queryInput().trim());
  }

  protected onResultSelect(conversationId: number): void {
    this.router.navigate(['/chat', conversationId]);
    this.close();
  }

  protected close(): void {
    this.searchService.reset();
    this.dismissed.emit();
  }

  protected onNativeDialogCancel(event: Event): void {
    event.preventDefault();
    this.close();
  }

  private runSearch(query: string): void {
    if (query.length === 0) {
      this.blankQueryError.set(true);
      return;
    }
    this.blankQueryError.set(false);

    const dateFrom = this.dateFromInput();
    const dateTo = this.dateToInput();
    if (dateFrom && dateTo && dateFrom > dateTo) {
      this.invalidDateRangeError.set(true);
      return;
    }
    this.invalidDateRangeError.set(false);

    this.searchService.search({
      q: query,
      ...(this.senderId() !== undefined ? { senderId: this.senderId() } : {}),
      ...(this.conversationId() !== undefined ? { conversationId: this.conversationId() } : {}),
      ...(dateFrom ? { dateFrom } : {}),
      ...(dateTo ? { dateTo } : {}),
    });
  }

  private resetForm(): void {
    this.queryInput.set('');
    this.senderId.set(undefined);
    this.conversationId.set(undefined);
    this.dateFromInput.set('');
    this.dateToInput.set('');
    this.blankQueryError.set(false);
    this.invalidDateRangeError.set(false);
  }
}
