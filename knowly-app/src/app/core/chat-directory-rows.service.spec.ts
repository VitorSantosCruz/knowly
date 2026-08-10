import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { ActiveTenantService } from './active-tenant.service';
import { ChatDirectoryRowsService } from './chat-directory-rows.service';

describe('ChatDirectoryRowsService — Amendment (3) unified rows', () => {
  let service: ChatDirectoryRowsService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ChatDirectoryRowsService);
    httpMock = TestBed.inject(HttpTestingController);
    vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
  });

  afterEach(() => httpMock.verify());

  function flushInit(opts: {
    conversations?: unknown[];
    eligible?: unknown[];
    discoverable?: unknown[];
  }): void {
    service.ensureLoaded();
    httpMock.expectOne('/api/chat/conversations').flush(opts.conversations ?? []);
    httpMock
      .expectOne((r) => r.url === '/api/chat/eligible-participants' && !r.params.has('tenantId'))
      .flush(opts.eligible ?? []);
    httpMock
      .expectOne((r) => r.url === '/api/chat/discoverable-groups')
      .flush({
        content: opts.discoverable ?? [],
        page: 0,
        size: 200,
        totalElements: (opts.discoverable ?? []).length,
        totalPages: 1,
      });
    httpMock
      .expectOne('/api/users/me/profile')
      .flush({ userId: 1, email: 'me@x.com', fields: {}, avatarUrl: null });
    httpMock
      .expectOne('/api/tenants/active')
      .flush(null, { status: 204, statusText: 'No Content' });
  }

  it('conversationRows() returns [supportRow, ...rest] with Support always first, regardless of underlying ordering', () => {
    flushInit({
      conversations: [
        { id: 5, kind: 'PEER_DIRECT', tenantId: null, title: null, participantUserIds: [1, 2] },
        { id: 6, kind: 'PEER_GROUP', tenantId: null, title: 'Group', participantUserIds: [1, 3] },
      ],
      eligible: [{ userId: 2, nickname: 'Bob' }],
    });

    const rows = service.conversationRows();
    expect(rows[0]).toEqual(service.supportRow);
    expect(rows.some((r) => r.kind === 'person' && r.userId === 2)).toBe(true);
    expect(rows.some((r) => r.kind === 'group' && r.id === 6)).toBe(true);
  });

  it('conversationRows() never changes because of talkedQuery/search state (no such input exists on the service)', () => {
    flushInit({
      conversations: [
        { id: 5, kind: 'PEER_DIRECT', tenantId: null, title: null, participantUserIds: [1, 2] },
      ],
      eligible: [{ userId: 2, nickname: 'Bob' }],
    });

    const before = service.conversationRows();
    // Nothing on this service exposes a query signal that could affect conversationRows() —
    // this assertion anchors that invariant by re-reading it and expecting no change.
    const after = service.conversationRows();
    expect(after).toEqual(before);
  });

  it('discoveryRows() returns not-yet-messaged people and discoverable, non-member groups, with zero overlap against conversationRows()', () => {
    flushInit({
      conversations: [
        { id: 5, kind: 'PEER_DIRECT', tenantId: null, title: null, participantUserIds: [1, 2] },
      ],
      eligible: [
        { userId: 2, nickname: 'Bob' },
        { userId: 3, nickname: 'Alice' },
      ],
      discoverable: [
        { id: 9, title: 'Grupo Público', tenantId: 1, visibility: 'PUBLIC', participantCount: 2 },
      ],
    });

    const discovery = service.discoveryRows();
    const conversation = service.conversationRows();

    expect(discovery.some((r) => r.kind === 'person' && r.userId === 3)).toBe(true);
    expect(discovery.some((r) => r.kind === 'group' && r.id === 9)).toBe(true);
    // Bob has an existing conversation, so must not appear in discoveryRows().
    expect(discovery.some((r) => r.kind === 'person' && r.userId === 2)).toBe(false);

    const discoveryKeys = new Set(discovery.map((r) => r.key));
    const conversationKeys = new Set(conversation.map((r) => r.key));
    for (const key of discoveryKeys) {
      expect(conversationKeys.has(key)).toBe(false);
    }
  });

  it('discoveryRows() sorts alphabetically by displayName — the documented interim fallback for REQ-2d, not the real recency sort', () => {
    flushInit({
      eligible: [
        { userId: 2, nickname: 'Zed' },
        { userId: 3, nickname: 'Alice' },
      ],
      discoverable: [
        { id: 9, title: 'Middle Group', tenantId: 1, visibility: 'PUBLIC', participantCount: 2 },
      ],
    });

    const names = service.discoveryRows().map((r) => r.displayName);
    expect(names).toEqual([...names].sort((a, b) => a.localeCompare(b)));
  });

  // Bug fix (2026-08-10): switching the active tenant used to leave `conversations()`/
  // `discoverableGroups()` stale — a staff-only group (or a previous tenant's groups) loaded
  // before the switch kept showing after entering/leaving/switching a tenant, until a full page
  // reload. `maybeRefetchConversations()` now re-fetches both whenever `activeTenantId()`
  // changes, mirroring the already-correct `eligibleParticipants`/`articles` re-fetch pattern.
  it('re-fetches conversations and discoverable-groups when the active tenant changes', () => {
    flushInit({
      conversations: [
        {
          id: 6,
          kind: 'PEER_GROUP',
          tenantId: null,
          title: 'Staff Internal Group',
          participantUserIds: [1, 3],
        },
      ],
    });

    expect(service.conversationRows().some((r) => r.kind === 'group' && r.id === 6)).toBe(true);

    const activeTenantService = TestBed.inject(ActiveTenantService);
    activeTenantService.selectTenant(10, 'Tenant A').subscribe();
    httpMock.expectOne('/api/tenants/active').flush(null, { status: 200, statusText: 'OK' });
    TestBed.tick();

    // Switching tenants must trigger a fresh fetch of both conversations and discoverable
    // groups, scoped to the new tenant context — this is the assertion that was previously red.
    httpMock.expectOne('/api/chat/conversations').flush([]);
    httpMock
      .expectOne((r) => r.url === '/api/chat/discoverable-groups')
      .flush({ content: [], page: 0, size: 200, totalElements: 0, totalPages: 1 });
    // Unrelated to this fix, but the active tenant change also re-fires these two already-correct
    // effects (eligible-participants/articles) — drained here so httpMock.verify() stays clean.
    httpMock
      .expectOne(
        (r) => r.url === '/api/chat/eligible-participants' && r.params.get('tenantId') === '10',
      )
      .flush([]);
    httpMock.expectOne('/api/tenants/10/conversations').flush([]);

    expect(service.conversationRows().some((r) => r.kind === 'group' && r.id === 6)).toBe(false);
  });
});
