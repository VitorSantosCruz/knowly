import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { Subject } from 'rxjs';
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
    // jsdom has no scrollIntoView implementation at all (real browsers do) — stubbed globally
    // here (not just in the dedicated jump-to-message tests below) because message-thread's own
    // scroll-into-view fix (2026-08-11 bug fix) retries via a microtask, so any test resolving a
    // jump target can end up invoking it asynchronously, after that test's own assertions ran.
    Element.prototype.scrollIntoView = vi.fn();
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

  /** Opens the group info modal via the header's icon+name button — everything that used to be
   * always-visible under the header now lives there (2026-08-09 UX follow-up). */
  function openInfoModal() {
    fixture.nativeElement.querySelector('[data-testid="chat-header-open-info"]').click();
    fixture.detectChanges();
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

  it('the header icon+name opens the group info modal, which hosts "leave group" for a genuine participant', () => {
    fixture.detectChanges();
    flushOpen([1, 2]);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="leave-group"]')).toBeNull();

    openInfoModal();

    expect(fixture.nativeElement.querySelector('[data-testid="group-info-modal"]')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('[data-testid="leave-group"]')).toBeTruthy();
  });

  it('renders no "limpar"/"clear" control anywhere in a group\'s view — distinct from leaving (REQ-34)', () => {
    fixture.detectChanges();
    flushOpen([1, 2]);
    fixture.detectChanges();
    openInfoModal();

    expect(fixture.nativeElement.querySelector('[data-testid*="clear"]')).toBeNull();
    expect(fixture.nativeElement.textContent.toLowerCase()).not.toContain('limpar');
    expect(fixture.nativeElement.textContent.toLowerCase()).not.toContain('clear conversation');
  });

  it('omits "leave group" for a LOOKING_IN viewer, even with the modal open', () => {
    fixture.detectChanges();
    flushOpen([2, 3]);
    fixture.detectChanges();

    openInfoModal();

    expect(fixture.nativeElement.querySelector('[data-testid="leave-group"]')).toBeNull();
  });

  it('confirming "leave group" calls ChatGroupService.leave and navigates away on success (REQ-17)', () => {
    fixture.detectChanges();
    flushOpen([1, 2]);
    fixture.detectChanges();
    openInfoModal();

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
    openInfoModal();

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

  it('renders GroupAdminPanelComponent inside the group info modal for a PEER_GROUP conversation', () => {
    fixture.detectChanges();
    flushOpen([1, 2]);
    fixture.detectChanges();
    openInfoModal();

    // Non-admin fixture (empty adminUserIds): the panel itself renders nothing visible, but its
    // host element is present in the tree — see group-admin-panel.component.spec.ts for its
    // own admin-gating coverage.
    expect(fixture.nativeElement.querySelector('app-group-admin-panel')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('[data-testid="group-admin-panel"]')).toBeNull();
  });

  it('a PEER_DIRECT conversation opens the person info modal instead, for the other participant', () => {
    fixture.detectChanges();
    httpMock.expectOne('/api/chat/conversations/1').flush({
      id: 1,
      kind: 'PEER_DIRECT',
      tenantId: null,
      title: null,
      participantUserIds: [1, 2],
      participantNicknames: { 1: 'Me', 2: 'Bob' },
      visibility: 'PRIVATE',
      archivedAt: null,
      adminUserIds: [],
    });
    httpMock
      .expectOne((r) => r.url === '/api/chat/conversations/1/messages')
      .flush({ messages: [], nextCursor: null });
    httpMock
      .expectOne('/api/users/me/profile')
      .flush({ userId: 1, email: 'me@x.com', fields: {}, avatarUrl: null });
    fixture.detectChanges();

    openInfoModal();

    expect(fixture.nativeElement.querySelector('[data-testid="group-info-modal"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('[data-testid="person-info-modal"]')).toBeTruthy();

    httpMock
      .expectOne('/api/users/2/profile')
      .flush({ userId: 2, email: 'bob@x.com', fields: { fullName: 'Bob' }, avatarUrl: null });
  });

  describe('REQ-33/34 (Amended 2026-08-10): jump-to-message from a search result', () => {
    function createWithJumpState(jumpToMessageId: number, jumpToQuery: string) {
      TestBed.resetTestingModule();
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
      router = TestBed.inject(Router);
      vi.spyOn(router, 'navigate').mockResolvedValue(true);
      // See conversation-detail.component.ts's Javadoc on `jumpRequestedMessageId` — this
      // component reads `history.state`, not `router.getCurrentNavigation()`, because it isn't
      // constructed directly by a RouterOutlet in this codebase's routing shape.
      vi.spyOn(window.history, 'state', 'get').mockReturnValue({ jumpToMessageId, jumpToQuery });
      fixture = TestBed.createComponent(ConversationDetailComponent);
      httpMock = TestBed.inject(HttpTestingController);
    }

    it('passes highlightMessageId/highlightQuery straight through when the target is already loaded', () => {
      createWithJumpState(10, 'hi');
      fixture.detectChanges();
      flushOpen([1, 2]); // seeds message id 10, content 'hi'
      fixture.detectChanges();

      const thread = fixture.nativeElement.querySelector('[data-testid="message-thread"]');
      expect(thread).toBeTruthy();
      httpMock.expectNone(
        (r) => r.url === '/api/chat/conversations/1/messages' && r.params.has('before'),
      );
    });

    it('calls loadOlderMessages repeatedly until the target message is found', () => {
      createWithJumpState(999, 'hi');
      fixture.detectChanges();

      httpMock.expectOne('/api/chat/conversations/1').flush({
        id: 1,
        kind: 'PEER_GROUP',
        tenantId: null,
        title: 'Group',
        participantUserIds: [1, 2],
        participantNicknames: {},
        visibility: 'PRIVATE',
        archivedAt: null,
        adminUserIds: [],
      });
      httpMock
        .expectOne((r) => r.url === '/api/chat/conversations/1/messages' && !r.params.has('before'))
        .flush({
          messages: [
            { id: 10, senderUserId: 2, senderNickname: 'Bob', content: 'hi', createdAt: 'now' },
          ],
          nextCursor: 'c1',
        });
      httpMock
        .expectOne('/api/users/me/profile')
        .flush({ userId: 1, email: 'me@x.com', fields: {}, avatarUrl: null });
      fixture.detectChanges();

      // Not found yet (target id 999) — another older page is requested automatically.
      httpMock
        .expectOne(
          (r) => r.url === '/api/chat/conversations/1/messages' && r.params.get('before') === 'c1',
        )
        .flush({
          messages: [
            {
              id: 999,
              senderUserId: 2,
              senderNickname: 'Bob',
              content: 'hi again',
              createdAt: 'earlier',
            },
          ],
          nextCursor: null,
        });
      fixture.detectChanges();

      httpMock.expectNone(
        (r) => r.url === '/api/chat/conversations/1/messages' && r.params.has('before'),
      );
    });

    it('gives up once hasMore is false without an error state', () => {
      createWithJumpState(999, 'hi');
      fixture.detectChanges();

      httpMock.expectOne('/api/chat/conversations/1').flush({
        id: 1,
        kind: 'PEER_GROUP',
        tenantId: null,
        title: 'Group',
        participantUserIds: [1, 2],
        participantNicknames: {},
        visibility: 'PRIVATE',
        archivedAt: null,
        adminUserIds: [],
      });
      httpMock
        .expectOne((r) => r.url === '/api/chat/conversations/1/messages' && !r.params.has('before'))
        .flush({
          messages: [
            { id: 10, senderUserId: 2, senderNickname: 'Bob', content: 'hi', createdAt: 'now' },
          ],
          nextCursor: null,
        });
      httpMock
        .expectOne('/api/users/me/profile')
        .flush({ userId: 1, email: 'me@x.com', fields: {}, avatarUrl: null });
      fixture.detectChanges();

      httpMock.expectNone(
        (r) => r.url === '/api/chat/conversations/1/messages' && r.params.has('before'),
      );
      expect(fixture.nativeElement.querySelector('[role="alert"]')).toBeNull();
    });

    it.each([
      ['PEER_GROUP', [1, 2]],
      ['PEER_DIRECT', [1, 2]],
    ] as const)(
      'bug fix (%s): jump-to-message calls element.scrollIntoView, not just the <mark> highlight, once the target loads — reported live as working for PEER_GROUP but not PEER_DIRECT',
      async (kind, participantUserIds) => {
        const scrollIntoViewSpy = vi.spyOn(Element.prototype, 'scrollIntoView');

        createWithJumpState(10, 'hi');
        fixture.detectChanges();
        httpMock.expectOne('/api/chat/conversations/1').flush({
          id: 1,
          kind,
          tenantId: null,
          title: kind === 'PEER_GROUP' ? 'Group' : null,
          participantUserIds,
          participantNicknames: kind === 'PEER_DIRECT' ? { 1: 'Me', 2: 'Bob' } : {},
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
        fixture.detectChanges();

        // The <mark> highlight (a template binding) and the imperative scrollIntoView call are
        // two independently-verified assertions — the reported bug was <mark> working while
        // scrollIntoView silently never fired for PEER_DIRECT specifically.
        expect(fixture.nativeElement.querySelector('mark')?.textContent).toBe('hi');
        // The scroll may resolve on the effect's first synchronous pass or via its microtask
        // retry (see message-thread.component.ts's `scrollToTarget` Javadoc) — either is correct.
        await Promise.resolve();
        expect(scrollIntoViewSpy).toHaveBeenCalled();
      },
    );

    it('bug fix: resolves a jump-to-message request on a SECOND paramMap navigation too — not just at first construction, since ChatShellComponent keeps this component alive across /chat/:id navigations', () => {
      const paramMap$ = new Subject<ReturnType<typeof convertToParamMap>>();
      TestBed.resetTestingModule();
      TestBed.configureTestingModule({
        imports: [ConversationDetailComponent],
        providers: [
          provideHttpClient(),
          provideHttpClientTesting(),
          provideTransloco({
            config: { availableLangs: ['en'], defaultLang: 'en' },
            loader: FakeTranslocoLoader,
          }),
          { provide: ActivatedRoute, useValue: { paramMap: paramMap$ } },
        ],
      });
      router = TestBed.inject(Router);
      vi.spyOn(router, 'navigate').mockResolvedValue(true);
      // First navigation: no jump requested at all — mirrors an ordinary "open conversation 1"
      // click that doesn't come from a search result.
      const historyStateSpy = vi.spyOn(window.history, 'state', 'get').mockReturnValue(null);
      fixture = TestBed.createComponent(ConversationDetailComponent);
      httpMock = TestBed.inject(HttpTestingController);

      fixture.detectChanges();
      paramMap$.next(convertToParamMap({ conversationId: '1' }));
      httpMock.expectOne('/api/chat/conversations/1').flush({
        id: 1,
        kind: 'PEER_GROUP',
        tenantId: null,
        title: 'Group',
        participantUserIds: [1, 2],
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
      fixture.detectChanges();

      // Second navigation, same still-alive component instance (as ChatShellComponent's @if
      // never toggles false/true across a /chat/:id -> /chat/:otherId search-result click) — this
      // time carrying a genuine jump-to-message request.
      historyStateSpy.mockReturnValue({ jumpToMessageId: 20, jumpToQuery: 'oi' });
      paramMap$.next(convertToParamMap({ conversationId: '2' }));
      httpMock.expectOne('/api/chat/conversations/2').flush({
        id: 2,
        kind: 'PEER_GROUP',
        tenantId: null,
        title: 'Group 2',
        participantUserIds: [1, 2],
        participantNicknames: {},
        visibility: 'PRIVATE',
        archivedAt: null,
        adminUserIds: [],
      });
      httpMock
        .expectOne((r) => r.url === '/api/chat/conversations/2/messages')
        .flush({
          messages: [
            { id: 20, senderUserId: 2, senderNickname: 'Bob', content: 'oi', createdAt: 'now' },
          ],
          nextCursor: null,
        });
      fixture.detectChanges();

      const thread = fixture.nativeElement.querySelector('[data-testid="message-thread"]');
      expect(thread).toBeTruthy();
      // The bug: without the fix, jumpRequestedMessageId stays null forever (read once at
      // construction, when history.state was still null), so no further "load older" calls are
      // ever made and no highlight is ever passed down — this assertion would still pass on
      // broken code (no crash), so the real regression coverage is message-thread's own
      // highlight/flash spec plus this component's `<mark>`-bearing DOM below.
      expect(fixture.nativeElement.querySelector('mark')?.textContent).toBe('oi');
    });
  });
});
