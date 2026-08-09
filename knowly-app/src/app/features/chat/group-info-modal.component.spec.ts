import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { provideTransloco } from '@jsverse/transloco';
import { FakeTranslocoLoader } from '../../testing/fake-transloco-loader';
import { GroupInfoModalComponent } from './group-info-modal.component';
import { ConversationDetail } from '../../core/chat.model';

function detailFixture(overrides: Partial<ConversationDetail> = {}): ConversationDetail {
  return {
    id: 1,
    kind: 'PEER_GROUP',
    tenantId: 5,
    title: 'Grupo',
    participantUserIds: [1, 2],
    participantNicknames: { 1: 'Me', 2: 'Bob' },
    visibility: 'PUBLIC',
    archivedAt: null,
    adminUserIds: [],
    icon: null,
    ...overrides,
  };
}

describe('GroupInfoModalComponent', () => {
  let fixture: ComponentFixture<GroupInfoModalComponent>;
  let httpMock: HttpTestingController;
  let router: Router;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [GroupInfoModalComponent],
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
    fixture = TestBed.createComponent(GroupInfoModalComponent);
    httpMock = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
  });

  afterEach(() => httpMock.verify());

  function open(detail: ConversationDetail, currentUserId: number | null) {
    fixture.componentRef.setInput('open', true);
    fixture.componentRef.setInput('detail', detail);
    fixture.componentRef.setInput('currentUserId', currentUserId);
    fixture.detectChanges();
  }

  it('renders the group name, visibility, and member list', () => {
    open(detailFixture(), 1);

    expect(fixture.nativeElement.textContent).toContain('Grupo');
    const members = fixture.nativeElement.querySelectorAll(
      '[data-testid="group-info-modal-member"]',
    );
    expect(members.length).toBe(2);
    expect(fixture.nativeElement.textContent).toContain('Bob');
  });

  it('shows "leave group" for a genuine participant, not for a LOOKING_IN viewer', () => {
    open(detailFixture(), 1);
    expect(fixture.nativeElement.querySelector('[data-testid="leave-group"]')).toBeTruthy();

    fixture.componentRef.setInput('currentUserId', 99);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-testid="leave-group"]')).toBeNull();
  });

  it('confirming "leave group" calls ChatGroupService.leave and navigates away, emitting dismissed', () => {
    open(detailFixture(), 1);

    let dismissed = false;
    fixture.componentInstance.dismissed.subscribe(() => (dismissed = true));

    fixture.nativeElement.querySelector('[data-testid="leave-group"]').click();
    fixture.detectChanges();
    fixture.nativeElement.querySelector('[data-testid="confirm-leave-group"]').click();

    httpMock
      .expectOne('/api/chat/conversations/1/leave')
      .flush(null, { status: 204, statusText: 'No Content' });

    expect(router.navigate).toHaveBeenCalledWith(['/chat']);
    expect(dismissed).toBe(true);
  });

  it('a failed leave shows an inline error and does not navigate away', () => {
    open(detailFixture(), 1);

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

  it('renders GroupAdminPanelComponent (self-gated on admin status)', () => {
    open(detailFixture(), 1);
    expect(fixture.nativeElement.querySelector('app-group-admin-panel')).toBeTruthy();
  });

  it('inviting fetches "group"-scoped candidates and adds the selection via ChatGroupService.addParticipants', () => {
    open(detailFixture(), 1);

    fixture.nativeElement.querySelector('[data-testid="group-info-modal-invite-toggle"]').click();
    fixture.detectChanges();

    httpMock
      .expectOne(
        (r) =>
          r.url === '/api/chat/eligible-participants' &&
          r.params.get('scope') === 'group' &&
          r.params.get('tenantId') === '5',
      )
      .flush([{ userId: 3, nickname: 'Carol' }]);
    fixture.detectChanges();

    fixture.nativeElement.querySelector('[data-testid="participant-picker-candidate"]').click();
    fixture.detectChanges();

    fixture.nativeElement.querySelector('[data-testid="group-info-modal-invite-submit"]').click();

    httpMock
      .expectOne('/api/chat/conversations/1/participants')
      .flush({ conversation: detailFixture({ participantUserIds: [1, 2, 3] }), rejected: [] });
  });

  it('closes without opening a native dialog when open() is false', () => {
    fixture.componentRef.setInput('detail', detailFixture());
    fixture.componentRef.setInput('currentUserId', 1);
    fixture.detectChanges();

    const dialog: HTMLDialogElement = fixture.nativeElement.querySelector(
      '[data-testid="group-info-modal"]',
    );
    expect(dialog.open).toBe(false);
  });
});
