import { Component, DestroyRef, HostListener, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { Subject, debounceTime, distinctUntilChanged } from 'rxjs';
import { ChatEntitySearchService } from '../../core/chat-entity-search.service';
import { ChatRecentPlaceDto } from '../../core/chat.model';
import { ChatDirectoryRowsService, GroupRow } from '../../core/chat-directory-rows.service';
import { ChatMessageSearchService } from '../../core/chat-message-search.service';
import { ChatService } from '../../core/chat.service';
import {
  ChatSearchResultRowComponent,
  ChatSearchRowResult,
} from './chat-search-result-row.component';

/** Same 400ms constant the retired chat-search-dialog.component.ts used — reused unchanged in
 * mechanism, per PLAN.md's "Debounce" decision. */
const QUERY_DEBOUNCE_MS = 400;

type UnifiedStatus = 'idle' | 'loading' | 'results' | 'no-results' | 'error';

/**
 * `chat-message-search` PLAN.md, Amended (2026-08-10) — the unified, Slack-style search bar's
 * dropdown content: debounced type-ahead over both `ChatMessageSearchService`
 * (`GET /api/chat/messages/search`) and `ChatEntitySearchService` (`GET /api/chat/search`),
 * five result groups (People, Groups, Base de artigos, Support, Messages), per-group "see more",
 * and "recent places" on a blank query. Owned entirely by this feature — `chat-unified-ui`'s
 * `ChatShellComponent` only mounts `<app-chat-unified-search>` with no inputs/outputs.
 */
@Component({
  selector: 'app-chat-unified-search',
  imports: [TranslocoPipe, ChatSearchResultRowComponent],
  template: `
    <div data-testid="chat-unified-search" class="relative">
      <input
        type="search"
        data-testid="chat-unified-search-input"
        [attr.aria-label]="'chat.search.barPlaceholder' | transloco"
        [value]="queryInput()"
        (input)="onQueryInput($any($event.target).value)"
        (focus)="onFocus()"
        placeholder="{{ 'chat.search.barPlaceholder' | transloco }}"
        class="w-full rounded-full border border-ink-200/70 bg-ink-50/60 px-4 py-2 text-sm dark:border-ink-800/70 dark:bg-ink-950/40"
      />

      @if (open()) {
        <div
          data-testid="chat-unified-search-dropdown"
          class="absolute top-full left-0 z-20 mt-1 max-h-[70vh] w-full overflow-y-auto rounded-2xl border border-ink-200/70 bg-white p-3 shadow-lg shadow-ink-900/5 dark:border-ink-800/70 dark:bg-ink-900"
        >
          @if (isBlankQuery()) {
            <section role="group" [attr.aria-label]="'chat.search.recentPlacesLabel' | transloco">
              <h3 class="mb-2 text-xs font-semibold text-ink-500 dark:text-ink-400">
                {{ 'chat.search.recentPlacesLabel' | transloco }}
              </h3>
              <ul class="flex flex-col gap-1">
                @for (place of entitySearch.recentPlaces_(); track place.conversationId) {
                  <li>
                    <button
                      type="button"
                      data-testid="chat-unified-search-recent-place"
                      (click)="onRecentPlaceSelect(place)"
                      class="w-full rounded-lg px-2 py-1 text-left text-sm hover:bg-ink-50 dark:hover:bg-ink-800"
                    >
                      {{ place.title }}
                    </button>
                  </li>
                }
              </ul>
            </section>
          } @else {
            @switch (status()) {
              @case ('loading') {
                <p
                  data-testid="chat-unified-search-status-loading"
                  role="status"
                  class="text-sm text-ink-500 dark:text-ink-400"
                >
                  {{ 'chat.search.loading' | transloco }}
                </p>
              }
              @case ('error') {
                <p
                  data-testid="chat-unified-search-status-error"
                  role="alert"
                  class="text-sm text-red-600 dark:text-red-400"
                >
                  {{ 'chat.search.error' | transloco }}
                </p>
              }
              @case ('no-results') {
                <p
                  data-testid="chat-unified-search-status-no-results"
                  class="text-sm text-ink-500 dark:text-ink-400"
                >
                  {{ 'chat.search.noResults' | transloco: { query: queryInput() } }}
                </p>
              }
              @case ('results') {
                @if (peopleResults().length > 0) {
                  <section
                    role="group"
                    [attr.aria-label]="'chat.search.groupLabelPeople' | transloco"
                    class="mb-3"
                  >
                    <h3 class="mb-1 text-xs font-semibold text-ink-500 dark:text-ink-400">
                      {{ 'chat.search.groupLabelPeople' | transloco }}
                    </h3>
                    <ul class="flex flex-col gap-1">
                      @for (r of peopleResults(); track r.userId) {
                        <app-chat-search-result-row
                          [result]="r"
                          (rowSelected)="onEntitySelect(r)"
                        />
                      }
                    </ul>
                    @if (entitySearch.peopleHasMore()) {
                      <button
                        type="button"
                        data-testid="chat-unified-search-see-more-people"
                        (click)="entitySearch.expandSection('people', queryInput())"
                        class="mt-1 text-xs text-signal-700 dark:text-signal-300"
                      >
                        {{
                          'chat.search.seeMore'
                            | transloco: { group: 'chat.search.groupLabelPeople' | transloco }
                        }}
                      </button>
                    }
                    @if (entitySearch.peopleStatus() === 'error') {
                      <p role="alert" class="mt-1 text-xs text-red-600 dark:text-red-400">
                        {{ 'chat.search.error' | transloco }}
                      </p>
                    }
                  </section>
                }
                @if (groupsResults().length > 0) {
                  <section
                    role="group"
                    [attr.aria-label]="'chat.search.groupLabelGroups' | transloco"
                    class="mb-3"
                  >
                    <h3 class="mb-1 text-xs font-semibold text-ink-500 dark:text-ink-400">
                      {{ 'chat.search.groupLabelGroups' | transloco }}
                    </h3>
                    <ul class="flex flex-col gap-1">
                      @for (r of groupsResults(); track r.id) {
                        <app-chat-search-result-row
                          [result]="r"
                          (rowSelected)="onEntitySelect(r)"
                        />
                      }
                    </ul>
                    @if (entitySearch.groupsHasMore()) {
                      <button
                        type="button"
                        data-testid="chat-unified-search-see-more-groups"
                        (click)="entitySearch.expandSection('groups', queryInput())"
                        class="mt-1 text-xs text-signal-700 dark:text-signal-300"
                      >
                        {{
                          'chat.search.seeMore'
                            | transloco: { group: 'chat.search.groupLabelGroups' | transloco }
                        }}
                      </button>
                    }
                  </section>
                }
                @if (ragResults().length > 0) {
                  <section
                    role="group"
                    [attr.aria-label]="'chat.search.groupLabelRag' | transloco"
                    class="mb-3"
                  >
                    <h3 class="mb-1 text-xs font-semibold text-ink-500 dark:text-ink-400">
                      {{ 'chat.search.groupLabelRag' | transloco }}
                    </h3>
                    <ul class="flex flex-col gap-1">
                      @for (r of ragResults(); track r.id) {
                        <app-chat-search-result-row
                          [result]="r"
                          (rowSelected)="onEntitySelect(r)"
                        />
                      }
                    </ul>
                    @if (entitySearch.ragHasMore()) {
                      <button
                        type="button"
                        data-testid="chat-unified-search-see-more-rag"
                        (click)="entitySearch.expandSection('rag', queryInput())"
                        class="mt-1 text-xs text-signal-700 dark:text-signal-300"
                      >
                        {{
                          'chat.search.seeMore'
                            | transloco: { group: 'chat.search.groupLabelRag' | transloco }
                        }}
                      </button>
                    }
                  </section>
                }
                @if (supportResult(); as support) {
                  <section
                    role="group"
                    [attr.aria-label]="'chat.search.groupLabelSupport' | transloco"
                    class="mb-3"
                  >
                    <h3 class="mb-1 text-xs font-semibold text-ink-500 dark:text-ink-400">
                      {{ 'chat.search.groupLabelSupport' | transloco }}
                    </h3>
                    <ul class="flex flex-col gap-1">
                      <app-chat-search-result-row
                        [result]="support"
                        (rowSelected)="onEntitySelect(support)"
                      />
                    </ul>
                  </section>
                }
                @if (messageResults().length > 0) {
                  <section
                    role="group"
                    [attr.aria-label]="'chat.search.groupLabelMessages' | transloco"
                    class="mb-3"
                  >
                    <h3 class="mb-1 text-xs font-semibold text-ink-500 dark:text-ink-400">
                      {{ 'chat.search.groupLabelMessages' | transloco }}
                    </h3>
                    <ul class="flex flex-col gap-1">
                      @for (r of messageResults(); track r.id) {
                        <app-chat-search-result-row
                          [result]="r"
                          [query]="queryInput()"
                          (rowSelected)="onMessageSelect(r)"
                        />
                      }
                    </ul>
                    @if (messageSearch.hasMore()) {
                      <button
                        type="button"
                        data-testid="chat-unified-search-see-more-messages"
                        (click)="messageSearch.loadMore()"
                        class="mt-1 text-xs text-signal-700 dark:text-signal-300"
                      >
                        {{
                          'chat.search.seeMore'
                            | transloco: { group: 'chat.search.groupLabelMessages' | transloco }
                        }}
                      </button>
                    }
                  </section>
                }
              }
            }
          }
        </div>
      }
    </div>
  `,
})
export class ChatUnifiedSearchComponent {
  protected readonly entitySearch = inject(ChatEntitySearchService);
  protected readonly messageSearch = inject(ChatMessageSearchService);
  private readonly chatService = inject(ChatService);
  private readonly rowsService = inject(ChatDirectoryRowsService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly queryInput = signal('');
  protected readonly open = signal(false);

  protected readonly isBlankQuery = computed(() => this.queryInput().trim() === '');

  private readonly querySubject = new Subject<string>();

  constructor() {
    this.querySubject
      .pipe(debounceTime(QUERY_DEBOUNCE_MS), distinctUntilChanged(), takeUntilDestroyed())
      .subscribe((q) => this.runSearch(q));
  }

  protected readonly peopleResults = computed(() =>
    this.entitySearch.people().map((p) => ({ kind: 'person' as const, ...p })),
  );
  protected readonly groupsResults = computed(() =>
    this.entitySearch.groups().map((g) => ({ kind: 'group' as const, ...g })),
  );
  protected readonly ragResults = computed(() =>
    this.entitySearch.rag().map((r) => ({ kind: 'rag' as const, ...r })),
  );
  protected readonly supportResult = computed(() => {
    const support = this.entitySearch.support();
    return support ? ({ kind: 'support' as const, ...support } as const) : null;
  });
  protected readonly messageResults = computed(() =>
    this.messageSearch.results().map((m) => ({ kind: 'message' as const, ...m })),
  );

  private readonly entitySectionsFailed = computed(
    () =>
      this.entitySearch.peopleStatus() === 'error' &&
      this.entitySearch.groupsStatus() === 'error' &&
      this.entitySearch.supportStatus() === 'error' &&
      this.entitySearch.ragStatus() === 'error',
  );

  /** REQ-27/28/29, PLAN.md's "two-domain partial failure" decision (entities vs. messages, not
   * five-way — an explicit, already-accepted PLAN narrowing). */
  protected readonly status = computed<UnifiedStatus>(() => {
    if (this.isBlankQuery()) {
      return 'idle';
    }
    if (this.entitySearch.anyEntityLoading() || this.messageSearch.status() === 'loading') {
      return 'loading';
    }
    const entitiesFailed = this.entitySectionsFailed();
    const messagesFailed = this.messageSearch.status() === 'error';
    if (entitiesFailed && messagesFailed) {
      return 'error';
    }
    if (entitiesFailed || messagesFailed) {
      return 'results';
    }
    const total =
      this.peopleResults().length +
      this.groupsResults().length +
      this.ragResults().length +
      (this.supportResult() ? 1 : 0) +
      this.messageResults().length;
    return total === 0 ? 'no-results' : 'results';
  });

  protected onQueryInput(value: string): void {
    this.queryInput.set(value);
    this.open.set(true);
    this.querySubject.next(value.trim());
  }

  protected onFocus(): void {
    this.open.set(true);
    if (this.isBlankQuery()) {
      this.entitySearch.recentPlaces();
    }
  }

  @HostListener('document:keydown.escape')
  protected onEscape(): void {
    if (this.open()) {
      this.dismiss();
    }
  }

  @HostListener('document:click', ['$event'])
  protected onDocumentClick(event: MouseEvent): void {
    if (!this.open()) {
      return;
    }
    const target = event.target as Node;
    const host = this.hostRootElement();
    if (host && !host.contains(target)) {
      this.dismiss();
    }
  }

  private hostRootElement(): HTMLElement | null {
    return document.querySelector('[data-testid="chat-unified-search"]');
  }

  private runSearch(q: string): void {
    if (q === '') {
      this.entitySearch.recentPlaces();
      return;
    }
    this.messageSearch.search(q);
    this.entitySearch.search(q);
  }

  protected onEntitySelect(result: ChatSearchRowResult): void {
    switch (result.kind) {
      case 'person':
        this.chatService.openPersonConversation(result.userId).subscribe((id) => {
          this.dismiss();
          this.router.navigate(['/chat', id]);
        });
        return;
      case 'group': {
        const groupRow: GroupRow = {
          kind: 'group',
          key: `group:${result.id}`,
          id: result.id,
          displayName: result.title,
          visibility: result.visibility,
          isMember: result.isParticipant,
          icon: undefined,
        };
        this.dismiss();
        if (groupRow.isMember) {
          this.router.navigate(['/chat', groupRow.id]);
        } else {
          this.rowsService.onGroupClick(groupRow);
        }
        return;
      }
      case 'support':
        this.dismiss();
        this.router.navigate(['/chat/support', result.channelId]);
        return;
      case 'rag':
        this.dismiss();
        this.router.navigate(['/chat/articles', result.id]);
        return;
      case 'message':
        this.onMessageSelect(result);
        return;
    }
  }

  /** REQ-33/34 (Amended 2026-08-10): the target message id + the raw query string travel via
   * router navigation `state`, not a query param — the SPEC's amendment explicitly excludes
   * deep-linking a message via a shareable/bookmarkable URL, so this is a one-shot,
   * in-session-only signal `ConversationDetailComponent` reads off
   * `router.getCurrentNavigation()?.extras.state` in its own constructor. */
  protected onMessageSelect(result: Extract<ChatSearchRowResult, { kind: 'message' }>): void {
    const query = this.queryInput();
    this.dismiss();
    this.router.navigate(['/chat', result.conversationId], {
      state: { jumpToMessageId: result.id, jumpToQuery: query },
    });
  }

  protected onRecentPlaceSelect(place: ChatRecentPlaceDto): void {
    this.dismiss();
    if (place.kind === 'PEER_DIRECT' || place.kind === 'PEER_GROUP') {
      this.router.navigate(['/chat', place.conversationId]);
    } else if (place.kind === 'SUPPORT') {
      this.router.navigate(['/chat/support', place.conversationId]);
    } else {
      this.router.navigate(['/chat/articles', place.conversationId]);
    }
  }

  private dismiss(): void {
    this.open.set(false);
    this.queryInput.set('');
    this.messageSearch.reset();
    this.entitySearch.reset();
  }
}
