import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { catchError, finalize, map, Observable, of, tap, throwError } from 'rxjs';
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
 * Mirrors the backend's `ChatCursor.encode` (`base64(String.valueOf(id))`) — the backend never
 * hands back a cursor pointing at the newest message (its own `nextCursor` always points at the
 * oldest end of whichever page it just returned), so polling/optimistic-send have to mint one
 * client-side from a known message id. A previous version sent the raw id string, which the
 * backend's `ChatCursor.decode` rejects outright (`CHAT_INVALID_CURSOR`, every poll cycle).
 */
function encodeMessageCursor(id: number): string {
  return btoa(String(id));
}

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

  /** REQ-9: set when `openConversation()`'s detail fetch 403/404s (not a participant, not
   * eligible for look-in) — lets the detail component render the existing no-access state. */
  private readonly _detailErrors = signal<Set<number>>(new Set());
  readonly detailErrors = this._detailErrors.asReadonly();

  /**
   * Bug fix (found live: "search twice inside the same open conversation and only the first jump
   * ever works") — `ChatUnifiedSearchComponent#onMessageSelect()` used to *always* go through
   * `router.navigate(['/chat', conversationId], { state: {...} })`. When the clicked result
   * belongs to the conversation that's already open, that's a same-URL navigation, and this app
   * doesn't override the Router's default `onSameUrlNavigation: 'ignore'` (see `app.config.ts`)
   * — Angular silently drops it, `history.state` never changes, and
   * `ConversationDetailComponent`'s `route.paramMap` subscription (the only place that used to
   * read a jump request) never re-fires. Every jump to a message already in view after the first
   * one was a no-op: no scroll, no highlight, no error.
   *
   * This signal is the direct, route-independent channel for that specific case:
   * `ChatUnifiedSearchComponent` detects "clicked result's conversation === currently open
   * conversation" itself (comparing against the Router's current URL) and calls `requestJump()`
   * instead of navigating; `ConversationDetailComponent` watches this signal in its own effect and
   * consumes (clears) it once read, independent of any `paramMap` emission.
   */
  private readonly _jumpRequest = signal<{
    conversationId: number;
    messageId: number;
    query: string;
  } | null>(null);
  readonly jumpRequest = this._jumpRequest.asReadonly();

  requestJump(conversationId: number, messageId: number, query: string): void {
    this._jumpRequest.set({ conversationId, messageId, query });
  }

  clearJumpRequest(): void {
    this._jumpRequest.set(null);
  }

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

  /**
   * chat-unified-ui PLAN.md's "A person search result opening a 1:1 with no existing
   * conversation" decision — the shared create-or-open helper `chat-directory-rows.service.ts`'s
   * `onPersonClick` and `chat-unified-search.component.ts`'s person-result click both call,
   * rather than duplicating the "existing conversation vs. create-and-open" branch a third time.
   * Resolves to that person's DIRECT conversation id (existing or newly created).
   */
  openPersonConversation(userId: number): Observable<number> {
    const existing = this._conversations().find(
      (c) => c.kind === 'PEER_DIRECT' && c.participantUserIds.includes(userId),
    );
    if (existing) {
      return of(existing.id);
    }
    return this.createConversation({ kind: 'DIRECT', participantUserIds: [userId] }).pipe(
      map((conversation) => conversation.id),
    );
  }

  openConversation(id: number): void {
    this.http.get<ConversationDetail>(`/api/chat/conversations/${id}`).subscribe({
      next: (detail) => {
        this._details.update((map) => new Map(map).set(id, detail));
        this._detailErrors.update((set) => {
          const next = new Set(set);
          next.delete(id);
          return next;
        });
      },
      error: () => this._detailErrors.update((set) => new Set(set).add(id)),
    });

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
        page.messages.length > 0
          ? encodeMessageCursor(page.messages[page.messages.length - 1].id)
          : null,
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

  /**
   * Bug fix (found live, reported as "consuming the backend nonstop" — a DIFFERENT root cause
   * from the earlier `before=`-cursor jump-pagination storms fixed in `7e6fc57`/`3d32946`/
   * `30378ea`, this one is about the plain 5s `after=` new-message poll `ConversationDetailComponent`
   * runs forever while a conversation is open): this used to fire a brand-new `GET .../messages`
   * request on every 5s tick with zero regard for whether the *previous* poll's request had
   * actually come back yet. Under load (many open conversations/tabs, or the backend itself
   * slowing down — exactly the "tortura" scenario reported), a poll response taking longer than
   * 5s let the next tick fire anyway, both still reading `entry.newestCursor` off the SAME
   * not-yet-updated cache entry (the first poll's response, which is what advances
   * `newestCursor` via `appendNewer`, hadn't landed yet) — so the second request went out with
   * the identical `after=<cursor>` as the first, in flight *concurrently*, not one after another.
   * Each additional overlapping poll adds more backend load without ever letting a response
   * catch up and advance the cursor, compounding: more concurrent requests, slower backend,
   * more requests still queued behind the same stale cursor — observed live as an unbounded
   * stream of identical `after=<cursor>` requests with no real gap between them, not a clean
   * one-every-5-seconds cadence. Fixed by tracking in-flight polls per conversation id and
   * skipping a tick entirely if that conversation's previous poll hasn't resolved yet — the very
   * next 5s tick after it finally does resolve picks up the now-advanced `newestCursor` and
   * proceeds normally.
   */
  private readonly pollInFlightIds = new Set<number>();

  pollNewMessages(id: number): void {
    if (this.pollInFlightIds.has(id)) {
      return;
    }

    const entry = this.entryOf(id);
    const params = new HttpParams().set('size', PAGE_SIZE);

    this.pollInFlightIds.add(id);
    this.http
      .get<MessagePage>(`/api/chat/conversations/${id}/messages`, {
        params: entry.newestCursor ? params.set('after', entry.newestCursor) : params,
      })
      .pipe(
        // A poll failing (e.g. a transient 5xx while the backend is under exactly the load this
        // fix addresses) must not surface as an unhandled RxJS error — `finalize` below still
        // clears `pollInFlightIds` either way, so the next 5s tick simply retries.
        catchError(() => of(null)),
        finalize(() => this.pollInFlightIds.delete(id)),
      )
      .subscribe((page) => {
        if (page) {
          this.appendNewer(id, page);
        }
      });
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
        newestCursor: encodeMessageCursor(deduped[deduped.length - 1].id),
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
      const exists = current.messages.some((m) => m.localId === localId);
      const pendingMessage: DisplayMessage = {
        id: -1,
        senderUserId: -1,
        senderNickname: '',
        content,
        createdAt: new Date().toISOString(),
        sendState: 'pending',
        localId,
      };
      if (!exists) {
        return { ...current, messages: [...current.messages, pendingMessage] };
      }
      return {
        ...current,
        messages: current.messages.map((m) =>
          m.localId === localId ? { ...m, sendState: 'pending' as const } : m,
        ),
      };
    });
  }

  private resolveLocal(id: number, localId: string, message: Message): void {
    this.patchEntry(id, (current) => ({
      ...current,
      messages: current.messages.map((m) =>
        m.localId === localId ? { ...message, sendState: undefined, localId } : m,
      ),
      newestCursor: encodeMessageCursor(message.id),
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

  /**
   * Same endpoint as `fetchEligibleParticipants`, but returns the list directly instead of
   * writing it into the shared `eligibleParticipants` signal — that signal already backs the
   * directory's "haven't talked yet" People list (`scope: 'direct'`); a caller fetching
   * `'group'`-scoped invite candidates (e.g. the group info modal's "invite someone" picker)
   * must not clobber it just because both happen to be visible on the same page.
   */
  getEligibleParticipants(scope: EligibilityScope, tenantId?: number): Observable<CandidateUser[]> {
    let params = new HttpParams().set('scope', scope);
    if (tenantId !== undefined) {
      params = params.set('tenantId', tenantId);
    }

    return this.http
      .get<CandidateUser[]>('/api/chat/eligible-participants', { params })
      .pipe(catchError(() => of([] as CandidateUser[])));
  }

  /** Small cross-service seam for `ChatGroupService`: writes a fresh detail (e.g. from a
   * governance action's response) straight into this service's own `_details` map, the single
   * source of truth for conversation detail state — never a second, parallel copy. */
  patchDetail(id: number, detail: ConversationDetail): void {
    this._details.update((map) => new Map(map).set(id, detail));
  }

  /** Small cross-service seam for `ChatGroupService`'s `leave`/`deleteGroup`: drops a
   * conversation from this service's own list + detail map once the backend confirms it. */
  dropConversation(id: number): void {
    this._conversations.update((list) => list.filter((c) => c.id !== id));
    this._details.update((map) => {
      const next = new Map(map);
      next.delete(id);
      return next;
    });
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
