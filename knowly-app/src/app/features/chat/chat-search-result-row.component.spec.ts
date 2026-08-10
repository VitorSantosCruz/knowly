import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTransloco } from '@jsverse/transloco';
import { FakeTranslocoLoader } from '../../testing/fake-transloco-loader';
import { ChatSearchResultRowComponent } from './chat-search-result-row.component';
import { ChatMessageSearchResultDto } from '../../core/chat.model';

const RESULT: ChatMessageSearchResultDto = {
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

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ChatSearchResultRowComponent],
      providers: [
        provideTransloco({
          config: { availableLangs: ['en'], defaultLang: 'en' },
          loader: FakeTranslocoLoader,
        }),
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(ChatSearchResultRowComponent);
    fixture.componentRef.setInput('result', RESULT);
    fixture.detectChanges();
  });

  it('renders sender nickname, conversation title, and content snippet', () => {
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
    fixture.componentInstance.select.subscribe(spy);
    const row = fixture.nativeElement.querySelector('[data-testid="chat-search-result-row"]');
    row.click();
    expect(spy).toHaveBeenCalledWith(10);
  });

  it('emits its conversationId on Enter and Space keydown', () => {
    const spy = vi.fn();
    fixture.componentInstance.select.subscribe(spy);
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
});
