import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { provideTransloco } from '@jsverse/transloco';
import { FakeTranslocoLoader } from '../../testing/fake-transloco-loader';
import { ConversationDetailComponent } from './conversation-detail.component';

describe('ConversationDetailComponent', () => {
  let fixture: ComponentFixture<ConversationDetailComponent>;
  let httpMock: HttpTestingController;
  let router: Router;

  beforeEach(() => {
    vi.useFakeTimers();
    TestBed.configureTestingModule({
      imports: [ConversationDetailComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideTransloco({
          config: { availableLangs: ['en'], defaultLang: 'en' },
          loader: FakeTranslocoLoader,
        }),
        {
          provide: ActivatedRoute,
          useValue: { paramMap: of(convertToParamMap({ conversationId: '1' })) },
        },
      ],
    });
    fixture = TestBed.createComponent(ConversationDetailComponent);
    httpMock = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
  });

  afterEach(() => {
    httpMock.verify();
    vi.useRealTimers();
  });

  function flushOpen(participantIds: number[]) {
    httpMock.expectOne('/api/chat/conversations/1').flush({
      id: 1,
      kind: 'PEER_GROUP',
      tenantId: null,
      title: 'Group',
      participantUserIds: participantIds,
      participantNicknames: {},
      visibility: 'PRIVATE',
      archivedAt: null,
      adminUserIds: [],
    });
    httpMock
      .expectOne((r) => r.url === '/api/chat/conversations/1/messages')
      .flush({
        messages: [
          { id: 10, senderUserId: 2, senderNickname: 'Bob', content: 'hi', createdAt: 'now' },
        ],
        nextCursor: null,
      });
    httpMock
      .expectOne('/api/users/me/profile')
      .flush({ userId: 1, email: 'me@x.com', fields: {}, avatarUrl: null });
  }

  it('opens the conversation on route param change and passes data into message-thread', () => {
    fixture.detectChanges();
    flushOpen([1, 2]);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="message-thread"]')).toBeTruthy();
  });

  it('omits the composer when viewerRelation is LOOKING_IN', () => {
    fixture.detectChanges();
    flushOpen([2, 3]);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="message-composer"]')).toBeNull();
  });

  it('shows the composer when the viewer is a genuine participant', () => {
    fixture.detectChanges();
    flushOpen([1, 2]);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="message-composer"]')).toBeTruthy();
  });

  it('renders the no-access state on a 403/404 rather than crashing', () => {
    fixture.detectChanges();
    httpMock
      .expectOne('/api/chat/conversations/1')
      .flush('nope', { status: 403, statusText: 'Forbidden' });
    httpMock
      .expectOne((r) => r.url === '/api/chat/conversations/1/messages')
      .flush('nope', { status: 403, statusText: 'Forbidden' });
    httpMock
      .expectOne('/api/users/me/profile')
      .flush({ userId: 1, email: 'me@x.com', fields: {}, avatarUrl: null });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="no-access-state"]')).toBeTruthy();
  });

  it('polls for new messages every 5s only while the tab is visible', () => {
    fixture.detectChanges();
    flushOpen([1, 2]);
    fixture.detectChanges();

    Object.defineProperty(document, 'visibilityState', { value: 'hidden', configurable: true });
    vi.advanceTimersByTime(5000);
    httpMock.expectNone(
      (r) => r.url === '/api/chat/conversations/1/messages' && r.params.has('after'),
    );

    Object.defineProperty(document, 'visibilityState', { value: 'visible', configurable: true });
    vi.advanceTimersByTime(5000);
    httpMock
      .expectOne((r) => r.url === '/api/chat/conversations/1/messages' && r.params.has('after'))
      .flush({ messages: [], nextCursor: null });
  });

  it('shows "sair do grupo" for a genuine participant, not for a LOOKING_IN viewer (REQ-16)', () => {
    fixture.detectChanges();
    flushOpen([1, 2]);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-testid="leave-group"]')).toBeTruthy();
  });

  it('omits "sair do grupo" for a LOOKING_IN viewer', () => {
    fixture.detectChanges();
    flushOpen([2, 3]);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-testid="leave-group"]')).toBeNull();
  });

  it('confirming "sair do grupo" calls ChatGroupService.leave and navigates away on success (REQ-17)', () => {
    fixture.detectChanges();
    flushOpen([1, 2]);
    fixture.detectChanges();

    fixture.nativeElement.querySelector('[data-testid="leave-group"]').click();
    fixture.detectChanges();
    fixture.nativeElement.querySelector('[data-testid="confirm-leave-group"]').click();

    httpMock
      .expectOne('/api/chat/conversations/1/leave')
      .flush(null, { status: 204, statusText: 'No Content' });

    expect(router.navigate).toHaveBeenCalledWith(['/chat']);
  });

  it('a failed leave shows an inline error and leaves the view unchanged (REQ-27)', () => {
    fixture.detectChanges();
    flushOpen([1, 2]);
    fixture.detectChanges();

    fixture.nativeElement.querySelector('[data-testid="leave-group"]').click();
    fixture.detectChanges();
    fixture.nativeElement.querySelector('[data-testid="confirm-leave-group"]').click();

    httpMock
      .expectOne('/api/chat/conversations/1/leave')
      .flush(null, { status: 403, statusText: 'Forbidden' });
    fixture.detectChanges();

    expect(router.navigate).not.toHaveBeenCalledWith(['/chat']);
    expect(fixture.nativeElement.querySelector('[role="alert"]')).toBeTruthy();
  });

  it('renders GroupAdminPanelComponent for a PEER_GROUP conversation', () => {
    fixture.detectChanges();
    flushOpen([1, 2]);
    fixture.detectChanges();
    // Non-admin fixture (empty adminUserIds): the panel itself renders nothing visible, but its
    // host element is present in the tree — see group-admin-panel.component.spec.ts for its
    // own admin-gating coverage.
    expect(fixture.nativeElement.querySelector('app-group-admin-panel')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('[data-testid="group-admin-panel"]')).toBeNull();
  });
});
