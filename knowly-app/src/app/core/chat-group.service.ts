import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { ChatService } from './chat.service';
import {
  ChatAddParticipantsResultDto,
  ChatGroupVisibility,
  ChatJoinRequestDto,
  ConversationDetail,
  IconKey,
} from './chat.model';

/**
 * Group governance actions (join/leave/promote/visibility/delete/approve/reject/add
 * participants) — kept separate from `ChatService` because it's a materially different
 * concern (group governance, not messaging state), per chat-unified-ui PLAN.md.
 *
 * Every action here is **non-optimistic** (REQ-25/REQ-27): the HTTP call is made, and only
 * on success does this service patch `ChatService`'s own `_details`/`_conversations` signals
 * — it never keeps a second, parallel copy of conversation state. On any error, no signal
 * mutation happens; the error is propagated for the calling component to render inline.
 */
@Injectable({ providedIn: 'root' })
export class ChatGroupService {
  private readonly http = inject(HttpClient);
  private readonly chatService = inject(ChatService);

  private readonly _pendingJoinRequests = signal<Map<number, ChatJoinRequestDto[]>>(new Map());
  readonly pendingJoinRequests = this._pendingJoinRequests.asReadonly();

  /** Writes a fresh detail straight into `ChatService`'s own map (small cross-service call,
   * same pattern `SupportService` already uses when patching state it doesn't itself own the
   * source-of-truth fetch for) — `ChatGroupService` never keeps a second, parallel copy. */
  private patchDetail(id: number, detail: ConversationDetail): void {
    this.chatService.patchDetail(id, detail);
  }

  join(id: number): Observable<ConversationDetail> {
    return this.http
      .post<ConversationDetail>(`/api/chat/conversations/${id}/join`, {})
      .pipe(tap((detail) => this.patchDetail(id, detail)));
  }

  requestToJoin(id: number): Observable<ChatJoinRequestDto> {
    return this.http.post<ChatJoinRequestDto>(`/api/chat/conversations/${id}/join-requests`, {});
  }

  fetchPendingJoinRequests(id: number): void {
    this.http
      .get<ChatJoinRequestDto[]>(`/api/chat/conversations/${id}/join-requests`, {
        params: { status: 'PENDING' },
      })
      .subscribe((requests) => {
        this._pendingJoinRequests.update((map) => new Map(map).set(id, requests));
      });
  }

  private removeFromPending(id: number, requestId: number): void {
    this._pendingJoinRequests.update((map) => {
      const next = new Map(map);
      next.set(
        id,
        (next.get(id) ?? []).filter((r) => r.id !== requestId),
      );
      return next;
    });
  }

  approveJoinRequest(id: number, requestId: number): Observable<ChatJoinRequestDto> {
    return this.http
      .post<ChatJoinRequestDto>(
        `/api/chat/conversations/${id}/join-requests/${requestId}/approve`,
        {},
      )
      .pipe(
        tap(() => {
          // REQ-30a's 400 CHAT_INELIGIBLE_PARTICIPANT case never reaches this `tap` (it's an
          // error response) — the request correctly stays PENDING/in the list on that path.
          this.removeFromPending(id, requestId);
          this.chatService.openConversation(id);
        }),
      );
  }

  rejectJoinRequest(id: number, requestId: number): Observable<ChatJoinRequestDto> {
    return this.http
      .post<ChatJoinRequestDto>(
        `/api/chat/conversations/${id}/join-requests/${requestId}/reject`,
        {},
      )
      .pipe(tap(() => this.removeFromPending(id, requestId)));
  }

  promote(id: number, userId: number): Observable<ConversationDetail> {
    return this.http
      .post<ConversationDetail>(`/api/chat/conversations/${id}/admins/${userId}`, {})
      .pipe(tap((detail) => this.patchDetail(id, detail)));
  }

  removeParticipant(id: number, userId: number): Observable<ConversationDetail> {
    return this.http
      .delete<ConversationDetail>(`/api/chat/conversations/${id}/participants/${userId}`)
      .pipe(tap((detail) => this.patchDetail(id, detail)));
  }

  leave(id: number): Observable<void> {
    return this.http
      .post<void>(`/api/chat/conversations/${id}/leave`, {})
      .pipe(tap(() => this.chatService.dropConversation(id)));
  }

  changeVisibility(id: number, visibility: ChatGroupVisibility): Observable<ConversationDetail> {
    return this.http
      .put<ConversationDetail>(`/api/chat/conversations/${id}/visibility`, { visibility })
      .pipe(tap((detail) => this.patchDetail(id, detail)));
  }

  deleteGroup(id: number): Observable<void> {
    return this.http
      .delete<void>(`/api/chat/conversations/${id}`)
      .pipe(tap(() => this.chatService.dropConversation(id)));
  }

  /** Amendment (4), REQ-40 (final): group rename. `403` (not a current group admin) and `404`
   * (unknown/wrong-kind/deleted) both leave `_details` untouched — no signal mutation happens on
   * error, the calling component renders its own inline error (REQ-41). */
  rename(id: number, title: string, icon?: IconKey): Observable<ConversationDetail> {
    return this.http
      .put<ConversationDetail>(`/api/chat/conversations/${id}`, { title, icon })
      .pipe(tap((detail) => this.patchDetail(id, detail)));
  }

  addParticipants(id: number, userIds: number[]): Observable<ChatAddParticipantsResultDto> {
    return this.http
      .post<ChatAddParticipantsResultDto>(`/api/chat/conversations/${id}/participants`, {
        userIds,
      })
      .pipe(tap((result) => this.patchDetail(id, result.conversation)));
  }
}
