import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { ChatService } from './chat.service';

describe('ChatService', () => {
  let service: ChatService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ChatService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('fetchConversations populates conversations()', () => {
    service.fetchConversations();
    httpMock
      .expectOne('/api/chat/conversations')
      .flush([
        { id: 1, kind: 'PEER_DIRECT', tenantId: null, title: null, participantUserIds: [1, 2] },
      ]);

    expect(service.conversations().length).toBe(1);
  });

  it('createConversation posts the DIRECT/GROUP payload and appends the result', () => {
    let result: unknown;
    service
      .createConversation({ kind: 'DIRECT', participantUserIds: [2] })
      .subscribe((c) => (result = c));

    const req = httpMock.expectOne('/api/chat/conversations');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ kind: 'DIRECT', participantUserIds: [2] });
    req.flush({
      id: 5,
      kind: 'PEER_DIRECT',
      tenantId: null,
      title: null,
      participantUserIds: [1, 2],
    });

    expect(result).toBeTruthy();
    expect(service.conversations().some((c) => c.id === 5)).toBe(true);
  });

  it('openConversation fetches detail and first message page, seeding the cache', () => {
    service.openConversation(1);

    httpMock.expectOne('/api/chat/conversations/1').flush({
      id: 1,
      kind: 'PEER_DIRECT',
      tenantId: null,
      title: null,
      participantUserIds: [1, 2],
      participantNicknames: {},
    });

    const messagesReq = httpMock.expectOne(
      (r) => r.url === '/api/chat/conversations/1/messages' && r.params.get('size') === '30',
    );
    messagesReq.flush({
      messages: [
        { id: 10, senderUserId: 2, senderNickname: 'Bob', content: 'hi', createdAt: 'now' },
      ],
      nextCursor: 'c9',
    });

    const entry = service.entryOf(1);
    expect(entry.messages.length).toBe(1);
    expect(entry.hasMore).toBe(true);
    expect(entry.oldestCursor).toBe('c9');
    expect(service.details().get(1)?.participantUserIds).toEqual([1, 2]);
  });

  function seedOpenConversation(id: number) {
    service.openConversation(id);
    httpMock.expectOne(`/api/chat/conversations/${id}`).flush({
      id,
      kind: 'PEER_DIRECT',
      tenantId: null,
      title: null,
      participantUserIds: [1, 2],
      participantNicknames: {},
    });
    httpMock
      .expectOne(
        (r) => r.url === `/api/chat/conversations/${id}/messages` && r.params.get('size') === '30',
      )
      .flush({
        messages: [
          { id: 10, senderUserId: 2, senderNickname: 'Bob', content: 'hi', createdAt: 'now' },
        ],
        nextCursor: 'c9',
      });
  }

  it('loadOlderMessages prepends without duplicating and updates cursor/hasMore', () => {
    seedOpenConversation(1);

    service.loadOlderMessages(1);
    const req = httpMock.expectOne(
      (r) => r.url === '/api/chat/conversations/1/messages' && r.params.get('before') === 'c9',
    );
    req.flush({
      messages: [
        { id: 5, senderUserId: 1, senderNickname: 'Me', content: 'older', createdAt: 'earlier' },
      ],
      nextCursor: null,
    });

    const entry = service.entryOf(1);
    expect(entry.messages.map((m) => m.id)).toEqual([5, 10]);
    expect(entry.hasMore).toBe(false);
    expect(entry.oldestCursor).toBeNull();
  });

  it('a failed loadOlderMessages call leaves existing messages untouched and sets loadError', () => {
    seedOpenConversation(1);

    service.loadOlderMessages(1);
    httpMock
      .expectOne(
        (r) => r.url === '/api/chat/conversations/1/messages' && r.params.get('before') === 'c9',
      )
      .flush('boom', { status: 500, statusText: 'error' });

    const entry = service.entryOf(1);
    expect(entry.messages.length).toBe(1);
    expect(entry.loadError).toBe(true);
  });

  it('pollNewMessages appends without duplicating, no-ops on empty page', () => {
    seedOpenConversation(1);

    service.pollNewMessages(1);
    httpMock
      .expectOne(
        (r) =>
          r.url === '/api/chat/conversations/1/messages' && r.params.get('after') === btoa('10'),
      )
      .flush({
        messages: [
          { id: 11, senderUserId: 2, senderNickname: 'Bob', content: 'new', createdAt: 'now2' },
        ],
        nextCursor: '11',
      });

    expect(service.entryOf(1).messages.map((m) => m.id)).toEqual([10, 11]);

    service.pollNewMessages(1);
    httpMock
      .expectOne(
        (r) =>
          r.url === '/api/chat/conversations/1/messages' && r.params.get('after') === btoa('11'),
      )
      .flush({ messages: [], nextCursor: null });

    expect(service.entryOf(1).messages.length).toBe(2);
  });

  it('sendMessage optimistically appends, then replaces on success; marks failed on error', () => {
    seedOpenConversation(1);

    service.sendMessage(1, 'hello', 'local-1').subscribe();
    let entry = service.entryOf(1);
    expect(entry.messages.at(-1)?.sendState).toBe('pending');

    httpMock
      .expectOne('/api/chat/conversations/1/messages')
      .flush({ id: 20, senderUserId: 1, senderNickname: 'Me', content: 'hello', createdAt: 'now' });

    entry = service.entryOf(1);
    expect(entry.messages.at(-1)?.sendState).toBeUndefined();
    expect(entry.messages.at(-1)?.id).toBe(20);
  });

  it('marks a message failed on send error, without removing it, and retry clears the flag', () => {
    seedOpenConversation(1);

    service.sendMessage(1, 'hello', 'local-1').subscribe({
      error: () => {
        // expected: the service surfaces the error via message sendState, not a rethrow assertion here.
      },
    });
    httpMock
      .expectOne('/api/chat/conversations/1/messages')
      .flush('boom', { status: 500, statusText: 'err' });

    let entry = service.entryOf(1);
    expect(entry.messages.length).toBe(2);
    expect(entry.messages.at(-1)?.sendState).toBe('failed');

    service.sendMessage(1, 'hello', 'local-1').subscribe();
    httpMock
      .expectOne('/api/chat/conversations/1/messages')
      .flush({ id: 21, senderUserId: 1, senderNickname: 'Me', content: 'hello', createdAt: 'now' });

    entry = service.entryOf(1);
    expect(entry.messages.length).toBe(2);
    expect(entry.messages.at(-1)?.sendState).toBeUndefined();
  });

  it('fetchEligibleParticipants calls the three scope variants verbatim, with no client-side filtering', () => {
    service.fetchEligibleParticipants('direct');
    httpMock
      .expectOne(
        (r) => r.url === '/api/chat/eligible-participants' && r.params.get('scope') === 'direct',
      )
      .flush([{ userId: 1, nickname: 'Staffer' }]);
    expect(service.eligibleParticipants()).toEqual([{ userId: 1, nickname: 'Staffer' }]);

    service.fetchEligibleParticipants('group', 7);
    httpMock
      .expectOne(
        (r) =>
          r.url === '/api/chat/eligible-participants' &&
          r.params.get('scope') === 'group' &&
          r.params.get('tenantId') === '7',
      )
      .flush([{ userId: 2, nickname: 'Member' }]);

    service.fetchEligibleParticipants('group-staff-only');
    httpMock
      .expectOne(
        (r) =>
          r.url === '/api/chat/eligible-participants' &&
          r.params.get('scope') === 'group-staff-only',
      )
      .flush([{ userId: 1, nickname: 'Staffer' }]);
  });
});
