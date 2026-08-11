import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { Subject } from 'rxjs';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { provideTransloco } from '@jsverse/transloco';
import { FakeTranslocoLoader } from '../../testing/fake-transloco-loader';
import { ChatService } from '../../core/chat.service';
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

    it('regression: the 5s poll interleaving with in-progress jump pagination must not restart/uncap the older-page load loop', () => {
      // Reported live as an infinite-request loop after clicking a search result: the 5s poll
      // (`pollNewMessages`/`appendNewer`) and the jump effect's own `loadOlderMessages` chain both
      // funnel through the SAME `ChatService._messageCache` signal. If a poll tick lands between
      // two "before" round trips it produces a *new* entry object (new newer message appended),
      // which re-runs `jumpToMessageEffect` — this must not let the older-page loop exceed
      // `MAX_JUMP_LOAD_ATTEMPTS` (20) or ever resume once it has given up.
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
          nextCursor: 'c0',
        });
      httpMock
        .expectOne('/api/users/me/profile')
        .flush({ userId: 1, email: 'me@x.com', fields: {}, avatarUrl: null });
      fixture.detectChanges();

      let beforeRequestCount = 0;
      let cursor = 'c0';
      // Drive up to 25 "before" round trips (more than MAX_JUMP_LOAD_ATTEMPTS=20) — after every
      // other one, advance the fake clock past the 5s poll interval and flush a poll response
      // that appends a brand-new (unrelated) message, forcing `_messageCache` to change identity
      // mid-chain.
      for (let i = 0; i < 25; i++) {
        const pending = httpMock.match(
          (r) =>
            r.url === '/api/chat/conversations/1/messages' && r.params.get('before') === cursor,
        );
        if (pending.length === 0) {
          break;
        }
        beforeRequestCount += pending.length;
        const nextCursor = `c${i + 1}`;
        pending.forEach((req) =>
          req.flush({
            messages: [
              {
                id: 100 + i,
                senderUserId: 2,
                senderNickname: 'Bob',
                content: 'older',
                createdAt: 'earlier',
              },
            ],
            nextCursor,
          }),
        );
        cursor = nextCursor;
        fixture.detectChanges();

        if (i % 2 === 0) {
          vi.advanceTimersByTime(5000);
          const poll = httpMock.match(
            (r) => r.url === '/api/chat/conversations/1/messages' && r.params.has('after'),
          );
          poll.forEach((req) =>
            req.flush({
              messages: [
                {
                  id: 500 + i,
                  senderUserId: 2,
                  senderNickname: 'Bob',
                  content: 'new',
                  createdAt: 'now',
                },
              ],
              nextCursor: null,
            }),
          );
          fixture.detectChanges();
        }
      }

      // Target message 999 is never among any of the flushed pages, so the loop must have given
      // up at (or before) MAX_JUMP_LOAD_ATTEMPTS=20 "before" requests — not run unbounded.
      expect(beforeRequestCount).toBeGreaterThan(0);
      expect(beforeRequestCount).toBeLessThanOrEqual(20);

      // Once given up, further poll ticks must never resume it.
      vi.advanceTimersByTime(30000);
      httpMock
        .match((r) => r.url === '/api/chat/conversations/1/messages' && r.params.has('after'))
        .forEach((req) => req.flush({ messages: [], nextCursor: null }));
      fixture.detectChanges();
      httpMock.expectNone(
        (r) => r.url === '/api/chat/conversations/1/messages' && r.params.has('before'),
      );
    });

    it('regression: a second jump-to-message request for the SAME still-open conversation must not re-arm a fresh 20-page budget on top of an already-exhausted one', () => {
      // Reported live as an unbounded-looking request storm after clicking a search result: the
      // paramMap.subscribe callback (added in the history.state re-resolve fix) unconditionally
      // reset `jumpLoadAttempts = 0` on every navigation, including a second/third/... search
      // click landing on a conversation that's already open. Since ChatShellComponent keeps this
      // component mounted across same-conversation re-navigations too, a user trying several
      // different search results in a row against a large, mostly-unloaded conversation could
      // trigger 20 "before" requests per click, unboundedly, instead of a single 20-page ceiling
      // for that conversation's whole time open.
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
      const historyStateSpy = vi
        .spyOn(window.history, 'state', 'get')
        .mockReturnValue({ jumpToMessageId: 999, jumpToQuery: 'hi' });
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
        .expectOne((r) => r.url === '/api/chat/conversations/1/messages' && !r.params.has('before'))
        .flush({
          messages: [
            { id: 10, senderUserId: 2, senderNickname: 'Bob', content: 'hi', createdAt: 'now' },
          ],
          nextCursor: 'c0',
        });
      httpMock
        .expectOne('/api/users/me/profile')
        .flush({ userId: 1, email: 'me@x.com', fields: {}, avatarUrl: null });
      fixture.detectChanges();

      let totalBeforeRequests = 0;
      let cursor = 'c0';
      const drainOlderPageRequests = () => {
        for (let i = 0; i < 30; i++) {
          const pending = httpMock.match(
            (r) =>
              r.url === '/api/chat/conversations/1/messages' && r.params.get('before') === cursor,
          );
          if (pending.length === 0) {
            return;
          }
          totalBeforeRequests += pending.length;
          const nextCursor = `c${cursor}-${i + 1}`;
          pending.forEach((req) =>
            req.flush({
              messages: [
                {
                  id: 1000 + i,
                  senderUserId: 2,
                  senderNickname: 'Bob',
                  content: 'older',
                  createdAt: 'earlier',
                },
              ],
              nextCursor,
            }),
          );
          cursor = nextCursor;
          fixture.detectChanges();
        }
      };

      // First jump-to-message request: never found, exhausts the 20-page ceiling.
      drainOlderPageRequests();
      expect(totalBeforeRequests).toBe(20);

      // A second click on a DIFFERENT search result, still targeting this SAME conversation —
      // ChatShellComponent doesn't destroy/recreate this component, so this is another
      // `paramMap` emission with the SAME conversationId, carrying fresh `history.state`. Bug fix
      // (distinct from the one above, found live via Playwright): this must NOT re-trigger
      // `ChatService.openConversation(1)` — doing so used to re-seed the message cache back down
      // to just the latest page, discarding every older page the first jump had already paginated
      // in, which is what let a jump back to an already-loaded older message look "not found"
      // again and restart an unbounded pagination loop. A same-conversation jump-target change
      // must reuse the already-cached messages/cursor state as-is.
      historyStateSpy.mockReturnValue({ jumpToMessageId: 998, jumpToQuery: 'oi' });
      paramMap$.next(convertToParamMap({ conversationId: '1' }));
      httpMock.expectNone('/api/chat/conversations/1');
      httpMock.expectNone(
        (r) => r.url === '/api/chat/conversations/1/messages' && !r.params.has('before'),
      );
      fixture.detectChanges();
      drainOlderPageRequests();

      // The combined total across both jump requests for this one still-open conversation must
      // stay at the single 20-page ceiling — not 20 (first) + 20 (second) = 40. Since the cache
      // was never re-seeded, the second jump's own budget is already exhausted too, so it makes
      // zero further "before" requests.
      expect(totalBeforeRequests).toBe(20);
    });

    it('regression: jumping back to a message already loaded earlier in this same open conversation makes no new pagination requests (distinct from the jumpLoadAttempts-budget bug above — this is about the message cache itself being wrongly re-seeded)', () => {
      // Reported live via Playwright: jump to an older message (paginate to find it), jump to a
      // different, newer, already-loaded message, then jump BACK to the original older message —
      // GET .../messages?after=<same cursor> then fired repeatedly, ~1/s, 30+ times, never
      // stopping. Root cause: `openConversation` used to run unconditionally on every `paramMap`
      // emission, including same-conversation jump-target changes, which re-seeded the message
      // cache back down to just the latest page each time — evicting the older message that had
      // already been loaded, so jumping back to it looked "not found" and restarted pagination
      // (and overlapping in-flight re-seed responses could resolve out of order, looking like an
      // infinite loop against the same cursor).
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
      const historyStateSpy = vi
        .spyOn(window.history, 'state', 'get')
        .mockReturnValue({ jumpToMessageId: 999, jumpToQuery: 'hi' });
      fixture = TestBed.createComponent(ConversationDetailComponent);
      httpMock = TestBed.inject(HttpTestingController);

      // First navigation: opens conversation 1, jumping to older message id 999.
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
        .expectOne((r) => r.url === '/api/chat/conversations/1/messages' && !r.params.has('before'))
        .flush({
          messages: [
            { id: 10, senderUserId: 2, senderNickname: 'Bob', content: 'hi', createdAt: 'now' },
          ],
          nextCursor: 'c0',
        });
      httpMock
        .expectOne('/api/users/me/profile')
        .flush({ userId: 1, email: 'me@x.com', fields: {}, avatarUrl: null });
      fixture.detectChanges();

      // Paginate once to find message 999.
      httpMock
        .expectOne(
          (r) => r.url === '/api/chat/conversations/1/messages' && r.params.get('before') === 'c0',
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
      expect(fixture.nativeElement.querySelector('mark')?.textContent).toBe('hi');

      // Jump to message id 10, already loaded in the first page — no new requests at all.
      historyStateSpy.mockReturnValue({ jumpToMessageId: 10, jumpToQuery: 'hi' });
      paramMap$.next(convertToParamMap({ conversationId: '1' }));
      httpMock.expectNone('/api/chat/conversations/1');
      httpMock.expectNone((r) => r.url === '/api/chat/conversations/1/messages');
      fixture.detectChanges();

      // Jump BACK to message id 999 — already loaded earlier in this same open conversation, so
      // this must not trigger any new HTTP request either.
      historyStateSpy.mockReturnValue({ jumpToMessageId: 999, jumpToQuery: 'again' });
      paramMap$.next(convertToParamMap({ conversationId: '1' }));
      httpMock.expectNone('/api/chat/conversations/1');
      httpMock.expectNone((r) => r.url === '/api/chat/conversations/1/messages');
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelector('mark')?.textContent).toBe('again');
    });

    it('regression: jumping back into a conversation being re-opened (after visiting a different conversation) must not resolve against the stale pre-reseed cache and then lose the target when seedFirstPage overwrites it', () => {
      // Reported live via Playwright: open conversation 1, paginate back to an old message
      // (loading it into the cache), switch to conversation 2, then jump back to that SAME old
      // message in conversation 1. The old race: `openConversation(1)` synchronously flips
      // `entry.loading = true` on the STILL-STALE cache entry (still holding the previously
      // paginated messages, including the target) before the effect's next microtask runs. The
      // jump effect used to check `found` against that stale-but-still-populated cache BEFORE
      // checking `loading`, so it declared the jump "resolved" and cleared
      // `jumpRequestedMessageId` — moments before the real `seedFirstPage` response overwrote
      // `messages` back down to just the latest page, silently discarding the target with no
      // highlight, no scroll, and no error. Fixed by checking `loading` before `found`, so a jump
      // re-entering a conversation that's mid-reseed defers resolution until the fresh page lands,
      // then (correctly) re-paginates via the normal REQ-34 mechanism if the target isn't in it.
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
      const historyStateSpy = vi
        .spyOn(window.history, 'state', 'get')
        .mockReturnValue({ jumpToMessageId: 999, jumpToQuery: 'hi' });
      fixture = TestBed.createComponent(ConversationDetailComponent);
      httpMock = TestBed.inject(HttpTestingController);

      // First navigation: open conversation 1, jump to older message id 999 (forces pagination).
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
        .expectOne((r) => r.url === '/api/chat/conversations/1/messages' && !r.params.has('before'))
        .flush({
          messages: [
            { id: 10, senderUserId: 2, senderNickname: 'Bob', content: 'hi', createdAt: 'now' },
          ],
          nextCursor: 'c0',
        });
      httpMock
        .expectOne('/api/users/me/profile')
        .flush({ userId: 1, email: 'me@x.com', fields: {}, avatarUrl: null });
      fixture.detectChanges();

      httpMock
        .expectOne(
          (r) => r.url === '/api/chat/conversations/1/messages' && r.params.get('before') === 'c0',
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
      expect(fixture.nativeElement.querySelector('mark')?.textContent).toBe('hi');

      // Switch to conversation 2 — a genuinely new conversation, so `openConversation` fires.
      historyStateSpy.mockReturnValue(null);
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

      // Jump back to conversation 1's message 999 — a genuinely different conversation than what's
      // currently open (2), so `isNewConversation` is true and `openConversation(1)` re-fires,
      // re-seeding the cache. The target must still end up highlighted once that settles, not
      // silently dropped.
      historyStateSpy.mockReturnValue({ jumpToMessageId: 999, jumpToQuery: 'again' });
      paramMap$.next(convertToParamMap({ conversationId: '1' }));
      // Simulate the real-world gap between `openConversation(1)` synchronously flipping
      // `entry.loading = true` (over the STILL-STALE, not-yet-overwritten cache) and its HTTP
      // response actually arriving — in production this is real network latency; here, forcing a
      // change-detection/effect flush at this exact point (before either response is flushed)
      // reproduces the same window in which `jumpToMessageEffect` can run against that stale-but-
      // populated cache.
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
          nextCursor: 'c0',
        });
      fixture.detectChanges();

      // Target 999 isn't in the freshly reseeded latest page — the jump must re-paginate for it
      // rather than having already (wrongly) declared victory against the stale pre-reseed cache.
      httpMock
        .expectOne(
          (r) => r.url === '/api/chat/conversations/1/messages' && r.params.get('before') === 'c0',
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

      expect(fixture.nativeElement.querySelector('mark')?.textContent).toBe('again');
    });

    it('bug fix: consumes ChatService#jumpRequest for the currently open conversation, resolving a search-result click on an already-loaded message every time (not just via history.state on a route change)', () => {
      // Counterpart to ChatUnifiedSearchComponent's own coverage — that component now calls
      // `ChatService#requestJump()` directly (instead of navigating to the SAME URL, which the
      // Router's default `onSameUrlNavigation: 'ignore'` silently drops) whenever the clicked
      // result's conversation is the one already open. This asserts the consuming side: this
      // component must pick that request up via its own effect, resolve it exactly like an
      // ordinary jump, and clear it so it isn't re-applied.
      fixture.detectChanges();
      flushOpen([1, 2]); // seeds message id 10, content 'hi'
      fixture.detectChanges();

      const chatService = TestBed.inject(ChatService);
      chatService.requestJump(1, 10, 'hi');
      fixture.detectChanges();

      expect(fixture.nativeElement.querySelector('mark')?.textContent).toBe('hi');
      expect(chatService.jumpRequest()).toBeNull();
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
