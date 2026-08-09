import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { IconKey } from './chat.model';

export type MessageRole = 'USER' | 'ASSISTANT';

export interface ConversationSummary {
  id: number;
  title: string | null;
  /** Amendment (4), REQ-38/REQ-39: additive, nullable — `null` for every pre-Amendment-(4) RAG
   * conversation (V32 backfill leaves `icon` untouched for existing rows). */
  icon: IconKey | null;
}

export interface Message {
  id: number;
  role: MessageRole;
  content: string;
}

export interface ConversationDetail extends ConversationSummary {
  messages: Message[];
}

export type ChatStreamEvent =
  | { type: 'message'; data: string }
  | { type: 'done' }
  | { type: 'error'; data: string }
  | { type: 'permission-denied' };

@Injectable({ providedIn: 'root' })
export class ConversationService {
  private readonly http = inject(HttpClient);

  list(tenantId: number): Observable<ConversationSummary[]> {
    return this.http.get<ConversationSummary[]>(`/api/tenants/${tenantId}/conversations`);
  }

  /** Amendment (4), REQ-38: `title` is now required (non-blank, backend-enforced) and `icon` is
   * optional — every call site must route through the new naming dialog rather than calling this
   * with no name (see `create-conversation-dialog.component.ts`). */
  create(tenantId: number, title: string, icon?: IconKey): Observable<ConversationSummary> {
    return this.http.post<ConversationSummary>(`/api/tenants/${tenantId}/conversations`, {
      title,
      icon,
    });
  }

  /** Amendment (4), REQ-39: RAG rename. The backend returns `404` (not `403`) when the caller
   * isn't this conversation's owning participant — deliberate, existence-hiding (see PLAN.md's
   * "Amendment (4) reconciliation" — AppSec's status-code-agnostic error requirement). */
  rename(
    tenantId: number,
    conversationId: number,
    title: string,
    icon?: IconKey,
  ): Observable<ConversationSummary> {
    return this.http.put<ConversationSummary>(
      `/api/tenants/${tenantId}/conversations/${conversationId}`,
      { title, icon },
    );
  }

  getDetail(tenantId: number, conversationId: number): Observable<ConversationDetail> {
    return this.http.get<ConversationDetail>(
      `/api/tenants/${tenantId}/conversations/${conversationId}`,
    );
  }

  sendMessage(
    tenantId: number,
    conversationId: number,
    content: string,
  ): Observable<ChatStreamEvent> {
    return new Observable<ChatStreamEvent>((subscriber) => {
      const controller = new AbortController();

      fetch(`/api/tenants/${tenantId}/conversations/${conversationId}/messages`, {
        method: 'POST',
        credentials: 'include',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ content }),
        signal: controller.signal,
      })
        .then((response) => this.pump(response, subscriber))
        .catch((error: unknown) => subscriber.error(error));

      return () => controller.abort();
    });
  }

  private async pump(
    response: Response,
    subscriber: {
      next: (event: ChatStreamEvent) => void;
      complete: () => void;
      error: (e: unknown) => void;
    },
  ): Promise<void> {
    if (!response.ok) {
      if (response.status === 403) {
        subscriber.next({ type: 'permission-denied' });
      } else {
        subscriber.next({ type: 'error', data: 'The assistant is unavailable.' });
      }
      subscriber.complete();
      return;
    }

    if (!response.body) {
      subscriber.complete();
      return;
    }

    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    let buffer = '';

    try {
      let done = false;
      while (!done) {
        const result = await reader.read();
        done = result.done;

        if (result.value) {
          buffer += decoder.decode(result.value, { stream: true });
          buffer = this.emitCompleteEvents(buffer, subscriber);
        }
      }
      subscriber.complete();
    } catch (error) {
      subscriber.error(error);
    }
  }

  private emitCompleteEvents(
    buffer: string,
    subscriber: { next: (event: ChatStreamEvent) => void },
  ): string {
    const parts = buffer.split('\n\n');
    const remainder = parts.pop() ?? '';

    for (const raw of parts) {
      if (!raw.trim()) {
        continue;
      }

      let eventName = 'message';
      let data = '';

      for (const line of raw.split('\n')) {
        if (line.startsWith('event:')) {
          eventName = line.slice('event:'.length).trim();
        } else if (line.startsWith('data:')) {
          data += line.slice('data:'.length).trim();
        }
      }

      if (eventName === 'done') {
        subscriber.next({ type: 'done' });
      } else if (eventName === 'error') {
        subscriber.next({ type: 'error', data });
      } else {
        subscriber.next({ type: 'message', data });
      }
    }

    return remainder;
  }
}
