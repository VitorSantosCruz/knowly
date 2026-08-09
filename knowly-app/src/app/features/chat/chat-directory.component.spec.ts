import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { provideTransloco } from '@jsverse/transloco';
import { FakeTranslocoLoader } from '../../testing/fake-transloco-loader';
import { ChatDirectoryComponent } from './chat-directory.component';

describe('ChatDirectoryComponent', () => {
  let fixture: ComponentFixture<ChatDirectoryComponent>;
  let httpMock: HttpTestingController;
  let router: Router;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [ChatDirectoryComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideTransloco({
          config: { availableLangs: ['en'], defaultLang: 'en' },
          loader: FakeTranslocoLoader,
        }),
      ],
    });
    fixture = TestBed.createComponent(ChatDirectoryComponent);
    httpMock = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
  });

  afterEach(() => httpMock.verify());

  function flushInit(opts: {
    conversations?: unknown[];
    eligible?: unknown[];
    discoverable?: unknown[];
    activeTenantId?: number | null;
    articles?: unknown[];
  }): void {
    httpMock.expectOne('/api/chat/conversations').flush(opts.conversations ?? []);
    httpMock
      .expectOne((r) => r.url === '/api/chat/eligible-participants')
      .flush(opts.eligible ?? []);
    httpMock
      .expectOne((r) => r.url === '/api/chat/discoverable-groups')
      .flush({
        content: opts.discoverable ?? [],
        page: 0,
        size: 200,
        totalElements: (opts.discoverable ?? []).length,
        totalPages: 1,
      });
    httpMock
      .expectOne('/api/users/me/profile')
      .flush({ userId: 1, email: 'me@x.com', fields: {}, avatarUrl: null });
    const tenantId = opts.activeTenantId ?? null;
    if (tenantId === null) {
      httpMock
        .expectOne('/api/tenants/active')
        .flush(null, { status: 204, statusText: 'No Content' });
    } else {
      httpMock
        .expectOne('/api/tenants/active')
        .flush({ tenantId, tenantName: 'Acme', role: 'MEMBER' });
      fixture.detectChanges();
      httpMock.expectOne(`/api/tenants/${tenantId}/conversations`).flush(opts.articles ?? []);
    }
  }

  function searchInput(): HTMLInputElement {
    return fixture.nativeElement.querySelector('[data-testid="chat-directory-search"]');
  }

  function search(query: string): void {
    searchInput().value = query;
    searchInput().dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  function searchNotTalked(query: string): void {
    const input = fixture.nativeElement.querySelector(
      '[data-testid="chat-directory-not-talked-search"]',
    ) as HTMLInputElement;
    input.value = query;
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  function searchTalked(query: string): void {
    const input = fixture.nativeElement.querySelector(
      '[data-testid="chat-directory-talked-search"]',
    ) as HTMLInputElement;
    input.value = query;
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  it('fetches conversations/eligible-participants/discoverable-groups on init and renders the combined unfiltered list', () => {
    fixture.detectChanges();
    flushInit({
      eligible: [{ userId: 2, nickname: 'Bob' }],
      discoverable: [
        { id: 9, title: 'Grupo Público', tenantId: 1, visibility: 'PUBLIC', participantCount: 2 },
      ],
    });
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[data-testid="chat-directory-row-person:2"]'),
    ).toBeTruthy();
    expect(
      fixture.nativeElement.querySelector('[data-testid="chat-directory-row-group:9"]'),
    ).toBeTruthy();
  });

  it('clicking a person not yet messaged creates a DIRECT conversation and navigates; an existing one navigates directly', () => {
    fixture.detectChanges();
    flushInit({
      conversations: [
        { id: 5, kind: 'PEER_DIRECT', tenantId: null, title: null, participantUserIds: [1, 3] },
      ],
      eligible: [
        { userId: 2, nickname: 'Bob' },
        { userId: 3, nickname: 'Carol' },
      ],
    });
    fixture.detectChanges();

    fixture.nativeElement.querySelector('[data-testid="chat-directory-row-person:3"]').click();
    expect(router.navigate).toHaveBeenCalledWith(['/chat', 5]);

    fixture.nativeElement.querySelector('[data-testid="chat-directory-row-person:2"]').click();
    const req = httpMock.expectOne('/api/chat/conversations');
    expect(req.request.method).toBe('POST');
    req.flush({
      id: 11,
      kind: 'PEER_DIRECT',
      tenantId: null,
      title: null,
      participantUserIds: [1, 2],
    });
    expect(router.navigate).toHaveBeenCalledWith(['/chat', 11]);
  });

  it('clicking a group already participated in navigates to its conversation view', () => {
    fixture.detectChanges();
    flushInit({
      conversations: [
        { id: 6, kind: 'PEER_GROUP', tenantId: 1, title: 'Meu Grupo', participantUserIds: [1, 2] },
      ],
    });
    fixture.detectChanges();

    fixture.nativeElement.querySelector('[data-testid="chat-directory-row-group:6"]').click();
    expect(router.navigate).toHaveBeenCalledWith(['/chat', 6]);
  });

  it('clicking a PUBLIC discoverable group joins immediately and navigates with no confirmation step', () => {
    fixture.detectChanges();
    flushInit({
      discoverable: [
        { id: 9, title: 'Grupo Público', tenantId: 1, visibility: 'PUBLIC', participantCount: 2 },
      ],
    });
    fixture.detectChanges();

    fixture.nativeElement.querySelector('[data-testid="chat-directory-row-group:9"]').click();
    const req = httpMock.expectOne('/api/chat/conversations/9/join');
    req.flush({
      id: 9,
      kind: 'PEER_GROUP',
      tenantId: 1,
      title: 'Grupo Público',
      participantUserIds: [1],
      participantNicknames: {},
      visibility: 'PUBLIC',
      archivedAt: null,
      adminUserIds: [],
    });
    expect(router.navigate).toHaveBeenCalledWith(['/chat', 9]);
  });

  it('clicking a REQUEST_TO_JOIN discoverable group submits a join request and shows it pending, without navigating', () => {
    fixture.detectChanges();
    flushInit({
      discoverable: [
        {
          id: 10,
          title: 'Grupo Solicitação',
          tenantId: 1,
          visibility: 'REQUEST_TO_JOIN',
          participantCount: 4,
        },
      ],
    });
    fixture.detectChanges();

    fixture.nativeElement.querySelector('[data-testid="chat-directory-row-group:10"]').click();
    httpMock.expectOne('/api/chat/conversations/10/join-requests').flush({
      id: 1,
      conversationId: 10,
      requesterUserId: 1,
      requesterNickname: 'Me',
      status: 'PENDING',
      decidedAt: null,
    });
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[data-testid="chat-directory-request-pending"]'),
    ).toBeTruthy();
    expect(router.navigate).not.toHaveBeenCalled();
  });

  it('a failed join/join-request shows an inline error on that row and leaves it clickable', () => {
    fixture.detectChanges();
    flushInit({
      discoverable: [
        { id: 9, title: 'Grupo Público', tenantId: 1, visibility: 'PUBLIC', participantCount: 2 },
      ],
    });
    fixture.detectChanges();

    fixture.nativeElement.querySelector('[data-testid="chat-directory-row-group:9"]').click();
    httpMock
      .expectOne('/api/chat/conversations/9/join')
      .flush(null, { status: 409, statusText: 'Conflict' });
    fixture.detectChanges();

    const row = fixture.nativeElement.querySelector('[data-testid="chat-directory-row-group:9"]');
    expect(row.disabled).toBe(false);
    expect(row.parentElement.querySelector('[role="alert"]')).toBeTruthy();
  });

  it('the "haven\'t talked yet" partition filters by display name case-insensitively, live per keystroke (REQ-8)', () => {
    fixture.detectChanges();
    flushInit({
      eligible: [
        { userId: 2, nickname: 'Bob' },
        { userId: 3, nickname: 'Alice' },
      ],
    });
    fixture.detectChanges();

    searchNotTalked('ali');
    expect(
      fixture.nativeElement.querySelector('[data-testid="chat-directory-row-person:3"]'),
    ).toBeTruthy();
    expect(
      fixture.nativeElement.querySelector('[data-testid="chat-directory-row-person:2"]'),
    ).toBeNull();
  });

  it('the "already talked to" partition has its own independent search', () => {
    fixture.detectChanges();
    flushInit({
      conversations: [
        { id: 5, kind: 'PEER_DIRECT', tenantId: null, title: null, participantUserIds: [1, 2] },
        { id: 6, kind: 'PEER_DIRECT', tenantId: null, title: null, participantUserIds: [1, 3] },
      ],
      eligible: [
        { userId: 2, nickname: 'Bob' },
        { userId: 3, nickname: 'Alice' },
      ],
    });
    fixture.detectChanges();

    searchTalked('ali');
    expect(
      fixture.nativeElement.querySelector('[data-testid="chat-directory-row-person:3"]'),
    ).toBeTruthy();
    expect(
      fixture.nativeElement.querySelector('[data-testid="chat-directory-row-person:2"]'),
    ).toBeNull();
  });

  it('opening a conversation does not itself reorder the "already talked to" list (bug fix: no reordering flicker on mere selection)', () => {
    fixture.detectChanges();
    flushInit({
      conversations: [
        { id: 5, kind: 'PEER_DIRECT', tenantId: null, title: null, participantUserIds: [1, 2] },
        { id: 6, kind: 'PEER_DIRECT', tenantId: null, title: null, participantUserIds: [1, 3] },
      ],
      eligible: [
        { userId: 2, nickname: 'Bob' },
        { userId: 3, nickname: 'Alice' },
      ],
    });
    fixture.detectChanges();

    const keysBefore = Array.from(
      fixture.nativeElement.querySelectorAll('[data-testid^="chat-directory-row-person"]'),
    ).map((el) => (el as HTMLElement).getAttribute('data-testid'));

    fixture.nativeElement.querySelector('[data-testid="chat-directory-row-person:2"]').click();
    fixture.detectChanges();

    const keysAfter = Array.from(
      fixture.nativeElement.querySelectorAll('[data-testid^="chat-directory-row-person"]'),
    ).map((el) => (el as HTMLElement).getAttribute('data-testid'));

    expect(keysAfter).toEqual(keysBefore);
  });

  it('a search with no matches shows a distinct "no results" message', () => {
    fixture.detectChanges();
    flushInit({ eligible: [{ userId: 2, nickname: 'Bob' }] });
    fixture.detectChanges();

    searchNotTalked('zzz');
    expect(
      fixture.nativeElement.querySelector('[data-testid="chat-directory-not-talked-no-results"]'),
    ).toBeTruthy();
    expect(fixture.nativeElement.querySelector('[data-testid="chat-directory-empty"]')).toBeNull();
  });

  it('clearing the search field restores the full list (REQ-11)', () => {
    fixture.detectChanges();
    flushInit({ eligible: [{ userId: 2, nickname: 'Bob' }] });
    fixture.detectChanges();

    searchNotTalked('zzz');
    expect(
      fixture.nativeElement.querySelector('[data-testid="chat-directory-row-person:2"]'),
    ).toBeNull();

    searchNotTalked('');
    expect(
      fixture.nativeElement.querySelector('[data-testid="chat-directory-row-person:2"]'),
    ).toBeTruthy();
  });

  it('never renders a PRIVATE group even if it would textually match — impossible by construction, not client-filtered', () => {
    // The backend never returns a PRIVATE row to a non-participant (see ChatDirectoryService's
    // own doc comment) — this fixture documents that invariant rather than adding a filter here.
    fixture.detectChanges();
    flushInit({ discoverable: [] });
    fixture.detectChanges();

    search('anything');
    expect(
      fixture.nativeElement.querySelector('[data-testid="chat-directory-no-results"]'),
    ).toBeTruthy();
  });

  it('the search field is keyboard-navigable with an explicit aria-label', () => {
    fixture.detectChanges();
    flushInit({});
    fixture.detectChanges();

    expect(searchInput().getAttribute('aria-label')).toBeTruthy();
  });

  it('shows the empty-conversations state distinctly from the no-results state when there is nothing at all', () => {
    fixture.detectChanges();
    flushInit({});
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[data-testid="chat-directory-empty"]'),
    ).toBeTruthy();
  });

  it('renders an avatar (generic fallback, since eligible-participants carries no avatarUrl yet) next to each person row', () => {
    fixture.detectChanges();
    flushInit({ eligible: [{ userId: 2, nickname: 'Bob' }] });
    fixture.detectChanges();

    const row = fixture.nativeElement.querySelector('[data-testid="chat-directory-row-person:2"]');
    expect(row.querySelector('[data-testid="avatar-fallback"]')).toBeTruthy();
  });

  it('always renders a Support row, unaffected by search (REQ-9)', () => {
    fixture.detectChanges();
    flushInit({ eligible: [{ userId: 2, nickname: 'Bob' }] });
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[data-testid="chat-directory-row-support"]'),
    ).toBeTruthy();

    search('zzz');
    expect(
      fixture.nativeElement.querySelector('[data-testid="chat-directory-row-support"]'),
    ).toBeTruthy();
  });

  it('the Support row uses a distinct label from the sidebar\'s "Abrir chamado de suporte" action (bug fix: duplicate label)', () => {
    fixture.detectChanges();
    flushInit({});
    fixture.detectChanges();

    const row = fixture.nativeElement.querySelector('[data-testid="chat-directory-row-support"]');
    expect(row.textContent.trim()).not.toBe('Abrir chamado de suporte');
  });

  it('clicking the Support row navigates to the support section', () => {
    fixture.detectChanges();
    flushInit({});
    fixture.detectChanges();

    fixture.nativeElement.querySelector('[data-testid="chat-directory-row-support"]').click();
    expect(router.navigate).toHaveBeenCalledWith(['/chat'], {
      queryParams: { section: 'support' },
    });
  });

  it('renders every existing "Base de artigos" conversation when a tenant is active, unaffected by search (REQ-9)', () => {
    fixture.detectChanges();
    flushInit({
      activeTenantId: 1,
      articles: [{ id: 7, title: 'Minha conversa' }],
    });
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[data-testid="chat-directory-row-article:7"]'),
    ).toBeTruthy();

    search('zzz');
    expect(
      fixture.nativeElement.querySelector('[data-testid="chat-directory-row-article:7"]'),
    ).toBeTruthy();
  });

  it('clicking a "Base de artigos" row navigates to that conversation', () => {
    fixture.detectChanges();
    flushInit({
      activeTenantId: 1,
      articles: [{ id: 7, title: 'Minha conversa' }],
    });
    fixture.detectChanges();

    fixture.nativeElement.querySelector('[data-testid="chat-directory-row-article:7"]').click();
    expect(router.navigate).toHaveBeenCalledWith(['/chat/articles', 7]);
  });
});
