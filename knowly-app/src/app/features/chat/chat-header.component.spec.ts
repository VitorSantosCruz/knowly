import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTransloco } from '@jsverse/transloco';
import { FakeTranslocoLoader } from '../../testing/fake-transloco-loader';
import { ConversationDetail } from '../../core/chat.model';
import { ChatHeaderComponent } from './chat-header.component';

describe('ChatHeaderComponent', () => {
  let fixture: ComponentFixture<ChatHeaderComponent>;

  const detail: ConversationDetail = {
    id: 1,
    kind: 'PEER_GROUP',
    tenantId: 5,
    title: null,
    participantUserIds: [1, 2],
    participantNicknames: { 1: 'Alice', 2: 'Bob' },
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [ChatHeaderComponent],
      providers: [
        provideTransloco({
          config: { availableLangs: ['en'], defaultLang: 'en' },
          loader: FakeTranslocoLoader,
        }),
      ],
    });
    fixture = TestBed.createComponent(ChatHeaderComponent);
    fixture.componentRef.setInput('detail', detail);
  });

  it('renders participant nicknames normally for PARTICIPANT, no banner', () => {
    fixture.componentRef.setInput('viewerRelation', 'PARTICIPANT');
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Alice');
    expect(
      fixture.nativeElement.querySelector('[data-testid="chat-header-looking-in-banner"]'),
    ).toBeNull();
  });

  it('renders a distinct oversight banner, non-"joined" copy, for LOOKING_IN', () => {
    fixture.componentRef.setInput('viewerRelation', 'LOOKING_IN');
    fixture.detectChanges();

    const banner = fixture.nativeElement.querySelector(
      '[data-testid="chat-header-looking-in-banner"]',
    );
    expect(banner).toBeTruthy();
    expect(banner.getAttribute('aria-label')).toBeTruthy();
    expect(banner.textContent).not.toMatch(/joined/i);
  });
});
