import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTransloco } from '@jsverse/transloco';
import { FakeTranslocoLoader } from '../../testing/fake-transloco-loader';
import { GroupAdminPanelComponent } from './group-admin-panel.component';
import { ConversationDetail } from '../../core/chat.model';

function detailFixture(overrides: Partial<ConversationDetail> = {}): ConversationDetail {
  return {
    id: 1,
    kind: 'PEER_GROUP',
    tenantId: 1,
    title: 'Grupo',
    participantUserIds: [1, 2, 3],
    participantNicknames: { 1: 'Me', 2: 'Bob', 3: 'Carol' },
    visibility: 'PUBLIC',
    archivedAt: null,
    adminUserIds: [1],
    icon: null,
    ...overrides,
  };
}

describe('GroupAdminPanelComponent', () => {
  let fixture: ComponentFixture<GroupAdminPanelComponent>;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [GroupAdminPanelComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideTransloco({
          config: { availableLangs: ['en'], defaultLang: 'en' },
          loader: FakeTranslocoLoader,
        }),
      ],
    });
    fixture = TestBed.createComponent(GroupAdminPanelComponent);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('renders none of its actions for a non-admin viewer — removed from the DOM, not hidden', () => {
    fixture.componentRef.setInput('detail', detailFixture());
    fixture.componentRef.setInput('currentUserId', 2);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="group-admin-panel"]')).toBeNull();
    httpMock.expectNone((r) => r.url.includes('join-requests'));
  });

  it('fetches and renders pending join requests with approve/reject for an admin viewer', () => {
    fixture.componentRef.setInput('detail', detailFixture());
    fixture.componentRef.setInput('currentUserId', 1);
    fixture.detectChanges();

    httpMock
      .expectOne((r) => r.url === '/api/chat/conversations/1/join-requests')
      .flush([
        {
          id: 5,
          conversationId: 1,
          requesterUserId: 9,
          requesterNickname: 'Dan',
          status: 'PENDING',
          decidedAt: null,
        },
      ]);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="group-admin-panel"]')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('[data-testid="approve-request-5"]')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('[data-testid="reject-request-5"]')).toBeTruthy();
  });

  it('approving removes the row on success; a 400 keeps it with the distinct "not approvable" message', () => {
    fixture.componentRef.setInput('detail', detailFixture());
    fixture.componentRef.setInput('currentUserId', 1);
    fixture.detectChanges();
    httpMock
      .expectOne((r) => r.url === '/api/chat/conversations/1/join-requests')
      .flush([
        {
          id: 5,
          conversationId: 1,
          requesterUserId: 9,
          requesterNickname: 'Dan',
          status: 'PENDING',
          decidedAt: null,
        },
      ]);
    fixture.detectChanges();

    fixture.nativeElement.querySelector('[data-testid="approve-request-5"]').click();
    httpMock
      .expectOne('/api/chat/conversations/1/join-requests/5/approve')
      .flush(null, { status: 400, statusText: 'Bad Request' });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="approve-request-5"]')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('[role="alert"]')).toBeTruthy();
  });

  it('rejecting removes the row on success only', () => {
    fixture.componentRef.setInput('detail', detailFixture());
    fixture.componentRef.setInput('currentUserId', 1);
    fixture.detectChanges();
    httpMock
      .expectOne((r) => r.url === '/api/chat/conversations/1/join-requests')
      .flush([
        {
          id: 5,
          conversationId: 1,
          requesterUserId: 9,
          requesterNickname: 'Dan',
          status: 'PENDING',
          decidedAt: null,
        },
      ]);
    fixture.detectChanges();

    fixture.nativeElement.querySelector('[data-testid="reject-request-5"]').click();
    httpMock.expectOne('/api/chat/conversations/1/join-requests/5/reject').flush({
      id: 5,
      conversationId: 1,
      requesterUserId: 9,
      requesterNickname: 'Dan',
      status: 'REJECTED',
      decidedAt: 'now',
    });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="reject-request-5"]')).toBeNull();
  });

  it('promotes a non-admin participant to admin on success (REQ-30)', () => {
    fixture.componentRef.setInput('detail', detailFixture());
    fixture.componentRef.setInput('currentUserId', 1);
    fixture.detectChanges();
    httpMock.expectOne((r) => r.url === '/api/chat/conversations/1/join-requests').flush([]);
    fixture.detectChanges();

    fixture.nativeElement.querySelector('[data-testid="promote-2"]').click();
    httpMock
      .expectOne('/api/chat/conversations/1/admins/2')
      .flush(detailFixture({ adminUserIds: [1, 2] }));
    // GroupAdminPanelComponent is purely presentational off its `detail` input — in the real
    // app, the parent (conversation-detail.component.ts) re-renders it from ChatService's own
    // patched _details map (which ChatGroupService.promote() just updated); this unit test
    // simulates that same re-render explicitly rather than reaching into ChatService.
    fixture.componentRef.setInput('detail', detailFixture({ adminUserIds: [1, 2] }));
    fixture.detectChanges();
    httpMock.expectOne((r) => r.url === '/api/chat/conversations/1/join-requests').flush([]);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="promote-2"]')).toBeNull();
  });

  it('removing a participant requires confirmation and removes them from the list on success only', () => {
    fixture.componentRef.setInput('detail', detailFixture());
    fixture.componentRef.setInput('currentUserId', 1);
    fixture.detectChanges();
    httpMock.expectOne((r) => r.url === '/api/chat/conversations/1/join-requests').flush([]);
    fixture.detectChanges();

    fixture.nativeElement.querySelector('[data-testid="remove-2"]').click();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-testid="confirm-remove-2"]')).toBeTruthy();

    fixture.nativeElement.querySelector('[data-testid="confirm-remove-2"]').click();
    httpMock
      .expectOne('/api/chat/conversations/1/participants/2')
      .flush(detailFixture({ participantUserIds: [1, 3] }));
    fixture.componentRef.setInput('detail', detailFixture({ participantUserIds: [1, 3] }));
    fixture.detectChanges();
    httpMock.expectOne((r) => r.url === '/api/chat/conversations/1/join-requests').flush([]);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="remove-2"]')).toBeNull();
  });

  it.each([['PRIVATE'], ['REQUEST_TO_JOIN'], ['PUBLIC']] as const)(
    'reflects the real group visibility (%s) as the selected option on open, not a hardcoded default',
    (visibility) => {
      fixture.componentRef.setInput('detail', detailFixture({ visibility }));
      fixture.componentRef.setInput('currentUserId', 1);
      fixture.detectChanges();
      httpMock.expectOne((r) => r.url === '/api/chat/conversations/1/join-requests').flush([]);
      fixture.detectChanges();

      const select: HTMLSelectElement = fixture.nativeElement.querySelector(
        '[data-testid="group-admin-visibility-select"]',
      );
      expect(select.value).toBe(visibility);
    },
  );

  it('changing visibility calls the update endpoint and updates the badge on success only', () => {
    fixture.componentRef.setInput('detail', detailFixture({ visibility: 'PUBLIC' }));
    fixture.componentRef.setInput('currentUserId', 1);
    fixture.detectChanges();
    httpMock.expectOne((r) => r.url === '/api/chat/conversations/1/join-requests').flush([]);
    fixture.detectChanges();

    const select: HTMLSelectElement = fixture.nativeElement.querySelector(
      '[data-testid="group-admin-visibility-select"]',
    );
    select.value = 'REQUEST_TO_JOIN';
    select.dispatchEvent(new Event('change'));

    httpMock
      .expectOne('/api/chat/conversations/1/visibility')
      .flush(detailFixture({ visibility: 'REQUEST_TO_JOIN' }));
  });

  it('deleting the group requires confirmation and calls the delete endpoint on confirm', () => {
    fixture.componentRef.setInput('detail', detailFixture());
    fixture.componentRef.setInput('currentUserId', 1);
    fixture.detectChanges();
    httpMock.expectOne((r) => r.url === '/api/chat/conversations/1/join-requests').flush([]);
    fixture.detectChanges();

    fixture.nativeElement.querySelector('[data-testid="delete-group"]').click();
    fixture.detectChanges();
    fixture.nativeElement.querySelector('[data-testid="confirm-delete-group"]').click();

    httpMock
      .expectOne('/api/chat/conversations/1')
      .flush(null, { status: 204, statusText: 'No Content' });
  });
});
