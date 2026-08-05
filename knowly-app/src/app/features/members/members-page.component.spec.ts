import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import { provideTransloco } from '@jsverse/transloco';
import { MembersPageComponent } from './members-page.component';
import { FakeTranslocoLoader } from '../../testing/fake-transloco-loader';

describe('MembersPageComponent', () => {
  let fixture: ComponentFixture<MembersPageComponent>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MembersPageComponent],
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

    fixture = TestBed.createComponent(MembersPageComponent);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  function flushActiveTenant() {
    httpMock
      .expectOne('/api/tenants/active')
      .flush({ tenantId: 7, tenantName: 'Acme', role: 'MEMBER_ADMIN' });
  }

  function flushOwnProfile(userId = 999) {
    httpMock.expectOne('/api/users/me/profile').flush({
      userId,
      email: 'me@example.com',
      fields: {
        fullName: 'Me',
        cpf: null,
        rg: null,
        rgOrgaoEmissor: null,
        birthDate: null,
        address: null,
        contacts: [],
      },
      avatarUrl: null,
    });
  }

  it('renders the member list once the active tenant and members resolve', () => {
    fixture.detectChanges();
    flushActiveTenant();
    flushOwnProfile();
    fixture.detectChanges();

    httpMock
      .expectOne('/api/tenants/7/members')
      .flush([{ membershipId: 1, email: 'a@example.com', role: 'MEMBER' }]);
    httpMock.expectOne('/api/tenants/7/access-groups').flush([]);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('app-shared-list')).toBeTruthy();
    expect(fixture.nativeElement.textContent).toContain('a@example.com');
  });

  it('adding a member collects the mandatory profile first, then posts it and refreshes the list', () => {
    fixture.detectChanges();
    flushActiveTenant();
    flushOwnProfile();
    fixture.detectChanges();
    httpMock.expectOne('/api/tenants/7/members').flush([]);
    httpMock.expectOne('/api/tenants/7/access-groups').flush([]);
    fixture.detectChanges();

    const emailInput: HTMLInputElement = fixture.nativeElement.querySelector(
      '[data-testid="add-member-email"]',
    );
    emailInput.value = 'new@example.com';
    emailInput.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    const form: HTMLFormElement = fixture.nativeElement.querySelector(
      '[data-testid="add-member-form"]',
    );
    form.dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    // The plain email/role form is replaced by the mandatory-profile-fields form (backend
    // requires a full profile on this request) rather than posting immediately.
    expect(fixture.nativeElement.querySelector('[data-testid="add-member-form"]')).toBeFalsy();
    const profileForm = fixture.nativeElement.querySelector(
      '[data-testid="add-member-profile-form"]',
    );
    expect(profileForm).toBeTruthy();

    const input = (testId: string): HTMLInputElement =>
      fixture.nativeElement.querySelector(`[data-testid="${testId}"]`);

    input('profile-field-fullName').value = 'New Member';
    input('profile-field-fullName').dispatchEvent(new Event('input'));
    input('profile-field-countryCode').value = 'BR';
    input('profile-field-countryCode').dispatchEvent(new Event('change'));
    input('profile-field-taxId').value = '111.111.111-11';
    input('profile-field-taxId').dispatchEvent(new Event('input'));
    input('profile-address-field-addressLine1').value = 'Main St, 123';
    input('profile-address-field-addressLine1').dispatchEvent(new Event('input'));
    input('profile-address-field-city').value = 'Sao Paulo';
    input('profile-address-field-city').dispatchEvent(new Event('input'));
    input('profile-address-field-stateRegion').value = 'SP';
    input('profile-address-field-stateRegion').dispatchEvent(new Event('input'));
    input('profile-address-field-postalCode').value = '01000-000';
    input('profile-address-field-postalCode').dispatchEvent(new Event('input'));
    fixture.detectChanges();

    input('profile-contact-add').click();
    fixture.detectChanges();
    const newRow = fixture.nativeElement.querySelector('[data-testid^="profile-contact-row-"]');
    const rowKey = newRow.getAttribute('data-testid').replace('profile-contact-row-', '');
    input(`profile-contact-type-${rowKey}`).value = 'EMAIL';
    input(`profile-contact-type-${rowKey}`).dispatchEvent(new Event('change'));
    fixture.detectChanges();
    input(`profile-contact-value-${rowKey}`).value = 'new@example.com';
    input(`profile-contact-value-${rowKey}`).dispatchEvent(new Event('input'));
    fixture.detectChanges();

    fixture.nativeElement.querySelector('[data-testid="profile-fields-submit"]').click();

    const req = httpMock.expectOne('/api/tenants/7/members');
    expect(req.request.method).toBe('POST');
    expect(req.request.body.email).toBe('new@example.com');
    expect(req.request.body.role).toBe('MEMBER');
    expect(req.request.body.profile.fullName).toBe('New Member');
    // The real backend's addMember returns ResponseEntity.ok().build() -- a genuinely empty
    // body, which Angular's HttpClient parses as `null`, not `{}`. Regression test for a bug
    // where the success handler's `if (result !== null)` check silently never fired against
    // the real backend, leaving the form stuck disabled forever.
    req.flush(null);
    httpMock
      .expectOne('/api/tenants/7/members')
      .flush([{ membershipId: 2, email: 'new@example.com', role: 'MEMBER' }]);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('new@example.com');
  });

  it('removing a member removes it from the list', () => {
    fixture.detectChanges();
    flushActiveTenant();
    flushOwnProfile();
    fixture.detectChanges();
    httpMock
      .expectOne('/api/tenants/7/members')
      .flush([{ membershipId: 1, email: 'a@example.com', role: 'MEMBER' }]);
    httpMock.expectOne('/api/tenants/7/access-groups').flush([]);
    fixture.detectChanges();

    const removeButton: HTMLButtonElement = fixture.nativeElement.querySelector(
      '[data-testid="shared-list-action-sharedList.actions.delete-1"]',
    );
    removeButton.click();
    fixture.detectChanges();

    httpMock
      .expectOne('/api/tenants/7/members/1/deletion-confirmation-token')
      .flush({ word: 'correct-horse' });
    fixture.detectChanges();

    const dialogEl = fixture.nativeElement.querySelector('app-confirm-dialog');
    const input: HTMLInputElement = dialogEl.querySelector('[data-testid="confirm-dialog-input"]');
    input.value = 'correct-horse';
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    dialogEl.querySelector('[data-testid="confirm-dialog-confirm"]').click();
    fixture.detectChanges();

    const deleteReq = httpMock.expectOne('/api/tenants/7/members/1');
    expect(deleteReq.request.body).toEqual({ word: 'correct-horse' });
    deleteReq.flush({});
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).not.toContain('a@example.com');
  });

  it('selecting a member shows the detail panel, opened in edit mode', () => {
    fixture.detectChanges();
    flushActiveTenant();
    flushOwnProfile();
    fixture.detectChanges();

    httpMock
      .expectOne('/api/tenants/7/members')
      .flush([{ membershipId: 1, userId: 42, email: 'a@example.com', role: 'MEMBER' }]);
    httpMock.expectOne('/api/tenants/7/access-groups').flush([]);
    fixture.detectChanges();

    const editButton: HTMLElement = fixture.nativeElement.querySelector(
      '[data-testid="shared-list-action-sharedList.actions.edit-1"]',
    );
    editButton.click();
    fixture.detectChanges();

    httpMock.expectOne('/api/tenants/7/members/1').flush({
      membershipId: 1,
      userId: 42,
      email: 'a@example.com',
      role: 'MEMBER',
      directPermissions: [],
      accessGroups: [],
      effectivePermissions: [],
    });
    httpMock.expectOne('/api/tenants/7/access-groups').flush([]);
    fixture.detectChanges();

    httpMock.expectOne('/api/users/42/profile').flush({
      userId: 42,
      email: 'a@example.com',
      fields: {
        fullName: 'Member A',
        cpf: '111.111.111-11',
        rg: '11.111.111-1',
        rgOrgaoEmissor: 'SSP',
        birthDate: '1990-01-01',
        address: null,
        contacts: [],
      },
      avatarUrl: null,
    });
    flushOwnProfile();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="member-detail-panel"]')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('[data-testid="profile-section"]')).toBeTruthy();
    // REQ-6: clicking the edit row action opens the panel directly in edit mode, not merely
    // opens the panel requiring a further click to find edit.
    expect(fixture.nativeElement.querySelector('[data-testid="profile-fields-form"]')).toBeTruthy();
  });

  it('shows a permission-denied state when the members list is forbidden', () => {
    fixture.detectChanges();
    flushActiveTenant();
    flushOwnProfile();
    fixture.detectChanges();

    httpMock
      .expectOne('/api/tenants/7/members')
      .flush({ code: 'PERMISSION_DENIED' }, { status: 403, statusText: 'Forbidden' });
    httpMock.expectOne('/api/tenants/7/access-groups').flush([]);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="no-access-state"]')).toBeTruthy();
  });

  it("shows a 'my profile' action instead of edit/delete on the viewer's own row, navigating to /profile", () => {
    const navigateSpy = vi.spyOn(TestBed.inject(Router), 'navigateByUrl');
    fixture.detectChanges();
    flushActiveTenant();
    flushOwnProfile(42);
    fixture.detectChanges();

    httpMock.expectOne('/api/tenants/7/members').flush([
      { membershipId: 1, userId: 42, email: 'me@example.com', role: 'MEMBER' },
      { membershipId: 2, userId: 43, email: 'other@example.com', role: 'MEMBER' },
    ]);
    httpMock.expectOne('/api/tenants/7/access-groups').flush([]);
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector(
        '[data-testid="shared-list-action-sharedList.actions.edit-1"]',
      ),
    ).toBeFalsy();
    expect(
      fixture.nativeElement.querySelector(
        '[data-testid="shared-list-action-sharedList.actions.delete-1"]',
      ),
    ).toBeFalsy();
    const myProfileAction: HTMLButtonElement = fixture.nativeElement.querySelector(
      '[data-testid="shared-list-action-sharedList.actions.myProfile-1"]',
    );
    expect(myProfileAction).toBeTruthy();

    expect(
      fixture.nativeElement.querySelector(
        '[data-testid="shared-list-action-sharedList.actions.edit-2"]',
      ),
    ).toBeTruthy();
    expect(
      fixture.nativeElement.querySelector(
        '[data-testid="shared-list-action-sharedList.actions.delete-2"]',
      ),
    ).toBeTruthy();

    myProfileAction.click();
    expect(navigateSpy).toHaveBeenCalledWith('/profile');
  });

  // Regression: hard-delete (irreversible account deletion, distinct from the soft "remove
  // from tenant" row action) used to live only inside member-detail-panel.component.ts's
  // bottom-of-panel button, which the design-system-consistency-pass migration removed
  // without adding a replacement anywhere — restored here as its own row action.
  it("shows a hard-delete row action (distinct from remove) for a MEMBER_ADMIN viewer, omitted from the viewer's own row", () => {
    fixture.detectChanges();
    flushActiveTenant();
    flushOwnProfile(42);
    fixture.detectChanges();

    httpMock.expectOne('/api/tenants/7/members').flush([
      { membershipId: 1, userId: 42, email: 'me@example.com', role: 'MEMBER' },
      { membershipId: 2, userId: 43, email: 'other@example.com', role: 'MEMBER' },
    ]);
    httpMock.expectOne('/api/tenants/7/access-groups').flush([]);
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[data-testid="shared-list-action-members.delete-1"]'),
    ).toBeFalsy();
    expect(
      fixture.nativeElement.querySelector('[data-testid="shared-list-action-members.delete-2"]'),
    ).toBeTruthy();
  });

  it('hard-deleting a member removes it from the list', () => {
    fixture.detectChanges();
    flushActiveTenant();
    flushOwnProfile();
    fixture.detectChanges();
    httpMock
      .expectOne('/api/tenants/7/members')
      .flush([{ membershipId: 1, userId: 43, email: 'a@example.com', role: 'MEMBER' }]);
    httpMock.expectOne('/api/tenants/7/access-groups').flush([]);
    fixture.detectChanges();

    const hardDeleteButton: HTMLButtonElement = fixture.nativeElement.querySelector(
      '[data-testid="shared-list-action-members.delete-1"]',
    );
    hardDeleteButton.click();
    fixture.detectChanges();

    httpMock
      .expectOne('/api/tenants/7/members/1/hard-delete/deletion-confirmation-token')
      .flush({ word: 'severed-oak' });
    fixture.detectChanges();

    const dialogEl = fixture.nativeElement.querySelector('app-confirm-dialog');
    const input: HTMLInputElement = dialogEl.querySelector('[data-testid="confirm-dialog-input"]');
    input.value = 'severed-oak';
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    dialogEl.querySelector('[data-testid="confirm-dialog-confirm"]').click();
    fixture.detectChanges();

    const deleteReq = httpMock.expectOne('/api/tenants/7/members/1/hard-delete');
    expect(deleteReq.request.method).toBe('DELETE');
    expect(deleteReq.request.body).toEqual({ word: 'severed-oak' });
    deleteReq.flush({});
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).not.toContain('a@example.com');
  });
});
