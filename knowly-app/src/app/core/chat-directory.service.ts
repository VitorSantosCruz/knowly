import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { ChatDiscoverableGroupDto } from './chat.model';
import { PageResponse } from './active-tenant.service';

/**
 * Backs the Groups half of REQ-8's search candidate set (chat-unified-ui PLAN.md, "New —
 * group discovery"). Kept separate from `ChatService` because discovery ("groups I could
 * join") is a distinct backend concept from "my conversations" — same reasoning
 * `PermissionsService`/`GlobalPermissionsService` already establish for "structurally
 * similar, conceptually distinct" state.
 *
 * The backend excludes `PRIVATE` and already-joined groups server-side (REQ-19/REQ-28) —
 * this service deliberately applies no client-side re-filtering of that invariant.
 */
@Injectable({ providedIn: 'root' })
export class ChatDirectoryService {
  private readonly http = inject(HttpClient);

  private readonly _discoverableGroups = signal<ChatDiscoverableGroupDto[]>([]);
  readonly discoverableGroups = this._discoverableGroups.asReadonly();

  fetchDiscoverableGroups(): void {
    const params = new HttpParams().set('page', 0).set('size', 200);
    this.http
      .get<PageResponse<ChatDiscoverableGroupDto>>('/api/chat/discoverable-groups', { params })
      .subscribe((response) => this._discoverableGroups.set(response.content));
  }
}
