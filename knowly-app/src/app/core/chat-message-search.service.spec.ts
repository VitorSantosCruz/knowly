import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { ChatMessageSearchService } from './chat-message-search.service';
import { ChatMessageSearchResultDto } from './chat.model';

describe('ChatMessageSearchService', () => {
  let service: ChatMessageSearchService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ChatMessageSearchService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  const result = (
    overrides: Partial<ChatMessageSearchResultDto> = {},
  ): ChatMessageSearchResultDto => ({
    id: 1,
    conversationId: 10,
    conversationTitle: 'Grupo A',
    senderUserId: 2,
    senderNickname: 'Ana',
    content: 'reunião amanhã',
    createdAt: '2026-08-09T10:00:00Z',
    ...overrides,
  });

  it('search({ q }) calls GET /api/chat/messages/search with only q, no other params, and sets status loading first', () => {
    service.search({ q: 'reunião' });

    expect(service.status()).toBe('loading');

    const req = httpMock.expectOne(
      (r) => r.url === '/api/chat/messages/search' && r.method === 'GET',
    );
    expect(req.request.params.get('q')).toBe('reunião');
    expect(req.request.params.has('senderId')).toBe(false);
    expect(req.request.params.has('conversationId')).toBe(false);
    expect(req.request.params.has('dateFrom')).toBe(false);
    expect(req.request.params.has('dateTo')).toBe(false);
    expect(req.request.params.has('cursor')).toBe(false);

    req.flush({ results: [], nextCursor: null });
  });

  it('search() with every optional filter set includes all of them as query params', () => {
    service.search({
      q: 'reunião',
      senderId: 2,
      conversationId: 10,
      dateFrom: '2026-08-01T00:00:00Z',
      dateTo: '2026-08-09T00:00:00Z',
    });

    const req = httpMock.expectOne((r) => r.url === '/api/chat/messages/search');
    expect(req.request.params.get('senderId')).toBe('2');
    expect(req.request.params.get('conversationId')).toBe('10');
    expect(req.request.params.get('dateFrom')).toBe('2026-08-01T00:00:00Z');
    expect(req.request.params.get('dateTo')).toBe('2026-08-09T00:00:00Z');

    req.flush({ results: [], nextCursor: null });
  });

  it('search() success with non-empty results replaces _results, sets status "results", and stores nextCursor', () => {
    service.search({ q: 'reunião' });
    const results = [result()];
    httpMock
      .expectOne((r) => r.url === '/api/chat/messages/search')
      .flush({
        results,
        nextCursor: 'cursor-1',
      });

    expect(service.results()).toEqual(results);
    expect(service.status()).toBe('results');
    expect(service.hasMore()).toBe(true);
  });

  it('search() success with results: [] sets status "no-results" and stores lastQuery', () => {
    service.search({ q: 'xyz' });
    httpMock
      .expectOne((r) => r.url === '/api/chat/messages/search')
      .flush({ results: [], nextCursor: null });

    expect(service.status()).toBe('no-results');
    expect(service.lastQuery()).toBe('xyz');
    expect(service.hasMore()).toBe(false);
  });

  it('search() failure sets status "error" without clearing a prior non-empty _results', () => {
    service.search({ q: 'reunião' });
    httpMock
      .expectOne((r) => r.url === '/api/chat/messages/search')
      .flush({ results: [result()], nextCursor: null });
    expect(service.status()).toBe('results');

    service.search({ q: 'outra' });
    httpMock
      .expectOne((r) => r.url === '/api/chat/messages/search')
      .flush('boom', { status: 500, statusText: 'Server Error' });

    expect(service.status()).toBe('error');
    expect(service.results()).toEqual([result()]);
  });

  it('loadMore() sends the cursor param and the last filters, appending to _results and updating nextCursor', () => {
    service.search({ q: 'reunião', senderId: 2 });
    httpMock
      .expectOne((r) => r.url === '/api/chat/messages/search')
      .flush({ results: [result({ id: 1 })], nextCursor: 'cursor-1' });

    service.loadMore();
    const req = httpMock.expectOne((r) => r.url === '/api/chat/messages/search');
    expect(req.request.params.get('cursor')).toBe('cursor-1');
    expect(req.request.params.get('q')).toBe('reunião');
    expect(req.request.params.get('senderId')).toBe('2');
    req.flush({ results: [result({ id: 2 })], nextCursor: null });

    expect(service.results()).toEqual([result({ id: 1 }), result({ id: 2 })]);
    expect(service.hasMore()).toBe(false);
  });

  it('loadMore() no-ops when nextCursor is null, and when status is already loading', () => {
    // never searched — nextCursor null, status idle
    service.loadMore();
    httpMock.expectNone((r) => r.url === '/api/chat/messages/search');

    service.search({ q: 'reunião' });
    // status is 'loading' right now — a second loadMore() call must no-op too
    service.loadMore();
    httpMock
      .expectOne((r) => r.url === '/api/chat/messages/search')
      .flush({
        results: [],
        nextCursor: null,
      });
  });

  it('reset() returns to idle with empty results, null cursor, and empty lastQuery', () => {
    service.search({ q: 'reunião' });
    httpMock
      .expectOne((r) => r.url === '/api/chat/messages/search')
      .flush({ results: [result()], nextCursor: 'cursor-1' });

    service.reset();

    expect(service.status()).toBe('idle');
    expect(service.results()).toEqual([]);
    expect(service.hasMore()).toBe(false);
    expect(service.lastQuery()).toBe('');
  });
});
