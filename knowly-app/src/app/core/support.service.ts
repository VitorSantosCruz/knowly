import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { catchError, Observable, of, tap, throwError } from 'rxjs';
import {
  ConversationDetail,
  DisplayMessage,
  emptyMessageCacheEntry,
  Message,
  MessageCacheEntry,
  MessagePage,
  TicketSummary,
} from './chat.model';

const PAGE_SIZE = 30;

function channelKey(tenantId: number, memberUserId: number): string {
  return `${tenantId}:${memberUserId}`;
}

/**
 * Signals-based support-channel service (REQ-10..18), mirroring `ChatService`'s shape.
 *
 * Deviation from PLAN.md: there is no `SupportChannelSummary` DTO and no GET endpoint
 * returning a member's ticket history/open-ticket status — the backend only returns a
 * `TicketSummary` (`SupportTicketDto`) from the four ticket action endpoints
 * (open/claim/transfer/close) and an unclaimed-list endpoint scoped to `OPEN` tickets only.
 * `myOpenTicket()` is therefore tracked purely from those action responses within the current
 * session (it starts `null`/unknown on a fresh page load and is set by `openTicket()`'s
 * response, or by `openTicket()`'s 409 body, which does carry back nothing usable either —
 * see `openTicket()`'s doc comment for the concrete fallback this implies for REQ-11).
 */
@Injectable({ providedIn: 'root' })
export class SupportService {
  private readonly http = inject(HttpClient);

  private readonly _myChannel = signal<ConversationDetail | null>(null);
  readonly myChannel = this._myChannel.asReadonly();

  private readonly _myOpenTicket = signal<TicketSummary | null>(null);
  readonly myOpenTicket = this._myOpenTicket.asReadonly();

  private readonly _myChannelNotFound = signal(false);
  readonly myChannelNotFound = this._myChannelNotFound.asReadonly();

  private readonly _inboxTickets = signal<TicketSummary[]>([]);
  readonly inboxTickets = this._inboxTickets.asReadonly();

  /** The ticket a staff user is currently viewing/acting on (REQ-13..16) — set by
   * claim()/transfer()/close(), since there is no GET returning "the ticket for member X". */
  private readonly _activeTicket = signal<TicketSummary | null>(null);
  readonly activeTicket = this._activeTicket.asReadonly();

  private readonly _channelMessageCache = signal<Map<string, MessageCacheEntry>>(new Map());
  readonly channelMessageCache = this._channelMessageCache.asReadonly();

  /**
   * `myChannelNotFound()` is set on a 404 — this is the one reliable "definitely no ticket
   * has ever been opened" signal (the channel is lazily created on first `openTicket()` call,
   * per `SupportTicketService#getOrCreateChannel`), which is what REQ-11's "no open ticket"
   * branch relies on for a member who has never opened a ticket at all.
   */
  fetchMyChannel(tenantId: number, memberUserId: number): void {
    this.http
      .get<ConversationDetail>(`/api/tenants/${tenantId}/support/members/${memberUserId}/channel`)
      .subscribe({
        next: (detail) => {
          this._myChannel.set(detail);
          this._myChannelNotFound.set(false);
        },
        error: (error) => {
          if (error?.status === 404) {
            this._myChannel.set(null);
            this._myChannelNotFound.set(true);
          }
        },
      });
  }

  /**
   * On success, this is the authoritative "there is now an open ticket" signal (REQ-11).
   * On a 409 ("already has an open ticket"), the caller should treat that as "there was
   * already one open" and fall back to rendering the channel's message thread rather than
   * looping/retrying — see `member-support-channel.component.ts`.
   */
  openTicket(tenantId: number): Observable<TicketSummary> {
    return this.http
      .post<TicketSummary>(`/api/tenants/${tenantId}/support/tickets`, {})
      .pipe(tap((ticket) => this._myOpenTicket.set(ticket)));
  }

  fetchInbox(tenantId: number): void {
    this.http
      .get<TicketSummary[]>(`/api/tenants/${tenantId}/support/tickets/unclaimed`)
      .pipe(catchError(() => of([] as TicketSummary[])))
      .subscribe((tickets) => {
        this._inboxTickets.update((current) => {
          const merged = new Map(current.map((t) => [t.id, t]));
          for (const ticket of tickets) {
            merged.set(ticket.id, ticket);
          }
          return [...merged.values()];
        });
      });
  }

  claim(tenantId: number, ticketId: number): Observable<TicketSummary> {
    return this.http
      .post<TicketSummary>(`/api/tenants/${tenantId}/support/tickets/${ticketId}/claim`, {})
      .pipe(
        tap((ticket) => {
          this._inboxTickets.update((list) => list.filter((t) => t.id !== ticketId));
          this.patchTicket(ticket);
          this._activeTicket.set(ticket);
        }),
      );
  }

  transfer(tenantId: number, ticketId: number, toStaffUserId: number): Observable<TicketSummary> {
    return this.http
      .post<TicketSummary>(`/api/tenants/${tenantId}/support/tickets/${ticketId}/transfer`, {
        toStaffUserId,
      })
      .pipe(
        tap((ticket) => {
          this.patchTicket(ticket);
          this._activeTicket.set(ticket);
        }),
      );
  }

  close(tenantId: number, ticketId: number): Observable<TicketSummary> {
    return this.http
      .post<TicketSummary>(`/api/tenants/${tenantId}/support/tickets/${ticketId}/close`, {})
      .pipe(
        tap((ticket) => {
          this.patchTicket(ticket);
          this._activeTicket.set(ticket);
        }),
      );
  }

  private patchTicket(ticket: TicketSummary): void {
    if (this._myOpenTicket()?.id === ticket.id) {
      this._myOpenTicket.set(ticket.status === 'CLOSED' ? null : ticket);
    }
  }

  /** REQ-17: set when `openChannel()`'s first-page fetch 403s (viewer lacks
   * `SUPPORT_CHANNEL_VIEW`) — lets `member-support-browse.component.ts` render the existing
   * no-access state instead of a partial view. */
  private readonly _channelAccessDenied = signal<Set<string>>(new Set());

  channelAccessDenied(tenantId: number, memberUserId: number): boolean {
    return this._channelAccessDenied().has(channelKey(tenantId, memberUserId));
  }

  openChannel(tenantId: number, memberUserId: number): void {
    const key = channelKey(tenantId, memberUserId);
    this.patchEntry(key, (entry) => ({ ...entry, loading: true, loadError: false }));

    this.http
      .get<MessagePage>(
        `/api/tenants/${tenantId}/support/members/${memberUserId}/channel/messages`,
        { params: new HttpParams().set('size', PAGE_SIZE) },
      )
      .subscribe({
        next: (page) => {
          this._channelAccessDenied.update((set) => {
            const next = new Set(set);
            next.delete(key);
            return next;
          });
          this.seedFirstPage(key, page);
        },
        error: (error) => {
          if (error?.status === 403) {
            this._channelAccessDenied.update((set) => new Set(set).add(key));
          }
          this.patchEntry(key, (entry) => ({ ...entry, loading: false, loadError: true }));
        },
      });
  }

  private seedFirstPage(key: string, page: MessagePage): void {
    this.patchEntry(key, () => ({
      messages: page.messages,
      hasMore: page.nextCursor !== null,
      oldestCursor: page.nextCursor,
      newestCursor:
        page.messages.length > 0 ? String(page.messages[page.messages.length - 1].id) : null,
      loadError: false,
      loading: false,
    }));
  }

  loadOlderMessages(tenantId: number, memberUserId: number): void {
    const key = channelKey(tenantId, memberUserId);
    const entry = this.entryOf(tenantId, memberUserId);
    if (!entry.oldestCursor) {
      return;
    }

    this.patchEntry(key, (current) => ({ ...current, loading: true, loadError: false }));
    this.http
      .get<MessagePage>(
        `/api/tenants/${tenantId}/support/members/${memberUserId}/channel/messages`,
        { params: new HttpParams().set('before', entry.oldestCursor).set('size', PAGE_SIZE) },
      )
      .subscribe({
        next: (page) => this.prependOlder(key, page),
        error: () =>
          this.patchEntry(key, (current) => ({ ...current, loading: false, loadError: true })),
      });
  }

  private prependOlder(key: string, page: MessagePage): void {
    this.patchEntry(key, (current) => {
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

  pollNewMessages(tenantId: number, memberUserId: number): void {
    const key = channelKey(tenantId, memberUserId);
    const entry = this.entryOf(tenantId, memberUserId);
    const params = new HttpParams().set('size', PAGE_SIZE);

    this.http
      .get<MessagePage>(
        `/api/tenants/${tenantId}/support/members/${memberUserId}/channel/messages`,
        { params: entry.newestCursor ? params.set('after', entry.newestCursor) : params },
      )
      .subscribe((page) => this.appendNewer(key, page));
  }

  private appendNewer(key: string, page: MessagePage): void {
    if (page.messages.length === 0) {
      return;
    }
    this.patchEntry(key, (current) => {
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

  sendMessage(
    tenantId: number,
    memberUserId: number,
    content: string,
    localId: string,
  ): Observable<Message> {
    const key = channelKey(tenantId, memberUserId);
    this.upsertLocal(key, localId, content);

    return this.http
      .post<Message>(`/api/tenants/${tenantId}/support/members/${memberUserId}/channel/messages`, {
        content,
      })
      .pipe(
        tap((message) => this.resolveLocal(key, localId, message)),
        catchError((error) => {
          this.failLocal(key, localId);
          return throwError(() => error);
        }),
      );
  }

  private upsertLocal(key: string, localId: string, content: string): void {
    this.patchEntry(key, (current) => {
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

  private resolveLocal(key: string, localId: string, message: Message): void {
    this.patchEntry(key, (current) => ({
      ...current,
      messages: current.messages.map((m) =>
        m.localId === localId ? { ...message, sendState: undefined, localId } : m,
      ),
      newestCursor: String(message.id),
    }));
  }

  private failLocal(key: string, localId: string): void {
    this.patchEntry(key, (current) => ({
      ...current,
      messages: current.messages.map((m) =>
        m.localId === localId ? { ...m, sendState: 'failed' as const } : m,
      ),
    }));
  }

  entryOf(tenantId: number, memberUserId: number): MessageCacheEntry {
    return (
      this._channelMessageCache().get(channelKey(tenantId, memberUserId)) ??
      emptyMessageCacheEntry()
    );
  }

  private patchEntry(key: string, updater: (entry: MessageCacheEntry) => MessageCacheEntry): void {
    this._channelMessageCache.update((map) => {
      const next = new Map(map);
      next.set(key, updater(next.get(key) ?? emptyMessageCacheEntry()));
      return next;
    });
  }
}
