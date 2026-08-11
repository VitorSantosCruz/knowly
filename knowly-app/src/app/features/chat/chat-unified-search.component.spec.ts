import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { provideTransloco } from '@jsverse/transloco';
import { FakeTranslocoLoader } from '../../testing/fake-transloco-loader';
import { ChatEntitySearchService } from '../../core/chat-entity-search.service';
import { ChatMessageSearchService } from '../../core/chat-message-search.service';
import { ChatUnifiedSearchComponent } from './chat-unified-search.component';

describe('ChatUnifiedSearchComponent', () => {
  let fixture: ComponentFixture<ChatUnifiedSearchComponent>;
  let httpMock: HttpTestingController;
  let router: Router;
  let entitySearch: ChatEntitySearchService;
  let messageSearch: ChatMessageSearchService;

  beforeEach(() => {
    vi.useFakeTimers();
    TestBed.configureTestingModule({
      imports: [ChatUnifiedSearchComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideTransloco({
          config: { availableLangs: ['en'], defaultLang: 'en' },
          loader: FakeTranslocoLoader,
        }),
      ],
    });
    fixture = TestBed.createComponent(ChatUnifiedSearchComponent);
    httpMock = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    entitySearch = TestBed.inject(ChatEntitySearchService);
    messageSearch = TestBed.inject(ChatMessageSearchService);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
    fixture.detectChanges();
  });

  afterEach(() => {
    httpMock.verify();
    vi.useRealTimers();
  });

  function input(): HTMLInputElement {
    return fixture.nativeElement.querySelector('[data-testid="chat-unified-search-input"]');
  }
  function type(value: string): void {
    const el = input();
    el.value = value;
    el.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  const entityResponse = () => ({
    people: { results: [], hasMore: false },
    groups: { results: [], hasMore: false },
    support: null,
    rag: { results: [], hasMore: false },
  });

  it('renders closed by default; typing a non-blank query debounces 400ms then fires both services exactly once', () => {
    expect(
      fixture.nativeElement.querySelector('[data-testid="chat-unified-search-dropdown"]'),
    ).toBeFalsy();

    type('ana');
    vi.advanceTimersByTime(399);
    httpMock.expectNone((r) => r.url === '/api/chat/messages/search');
    httpMock.expectNone((r) => r.url === '/api/chat/search');

    vi.advanceTimersByTime(1);
    const msgReq = httpMock.expectOne((r) => r.url === '/api/chat/messages/search');
    const entityReq = httpMock.expectOne((r) => r.url === '/api/chat/search' && r.params.has('q'));
    msgReq.flush({ results: [], nextCursor: null });
    entityReq.flush(entityResponse());
  });

  it('an unchanged query resubmitted does not trigger a second search on either service', () => {
    type('ana');
    vi.advanceTimersByTime(400);
    httpMock
      .expectOne((r) => r.url === '/api/chat/messages/search')
      .flush({
        results: [],
        nextCursor: null,
      });
    httpMock.expectOne((r) => r.url === '/api/chat/search').flush(entityResponse());

    type('ana');
    vi.advanceTimersByTime(400);
    httpMock.expectNone((r) => r.url === '/api/chat/messages/search');
    httpMock.expectNone((r) => r.url === '/api/chat/search');
  });

  it('regression: searching, closing (Escape), then searching the SAME term again in the same page load still fires both services — not silently swallowed', () => {
    // Reported live as "search twice and the second one doesn't go": distinctUntilChanged() used
    // to guard the debounced query pipe, but its "last value" state lived inside the RxJS operator
    // for this component's whole lifetime (a singleton mounted once in the chat shell, never
    // destroyed/recreated between searches) — dismiss() reset queryInput/messageSearch/
    // entitySearch but had no way to reset that operator-internal state. So closing the dropdown
    // and reopening it to search the exact same term again produced no request at all (not an
    // empty result — no HTTP call in the first place), reproducible entirely within one SPA load,
    // no reload needed.
    type('ana');
    vi.advanceTimersByTime(400);
    httpMock
      .expectOne((r) => r.url === '/api/chat/messages/search')
      .flush({ results: [], nextCursor: null });
    httpMock.expectOne((r) => r.url === '/api/chat/search').flush(entityResponse());

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    fixture.detectChanges();
    expect(
      fixture.nativeElement.querySelector('[data-testid="chat-unified-search-dropdown"]'),
    ).toBeFalsy();

    type('ana');
    vi.advanceTimersByTime(400);
    httpMock
      .expectOne((r) => r.url === '/api/chat/messages/search')
      .flush({ results: [], nextCursor: null });
    httpMock.expectOne((r) => r.url === '/api/chat/search').flush(entityResponse());
  });

  it('REQ-19/20: opening the bar (focus, blank query) calls recentPlaces() and neither search()', () => {
    input().dispatchEvent(new Event('focus'));
    fixture.detectChanges();

    const req = httpMock.expectOne((r) => r.url === '/api/chat/search');
    expect(req.request.params.has('q')).toBe(false);
    req.flush({ recentPlaces: [] });
    httpMock.expectNone((r) => r.url === '/api/chat/messages/search');
  });

  it('typing then clearing back to blank calls recentPlaces(), replacing results entirely', () => {
    type('ana');
    vi.advanceTimersByTime(400);
    httpMock
      .expectOne((r) => r.url === '/api/chat/messages/search')
      .flush({
        results: [
          {
            id: 1,
            conversationId: 5,
            conversationTitle: 'G',
            senderUserId: 1,
            senderNickname: 'A',
            content: 'oi',
            createdAt: '2026-08-09T00:00:00Z',
          },
        ],
        nextCursor: null,
      });
    httpMock
      .expectOne((r) => r.url === '/api/chat/search' && r.params.has('q'))
      .flush(entityResponse());
    fixture.detectChanges();
    expect(
      fixture.nativeElement.querySelector('[data-testid="chat-unified-search-status-loading"]'),
    ).toBeFalsy();

    type('');
    vi.advanceTimersByTime(400);
    const recentReq = httpMock.expectOne((r) => r.url === '/api/chat/search');
    expect(recentReq.request.params.has('q')).toBe(false);
    recentReq.flush({ recentPlaces: [] });
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[data-testid="chat-unified-search-recent-place"]'),
    ).toBeFalsy();
    // messages section is not rendered once the query goes blank — replaced, not merged
    expect(fixture.nativeElement.textContent).not.toContain('oi');
  });

  it('REQ-21: renders groups omitting zero-match groups', () => {
    type('ana');
    vi.advanceTimersByTime(400);
    httpMock
      .expectOne((r) => r.url === '/api/chat/messages/search')
      .flush({ results: [], nextCursor: null });
    httpMock
      .expectOne((r) => r.url === '/api/chat/search' && r.params.has('q'))
      .flush({
        people: { results: [{ userId: 1, nickname: 'Ana', avatarUrl: null }], hasMore: false },
        groups: { results: [], hasMore: false },
        support: null,
        rag: { results: [], hasMore: false },
      });
    fixture.detectChanges();

    const groups = fixture.nativeElement.querySelectorAll('section[role="group"]');
    // Only "People" group renders — Groups/Support/RAG/Messages all empty, omitted
    expect(groups.length).toBe(1);
    expect(fixture.nativeElement.textContent).toContain('Ana');
  });

  it("REQ-22: a group's see more only calls that group's own expand mechanism", () => {
    type('ana');
    vi.advanceTimersByTime(400);
    httpMock
      .expectOne((r) => r.url === '/api/chat/messages/search')
      .flush({ results: [], nextCursor: null });
    httpMock
      .expectOne((r) => r.url === '/api/chat/search' && r.params.has('q'))
      .flush({
        people: { results: [{ userId: 1, nickname: 'Ana', avatarUrl: null }], hasMore: true },
        groups: { results: [], hasMore: false },
        support: null,
        rag: { results: [], hasMore: false },
      });
    fixture.detectChanges();

    const expandSpy = vi.spyOn(entitySearch, 'expandSection');
    const loadMoreSpy = vi.spyOn(messageSearch, 'loadMore');
    fixture.nativeElement
      .querySelector('[data-testid="chat-unified-search-see-more-people"]')
      .click();
    expect(expandSpy).toHaveBeenCalledWith('people', 'ana');
    expect(loadMoreSpy).not.toHaveBeenCalled();

    httpMock.expectOne((r) => r.url === '/api/chat/search').flush({ results: [], hasMore: false });
  });

  it('REQ-27/28/29: partial entity failure with Messages succeeding resolves to "results", not "error"', () => {
    type('ana');
    vi.advanceTimersByTime(400);
    httpMock
      .expectOne((r) => r.url === '/api/chat/messages/search')
      .flush({
        results: [
          {
            id: 1,
            conversationId: 5,
            conversationTitle: 'G',
            senderUserId: 1,
            senderNickname: 'A',
            content: 'oi',
            createdAt: '2026-08-09T00:00:00Z',
          },
        ],
        nextCursor: null,
      });
    httpMock
      .expectOne((r) => r.url === '/api/chat/search' && r.params.has('q'))
      .flush('boom', { status: 500, statusText: 'err' });
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[data-testid="chat-unified-search-status-error"]'),
    ).toBeFalsy();
    expect(fixture.nativeElement.textContent).toContain('oi');
  });

  it('a total failure on both domains renders the error state', () => {
    type('ana');
    vi.advanceTimersByTime(400);
    httpMock
      .expectOne((r) => r.url === '/api/chat/messages/search')
      .flush('boom', { status: 500, statusText: 'err' });
    httpMock
      .expectOne((r) => r.url === '/api/chat/search' && r.params.has('q'))
      .flush('boom', { status: 500, statusText: 'err' });
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[data-testid="chat-unified-search-status-error"]'),
    ).toBeTruthy();
  });

  it('REQ-23/24/26: clicking a message result navigates and resets both services', () => {
    type('ana');
    vi.advanceTimersByTime(400);
    httpMock
      .expectOne((r) => r.url === '/api/chat/messages/search')
      .flush({
        results: [
          {
            id: 1,
            conversationId: 42,
            conversationTitle: 'G',
            senderUserId: 1,
            senderNickname: 'A',
            content: 'oi',
            createdAt: '2026-08-09T00:00:00Z',
          },
        ],
        nextCursor: null,
      });
    httpMock
      .expectOne((r) => r.url === '/api/chat/search' && r.params.has('q'))
      .flush(entityResponse());
    fixture.detectChanges();

    fixture.nativeElement.querySelector('[data-testid="chat-search-result-row"]').click();
    fixture.detectChanges();

    expect(router.navigate).toHaveBeenCalledWith(['/chat', 42], {
      state: { jumpToMessageId: 1, jumpToQuery: 'ana' },
    });
    expect(messageSearch.status()).toBe('idle');
    expect(entitySearch.peopleStatus()).toBe('idle');
    expect(
      fixture.nativeElement.querySelector('[data-testid="chat-unified-search-dropdown"]'),
    ).toBeFalsy();
  });

  it('REQ-31: Escape resets both services; reopening re-fetches recentPlaces, not stale results', () => {
    type('ana');
    vi.advanceTimersByTime(400);
    httpMock
      .expectOne((r) => r.url === '/api/chat/messages/search')
      .flush({ results: [], nextCursor: null });
    httpMock
      .expectOne((r) => r.url === '/api/chat/search' && r.params.has('q'))
      .flush(entityResponse());
    fixture.detectChanges();

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    fixture.detectChanges();
    expect(
      fixture.nativeElement.querySelector('[data-testid="chat-unified-search-dropdown"]'),
    ).toBeFalsy();

    input().dispatchEvent(new Event('focus'));
    fixture.detectChanges();
    const req = httpMock.expectOne((r) => r.url === '/api/chat/search');
    expect(req.request.params.has('q')).toBe(false);
    req.flush({ recentPlaces: [] });
  });
});
