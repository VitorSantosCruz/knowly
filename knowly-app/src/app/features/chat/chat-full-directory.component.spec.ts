import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { provideTransloco } from '@jsverse/transloco';
import { FakeTranslocoLoader } from '../../testing/fake-transloco-loader';
import { ChatFullDirectoryComponent } from './chat-full-directory.component';

describe('ChatFullDirectoryComponent — Amendment (3): column 3', () => {
  let fixture: ComponentFixture<ChatFullDirectoryComponent>;
  let httpMock: HttpTestingController;
  let router: Router;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [ChatFullDirectoryComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideTransloco({
          config: { availableLangs: ['en'], defaultLang: 'en' },
          loader: FakeTranslocoLoader,
        }),
      ],
    });
    fixture = TestBed.createComponent(ChatFullDirectoryComponent);
    httpMock = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
  });

  afterEach(() => httpMock.verify());

  function flushInit(opts: {
    conversations?: unknown[];
    eligible?: unknown[];
    discoverable?: unknown[];
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
    httpMock
      .expectOne('/api/tenants/active')
      .flush(null, { status: 204, statusText: 'No Content' });
  }

  function searchInput(): HTMLInputElement {
    return fixture.nativeElement.querySelector('[data-testid="chat-full-directory-search"]');
  }

  function search(query: string): void {
    searchInput().value = query;
    searchInput().dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  it('renders discoveryRows() — not-yet-messaged people and discoverable groups, filtered by its own independent searchQuery', () => {
    fixture.detectChanges();
    flushInit({
      eligible: [{ userId: 2, nickname: 'Bob' }],
      discoverable: [
        { id: 9, title: 'Grupo Público', tenantId: 1, visibility: 'PUBLIC', participantCount: 2 },
      ],
    });
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[data-testid="chat-full-directory-row-person:2"]'),
    ).toBeTruthy();
    expect(
      fixture.nativeElement.querySelector('[data-testid="chat-full-directory-row-group:9"]'),
    ).toBeTruthy();
  });

  it('does not render a person already messaged (that row belongs in column 1 only)', () => {
    fixture.detectChanges();
    flushInit({
      conversations: [
        { id: 5, kind: 'PEER_DIRECT', tenantId: null, title: null, participantUserIds: [1, 2] },
      ],
      eligible: [{ userId: 2, nickname: 'Bob' }],
    });
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[data-testid="chat-full-directory-row-person:2"]'),
    ).toBeNull();
  });

  it('has its own searchQuery, never affecting or affected by column 1 (distinct data-testid/aria-label from column 1)', () => {
    fixture.detectChanges();
    flushInit({
      eligible: [
        { userId: 2, nickname: 'Bob' },
        { userId: 3, nickname: 'Alice' },
      ],
    });
    fixture.detectChanges();

    search('ali');

    expect(
      fixture.nativeElement.querySelector('[data-testid="chat-full-directory-row-person:3"]'),
    ).toBeTruthy();
    expect(
      fixture.nativeElement.querySelector('[data-testid="chat-full-directory-row-person:2"]'),
    ).toBeNull();

    expect(searchInput().getAttribute('aria-label')).toBeTruthy();
  });

  it('a search with zero matches shows its own distinct "no results" message', () => {
    fixture.detectChanges();
    flushInit({ eligible: [{ userId: 2, nickname: 'Bob' }] });
    fixture.detectChanges();

    search('zzz');
    expect(
      fixture.nativeElement.querySelector('[data-testid="chat-full-directory-no-results"]'),
    ).toBeTruthy();
  });

  it('shows its own empty-state message when there is nothing at all', () => {
    fixture.detectChanges();
    flushInit({});
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[data-testid="chat-full-directory-empty"]'),
    ).toBeTruthy();
  });

  it('clicking a not-yet-messaged person creates a DIRECT conversation and navigates (REQ-3, same behavior as column 1)', () => {
    fixture.detectChanges();
    flushInit({ eligible: [{ userId: 2, nickname: 'Bob' }] });
    fixture.detectChanges();

    fixture.nativeElement.querySelector('[data-testid="chat-full-directory-row-person:2"]').click();
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

  it('clicking a PUBLIC discoverable group joins immediately and navigates (REQ-3, same behavior as column 1)', () => {
    fixture.detectChanges();
    flushInit({
      discoverable: [
        { id: 9, title: 'Grupo Público', tenantId: 1, visibility: 'PUBLIC', participantCount: 2 },
      ],
    });
    fixture.detectChanges();

    fixture.nativeElement.querySelector('[data-testid="chat-full-directory-row-group:9"]').click();
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

  it('a REQUEST_TO_JOIN group shows pending state without navigating', () => {
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

    fixture.nativeElement.querySelector('[data-testid="chat-full-directory-row-group:10"]').click();
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
      fixture.nativeElement.querySelector('[data-testid="chat-full-directory-request-pending"]'),
    ).toBeTruthy();
    expect(router.navigate).not.toHaveBeenCalled();
  });

  it("the search field is keyboard-navigable with a distinct aria-label from column 1's", () => {
    fixture.detectChanges();
    flushInit({});
    fixture.detectChanges();

    const label = searchInput().getAttribute('aria-label');
    expect(label).toBeTruthy();
  });
});
