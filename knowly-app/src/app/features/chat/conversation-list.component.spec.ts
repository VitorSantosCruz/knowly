import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTransloco } from '@jsverse/transloco';
import { FakeTranslocoLoader } from '../../testing/fake-transloco-loader';
import { ConversationListComponent } from './conversation-list.component';

describe('ConversationListComponent', () => {
  let fixture: ComponentFixture<ConversationListComponent>;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [ConversationListComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        provideTransloco({
          config: { availableLangs: ['en'], defaultLang: 'en' },
          loader: FakeTranslocoLoader,
        }),
      ],
    });
    fixture = TestBed.createComponent(ConversationListComponent);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('calls fetchConversations() on init and renders the result — no permission-denied state', () => {
    fixture.detectChanges();

    httpMock
      .expectOne('/api/chat/conversations')
      .flush([
        {
          id: 1,
          kind: 'PEER_DIRECT',
          tenantId: null,
          title: 'Team chat',
          participantUserIds: [1, 2],
        },
      ]);
    httpMock
      .expectOne('/api/users/me/profile')
      .flush({ userId: 1, email: 'me@x.com', fields: {}, avatarUrl: null });

    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelectorAll('[data-testid="conversation-list-item"]').length,
    ).toBe(1);
    expect(fixture.nativeElement.textContent).not.toMatch(/no-access/i);
  });
});
