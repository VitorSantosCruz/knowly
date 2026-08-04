import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { provideTransloco } from '@jsverse/transloco';
import { AccessGroupManagementPageComponent } from './access-group-management-page.component';
import { FakeTranslocoLoader } from '../../testing/fake-transloco-loader';

describe('AccessGroupManagementPageComponent', () => {
  let fixture: ComponentFixture<AccessGroupManagementPageComponent>;
  let httpMock: HttpTestingController;

  async function createFixture(): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [AccessGroupManagementPageComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        provideTransloco({
          config: { availableLangs: ['en', 'pt-BR'], defaultLang: 'en' },
          loader: FakeTranslocoLoader,
        }),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(AccessGroupManagementPageComponent);
  }

  afterEach(() => {
    httpMock.verify();
  });

  it('renders the access-group list via app-shared-list once it resolves', async () => {
    await createFixture();
    fixture.detectChanges();
    httpMock = TestBed.inject(HttpTestingController);

    httpMock.expectOne('/api/staff/access-groups').flush([{ id: 5, name: 'Support' }]);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('app-shared-list')).toBeTruthy();
    expect(fixture.nativeElement.textContent).toContain('Support');
  });

  it('creating a group submits the name and refreshes the list', async () => {
    await createFixture();
    fixture.detectChanges();
    httpMock = TestBed.inject(HttpTestingController);
    httpMock.expectOne('/api/staff/access-groups').flush([]);
    fixture.detectChanges();

    const nameInput: HTMLInputElement = fixture.nativeElement.querySelector(
      '[data-testid="access-group-name-input"]',
    );
    nameInput.value = 'Reviewers';
    nameInput.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    fixture.nativeElement
      .querySelector('[data-testid="create-access-group-form"]')
      .dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    const createReq = httpMock.expectOne('/api/staff/access-groups');
    expect(createReq.request.body).toEqual({ name: 'Reviewers' });
    createReq.flush({ id: 9, name: 'Reviewers' });

    httpMock.expectOne('/api/staff/access-groups').flush([{ id: 9, name: 'Reviewers' }]);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Reviewers');
  });

  it('selecting a group loads only STAFF (non-admin) users as assignable candidates', async () => {
    await createFixture();
    fixture.detectChanges();
    httpMock = TestBed.inject(HttpTestingController);
    httpMock.expectOne('/api/staff/access-groups').flush([{ id: 5, name: 'Support' }]);
    fixture.detectChanges();

    fixture.nativeElement
      .querySelector('[data-testid="shared-list-action-sharedList.actions.edit-5"]')
      .click();
    fixture.detectChanges();

    httpMock.expectOne('/api/staff/users').flush([
      { id: 1, email: 'admin@example.com', globalRole: 'STAFF_ADMIN' },
      { id: 2, email: 'staffer@example.com', globalRole: 'STAFF' },
    ]);
    fixture.detectChanges();

    httpMock.expectOne('/api/staff/users/2/permissions').flush({
      userId: 2,
      email: 'staffer@example.com',
      globalRole: 'STAFF',
      directPermissions: [],
      accessGroups: [],
      effectivePermissions: [],
      isLastAdminOfType: false,
    });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).not.toContain('admin@example.com');
    expect(fixture.nativeElement.textContent).toContain('staffer@example.com');
    expect(
      fixture.nativeElement.querySelector('[data-testid="access-group-assign-2"]'),
    ).toBeTruthy();
  });

  it('assigning a candidate calls the assign endpoint and reloads membership', async () => {
    await createFixture();
    fixture.detectChanges();
    httpMock = TestBed.inject(HttpTestingController);
    httpMock.expectOne('/api/staff/access-groups').flush([{ id: 5, name: 'Support' }]);
    fixture.detectChanges();

    fixture.nativeElement
      .querySelector('[data-testid="shared-list-action-sharedList.actions.edit-5"]')
      .click();
    fixture.detectChanges();

    httpMock
      .expectOne('/api/staff/users')
      .flush([{ id: 2, email: 'staffer@example.com', globalRole: 'STAFF' }]);
    fixture.detectChanges();

    httpMock.expectOne('/api/staff/users/2/permissions').flush({
      userId: 2,
      email: 'staffer@example.com',
      globalRole: 'STAFF',
      directPermissions: [],
      accessGroups: [],
      effectivePermissions: [],
      isLastAdminOfType: false,
    });
    fixture.detectChanges();

    fixture.nativeElement.querySelector('[data-testid="access-group-assign-2"]').click();
    fixture.detectChanges();

    // Real backend returns ResponseEntity.ok().build() -- a genuinely empty body, which
    // Angular parses as `null`, not `{}` (regression test, same class of bug as
    // MembersPageComponent#onAddMember).
    httpMock.expectOne('/api/staff/users/2/access-groups/5').flush(null);
    httpMock
      .expectOne('/api/staff/users')
      .flush([{ id: 2, email: 'staffer@example.com', globalRole: 'STAFF' }]);
    fixture.detectChanges();

    httpMock.expectOne('/api/staff/users/2/permissions').flush({
      userId: 2,
      email: 'staffer@example.com',
      globalRole: 'STAFF',
      directPermissions: [],
      accessGroups: [{ id: 5, name: 'Support' }],
      effectivePermissions: [],
      isLastAdminOfType: false,
    });
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[data-testid="access-group-unassign-2"]'),
    ).toBeTruthy();
  });

  it('unassigning a member goes through the security-phrase confirm dialog', async () => {
    await createFixture();
    fixture.detectChanges();
    httpMock = TestBed.inject(HttpTestingController);
    httpMock.expectOne('/api/staff/access-groups').flush([{ id: 5, name: 'Support' }]);
    fixture.detectChanges();

    fixture.nativeElement
      .querySelector('[data-testid="shared-list-action-sharedList.actions.edit-5"]')
      .click();
    fixture.detectChanges();

    httpMock
      .expectOne('/api/staff/users')
      .flush([{ id: 2, email: 'staffer@example.com', globalRole: 'STAFF' }]);
    fixture.detectChanges();

    httpMock.expectOne('/api/staff/users/2/permissions').flush({
      userId: 2,
      email: 'staffer@example.com',
      globalRole: 'STAFF',
      directPermissions: [],
      accessGroups: [{ id: 5, name: 'Support' }],
      effectivePermissions: [],
      isLastAdminOfType: false,
    });
    fixture.detectChanges();

    fixture.nativeElement.querySelector('[data-testid="access-group-unassign-2"]').click();
    fixture.detectChanges();

    httpMock
      .expectOne('/api/staff/users/2/access-groups/5/deletion-confirmation-token')
      .flush({ word: 'correct-horse' });
    fixture.detectChanges();

    const dialogEl = fixture.nativeElement.querySelector('app-confirm-dialog');
    const input: HTMLInputElement = dialogEl.querySelector('[data-testid="confirm-dialog-input"]');
    input.value = 'correct-horse';
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    dialogEl.querySelector('[data-testid="confirm-dialog-confirm"]').click();
    fixture.detectChanges();

    const deleteReq = httpMock.expectOne('/api/staff/users/2/access-groups/5');
    expect(deleteReq.request.body).toEqual({ word: 'correct-horse' });
    deleteReq.flush({});

    httpMock
      .expectOne('/api/staff/users')
      .flush([{ id: 2, email: 'staffer@example.com', globalRole: 'STAFF' }]);
    httpMock.expectOne('/api/staff/users/2/permissions').flush({
      userId: 2,
      email: 'staffer@example.com',
      globalRole: 'STAFF',
      directPermissions: [],
      accessGroups: [],
      effectivePermissions: [],
      isLastAdminOfType: false,
    });
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[data-testid="access-group-unassign-2"]'),
    ).toBeFalsy();
  });
});
