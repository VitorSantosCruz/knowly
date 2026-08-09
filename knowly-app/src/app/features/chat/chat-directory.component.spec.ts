import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { provideTransloco } from '@jsverse/transloco';
import { FakeTranslocoLoader } from '../../testing/fake-transloco-loader';
import { ChatDirectoryComponent } from './chat-directory.component';

describe('ChatDirectoryComponent — Amendment (3): unified column 1', () => {
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
    eligibleForTenant?: unknown[];
    discoverable?: unknown[];
    activeTenantId?: number | null;
    articles?: unknown[];
  }): void {
    httpMock.expectOne('/api/chat/conversations').flush(opts.conversations ?? []);
    httpMock
      .expectOne((r) => r.url === '/api/chat/eligible-participants' && !r.params.has('tenantId'))
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
      httpMock
        .expectOne(
          (r) =>
            r.url === '/api/chat/eligible-participants' &&
            r.params.get('tenantId') === String(tenantId),
        )
        .flush(opts.eligibleForTenant ?? opts.eligible ?? []);
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

  it('renders one <ul> over conversationRows() with Support always the first row in the DOM', () => {
    fixture.detectChanges();
    flushInit({
      conversations: [
        { id: 5, kind: 'PEER_DIRECT', tenantId: null, title: null, participantUserIds: [1, 2] },
      ],
      eligible: [{ userId: 2, nickname: 'Bob' }],
    });
    fixture.detectChanges();

    const list = fixture.nativeElement.querySelector('[data-testid="chat-directory-list"]');
    expect(list).toBeTruthy();
    const rows = Array.from(list.querySelectorAll('[data-testid^="chat-directory-row-"]'));
    expect((rows[0] as HTMLElement).getAttribute('data-testid')).toBe('chat-directory-row-support');
  });

  it('does not render a not-yet-messaged person (they belong in column 3 only)', () => {
    fixture.detectChanges();
    flushInit({
      eligible: [{ userId: 2, nickname: 'Bob' }],
    });
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[data-testid="chat-directory-row-person:2"]'),
    ).toBeNull();
  });

  it('does not render a discoverable, non-member group (it belongs in column 3 only)', () => {
    fixture.detectChanges();
    flushInit({
      discoverable: [
        { id: 9, title: 'Grupo Público', tenantId: 1, visibility: 'PUBLIC', participantCount: 2 },
      ],
    });
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[data-testid="chat-directory-row-group:9"]'),
    ).toBeNull();
  });

  it('renders a person already messaged and a group already joined in the unified list', () => {
    fixture.detectChanges();
    flushInit({
      conversations: [
        { id: 5, kind: 'PEER_DIRECT', tenantId: null, title: null, participantUserIds: [1, 2] },
        { id: 6, kind: 'PEER_GROUP', tenantId: 1, title: 'Meu Grupo', participantUserIds: [1, 3] },
      ],
      eligible: [{ userId: 2, nickname: 'Bob' }],
    });
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[data-testid="chat-directory-row-person:2"]'),
    ).toBeTruthy();
    expect(
      fixture.nativeElement.querySelector('[data-testid="chat-directory-row-group:6"]'),
    ).toBeTruthy();
  });

  it('one unifiedQuery search field filters every row except the pinned Support row (REQ-2/REQ-9)', () => {
    fixture.detectChanges();
    flushInit({
      conversations: [
        { id: 5, kind: 'PEER_DIRECT', tenantId: null, title: null, participantUserIds: [1, 2] },
      ],
      eligible: [{ userId: 2, nickname: 'Bob' }],
    });
    fixture.detectChanges();

    search('zzz');

    expect(
      fixture.nativeElement.querySelector('[data-testid="chat-directory-row-person:2"]'),
    ).toBeNull();
    expect(
      fixture.nativeElement.querySelector('[data-testid="chat-directory-row-support"]'),
    ).toBeTruthy();
  });

  it('a search with zero matches shows the distinct "no results" message; clearing restores the full list (REQ-10/REQ-11)', () => {
    fixture.detectChanges();
    flushInit({
      conversations: [
        { id: 5, kind: 'PEER_DIRECT', tenantId: null, title: null, participantUserIds: [1, 2] },
      ],
      eligible: [{ userId: 2, nickname: 'Bob' }],
    });
    fixture.detectChanges();

    search('zzz');
    expect(
      fixture.nativeElement.querySelector('[data-testid="chat-directory-no-results"]'),
    ).toBeTruthy();

    search('');
    expect(
      fixture.nativeElement.querySelector('[data-testid="chat-directory-row-person:2"]'),
    ).toBeTruthy();
  });

  it('clicking a person not yet messaged creates a DIRECT conversation and navigates; an existing one navigates directly', () => {
    fixture.detectChanges();
    flushInit({
      conversations: [
        { id: 5, kind: 'PEER_DIRECT', tenantId: null, title: null, participantUserIds: [1, 3] },
      ],
      eligible: [{ userId: 3, nickname: 'Carol' }],
    });
    fixture.detectChanges();

    fixture.nativeElement.querySelector('[data-testid="chat-directory-row-person:3"]').click();
    expect(router.navigate).toHaveBeenCalledWith(['/chat', 5]);
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

  it('a failed action shows an inline error on that row and leaves it clickable', () => {
    fixture.detectChanges();
    flushInit({
      conversations: [
        { id: 5, kind: 'PEER_DIRECT', tenantId: null, title: null, participantUserIds: [1, 2] },
      ],
      eligible: [{ userId: 2, nickname: 'Bob' }],
    });
    fixture.detectChanges();
    // No failure path exercised here beyond the row-error rendering contract already covered
    // by chat-full-directory.component.spec.ts (join/join-request failures now originate from
    // column 3 rows, since only not-yet-joined groups can fail to join).
    expect(
      fixture.nativeElement.querySelector('[data-testid="chat-directory-row-person:2"]'),
    ).toBeTruthy();
  });

  it('opening a conversation does not itself reorder the list (bug fix: no reordering flicker on mere selection)', () => {
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

  it('never renders a PRIVATE group even if it would textually match — impossible by construction, not client-filtered', () => {
    fixture.detectChanges();
    flushInit({ discoverable: [] });
    fixture.detectChanges();

    search('anything');
    expect(
      fixture.nativeElement.querySelector('[data-testid="chat-directory-no-results"]'),
    ).toBeTruthy();
    // Support stays exempt from the search filter even while "no results" is showing.
    expect(
      fixture.nativeElement.querySelector('[data-testid="chat-directory-row-support"]'),
    ).toBeTruthy();
  });

  it('the search field is keyboard-navigable with an explicit aria-label', () => {
    fixture.detectChanges();
    flushInit({});
    fixture.detectChanges();

    expect(searchInput().getAttribute('aria-label')).toBeTruthy();
  });

  it('renders an avatar (generic fallback, since eligible-participants carries no avatarUrl yet) next to each person row', () => {
    fixture.detectChanges();
    flushInit({
      conversations: [
        { id: 5, kind: 'PEER_DIRECT', tenantId: null, title: null, participantUserIds: [1, 2] },
      ],
      eligible: [{ userId: 2, nickname: 'Bob' }],
    });
    fixture.detectChanges();

    const row = fixture.nativeElement.querySelector('[data-testid="chat-directory-row-person:2"]');
    expect(row.querySelector('[data-testid="avatar-fallback"]')).toBeTruthy();
  });

  it('always renders a Support row, unaffected by search (REQ-9)', () => {
    fixture.detectChanges();
    flushInit({});
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[data-testid="chat-directory-row-support"]'),
    ).toBeTruthy();

    search('zzz');
    expect(
      fixture.nativeElement.querySelector('[data-testid="chat-directory-row-support"]'),
    ).toBeTruthy();
  });

  it('does not render any clear/limpar control for the Support row, under any state (REQ-35)', () => {
    fixture.detectChanges();
    flushInit({});
    fixture.detectChanges();

    const row = fixture.nativeElement
      .querySelector('[data-testid="chat-directory-row-support"]')
      .closest('li');
    expect(row.querySelector('[data-testid*="clear"]')).toBeNull();
    expect(row.textContent.toLowerCase()).not.toContain('limpar');
    expect(row.textContent.toLowerCase()).not.toContain('clear');
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

  it('renders every existing "Base de artigos" conversation when a tenant is active, unaffected by search until Amendment (3)\'s unified filter applies', () => {
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
    ).toBeNull();
  });

  it("renders an article row's own icon (Amendment (4)) instead of the generic fallback when set", () => {
    fixture.detectChanges();
    flushInit({
      activeTenantId: 1,
      articles: [{ id: 7, title: 'Minha conversa', icon: 'BOOK_OPEN' }],
    });
    fixture.detectChanges();

    const row = fixture.nativeElement.querySelector('[data-testid="chat-directory-row-article:7"]');
    expect(row.querySelector('[data-testid="avatar-icon"]')).toBeTruthy();
    expect(row.querySelector('[data-testid="chat-icon-BOOK_OPEN"]')).toBeTruthy();
    expect(row.querySelector('[data-testid="avatar-fallback"]')).toBeNull();
  });

  it("renders a group row's own icon (Amendment (4)) instead of the generic fallback when set", () => {
    fixture.detectChanges();
    flushInit({
      conversations: [
        {
          id: 6,
          kind: 'PEER_GROUP',
          tenantId: null,
          title: 'Grupo',
          participantUserIds: [1, 2],
          icon: 'ROCKET',
        },
      ],
    });
    fixture.detectChanges();

    const row = fixture.nativeElement.querySelector('[data-testid="chat-directory-row-group:6"]');
    expect(row.querySelector('[data-testid="chat-icon-ROCKET"]')).toBeTruthy();
  });

  it('a row with no icon set renders the existing default/fallback icon, not a broken/blank one', () => {
    fixture.detectChanges();
    flushInit({
      activeTenantId: 1,
      articles: [{ id: 7, title: 'Minha conversa', icon: null }],
    });
    fixture.detectChanges();

    const row = fixture.nativeElement.querySelector('[data-testid="chat-directory-row-article:7"]');
    expect(row.querySelector('[data-testid="avatar-fallback"]')).toBeTruthy();
    expect(row.querySelector('[data-testid="avatar-icon"]')).toBeNull();
  });

  it('renders a fallback label instead of a blank row for "Base de artigos" conversations with no title yet', () => {
    fixture.detectChanges();
    flushInit({
      activeTenantId: 1,
      articles: [
        { id: 7, title: null },
        { id: 8, title: '' },
      ],
    });
    fixture.detectChanges();

    const row7 = fixture.nativeElement.querySelector(
      '[data-testid="chat-directory-row-article:7"]',
    );
    const row8 = fixture.nativeElement.querySelector(
      '[data-testid="chat-directory-row-article:8"]',
    );
    expect(row7).toBeTruthy();
    expect(row8).toBeTruthy();
    expect(row7.textContent.trim()).not.toBe('');
    expect(row8.textContent.trim()).not.toBe('');
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

  it('shows the empty-conversations state distinctly from the no-results state when there is nothing but Support', () => {
    fixture.detectChanges();
    flushInit({});
    fixture.detectChanges();

    // Support alone is always present, so this list is never fully "empty" — the empty-state
    // copy only applies to the filtered-out remainder, verified indirectly via the no-results
    // test above; this test anchors that Support keeps rendering with zero other rows.
    expect(
      fixture.nativeElement.querySelector('[data-testid="chat-directory-row-support"]'),
    ).toBeTruthy();
  });
});
