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

  search(q: string): void {
    this.lastQ = q;
    this._lastQuery.set(q);
    this._status.set('loading');

    this.http
      .get<ChatMessageSearchPageDto>('/api/chat/messages/search', {
        params: new HttpParams().set('q', q),
      })
      .pipe(catchError(() => of(null)))
      .subscribe((page) => {
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
  }
}
