import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { provideTransloco } from '@jsverse/transloco';
import { Subject } from 'rxjs';
import { ConversationsPageComponent } from './conversations-page.component';
import { ChatStreamEvent, ConversationService } from '../../core/conversation.service';
import { FakeTranslocoLoader } from '../../testing/fake-transloco-loader';

describe('ConversationsPageComponent', () => {
  let fixture: ComponentFixture<ConversationsPageComponent>;
  let httpMock: HttpTestingController;
  let conversationService: ConversationService;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ConversationsPageComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        provideTransloco({
          config: { availableLangs: ['en', 'pt-BR'], defaultLang: 'en' },
          loader: FakeTranslocoLoader,
        }),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ConversationsPageComponent);
    httpMock = TestBed.inject(HttpTestingController);
    conversationService = TestBed.inject(ConversationService);
  });

  afterEach(() => {
    httpMock.verify();
  });

  function flushActiveTenantAndList(conversations: Array<{ id: number; title: string | null }>) {
    httpMock
      .expectOne('/api/tenants/memberships')
      .flush([{ tenantId: 7, tenantName: 'Acme', role: 'ADMIN', active: true }]);
    fixture.detectChanges();
    httpMock.expectOne('/api/tenants/7/conversations').flush(conversations);
    fixture.detectChanges();
  }

  it('renders the conversation list on load', () => {
    fixture.detectChanges();
    flushActiveTenantAndList([{ id: 1, title: 'First chat' }]);

    expect(fixture.nativeElement.textContent).toContain('First chat');
  });

  it('starting a new conversation creates it and makes it active', () => {
    fixture.detectChanges();
    flushActiveTenantAndList([]);

    const newButton: HTMLButtonElement = fixture.nativeElement.querySelector(
      '[data-testid="new-conversation"]',
    );
    newButton.click();

    httpMock.expectOne('/api/tenants/7/conversations').flush({ id: 9, title: null });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="message-input"]')).toBeTruthy();
  });

  it('selecting a past conversation loads and shows its messages', () => {
    fixture.detectChanges();
    flushActiveTenantAndList([{ id: 1, title: 'First chat' }]);

    const conversationButton: HTMLButtonElement = fixture.nativeElement.querySelector(
      '[data-testid="select-conversation-1"]',
    );
    conversationButton.click();

    httpMock.expectOne('/api/tenants/7/conversations/1').flush({
      id: 1,
      title: 'First chat',
      messages: [
        { id: 1, role: 'USER', content: 'Hi' },
        { id: 2, role: 'ASSISTANT', content: 'Hello!' },
      ],
    });
    fixture.detectChanges();

    const userMessage = fixture.nativeElement.querySelector('[data-testid="message-role-USER"]');
    const assistantMessage = fixture.nativeElement.querySelector(
      '[data-testid="message-role-ASSISTANT"]',
    );
    expect(userMessage?.textContent).toContain('Hi');
    expect(assistantMessage?.textContent).toContain('Hello!');
  });

  it('shows a permission-denied state when the conversation list is forbidden', () => {
    fixture.detectChanges();
    httpMock
      .expectOne('/api/tenants/memberships')
      .flush([{ tenantId: 7, tenantName: 'Acme', role: 'ADMIN', active: true }]);
    fixture.detectChanges();
    httpMock
      .expectOne('/api/tenants/7/conversations')
      .flush({ code: 'PERMISSION_DENIED' }, { status: 403, statusText: 'Forbidden' });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="no-access-state"]')).toBeTruthy();
  });

  describe('sending a message', () => {
    let streamSubject: Subject<ChatStreamEvent>;

    beforeEach(() => {
      fixture.detectChanges();
      flushActiveTenantAndList([{ id: 1, title: 'First chat' }]);

      const conversationButton: HTMLButtonElement = fixture.nativeElement.querySelector(
        '[data-testid="select-conversation-1"]',
      );
      conversationButton.click();
      httpMock.expectOne('/api/tenants/7/conversations/1').flush({
        id: 1,
        title: 'First chat',
        messages: [],
      });
      fixture.detectChanges();

      streamSubject = new Subject<ChatStreamEvent>();
      vi.spyOn(conversationService, 'sendMessage').mockReturnValue(streamSubject.asObservable());
    });

    function typeAndSubmit(content: string) {
      const input: HTMLInputElement = fixture.nativeElement.querySelector(
        '[data-testid="message-input"]',
      );
      input.value = content;
      input.dispatchEvent(new Event('input'));
      const form: HTMLFormElement = fixture.nativeElement.querySelector(
        '[data-testid="send-message-form"]',
      );
      form.dispatchEvent(new Event('submit'));
      fixture.detectChanges();
    }

    it('shows the user message immediately then streams the assistant reply', () => {
      typeAndSubmit('What is X?');

      expect(fixture.nativeElement.textContent).toContain('What is X?');

      streamSubject.next({ type: 'message', data: 'Hello' });
      streamSubject.next({ type: 'message', data: ', world!' });
      streamSubject.next({ type: 'done' });
      fixture.detectChanges();

      const assistantMessage = fixture.nativeElement.querySelector(
        '[data-testid="message-role-ASSISTANT"]',
      );
      expect(assistantMessage?.textContent).toContain('Hello, world!');
    });

    it('disables the input while streaming and re-enables it on completion', () => {
      typeAndSubmit('question');

      const input: HTMLInputElement = fixture.nativeElement.querySelector(
        '[data-testid="message-input"]',
      );
      expect(input.disabled).toBe(true);

      streamSubject.next({ type: 'done' });
      fixture.detectChanges();

      expect(input.disabled).toBe(false);
    });

    it('shows an inline error when the stream ends with an error event', () => {
      typeAndSubmit('question');

      streamSubject.next({ type: 'error', data: 'The assistant is unavailable.' });
      fixture.detectChanges();

      expect(
        fixture.nativeElement.querySelector('[data-testid="message-stream-error"]'),
      ).toBeTruthy();
      const input: HTMLInputElement = fixture.nativeElement.querySelector(
        '[data-testid="message-input"]',
      );
      expect(input.disabled).toBe(false);
    });
  });
});
