import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient, withXsrfConfiguration } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { ChatGroupService } from './chat-group.service';
import { ChatService } from './chat.service';
import { ChatAddParticipantsResultDto, ConversationDetail } from './chat.model';

function detailFixture(overrides: Partial<ConversationDetail> = {}): ConversationDetail {
  return {
    id: 1,
    kind: 'PEER_GROUP',
    tenantId: 1,
    title: 'Grupo',
    participantUserIds: [1, 2],
    participantNicknames: { 1: 'Alice', 2: 'Bob' },
    visibility: 'PUBLIC',
    archivedAt: null,
    adminUserIds: [1],
    icon: null,
    ...overrides,
  };
}

describe('ChatGroupService', () => {
  let service: ChatGroupService;
  let chatService: ChatService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=test-csrf-token';
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(
          withXsrfConfiguration({ cookieName: 'XSRF-TOKEN', headerName: 'X-XSRF-TOKEN' }),
        ),
        provideHttpClientTesting(),
      ],
    });
    service = TestBed.inject(ChatGroupService);
    chatService = TestBed.inject(ChatService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 UTC';
  });

  it('attaches the X-XSRF-TOKEN header on a mutating call (join), mirroring ChatService', () => {
    service.join(1).subscribe();
    const req = httpMock.expectOne('/api/chat/conversations/1/join');
    expect(req.request.headers.get('X-XSRF-TOKEN')).toBe('test-csrf-token');
    req.flush(detailFixture());
  });

  describe('join', () => {
    it('POSTs an empty body and patches ChatService details on 200', () => {
      service.join(1).subscribe();
      const req = httpMock.expectOne('/api/chat/conversations/1/join');
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({});
      const detail = detailFixture({ participantUserIds: [1, 2, 3] });
      req.flush(detail);
      expect(chatService.details().get(1)).toEqual(detail);
    });

    it('leaves ChatService state untouched on 400/403/409 failures', () => {
      for (const status of [400, 403, 409]) {
        let errored = false;
        service.join(1).subscribe({ error: () => (errored = true) });
        httpMock
          .expectOne('/api/chat/conversations/1/join')
          .flush(null, { status, statusText: 'Error' });
        expect(errored).toBe(true);
        expect(chatService.details().has(1)).toBe(false);
      }
    });
  });

  describe('requestToJoin', () => {
    it('POSTs to join-requests and returns the created pending request without opening the conversation', () => {
      let result: unknown;
      service.requestToJoin(2).subscribe((r) => (result = r));
      const req = httpMock.expectOne('/api/chat/conversations/2/join-requests');
      expect(req.request.method).toBe('POST');
      req.flush({
        id: 9,
        conversationId: 2,
        requesterUserId: 1,
        requesterNickname: 'Alice',
        status: 'PENDING',
        decidedAt: null,
      });
      expect(result).toEqual(expect.objectContaining({ status: 'PENDING' }));
      expect(chatService.details().has(2)).toBe(false);
    });

    it('leaves no partial state behind on 400/403/409', () => {
      let errored = false;
      service.requestToJoin(2).subscribe({ error: () => (errored = true) });
      httpMock
        .expectOne('/api/chat/conversations/2/join-requests')
        .flush(null, { status: 409, statusText: 'Conflict' });
      expect(errored).toBe(true);
      expect(chatService.details().has(2)).toBe(false);
    });
  });

  describe('pending join requests + approve/reject', () => {
    it('fetchPendingJoinRequests populates pendingJoinRequests() keyed by conversation id', () => {
      service.fetchPendingJoinRequests(1);
      const req = httpMock.expectOne(
        (r) =>
          r.url === '/api/chat/conversations/1/join-requests' &&
          r.params.get('status') === 'PENDING',
      );
      req.flush([
        {
          id: 1,
          conversationId: 1,
          requesterUserId: 5,
          requesterNickname: 'Carol',
          status: 'PENDING',
          decidedAt: null,
        },
      ]);
      expect(service.pendingJoinRequests().get(1)?.length).toBe(1);
    });

    it('approveJoinRequest removes the request and re-opens the conversation on success', () => {
      service.fetchPendingJoinRequests(1);
      httpMock
        .expectOne((r) => r.url === '/api/chat/conversations/1/join-requests')
        .flush([
          {
            id: 7,
            conversationId: 1,
            requesterUserId: 5,
            requesterNickname: 'Carol',
            status: 'PENDING',
            decidedAt: null,
          },
        ]);

      service.approveJoinRequest(1, 7).subscribe();
      httpMock.expectOne('/api/chat/conversations/1/join-requests/7/approve').flush({
        id: 7,
        conversationId: 1,
        requesterUserId: 5,
        requesterNickname: 'Carol',
        status: 'APPROVED',
        decidedAt: 'now',
      });

      expect(service.pendingJoinRequests().get(1)).toEqual([]);
      httpMock.expectOne('/api/chat/conversations/1').flush(detailFixture());
      httpMock
        .expectOne((r) => r.url === '/api/chat/conversations/1/messages')
        .flush({ messages: [], nextCursor: null });
    });

    it('REQ-30a: a 400 CHAT_INELIGIBLE_PARTICIPANT keeps the request pending, not removed', () => {
      service.fetchPendingJoinRequests(1);
      httpMock
        .expectOne((r) => r.url === '/api/chat/conversations/1/join-requests')
        .flush([
          {
            id: 7,
            conversationId: 1,
            requesterUserId: 5,
            requesterNickname: 'Carol',
            status: 'PENDING',
            decidedAt: null,
          },
        ]);

      let errored = false;
      service.approveJoinRequest(1, 7).subscribe({ error: () => (errored = true) });
      httpMock
        .expectOne('/api/chat/conversations/1/join-requests/7/approve')
        .flush({ code: 'CHAT_INELIGIBLE_PARTICIPANT' }, { status: 400, statusText: 'Bad Request' });

      expect(errored).toBe(true);
      expect(service.pendingJoinRequests().get(1)?.length).toBe(1);
    });

    it('a 403/409 on approve also leaves the pending list untouched', () => {
      service.fetchPendingJoinRequests(1);
      httpMock
        .expectOne((r) => r.url === '/api/chat/conversations/1/join-requests')
        .flush([
          {
            id: 7,
            conversationId: 1,
            requesterUserId: 5,
            requesterNickname: 'Carol',
            status: 'PENDING',
            decidedAt: null,
          },
        ]);

      let errored = false;
      service.approveJoinRequest(1, 7).subscribe({ error: () => (errored = true) });
      httpMock
        .expectOne('/api/chat/conversations/1/join-requests/7/approve')
        .flush(null, { status: 409, statusText: 'Conflict' });

      expect(errored).toBe(true);
      expect(service.pendingJoinRequests().get(1)?.length).toBe(1);
    });

    it('rejectJoinRequest removes the request on success, leaves it on 403/409 failure', () => {
      service.fetchPendingJoinRequests(1);
      httpMock
        .expectOne((r) => r.url === '/api/chat/conversations/1/join-requests')
        .flush([
          {
            id: 7,
            conversationId: 1,
            requesterUserId: 5,
            requesterNickname: 'Carol',
            status: 'PENDING',
            decidedAt: null,
          },
          {
            id: 8,
            conversationId: 1,
            requesterUserId: 6,
            requesterNickname: 'Dan',
            status: 'PENDING',
            decidedAt: null,
          },
        ]);

      service.rejectJoinRequest(1, 7).subscribe();
      httpMock.expectOne('/api/chat/conversations/1/join-requests/7/reject').flush({
        id: 7,
        conversationId: 1,
        requesterUserId: 5,
        requesterNickname: 'Carol',
        status: 'REJECTED',
        decidedAt: 'now',
      });
      expect(
        service
          .pendingJoinRequests()
          .get(1)
          ?.map((r) => r.id),
      ).toEqual([8]);

      let errored = false;
      service.rejectJoinRequest(1, 8).subscribe({ error: () => (errored = true) });
      httpMock
        .expectOne('/api/chat/conversations/1/join-requests/8/reject')
        .flush(null, { status: 403, statusText: 'Forbidden' });
      expect(errored).toBe(true);
      expect(
        service
          .pendingJoinRequests()
          .get(1)
          ?.map((r) => r.id),
      ).toEqual([8]);
    });
  });

  describe('promote', () => {
    it('POSTs to admins/{userId} and patches details on 200', () => {
      service.promote(1, 2).subscribe();
      const req = httpMock.expectOne('/api/chat/conversations/1/admins/2');
      expect(req.request.method).toBe('POST');
      const detail = detailFixture({ adminUserIds: [1, 2] });
      req.flush(detail);
      expect(chatService.details().get(1)?.adminUserIds).toEqual([1, 2]);
    });

    it('leaves state untouched on 400/403/404', () => {
      let errored = false;
      service.promote(1, 2).subscribe({ error: () => (errored = true) });
      httpMock
        .expectOne('/api/chat/conversations/1/admins/2')
        .flush(null, { status: 400, statusText: 'Bad Request' });
      expect(errored).toBe(true);
      expect(chatService.details().has(1)).toBe(false);
    });
  });

  describe('removeParticipant', () => {
    it('DELETEs and patches details directly from the 200 body (no client-side recompute)', () => {
      service.removeParticipant(1, 2).subscribe();
      const req = httpMock.expectOne('/api/chat/conversations/1/participants/2');
      expect(req.request.method).toBe('DELETE');
      const detail = detailFixture({ participantUserIds: [1] });
      req.flush(detail);
      expect(chatService.details().get(1)).toEqual(detail);
    });

    it('leaves the cached detail untouched on 403/404/409', () => {
      chatService.patchDetail(1, detailFixture());
      let errored = false;
      service.removeParticipant(1, 2).subscribe({ error: () => (errored = true) });
      httpMock
        .expectOne('/api/chat/conversations/1/participants/2')
        .flush(null, { status: 409, statusText: 'Conflict' });
      expect(errored).toBe(true);
      expect(chatService.details().get(1)?.participantUserIds).toEqual([1, 2]);
    });
  });

  describe('leave', () => {
    it('POSTs with no body parsing and drops the conversation on success (204)', () => {
      chatService.patchDetail(5, detailFixture({ id: 5 }));
      service.leave(5).subscribe();
      const req = httpMock.expectOne('/api/chat/conversations/5/leave');
      expect(req.request.method).toBe('POST');
      req.flush(null, { status: 204, statusText: 'No Content' });
      expect(chatService.details().has(5)).toBe(false);
    });

    it('leaves _conversations untouched on 403 CHAT_ACCESS_DENIED', () => {
      let errored = false;
      service.leave(5).subscribe({ error: () => (errored = true) });
      httpMock
        .expectOne('/api/chat/conversations/5/leave')
        .flush(null, { status: 403, statusText: 'Forbidden' });
      expect(errored).toBe(true);
    });
  });

  describe('changeVisibility', () => {
    it('PUTs { visibility } and patches details on 200', () => {
      service.changeVisibility(1, 'REQUEST_TO_JOIN').subscribe();
      const req = httpMock.expectOne('/api/chat/conversations/1/visibility');
      expect(req.request.method).toBe('PUT');
      expect(req.request.body).toEqual({ visibility: 'REQUEST_TO_JOIN' });
      const detail = detailFixture({ visibility: 'REQUEST_TO_JOIN' });
      req.flush(detail);
      expect(chatService.details().get(1)?.visibility).toBe('REQUEST_TO_JOIN');
    });

    it('leaves cached state untouched on 400/403/409', () => {
      let errored = false;
      service.changeVisibility(1, 'PUBLIC').subscribe({ error: () => (errored = true) });
      httpMock
        .expectOne('/api/chat/conversations/1/visibility')
        .flush(null, { status: 400, statusText: 'Bad Request' });
      expect(errored).toBe(true);
      expect(chatService.details().has(1)).toBe(false);
    });
  });

  describe('rename', () => {
    it('PUTs { title, icon } and patches ChatService details on 200', () => {
      service.rename(1, 'Novo nome', 'ROCKET').subscribe();
      const req = httpMock.expectOne('/api/chat/conversations/1');
      expect(req.request.method).toBe('PUT');
      expect(req.request.body).toEqual({ title: 'Novo nome', icon: 'ROCKET' });
      const detail = detailFixture({ title: 'Novo nome', icon: 'ROCKET' });
      req.flush(detail);
      expect(chatService.details().get(1)?.title).toBe('Novo nome');
      expect(chatService.details().get(1)?.icon).toBe('ROCKET');
    });

    it('leaves _details untouched with an inline error on 400/403/404', () => {
      let errored400 = false;
      service.rename(1, '', undefined).subscribe({ error: () => (errored400 = true) });
      httpMock
        .expectOne('/api/chat/conversations/1')
        .flush(null, { status: 400, statusText: 'Bad Request' });
      expect(errored400).toBe(true);
      expect(chatService.details().has(1)).toBe(false);

      let errored403 = false;
      service.rename(1, 'Nome', undefined).subscribe({ error: () => (errored403 = true) });
      httpMock
        .expectOne('/api/chat/conversations/1')
        .flush(null, { status: 403, statusText: 'Forbidden' });
      expect(errored403).toBe(true);
      expect(chatService.details().has(1)).toBe(false);

      let errored404 = false;
      service.rename(1, 'Nome', undefined).subscribe({ error: () => (errored404 = true) });
      httpMock
        .expectOne('/api/chat/conversations/1')
        .flush(null, { status: 404, statusText: 'Not Found' });
      expect(errored404).toBe(true);
      expect(chatService.details().has(1)).toBe(false);
    });
  });

  describe('deleteGroup', () => {
    it('DELETEs and drops the conversation on 204', () => {
      chatService.patchDetail(9, detailFixture({ id: 9 }));
      service.deleteGroup(9).subscribe();
      const req = httpMock.expectOne('/api/chat/conversations/9');
      expect(req.request.method).toBe('DELETE');
      req.flush(null, { status: 204, statusText: 'No Content' });
      expect(chatService.details().has(9)).toBe(false);
    });

    it('leaves state untouched on 403/404/409', () => {
      chatService.patchDetail(9, detailFixture({ id: 9 }));
      let errored = false;
      service.deleteGroup(9).subscribe({ error: () => (errored = true) });
      httpMock
        .expectOne('/api/chat/conversations/9')
        .flush(null, { status: 409, statusText: 'Conflict' });
      expect(errored).toBe(true);
      expect(chatService.details().has(9)).toBe(true);
    });
  });

  describe('addParticipants', () => {
    it('POSTs { userIds } and patches details from result.conversation on all-accepted 200', () => {
      const result: ChatAddParticipantsResultDto = {
        conversation: detailFixture({ participantUserIds: [1, 2, 3] }),
        rejected: [],
      };
      let response: ChatAddParticipantsResultDto | undefined;
      service.addParticipants(1, [3]).subscribe((r) => (response = r));
      const req = httpMock.expectOne('/api/chat/conversations/1/participants');
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({ userIds: [3] });
      req.flush(result);
      expect(chatService.details().get(1)?.participantUserIds).toEqual([1, 2, 3]);
      expect(response?.rejected).toEqual([]);
    });

    it('a partial-success 200 (non-empty rejected[]) still patches details and returns rejected for inline display, not treated as an error', () => {
      const result: ChatAddParticipantsResultDto = {
        conversation: detailFixture({ participantUserIds: [1, 2, 4] }),
        rejected: [{ userId: 3, reason: 'INELIGIBLE' }],
      };
      let response: ChatAddParticipantsResultDto | undefined;
      let errored = false;
      service.addParticipants(1, [3, 4]).subscribe({
        next: (r) => (response = r),
        error: () => (errored = true),
      });
      httpMock.expectOne('/api/chat/conversations/1/participants').flush(result);
      expect(errored).toBe(false);
      expect(response?.rejected).toEqual([{ userId: 3, reason: 'INELIGIBLE' }]);
      expect(chatService.details().get(1)?.participantUserIds).toEqual([1, 2, 4]);
    });

    it('leaves details untouched on 400 (all rejected)/403/404/409', () => {
      let errored = false;
      service.addParticipants(1, [3]).subscribe({ error: () => (errored = true) });
      httpMock
        .expectOne('/api/chat/conversations/1/participants')
        .flush(null, { status: 400, statusText: 'Bad Request' });
      expect(errored).toBe(true);
      expect(chatService.details().has(1)).toBe(false);
    });
  });
});
