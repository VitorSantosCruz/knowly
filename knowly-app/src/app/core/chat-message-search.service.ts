import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { catchError, of } from 'rxjs';
import {
  ChatMessageSearchFilters,
  ChatMessageSearchPageDto,
  ChatMessageSearchResultDto,
  ChatMessageSearchStatus,
} from './chat.model';

/**
 * `chat-message-search` PLAN.md — signals-based, mirrors `ChatDirectoryService`'s shape, kept
 * separate from `ChatService` (cross-conversation, filtered, cursor-paginated search over
 * `GET /api/chat/messages/search`, a fundamentally different concern than "my conversations +
 * their message history" addressed by `conversationId`).
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

  private lastFilters: ChatMessageSearchFilters | null = null;

  search(filters: ChatMessageSearchFilters): void {
    this.lastFilters = filters;
    this._lastQuery.set(filters.q);
    this._status.set('loading');

    this.http
      .get<ChatMessageSearchPageDto>('/api/chat/messages/search', {
        params: this.buildParams(filters),
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
    if (cursor === null || this._status() === 'loading' || this.lastFilters === null) {
      return;
    }
    const filters = this.lastFilters;
    this._status.set('loading');

    this.http
      .get<ChatMessageSearchPageDto>('/api/chat/messages/search', {
        params: this.buildParams(filters).set('cursor', cursor),
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
    this.lastFilters = null;
  }

  private buildParams(filters: ChatMessageSearchFilters): HttpParams {
    let params = new HttpParams().set('q', filters.q);
    if (filters.senderId !== undefined) {
      params = params.set('senderId', filters.senderId);
    }
    if (filters.conversationId !== undefined) {
      params = params.set('conversationId', filters.conversationId);
    }
    if (filters.dateFrom !== undefined) {
      params = params.set('dateFrom', filters.dateFrom);
    }
    if (filters.dateTo !== undefined) {
      params = params.set('dateTo', filters.dateTo);
    }
    return params;
  }
}
