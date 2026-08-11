import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTransloco, TranslocoService } from '@jsverse/transloco';
import { firstValueFrom } from 'rxjs';
import { FakeTranslocoLoader } from '../../testing/fake-transloco-loader';
import {
  ChatSearchResultRowComponent,
  ChatSearchRowResult,
} from './chat-search-result-row.component';

const MESSAGE_RESULT: ChatSearchRowResult = {
  kind: 'message',
  id: 1,
  conversationId: 10,
  conversationTitle: 'Grupo A',
  senderUserId: 2,
  senderNickname: 'Ana',
  content: 'vamos falar sobre a reunião',
  createdAt: '2026-08-09T10:00:00.000Z',
};

describe('ChatSearchResultRowComponent', () => {
  let fixture: ComponentFixture<ChatSearchResultRowComponent>;

  async function createWith(result: ChatSearchRowResult): Promise<void> {
    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      imports: [ChatSearchResultRowComponent],
      providers: [
        provideTransloco({
          config: { availableLangs: ['en'], defaultLang: 'en' },
          loader: FakeTranslocoLoader,
        }),
      ],
    }).compileComponents();
    await firstValueFrom(TestBed.inject(TranslocoService).load('en'));
    fixture = TestBed.createComponent(ChatSearchResultRowComponent);
    fixture.componentRef.setInput('result', result);
    fixture.detectChanges();
  }

  beforeEach(async () => {
    await createWith(MESSAGE_RESULT);
  });

  it('renders sender nickname, conversation title, and content snippet (kind: message)', () => {
    const el: HTMLElement = fixture.nativeElement;
    expect(el.textContent).toContain('Ana');
    expect(el.textContent).toContain('Grupo A');
    expect(el.textContent).toContain('vamos falar sobre a reunião');
  });

  it('renders a formatted timestamp', () => {
    const el: HTMLElement = fixture.nativeElement;
    const time = el.querySelector('[data-testid="chat-search-result-timestamp"]');
    expect(time?.textContent?.trim().length).toBeGreaterThan(0);
  });

  it('emits its conversationId on click', () => {
    const spy = vi.fn();
    fixture.componentInstance.rowSelected.subscribe(spy);
    const row = fixture.nativeElement.querySelector('[data-testid="chat-search-result-row"]');
    row.click();
    expect(spy).toHaveBeenCalledWith(10);
  });

  it('emits its conversationId on Enter and Space keydown', () => {
    const spy = vi.fn();
    fixture.componentInstance.rowSelected.subscribe(spy);
    const row: HTMLElement = fixture.nativeElement.querySelector(
      '[data-testid="chat-search-result-row"]',
    );
    row.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }));
    row.dispatchEvent(new KeyboardEvent('keydown', { key: ' ' }));
    expect(spy).toHaveBeenCalledTimes(2);
    expect(spy).toHaveBeenCalledWith(10);
  });

  it('has tabindex 0 and role button for keyboard navigability', () => {
    const row: HTMLElement = fixture.nativeElement.querySelector(
      '[data-testid="chat-search-result-row"]',
    );
    expect(row.getAttribute('tabindex')).toBe('0');
    expect(row.getAttribute('role')).toBe('button');
  });

  it('has an interpolated aria-label composing sender + conversation + timestamp', () => {
    const row: HTMLElement = fixture.nativeElement.querySelector(
      '[data-testid="chat-search-result-row"]',
    );
    const label = row.getAttribute('aria-label') ?? '';
    expect(label).toContain('Ana');
    expect(label).toContain('Grupo A');
  });

  describe('REQ-32 (Amended 2026-08-10): matched-substring highlight', () => {
    it('wraps the matched substring of a message result in <mark> when `query` is set', async () => {
      await createWith(MESSAGE_RESULT);
      fixture.componentRef.setInput('query', 'reunião');
      fixture.detectChanges();
      const mark: HTMLElement | null = fixture.nativeElement.querySelector('mark');
      expect(mark?.textContent).toBe('reunião');
    });

    it('renders plain, unmarked text when `query` does not literally substring-match', async () => {
      await createWith(MESSAGE_RESULT);
      fixture.componentRef.setInput('query', 'orçamento');
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelector('mark')).toBeNull();
      expect(fixture.nativeElement.textContent).toContain('vamos falar sobre a reunião');
    });

    it('renders plain text with no `query` input set (default)', () => {
      expect(fixture.nativeElement.querySelector('mark')).toBeNull();
      expect(fixture.nativeElement.textContent).toContain('vamos falar sobre a reunião');
    });
  });

  describe('per-kind rendering (Amended 2026-08-10)', () => {
    it('kind: person — renders nickname, emits userId, distinct aria-label', async () => {
      await createWith({ kind: 'person', userId: 7, nickname: 'Beltrano', avatarUrl: null });
      expect(fixture.nativeElement.textContent).toContain('Beltrano');
      const spy = vi.fn();
      fixture.componentInstance.rowSelected.subscribe(spy);
      fixture.nativeElement.querySelector('[data-testid="chat-search-result-row"]').click();
      expect(spy).toHaveBeenCalledWith(7);
      const row: HTMLElement = fixture.nativeElement.querySelector(
        '[data-testid="chat-search-result-row"]',
      );
      expect(row.getAttribute('aria-label')).toContain('Beltrano');
    });

    it('kind: group — renders title, emits id', async () => {
      await createWith({
        kind: 'group',
        id: 3,
        title: 'Grupo X',
        isParticipant: true,
        visibility: 'PUBLIC',
      });
      expect(fixture.nativeElement.textContent).toContain('Grupo X');
      const spy = vi.fn();
      fixture.componentInstance.rowSelected.subscribe(spy);
      fixture.nativeElement.querySelector('[data-testid="chat-search-result-row"]').click();
      expect(spy).toHaveBeenCalledWith(3);
    });

    it('kind: support — renders label, emits channelId', async () => {
      await createWith({ kind: 'support', channelId: 9 });
      const spy = vi.fn();
      fixture.componentInstance.rowSelected.subscribe(spy);
      fixture.nativeElement.querySelector('[data-testid="chat-search-result-row"]').click();
      expect(spy).toHaveBeenCalledWith(9);
    });

    it('kind: rag — renders title, emits id', async () => {
      await createWith({ kind: 'rag', id: 5, title: 'Base X' });
      expect(fixture.nativeElement.textContent).toContain('Base X');
      const spy = vi.fn();
      fixture.componentInstance.rowSelected.subscribe(spy);
      fixture.nativeElement.querySelector('[data-testid="chat-search-result-row"]').click();
      expect(spy).toHaveBeenCalledWith(5);
    });
  });

  describe('RAG turn-content match rendering (Amended 2026-08-11, REQ-38 through REQ-43)', () => {
    it('renders a snippet block beneath the title when `matchedSnippet` is present', async () => {
      await createWith({
        kind: 'rag',
        id: 5,
        title: 'Base X',
        matchedSnippet: 'a resposta certa é sobre férias',
        matchedRole: 'ASSISTANT',
      });
      const el: HTMLElement = fixture.nativeElement;
      expect(el.textContent).toContain('Base X');
      expect(el.textContent).toContain('a resposta certa é sobre férias');
    });

    it('renders exactly as before (title only, no snippet, no role indicator) when `matchedSnippet` is absent/null', async () => {
      await createWith({ kind: 'rag', id: 5, title: 'Base X', matchedSnippet: null });
      const el: HTMLElement = fixture.nativeElement;
      expect(el.textContent).toContain('Base X');
      expect(el.querySelector('[data-testid="chat-search-result-rag-snippet"]')).toBeNull();
      expect(el.querySelector('[data-testid="chat-search-result-rag-role"]')).toBeNull();
    });

    it('highlights the current query inside the snippet when it literally, case-insensitively matches', async () => {
      await createWith({
        kind: 'rag',
        id: 5,
        title: 'Base X',
        matchedSnippet: 'a resposta certa é sobre férias',
        matchedRole: 'ASSISTANT',
      });
      fixture.componentRef.setInput('query', 'FÉRIAS');
      fixture.detectChanges();
      const mark: HTMLElement | null = fixture.nativeElement.querySelector('mark');
      expect(mark?.textContent?.toLowerCase()).toBe('férias');
    });

    it('renders the snippet unmarked when the query does not literally substring-match it', async () => {
      await createWith({
        kind: 'rag',
        id: 5,
        title: 'Base X',
        matchedSnippet: 'a resposta certa é sobre férias',
        matchedRole: 'ASSISTANT',
      });
      fixture.componentRef.setInput('query', 'orçamento');
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelector('mark')).toBeNull();
    });

    it('renders a distinct icon+text indicator for matchedRole === "USER"', async () => {
      await createWith({
        kind: 'rag',
        id: 5,
        title: 'Base X',
        matchedSnippet: 'quando são as férias?',
        matchedRole: 'USER',
      });
      const role: HTMLElement | null = fixture.nativeElement.querySelector(
        '[data-testid="chat-search-result-rag-role"]',
      );
      expect(role?.textContent).toContain('You asked');
      expect(role?.querySelector('svg')).not.toBeNull();
    });

    it('renders a distinct icon+text indicator for matchedRole === "ASSISTANT", differing from USER', async () => {
      await createWith({
        kind: 'rag',
        id: 5,
        title: 'Base X',
        matchedSnippet: 'as férias são em julho',
        matchedRole: 'ASSISTANT',
      });
      const role: HTMLElement | null = fixture.nativeElement.querySelector(
        '[data-testid="chat-search-result-rag-role"]',
      );
      expect(role?.textContent).toContain('The assistant answered');
      expect(role?.textContent).not.toContain('You asked');
      expect(role?.querySelector('svg')).not.toBeNull();
    });

    it('renders the snippet but omits the role indicator when matchedRole is null/absent (REQ-41)', async () => {
      await createWith({
        kind: 'rag',
        id: 5,
        title: 'Base X',
        matchedSnippet: 'as férias são em julho',
        matchedRole: null,
      });
      const el: HTMLElement = fixture.nativeElement;
      expect(el.querySelector('[data-testid="chat-search-result-rag-snippet"]')).not.toBeNull();
      expect(el.querySelector('[data-testid="chat-search-result-rag-role"]')).toBeNull();
    });

    it('interpolates the role-label text into the aria-label when matchedRole is present (REQ-42)', async () => {
      await createWith({
        kind: 'rag',
        id: 5,
        title: 'Base X',
        matchedSnippet: 'quando são as férias?',
        matchedRole: 'USER',
      });
      const row: HTMLElement = fixture.nativeElement.querySelector(
        '[data-testid="chat-search-result-row"]',
      );
      expect(row.getAttribute('aria-label')).toContain('You asked');
    });
  });
});
