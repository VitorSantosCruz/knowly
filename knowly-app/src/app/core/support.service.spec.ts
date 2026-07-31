import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { SupportService } from './support.service';

describe('SupportService', () => {
  let service: SupportService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(SupportService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('fetchMyChannel populates myChannel()', () => {
    service.fetchMyChannel(1, 9);
    httpMock.expectOne('/api/tenants/1/support/members/9/channel').flush({
      id: 1,
      kind: 'SUPPORT',
      tenantId: 1,
      title: null,
      participantUserIds: [9],
      participantNicknames: { 9: 'Nick' },
    });

    expect(service.myChannel()?.participantNicknames[9]).toBe('Nick');
    expect(service.myChannelNotFound()).toBe(false);
  });

  it('fetchMyChannel sets myChannelNotFound on 404 (never opened a ticket)', () => {
    service.fetchMyChannel(1, 9);
    httpMock
      .expectOne('/api/tenants/1/support/members/9/channel')
      .flush('nf', { status: 404, statusText: 'Not Found' });

    expect(service.myChannelNotFound()).toBe(true);
    expect(service.myChannel()).toBeNull();
  });

  it('openTicket posts and sets myOpenTicket on success', () => {
    let result: unknown;
    service.openTicket(1).subscribe((t) => (result = t));

    const req = httpMock.expectOne('/api/tenants/1/support/tickets');
    expect(req.request.method).toBe('POST');
    req.flush({
      id: 5,
      supportChannelId: 1,
      status: 'OPEN',
      assignedStaffUserId: null,
      openedAt: 'now',
      closedAt: null,
    });

    expect(result).toBeTruthy();
    expect(service.myOpenTicket()?.id).toBe(5);
  });

  it('openTicket surfaces a 409 without retry-looping', () => {
    let error: unknown;
    service.openTicket(1).subscribe({ error: (e) => (error = e) });
    httpMock
      .expectOne('/api/tenants/1/support/tickets')
      .flush('conflict', { status: 409, statusText: 'Conflict' });

    expect(error).toBeTruthy();
  });

  it('fetchInbox merges results across tenants without duplicate ticket ids', () => {
    service.fetchInbox(1);
    httpMock.expectOne('/api/tenants/1/support/tickets/unclaimed').flush([
      {
        id: 1,
        supportChannelId: 1,
        status: 'OPEN',
        assignedStaffUserId: null,
        openedAt: 'now',
        closedAt: null,
      },
    ]);

    service.fetchInbox(2);
    httpMock.expectOne('/api/tenants/2/support/tickets/unclaimed').flush([
      {
        id: 1,
        supportChannelId: 1,
        status: 'OPEN',
        assignedStaffUserId: null,
        openedAt: 'now',
        closedAt: null,
      },
      {
        id: 2,
        supportChannelId: 2,
        status: 'OPEN',
        assignedStaffUserId: null,
        openedAt: 'now',
        closedAt: null,
      },
    ]);

    expect(
      service
        .inboxTickets()
        .map((t) => t.id)
        .sort(),
    ).toEqual([1, 2]);
  });

  it('claim patches the ticket in place and removes it from inboxTickets()', () => {
    service.fetchInbox(1);
    httpMock.expectOne('/api/tenants/1/support/tickets/unclaimed').flush([
      {
        id: 1,
        supportChannelId: 1,
        status: 'OPEN',
        assignedStaffUserId: null,
        openedAt: 'now',
        closedAt: null,
      },
    ]);

    service.claim(1, 1).subscribe();
    httpMock.expectOne('/api/tenants/1/support/tickets/1/claim').flush({
      id: 1,
      supportChannelId: 1,
      status: 'ASSIGNED',
      assignedStaffUserId: 42,
      openedAt: 'now',
      closedAt: null,
    });

    expect(service.inboxTickets().length).toBe(0);
  });

  it('transfer patches assignedStaffUserId in place', () => {
    service.claim(1, 1).subscribe();
    httpMock.expectOne('/api/tenants/1/support/tickets/1/claim').flush({
      id: 1,
      supportChannelId: 1,
      status: 'ASSIGNED',
      assignedStaffUserId: 42,
      openedAt: 'now',
      closedAt: null,
    });
    service['_myOpenTicket'].set({
      id: 1,
      supportChannelId: 1,
      status: 'ASSIGNED',
      assignedStaffUserId: 42,
      openedAt: 'now',
      closedAt: null,
    });

    service.transfer(1, 1, 99).subscribe();
    httpMock.expectOne('/api/tenants/1/support/tickets/1/transfer').flush({
      id: 1,
      supportChannelId: 1,
      status: 'ASSIGNED',
      assignedStaffUserId: 99,
      openedAt: 'now',
      closedAt: null,
    });

    expect(service.myOpenTicket()?.assignedStaffUserId).toBe(99);
  });

  it('close patches status/closedAt in place', () => {
    service['_myOpenTicket'].set({
      id: 1,
      supportChannelId: 1,
      status: 'ASSIGNED',
      assignedStaffUserId: 42,
      openedAt: 'now',
      closedAt: null,
    });

    service.close(1, 1).subscribe();
    httpMock.expectOne('/api/tenants/1/support/tickets/1/close').flush({
      id: 1,
      supportChannelId: 1,
      status: 'CLOSED',
      assignedStaffUserId: 42,
      openedAt: 'now',
      closedAt: 'later',
    });

    expect(service.myOpenTicket()).toBeNull();
  });

  it('openChannel loads the first message page, load-older prepends, poll-after appends, without duplicating', () => {
    service.openChannel(1, 9);
    httpMock
      .expectOne(
        (r) =>
          r.url === '/api/tenants/1/support/members/9/channel/messages' &&
          r.params.get('size') === '30',
      )
      .flush({
        messages: [
          { id: 10, senderUserId: 9, senderNickname: 'Bob', content: 'hi', createdAt: 'now' },
        ],
        nextCursor: 'c9',
      });

    expect(service.entryOf(1, 9).messages.length).toBe(1);

    service.loadOlderMessages(1, 9);
    httpMock
      .expectOne(
        (r) =>
          r.url === '/api/tenants/1/support/members/9/channel/messages' &&
          r.params.get('before') === 'c9',
      )
      .flush({
        messages: [
          { id: 5, senderUserId: 9, senderNickname: 'Bob', content: 'older', createdAt: 'earlier' },
        ],
        nextCursor: null,
      });

    expect(service.entryOf(1, 9).messages.map((m) => m.id)).toEqual([5, 10]);

    service.pollNewMessages(1, 9);
    httpMock
      .expectOne(
        (r) =>
          r.url === '/api/tenants/1/support/members/9/channel/messages' &&
          r.params.get('after') === '10',
      )
      .flush({
        messages: [
          { id: 11, senderUserId: 9, senderNickname: 'Bob', content: 'new', createdAt: 'now2' },
        ],
        nextCursor: '11',
      });

    expect(service.entryOf(1, 9).messages.map((m) => m.id)).toEqual([5, 10, 11]);
  });

  it('sendMessage optimistically appends with pending/failed flag identical to ChatService', () => {
    service.sendMessage(1, 9, 'hello', 'local-1').subscribe();
    expect(service.entryOf(1, 9).messages.at(-1)?.sendState).toBe('pending');

    httpMock.expectOne('/api/tenants/1/support/members/9/channel/messages').flush({
      id: 30,
      senderUserId: 1,
      senderNickname: 'Staff',
      content: 'hello',
      createdAt: 'now',
    });

    expect(service.entryOf(1, 9).messages.at(-1)?.sendState).toBeUndefined();
  });
});
