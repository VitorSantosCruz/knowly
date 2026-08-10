import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { ChatEntitySearchService } from './chat-entity-search.service';
import {
  ChatEntitySearchResponseDto,
  ChatPersonSearchResultDto,
  ChatGroupSearchResultDto,
  ChatRagConversationSearchResultDto,
} from './chat.model';

describe('ChatEntitySearchService', () => {
  let service: ChatEntitySearchService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ChatEntitySearchService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  const person = (
    overrides: Partial<ChatPersonSearchResultDto> = {},
  ): ChatPersonSearchResultDto => ({
    userId: 1,
    nickname: 'Ana',
    avatarUrl: null,
    ...overrides,
  });
  const group = (overrides: Partial<ChatGroupSearchResultDto> = {}): ChatGroupSearchResultDto => ({
    id: 1,
    title: 'Grupo A',
    isParticipant: true,
    visibility: 'PUBLIC',
    ...overrides,
  });
  const rag = (
    overrides: Partial<ChatRagConversationSearchResultDto> = {},
  ): ChatRagConversationSearchResultDto => ({
    id: 1,
    title: 'Base de artigos',
    ...overrides,
  });

  const fullResponse = (
    overrides: Partial<ChatEntitySearchResponseDto> = {},
  ): ChatEntitySearchResponseDto => ({
    people: { results: [person()], hasMore: false },
    groups: { results: [group()], hasMore: false },
    support: { channelId: 5 },
    rag: { results: [rag()], hasMore: false },
    ...overrides,
  });

  it('search(q) populates all four sections from one response and marks them all "ok"', () => {
    service.search('ana');
    const req = httpMock.expectOne((r) => r.url === '/api/chat/search');
    expect(req.request.params.get('q')).toBe('ana');
    req.flush(fullResponse());

    expect(service.people()).toEqual([person()]);
    expect(service.groups()).toEqual([group()]);
    expect(service.support()).toEqual({ channelId: 5 });
    expect(service.rag()).toEqual([rag()]);
    expect(service.peopleStatus()).toBe('ok');
    expect(service.groupsStatus()).toBe('ok');
    expect(service.supportStatus()).toBe('ok');
    expect(service.ragStatus()).toBe('ok');
  });

  it('a support: null response leaves _support null and supportStatus "ok", not "error"', () => {
    service.search('ana');
    httpMock.expectOne((r) => r.url === '/api/chat/search').flush(fullResponse({ support: null }));

    expect(service.support()).toBeNull();
    expect(service.supportStatus()).toBe('ok');
  });

  it('a network/5xx failure marks all four entity section statuses "error" simultaneously', () => {
    service.search('ana');
    httpMock
      .expectOne((r) => r.url === '/api/chat/search')
      .flush('boom', { status: 500, statusText: 'Server Error' });

    expect(service.peopleStatus()).toBe('error');
    expect(service.groupsStatus()).toBe('error');
    expect(service.supportStatus()).toBe('error');
    expect(service.ragStatus()).toBe('error');
  });

  it('recentPlaces() calls with blank q, populates only recentPlaces, leaving entity sections untouched', () => {
    service.search('ana');
    httpMock.expectOne((r) => r.url === '/api/chat/search').flush(fullResponse());

    service.recentPlaces();
    const req = httpMock.expectOne((r) => r.url === '/api/chat/search');
    expect(req.request.params.has('q')).toBe(false);
    req.flush({
      recentPlaces: [
        {
          conversationId: 1,
          kind: 'PEER_DIRECT',
          title: 'Ana',
          orderingTimestamp: '2026-08-09T00:00:00Z',
        },
      ],
    });

    expect(service.recentPlaces_()).toHaveLength(1);
    expect(service.recentPlacesStatus()).toBe('ok');
    expect(service.people()).toEqual([person()]);
    expect(service.peopleStatus()).toBe('ok');
  });

  it('expandSection("groups", q) sends type=groups&offset=<current length>&q=<q> and appends, updating only hasMore for that section', () => {
    service.search('ana');
    httpMock
      .expectOne((r) => r.url === '/api/chat/search')
      .flush(fullResponse({ groups: { results: [group({ id: 1 })], hasMore: true } }));

    service.expandSection('groups', 'ana');
    const req = httpMock.expectOne((r) => r.url === '/api/chat/search');
    expect(req.request.params.get('type')).toBe('groups');
    expect(req.request.params.get('offset')).toBe('1');
    expect(req.request.params.get('q')).toBe('ana');
    req.flush({ results: [group({ id: 2 })], hasMore: true });

    expect(service.groups()).toEqual([group({ id: 1 }), group({ id: 2 })]);
    expect(service.groupsHasMore()).toBe(true);
    expect(service.people()).toEqual([person()]);

    // second call — cumulative growth, not a replace
    service.expandSection('groups', 'ana');
    const req2 = httpMock.expectOne((r) => r.url === '/api/chat/search');
    expect(req2.request.params.get('offset')).toBe('2');
    req2.flush({ results: [group({ id: 3 })], hasMore: false });
    expect(service.groups()).toEqual([group({ id: 1 }), group({ id: 2 }), group({ id: 3 })]);
    expect(service.groupsHasMore()).toBe(false);
  });

  it('expandSection("people"/"rag", ...) behave identically — append-only, correct type/offset, only that section touched', () => {
    service.search('ana');
    httpMock
      .expectOne((r) => r.url === '/api/chat/search')
      .flush(fullResponse({ people: { results: [person({ userId: 1 })], hasMore: true } }));

    service.expandSection('people', 'ana');
    const req = httpMock.expectOne((r) => r.url === '/api/chat/search');
    expect(req.request.params.get('type')).toBe('people');
    expect(req.request.params.get('offset')).toBe('1');
    req.flush({ results: [person({ userId: 2 })], hasMore: false });

    expect(service.people()).toEqual([person({ userId: 1 }), person({ userId: 2 })]);
    expect(service.peopleHasMore()).toBe(false);
    expect(service.groups()).toEqual([group()]);
  });

  it('reset() returns every section to idle/empty', () => {
    service.search('ana');
    httpMock.expectOne((r) => r.url === '/api/chat/search').flush(fullResponse());

    service.reset();

    expect(service.people()).toEqual([]);
    expect(service.groups()).toEqual([]);
    expect(service.support()).toBeNull();
    expect(service.rag()).toEqual([]);
    expect(service.recentPlaces_()).toEqual([]);
    expect(service.peopleStatus()).toBe('idle');
    expect(service.groupsStatus()).toBe('idle');
    expect(service.supportStatus()).toBe('idle');
    expect(service.ragStatus()).toBe('idle');
    expect(service.recentPlacesStatus()).toBe('idle');
  });
});
