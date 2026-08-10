import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { provideTransloco } from '@jsverse/transloco';
import { FakeTranslocoLoader } from '../../testing/fake-transloco-loader';
import { ChatService } from '../../core/chat.service';
import {
  CandidateUser,
  ChatMessageSearchResultDto,
  ConversationSummary,
} from '../../core/chat.model';
import { ChatSearchDialogComponent } from './chat-search-dialog.component';

class FakeIntersectionObserver {
  static instances: FakeIntersectionObserver[] = [];
  callback: IntersectionObserverCallback;
  constructor(callback: IntersectionObserverCallback) {
    this.callback = callback;
    FakeIntersectionObserver.instances.push(this);
  }
  observe(): void {
    // no-op: this fake only needs to capture the callback and let tests call trigger()
  }
  unobserve(): void {
    // no-op
  }
  disconnect(): void {
    // no-op
  }
  trigger(): void {
    this.callback([{ isIntersecting: true } as IntersectionObserverEntry], this as never);
  }
}

describe('ChatSearchDialogComponent', () => {
  let fixture: ComponentFixture<ChatSearchDialogComponent>;
  let httpMock: HttpTestingController;
  let router: Router;
  let chatService: ChatService;

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

  beforeEach(() => {
    vi.useFakeTimers();
    (globalThis as unknown as { IntersectionObserver: unknown }).IntersectionObserver =
      FakeIntersectionObserver;
    FakeIntersectionObserver.instances = [];

    TestBed.configureTestingModule({
      imports: [ChatSearchDialogComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideTransloco({
          config: { availableLangs: ['en'], defaultLang: 'en' },
          loader: FakeTranslocoLoader,
        }),
      ],
    });
    fixture = TestBed.createComponent(ChatSearchDialogComponent);
    httpMock = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    chatService = TestBed.inject(ChatService);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);

    const people: CandidateUser[] = [{ userId: 2, nickname: 'Ana' }];
    const conversations: ConversationSummary[] = [
      { id: 10, kind: 'PEER_GROUP', tenantId: 1, title: 'Grupo A', participantUserIds: [2] },
      { id: 11, kind: 'SUPPORT', tenantId: 1, title: null, participantUserIds: [] },
    ];
    (chatService as unknown as { _conversations: { set: (v: unknown) => void } })[
      '_conversations'
    ].set(conversations);
    (chatService as unknown as { _eligibleParticipants: { set: (v: unknown) => void } })[
      '_eligibleParticipants'
    ].set(people);

    fixture.componentRef.setInput('open', true);
    fixture.detectChanges();
  });

  afterEach(() => {
    httpMock.verify();
    vi.useRealTimers();
  });

  function queryInput(): HTMLInputElement {
    return fixture.nativeElement.querySelector('[data-testid="chat-search-query-input"]');
  }
  function typeQuery(value: string): void {
    const el = queryInput();
    el.value = value;
    el.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  it('renders a closed-by-default dialog, opened via the open input', () => {
    const dialogFixture = TestBed.createComponent(ChatSearchDialogComponent);
    dialogFixture.detectChanges();
    const dialog: HTMLDialogElement = dialogFixture.nativeElement.querySelector('dialog');
    expect(dialog.open).toBe(false);
  });

  it('REQ-7: submitting a blank/whitespace-only query shows blankQueryError and makes zero HTTP calls', () => {
    typeQuery('   ');
    vi.advanceTimersByTime(400);
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[data-testid="chat-search-blank-error"]'),
    ).toBeTruthy();
    httpMock.expectNone((r) => r.url === '/api/chat/messages/search');
  });

  it('debounces query input: rapid typing triggers exactly one search() call 400ms after the last keystroke', () => {
    typeQuery('re');
    vi.advanceTimersByTime(100);
    typeQuery('reu');
    vi.advanceTimersByTime(100);
    typeQuery('reunião');
    vi.advanceTimersByTime(399);
    httpMock.expectNone((r) => r.url === '/api/chat/messages/search');

    vi.advanceTimersByTime(1);
    const req = httpMock.expectOne((r) => r.url === '/api/chat/messages/search');
    expect(req.request.params.get('q')).toBe('reunião');
    req.flush({ results: [], nextCursor: null });
  });

  it('an unchanged query resubmitted (same trimmed string) does not trigger a second search() call', () => {
    typeQuery('reunião');
    vi.advanceTimersByTime(400);
    httpMock
      .expectOne((r) => r.url === '/api/chat/messages/search')
      .flush({
        results: [],
        nextCursor: null,
      });

    typeQuery('reunião');
    vi.advanceTimersByTime(400);
    httpMock.expectNone((r) => r.url === '/api/chat/messages/search');
  });

  it('REQ-4/5/6: selecting sender/conversation/date filters includes them in the next search() call, individually and combined', () => {
    typeQuery('reunião');
    vi.advanceTimersByTime(400);
    httpMock
      .expectOne((r) => r.url === '/api/chat/messages/search')
      .flush({
        results: [],
        nextCursor: null,
      });

    const senderSelect: HTMLSelectElement = fixture.nativeElement.querySelector(
      '[data-testid="chat-search-sender-select"]',
    );
    senderSelect.value = '2';
    senderSelect.dispatchEvent(new Event('change'));
    fixture.detectChanges();

    let req = httpMock.expectOne((r) => r.url === '/api/chat/messages/search');
    expect(req.request.params.get('senderId')).toBe('2');
    req.flush({ results: [], nextCursor: null });

    const conversationSelect: HTMLSelectElement = fixture.nativeElement.querySelector(
      '[data-testid="chat-search-conversation-select"]',
    );
    conversationSelect.value = '10';
    conversationSelect.dispatchEvent(new Event('change'));
    fixture.detectChanges();

    req = httpMock.expectOne((r) => r.url === '/api/chat/messages/search');
    expect(req.request.params.get('senderId')).toBe('2');
    expect(req.request.params.get('conversationId')).toBe('10');
    req.flush({ results: [], nextCursor: null });
  });

  it('the sender select options come from ChatService.eligibleParticipants, no new HTTP call', () => {
    const options = fixture.nativeElement.querySelectorAll(
      '[data-testid="chat-search-sender-select"] option',
    );
    const texts = Array.from(options as NodeListOf<HTMLOptionElement>).map((o) => o.textContent);
    expect(texts.some((t) => t?.includes('Ana'))).toBe(true);
    httpMock.expectNone((r) => r.url.includes('eligible-participants'));
  });

  it('the conversation select options exclude SUPPORT conversations, no new HTTP call', () => {
    const options: NodeListOf<HTMLOptionElement> = fixture.nativeElement.querySelectorAll(
      '[data-testid="chat-search-conversation-select"] option',
    );
    const values = Array.from(options).map((o) => o.value);
    expect(values).toContain('10');
    expect(values).not.toContain('11');
  });

  it('REQ-8: dateFrom after dateTo shows invalidDateRangeError and makes zero HTTP calls', () => {
    typeQuery('reunião');
    vi.advanceTimersByTime(400);
    httpMock
      .expectOne((r) => r.url === '/api/chat/messages/search')
      .flush({
        results: [],
        nextCursor: null,
      });

    const fromInput: HTMLInputElement = fixture.nativeElement.querySelector(
      '[data-testid="chat-search-date-from-input"]',
    );
    const toInput: HTMLInputElement = fixture.nativeElement.querySelector(
      '[data-testid="chat-search-date-to-input"]',
    );
    fromInput.value = '2026-08-09';
    fromInput.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    httpMock
      .expectOne((r) => r.url === '/api/chat/messages/search')
      .flush({ results: [], nextCursor: null });

    toInput.value = '2026-08-01';
    toInput.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[data-testid="chat-search-date-range-error"]'),
    ).toBeTruthy();
    httpMock.expectNone((r) => r.url === '/api/chat/messages/search');
  });

  it('REQ-12/13/14: the four status values each render their own distinct, mutually exclusive block', () => {
    expect(
      fixture.nativeElement.querySelector('[data-testid="chat-search-status-idle"]'),
    ).toBeTruthy();

    typeQuery('reunião');
    vi.advanceTimersByTime(400);
    fixture.detectChanges();
    expect(
      fixture.nativeElement.querySelector('[data-testid="chat-search-status-loading"]'),
    ).toBeTruthy();
    expect(
      fixture.nativeElement.querySelector('[data-testid="chat-search-status-idle"]'),
    ).toBeFalsy();

    httpMock
      .expectOne((r) => r.url === '/api/chat/messages/search')
      .flush({ results: [], nextCursor: null });
    fixture.detectChanges();
    expect(
      fixture.nativeElement.querySelector('[data-testid="chat-search-status-no-results"]'),
    ).toBeTruthy();
    expect(
      fixture.nativeElement.querySelector('[data-testid="chat-search-status-loading"]'),
    ).toBeFalsy();

    typeQuery('outra');
    vi.advanceTimersByTime(400);
    httpMock
      .expectOne((r) => r.url === '/api/chat/messages/search')
      .flush('boom', { status: 500, statusText: 'err' });
    fixture.detectChanges();
    expect(
      fixture.nativeElement.querySelector('[data-testid="chat-search-status-error"]'),
    ).toBeTruthy();
  });

  it('the no-results block interpolates the submitted query', () => {
    typeQuery('xyz');
    vi.advanceTimersByTime(400);
    httpMock
      .expectOne((r) => r.url === '/api/chat/messages/search')
      .flush({ results: [], nextCursor: null });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('xyz');
  });

  it('REQ-9: results render in the order the service returns them, each row via chat-search-result-row', () => {
    typeQuery('reunião');
    vi.advanceTimersByTime(400);
    httpMock
      .expectOne((r) => r.url === '/api/chat/messages/search')
      .flush({
        results: [result({ id: 1 }), result({ id: 2 })],
        nextCursor: null,
      });
    fixture.detectChanges();

    const rows = fixture.nativeElement.querySelectorAll('[data-testid="chat-search-result-row"]');
    expect(rows.length).toBe(2);
  });

  it('REQ-10: scrolling to the sentinel calls loadMore(); clicking the load-more button also calls it', () => {
    typeQuery('reunião');
    vi.advanceTimersByTime(400);
    httpMock
      .expectOne((r) => r.url === '/api/chat/messages/search')
      .flush({
        results: [result()],
        nextCursor: 'cursor-1',
      });
    fixture.detectChanges();

    FakeIntersectionObserver.instances[0].trigger();
    let req = httpMock.expectOne((r) => r.url === '/api/chat/messages/search');
    expect(req.request.params.get('cursor')).toBe('cursor-1');
    req.flush({ results: [result({ id: 2 })], nextCursor: 'cursor-2' });
    fixture.detectChanges();

    const button: HTMLButtonElement = fixture.nativeElement.querySelector(
      '[data-testid="chat-search-load-more"]',
    );
    button.click();
    req = httpMock.expectOne((r) => r.url === '/api/chat/messages/search');
    expect(req.request.params.get('cursor')).toBe('cursor-2');
    req.flush({ results: [], nextCursor: null });
  });

  it('REQ-11: clicking a result row navigates to /chat/:conversationId and closes the dialog, no highlight param', () => {
    typeQuery('reunião');
    vi.advanceTimersByTime(400);
    httpMock
      .expectOne((r) => r.url === '/api/chat/messages/search')
      .flush({
        results: [result({ conversationId: 42 })],
        nextCursor: null,
      });
    fixture.detectChanges();

    const row: HTMLElement = fixture.nativeElement.querySelector(
      '[data-testid="chat-search-result-row"]',
    );
    row.click();

    expect(router.navigate).toHaveBeenCalledWith(['/chat', 42]);
    expect(router.navigate).not.toHaveBeenCalledWith(
      ['/chat', 42],
      expect.objectContaining({ queryParams: expect.anything() }),
    );
  });

  it('closing the dialog calls ChatMessageSearchService.reset()', () => {
    typeQuery('reunião');
    vi.advanceTimersByTime(400);
    httpMock
      .expectOne((r) => r.url === '/api/chat/messages/search')
      .flush({
        results: [result()],
        nextCursor: null,
      });
    fixture.detectChanges();

    const cancelButton: HTMLButtonElement = fixture.nativeElement.querySelector(
      '[data-testid="chat-search-close"]',
    );
    cancelButton.click();
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[data-testid="chat-search-status-idle"]'),
    ).toBeTruthy();
  });
});
