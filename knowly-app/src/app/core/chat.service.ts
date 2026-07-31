import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { catchError, map, Observable, of, tap, throwError } from 'rxjs';
import {
  CandidateUser,
  ConversationDetail,
  ConversationSummary,
  CreateConversationRequest,
  DisplayMessage,
  EligibilityScope,
  emptyMessageCacheEntry,
  Message,
  MessageCacheEntry,
  MessagePage,
} from './chat.model';

const PAGE_SIZE = 30;

/**
 * Signals-based peer-chat service (REQ-1, REQ-2, REQ-3, REQ-5, REQ-6, REQ-19, REQ-21),
 * following `PermissionsService`/`ActiveTenantService`'s shape: private signal(s) + public
 * `.asReadonly()` + fetch()/action methods owning the HTTP call.
 */
@Injectable({ providedIn: 'root' })
export class ChatService {
  private readonly http = inject(HttpClient);

  private readonly _conversations = signal<ConversationSummary[]>([]);
  readonly conversations = this._conversations.asReadonly();

  private readonly _details = signal<Map<number, ConversationDetail>>(new Map());
  readonly details = this._details.asReadonly();

  private readonly _messageCache = signal<Map<number, MessageCacheEntry>>(new Map());
  readonly messageCache = this._messageCache.asReadonly();

  private readonly _eligibleParticipants = signal<CandidateUser[]>([]);
  readonly eligibleParticipants = this._eligibleParticipants.asReadonly();

  fetchConversations(): void {
    this.http
      .get<ConversationSummary[]>('/api/chat/conversations')
      .subscribe((conversations) => this._conversations.set(conversations));
  }

  createConversation(request: CreateConversationRequest): Observable<ConversationSummary> {
    return this.http
      .post<ConversationSummary>('/api/chat/conversations', request)
      .pipe(tap((conversation) => this._conversations.update((list) => [...list, conversation])));
  }

  openConversation(id: number): void {
    this.http
      .get<ConversationDetail>(`/api/chat/conversations/${id}`)
      .subscribe((detail) => this._details.update((map) => new Map(map).set(id, detail)));

    this.patchEntry(id, (entry) => ({ ...entry, loading: true, loadError: false }));
    this.http
      .get<MessagePage>(`/api/chat/conversations/${id}/messages`, {
        params: new HttpParams().set('size', PAGE_SIZE),
      })
      .subscribe({
        next: (page) => this.seedFirstPage(id, page),
        error: () =>
          this.patchEntry(id, (entry) => ({ ...entry, loading: false, loadError: true })),
      });
  }

  private seedFirstPage(id: number, page: MessagePage): void {
    this.patchEntry(id, () => ({
      messages: page.messages,
      hasMore: page.nextCursor !== null,
      oldestCursor: page.nextCursor,
      newestCursor:
        page.messages.length > 0 ? String(page.messages[page.messages.length - 1].id) : null,
      loadError: false,
      loading: false,
    }));
  }

  loadOlderMessages(id: number): void {
    const entry = this.entryOf(id);
    if (!entry.oldestCursor) {
      return;
    }

    this.patchEntry(id, (current) => ({ ...current, loading: true, loadError: false }));
    this.http
      .get<MessagePage>(`/api/chat/conversations/${id}/messages`, {
        params: new HttpParams().set('before', entry.oldestCursor).set('size', PAGE_SIZE),
      })
      .subscribe({
        next: (page) => this.prependOlder(id, page),
        error: () =>
          this.patchEntry(id, (current) => ({ ...current, loading: false, loadError: true })),
      });
  }

  private prependOlder(id: number, page: MessagePage): void {
    this.patchEntry(id, (current) => {
      const knownIds = new Set(current.messages.map((m) => m.id));
      const deduped = page.messages.filter((m) => !knownIds.has(m.id));
      return {
        ...current,
        messages: [...deduped, ...current.messages],
        hasMore: page.nextCursor !== null,
        oldestCursor: page.nextCursor,
        loading: false,
        loadError: false,
      };
    });
  }

  pollNewMessages(id: number): void {
    const entry = this.entryOf(id);
    const params = new HttpParams().set('size', PAGE_SIZE);

    this.http
      .get<MessagePage>(`/api/chat/conversations/${id}/messages`, {
        params: entry.newestCursor ? params.set('after', entry.newestCursor) : params,
      })
      .subscribe((page) => this.appendNewer(id, page));
  }

  private appendNewer(id: number, page: MessagePage): void {
    if (page.messages.length === 0) {
      return;
    }

    this.patchEntry(id, (current) => {
      const knownIds = new Set(current.messages.map((m) => m.id));
      const deduped = page.messages.filter((m) => !knownIds.has(m.id));
      if (deduped.length === 0) {
        return current;
      }
      return {
        ...current,
        messages: [...current.messages, ...deduped],
        newestCursor: String(deduped[deduped.length - 1].id),
      };
    });
  }

  /**
   * REQ-5/REQ-6: appends optimistically (with a `pending` flag) as soon as the user sends —
   * not only on server confirmation — then replaces with the confirmed message on success, or
   * marks the same entry `failed` (never removed) on error. Re-invoking with the same
   * `localId` for a previously-`failed` entry is the retry path (task 17/18): it clears the
   * failed flag on success and re-marks it on repeated failure without duplicating the entry.
   */
  sendMessage(id: number, content: string, localId: string): Observable<Message> {
    this.upsertLocal(id, localId, content);

    return this.http.post<Message>(`/api/chat/conversations/${id}/messages`, { content }).pipe(
      tap((message) => this.resolveLocal(id, localId, message)),
      catchError((error) => {
        this.failLocal(id, localId);
        return throwError(() => error);
      }),
    );
  }

  private upsertLocal(id: number, localId: string, content: string): void {
    this.patchEntry(id, (current) => {
      const existingIndex = current.messages.findIndex((m) => m.localId === localId);
      const pendingMessage: DisplayMessage = {
        id: -1,
        senderUserId: -1,
        senderNickname: '',
        content,
        createdAt: new Date().toISOString(),
        sendState: 'pending',
        localId,
      };
      if (existingIndex === -1) {
        return { ...current, messages: [...current.messages, pendingMessage] };
      }
      const messages = [...current.messages];
      messages[existingIndex] = { ...messages[existingIndex], sendState: 'pending' };
      return { ...current, messages };
    });
  }

  private resolveLocal(id: number, localId: string, message: Message): void {
    this.patchEntry(id, (current) => ({
      ...current,
      messages: current.messages.map((m) =>
        m.localId === localId ? { ...message, sendState: undefined, localId } : m,
      ),
      newestCursor: String(message.id),
    }));
  }

  private failLocal(id: number, localId: string): void {
    this.patchEntry(id, (current) => ({
      ...current,
      messages: current.messages.map((m) =>
        m.localId === localId ? { ...m, sendState: 'failed' as const } : m,
      ),
    }));
  }

  fetchEligibleParticipants(scope: EligibilityScope, tenantId?: number): void {
    let params = new HttpParams().set('scope', scope);
    if (tenantId !== undefined) {
      params = params.set('tenantId', tenantId);
    }

    this.http
      .get<CandidateUser[]>('/api/chat/eligible-participants', { params })
      .pipe(catchError(() => of([] as CandidateUser[])))
      .subscribe((candidates) => this._eligibleParticipants.set(candidates));
  }

  entryOf(id: number): MessageCacheEntry {
    return this._messageCache().get(id) ?? emptyMessageCacheEntry();
  }

  private patchEntry(id: number, updater: (entry: MessageCacheEntry) => MessageCacheEntry): void {
    this._messageCache.update((map) => {
      const next = new Map(map);
      next.set(id, updater(next.get(id) ?? emptyMessageCacheEntry()));
      return next;
    });
  }
}
