import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { BehaviorSubject, of } from 'rxjs';
import { provideTransloco } from '@jsverse/transloco';
import { FakeTranslocoLoader } from '../../testing/fake-transloco-loader';
import { mockViewportMatchMedia } from '../../testing/mock-match-media';
import { ChatShellComponent } from './chat-shell.component';

describe('ChatShellComponent', () => {
  let fixture: ComponentFixture<ChatShellComponent>;
  let httpMock: HttpTestingController;
  let router: Router;
  let queryParamMap$: BehaviorSubject<ReturnType<typeof convertToParamMap>>;
  let data$: BehaviorSubject<Record<string, unknown>>;

  function setup(opts: { viewportIsDesktop?: boolean } = {}): void {
    TestBed.resetTestingModule();
    mockViewportMatchMedia(opts.viewportIsDesktop ?? true);
    queryParamMap$ = new BehaviorSubject(convertToParamMap({}));
    data$ = new BehaviorSubject<Record<string, unknown>>({});
    TestBed.configureTestingModule({
      imports: [ChatShellComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideTransloco({
          config: { availableLangs: ['en'], defaultLang: 'en' },
          loader: FakeTranslocoLoader,
        }),
        {
          provide: ActivatedRoute,
          useValue: {
            queryParamMap: queryParamMap$,
            paramMap: of(convertToParamMap({})),
            data: data$,
            snapshot: { data: {} },
          },
        },
      ],
    });
    fixture = TestBed.createComponent(ChatShellComponent);
    httpMock = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
  }

  afterEach(() => httpMock.verify());

  /** Every branch this shell can render triggers its own `ActiveTenantService.fetch()` call
   * in addition to this shell's own (`SupportPageComponent`/none for the others) — flush every
   * currently-pending `/api/tenants/active` request identically rather than assuming exactly
   * one, since that count is an implementation detail of whichever child is active. */
  function flushActiveTenant(tenantId: number | null): void {
    for (const req of httpMock.match('/api/tenants/active')) {
      if (tenantId === null) {
        req.flush(null, { status: 204, statusText: 'No Content' });
      } else {
        req.flush({ tenantId, tenantName: 'Acme', role: 'MEMBER' });
      }
    }
  }

  /** More than one mounted branch can fetch the current user's own profile
   * (`ChatDirectoryRowsService` and, independently, `SupportPageComponent`) — flush every
   * currently-pending request identically, same reasoning as `flushActiveTenant`. */
  function flushProfile(): void {
    for (const req of httpMock.match('/api/users/me/profile')) {
      req.flush({ userId: 1, email: 'me@x.com', fields: {}, avatarUrl: null });
    }
  }

  function flushDirectory(): void {
    httpMock.expectOne('/api/chat/conversations').flush([]);
    httpMock.expectOne((r) => r.url === '/api/chat/eligible-participants').flush([]);
    httpMock
      .expectOne((r) => r.url === '/api/chat/discoverable-groups')
      .flush({ content: [], page: 0, size: 200, totalElements: 0, totalPages: 1 });
    flushProfile();
    flushActiveTenant(null);
  }

  function flushSupportPageBootstrap(): void {
    httpMock.expectOne('/api/staff/permissions').flush({ permissions: [] });
    httpMock.expectOne('/api/tenants/permissions').flush({ permissions: [] });
    flushProfile();
    flushActiveTenant(null);
  }

  it('renders the directory column (sidebar actions + unified list) alongside a conversation placeholder when nothing is open', () => {
    setup();
    fixture.detectChanges();
    flushActiveTenant(null);
    flushDirectory();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('app-chat-directory')).toBeTruthy();
    expect(
      fixture.nativeElement.querySelector('[data-testid="chat-sidebar-action-create-group"]'),
    ).toBeTruthy();
    expect(
      fixture.nativeElement.querySelector('[data-testid="chat-shell-conversation-placeholder"]'),
    ).toBeTruthy();
  });

  it('collapses to one column at a time below the breakpoint (REQ-2c) — directory only, no conversation open yet', () => {
    setup({ viewportIsDesktop: false });
    fixture.detectChanges();
    flushActiveTenant(null);
    flushDirectory();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('app-chat-directory')).toBeTruthy();
    expect(
      fixture.nativeElement.querySelector('[data-testid="chat-shell-conversation-column"]'),
    ).toBeNull();
  });

  it('renders SupportPageComponent for section=support', () => {
    setup();
    queryParamMap$.next(convertToParamMap({ section: 'support' }));
    fixture.detectChanges();
    flushActiveTenant(null);
    flushDirectory();
    flushSupportPageBootstrap();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('app-support-page')).toBeTruthy();
  });

  it('renders ConversationsPageComponent for section=articles with an active tenant, and the no-tenant state without one', () => {
    setup();
    queryParamMap$.next(convertToParamMap({ section: 'articles' }));
    fixture.detectChanges();
    flushActiveTenant(1);
    flushDirectory();
    fixture.detectChanges();
    flushActiveTenant(1);
    // Both `ConversationsPageComponent`'s own fetch and `ChatDirectoryRowsService`'s
    // article-row fetch hit this exact same endpoint once the tenant resolves.
    for (const req of httpMock.match((r) => r.url === '/api/tenants/1/conversations')) {
      req.flush([]);
    }
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('app-conversations-page')).toBeTruthy();
    expect(
      fixture.nativeElement.querySelector('[data-testid="no-active-tenant-state"]'),
    ).toBeNull();
  });

  it('renders the no-active-tenant state for section=articles with no active tenant (regression, dropped route guard)', () => {
    setup();
    queryParamMap$.next(convertToParamMap({ section: 'articles' }));
    fixture.detectChanges();
    flushActiveTenant(null);
    flushDirectory();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('app-conversations-page')).toBeNull();
    expect(
      fixture.nativeElement.querySelector('[data-testid="no-active-tenant-state"]'),
    ).toBeTruthy();
  });

  it('renders ConversationDetailComponent when the route data marks this a peer conversation', () => {
    setup();
    data$.next({ chatSection: 'peer' });
    fixture.detectChanges();
    flushActiveTenant(null);
    flushDirectory();
    httpMock
      .expectOne((r) => r.url === '/api/chat/conversations/0')
      .flush(null, { status: 404, statusText: 'Not Found' });
    httpMock
      .expectOne((r) => r.url === '/api/chat/conversations/0/messages')
      .flush(null, { status: 404, statusText: 'Not Found' });
    flushProfile();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('app-conversation-detail')).toBeTruthy();
  });

  it('clicking the "Abrir chamado de suporte" action navigates via the Router with the support section, not a full reload', () => {
    setup();
    fixture.detectChanges();
    flushActiveTenant(1);
    flushDirectory();
    fixture.detectChanges();
    // ChatDirectoryRowsService's own article-row fetch, triggered once activeTenantId
    // resolves to 1 — unrelated to the action under test, just needs flushing.
    httpMock.expectOne((r) => r.url === '/api/tenants/1/conversations').flush([]);

    fixture.nativeElement.querySelector('[data-testid="chat-sidebar-action-support"]').click();

    expect(router.navigate).toHaveBeenCalledWith(['/chat'], {
      queryParams: { section: 'support' },
    });
  });

  it('hides the "Abrir chamado de suporte"/"Falar com a base de artigos" quick actions without an active tenant (bug fix: staff-without-tenant oversight view), while still reaching Support via its always-present row', () => {
    setup();
    fixture.detectChanges();
    flushActiveTenant(null);
    flushDirectory();
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[data-testid="chat-sidebar-action-support"]'),
    ).toBeNull();
    expect(
      fixture.nativeElement.querySelector('[data-testid="chat-sidebar-action-articles"]'),
    ).toBeNull();
    expect(
      fixture.nativeElement.querySelector('[data-testid="chat-sidebar-action-create-group"]'),
    ).toBeTruthy();
    expect(
      fixture.nativeElement.querySelector('[data-testid="chat-directory-row-support"]'),
    ).toBeTruthy();
  });

  it('clicking "Falar com a base de artigos" with an active tenant creates a new RAG conversation and navigates straight to it', () => {
    setup();
    queryParamMap$.next(convertToParamMap({}));
    fixture.detectChanges();
    flushActiveTenant(1);
    flushDirectory();
    fixture.detectChanges();
    // ChatDirectoryRowsService's own article-row fetch, triggered once activeTenantId
    // resolves to 1 — unrelated to the action under test, just needs flushing.
    httpMock.expectOne((r) => r.url === '/api/tenants/1/conversations').flush([]);

    fixture.nativeElement.querySelector('[data-testid="chat-sidebar-action-articles"]').click();
    const req = httpMock.expectOne('/api/tenants/1/conversations');
    expect(req.request.method).toBe('POST');
    req.flush({ id: 42, title: null });

    expect(router.navigate).toHaveBeenCalledWith(['/chat/articles', 42]);
  });

  it('does not flash the no-active-tenant state while /api/tenants/active is still resolving on a reload (bug fix: staff-inside-a-tenant losing context on F5)', () => {
    setup();
    fixture.detectChanges();

    // GET /api/tenants/active is still pending (activeTenantResolved() reads false) — the
    // sidebar's tenant-scoped quick actions must not have already collapsed to the
    // no-active-tenant look, or a reload would visibly (if briefly) present as "lost the
    // tenant" before the real response lands.
    expect(
      fixture.nativeElement.querySelector('[data-testid="chat-sidebar-action-support"]'),
    ).toBeTruthy();
    expect(
      fixture.nativeElement.querySelector('[data-testid="chat-sidebar-action-articles"]'),
    ).toBeTruthy();

    flushActiveTenant(1);
    flushDirectory();
    fixture.detectChanges();
    httpMock.expectOne((r) => r.url === '/api/tenants/1/conversations').flush([]);

    // Still correct once resolved with a real active tenant.
    expect(
      fixture.nativeElement.querySelector('[data-testid="chat-sidebar-action-support"]'),
    ).toBeTruthy();
    expect(
      fixture.nativeElement.querySelector('[data-testid="chat-sidebar-action-articles"]'),
    ).toBeTruthy();
  });

  it('shows a loading state, not the no-active-tenant state, for section=articles while /api/tenants/active is still resolving on a reload', () => {
    setup();
    queryParamMap$.next(convertToParamMap({ section: 'articles' }));
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[data-testid="chat-shell-articles-loading-state"]'),
    ).toBeTruthy();
    expect(
      fixture.nativeElement.querySelector('[data-testid="no-active-tenant-state"]'),
    ).toBeNull();
    expect(fixture.nativeElement.querySelector('app-conversations-page')).toBeNull();

    flushActiveTenant(1);
    flushDirectory();
    fixture.detectChanges();
    flushActiveTenant(1);
    for (const req of httpMock.match((r) => r.url === '/api/tenants/1/conversations')) {
      req.flush([]);
    }
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[data-testid="chat-shell-articles-loading-state"]'),
    ).toBeNull();
    expect(fixture.nativeElement.querySelector('app-conversations-page')).toBeTruthy();
  });

  it('clicking "Criar grupo" opens the create-group dialog', () => {
    setup();
    fixture.detectChanges();
    flushActiveTenant(null);
    flushDirectory();
    fixture.detectChanges();

    fixture.nativeElement.querySelector('[data-testid="chat-sidebar-action-create-group"]').click();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="create-group-dialog"]').open).toBe(
      true,
    );
  });
});
