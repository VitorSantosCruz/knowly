import { provideRouter } from '@angular/router';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTransloco } from '@jsverse/transloco';
import { FakeTranslocoLoader } from '../../testing/fake-transloco-loader';
import { ConversationSummary } from '../../core/chat.model';
import { ConversationListItemComponent } from './conversation-list-item.component';

describe('ConversationListItemComponent', () => {
  let fixture: ComponentFixture<ConversationListItemComponent>;

  const conversation: ConversationSummary = {
    id: 1,
    kind: 'PEER_GROUP',
    tenantId: 5,
    title: 'Team chat',
    participantUserIds: [1, 2],
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [ConversationListItemComponent],
      providers: [
        provideRouter([]),
        provideTransloco({
          config: { availableLangs: ['en'], defaultLang: 'en' },
          loader: FakeTranslocoLoader,
        }),
      ],
    });
    fixture = TestBed.createComponent(ConversationListItemComponent);
    fixture.componentRef.setInput('conversation', conversation);
  });

  it('renders normally (no oversight badge) when the current user is a participant', () => {
    fixture.componentRef.setInput('currentUserId', 1);
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[data-testid="conversation-looking-in-badge"]'),
    ).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('Team chat');
  });

  it('renders a distinct "looking in" badge with a support/admin aria-label when the current user is not a participant', () => {
    fixture.componentRef.setInput('currentUserId', 99);
    fixture.detectChanges();

    const badge = fixture.nativeElement.querySelector(
      '[data-testid="conversation-looking-in-badge"]',
    );
    expect(badge).toBeTruthy();
    expect(badge.getAttribute('aria-label')).toBeTruthy();
    expect(badge.getAttribute('aria-label')).not.toMatch(/joined/i);
  });
});
