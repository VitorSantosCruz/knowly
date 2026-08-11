import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { catchError, of } from 'rxjs';
import {
  ChatMessageSearchPageDto,
  ChatMessageSearchResultDto,
  ChatMessageSearchStatus,
} from './chat.model';

/**
 * `chat-message-search` PLAN.md — signals-based, mirrors `ChatDirectoryService`'s shape, kept
 * separate from `ChatService` (cross-conversation, filtered, cursor-paginated search over
 * `GET /api/chat/messages/search`, a fundamentally different concern than "my conversations +
 * their message history" addressed by `conversationId`).
 *
 * **Amended (2026-08-10)**: `search()`'s signature narrows to `search(q: string)` — the old
 * `senderId`/`conversationId`/`dateFrom`/`dateTo` filter-param composition is removed alongside
 * `chat-search-dialog.component.ts`'s own retirement (no filter form exists anymore); this
 * service now always calls the backend with only `q` set. `_results`/`_status`/`_nextCursor`/
 * `_lastQuery`/`loadMore()`/`reset()` are otherwise byte-for-byte unchanged from the shipped
 * implementation.
 */
@Injectable({ providedIn: 'root' })
export class ChatMessageSearchService {
  private readonly http = inject(HttpClient);

  private readonly _results = signal<ChatMessageSearchResultDto[]>([]);
  readonly results = this._results.asReadonly();

  private readonly _status = signal<ChatMessageSearchStatus>('idle');
  readonly status = this._status.asReadonly();

  private readonly _nextCursor = signal<string | null>(null);
  readonly hasMore = computed(() => this._nextCursor() !== null);

  private readonly _lastQuery = signal('');
  readonly lastQuery = this._lastQuery.asReadonly();

  private lastQ: string | null = null;

  /** Bug fix (found live: type-ahead flicker while typing) — a plain `.subscribe()` per
   * keystroke's debounced `search()` call has no cancellation/ordering guarantee: an in-flight
   * request for an earlier, shorter query can resolve AFTER a later request for a longer one and
   * overwrite its correct, newer results with stale/empty ones. Guarded with a monotonically
   * increasing generation token instead of `switchMap` — `runSearch()` in
   * `chat-unified-search.component.ts` fires this and `ChatEntitySearchService.search()` as two
   * independent HTTP calls off the same debounced query, so the guard lives here rather than in
   * an RxJS operator chain the component doesn't own the subscription for. */
  private searchGeneration = 0;

  search(q: string): void {
    this.lastQ = q;
    this._lastQuery.set(q);
    this._status.set('loading');

    const generation = ++this.searchGeneration;

    this.http
      .get<ChatMessageSearchPageDto>('/api/chat/messages/search', {
        params: new HttpParams().set('q', q),
      })
      .pipe(catchError(() => of(null)))
      .subscribe((page) => {
        if (generation !== this.searchGeneration) {
          // A newer search() call has already superseded this one — never let a stale response
          // overwrite the current query's state.
          return;
        }
        if (page === null) {
          this._status.set('error');
          return;
        }
        this._results.set(page.results);
        this._nextCursor.set(page.nextCursor);
        this._status.set(page.results.length === 0 ? 'no-results' : 'results');
      });
  }

  loadMore(): void {
    const cursor = this._nextCursor();
    if (cursor === null || this._status() === 'loading' || this.lastQ === null) {
      return;
    }
    const q = this.lastQ;
    this._status.set('loading');

    this.http
      .get<ChatMessageSearchPageDto>('/api/chat/messages/search', {
        params: new HttpParams().set('q', q).set('cursor', cursor),
      })
      .pipe(catchError(() => of(null)))
      .subscribe((page) => {
        if (page === null) {
          this._status.set('error');
          return;
        }
        this._results.update((existing) => [...existing, ...page.results]);
        this._nextCursor.set(page.nextCursor);
        this._status.set(this._results().length === 0 ? 'no-results' : 'results');
      });
  }

  reset(): void {
    this._results.set([]);
    this._status.set('idle');
    this._nextCursor.set(null);
    this._lastQuery.set('');
    this.lastQ = null;
    // Also supersedes any in-flight search()/loadMore() response — reopening the dropdown always
    // starts clean, never gets repopulated by a request that was already in flight when it closed.
    this.searchGeneration += 1;
  }
}
